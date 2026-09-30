package com.astrea.flashflow.messaging;

import com.astrea.flashflow.seckill.ReservationCommand;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "flashflow.messaging.enabled", havingValue = "false")
public class DisabledMessageBus implements MessageBus {
  @Override
  public void sendReservation(ReservationCommand c) {
    throw new IllegalStateException("RocketMQ disabled");
  }

  @Override
  public void sendTimeout(String json, String orderNo, int delayLevel) {
    throw new IllegalStateException("RocketMQ disabled");
  }
}
