package com.lang.portal.web.account.aggregation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class AccountAggregationArchitectureGuardTests {

  private static final Path PACKAGE_ROOT =
      Paths.get("src/main/java/com/lang/portal/web/account/aggregation");

  @Test
  void accountAggregationSourcesNeverReferenceUpstreamDtoOrMoneyConverter() throws IOException {
    List<Path> sources = listJavaSources();
    assertThat(sources).isNotEmpty();
    for (Path path : sources) {
      String content = Files.readString(path);
      assertThat(content)
          .as("文件 %s 不应引用 New API 上游 DTO", path.getFileName())
          .doesNotContain("com.lang.portal.upstream.newapi.dto")
          .doesNotContain("NewApiEnvelope")
          .doesNotContain("NewApiLogPageRaw");
      assertThat(content)
          .as("文件 %s 不应调用正式货币换算器", path.getFileName())
          .doesNotContain("QuotaMoneyConverter")
          .doesNotContain("quotaPerUsd")
          .doesNotContain("quota-per-usd");
      assertThat(content)
          .as("文件 %s 不应调用 P1 充值替代源", path.getFileName())
          .doesNotContain("/api/user/topup/self")
          .doesNotContain("/api/log/self/stat")
          .doesNotContain("/api/data/self");
    }
  }

  private static List<Path> listJavaSources() throws IOException {
    try (Stream<Path> stream = Files.walk(PACKAGE_ROOT)) {
      return stream.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
    }
  }
}
