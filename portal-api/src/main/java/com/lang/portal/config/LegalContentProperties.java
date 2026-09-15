package com.lang.portal.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "lang.legal")
@Validated
public class LegalContentProperties {

  private String sourceLocale = "zh-CN";
  private LegalContentFormat sourceFormat = LegalContentFormat.MARKDOWN;
  private long maxInputBytes = 256 * 1024L;
  private long maxOutputBytes = 256 * 1024L;
  private Duration cacheTtl = Duration.ofMinutes(5);

  public String sourceLocale() {
    return sourceLocale;
  }

  public void setSourceLocale(String sourceLocale) {
    this.sourceLocale = sourceLocale == null ? "" : sourceLocale.trim();
  }

  public LegalContentFormat sourceFormat() {
    return sourceFormat;
  }

  public void setSourceFormat(LegalContentFormat sourceFormat) {
    this.sourceFormat = sourceFormat;
  }

  public long maxInputBytes() {
    return maxInputBytes;
  }

  public void setMaxInputBytes(long maxInputBytes) {
    this.maxInputBytes = maxInputBytes;
  }

  public long maxOutputBytes() {
    return maxOutputBytes;
  }

  public void setMaxOutputBytes(long maxOutputBytes) {
    this.maxOutputBytes = maxOutputBytes;
  }

  public Duration cacheTtl() {
    return cacheTtl;
  }

  public void setCacheTtl(Duration cacheTtl) {
    this.cacheTtl = cacheTtl;
  }
}
