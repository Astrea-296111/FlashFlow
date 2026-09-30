package com.astrea.flashflow.order;

public enum OrderStatus {
  WAIT_PAY,
  PAID,
  ISSUED,
  CLOSED,
  REFUNDING,
  REFUNDED;

  public boolean canTransitionTo(OrderStatus next) {
    return switch (this) {
      case WAIT_PAY -> next == PAID || next == CLOSED;
      case PAID -> next == ISSUED || next == REFUNDING;
      case ISSUED -> next == REFUNDING;
      case REFUNDING -> next == REFUNDED;
      case CLOSED, REFUNDED -> false;
    };
  }
}
