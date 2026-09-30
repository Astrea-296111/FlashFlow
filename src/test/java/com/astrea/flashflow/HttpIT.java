package com.astrea.flashflow;

import static org.assertj.core.api.Assertions.*;

import com.astrea.flashflow.auth.AuthService;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

class HttpIT extends IntegrationSupport {
  @Autowired TestRestTemplate http;
  @Autowired AuthService auth;

  ResponseEntity<Map> post(String path, Object data, String token) {
    var h = new HttpHeaders();
    h.setContentType(MediaType.APPLICATION_JSON);
    if (token != null) h.setBearerAuth(token);
    return http.exchange(path, HttpMethod.POST, new HttpEntity<>(data, h), Map.class);
  }

  @Test
  void anonymousCannotBuy() {
    long sku = sku(1);
    assertThat(post("/api/v1/seckill/baseline/" + sku, null, null).getStatusCode().value())
        .isEqualTo(401);
  }

  @Test
  void usersCannotCreateEvents() {
    users(1);
    assertThat(
            post("/api/v1/admin/events", Map.of(), auth.token(1, "USER")).getStatusCode().value())
        .isEqualTo(403);
  }

  @Test
  void nullCredentialsRejectCleanly() {
    assertThat(post("/api/v1/auth/login", Map.of(), null).getStatusCode().value()).isEqualTo(400);
  }

  @Test
  void registrationCannotEscalateRole() {
    assertThat(
            post(
                    "/api/v1/auth/register",
                    Map.of("username", "new-user", "password", "password-123", "role", "ADMIN"),
                    null)
                .getStatusCode()
                .is2xxSuccessful())
        .isTrue();
    assertThat(
            db.queryForObject("SELECT role FROM app_user WHERE username='new-user'", String.class))
        .isEqualTo("USER");
    assertThat(
            post(
                    "/api/v1/auth/login",
                    Map.of("username", "new-user", "password", "password-123"),
                    null)
                .getStatusCode()
                .is2xxSuccessful())
        .isTrue();
  }

  @Test
  void badPasswordRejects() {
    auth.register("sample-user", "password-123", "USER");
    assertThat(
            post(
                    "/api/v1/auth/login",
                    Map.of("username", "sample-user", "password", "bad-password"),
                    null)
                .getStatusCode()
                .value())
        .isEqualTo(401);
  }

  @Test
  void invalidJwtSignatureRejects() {
    long sku = sku(1);
    assertThat(
            post("/api/v1/seckill/baseline/" + sku, null, "invalid.token.signature")
                .getStatusCode()
                .value())
        .isEqualTo(401);
  }

  @Test
  void cacheInvalidatesAfterCommittedAdminUpdate() {
    auth.register("admin-test", "password-123", "ADMIN");
    long id =
        events.create(
            new com.astrea.flashflow.event.EventService.EventInput(
                "Before", "Venue", Instant.now().minusSeconds(20), Instant.now().plusSeconds(600)));
    assertThat(http.getForObject("/api/v1/events/" + id, Map.class).toString()).contains("Before");
    var headers = new HttpHeaders();
    headers.setBearerAuth(auth.login("admin-test", "password-123"));
    headers.setContentType(MediaType.APPLICATION_JSON);
    var input =
        Map.of(
            "name",
            "After",
            "venue",
            "Venue",
            "saleStartAt",
            Instant.now().minusSeconds(20).toString(),
            "saleEndAt",
            Instant.now().plusSeconds(600).toString());
    assertThat(
            http.exchange(
                    "/api/v1/admin/events/" + id,
                    HttpMethod.PUT,
                    new HttpEntity<>(input, headers),
                    String.class)
                .getStatusCode()
                .is2xxSuccessful())
        .isTrue();
    assertThat(http.getForObject("/api/v1/events/" + id, Map.class).toString()).contains("After");
  }

  @Test
  void nonexistentEventIsNegativelyCached() {
    assertThat(http.getForEntity("/api/v1/events/999999999", Map.class).getStatusCode().value())
        .isEqualTo(404);
    assertThat(http.getForEntity("/api/v1/events/999999999", Map.class).getStatusCode().value())
        .isEqualTo(404);
  }
}
