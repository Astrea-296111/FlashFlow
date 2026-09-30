package com.astrea.flashflow.seckill;

import com.astrea.flashflow.order.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
public class BaselineController {
  private final OrderService orders;

  public BaselineController(OrderService orders) {
    this.orders = orders;
  }

  @PostMapping("/api/v1/seckill/baseline/{skuId}")
  public OrderView buy(@AuthenticationPrincipal Jwt jwt, @PathVariable long skuId) {
    return orders.baseline(Long.parseLong(jwt.getSubject()), skuId);
  }
}
