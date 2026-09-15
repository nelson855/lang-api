package com.lang.portal.web.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.PortalException;
import com.lang.portal.base.exception.UpstreamException;
import com.lang.portal.config.PortalCommonProperties;
import com.lang.portal.config.PublicationMode;
import com.lang.portal.config.PublicationProperties;
import com.lang.portal.web.legal.LegalContentDocument;
import com.lang.portal.web.legal.LegalContentService;
import com.lang.portal.web.legal.LegalContentType;
import org.junit.jupiter.api.Test;

class RegistrationPolicyServiceTests {

  @Test
  void adminDisabledClosesRegistrationRegardlessOfModeAndLegal() {
    RegistrationPolicyService policy = policy(false, PublicationMode.PUBLIC, true, true);

    RegistrationPolicy result = policy.evaluate();

    assertThat(result.registrationEnabled()).isFalse();
    assertThat(result.registrationDisabledReason()).isEqualTo("ADMIN_DISABLED");
  }

  @Test
  void previewModeClosesRegistrationWhenAdminEnabledAndLegalReady() {
    RegistrationPolicyService policy = policy(true, PublicationMode.PREVIEW, true, true);

    RegistrationPolicy result = policy.evaluate();

    assertThat(result.registrationEnabled()).isFalse();
    assertThat(result.registrationDisabledReason()).isEqualTo("PREVIEW_MODE");
  }

  @Test
  void missingTermsClosesRegistration() {
    RegistrationPolicyService policy = policy(true, PublicationMode.PUBLIC, false, true);

    RegistrationPolicy result = policy.evaluate();

    assertThat(result.registrationEnabled()).isFalse();
    assertThat(result.registrationDisabledReason()).isEqualTo("LEGAL_UNAVAILABLE");
  }

  @Test
  void missingPrivacyClosesRegistration() {
    RegistrationPolicyService policy = policy(true, PublicationMode.PUBLIC, true, false);

    RegistrationPolicy result = policy.evaluate();

    assertThat(result.registrationEnabled()).isFalse();
    assertThat(result.registrationDisabledReason()).isEqualTo("LEGAL_UNAVAILABLE");
  }

  @Test
  void upstreamFailureCountsAsLegalUnavailable() {
    LegalContentService legal = mock(LegalContentService.class);
    when(legal.document(LegalContentType.TERMS)).thenReturn(document(LegalContentType.TERMS));
    when(legal.document(LegalContentType.PRIVACY)).thenThrow(new UpstreamException(PortalErrorCode.UPSTREAM_ERROR));
    RegistrationPolicyService policy = policy(true, PublicationMode.PUBLIC, legal);

    RegistrationPolicy result = policy.evaluate();

    assertThat(result.registrationEnabled()).isFalse();
    assertThat(result.registrationDisabledReason()).isEqualTo("LEGAL_UNAVAILABLE");
  }

  @Test
  void allReadyOpensRegistrationWithNullReason() {
    RegistrationPolicyService policy = policy(true, PublicationMode.PUBLIC, true, true);

    RegistrationPolicy result = policy.evaluate();

    assertThat(result.registrationEnabled()).isTrue();
    assertThat(result.registrationDisabledReason()).isNull();
  }

  @Test
  void disabledReasonsFollowAdminThenPreviewThenLegalPriority() {
    assertThat(policy(false, PublicationMode.PREVIEW, false, false).evaluate().registrationDisabledReason())
        .isEqualTo("ADMIN_DISABLED");
    assertThat(policy(true, PublicationMode.PREVIEW, false, false).evaluate().registrationDisabledReason())
        .isEqualTo("PREVIEW_MODE");
  }

  private static RegistrationPolicyService policy(
      boolean adminEnabled, PublicationMode mode, boolean termsAvailable, boolean privacyAvailable) {
    LegalContentService legal = mock(LegalContentService.class);
    if (termsAvailable) {
      when(legal.document(LegalContentType.TERMS)).thenReturn(document(LegalContentType.TERMS));
    } else {
      when(legal.document(LegalContentType.TERMS)).thenThrow(new PortalException(PortalErrorCode.NOT_FOUND));
    }
    if (privacyAvailable) {
      when(legal.document(LegalContentType.PRIVACY)).thenReturn(document(LegalContentType.PRIVACY));
    } else {
      when(legal.document(LegalContentType.PRIVACY)).thenThrow(new PortalException(PortalErrorCode.NOT_FOUND));
    }
    return policy(adminEnabled, mode, legal);
  }

  private static RegistrationPolicyService policy(
      boolean adminEnabled, PublicationMode mode, LegalContentService legal) {
    PortalCommonProperties properties = new PortalCommonProperties();
    properties.auth().registration().setEnabled(adminEnabled);
    PublicationProperties publication = new PublicationProperties();
    publication.setMode(mode);
    return new RegistrationPolicyService(properties, publication, legal);
  }

  private static LegalContentDocument document(LegalContentType type) {
    return new LegalContentDocument(type, "标题", "<p>safe</p>", "zh-CN");
  }
}
