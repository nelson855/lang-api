package com.lang.portal.upstream.newapi.probe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AggregationProbeGateTests {

  Path tempDir;

  @BeforeEach
  void createTempDir() throws Exception {
    Path base = Path.of("target/probe-gate-tests");
    Files.createDirectories(base);
    tempDir = Files.createTempDirectory(base, "case-");
  }

  @Test
  void disabledByDefaultWhenNoOptInFlag() {
    Optional<AggregationProbeGate.ProbeConfig> config =
        AggregationProbeGate.resolve(
            envWithoutOptIn(), "http://127.0.0.1:3000", tempDir);
    assertThat(config).isEmpty();
  }

  @Test
  void disabledWhenOptInFlagIsNotTrue() {
    Optional<AggregationProbeGate.ProbeConfig> config =
        AggregationProbeGate.resolve(
            envWith("LANG_PORTAL_AGGREGATION_PROBE_ENABLED", "yes"),
            "http://127.0.0.1:3000",
            tempDir);
    assertThat(config).isEmpty();
  }

  @Test
  void enabledWithExactTrueAndLocalAddress() {
    Optional<AggregationProbeGate.ProbeConfig> config =
        AggregationProbeGate.resolve(
            envWith("LANG_PORTAL_AGGREGATION_PROBE_ENABLED", "true"),
            "http://127.0.0.1:3000",
            tempDir);
    assertThat(config).isPresent();
    assertThat(config.get().baseUrl()).isEqualTo("http://127.0.0.1:3000");
    assertThat(config.get().rawOutputDir().toString()).endsWith("target");
  }

  @Test
  void rejectsPublicHttpAddressEvenWithOptIn() {
    Optional<AggregationProbeGate.ProbeConfig> config =
        AggregationProbeGate.resolve(
            envWith("LANG_PORTAL_AGGREGATION_PROBE_ENABLED", "true"),
            "http://example.com",
            tempDir);
    assertThat(config).isEmpty();
  }

  @Test
  void rejectsPublicHttpsAddressEvenWithOptIn() {
    Optional<AggregationProbeGate.ProbeConfig> config =
        AggregationProbeGate.resolve(
            envWith("LANG_PORTAL_AGGREGATION_PROBE_ENABLED", "true"),
            "https://new-api.example.com",
            tempDir);
    assertThat(config).isEmpty();
  }

  @Test
  void acceptsLocalhostHostname() {
    Optional<AggregationProbeGate.ProbeConfig> config =
        AggregationProbeGate.resolve(
            envWith("LANG_PORTAL_AGGREGATION_PROBE_ENABLED", "true"),
            "http://localhost:3000",
            tempDir);
    assertThat(config).isPresent();
  }

  @Test
  void acceptsLoopbackIpv6() {
    Optional<AggregationProbeGate.ProbeConfig> config =
        AggregationProbeGate.resolve(
            envWith("LANG_PORTAL_AGGREGATION_PROBE_ENABLED", "true"),
            "http://[::1]:3000",
            tempDir);
    assertThat(config).isPresent();
  }

  @Test
  void acceptsPrivateRFC1918Address() {
    Optional<AggregationProbeGate.ProbeConfig> config =
        AggregationProbeGate.resolve(
            envWith("LANG_PORTAL_AGGREGATION_PROBE_ENABLED", "true"),
            "http://192.168.1.10:3000",
            tempDir);
    assertThat(config).isPresent();
  }

  @Test
  void rejectsEmptyBaseUrl() {
    Optional<AggregationProbeGate.ProbeConfig> config =
        AggregationProbeGate.resolve(
            envWith("LANG_PORTAL_AGGREGATION_PROBE_ENABLED", "true"),
            "",
            tempDir);
    assertThat(config).isEmpty();
  }

  @Test
  void rejectsNullBaseUrl() {
    Optional<AggregationProbeGate.ProbeConfig> config =
        AggregationProbeGate.resolve(
            envWith("LANG_PORTAL_AGGREGATION_PROBE_ENABLED", "true"),
            null,
            tempDir);
    assertThat(config).isEmpty();
  }

  @Test
  void credentialsOnlyFromEnvironmentNotFromSystemProperties() {
    System.setProperty("LANG_PORTAL_PROBE_USERNAME", "should-be-ignored");
    try {
      AggregationProbeGate.ProbeCredentials creds =
          AggregationProbeGate.loadCredentials(envWithCredentials());
      assertThat(creds.username()).isEqualTo("probe-user");
      assertThat(creds.password()).isEqualTo("probe-password");
    } finally {
      System.clearProperty("LANG_PORTAL_PROBE_USERNAME");
    }
  }

  @Test
  void throwsWhenCredentialsMissingFromEnvironment() {
    assertThatThrownBy(
            () -> AggregationProbeGate.loadCredentials(envWithoutOptIn()))
        .isInstanceOf(AggregationProbeException.class)
        .hasMessageContaining("credentials");
  }

  @Test
  void throwsWhenPasswordMissing() {
    var env = envWith("LANG_PORTAL_PROBE_USERNAME", "probe-user");
    assertThatThrownBy(() -> AggregationProbeGate.loadCredentials(env))
        .isInstanceOf(AggregationProbeException.class);
  }

  @Test
  void throwsWhenUsernameMissing() {
    var env = envWith("LANG_PORTAL_PROBE_PASSWORD", "probe-password");
    assertThatThrownBy(() -> AggregationProbeGate.loadCredentials(env))
        .isInstanceOf(AggregationProbeException.class);
  }

  @Test
  void rawOutputDirMustBeUnderTarget() {
    assertThatThrownBy(
            () ->
                AggregationProbeGate.resolveRawOutputDir(
                    envWith("LANG_PORTAL_AGGREGATION_PROBE_RAW_DIR", "/tmp/forbidden")))
        .isInstanceOf(AggregationProbeException.class)
        .hasMessageContaining("target");
  }

  @Test
  void rawOutputDirDefaultsToMavenTarget() {
    Path p =
        AggregationProbeGate.resolveRawOutputDir(
            envWith("LANG_PORTAL_AGGREGATION_PROBE_RAW_DIR", ""));
    assertThat(p.toString()).endsWith("target");
  }

  @Test
  void rawOutputDirRejectsPathTraversal() {
    assertThatThrownBy(
            () ->
                AggregationProbeGate.resolveRawOutputDir(
                    envWith(
                        "LANG_PORTAL_AGGREGATION_PROBE_RAW_DIR",
                        "target/../../escape")))
        .isInstanceOf(AggregationProbeException.class);
  }

  @Test
  void defaultRawOutputDirResolvedAgainstModuleRoot() {
    Path defaultDir = AggregationProbeGate.defaultRawOutputDir();
    assertThat(defaultDir.toString()).endsWith("target");
    assertThat(defaultDir.toAbsolutePath().normalize().toString())
        .doesNotContain("..");
  }

  private static java.util.Map<String, String> envWithoutOptIn() {
    return java.util.Map.of();
  }

  private static java.util.Map<String, String> envWith(String key, String value) {
    return java.util.Map.of(key, value);
  }

  private static java.util.Map<String, String> envWithCredentials() {
    return java.util.Map.of(
        "LANG_PORTAL_PROBE_USERNAME", "probe-user",
        "LANG_PORTAL_PROBE_PASSWORD", "probe-password");
  }
}
