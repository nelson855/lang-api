package com.lang.portal.web.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.lang.portal.config.PortalCommonProperties;
import com.lang.portal.config.PublicationMode;
import com.lang.portal.config.PublicationProperties;
import com.lang.portal.web.legal.LegalContentDocument;
import com.lang.portal.web.legal.LegalContentService;
import com.lang.portal.web.legal.LegalContentType;
import org.junit.jupiter.api.Test;

class AuthOptionsServiceTests {

  @Test
  void exposesEffectiveRegistrationPolicyAndNeverClaimsUnsupportedVerification() {
    AuthOptions options = service(true, PublicationMode.PUBLIC, true).getOptions();

    assertThat(options.registrationEnabled()).isTrue();
    assertThat(options.registrationDisabledReason()).isNull();
    assertThat(options.emailVerificationEnabled()).isFalse();
    assertThat(options.captchaEnabled()).isFalse();
  }

  @Test
  void exposesPreviewCloseReasonWhenPublicationIsNotPublic() {
    AuthOptions options = service(true, PublicationMode.PREVIEW, true).getOptions();

    assertThat(options.registrationEnabled()).isFalse();
    assertThat(options.registrationDisabledReason()).isEqualTo("PREVIEW_MODE");
  }

  private static AuthOptionsService service(boolean adminEnabled, PublicationMode mode, boolean legalAvailable) {
    PortalCommonProperties properties = new PortalCommonProperties();
    properties.auth().registration().setEnabled(adminEnabled);
    PublicationProperties publication = new PublicationProperties();
    publication.setMode(mode);
    LegalContentService legal = mock(LegalContentService.class);
    if (legalAvailable) {
      when(legal.document(LegalContentType.TERMS))
          .thenReturn(new LegalContentDocument(LegalContentType.TERMS, "用户协议", "<p>safe</p>", "zh-CN"));
      when(legal.document(LegalContentType.PRIVACY))
          .thenReturn(new LegalContentDocument(LegalContentType.PRIVACY, "隐私政策", "<p>safe</p>", "zh-CN"));
    }
    return new AuthOptionsService(new RegistrationPolicyService(properties, publication, legal));
  }
}
