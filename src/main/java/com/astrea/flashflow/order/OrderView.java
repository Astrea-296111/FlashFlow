package com.astrea.flashflow.order;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderView(
    String orderNo,
    String reservationId,
    long userId,
    long eventId,
    long skuId,
    BigDecimal amount,
    OrderStatus status,
    Instant expireAt,
    Instant createdAt) {}
