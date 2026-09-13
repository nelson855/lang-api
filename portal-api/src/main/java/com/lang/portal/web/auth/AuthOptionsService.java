package com.lang.portal.web.auth;

import com.lang.portal.config.PortalCommonProperties;
import org.springframework.stereotype.Service;

@Service
public class AuthOptionsService {

  private final PortalCommonProperties properties;

  public AuthOptionsService(PortalCommonProperties properties) {
    this.properties = properties;
  }

  public AuthOptions getOptions() {
    return new AuthOptions(properties.auth().registration().enabled(), false, false);
  }
}
