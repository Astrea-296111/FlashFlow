package com.astrea.flashflow.order;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OrderStatusTest {
  @Test
  void closedCannotBePaidOrClosedAgain() {
    for (var next : OrderStatus.values())
      assertThat(OrderStatus.CLOSED.canTransitionTo(next)).isFalse();
  }

  @Test
  void paidCannotBeClosed() {
    assertThat(OrderStatus.PAID.canTransitionTo(OrderStatus.CLOSED)).isFalse();
  }

  @Test
  void permitsFulfillmentAndRefund() {
    assertThat(OrderStatus.WAIT_PAY.canTransitionTo(OrderStatus.PAID)).isTrue();
    assertThat(OrderStatus.PAID.canTransitionTo(OrderStatus.ISSUED)).isTrue();
    assertThat(OrderStatus.ISSUED.canTransitionTo(OrderStatus.REFUNDING)).isTrue();
    assertThat(OrderStatus.REFUNDING.canTransitionTo(OrderStatus.REFUNDED)).isTrue();
  }
}
