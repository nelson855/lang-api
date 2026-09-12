package com.lang.portal.web;

import com.lang.portal.base.response.ApiResponse;
import com.lang.portal.base.response.ApiResponses;
import com.lang.portal.web.dto.PublicConfigResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/portal/api/public-config")
public class PublicConfigController {

  private final PublicConfigService service;

  public PublicConfigController(PublicConfigService service) {
    this.service = service;
  }

  @GetMapping
  public ResponseEntity<ApiResponse<PublicConfigResponse>> current(HttpServletRequest request) {
    return ResponseEntity.ok()
        .header("Cache-Control", "no-store")
        .body(ApiResponses.ok(request, service.current()));
  }
}
