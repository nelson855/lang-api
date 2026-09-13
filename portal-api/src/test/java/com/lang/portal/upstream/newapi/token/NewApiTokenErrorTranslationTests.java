package com.lang.portal.upstream.newapi.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.PortalException;
import com.lang.portal.base.exception.UpstreamException;
import com.lang.portal.config.PortalCommonProperties;
import com.lang.portal.upstream.newapi.NewApiContractTestBase;
import com.lang.portal.upstream.newapi.auth.NewApiSession;
import com.lang.portal.upstream.newapi.policy.NewApiErrorTranslator;
import com.lang.portal.upstream.newapi.transport.NewApiExchange;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import okhttp3.mockwebserver.MockResponse;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class NewApiTokenErrorTranslationTests extends NewApiContractTestBase {

  private NewApiTokenClient client() {
    PortalCommonProperties props = new PortalCommonProperties();
    props.upstream().newApi().setBaseUrl(baseUrl());
    NewApiExchange exchange =
        new NewApiExchange(RestClient.builder().build(), props, new NewApiErrorTranslator());
    return new NewApiTokenClient(exchange);
  }

  private static final NewApiSession SESSION = new NewApiSession("upstream-session", 42L);

  @Test
  void createConflictWhenLimitReached() {
    server.enqueue(new MockResponse().setResponseCode(409).setBody("{\"success\":false}"));
    var command = new NewApiCreateTokenCommand("probe", false, 10L, null, List.of(), List.of());
    assertThatThrownBy(() -> client().createToken(SESSION, command))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.API_KEY_LIMIT_REACHED)
        .hasMessageNotContaining("probe");
  }

  @Test
  void createInvalidWhenBadQuota() {
    server.enqueue(new MockResponse().setResponseCode(400).setBody("{\"success\":false}"));
    var command = new NewApiCreateTokenCommand("probe", false, 10L, null, List.of(), List.of());
    assertThatThrownBy(() -> client().createToken(SESSION, command))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.INVALID_ARGUMENT);
  }

  @Test
  void enableExpiredKeyConflictDoesNotLeakUpstreamMessage() {
    server.enqueue(json("{\"success\":false,\"message\":\"token expired cannot enable\"}"));
    assertThatThrownBy(() -> client().updateStatus(SESSION, 7L, true))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.RESOURCE_CONFLICT)
        .hasMessageNotContaining("expired cannot enable");
  }

  @Test
  void crossUserDeleteBecomesNotFound() {
    server.enqueue(new MockResponse().setResponseCode(404).setBody("{\"success\":false}"));
    assertThatThrownBy(() -> client().deleteToken(SESSION, 999L))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.NOT_FOUND);
  }

  @Test
  void readDisconnectPropagatesUnavailable() {
    server.enqueue(disconnect());
    assertThatThrownBy(() -> client().listTokens(SESSION, 1, 20))
        .isInstanceOf(UpstreamException.class)
        .matches(e -> ((UpstreamException) e).errorCode() == PortalErrorCode.UPSTREAM_UNAVAILABLE);
  }

  @Test
  void writeDisconnectBecomesResultUnknown() {
    server.enqueue(disconnect());
    var command = new NewApiCreateTokenCommand("probe", false, 10L, null, List.of(), List.of());
    assertThatThrownBy(() -> client().createToken(SESSION, command))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.OPERATION_RESULT_UNKNOWN);
  }

  @Test
  void rawTokenShapesStayInsideUpstreamBoundary() {
    JavaClasses classes = new ClassFileImporter().importPackages("com.lang.portal");
    com.tngtech.archunit.base.DescribedPredicate<com.tngtech.archunit.core.domain.JavaClass> rawShapes =
        com.tngtech.archunit.base.DescribedPredicate.describe(
            "raw token shapes",
            c -> c.getSimpleName().equals("NewApiToken")
                || c.getSimpleName().equals("NewApiTokenPage")
                || c.getSimpleName().equals("NewApiCreateTokenRequest")
                || c.getSimpleName().equals("NewApiStatusUpdateRequest")
                || c.getSimpleName().equals("NewApiTokenKeyResponse"));
    ArchRuleDefinition.noClasses()
        .that().resideInAnyPackage("..web..", "..service..", "..controller..")
        .and().resideOutsideOfPackages("..web.apikey..", "..upstream.newapi..")
        .should().dependOnClassesThat(rawShapes)
        .allowEmptyShould(true)
        .check(classes);
  }

  @Test
  void tokenPathsStayInsideUpstreamBoundary() throws Exception {
    List<String> offenders;
    try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
      offenders =
          files.filter(p -> p.toString().endsWith(".java"))
              .filter(p -> !p.toString().contains("/upstream/newapi/"))
              .filter(p -> {
                try {
                  return Files.readString(p).contains("/api/token");
                } catch (Exception e) {
                  return false;
                }
              })
              .map(p -> p.toString())
              .toList();
    }
    assertThat(offenders).isEmpty();
    Path frontend = Path.of("../frontend/src");
    if (Files.isDirectory(frontend)) {
      try (Stream<Path> files = Files.walk(frontend)) {
        List<String> frontendOffenders =
            files.filter(p -> {
              String name = p.toString();
              return (name.endsWith(".ts") || name.endsWith(".tsx")) && !name.endsWith(".test.ts")
                  && !name.endsWith(".test.tsx");
            })
                .filter(p -> {
                  try {
                    return Files.readString(p).contains("/api/token");
                  } catch (Exception e) {
                    return false;
                  }
                })
                .map(p -> p.toString())
                .toList();
        assertThat(frontendOffenders).isEmpty();
      }
    }
  }
}
