package com.astrea.flashflow.auth;

import com.astrea.flashflow.common.BusinessException;
import com.astrea.flashflow.user.*;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.time.Clock;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
  private final UserMapper users;
  private final PasswordEncoder passwords;
  private final JwtEncoder encoder;
  private final Clock clock;

  public AuthService(UserMapper users, PasswordEncoder passwords, JwtEncoder encoder, Clock clock) {
    this.users = users;
    this.passwords = passwords;
    this.encoder = encoder;
    this.clock = clock;
  }

  public UserEntity register(String username, String password, String role) {
    var u = new UserEntity();
    u.setUsername(username);
    u.setPasswordHash(passwords.encode(password));
    u.setRole(role);
    u.setStatus("ACTIVE");
    users.insert(u);
    return u;
  }

  public String login(String username, String password) {
    var u = users.selectOne(new QueryWrapper<UserEntity>().eq("username", username));
    if (u == null
        || !"ACTIVE".equals(u.getStatus())
        || !passwords.matches(password, u.getPasswordHash()))
      throw new BusinessException("BAD_CREDENTIALS", 401);
    return token(u.getId(), u.getRole());
  }

  public String token(long userId, String role) {
    var now = clock.instant();
    var claims =
        JwtClaimsSet.builder()
            .issuer("flashflow")
            .subject(Long.toString(userId))
            .issuedAt(now)
            .expiresAt(now.plusSeconds(7200))
            .claim("roles", List.of(role))
            .build();
    return encoder
        .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
        .getTokenValue();
  }
}
