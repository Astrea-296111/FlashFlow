package com.astrea.flashflow.order;

import com.astrea.flashflow.common.BusinessException;
import com.astrea.flashflow.inventory.*;
import com.astrea.flashflow.seckill.ReservationCommand;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {
  private final JdbcTemplate db;
  private final InventoryRepository inventory;
  private final OrderRepository orders;
  private final Clock clock;
  private final long paymentTtl;

  public OrderService(
      JdbcTemplate db,
      InventoryRepository inventory,
      OrderRepository orders,
      Clock clock,
      @Value("${flashflow.payment-ttl}") long paymentTtl) {
    this.db = db;
    this.inventory = inventory;
    this.orders = orders;
    this.clock = clock;
    this.paymentTtl = paymentTtl;
  }

  @Transactional
  public OrderView baseline(long user, long skuId) {
    var sku = inventory.get(skuId, false);
    var now = clock.instant();
    validateSale(sku, now);
    if (!"BASELINE".equals(sku.mode())) throw new BusinessException("SKU_MODE_CONFLICT", 409);
    if (inventory.deduct(skuId, "BASELINE") != 1) throw new BusinessException("SOLD_OUT", 409);
    var id = ReservationCommand.newId(skuId);
    var deadline = now.plusSeconds(paymentTtl);
    db.update(
        "INSERT INTO seckill_reservation(reservation_id,user_id,event_id,sku_id,status,origin,expire_at,created_at) VALUES(?,?,?,?,'CONFIRMED','BASELINE',?,?)",
        id,
        user,
        sku.eventId(),
        skuId,
        Timestamp.from(deadline),
        Timestamp.from(now));
    return insertOrder(id, user, sku, now, deadline);
  }

  public static void validateSale(TicketSku sku, Instant now) {
    if (!"ACTIVE".equals(sku.status())
        || !"ACTIVE".equals(sku.eventStatus())
        || now.isBefore(sku.saleStartAt())
        || !now.isBefore(sku.saleEndAt())) throw new BusinessException("NOT_ON_SALE", 409);
  }

  OrderView insertOrder(String id, long user, TicketSku sku, Instant now, Instant deadline) {
    String no = UUID.randomUUID().toString();
    db.update(
        "INSERT INTO orders(order_no,reservation_id,user_id,event_id,sku_id,amount,status,expire_at,created_at) VALUES(?,?,?,?,?,?,'WAIT_PAY',?,?)",
        no,
        id,
        user,
        sku.eventId(),
        sku.id(),
        sku.price(),
        Timestamp.from(deadline),
        Timestamp.from(now));
    return orders.byNumber(no, false).orElseThrow();
  }

  public OrderView owned(String no, long user, boolean lock) {
    var order =
        orders.byNumber(no, lock).orElseThrow(() -> new BusinessException("ORDER_NOT_FOUND", 404));
    if (order.userId() != user) throw new BusinessException("ORDER_NOT_FOUND", 404);
    return order;
  }

  @Transactional
  public boolean closeExpired(String no) {
    var order = orders.byNumber(no, true);
    if (order.isEmpty()) return false;
    var o = order.get();
    if (db.update(
            "UPDATE orders SET status='CLOSED',closed_at=? WHERE order_no=? AND status='WAIT_PAY' AND expire_at<=?",
            Timestamp.from(clock.instant()),
            no,
            Timestamp.from(clock.instant()))
        != 1) return false;
    release(o);
    return true;
  }

  void release(OrderView o) {
    inventory.release(o.skuId());
    db.update(
        "UPDATE seckill_reservation SET status='CLOSED' WHERE reservation_id=? AND status='CONFIRMED'",
        o.reservationId());
    if ("ASYNC"
        .equals(
            db.queryForObject(
                "SELECT origin FROM seckill_reservation WHERE reservation_id=?",
                String.class,
                o.reservationId())))
      db.update(
          "INSERT IGNORE INTO stock_release_outbox(reservation_id,sku_id) VALUES(?,?)",
          o.reservationId(),
          o.skuId());
  }

  @Transactional
  public OrderView pay(long user, String no, String key) {
    var order = owned(no, user, true);
    var existing = db.queryForList("SELECT order_no FROM payment_record WHERE payment_key=?", key);
    if (!existing.isEmpty()) {
      if (existing.getFirst().get("order_no").equals(no)
          && (order.status() == OrderStatus.PAID || order.status() == OrderStatus.ISSUED))
        return order;
      throw new BusinessException("PAYMENT_KEY_CONFLICT", 409);
    }
    if (db.update(
            "UPDATE orders SET status='PAID',paid_at=? WHERE order_no=? AND status='WAIT_PAY' AND expire_at>?",
            Timestamp.from(clock.instant()),
            no,
            Timestamp.from(clock.instant()))
        != 1) throw new BusinessException("ORDER_NOT_PAYABLE", 409);
    db.update(
        "INSERT INTO payment_record(payment_key,order_no,amount,status) VALUES(?,?,?,'SUCCESS')",
        key,
        no,
        order.amount());
    return orders.byNumber(no, false).orElseThrow();
  }

  @Transactional
  public void issue(String no) {
    transition(no, "PAID", "ISSUED");
  }

  @Transactional
  public void refund(long user, String no) {
    owned(no, user, true);
    if (db.update(
            "UPDATE orders SET status='REFUNDING' WHERE order_no=? AND status IN ('PAID','ISSUED')",
            no)
        != 1) throw new BusinessException("INVALID_TRANSITION", 409);
  }

  @Transactional
  public void completeRefund(String no) {
    var o =
        orders.byNumber(no, true).orElseThrow(() -> new BusinessException("ORDER_NOT_FOUND", 404));
    transition(no, "REFUNDING", "REFUNDED");
    release(o);
  }

  private void transition(String no, String from, String to) {
    if (!OrderStatus.valueOf(from).canTransitionTo(OrderStatus.valueOf(to)))
      throw new IllegalArgumentException("Invalid transition");
    if (db.update("UPDATE orders SET status=? WHERE order_no=? AND status=?", to, no, from) != 1)
      throw new BusinessException("INVALID_TRANSITION", 409);
  }
}
