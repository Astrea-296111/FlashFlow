package com.astrea.flashflow.seckill;

import com.astrea.flashflow.common.BusinessException;
import com.astrea.flashflow.messaging.MessageBus;
import com.astrea.flashflow.observability.BusinessMetrics;
import com.astrea.flashflow.order.OrderRepository;
import com.github.benmanes.caffeine.cache.*;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class SeckillService {
  public record Result(String status, String reservationId, String delivery, String orderNo) {}

  private final RedisReservations redis;
  private final TrafficLimiter limiter;
  private final MessageBus bus;
  private final BusinessMetrics metrics;
  private final OrderRepository orders;
  private final JdbcTemplate db;
  private final long ttl;
  private final Cache<Long, Boolean> soldOut =
      Caffeine.newBuilder().maximumSize(10000).expireAfterWrite(Duration.ofMillis(250)).build();

  public SeckillService(
      RedisReservations redis,
      TrafficLimiter limiter,
      MessageBus bus,
      BusinessMetrics metrics,
      OrderRepository orders,
      JdbcTemplate db,
      @Value("${flashflow.reservation-ttl}") long ttl) {
    if (ttl < 1 || ttl > 86400) throw new IllegalArgumentException("Invalid reservation TTL");
    this.redis = redis;
    this.limiter = limiter;
    this.bus = bus;
    this.metrics = metrics;
    this.orders = orders;
    this.db = db;
    this.ttl = ttl;
  }

  public Result submit(long user, long sku) {
    if (soldOut.getIfPresent(sku) != null) {
      metrics.request("SOLD_OUT");
      throw new BusinessException("SOLD_OUT", 409);
    }
    if (!limiter.allow(sku)) {
      metrics.request("RATE_LIMITED");
      throw new BusinessException("RATE_LIMITED", 429);
    }
    var result = redis.reserve(user, sku, ttl);
    metrics.request(result.code());
    if ("DUPLICATE".equals(result.code()))
      return new Result("DUPLICATE", result.reservationId(), "PREVIOUS", null);
    if (!"RESERVED".equals(result.code())) {
      if ("SOLD_OUT".equals(result.code())) soldOut.put(sku, true);
      throw new BusinessException(
          result.code(),
          switch (result.code()) {
            case "NOT_INITIALIZED", "CORRUPT_STATE" -> 503;
            default -> 409;
          });
    }
    String delivery = "ACKNOWLEDGED";
    try {
      bus.sendReservation(result.command());
    } catch (Exception e) {
      delivery = "UNCERTAIN";
      metrics.increment("producer.failure");
    }
    return new Result("QUEUED", result.reservationId(), delivery, null);
  }

  public Result result(long user, String id) {
    var r = redis.get(id);
    if (!r.isEmpty() && Long.parseLong(r.get("userId").toString()) != user)
      throw new BusinessException("RESERVATION_NOT_FOUND", 404);
    if (!r.isEmpty() && "RESERVED".equals(r.get("status")))
      return new Result("QUEUED", id, "PENDING", null);
    var order = orders.byReservation(id);
    if (order.isPresent()) {
      if (order.get().userId() != user) throw new BusinessException("RESERVATION_NOT_FOUND", 404);
      return new Result("SUCCESS", id, "PERSISTED", order.get().orderNo());
    }
    var rows =
        db.queryForList(
            "SELECT user_id,status FROM seckill_reservation WHERE reservation_id=?", id);
    if (r.isEmpty()
        && (rows.isEmpty() || ((Number) rows.getFirst().get("user_id")).longValue() != user))
      throw new BusinessException("RESERVATION_NOT_FOUND", 404);
    return new Result("FAILED", id, "TERMINAL", null);
  }
}
