package com.lang.portal.web.auth;

import com.lang.portal.config.PortalCommonProperties;
import com.lang.portal.config.PublicationMode;
import com.lang.portal.config.PublicationProperties;
import com.lang.portal.web.legal.LegalContentService;
import com.lang.portal.web.legal.LegalContentType;
import org.springframework.stereotype.Service;

@Service
public class RegistrationPolicyService {

  private final PortalCommonProperties properties;
  private final PublicationProperties publication;
  private final LegalContentService legalContent;

  public RegistrationPolicyService(
      PortalCommonProperties properties,
      PublicationProperties publication,
      LegalContentService legalContent) {
    this.properties = properties;
    this.publication = publication;
    this.legalContent = legalContent;
  }

  public RegistrationPolicy evaluate() {
    if (!properties.auth().registration().enabled()) {
      return new RegistrationPolicy(false, "ADMIN_DISABLED");
    }
    if (publication.mode() != PublicationMode.PUBLIC) {
      return new RegistrationPolicy(false, "PREVIEW_MODE");
    }
    if (!legalAvailable()) {
      return new RegistrationPolicy(false, "LEGAL_UNAVAILABLE");
    }
    return new RegistrationPolicy(true, null);
  }

  private boolean legalAvailable() {
    try {
      legalContent.document(LegalContentType.TERMS);
      legalContent.document(LegalContentType.PRIVACY);
      return true;
    } catch (RuntimeException e) {
      return false;
    }
  }
}
