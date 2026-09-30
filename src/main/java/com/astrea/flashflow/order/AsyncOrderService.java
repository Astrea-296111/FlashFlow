package com.astrea.flashflow.order;

import com.astrea.flashflow.inventory.InventoryRepository;
import com.astrea.flashflow.messaging.*;
import com.astrea.flashflow.seckill.ReservationCommand;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.*;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AsyncOrderService {
  public record Decision(String action, OrderView order, boolean created) {}

  private final JdbcTemplate db;
  private final InventoryRepository inventory;
  private final OrderRepository orders;
  private final OrderService service;
  private final Clock clock;
  private final ObjectMapper json;
  private final long paymentTtl;

  public AsyncOrderService(
      JdbcTemplate db,
      InventoryRepository inventory,
      OrderRepository orders,
      OrderService service,
      Clock clock,
      ObjectMapper json,
      @Value("${flashflow.payment-ttl}") long paymentTtl) {
    DelayLevels.forSeconds(paymentTtl);
    this.db = db;
    this.inventory = inventory;
    this.orders = orders;
    this.service = service;
    this.clock = clock;
    this.json = json;
    this.paymentTtl = paymentTtl;
  }

  private Map<String, Object> fence(ReservationCommand c) {
    c.validate();
    // No-op upsert acquires an exclusive duplicate-key lock. INSERT IGNORE followed by
    // FOR UPDATE can deadlock when concurrent duplicates upgrade shared locks.
    db.update(
        "INSERT INTO seckill_reservation(reservation_id,user_id,event_id,sku_id,status,origin,expire_at,created_at) VALUES(?,?,?,?,'RESERVED','ASYNC',?,?) ON DUPLICATE KEY UPDATE reservation_id=reservation_id",
        c.reservationId(),
        c.userId(),
        c.eventId(),
        c.skuId(),
        Timestamp.from(Instant.ofEpochMilli(c.expiresAt())),
        Timestamp.from(Instant.ofEpochMilli(c.createdAt())));
    var row =
        db.queryForMap(
            "SELECT * FROM seckill_reservation WHERE reservation_id=? FOR UPDATE",
            c.reservationId());
    if (((Number) row.get("user_id")).longValue() != c.userId()
        || ((Number) row.get("sku_id")).longValue() != c.skuId()
        || ((Number) row.get("event_id")).longValue() != c.eventId()
        || ((Timestamp) row.get("expire_at")).getTime() != c.expiresAt())
      throw new IllegalArgumentException("Reservation identity mismatch");
    return row;
  }

  private Decision terminal(String state, String id) {
    return switch (state) {
      case "ROLLED_BACK", "EXPIRED" -> new Decision("ROLLBACK", null, false);
      case "CONFIRMED" -> new Decision("CONFIRM", orders.byReservation(id).orElseThrow(), false);
      case "CLOSED" -> new Decision("CLOSE", orders.byReservation(id).orElseThrow(), false);
      default -> null;
    };
  }

  private Decision rollback(String id, String state) {
    db.update(
        "UPDATE seckill_reservation SET status=? WHERE reservation_id=? AND status='RESERVED'",
        state,
        id);
    return new Decision("ROLLBACK", null, false);
  }

  @Transactional
  public Decision process(ReservationCommand c) {
    c.validate();
    // Lock the parent BEFORE inserting its child. The reservation FK otherwise
    // takes a shared SKU lock; concurrent consumers deadlock upgrading it to X.
    var sku = inventory.get(c.skuId(), true);
    if (sku.eventId() != c.eventId()) throw new IllegalArgumentException("SKU event mismatch");
    var r = fence(c);
    var terminal = terminal(r.get("status").toString(), c.reservationId());
    if (terminal != null) return terminal;
    if (c.expiresAt() <= clock.millis()) return rollback(c.reservationId(), "EXPIRED");
    if (!"ASYNC".equals(sku.mode())
        || db.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE user_id=? AND sku_id=?",
                Integer.class,
                c.userId(),
                c.skuId())
            > 0) return rollback(c.reservationId(), "ROLLED_BACK");
    if (inventory.deduct(c.skuId(), "ASYNC") != 1)
      return rollback(c.reservationId(), "ROLLED_BACK");
    var now = clock.instant();
    var order =
        service.insertOrder(c.reservationId(), c.userId(), sku, now, now.plusSeconds(paymentTtl));
    db.update(
        "UPDATE seckill_reservation SET status='CONFIRMED' WHERE reservation_id=? AND status='RESERVED'",
        c.reservationId());
    try {
      db.update(
          "INSERT INTO message_outbox(business_key,kind,payload,delay_level) VALUES(?,'ORDER_TIMEOUT',?,?)",
          order.orderNo(),
          json.writeValueAsString(
              new TimeoutCommand(order.orderNo(), order.expireAt().toEpochMilli())),
          DelayLevels.forSeconds(paymentTtl));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
    return new Decision("CONFIRM", order, true);
  }

  @Transactional
  public Decision reconcile(ReservationCommand c) {
    c.validate();
    var sku = inventory.get(c.skuId(), true);
    if (sku.eventId() != c.eventId()) throw new IllegalArgumentException("SKU event mismatch");
    var r = fence(c);
    var terminal = terminal(r.get("status").toString(), c.reservationId());
    if (terminal != null) return terminal;
    var order = orders.byReservation(c.reservationId());
    if (order.isPresent()) {
      String state =
          (order.get().status() == OrderStatus.CLOSED
                  || order.get().status() == OrderStatus.REFUNDED)
              ? "CLOSED"
              : "CONFIRMED";
      db.update(
          "UPDATE seckill_reservation SET status=? WHERE reservation_id=?",
          state,
          c.reservationId());
      return new Decision(state.equals("CLOSED") ? "CLOSE" : "CONFIRM", order.get(), false);
    }
    if (c.expiresAt() > clock.millis()) return new Decision("NONE", null, false);
    return rollback(c.reservationId(), "ROLLED_BACK");
  }
}
