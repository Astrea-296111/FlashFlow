package com.astrea.flashflow;

import com.astrea.flashflow.event.EventService;
import com.astrea.flashflow.order.OrderService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class IntegrationSupport {
  static final boolean EXTERNAL = Boolean.parseBoolean(System.getenv("FLASHFLOW_IT_EXTERNAL"));
  static MySQLContainer<?> mysql;
  static GenericContainer<?> redis;

  static {
    if (!EXTERNAL) {
      mysql = new MySQLContainer<>("mysql:8.4.9").withDatabaseName("flashflow_it");
      mysql.start();
      redis = new GenericContainer<>(DockerImageName.parse("redis:7.4.2")).withExposedPorts(6379);
      redis.start();
    }
  }

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add(
        "spring.datasource.url",
        () ->
            EXTERNAL
                ? "jdbc:mysql://localhost:3306/flashflow_it?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true"
                : mysql.getJdbcUrl());
    r.add("spring.datasource.username", () -> EXTERNAL ? "flashflow" : mysql.getUsername());
    r.add(
        "spring.datasource.password",
        () -> EXTERNAL ? "local-development-only" : mysql.getPassword());
    r.add("spring.data.redis.host", () -> EXTERNAL ? "localhost" : redis.getHost());
    r.add("spring.data.redis.port", () -> EXTERNAL ? 6379 : redis.getMappedPort(6379));
    r.add("spring.data.redis.database", () -> 1);
    r.add("flashflow.jwt-secret", () -> "test-only-32-byte-secret-not-for-deployment");
    r.add("flashflow.messaging.enabled", () -> false);
    r.add("flashflow.scheduling-enabled", () -> false);
    r.add("flashflow.rate-limit-enabled", () -> false);
  }

  @Autowired protected JdbcTemplate db;
  @Autowired protected EventService events;
  @Autowired protected OrderService orders;
  @Autowired protected StringRedisTemplate testRedis;

  @BeforeEach
  void clean() {
    // The dedicated test Redis database is isolated from the demo/benchmark DB 0.
    try (var connection = testRedis.getConnectionFactory().getConnection()) {
      connection.serverCommands().flushDb();
    }
    for (String table :
        List.of(
            "message_quarantine",
            "stock_release_outbox",
            "message_outbox",
            "payment_record",
            "orders",
            "seckill_reservation",
            "ticket_sku",
            "event",
            "app_user")) db.update("DELETE FROM " + table);
  }

  protected void users(int n) {
    List<Object[]> batch = new ArrayList<>();
    for (int i = 1; i <= n; i++) batch.add(new Object[] {i, "user" + i, "unused", "USER"});
    db.batchUpdate("INSERT INTO app_user(id,username,password_hash,role) VALUES(?,?,?,?)", batch);
  }

  protected long sku(int stock) {
    long event =
        events.create(
            new EventService.EventInput(
                "FlashFlow Live",
                "Shanghai Arena",
                Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(3600)));
    return events.addSku(event, new EventService.SkuInput("Floor", new BigDecimal("99.00"), stock));
  }

  protected <T> List<T> concurrently(List<Callable<T>> calls) throws Exception {
    var pool =
        new ThreadPoolExecutor(
            24,
            24,
            0,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(2000),
            new ThreadPoolExecutor.AbortPolicy());
    var gate = new CountDownLatch(1);
    List<Future<T>> futures = new ArrayList<>();
    try {
      for (var call : calls)
        futures.add(
            pool.submit(
                () -> {
                  gate.await();
                  return call.call();
                }));
      gate.countDown();
      List<T> result = new ArrayList<>();
      for (var f : futures) result.add(f.get(45, TimeUnit.SECONDS));
      return result;
    } finally {
      pool.shutdownNow();
      pool.awaitTermination(5, TimeUnit.SECONDS);
    }
  }
}
