package com.astrea.flashflow.seckill;

import com.astrea.flashflow.inventory.InventoryWarmup;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
public class SeckillController {
  private final SeckillService service;
  private final InventoryWarmup warmup;

  public SeckillController(SeckillService service, InventoryWarmup warmup) {
    this.service = service;
    this.warmup = warmup;
  }

  @PostMapping("/api/v1/seckill/{skuId}")
  public ResponseEntity<SeckillService.Result> buy(
      @AuthenticationPrincipal Jwt jwt, @PathVariable long skuId) {
    return ResponseEntity.accepted().body(service.submit(Long.parseLong(jwt.getSubject()), skuId));
  }

  @GetMapping("/api/v1/seckill/result/{id}")
  public SeckillService.Result result(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
    return service.result(Long.parseLong(jwt.getSubject()), id);
  }

  @PostMapping("/api/v1/admin/skus/{id}/warm")
  public void warm(@PathVariable long id) {
    warmup.warm(id);
  }
}
