package com.lang.portal.web.dto;

import java.util.List;

public record PublicConfigResponse(
    String siteName,
    String publicationMode,
    String siteUrl,
    String supportUrl,
    List<String> supportedRegions,
    List<String> enabledLocales,
    List<ApiBaseUrl> apiBaseUrls) {}
