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

class AccountAggregationReadOnlyBoundaryTests {

  private static final Path PACKAGE_ROOT =
      Paths.get("src/main/java/com/lang/portal/web/account/aggregation");

  @Test
  void accountAggregationContainsNoWriteEndpoints() throws IOException {
    List<Path> sources;
    try (Stream<Path> stream = Files.walk(PACKAGE_ROOT)) {
      sources = stream.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
    }
    assertThat(sources).isNotEmpty();
    for (Path path : sources) {
      String content = Files.readString(path);
      assertThat(content)
          .as("文件 %s 不应包含写操作", path.getFileName())
          .doesNotContain("@PostMapping")
          .doesNotContain("@PutMapping")
          .doesNotContain("@PatchMapping")
          .doesNotContain("@DeleteMapping")
          .doesNotContain("RequestMethod.POST")
          .doesNotContain("RequestMethod.PUT")
          .doesNotContain("RequestMethod.PATCH")
          .doesNotContain("RequestMethod.DELETE");
    }
  }
}
