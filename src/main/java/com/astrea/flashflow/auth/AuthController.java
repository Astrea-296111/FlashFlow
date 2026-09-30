package com.astrea.flashflow.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
  public record Credentials(
      @NotBlank @Pattern(regexp = "[a-zA-Z0-9_-]{3,64}") String username,
      @NotBlank @Size(min = 8, max = 72) String password) {}

  private final AuthService auth;

  public AuthController(AuthService auth) {
    this.auth = auth;
  }

  @PostMapping("/register")
  public Map<String, Object> register(@Valid @RequestBody Credentials c) {
    return Map.of("userId", auth.register(c.username(), c.password(), "USER").getId());
  }

  @PostMapping("/login")
  public Map<String, String> login(@Valid @RequestBody Credentials c) {
    return Map.of("token", auth.login(c.username(), c.password()));
  }
}
