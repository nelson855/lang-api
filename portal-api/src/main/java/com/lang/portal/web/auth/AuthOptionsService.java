package com.lang.portal.web.auth;

import org.springframework.stereotype.Service;

@Service
public class AuthOptionsService {

  private final RegistrationPolicyService registrationPolicy;

  public AuthOptionsService(RegistrationPolicyService registrationPolicy) {
    this.registrationPolicy = registrationPolicy;
  }

  public AuthOptions getOptions() {
    RegistrationPolicy policy = registrationPolicy.evaluate();
    return new AuthOptions(
        policy.registrationEnabled(), policy.registrationDisabledReason(), false, false);
  }
}
