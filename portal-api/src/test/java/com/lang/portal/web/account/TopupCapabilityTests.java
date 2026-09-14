package com.lang.portal.web.account;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TopupCapabilityTests {

  @Test
  void allDisabledMapsToNotConfigured() {
    TopupCapability capability = TopupCapability.fromUpstreamFlags(false, false, false, false, false);

    assertThat(capability.enabled()).isFalse();
    assertThat(capability.methods()).isEmpty();
    assertThat(capability.currency()).isEqualTo("USD");
    assertThat(capability.reason()).isEqualTo("NOT_CONFIGURED");
  }

  @Test
  void anyEnabledChannelStillFailClosedAsUnsupported() {
    TopupCapability capability = TopupCapability.fromUpstreamFlags(true, false, false, false, false);

    assertThat(capability.enabled()).isFalse();
    assertThat(capability.methods()).isEmpty();
    assertThat(capability.reason()).isEqualTo("UNSUPPORTED_PROVIDER");
  }

  @Test
  void unknownEnabledChannelAlsoFailClosed() {
    TopupCapability capability = TopupCapability.fromUpstreamFlags(false, false, false, false, true);

    assertThat(capability.enabled()).isFalse();
    assertThat(capability.methods()).isEmpty();
    assertThat(capability.reason()).isEqualTo("UNSUPPORTED_PROVIDER");
  }
}
