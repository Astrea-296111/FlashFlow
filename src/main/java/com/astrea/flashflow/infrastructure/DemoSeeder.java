package com.astrea.flashflow.infrastructure;

import com.astrea.flashflow.auth.AuthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "flashflow.demo", havingValue = "true")
public class DemoSeeder implements CommandLineRunner {
  private final AuthService auth;
  private final JdbcTemplate db;
  private final String password;

  public DemoSeeder(
      AuthService auth,
      JdbcTemplate db,
      @Value("${flashflow.demo-admin-password}") String password) {
    this.auth = auth;
    this.db = db;
    this.password = password;
  }

  @Override
  public void run(String... args) {
    if (password.length() < 8)
      throw new IllegalArgumentException("DEMO_ADMIN_PASSWORD must have 8+ characters");
    if (db.queryForObject("SELECT COUNT(*) FROM app_user WHERE username='admin'", Integer.class)
        == 0) {
      try {
        auth.register("admin", password, "ADMIN");
      } catch (org.springframework.dao.DuplicateKeyException alreadySeeded) {
        /* concurrent role startup */
      }
    }
  }
}
