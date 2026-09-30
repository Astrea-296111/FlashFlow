package com.astrea.flashflow;

import static org.assertj.core.api.Assertions.*;

import com.astrea.flashflow.common.BusinessException;
import com.astrea.flashflow.order.OrderStatus;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

class BaselineIT extends IntegrationSupport {
  @Test
  void stockZeroRejectsWithoutOrder() {
    users(1);
    long sku = sku(0);
    assertThatThrownBy(() -> orders.baseline(1, sku)).hasMessage("SOLD_OUT");
    assertThat(db.queryForObject("SELECT COUNT(*) FROM orders", Integer.class)).isZero();
  }

  @Test
  void uniqueConstraintRollsBackInventoryDeduction() {
    users(1);
    long sku = sku(2);
    orders.baseline(1, sku);
    assertThatThrownBy(() -> orders.baseline(1, sku)).isInstanceOf(DuplicateKeyException.class);
    assertThat(
            db.queryForObject(
                "SELECT available_stock FROM ticket_sku WHERE id=?", Integer.class, sku))
        .isEqualTo(1);
    assertThat(db.queryForObject("SELECT COUNT(*) FROM orders", Integer.class)).isEqualTo(1);
  }

  @Test
  void concurrentBuyersCannotOversell() throws Exception {
    users(300);
    long sku = sku(40);
    var calls = new ArrayList<Callable<Boolean>>();
    for (long user = 1; user <= 300; user++) {
      long u = user;
      calls.add(
          () -> {
            try {
              orders.baseline(u, sku);
              return true;
            } catch (BusinessException e) {
              return false;
            }
          });
    }
    assertThat(concurrently(calls).stream().filter(Boolean::booleanValue).count()).isEqualTo(40);
    assertThat(
            db.queryForObject(
                "SELECT available_stock FROM ticket_sku WHERE id=?", Integer.class, sku))
        .isZero();
    assertThat(db.queryForObject("SELECT COUNT(*) FROM orders", Integer.class)).isEqualTo(40);
  }

  @Test
  void rejectsBeforeSale() {
    users(1);
    long sku = sku(1);
    db.update("UPDATE event SET sale_start_at=?", Timestamp.from(Instant.now().plusSeconds(60)));
    assertThatThrownBy(() -> orders.baseline(1, sku)).hasMessage("NOT_ON_SALE");
  }

  @Test
  void ownershipAndIdempotentPayment() {
    users(2);
    long sku = sku(1);
    var o = orders.baseline(1, sku);
    assertThatThrownBy(() -> orders.owned(o.orderNo(), 2, false)).hasMessage("ORDER_NOT_FOUND");
    assertThat(orders.pay(1, o.orderNo(), "payment-0001").status()).isEqualTo(OrderStatus.PAID);
    assertThat(orders.pay(1, o.orderNo(), "payment-0001").status()).isEqualTo(OrderStatus.PAID);
    assertThat(db.queryForObject("SELECT COUNT(*) FROM payment_record", Integer.class))
        .isEqualTo(1);
    assertThat(orders.closeExpired(o.orderNo())).isFalse();
  }

  @Test
  void paymentKeyCannotPayTwoOrders() {
    users(2);
    long sku = sku(2);
    var a = orders.baseline(1, sku);
    var b = orders.baseline(2, sku);
    orders.pay(1, a.orderNo(), "payment-shared");
    assertThatThrownBy(() -> orders.pay(2, b.orderNo(), "payment-shared"))
        .hasMessage("PAYMENT_KEY_CONFLICT");
    assertThat(orders.owned(b.orderNo(), 2, false).status()).isEqualTo(OrderStatus.WAIT_PAY);
  }

  @Test
  void duplicateCloseOnlyReleasesOnce() {
    users(1);
    long sku = sku(1);
    var o = orders.baseline(1, sku);
    db.update("UPDATE orders SET expire_at=?", Timestamp.from(Instant.now().minusSeconds(1)));
    assertThat(orders.closeExpired(o.orderNo())).isTrue();
    assertThat(orders.closeExpired(o.orderNo())).isFalse();
    assertThat(
            db.queryForObject(
                "SELECT available_stock FROM ticket_sku WHERE id=?", Integer.class, sku))
        .isEqualTo(1);
    assertThatThrownBy(() -> orders.pay(1, o.orderNo(), "late-payment"))
        .hasMessage("ORDER_NOT_PAYABLE");
  }

  @Test
  void issueAndRefundReleasesOnce() {
    users(1);
    long sku = sku(1);
    var o = orders.baseline(1, sku);
    orders.pay(1, o.orderNo(), "payment-refund");
    orders.issue(o.orderNo());
    orders.refund(1, o.orderNo());
    orders.completeRefund(o.orderNo());
    assertThat(orders.owned(o.orderNo(), 1, false).status()).isEqualTo(OrderStatus.REFUNDED);
    assertThatThrownBy(() -> orders.completeRefund(o.orderNo())).hasMessage("INVALID_TRANSITION");
    assertThat(
            db.queryForObject(
                "SELECT available_stock FROM ticket_sku WHERE id=?", Integer.class, sku))
        .isEqualTo(1);
  }
}
