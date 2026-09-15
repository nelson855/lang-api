package com.lang.portal.web.legal;

import com.lang.portal.base.response.ApiResponse;
import com.lang.portal.base.response.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("permitAll()")
@RequestMapping("/portal/api/legal")
public class LegalContentController {

  private final LegalContentService service;

  public LegalContentController(LegalContentService service) {
    this.service = service;
  }

  @GetMapping("/terms")
  public ResponseEntity<ApiResponse<LegalContentDocument>> terms(HttpServletRequest request) {
    return ResponseEntity.ok(ApiResponses.ok(request, service.document(LegalContentType.TERMS)));
  }

  @GetMapping("/privacy")
  public ResponseEntity<ApiResponse<LegalContentDocument>> privacy(HttpServletRequest request) {
    return ResponseEntity.ok(ApiResponses.ok(request, service.document(LegalContentType.PRIVACY)));
  }
}
