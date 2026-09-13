package com.lang.portal.web.auth;

import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.PortalException;
import com.lang.portal.config.PortalCommonProperties;
import com.lang.portal.upstream.newapi.auth.NewApiAuthenticationClient;
import com.lang.portal.upstream.newapi.auth.NewApiCredentials;
import com.lang.portal.upstream.newapi.auth.NewApiLoginResult;
import com.lang.portal.upstream.newapi.auth.NewApiSession;
import org.springframework.stereotype.Service;

@Service
public class AuthApplicationService {

  private final PortalCommonProperties properties;
  private final NewApiAuthenticationClient authenticationClient;

  public AuthApplicationService(PortalCommonProperties properties, NewApiAuthenticationClient authenticationClient) {
    this.properties = properties;
    this.authenticationClient = authenticationClient;
  }

  public void register(RegisterRequest request) {
    if (!properties.auth().registration().enabled()) {
      throw new PortalException(PortalErrorCode.REGISTRATION_DISABLED);
    }
    if (!request.password().equals(request.confirmPassword())) {
      throw new PortalException(PortalErrorCode.INVALID_ARGUMENT);
    }
    authenticationClient.register(new NewApiCredentials(request.username(), request.password()));
  }

  public AuthLoginResult login(LoginRequest request) {
    NewApiLoginResult result = authenticationClient.login(new NewApiCredentials(request.username(), request.password()));
    var user = result.user();
    return new AuthLoginResult(
        result.session(), new AuthProfile(user.id(), user.username(), user.displayName(), user.email()));
  }

  public void logout(NewApiSession session) {
    authenticationClient.logout(session);
  }
}
