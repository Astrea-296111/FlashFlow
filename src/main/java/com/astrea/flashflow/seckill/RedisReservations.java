package com.astrea.flashflow.seckill;

import com.astrea.flashflow.common.BusinessException;
import com.astrea.flashflow.inventory.TicketSku;
import java.time.Clock;
import java.util.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class RedisReservations {
  public record ReservationResult(String code, String reservationId, ReservationCommand command) {}

  private final StringRedisTemplate redis;
  private final Clock clock;
  private final DefaultRedisScript<List> reserve = new DefaultRedisScript<>();
  private final DefaultRedisScript<Long> initialize = new DefaultRedisScript<>();
  private final DefaultRedisScript<Long> transition = new DefaultRedisScript<>();

  public RedisReservations(StringRedisTemplate redis, Clock clock) {
    this.redis = redis;
    this.clock = clock;
    reserve.setLocation(new ClassPathResource("lua/reserve.lua"));
    reserve.setResultType(List.class);
    initialize.setLocation(new ClassPathResource("lua/initialize.lua"));
    initialize.setResultType(Long.class);
    transition.setLocation(new ClassPathResource("lua/transition.lua"));
    transition.setResultType(Long.class);
  }

  public static String prefix(long sku) {
    return "flashflow:{sku:" + sku + "}:";
  }

  public void initialize(TicketSku sku) {
    var p = prefix(sku.id());
    Long value =
        redis.execute(
            initialize,
            List.of(p + "stock", p + "metadata", p + "buyers", p + "deadline"),
            Integer.toString(sku.availableStock()),
            Long.toString(sku.eventId()),
            Long.toString(sku.saleStartAt().toEpochMilli()),
            Long.toString(sku.saleEndAt().toEpochMilli()));
    if (value == null || value < 0) throw new BusinessException("REDIS_STATE_CORRUPT", 503);
  }

  public ReservationResult reserve(long user, long sku, long ttlSeconds) {
    String id = ReservationCommand.newId(sku);
    var p = prefix(sku);
    List<?> result =
        redis.execute(
            reserve,
            List.of(
                p + "stock", p + "buyers", p + "reservation:" + id, p + "deadline", p + "metadata"),
            Long.toString(user),
            id,
            Long.toString(sku),
            Long.toString(ttlSeconds * 1000));
    if (result == null || result.isEmpty()) throw new BusinessException("REDIS_UNAVAILABLE", 503);
    String code = result.getFirst().toString();
    if ("RESERVED".equals(code)) {
      var cmd =
          new ReservationCommand(
              id,
              user,
              Long.parseLong(result.get(2).toString()),
              sku,
              Long.parseLong(result.get(3).toString()),
              Long.parseLong(result.get(4).toString()));
      return new ReservationResult(code, id, cmd);
    }
    return new ReservationResult(code, result.size() > 1 ? result.get(1).toString() : null, null);
  }

  public long transition(String id, String action) {
    long sku = ReservationCommand.skuFromId(id);
    String p = prefix(sku);
    Long r =
        redis.execute(
            transition,
            List.of(p + "stock", p + "buyers", p + "reservation:" + id, p + "deadline"),
            action,
            id);
    if (r == null || r < 0) throw new BusinessException("REDIS_STATE_MISSING", 503);
    return r;
  }

  public Map<Object, Object> get(String id) {
    return redis
        .opsForHash()
        .entries(prefix(ReservationCommand.skuFromId(id)) + "reservation:" + id);
  }

  public ReservationCommand command(String id) {
    var r = get(id);
    if (r.isEmpty()) throw new BusinessException("RESERVATION_NOT_FOUND", 404);
    return new ReservationCommand(
        id,
        Long.parseLong(r.get("userId").toString()),
        Long.parseLong(r.get("eventId").toString()),
        Long.parseLong(r.get("skuId").toString()),
        Long.parseLong(r.get("createdAt").toString()),
        Long.parseLong(r.get("expiresAt").toString()));
  }

  public Set<String> due(long sku) {
    return redis.opsForZSet().rangeByScore(prefix(sku) + "deadline", 0, clock.millis(), 0, 100);
  }

  public int stock(long sku) {
    return Integer.parseInt(Objects.requireNonNull(redis.opsForValue().get(prefix(sku) + "stock")));
  }

  public boolean getTemplateStockMissing(long sku) {
    return !Boolean.TRUE.equals(redis.hasKey(prefix(sku) + "stock"));
  }
}
