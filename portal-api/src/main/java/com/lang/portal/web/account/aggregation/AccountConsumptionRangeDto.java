package com.lang.portal.web.account.aggregation;

public record AccountConsumptionRangeDto(
    String start, String end, String timezone, String granularity) {
  public AccountConsumptionRangeDto {
    if (start == null || start.isBlank()) {
      throw new IllegalArgumentException("规范化范围必须包含开始时间");
    }
    if (end == null || end.isBlank()) {
      throw new IllegalArgumentException("规范化范围必须包含结束时间");
    }
    if (timezone == null || timezone.isBlank()) {
      throw new IllegalArgumentException("规范化范围必须包含时区");
    }
    if (granularity == null || granularity.isBlank()) {
      throw new IllegalArgumentException("规范化范围必须包含粒度");
    }
  }
}
