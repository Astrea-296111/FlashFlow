package com.astrea.flashflow.messaging;

public final class DelayLevels {
  private static final long[] SECONDS = {
    1, 5, 10, 30, 60, 120, 180, 240, 300, 360, 420, 480, 540, 600, 1200, 1800, 3600, 7200
  };

  private DelayLevels() {}

  public static int forSeconds(long seconds) {
    for (int i = 0; i < SECONDS.length; i++) if (SECONDS[i] >= seconds) return i + 1;
    throw new IllegalArgumentException("Payment TTL exceeds classic RocketMQ delay levels");
  }
}
