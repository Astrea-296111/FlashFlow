package com.astrea.flashflow.messaging;

import com.astrea.flashflow.observability.BusinessMetrics;
import com.astrea.flashflow.seckill.RedisReservations;
import org.slf4j.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxRelay {
  private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
  private final JdbcTemplate db;
  private final MessageBus bus;
  private final RedisReservations redis;
  private final BusinessMetrics metrics;

  public OutboxRelay(
      JdbcTemplate db, MessageBus bus, RedisReservations redis, BusinessMetrics metrics) {
    this.db = db;
    this.bus = bus;
    this.redis = redis;
    this.metrics = metrics;
  }

  @Transactional
  public void messages() {
    var rows =
        db.queryForList(
            "SELECT * FROM message_outbox WHERE status='PENDING' ORDER BY id LIMIT 10 FOR UPDATE SKIP LOCKED");
    for (var r : rows) {
      try {
        bus.sendTimeout(
            r.get("payload").toString(),
            r.get("business_key").toString(),
            ((Number) r.get("delay_level")).intValue());
        db.update(
            "UPDATE message_outbox SET status='SENT',attempts=attempts+1 WHERE id=?", r.get("id"));
      } catch (Exception e) {
        db.update("UPDATE message_outbox SET attempts=attempts+1 WHERE id=?", r.get("id"));
        metrics.increment("outbox.retry");
        log.warn("Timeout outbox retry: {}", e.getClass().getSimpleName());
      }
    }
  }

  @Transactional
  public void releases() {
    var rows =
        db.queryForList(
            "SELECT reservation_id FROM stock_release_outbox WHERE status='PENDING' ORDER BY created_at LIMIT 100 FOR UPDATE SKIP LOCKED");
    for (var r : rows) {
      String id = r.get("reservation_id").toString();
      redis.transition(id, "CLOSE");
      db.update("UPDATE stock_release_outbox SET status='DONE' WHERE reservation_id=?", id);
      metrics.increment("stock.released");
    }
  }
}
