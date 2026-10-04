package com.lang.portal.web.catalog;

import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.PortalException;
import com.lang.portal.base.response.ApiResponse;
import com.lang.portal.base.response.ApiResponses;
import com.lang.portal.base.response.RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
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
    String requestId = requestId(request);
    return ResponseEntity.ok()
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .header(RequestIds.HEADER, requestId)
        .body(ApiResponses.ok(requestId, data));
  }

  @GetMapping("/portal/api/models/{modelId}")
  public ResponseEntity<ApiResponse<CatalogDetailData>> modelDetails(
      @PathVariable String modelId,
      @RequestParam Map<String, String> params,
      HttpServletRequest request) {
    rejectUnknownParams(params);
    String id = ModelRef.decode(modelId);
    CatalogModelDetails model =
        catalogService
            .findDetails(id)
            .orElseThrow(() -> new PortalException(PortalErrorCode.NOT_FOUND));
    CatalogSnapshot snapshot = catalogService.snapshot();
    String requestId = requestId(request);
    return ResponseEntity.ok()
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .header(RequestIds.HEADER, requestId)
        .body(ApiResponses.ok(requestId, new CatalogDetailData(snapshot.pricingVersion(), model)));
  }

  @GetMapping("/portal/api/models/{modelId}/**")
  public ResponseEntity<ApiResponse<Void>> rejectExtraModelPath() {
    throw new PortalException(PortalErrorCode.INVALID_ARGUMENT);
  }

  @GetMapping("/portal/api/model-providers")
  public ResponseEntity<ApiResponse<CatalogProvidersData>> providers(
      @RequestParam Map<String, String> params, HttpServletRequest request) {
    rejectUnknownParams(params);
    CatalogSnapshot snapshot = catalogService.snapshot();
    String requestId = requestId(request);
    return ResponseEntity.ok()
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .header(RequestIds.HEADER, requestId)
        .body(
            ApiResponses.ok(
                requestId,
                new CatalogProvidersData(snapshot.pricingVersion(), snapshot.providers())));
  }

  private void rejectUnknownParams(Map<String, String> params) {
    if (params != null && !params.isEmpty()) {
      throw new PortalException(
          PortalErrorCode.INVALID_ARGUMENT, "请求参数 " + params.keySet().iterator().next() + " 不合法");
    }
  }

  private String requestId(HttpServletRequest request) {
    String requestId = RequestIds.current(request);
    if (requestId.isBlank()) {
      requestId = RequestIds.generate();
    }
    return requestId;
  }
}
