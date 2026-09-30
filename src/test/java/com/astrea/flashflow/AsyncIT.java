package com.astrea.flashflow;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;

import com.astrea.flashflow.inventory.InventoryWarmup;
import com.astrea.flashflow.messaging.*;
import com.astrea.flashflow.order.AsyncOrderService;
import com.astrea.flashflow.seckill.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class AsyncIT extends IntegrationSupport {
  @Autowired RedisReservations reservations;
  @Autowired InventoryWarmup warmup;
  @Autowired DeliveryHandler handler;
  @Autowired AsyncOrderService asynchronous;
  @Autowired OutboxRelay relay;
  @Autowired SeckillService seckill;
  @MockitoBean MessageBus bus;

  @Test
  void differentMessagesForOneSkuDoNotUpgradeForeignKeySharedLocks() throws Exception {
    users(40);
    long sku = sku(40);
    warmup.warm(sku);
    var calls = new ArrayList<Callable<Boolean>>();
    for (long user = 1; user <= 40; user++) {
      var command = reservations.reserve(user, sku, 120).command();
      calls.add(() -> handler.consume(command).created());
    }
    assertThat(concurrently(calls)).allMatch(Boolean::booleanValue).hasSize(40);
    assertThat(db.queryForObject("SELECT COUNT(*) FROM orders", Integer.class)).isEqualTo(40);
    assertThat(
            db.queryForObject(
                "SELECT available_stock FROM ticket_sku WHERE id=?", Integer.class, sku))
        .isZero();
    assertThat(reservations.stock(sku)).isZero();
  }

  @Test
  void duplicateDeliveryProducesOneOrderAndOneDeduction() throws Exception {
    users(1);
    long sku = sku(2);
    warmup.warm(sku);
    var r = reservations.reserve(1, sku, 120);
    var c = r.command();
    var calls = new ArrayList<Callable<Boolean>>();
    for (int i = 0; i < 20; i++) calls.add(() -> handler.consume(c).created());
    assertThat(concurrently(calls).stream().filter(Boolean::booleanValue).count()).isEqualTo(1);
    assertThat(db.queryForObject("SELECT COUNT(*) FROM orders", Integer.class)).isEqualTo(1);
    assertThat(
            db.queryForObject(
                "SELECT available_stock FROM ticket_sku WHERE id=?", Integer.class, sku))
        .isEqualTo(1);
    assertThat(reservations.stock(sku)).isEqualTo(1);
  }

  @Test
  void expiredCompensationFencesLateDelivery() {
    users(1);
    long sku = sku(1);
    warmup.warm(sku);
    var r = reservations.reserve(1, sku, 1);
    await()
        .atMost(Duration.ofSeconds(4))
        .until(() -> System.currentTimeMillis() > r.command().expiresAt());
    handler.reconcile(r.command());
    handler.consume(r.command());
    handler.reconcile(r.command());
    assertThat(db.queryForObject("SELECT COUNT(*) FROM orders", Integer.class)).isZero();
    assertThat(reservations.stock(sku)).isEqualTo(1);
    assertThat(
            db.queryForObject(
                "SELECT status FROM seckill_reservation WHERE reservation_id=?",
                String.class,
                r.reservationId()))
        .isEqualTo("ROLLED_BACK");
  }

  @Test
  void recoveryAfterCommitBeforeRedisConfirmation() {
    users(1);
    long sku = sku(1);
    warmup.warm(sku);
    var r = reservations.reserve(1, sku, 120);
    asynchronous.process(r.command());
    assertThat(reservations.get(r.reservationId()).get("status")).isEqualTo("RESERVED");
    handler.reconcile(r.command());
    assertThat(reservations.get(r.reservationId()).get("status")).isEqualTo("CONFIRMED");
    assertThat(reservations.stock(sku)).isZero();
  }

  @Test
  void databaseUniqueKeyRejectsBypassedRedisEligibility() {
    users(1);
    long sku = sku(2);
    warmup.warm(sku);
    var r = reservations.reserve(1, sku, 120);
    handler.consume(r.command());
    var c =
        new ReservationCommand(
            ReservationCommand.newId(sku),
            1,
            r.command().eventId(),
            sku,
            System.currentTimeMillis(),
            System.currentTimeMillis() + 120000);
    assertThat(asynchronous.process(c).action()).isEqualTo("ROLLBACK");
    assertThat(db.queryForObject("SELECT COUNT(*) FROM orders", Integer.class)).isEqualTo(1);
    assertThat(
            db.queryForObject(
                "SELECT available_stock FROM ticket_sku WHERE id=?", Integer.class, sku))
        .isEqualTo(1);
  }

  @Test
  void producerTimeoutDoesNotImmediatelyRollback() throws Exception {
    users(1);
    long sku = sku(1);
    warmup.warm(sku);
    doThrow(new java.io.IOException("simulated timeout after broker receipt"))
        .when(bus)
        .sendReservation(any());
    var r = seckill.submit(1, sku);
    assertThat(r.delivery()).isEqualTo("UNCERTAIN");
    assertThat(reservations.stock(sku)).isZero();
    handler.consume(reservations.command(r.reservationId()));
    assertThat(seckill.result(1, r.reservationId()).status()).isEqualTo("SUCCESS");
  }

  @Test
  void outboxFailureLeavesRetryableMessage() throws Exception {
    users(1);
    long sku = sku(1);
    warmup.warm(sku);
    handler.consume(reservations.reserve(1, sku, 120).command());
    doThrow(new java.io.IOException("mock failure"))
        .when(bus)
        .sendTimeout(anyString(), anyString(), anyInt());
    relay.messages();
    assertThat(db.queryForObject("SELECT status FROM message_outbox", String.class))
        .isEqualTo("PENDING");
    assertThat(db.queryForObject("SELECT attempts FROM message_outbox", Integer.class))
        .isEqualTo(1);
    reset(bus);
    relay.messages();
    assertThat(db.queryForObject("SELECT status FROM message_outbox", String.class))
        .isEqualTo("SENT");
  }

  @Test
  void closeBeforeRedisConfirmationStillReleasesExactlyOnce() {
    users(1);
    long sku = sku(1);
    warmup.warm(sku);
    var r = reservations.reserve(1, sku, 120);
    var order = asynchronous.process(r.command()).order();
    db.update("UPDATE orders SET expire_at=?", Timestamp.from(Instant.now().minusSeconds(1)));
    orders.closeExpired(order.orderNo());
    relay.releases();
    relay.releases();
    handler.consume(r.command());
    assertThat(reservations.stock(sku)).isEqualTo(1);
    assertThat(
            db.queryForObject(
                "SELECT available_stock FROM ticket_sku WHERE id=?", Integer.class, sku))
        .isEqualTo(1);
    assertThat(reservations.get(r.reservationId()).get("status")).isEqualTo("CLOSED");
  }

  @Test
  void resultIsPrivate() {
    users(2);
    long sku = sku(1);
    warmup.warm(sku);
    var r = seckill.submit(1, sku);
    assertThatThrownBy(() -> seckill.result(2, r.reservationId()))
        .hasMessage("RESERVATION_NOT_FOUND");
  }
}
