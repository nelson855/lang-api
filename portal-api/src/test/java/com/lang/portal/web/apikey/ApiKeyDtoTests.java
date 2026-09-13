package com.lang.portal.web.apikey;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lang.portal.upstream.newapi.token.NewApiToken;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ApiKeyDtoTests {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static NewApiToken token() {
    return new NewApiToken(
        7L, 42L, "fN95**********CMHQ", 1, "probe-key-01",
        1789180807L, 1789180807L, -1L, 100000L, false, true,
        "gpt-4o,claude-sonnet", "1.1.1.1\n::1", 12L, "", false);
  }

  @Test
  void mapsPublicFieldsWithStableSemantics() {
    ApiKeyDto dto = ApiKeyDto.from(token(), Instant.ofEpochSecond(1790000000L));

    assertThat(dto.id()).isEqualTo(7L);
    assertThat(dto.name()).isEqualTo("probe-key-01");
    assertThat(dto.maskedKey()).isEqualTo("sk-fN95**********CMHQ");
    assertThat(dto.status()).isEqualTo("enabled");
    assertThat(dto.createdAt()).isEqualTo("2026-09-12T02:40:07Z");
    assertThat(dto.expiresAt()).isNull();
    assertThat(dto.quota().unlimited()).isFalse();
    assertThat(dto.quota().remaining()).isEqualTo(100000L);
    assertThat(dto.quota().unit()).isEqualTo("quota");
    assertThat(dto.usedQuota().value()).isEqualTo(12L);
    assertThat(dto.usedQuota().unit()).isEqualTo("quota");
    assertThat(dto.modelRestrictions().enabled()).isTrue();
    assertThat(dto.modelRestrictions().models()).containsExactly("gpt-4o", "claude-sonnet");
    assertThat(dto.allowedIps()).containsExactly("1.1.1.1", "::1");
  }

  @Test
  void finiteExpiryMapsToIso8601() {
    NewApiToken expiring = new NewApiToken(
        7L, 42L, "fN95**********CMHQ", 1, "probe", 1789180807L, 1789180807L,
        1790000000L, 100L, false, false, "", "", 0L, "", false);
    ApiKeyDto dto = ApiKeyDto.from(expiring, Instant.ofEpochSecond(1789180807L));
    assertThat(dto.expiresAt()).isEqualTo("2026-09-21T14:13:20Z");
  }

  @Test
  void serializedShapeExposesOnlyLangFields() throws Exception {
    JsonNode json = MAPPER.valueToTree(ApiKeyDto.from(token(), Instant.ofEpochSecond(1790000000L)));

    assertThat(json.fieldNames())
        .toIterable()
        .containsExactlyInAnyOrder(
            "id", "name", "maskedKey", "status", "createdAt", "expiresAt",
            "quota", "usedQuota", "modelRestrictions", "allowedIps");
    String raw = json.toString();
    assertThat(raw)
        .doesNotContain("accessed_time", "accessedTime", "group", "owner", "user_id", "userId",
            "remain_quota", "cross_group", "DeletedAt", "\"key\"");
    assertThat(json.get("quota").fieldNames())
        .toIterable()
        .containsExactlyInAnyOrder("unlimited", "remaining", "unit");
  }

  @Test
  void unlimitedQuotaProjection() {
    NewApiToken unlimited = new NewApiToken(
        8L, 42L, "fN95**********CMHQ", 1, "big", 1789180807L, 1789180807L,
        -1L, 0L, true, false, "", "", 0L, "", false);
    ApiKeyDto dto = ApiKeyDto.from(unlimited, Instant.ofEpochSecond(1790000000L));
    assertThat(dto.quota().unlimited()).isTrue();
    assertThat(dto.quota().remaining()).isEqualTo(0L);
  }

  @Test
  void statusDerivationUsesMapper() {
    NewApiToken exhausted = new NewApiToken(
        9L, 42L, "fN95**********CMHQ", 1, "dry", 1789180807L, 1789180807L,
        -1L, 0L, false, false, "", "", 7L, "", false);
    assertThat(ApiKeyDto.from(exhausted, Instant.ofEpochSecond(1790000000L)).status())
        .isEqualTo("exhausted");
  }
}
