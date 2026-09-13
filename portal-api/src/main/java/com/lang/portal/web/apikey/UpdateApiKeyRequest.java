package com.lang.portal.web.apikey;

import java.util.List;

public record UpdateApiKeyRequest(
    String name,
    Boolean unlimited,
    Long remaining,
    String expiresAt,
    List<String> models,
    List<String> ips) {}
