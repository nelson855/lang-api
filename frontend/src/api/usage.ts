import { z } from 'zod';
import { portalRequest } from './portalClient';
import { buildPortalApiUrl } from './portalPath';

const decimalString = z.string().regex(/^\d+(\.\d{1,6})?$/);

export const balanceSchema = z
  .object({
    quota: decimalString,
    amount: decimalString,
    currency: z.literal('USD'),
  })
  .strict();

export const summarySchema = z
  .object({
    quota: decimalString,
    amount: decimalString,
    currency: z.literal('USD'),
    rpm: z.number().int().nonnegative(),
    tpm: z.number().int().nonnegative(),
    rateWindowSeconds: z.literal(60),
  })
  .strict();

export const timeseriesPointSchema = z
  .object({
    bucketStart: z.string().datetime({ offset: true }),
    requestCount: z.number().int().nonnegative(),
    tokenCount: z.number().int().nonnegative(),
    quota: decimalString,
    amount: decimalString,
  })
  .strict();

export const timeseriesSchema = z
  .object({
    granularity: z.literal('HOUR'),
    points: z.array(timeseriesPointSchema),
  })
  .strict();

export type Balance = z.output<typeof balanceSchema>;
export type UsageSummary = z.output<typeof summarySchema>;
export type UsageTimeseriesPoint = z.output<typeof timeseriesPointSchema>;
export type UsageTimeseries = z.output<typeof timeseriesSchema>;

export interface UsageRange {
  startTime?: string;
  endTime?: string;
}

export const BALANCE_PATH = '/portal/api/account/balance';
export const SUMMARY_PATH = '/portal/api/usage/summary';
export const TIMESERIES_PATH = '/portal/api/usage/timeseries';
export const USAGE_QUERY_KEY = ['portal', 'usage'] as const;

export function buildSummaryUrl(range: UsageRange): string {
  return buildPortalApiUrl(SUMMARY_PATH, {
    startTime: range.startTime?.trim() ? range.startTime.trim() : undefined,
    endTime: range.endTime?.trim() ? range.endTime.trim() : undefined,
  });
}

export function buildTimeseriesUrl(range: UsageRange): string {
  return buildPortalApiUrl(TIMESERIES_PATH, {
    startTime: range.startTime?.trim() ? range.startTime.trim() : undefined,
    endTime: range.endTime?.trim() ? range.endTime.trim() : undefined,
  });
}

export function fetchBalance(signal?: AbortSignal) {
  return portalRequest(BALANCE_PATH, balanceSchema, signal ? { signal } : undefined);
}

export function fetchSummary(range: UsageRange, signal?: AbortSignal) {
  return portalRequest(buildSummaryUrl(range), summarySchema, signal ? { signal } : undefined);
}

export function fetchTimeseries(range: UsageRange, signal?: AbortSignal) {
  return portalRequest(buildTimeseriesUrl(range), timeseriesSchema, signal ? { signal } : undefined);
}
