package com.lang.portal.base.security;

public record PortalAuthenticatedUser(long id, String username, String displayName, String email) {
  @Override
  public String toString() {
    return username;
  }
}
