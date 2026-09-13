package com.lang.portal.web.apikey;

import com.lang.portal.base.exception.PortalErrorCode;
import com.lang.portal.base.exception.PortalException;
import com.lang.portal.upstream.newapi.token.NewApiToken;
import com.lang.portal.upstream.newapi.token.NewApiTokenMapper;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

public record ApiKeyDto(
    long id,
    String name,
    String maskedKey,
    String status,
    String createdAt,
    String expiresAt,
    ApiKeyQuota quota,
    ApiKeyUsedQuota usedQuota,
    ApiKeyModelRestrictions modelRestrictions,
    List<String> allowedIps) {

  public record ApiKeyQuota(boolean unlimited, long remaining, String unit) {}

  public record ApiKeyUsedQuota(long value, String unit) {}

  public record ApiKeyModelRestrictions(boolean enabled, List<String> models) {
    public ApiKeyModelRestrictions {
      models = models == null ? List.of() : List.copyOf(models);
    }
  }

  public ApiKeyDto {
    allowedIps = allowedIps == null ? List.of() : List.copyOf(allowedIps);
  }

  public static ApiKeyDto from(NewApiToken token, Instant now) {
    if (token == null
        || token.id() == null
        || token.name() == null
        || token.name().isBlank()
        || token.key() == null
        || token.key().isBlank()
        || token.createdTime() == null) {
      throw new PortalException(PortalErrorCode.UPSTREAM_ERROR);
    }
    boolean unlimited = Boolean.TRUE.equals(token.unlimitedQuota());
    long remaining = unlimited ? 0L : (token.remainQuota() == null ? 0L : token.remainQuota());
    if (remaining < 0) {
      throw new PortalException(PortalErrorCode.UPSTREAM_ERROR);
    }
    long nowSec = now == null ? Instant.now().getEpochSecond() : now.getEpochSecond();
    String status =
        NewApiTokenMapper.toStatus(token.status(), token.expiredTime(), token.remainQuota(), unlimited, nowSec);
    Instant expires = NewApiTokenMapper.toExpiresAt(token.expiredTime());
    List<String> models = NewApiTokenMapper.splitModels(token.modelLimits());
    boolean modelsEnabled = Boolean.TRUE.equals(token.modelLimitsEnabled()) || !models.isEmpty();
    return new ApiKeyDto(
        token.id(),
        token.name(),
        NewApiTokenMapper.normalizeMask(token.key()),
        status,
        format(token.createdTime()),
        expires == null ? null : format(expires.getEpochSecond()),
        new ApiKeyQuota(unlimited, remaining, "quota"),
        new ApiKeyUsedQuota(token.usedQuota() == null ? 0L : token.usedQuota(), "quota"),
        new ApiKeyModelRestrictions(modelsEnabled, models),
        NewApiTokenMapper.splitIps(token.allowIps()));
  }

  private static String format(long epochSecond) {
    return Instant.ofEpochSecond(epochSecond)
        .atOffset(ZoneOffset.UTC)
        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
  }
}
