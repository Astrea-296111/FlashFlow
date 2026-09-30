package com.astrea.flashflow.infrastructure;

import com.astrea.flashflow.inventory.InventoryRepository;
import com.astrea.flashflow.messaging.*;
import com.astrea.flashflow.order.OrderService;
import com.astrea.flashflow.seckill.RedisReservations;
import java.sql.Timestamp;
import java.time.Clock;
import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = "flashflow.scheduling-enabled",
    havingValue = "true",
    matchIfMissing = true)
@ConditionalOnExpression("'${flashflow.role}' == 'worker' or '${flashflow.role}' == 'all'")
public class MaintenanceJobs {
  private static final Logger log = LoggerFactory.getLogger(MaintenanceJobs.class);
  private final InventoryRepository inventory;
  private final RedisReservations redis;
  private final DeliveryHandler handler;
  private final OrderService orders;
  private final OutboxRelay relay;
  private final JdbcTemplate db;
  private final Clock clock;

  public MaintenanceJobs(
      InventoryRepository inventory,
      RedisReservations redis,
      DeliveryHandler handler,
      OrderService orders,
      OutboxRelay relay,
      JdbcTemplate db,
      Clock clock) {
    this.inventory = inventory;
    this.redis = redis;
    this.handler = handler;
    this.orders = orders;
    this.relay = relay;
    this.db = db;
    this.clock = clock;
  }

  @Scheduled(fixedDelay = 2000, initialDelay = 3000)
  public void reservations() {
    for (long sku : inventory.asyncSkus()) {
      try {
        for (String id : redis.due(sku)) handler.reconcile(redis.command(id));
      } catch (Exception e) {
        log.warn("Reservation reconciliation retry: {}", e.getClass().getSimpleName());
      }
    }
  }

  @Scheduled(fixedDelay = 2000, initialDelay = 3000)
  public void outboxes() {
    try {
      relay.messages();
      relay.releases();
    } catch (Exception e) {
      log.warn("Outbox retry: {}", e.getClass().getSimpleName());
    }
  }

  @Scheduled(fixedDelay = 2000, initialDelay = 4000)
  public void lifecycle() {
    for (String no :
        db.queryForList(
            "SELECT order_no FROM orders WHERE status='WAIT_PAY' AND expire_at<=? ORDER BY expire_at LIMIT 100",
            String.class,
            Timestamp.from(clock.instant()))) {
      try {
        orders.closeExpired(no);
      } catch (Exception e) {
        log.warn("Order timeout retry: {}", e.getClass().getSimpleName());
      }
    }
    for (String no :
        db.queryForList(
            "SELECT order_no FROM orders WHERE status='PAID' LIMIT 100", String.class)) {
      try {
        orders.issue(no);
      } catch (Exception e) {
        log.warn("Issue retry: {}", e.getClass().getSimpleName());
      }
    }
  }
}
