package com.lang.portal.web.auth;

public record AuthOptions(boolean registrationEnabled, boolean emailVerificationEnabled, boolean captchaEnabled) {}
