package com.lang.portal.web.apikey;

import java.util.List;

public record CreateApiKeyRequest(
    String name,
    Boolean unlimited,
    Long remaining,
    String expiresAt,
    List<String> models,
    List<String> ips) {}
