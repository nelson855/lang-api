package com.lang.portal.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/portal/api/**", produces = MediaType.APPLICATION_JSON_VALUE)
public class PortalApiNotFoundHandler {

  @RequestMapping
  public ResponseEntity<Map<String, Object>> notFound(HttpServletRequest request) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("status", 404);
    body.put("error", "Not Found");
    body.put("message", "请求的 Portal 接口不存在");
    body.put("path", request.getRequestURI());
    return ResponseEntity.status(404).body(body);
  }
}
