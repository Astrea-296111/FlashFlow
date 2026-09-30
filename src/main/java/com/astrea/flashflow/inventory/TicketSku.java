package com.astrea.flashflow.inventory;

import java.math.BigDecimal;
import java.time.Instant;

public record TicketSku(
    long id,
    long eventId,
    String tierName,
    BigDecimal price,
    int totalStock,
    int availableStock,
    String status,
    String mode,
    Instant saleStartAt,
    Instant saleEndAt,
    String eventStatus) {}
