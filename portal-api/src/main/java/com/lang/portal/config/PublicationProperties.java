package com.lang.portal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "lang.publication")
@Validated
public class PublicationProperties {

  private PublicationMode mode = PublicationMode.PREVIEW;

  public PublicationMode mode() {
    return mode;
  }

  public void setMode(PublicationMode mode) {
    this.mode = mode;
  }
}
