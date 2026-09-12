package com.lang.portal.web.dto;

import java.util.List;

public record PublicConfigResponse(String siteName, List<ApiBaseUrl> apiBaseUrls) {}
