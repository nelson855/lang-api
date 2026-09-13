package com.lang.portal.web.auth;

import com.lang.portal.upstream.newapi.auth.NewApiSession;

public record AuthLoginResult(NewApiSession session, AuthProfile profile) {}
