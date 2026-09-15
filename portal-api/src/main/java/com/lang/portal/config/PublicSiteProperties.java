package com.lang.portal.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "lang.public")
@Validated
public class PublicSiteProperties {

  private String siteUrl = "";
  private String supportUrl = "";
  private List<String> supportedRegions = new ArrayList<>();
  private List<String> enabledLocales = new ArrayList<>();

  public String siteUrl() {
    return siteUrl;
  }

  public void setSiteUrl(String siteUrl) {
    this.siteUrl = siteUrl == null ? "" : siteUrl.trim();
  }

  public String supportUrl() {
    return supportUrl;
  }

  public void setSupportUrl(String supportUrl) {
    this.supportUrl = supportUrl == null ? "" : supportUrl.trim();
  }

  public List<String> supportedRegions() {
    return List.copyOf(supportedRegions);
  }

  public void setSupportedRegions(List<String> supportedRegions) {
    this.supportedRegions = supportedRegions == null ? new ArrayList<>() : new ArrayList<>(supportedRegions);
  }

  public List<String> enabledLocales() {
    return List.copyOf(enabledLocales);
  }

  public void setEnabledLocales(List<String> enabledLocales) {
    this.enabledLocales = enabledLocales == null ? new ArrayList<>() : new ArrayList<>(enabledLocales);
  }
}
