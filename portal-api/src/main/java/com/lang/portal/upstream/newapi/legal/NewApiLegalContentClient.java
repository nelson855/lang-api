package com.lang.portal.upstream.newapi.legal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.UpstreamException;
import com.lang.portal.upstream.newapi.dto.NewApiEnvelope;
import com.lang.portal.upstream.newapi.operation.NewApiOperation;
import com.lang.portal.upstream.newapi.transport.NewApiExchange;
import com.lang.portal.upstream.newapi.transport.NewApiRawResponse;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

@Component
public class NewApiLegalContentClient implements NewApiLegalContentSource {

  private static final NewApiOperation USER_AGREEMENT =
      new NewApiOperation("legal_user_agreement", HttpMethod.GET, "/api/user-agreement", false);
  private static final NewApiOperation PRIVACY_POLICY =
      new NewApiOperation("legal_privacy_policy", HttpMethod.GET, "/api/privacy-policy", false);

  private final NewApiExchange exchange;

  public NewApiLegalContentClient(NewApiExchange exchange) {
    this.exchange = exchange;
  }

  public String getUserAgreement() {
    return fetch(USER_AGREEMENT);
  }

  public String getPrivacyPolicy() {
    return fetch(PRIVACY_POLICY);
  }

  private String fetch(NewApiOperation operation) {
    NewApiRawResponse<String> response = exchange.executeRaw(
        operation, null, java.util.Map.of(), new TypeReference<NewApiEnvelope<String>>() {});
    if (response.status() < 200 || response.status() >= 300 || !response.success() || response.data() == null) {
      throw new UpstreamException(PortalErrorCode.UPSTREAM_ERROR);
    }
    return response.data();
  }
}
