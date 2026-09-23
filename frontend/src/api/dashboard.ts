import { z } from 'zod';
import { portalRequest } from './portalClient';
import { buildPortalApiUrl } from './portalPath';

/**
 * Dashboard 统计接口（/portal/api/dashboard/stats）运行时契约。
 * 基于 P2-03 后端 DTO 冻结的字段名、类型与可用性矛盾规则。
 */

const decimalString = z.string().regex(/^\d+(\.\d{1,6})?$/);
const trendDecimalString = z.string().regex(/^\d+(\.\d+)?$/);

export const dashboardAvailabilitySchema = z.enum(['AVAILABLE', 'PARTIAL', 'UNAVAILABLE']);
export const dashboardReasonCodeSchema = z.enum([
  'BASELINE_NOT_VERIFIED',
  'NO_DATA',
  'SOURCE_FIELD_MISSING',
  'PARTIAL_SOURCE_COVERAGE',
]);

type MetricStatus = { availability: 'AVAILABLE' | 'UNAVAILABLE'; reasonCode: string | null };

/** 六指标共享的矛盾规则：AVAILABLE↔有值无原因；UNAVAILABLE↔无值有原因。 */
function metricIsConsistent(value: unknown, status: MetricStatus): boolean {
  if (status.availability === 'AVAILABLE') {
    return value !== null && value !== undefined && status.reasonCode === null;
  }
  return value === null && status.reasonCode !== null;
}

/** 趋势/集合共享的矛盾规则：AVAILABLE→无原因；PARTIAL/UNAVAILABLE→有原因。 */
function collectionIsConsistent(status: {
  availability: z.infer<typeof dashboardAvailabilitySchema>;
  reasonCode: string | null;
}): boolean {
  if (status.availability === 'AVAILABLE') {
    return status.reasonCode === null;
  }
  return status.reasonCode !== null;
}

export const countMetricSchema = z
  .object({
    value: z.number().int().nonnegative().nullable(),
    unit: z.string().min(1),
    availability: z.enum(['AVAILABLE', 'UNAVAILABLE']),
    reasonCode: dashboardReasonCodeSchema.nullable(),
  })
  .strict()
  .refine((m) => metricIsConsistent(m.value, m), '计数指标可用性与取值矛盾');

export const moneyMetricSchema = z
  .object({
    value: decimalString.nullable(),
    currency: z.string().min(1).nullable(),
    availability: z.enum(['AVAILABLE', 'UNAVAILABLE']),
    reasonCode: dashboardReasonCodeSchema.nullable(),
  })
  .strict()
  .refine((m) => metricIsConsistent(m.value, m), '金额指标可用性与取值矛盾')
  .refine((m) => m.availability === 'UNAVAILABLE' || m.currency !== null, '可用金额必须带有币种');

export const ratioMetricSchema = z
  .object({
    value: z.number().nonnegative().nullable(),
    unit: z.string().min(1),
    availability: z.enum(['AVAILABLE', 'UNAVAILABLE']),
    reasonCode: dashboardReasonCodeSchema.nullable(),
  })
  .strict()
  .refine((m) => metricIsConsistent(m.value, m), '比率指标可用性与取值矛盾');

export const averageMetricSchema = z
  .object({
    value: z.number().nonnegative().nullable(),
    unit: z.string().min(1),
    availability: z.enum(['AVAILABLE', 'UNAVAILABLE']),
    reasonCode: dashboardReasonCodeSchema.nullable(),
  })
  .strict()
  .refine((m) => metricIsConsistent(m.value, m), '平均值指标可用性与取值矛盾');

export const dashboardMetricsSchema = z
  .object({
    requestTotal: countMetricSchema,
    tokenUsage: countMetricSchema,
    spend: moneyMetricSchema,
    activeKeys: countMetricSchema,
    successRate: ratioMetricSchema,
    averageLatency: averageMetricSchema,
  })
  .strict();

export const trendPointSchema = z
  .object({
    bucketStart: z.string().datetime({ offset: true }),
    bucketEnd: z.string().datetime({ offset: true }),
    value: trendDecimalString,
  })
  .strict();

