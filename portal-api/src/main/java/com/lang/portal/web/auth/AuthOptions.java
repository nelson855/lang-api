package com.lang.portal.web.auth;

public record AuthOptions(
    boolean registrationEnabled,
    String registrationDisabledReason,
    boolean emailVerificationEnabled,
    boolean captchaEnabled) {}
