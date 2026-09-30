package com.astrea.flashflow;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.astrea.flashflow.inventory.InventoryWarmup;
import com.astrea.flashflow.messaging.*;
import com.astrea.flashflow.seckill.*;
import java.io.IOException;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

class ProducerFailureIT extends IntegrationSupport {
  @Autowired SeckillService seckill;
  @Autowired InventoryWarmup warmup;
  @Autowired RedisReservations reservations;
  @Autowired DeliveryHandler handler;
  @MockitoBean MessageBus bus;
  @MockitoSpyBean Clock clock;

  @Test
  void failedSendWithoutDeliveryReleasesAfterDeadlineAndFencesLateMessage() throws Exception {
    users(1);
    long sku = sku(1);
    warmup.warm(sku);
    doThrow(new IOException("deterministic failed send with no broker delivery"))
        .when(bus)
        .sendReservation(any());
    var result = seckill.submit(1, sku);
    var command = reservations.command(result.reservationId());
    assertThat(result.delivery()).isEqualTo("UNCERTAIN");
    assertThat(reservations.stock(sku)).isZero();
    handler.reconcile(command);
    assertThat(reservations.stock(sku)).isZero();
    // Logical-clock fault injection avoids waiting 120 seconds in the test suite.
    doReturn(command.expiresAt() + 1).when(clock).millis();
    handler.reconcile(command);
    handler.reconcile(command);
    handler.consume(command);
    assertThat(reservations.stock(sku)).isEqualTo(1);
    assertThat(db.queryForObject("SELECT COUNT(*) FROM orders", Integer.class)).isZero();
    assertThat(seckill.result(1, result.reservationId()).status()).isEqualTo("FAILED");
    assertThat(reservations.reserve(1, sku, 120).code()).isEqualTo("RESERVED");
  }
}
