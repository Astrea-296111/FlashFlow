package com.astrea.flashflow;

import static org.assertj.core.api.Assertions.*;

import com.astrea.flashflow.inventory.InventoryWarmup;
import com.astrea.flashflow.seckill.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

class RedisIT extends IntegrationSupport {
  @Autowired RedisReservations reservations;
  @Autowired InventoryWarmup warmup;
  @Autowired StringRedisTemplate redis;
  @Autowired TrafficLimiter limiter;

  @Test
  void duplicateReturnsOriginalReservationWithoutDeductingAgain() {
    users(1);
    long sku = sku(2);
    warmup.warm(sku);
    var a = reservations.reserve(1, sku, 120);
    var b = reservations.reserve(1, sku, 120);
    assertThat(a.code()).isEqualTo("RESERVED");
    assertThat(b.code()).isEqualTo("DUPLICATE");
    assertThat(b.reservationId()).isEqualTo(a.reservationId());
    assertThat(reservations.stock(sku)).isEqualTo(1);
  }

  @Test
  void zeroStockAndUninitializedFailClosed() {
    users(1);
    long sku = sku(0);
    assertThat(reservations.reserve(1, sku, 120).code()).isEqualTo("NOT_INITIALIZED");
    warmup.warm(sku);
    assertThat(reservations.reserve(1, sku, 120).code()).isEqualTo("SOLD_OUT");
  }

  @Test
  void saleWindowIsCheckedInsideLua() {
    users(1);
    long sku = sku(1);
    db.update("UPDATE event SET sale_start_at=?", Timestamp.from(Instant.now().plusSeconds(60)));
    warmup.warm(sku);
    assertThat(reservations.reserve(1, sku, 120).code()).isEqualTo("NOT_ON_SALE");
    assertThat(reservations.stock(sku)).isEqualTo(1);
  }

  @Test
  void concurrentReservationCannotOversell() throws Exception {
    users(200);
    long sku = sku(30);
    warmup.warm(sku);
    var calls = new ArrayList<Callable<String>>();
    for (long u = 1; u <= 200; u++) {
      long user = u;
      calls.add(() -> reservations.reserve(user, sku, 120).code());
    }
    assertThat(concurrently(calls).stream().filter("RESERVED"::equals).count()).isEqualTo(30);
    assertThat(reservations.stock(sku)).isZero();
  }

  @Test
  void rollbackIsIdempotentAndAllowsRetry() {
    users(1);
    long sku = sku(1);
    warmup.warm(sku);
    var r = reservations.reserve(1, sku, 120);
    assertThat(reservations.transition(r.reservationId(), "ROLLBACK")).isEqualTo(1);
    assertThat(reservations.transition(r.reservationId(), "ROLLBACK")).isZero();
    assertThat(reservations.stock(sku)).isEqualTo(1);
    assertThat(reservations.reserve(1, sku, 120).code()).isEqualTo("RESERVED");
  }

  @Test
  void wrongKeyTypeCannotPartiallyDeductStock() {
    users(1);
    long sku = sku(1);
    warmup.warm(sku);
    redis.opsForValue().set(RedisReservations.prefix(sku) + "deadline", "broken");
    assertThat(reservations.reserve(1, sku, 120).code()).isEqualTo("CORRUPT_STATE");
    assertThat(reservations.stock(sku)).isEqualTo(1);
  }

  @Test
  void warmingAgainDoesNotResetLiveStock() {
    users(1);
    long sku = sku(2);
    warmup.warm(sku);
    reservations.reserve(1, sku, 120);
    warmup.warm(sku);
    assertThat(reservations.stock(sku)).isEqualTo(1);
    assertThatThrownBy(() -> orders.baseline(1, sku)).hasMessage("SKU_MODE_CONFLICT");
  }

  @Test
  void lostLiveRedisInventoryCannotBeRewarmed() {
    users(1);
    long sku = sku(1);
    warmup.warm(sku);
    redis.delete(RedisReservations.prefix(sku) + "stock");
    assertThatThrownBy(() -> warmup.warm(sku)).hasMessage("LIVE_REWARM_FORBIDDEN");
  }

  @Test
  void staleRedisStateFromAnotherDatabaseGenerationCannotInitializeNewSku() {
    users(1);
    long sku = sku(100);
    redis.opsForValue().set(RedisReservations.prefix(sku) + "stock", "1");
    redis.opsForHash().put(RedisReservations.prefix(sku) + "metadata", "eventId", "999999");
    assertThatThrownBy(() -> warmup.warm(sku)).hasMessage("REDIS_STATE_CORRUPT");
    assertThat(db.queryForObject("SELECT mode FROM ticket_sku WHERE id=?", String.class, sku))
        .isEqualTo("BASELINE");
    assertThat(reservations.stock(sku)).isEqualTo(1);
  }

  @Test
  void burstBucketRejectsExcess() {
    int accepted = 0;
    long key = 999999999;
    redis.delete(RedisReservations.prefix(key) + "bucket");
    for (int i = 0; i < 40; i++) if (limiter.allow(key, 1, 5)) accepted++;
    assertThat(accepted).isEqualTo(5);
  }
}
