package com.lang.portal.upstream.newapi.profile;

public record ProfileUpdateCommand(
    String username, String displayName, String currentPassword, String newPassword) {

  public ProfileUpdateCommand {
    if (username == null || username.isBlank() || currentPassword == null || currentPassword.isBlank()) {
      throw new IllegalArgumentException("用户名与当前密码不能为空");
    }
  }

  @Override
  public String toString() {
    return "ProfileUpdateCommand[username=***, displayName=***, currentPassword=***, newPassword=***]";
  }
}