export const requestTrendSchema = z
  .object({
    availability: dashboardAvailabilitySchema,
    reasonCode: dashboardReasonCodeSchema.nullable(),
    unit: z.string().min(1),
    points: z.array(trendPointSchema),
  })
  .strict()
  .refine(collectionIsConsistent, '请求趋势可用性与原因矛盾');

export const spendTrendSchema = z
  .object({
    availability: dashboardAvailabilitySchema,
    reasonCode: dashboardReasonCodeSchema.nullable(),
    currency: z.string().min(1).nullable(),
    points: z.array(trendPointSchema),
  })
  .strict()
  .refine(collectionIsConsistent, '消费趋势可用性与原因矛盾');

export const recentRequestItemSchema = z
  .object({
    occurredAt: z.string().datetime({ offset: true }),
    requestId: z.string().min(1).nullable(),
    keyName: z.string().min(1).nullable(),
    model: z.string().min(1).nullable(),
    outcome: z.string().min(1),
    inputTokens: z.number().int().nonnegative(),
    outputTokens: z.number().int().nonnegative(),
    durationMs: z.number().int().nonnegative(),
    stream: z.boolean(),
  })
  .strict();

export const recentRequestsSchema = z
  .object({
    availability: dashboardAvailabilitySchema,
    reasonCode: dashboardReasonCodeSchema.nullable(),
    items: z.array(recentRequestItemSchema),
  })
  .strict()
  .refine(collectionIsConsistent, '最近请求可用性与原因矛盾');

export const dashboardRangeSchema = z
  .object({
    startTime: z.string().datetime({ offset: true }),
    endTime: z.string().datetime({ offset: true }),
    timezone: z.string().min(1),
    granularity: z.string().min(1),
  })
  .strict();

export const dashboardStatsSchema = z
  .object({
    baselineVersion: z.string().min(1),
    range: dashboardRangeSchema,
    metrics: dashboardMetricsSchema,
    requestTrend: requestTrendSchema,
    spendTrend: spendTrendSchema,
    recentRequests: recentRequestsSchema,
  })
  .strict();

export type DashboardAvailability = z.infer<typeof dashboardAvailabilitySchema>;
export type DashboardReasonCode = z.infer<typeof dashboardReasonCodeSchema>;
export type DashboardCountMetric = z.output<typeof countMetricSchema>;
export type DashboardMoneyMetric = z.output<typeof moneyMetricSchema>;
export type DashboardRatioMetric = z.output<typeof ratioMetricSchema>;
export type DashboardAverageMetric = z.output<typeof averageMetricSchema>;
export type DashboardMetrics = z.output<typeof dashboardMetricsSchema>;
export type DashboardTrendPoint = z.output<typeof trendPointSchema>;
export type DashboardRequestTrend = z.output<typeof requestTrendSchema>;
export type DashboardSpendTrend = z.output<typeof spendTrendSchema>;
export type DashboardRecentRequestItem = z.output<typeof recentRequestItemSchema>;
export type DashboardRecentRequests = z.output<typeof recentRequestsSchema>;
export type DashboardRange = z.output<typeof dashboardRangeSchema>;
export type DashboardStats = z.output<typeof dashboardStatsSchema>;

export const DASHBOARD_STATS_PATH = '/portal/api/dashboard/stats';
export const DASHBOARD_QUERY_KEY = ['portal', 'dashboard'] as const;

export interface DashboardStatsQueryParams {
  startTime: string;
  endTime: string;
  granularity: string;
  timezone: string;
}

export function buildDashboardStatsUrl(params: DashboardStatsQueryParams): string {
  return buildPortalApiUrl(DASHBOARD_STATS_PATH, {
    startTime: params.startTime?.trim() ? params.startTime.trim() : undefined,
    endTime: params.endTime?.trim() ? params.endTime.trim() : undefined,
    granularity: params.granularity?.trim() ? params.granularity.trim() : undefined,
    timezone: params.timezone?.trim() ? params.timezone.trim() : undefined,
  });
}

export function fetchDashboardStats(
  params: DashboardStatsQueryParams,
  signal?: AbortSignal,
): ReturnType<typeof portalRequest<DashboardStats>> {
  return portalRequest(buildDashboardStatsUrl(params), dashboardStatsSchema, signal ? { signal } : undefined);
}