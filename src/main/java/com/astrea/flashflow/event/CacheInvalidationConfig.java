package com.astrea.flashflow.event;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import org.springframework.context.annotation.*;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.*;

@Configuration
public class CacheInvalidationConfig {
  @Bean(destroyMethod = "shutdown")
  ThreadPoolExecutor cacheInvalidationExecutor() {
    return new ThreadPoolExecutor(
        2,
        4,
        60,
        TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(256),
        Thread.ofPlatform().name("cache-invalidate-", 0).factory(),
        new ThreadPoolExecutor.CallerRunsPolicy());
  }

  @Bean
  RedisMessageListenerContainer invalidations(
      RedisConnectionFactory connection,
      EventCache cache,
      ThreadPoolExecutor cacheInvalidationExecutor) {
    var container = new RedisMessageListenerContainer();
    container.setConnectionFactory(connection);
    container.setTaskExecutor(cacheInvalidationExecutor);
    container.addMessageListener(
        (message, pattern) -> {
          try {
            cache.evictLocal(Long.parseLong(new String(message.getBody(), StandardCharsets.UTF_8)));
          } catch (NumberFormatException ignored) {
            /* invalid control message */
          }
        },
        new ChannelTopic(EventCache.CHANNEL));
    return container;
  }
}
