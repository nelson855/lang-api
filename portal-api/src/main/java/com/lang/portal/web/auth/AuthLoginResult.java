package com.lang.portal.web.auth;

public record AuthLoginResult(PortalSessionHandle session, AuthProfile profile) {}