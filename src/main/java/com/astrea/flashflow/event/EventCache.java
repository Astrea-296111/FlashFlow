package com.astrea.flashflow.event;

import com.astrea.flashflow.common.BusinessException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.*;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class EventCache {
  public static final String CHANNEL = "flashflow:event:invalidate";
  private final EventService events;
  private final StringRedisTemplate redis;
  private final ObjectMapper json;
  private final boolean enabled;
  private final Cache<Long, Map<String, Object>> local =
      Caffeine.newBuilder()
          .maximumSize(1000)
          .expireAfterWrite(Duration.ofSeconds(2))
          .recordStats()
          .build();

  public EventCache(
      EventService events,
      StringRedisTemplate redis,
      ObjectMapper json,
      @Value("${flashflow.cache-enabled}") boolean enabled) {
    this.events = events;
    this.redis = redis;
    this.json = json;
    this.enabled = enabled;
  }

  public Map<String, Object> detail(long id) {
    if (!enabled) return events.detail(id);
    var r = local.get(id, this::load);
    if (r.containsKey("__missing")) throw new BusinessException("EVENT_NOT_FOUND", 404);
    return r;
  }

  private Map<String, Object> load(long id) {
    String key = "flashflow:event:" + id;
    try {
      String value = redis.opsForValue().get(key);
      if (value != null) return json.readValue(value, new TypeReference<Map<String, Object>>() {});
      Map<String, Object> data;
      try {
        data = events.detail(id);
      } catch (BusinessException missing) {
        if (missing.status() != 404) throw missing;
        data = Map.of("__missing", true);
      }
      redis
          .opsForValue()
          .set(
              key,
              json.writeValueAsString(data),
              Duration.ofSeconds(
                  data.containsKey("__missing")
                      ? 5
                      : 45 + ThreadLocalRandom.current().nextInt(16)));
      return data;
    } catch (org.springframework.data.redis.RedisConnectionFailureException offline) {
      return events.detail(id);
    } catch (com.fasterxml.jackson.core.JsonProcessingException invalidCache) {
      redis.delete(key);
      return events.detail(id);
    }
  }

  public void invalidate(long id) {
    local.invalidate(id);
    redis.delete("flashflow:event:" + id);
    redis.convertAndSend(CHANNEL, Long.toString(id));
  }

  public void evictLocal(long id) {
    local.invalidate(id);
  }
}
