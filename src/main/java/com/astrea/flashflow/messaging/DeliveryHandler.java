package com.astrea.flashflow.messaging;

import com.astrea.flashflow.observability.BusinessMetrics;
import com.astrea.flashflow.order.*;
import com.astrea.flashflow.seckill.*;
import java.time.Clock;
import org.springframework.stereotype.Service;

@Service
public class DeliveryHandler {
  private final AsyncOrderService orders;
  private final RedisReservations redis;
  private final BusinessMetrics metrics;
  private final Clock clock;

  public DeliveryHandler(
      AsyncOrderService orders, RedisReservations redis, BusinessMetrics metrics, Clock clock) {
    this.orders = orders;
    this.redis = redis;
    this.metrics = metrics;
    this.clock = clock;
  }

  public AsyncOrderService.Decision consume(ReservationCommand c) {
    var decision = orders.process(c);
    redis.transition(c.reservationId(), decision.action());
    if (decision.created()) {
      metrics.increment("order.created");
      metrics.completed(clock.millis() - c.createdAt());
    } else if (decision.order() != null) metrics.increment("message.duplicate");
    return decision;
  }

  public void reconcile(ReservationCommand c) {
    var decision = orders.reconcile(c);
    if (!"NONE".equals(decision.action())
        && redis.transition(c.reservationId(), decision.action()) > 0)
      metrics.increment("compensation");
  }
}
