package com.lang.portal.upstream.newapi;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;
import okio.Okio;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

public abstract class NewApiContractTestBase {

  protected MockWebServer server;

  @BeforeEach
  void startServer() throws IOException {
    server = new MockWebServer();
    server.start();
  }

  @AfterEach
  void stopServer() throws IOException {
    server.shutdown();
  }

  protected String baseUrl() {
    return server.url("/").toString().replaceAll("/$", "");
  }

  protected RecordedRequest takeRequest() throws InterruptedException {
    RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
    if (request == null) {
      throw new AssertionError("MockWebServer 未收到请求");
    }
    return request;
  }

  protected static MockResponse json(String body) {
    return new MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .setBody(body);
  }

  protected static MockResponse businessFailure() {
    return json("{\"success\":false,\"message\":\"原始上游错误\",\"data\":null}");
  }

  protected static MockResponse unknownFields() {
    return json("{\"success\":true,\"message\":\"ok\",\"data\":{\"id\":1},\"newField\":\"x\"}");
  }

  protected static MockResponse nonJson() {
    return new MockResponse().setResponseCode(200).addHeader("Content-Type", "text/html").setBody("<html>oops</html>");
  }

  protected static MockResponse delayed(long millis) {
    return json("{\"success\":true,\"message\":\"ok\",\"data\":null}").setBodyDelay(millis, TimeUnit.MILLISECONDS);
  }

  protected static MockResponse disconnect() {
    return new MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AT_START);
  }

  protected static MockResponse slowRead() {
    Buffer buffer = new Buffer();
    buffer.writeUtf8("{\"success\":true,\"message\":\"ok\",\"data\":null}");
    return new MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .setBody(buffer)
        .throttleBody(1, 50, TimeUnit.MILLISECONDS);
  }
}
