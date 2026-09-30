package com.astrea.flashflow.payment;

import com.astrea.flashflow.order.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
public class OrderController {
  public record Payment(@NotBlank @Pattern(regexp = "[a-zA-Z0-9_-]{8,64}") String paymentKey) {}

  private final OrderService service;
  private final OrderRepository orders;

  public OrderController(OrderService service, OrderRepository orders) {
    this.service = service;
    this.orders = orders;
  }

  @GetMapping("/api/v1/orders")
  public List<OrderView> mine(@AuthenticationPrincipal Jwt jwt) {
    return orders.forUser(Long.parseLong(jwt.getSubject()));
  }

  @GetMapping("/api/v1/orders/{no}")
  public OrderView get(@AuthenticationPrincipal Jwt jwt, @PathVariable String no) {
    return service.owned(no, Long.parseLong(jwt.getSubject()), false);
  }

  @PostMapping("/api/v1/orders/{no}/pay")
  public OrderView pay(
      @AuthenticationPrincipal Jwt jwt, @PathVariable String no, @Valid @RequestBody Payment p) {
    return service.pay(Long.parseLong(jwt.getSubject()), no, p.paymentKey());
  }

  @PostMapping("/api/v1/orders/{no}/refund")
  public void refund(@AuthenticationPrincipal Jwt jwt, @PathVariable String no) {
    service.refund(Long.parseLong(jwt.getSubject()), no);
  }

  @PostMapping("/api/v1/admin/orders/{no}/issue")
  public void issue(@PathVariable String no) {
    service.issue(no);
  }

  @PostMapping("/api/v1/admin/orders/{no}/refund-complete")
  public void refundComplete(@PathVariable String no) {
    service.completeRefund(no);
  }
}
