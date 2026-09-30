package com.astrea.flashflow.user;

import com.baomidou.mybatisplus.annotation.*;

@TableName("app_user")
public class UserEntity {
  @TableId(type = IdType.AUTO)
  private Long id;

  private String username;
  private String passwordHash;
  private String role;
  private String status;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public void setPasswordHash(String hash) {
    this.passwordHash = hash;
  }

  public String getRole() {
    return role;
  }

  public void setRole(String role) {
    this.role = role;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }
}
