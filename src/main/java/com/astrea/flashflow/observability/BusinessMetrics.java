package com.astrea.flashflow.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class BusinessMetrics {
  private final MeterRegistry registry;
  private final Timer completion;

  public BusinessMetrics(MeterRegistry registry) {
    this.registry = registry;
    completion =
        Timer.builder("flashflow.order.completion").publishPercentileHistogram().register(registry);
  }

  public void request(String outcome) {
    registry.counter("flashflow.seckill", "outcome", outcome).increment();
  }

  public void increment(String metric) {
    registry.counter("flashflow." + metric).increment();
  }

  public void completed(long millis) {
    completion.record(Duration.ofMillis(Math.max(0, millis)));
  }
}
