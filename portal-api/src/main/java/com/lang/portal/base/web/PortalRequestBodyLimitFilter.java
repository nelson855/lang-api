package com.lang.portal.base.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lang.portal.base.exception.PayloadTooLargeException;
import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.response.ApiResponses;
import com.lang.portal.base.response.RequestIds;
import com.lang.portal.config.PortalCommonProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class PortalRequestBodyLimitFilter extends OncePerRequestFilter {

  private final PortalCommonProperties properties;
  private final ObjectMapper mapper = new ObjectMapper();

  public PortalRequestBodyLimitFilter(PortalCommonProperties properties) {
    this.properties = properties;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith("/portal/api/");
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    long limit = properties.request().maxBodyBytes();
    try {
      String declared = request.getHeader("Content-Length");
      if (declared != null) {
        try {
          if (Long.parseLong(declared.trim()) > limit) {
            throw new PayloadTooLargeException();
          }
        } catch (NumberFormatException ignored) {
        }
      }
      BoundedRequest wrapped = new BoundedRequest(request, limit);
      chain.doFilter(wrapped, response);
    } catch (PayloadTooLargeException e) {
      String requestId = RequestIds.current(request);
      if (requestId.isBlank()) {
        requestId = RequestIds.generate();
      }
      response.setStatus(413);
      response.setContentType(org.springframework.http.MediaType.APPLICATION_JSON_VALUE);
      response.setHeader(RequestIds.HEADER, requestId);
      mapper.writeValue(
          response.getOutputStream(),
          ApiResponses.failure(requestId, PortalErrorCode.PAYLOAD_TOO_LARGE.name(), PortalErrorCode.PAYLOAD_TOO_LARGE.message()));
    }
  }

  static class BoundedRequest extends HttpServletRequestWrapper {
    private final long limit;

    BoundedRequest(HttpServletRequest request, long limit) {
      super(request);
      this.limit = limit;
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
      ServletInputStream original = super.getInputStream();
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      byte[] chunk = new byte[8192];
      long total = 0;
      int read;
      while ((read = original.read(chunk)) != -1) {
        total += read;
        if (total > limit) {
          throw new PayloadTooLargeException();
        }
        buffer.write(chunk, 0, read);
      }
      byte[] body = buffer.toByteArray();
      ByteArrayInputStream replay = new ByteArrayInputStream(body);
      return new ServletInputStream() {
        @Override
        public boolean isFinished() {
          return replay.available() == 0;
        }

        @Override
        public boolean isReady() {
          return true;
        }

        @Override
        public void setReadListener(ReadListener listener) {}

        @Override
        public int read() {
          return replay.read();
        }

        @Override
        public int read(byte[] b, int off, int len) {
          return replay.read(b, off, len);
        }
      };
    }
  }
}
