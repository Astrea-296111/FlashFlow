package com.astrea.flashflow.seckill;

import java.util.UUID;

public record ReservationCommand(
    String reservationId, long userId, long eventId, long skuId, long createdAt, long expiresAt) {
  public static String newId(long skuId) {
    return skuId + "-" + UUID.randomUUID();
  }

  public static long skuFromId(String id) {
    if (id == null
        || !id.matches(
            "[1-9][0-9]{0,17}-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
      throw new IllegalArgumentException("Invalid reservation ID");
    return Long.parseLong(id.substring(0, id.indexOf('-')));
  }

  public void validate() {
    if (skuFromId(reservationId) != skuId
        || userId <= 0
        || eventId <= 0
        || createdAt <= 0
        || expiresAt <= createdAt
        || expiresAt - createdAt > 86400000)
      throw new IllegalArgumentException("Invalid reservation message");
  }
}
