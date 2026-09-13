package com.lang.portal.upstream.newapi.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.PortalException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class NewApiTokenMapperTests {

  private static final long NOW = 1790000000L;

  @Test
  void mapsEnabledAndDisabled() {
    assertThat(NewApiTokenMapper.toStatus(1, -1L, 100L, false, NOW)).isEqualTo("enabled");
    assertThat(NewApiTokenMapper.toStatus(2, -1L, 100L, false, NOW)).isEqualTo("disabled");
  }

  @Test
  void expiredTakesPrecedenceOverEnabledFlag() {
    assertThat(NewApiTokenMapper.toStatus(1, NOW - 10, 100L, false, NOW)).isEqualTo("expired");
    assertThat(NewApiTokenMapper.toStatus(2, NOW - 10, 100L, false, NOW)).isEqualTo("expired");
  }

  @Test
  void exhaustedWhenNoQuotaLeft() {
    assertThat(NewApiTokenMapper.toStatus(1, -1L, 0L, false, NOW)).isEqualTo("exhausted");
    assertThat(NewApiTokenMapper.toStatus(1, -1L, 100L, true, NOW)).isEqualTo("enabled");
  }

  @Test
  void unknownStatusFailsAsUpstreamError() {
    assertThatThrownBy(() -> NewApiTokenMapper.toStatus(9, -1L, 100L, false, NOW))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.UPSTREAM_ERROR);
  }

  @Test
  void expiresMinusOneMapsToNull() {
    assertThat(NewApiTokenMapper.toExpiresAt(-1L)).isNull();
    assertThat(NewApiTokenMapper.toExpiresAt(null)).isNull();
    assertThat(NewApiTokenMapper.toExpiresAt(1790000000L)).isEqualTo(Instant.ofEpochSecond(1790000000L));
  }

  @Test
  void modelsRoundTripThroughCommaString() {
    assertThat(NewApiTokenMapper.splitModels("gpt-4o, claude-sonnet,,")).containsExactly("gpt-4o", "claude-sonnet");
    assertThat(NewApiTokenMapper.splitModels(null)).isEmpty();
    assertThat(NewApiTokenMapper.joinModels(List.of("gpt-4o", "claude-sonnet"))).isEqualTo("gpt-4o,claude-sonnet");
    assertThat(NewApiTokenMapper.joinModels(null)).isEqualTo("");
  }

  @Test
  void ipsRoundTripThroughNewlineString() {
    assertThat(NewApiTokenMapper.splitIps("1.1.1.1\n::1\n")).containsExactly("1.1.1.1", "::1");
    assertThat(NewApiTokenMapper.splitIps(null)).isEmpty();
    assertThat(NewApiTokenMapper.joinIps(List.of("1.1.1.1", "::1"))).isEqualTo("1.1.1.1\n::1");
  }

  @Test
  void secretAlwaysHasSinglePrefix() {
    assertThat(NewApiTokenMapper.normalizeSecret("abc")).isEqualTo("sk-abc");
    assertThat(NewApiTokenMapper.normalizeSecret("sk-abc")).isEqualTo("sk-abc");
    assertThat(NewApiTokenMapper.normalizeSecret("sk-sk-abc")).isEqualTo("sk-abc");
  }

  @Test
  void blankSecretFailsAsUpstreamError() {
    assertThatThrownBy(() -> NewApiTokenMapper.normalizeSecret("  "))
        .isInstanceOf(PortalException.class)
        .matches(e -> ((PortalException) e).errorCode() == PortalErrorCode.UPSTREAM_ERROR);
  }

  @Test
  void maskGetsSinglePrefix() {
    assertThat(NewApiTokenMapper.normalizeMask("fN95**********CMHQ")).isEqualTo("sk-fN95**********CMHQ");
    assertThat(NewApiTokenMapper.normalizeMask("sk-fN95**********CMHQ")).isEqualTo("sk-fN95**********CMHQ");
  }
}
