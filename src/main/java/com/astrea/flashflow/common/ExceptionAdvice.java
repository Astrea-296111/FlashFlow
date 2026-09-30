package com.astrea.flashflow.common;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ExceptionAdvice {
  private static final Logger log = LoggerFactory.getLogger(ExceptionAdvice.class);

  @ExceptionHandler(BusinessException.class)
  ResponseEntity<?> business(BusinessException e) {
    return ResponseEntity.status(e.status()).body(Map.of("code", e.code()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<?> invalid(Exception e) {
    return ResponseEntity.badRequest().body(Map.of("code", "INVALID_ARGUMENT"));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<?> invalidValue(Exception e) {
    return ResponseEntity.badRequest().body(Map.of("code", "INVALID_ARGUMENT"));
  }

  @ExceptionHandler(DuplicateKeyException.class)
  ResponseEntity<?> duplicate(Exception e) {
    return ResponseEntity.status(409).body(Map.of("code", "DUPLICATE"));
  }

  @ExceptionHandler({DataAccessException.class, RedisConnectionFailureException.class})
  ResponseEntity<?> unavailable(Exception e) {
    log.warn("Dependency unavailable: {}", e.getClass().getSimpleName());
    return ResponseEntity.status(503).body(Map.of("code", "DEPENDENCY_UNAVAILABLE"));
  }
}
