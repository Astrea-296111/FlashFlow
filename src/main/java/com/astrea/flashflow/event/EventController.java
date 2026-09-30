package com.astrea.flashflow.event;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
public class EventController {
  private final EventService events;
  private final EventCache cache;

  public EventController(EventService events, EventCache cache) {
    this.events = events;
    this.cache = cache;
  }

  @GetMapping("/api/v1/events")
  public List<Map<String, Object>> list() {
    return events.list();
  }

  @GetMapping("/api/v1/events/{id}")
  public Map<String, Object> detail(@PathVariable long id) {
    return cache.detail(id);
  }

  @PostMapping("/api/v1/admin/events")
  public Map<String, Long> create(@Valid @RequestBody EventService.EventInput e) {
    return Map.of("eventId", events.create(e));
  }

  @PutMapping("/api/v1/admin/events/{id}")
  public void update(@PathVariable long id, @Valid @RequestBody EventService.EventInput e) {
    events.update(id, e);
    cache.invalidate(id);
  }

  @DeleteMapping("/api/v1/admin/events/{id}")
  public void archive(@PathVariable long id) {
    events.archive(id);
    cache.invalidate(id);
  }

  @PostMapping("/api/v1/admin/events/{id}/skus")
  public Map<String, Long> sku(@PathVariable long id, @Valid @RequestBody EventService.SkuInput s) {
    long sku = events.addSku(id, s);
    cache.invalidate(id);
    return Map.of("skuId", sku);
  }
}
