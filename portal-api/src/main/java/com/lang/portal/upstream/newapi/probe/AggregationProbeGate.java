package com.lang.portal.upstream.newapi.probe;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;

public final class AggregationProbeGate {

  static final String OPT_IN_ENV = "LANG_PORTAL_AGGREGATION_PROBE_ENABLED";
  static final String USERNAME_ENV = "LANG_PORTAL_PROBE_USERNAME";
  static final String PASSWORD_ENV = "LANG_PORTAL_PROBE_PASSWORD";
  static final String RAW_DIR_ENV = "LANG_PORTAL_AGGREGATION_PROBE_RAW_DIR";

  private static final String EXPECTED_OPT_IN_VALUE = "true";
  private static final String DEFAULT_RAW_DIR_NAME = "target";

  private AggregationProbeGate() {}

  public record ProbeConfig(String baseUrl, Path rawOutputDir) {}

  public record ProbeCredentials(String username, String password) {}

  public static Optional<ProbeConfig> resolve(
      Map<String, String> env, String baseUrl, Path rawOutputDirFallback) {
    if (!isOptInEnabled(env)) {
      return Optional.empty();
    }
    if (baseUrl == null || baseUrl.isBlank()) {
      return Optional.empty();
    }
    if (!isLocalOrPrivateAddress(baseUrl)) {
      return Optional.empty();
    }
    Path rawDir;
    try {
      rawDir = resolveRawOutputDir(env);
    } catch (AggregationProbeException e) {
      return Optional.empty();
    }
    return Optional.of(new ProbeConfig(baseUrl, rawDir));
  }

  public static boolean isOptInEnabled(Map<String, String> env) {
    return EXPECTED_OPT_IN_VALUE.equals(env.get(OPT_IN_ENV));
  }

  public static ProbeCredentials loadCredentials(Map<String, String> env) {
    String username = env.get(USERNAME_ENV);
    String password = env.get(PASSWORD_ENV);
    if (username == null || username.isBlank()) {
      throw new AggregationProbeException(
          "probe credentials missing: set " + USERNAME_ENV + " in process environment");
    }
    if (password == null || password.isBlank()) {
      throw new AggregationProbeException(
          "probe credentials missing: set " + PASSWORD_ENV + " in process environment");
    }
    return new ProbeCredentials(username, password);
  }

  public static Path resolveRawOutputDir(Map<String, String> env) {
    String override = env.get(RAW_DIR_ENV);
    if (override == null || override.isBlank()) {
      return defaultRawOutputDir();
    }
    Path p = Paths.get(override).normalize().toAbsolutePath();
    String normalized = p.toString();
    if (!normalized.contains(DEFAULT_RAW_DIR_NAME)) {
      throw new AggregationProbeException(
          "raw output dir must reside under a 'target' directory: " + normalized);
    }
    if (normalized.contains("..")) {
      throw new AggregationProbeException("raw output dir must not contain '..': " + normalized);
    }
    return p;
  }

  public static Path defaultRawOutputDir() {
    Path cwd = Paths.get("").toAbsolutePath().normalize();
    Path candidate = cwd.resolve(DEFAULT_RAW_DIR_NAME);
    while (candidate != null && !java.nio.file.Files.isDirectory(candidate.getParent())) {
      candidate = candidate.getParent();
    }
    if (candidate == null) {
      return cwd.resolve(DEFAULT_RAW_DIR_NAME);
    }
    return candidate;
  }

  static boolean isLocalOrPrivateAddress(String baseUrl) {
    try {
      URI uri = URI.create(baseUrl);
      String host = uri.getHost();
      if (host == null || host.isBlank()) {
        return false;
      }
      String lower = host.toLowerCase();
      if ("localhost".equals(lower) || lower.endsWith(".localhost")) {
        return true;
      }
      InetAddress addr = InetAddress.getByName(host);
      return addr.isLoopbackAddress() || addr.isSiteLocalAddress() || addr.isLinkLocalAddress();
    } catch (IllegalArgumentException | UnknownHostException e) {
      return false;
    }
  }
}
