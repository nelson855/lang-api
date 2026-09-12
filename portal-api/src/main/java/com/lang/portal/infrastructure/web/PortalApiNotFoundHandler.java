package com.lang.portal.infrastructure.web;

import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.response.ApiResponse;
import com.lang.portal.base.response.ApiResponses;
import com.lang.portal.base.response.RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class PortalApiNotFoundHandler {

  public ResponseEntity<ApiResponse<Void>> unifiedNotFound(HttpServletRequest request) {
    String requestId = RequestIds.current(request);
    if (requestId.isBlank()) {
      requestId = RequestIds.generate();
    }
    return ResponseEntity.status(404)
        .body(ApiResponses.failure(requestId, PortalErrorCode.NOT_FOUND.name(), PortalErrorCode.NOT_FOUND.message()));
  }
}
