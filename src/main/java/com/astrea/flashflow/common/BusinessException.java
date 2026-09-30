package com.astrea.flashflow.common;

public class BusinessException extends RuntimeException {
  private final String code;
  private final int status;

  public BusinessException(String code, int status) {
    super(code);
    this.code = code;
    this.status = status;
  }

  public String code() {
    return code;
  }

  public int status() {
    return status;
  }
}
