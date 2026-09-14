package com.lang.portal.web.catalog;

import com.lang.portal.base.response.ApiResponse;
import com.lang.portal.base.response.ApiResponses;
import com.lang.portal.base.response.RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CatalogController {

  private final CatalogService catalogService;

  public CatalogController(CatalogService catalogService) {
    this.catalogService = catalogService;
  }

  @GetMapping("/portal/api/models")
  public ResponseEntity<ApiResponse<CatalogData>> models(HttpServletRequest request) {
    CatalogData data = catalogService.current();
    String requestId = RequestIds.current(request);
    if (requestId.isBlank()) {
      requestId = RequestIds.generate();
    }
    return ResponseEntity.ok()
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .header(RequestIds.HEADER, requestId)
        .body(ApiResponses.ok(requestId, data));
  }
}
