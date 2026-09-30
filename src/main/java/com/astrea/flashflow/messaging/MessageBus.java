package com.astrea.flashflow.messaging;

import com.astrea.flashflow.seckill.ReservationCommand;

public interface MessageBus {
  void sendReservation(ReservationCommand command) throws Exception;

  void sendTimeout(String json, String orderNo, int delayLevel) throws Exception;
}
