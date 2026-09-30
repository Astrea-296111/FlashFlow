package com.astrea.flashflow.messaging;

import com.astrea.flashflow.order.OrderService;
import com.astrea.flashflow.seckill.ReservationCommand;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.security.MessageDigest;
import java.util.*;
import java.util.HexFormat;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.*;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = "flashflow.messaging.enabled",
    havingValue = "true",
    matchIfMissing = true)
@ConditionalOnExpression("'${flashflow.role}' == 'worker' or '${flashflow.role}' == 'all'")
public class RocketConsumers {
  private static final Logger log = LoggerFactory.getLogger(RocketConsumers.class);
  private final ObjectMapper json;
  private final DeliveryHandler handler;
  private final OrderService orders;
  private final JdbcTemplate db;
  private final DefaultMQPushConsumer consumer;
  private final DefaultMQPushConsumer timeout;
  private final String orderTopic;
  private final String timeoutTopic;

  public RocketConsumers(
      ObjectMapper json,
      DeliveryHandler handler,
      OrderService orders,
      JdbcTemplate db,
      @Value("${flashflow.messaging.nameserver}") String nameserver,
      @Value("${flashflow.messaging.consumer-group}") String group,
      @Value("${flashflow.messaging.timeout-group}") String timeoutGroup,
      @Value("${flashflow.messaging.order-topic}") String orderTopic,
      @Value("${flashflow.messaging.timeout-topic}") String timeoutTopic) {
    this.json = json;
    this.handler = handler;
    this.orders = orders;
    this.db = db;
    this.orderTopic = orderTopic;
    this.timeoutTopic = timeoutTopic;
    consumer = create(group, nameserver);
    timeout = create(timeoutGroup, nameserver);
  }

  private DefaultMQPushConsumer create(String group, String nameserver) {
    var c = new DefaultMQPushConsumer(group);
    c.setNamesrvAddr(nameserver);
    c.setInstanceName(group + "-" + ProcessHandle.current().pid());
    c.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);
    c.setConsumeThreadMin(4);
    c.setConsumeThreadMax(4);
    c.setConsumeMessageBatchMaxSize(1);
    c.setMaxReconsumeTimes(3);
    return c;
  }

  @PostConstruct
  public void start() throws Exception {
    consumer.subscribe(orderTopic, "*");
    timeout.subscribe(timeoutTopic, "*");
    consumer.registerMessageListener(
        (MessageListenerConcurrently) (messages, context) -> consume(messages, false));
    timeout.registerMessageListener(
        (MessageListenerConcurrently) (messages, context) -> consume(messages, true));
    consumer.start();
    timeout.start();
  }

  private ConsumeConcurrentlyStatus consume(List<MessageExt> messages, boolean isTimeout) {
    try {
      for (var message : messages) {
        try {
          if (message.getBody().length > 4096)
            throw new IllegalArgumentException("Message too large");
          if (isTimeout) {
            var c = json.readValue(message.getBody(), TimeoutCommand.class);
            if (c == null) throw new IllegalArgumentException("Missing timeout command");
            c.validate();
            orders.closeExpired(c.orderNo());
          } else {
            var c = json.readValue(message.getBody(), ReservationCommand.class);
            if (c == null) throw new IllegalArgumentException("Missing reservation command");
            c.validate();
            handler.consume(c);
          }
        } catch (JsonProcessingException | IllegalArgumentException malformed) {
          var digest =
              HexFormat.of()
                  .formatHex(MessageDigest.getInstance("SHA-256").digest(message.getBody()));
          db.update(
              "INSERT IGNORE INTO message_quarantine(message_hash,topic,reason) VALUES(?,?,?)",
              digest,
              message.getTopic(),
              "Invalid message schema or identity");
          log.warn("Quarantined malformed message {}", message.getMsgId());
        }
      }
      return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
    } catch (Exception transientFailure) {
      log.warn("Message processing failed: {}", transientFailure.getClass().getSimpleName());
      return ConsumeConcurrentlyStatus.RECONSUME_LATER;
    }
  }

  @PreDestroy
  public void stop() {
    consumer.shutdown();
    timeout.shutdown();
  }
}
