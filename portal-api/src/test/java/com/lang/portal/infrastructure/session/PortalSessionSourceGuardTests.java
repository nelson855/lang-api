package com.lang.portal.infrastructure.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * 会话生命周期的安全约束：上游凭证与用户标识不得出现在日志、指标或 Cookie 里。
 */
class PortalSessionSourceGuardTests {

  private static final Path PACKAGE_ROOT =
      Paths.get("src/main/java/com/lang/portal/infrastructure/session");

  @Test
  void sessionSourcesNeverLogOrMeasureCredentials() throws IOException {
    List<Path> sources = listJavaSources();
    assertThat(sources).isNotEmpty();
    for (Path path : sources) {
      String content = Files.readString(path);
      // 会话值与用户标识只能出现在存储读写里，不能进日志或指标。
      assertThat(content)
          .as("文件 %s 不应记录日志事件", path.getFileName())
          .doesNotContain("log.info", "log.debug");
      assertThat(content)
          .as("文件 %s 不应把会话写入指标", path.getFileName())
          .doesNotContain("Counter", "Timer", "MeterRegistry", "Gauge");
    }
  }

  @Test
  void theUpstreamSessionValueIsNeverUsedAsTheBrowserFacingIdentifier() throws IOException {
    for (Path path : listJavaSources()) {
      String content = Files.readString(path);
      assertThat(content)
          .as("文件 %s 不得把上游会话值直接当作浏览器可见标识返回", path.getFileName())
          .doesNotContain("return upstreamSessionValue");
    }
  }

  @Test
  void recordAccessorsKeepCredentialsOutOfToString() {
    // 上游会话值不得出现在 toString 里，避免被日志框架或异常信息带出。
    assertThat(new PortalSessionRecord("id", "upstream-secret", 42L).toString())
        .doesNotContain("upstream-secret");
  }

  private static List<Path> listJavaSources() throws IOException {
    try (Stream<Path> stream = Files.walk(PACKAGE_ROOT)) {
      return stream.filter(path -> path.toString().endsWith(".java")).toList();
    }
  }
}