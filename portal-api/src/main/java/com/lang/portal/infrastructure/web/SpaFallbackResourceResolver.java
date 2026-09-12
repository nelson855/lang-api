package com.lang.portal.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.resource.AbstractResourceResolver;
import org.springframework.web.servlet.resource.ResourceResolverChain;

class SpaFallbackResourceResolver extends AbstractResourceResolver {

  @Override
  protected Resource resolveResourceInternal(
      HttpServletRequest request,
      String requestPath,
      List<? extends Resource> locations,
      ResourceResolverChain chain) {
    Resource resource = chain.resolveResource(request, requestPath, locations);
    if (resource != null) {
      return resource;
    }
    String method = request.getMethod();
    if (!HttpMethod.GET.matches(method) && !HttpMethod.HEAD.matches(method)) {
      return null;
    }
    if (isExcluded(requestPath)) {
      return null;
    }
    return chain.resolveResource(request, "index.html", locations);
  }

  @Override
  protected String resolveUrlPathInternal(
      String resourceUrlPath,
      List<? extends Resource> locations,
      ResourceResolverChain chain) {
    return chain.resolveUrlPath(resourceUrlPath, locations);
  }

  private boolean isExcluded(String requestPath) {
    String path = requestPath == null ? "" : requestPath;
    while (path.startsWith("/")) {
      path = path.substring(1);
    }
    if (path.equals("portal/api")
        || path.startsWith("portal/api/")
        || path.equals("actuator")
        || path.startsWith("actuator/")) {
      return true;
    }
    int lastSlash = path.lastIndexOf('/');
    String lastSegment = lastSlash < 0 ? path : path.substring(lastSlash + 1);
    return lastSegment.contains(".");
  }
}
