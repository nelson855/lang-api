package com.lang.portal.base.web;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class BoundedRequestChunkedTests {

  @Test
  void streamWithoutContentLengthOverLimitThrows() {
    byte[] big = new byte[1_048_577];
    MockHttpServletRequest mock = new MockHttpServletRequest();
    mock.setContent(big);
    mock.removeHeader("Content-Length");
    HttpServletRequest request = mock;
    PortalRequestBodyLimitFilter.BoundedRequest bounded =
        new PortalRequestBodyLimitFilter.BoundedRequest(request, 1_048_576);
    assertThatThrownBy(() -> {
      ServletInputStream in = bounded.getInputStream();
      byte[] buf = new byte[8192];
      while (in.read(buf) != -1) {
      }
    }).isInstanceOf(com.lang.portal.base.exception.PayloadTooLargeException.class);
  }
}
