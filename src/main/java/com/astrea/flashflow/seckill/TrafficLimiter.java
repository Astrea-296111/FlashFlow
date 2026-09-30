package com.astrea.flashflow.seckill;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class TrafficLimiter {
  private final StringRedisTemplate redis;
  private final DefaultRedisScript<Long> script = new DefaultRedisScript<>();
  private final boolean enabled;
  private final int rate;
  private final int burst;

  public TrafficLimiter(
      StringRedisTemplate redis,
      @Value("${flashflow.rate-limit-enabled}") boolean enabled,
      @Value("${flashflow.rate}") int rate,
      @Value("${flashflow.burst}") int burst) {
    if (rate <= 0 || burst <= 0)
      throw new IllegalArgumentException("Rate and burst must be positive");
    this.redis = redis;
    this.enabled = enabled;
    this.rate = rate;
    this.burst = burst;
    script.setLocation(new ClassPathResource("lua/token-bucket.lua"));
    script.setResultType(Long.class);
  }

  public boolean allow(long sku) {
    return !enabled || allow(sku, rate, burst);
  }

  public boolean allow(long sku, int rate, int burst) {
    return Long.valueOf(1)
        .equals(
            redis.execute(
                script,
                List.of(RedisReservations.prefix(sku) + "bucket"),
                Integer.toString(rate),
                Integer.toString(burst)));
  }
}
