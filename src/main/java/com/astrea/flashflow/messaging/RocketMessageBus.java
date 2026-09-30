package com.astrea.flashflow.messaging;

import com.astrea.flashflow.seckill.ReservationCommand;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import org.apache.rocketmq.client.producer.*;
import org.apache.rocketmq.common.message.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = "flashflow.messaging.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class RocketMessageBus implements MessageBus {
  private final DefaultMQProducer producer;
  private final ObjectMapper json;
  private final String orderTopic;
  private final String timeoutTopic;

  public RocketMessageBus(
      ObjectMapper json,
      @Value("${flashflow.messaging.nameserver}") String nameserver,
      @Value("${flashflow.role}") String role,
      @Value("${flashflow.messaging.order-topic}") String orderTopic,
      @Value("${flashflow.messaging.timeout-topic}") String timeoutTopic) {
    this.json = json;
    this.orderTopic = orderTopic;
    this.timeoutTopic = timeoutTopic;
    producer = new DefaultMQProducer("flashflow-producer-" + role);
    producer.setNamesrvAddr(nameserver);
    producer.setInstanceName("flashflow-" + role + "-" + ProcessHandle.current().pid());
    producer.setSendMsgTimeout(2000);
    producer.setRetryTimesWhenSendFailed(1);
    producer.setRetryAnotherBrokerWhenNotStoreOK(false);
  }

  @PostConstruct
  public void start() throws Exception {
    producer.start();
  }

  @PreDestroy
  public void stop() {
    producer.shutdown();
  }

  @Override
  public void sendReservation(ReservationCommand command) throws Exception {
    send(
        new Message(
            orderTopic, "RESERVE", command.reservationId(), json.writeValueAsBytes(command)));
  }

  @Override
  public void sendTimeout(String payload, String orderNo, int delayLevel) throws Exception {
    var message =
        new Message(
            timeoutTopic, "ORDER_TIMEOUT", orderNo, payload.getBytes(StandardCharsets.UTF_8));
    message.setDelayTimeLevel(delayLevel);
    send(message);
  }

  private void send(Message message) throws Exception {
    var result = producer.send(message);
    if (result.getSendStatus() != SendStatus.SEND_OK)
      throw new IllegalStateException("Uncertain send status: " + result.getSendStatus());
  }
}
