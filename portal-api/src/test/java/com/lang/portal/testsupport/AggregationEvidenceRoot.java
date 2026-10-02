package com.lang.portal.testsupport;

import java.nio.file.Files;
import java.nio.file.Path;

/** 聚合证据测试共用的只读仓库定位入口。 */
public final class AggregationEvidenceRoot {
  private AggregationEvidenceRoot() {}

  public static Path locate() {
    return locate(Path.of(System.getProperty("user.dir")));
  }

  static Path locate(Path start) {
    Path dir = start.toAbsolutePath().normalize();
    while (dir != null && (!Files.isRegularFile(dir.resolve("pom.xml"))
        || !Files.isDirectory(dir.resolve("docs/new-api")))) {
      dir = dir.getParent();
    }
    if (dir == null) {
      throw new IllegalStateException("cannot locate repository evidence root from " + start);
    }
    return dir;
  }
}
