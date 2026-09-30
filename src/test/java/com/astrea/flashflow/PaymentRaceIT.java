package com.astrea.flashflow;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

import com.astrea.flashflow.common.BusinessException;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.RepeatedTest;

class PaymentRaceIT extends IntegrationSupport {
  @RepeatedTest(3)
  void paymentAndTimeoutPreserveOneWinnerAndStockConservation() throws Exception {
    users(20);
    long sku = sku(20);
    var numbers = new ArrayList<String>();
    for (long u = 1; u <= 20; u++) numbers.add(orders.baseline(u, sku).orderNo());
    var deadline = Instant.now().plusMillis(150);
    db.update("UPDATE orders SET expire_at=?", Timestamp.from(deadline));
    var calls = new ArrayList<Callable<Boolean>>();
    for (int i = 0; i < numbers.size(); i++) {
      String no = numbers.get(i);
      long user = i + 1;
      int delay = i % 3 == 0 ? 0 : i % 3 == 1 ? 190 : 150;
      calls.add(
          () -> {
            try {
              if (delay > 0) Thread.sleep(delay);
              orders.pay(user, no, "race-" + no);
              return true;
            } catch (BusinessException expected) {
              return false;
            }
          });
      calls.add(
          () -> {
            while (Instant.now().isBefore(deadline)) Thread.sleep(1);
            return orders.closeExpired(no);
          });
    }
    concurrently(calls);
    await().atMost(Duration.ofSeconds(2)).until(() -> Instant.now().isAfter(deadline));
    for (String no : numbers) orders.closeExpired(no);
    int paid = db.queryForObject("SELECT COUNT(*) FROM orders WHERE status='PAID'", Integer.class);
    int closed =
        db.queryForObject("SELECT COUNT(*) FROM orders WHERE status='CLOSED'", Integer.class);
    assertThat(paid + closed).isEqualTo(20);
    assertThat(paid).isPositive();
    assertThat(closed).isPositive();
    assertThat(
            db.queryForObject(
                "SELECT available_stock FROM ticket_sku WHERE id=?", Integer.class, sku))
        .isEqualTo(closed);
    assertThat(db.queryForObject("SELECT COUNT(*) FROM payment_record", Integer.class))
        .isEqualTo(paid);
    System.out.println("Payment/timeout race: paid=" + paid + ", closed=" + closed + ", total=20");
  }
}
