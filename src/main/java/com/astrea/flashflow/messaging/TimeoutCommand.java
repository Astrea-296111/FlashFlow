package com.astrea.flashflow.messaging;

import java.util.UUID;

public record TimeoutCommand(String orderNo, long expiresAt) {
  public void validate() {
    if (expiresAt <= 0 || !UUID.fromString(orderNo).toString().equals(orderNo))
      throw new IllegalArgumentException("Invalid timeout message");
  }
}
