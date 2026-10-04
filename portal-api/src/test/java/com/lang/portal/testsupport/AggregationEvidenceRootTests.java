package com.lang.portal.testsupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AggregationEvidenceRootTests {
  @TempDir Path temp;

  @Test
  void locatesEvidenceFromDockerStyleModuleDirectoryWithoutGitOrOpenSpec() throws Exception {
    Path root = temp.resolve("build");
    Files.createDirectories(root.resolve("portal-api"));
    Files.writeString(root.resolve("pom.xml"), "<project/>");
    Files.createDirectories(root.resolve("docs/new-api"));
    assertThat(AggregationEvidenceRoot.locate(root.resolve("portal-api"))).isEqualTo(root);
    assertThat(AggregationEvidenceRoot.locate(root)).isEqualTo(root);
  }

  @Test
  void ignoresDocumentationDirectoryWithoutRepositoryPomAnchor() throws Exception {
    Files.writeString(temp.resolve("pom.xml"), "<project/>");
    Files.createDirectories(temp.resolve("docs/new-api"));
    Path module = temp.resolve("portal-api");
    Files.createDirectories(module.resolve("docs/new-api"));
    assertThat(AggregationEvidenceRoot.locate(module)).isEqualTo(temp);
  }

  @Test
  void missingEvidenceFailsExplicitly() throws Exception {
    Files.writeString(temp.resolve("pom.xml"), "<project/>");
    Files.createDirectories(temp.resolve("portal-api"));
    assertThatThrownBy(() -> AggregationEvidenceRoot.locate(temp.resolve("portal-api")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("cannot locate repository evidence root");
  }
}
