import { z } from 'zod';
import { portalRequest } from './portalClient';
import { buildPortalApiUrl } from './portalPath';

const decimalString = z.string().regex(/^\d+(\.\d{1,6})?$/);

export const requestLogResultSchema = z.enum(['SUCCESS', 'ERROR']);

export const requestLogSchema = z
  .object({
    occurredAt: z.string().datetime({ offset: true }),
    requestId: z.string().min(1).nullable(),
    keyName: z.string().min(1).nullable(),
    model: z.string().min(1).nullable(),
    result: requestLogResultSchema,
    inputTokens: z.number().int().nonnegative(),
    outputTokens: z.number().int().nonnegative(),
    durationMs: z.number().int().nonnegative(),
    stream: z.boolean(),
    quota: decimalString,
    amount: decimalString,
    currency: z.literal('USD'),
    protocol: z.string().min(1).nullable(),
    firstTokenLatencyMs: z.number().int().nonnegative().nullable(),
  })
  .strict();

export const requestLogPageSchema = z
  .object({
    items: z.array(requestLogSchema),
    page: z.number().int().min(1),
    pageSize: z.number().int().min(1),
    total: z.number().int().nonnegative(),
  })
  .strict();

export type RequestLog = z.output<typeof requestLogSchema>;
export type RequestLogPage = z.output<typeof requestLogPageSchema>;

export interface RequestLogListParams {
  page: number;
  pageSize: number;
  result: string;
  keyName?: string;
  model?: string;
  startTime?: string;
  endTime?: string;
}

export const REQUEST_LOGS_PATH = '/portal/api/request-logs';
export const API_REQUEST_LOGS_QUERY_KEY = ['portal', 'request-logs'] as const;

export function buildRequestLogsUrl(params: RequestLogListParams): string {
  return buildPortalApiUrl(REQUEST_LOGS_PATH, {
    page: params.page,
    pageSize: params.pageSize,
    result: params.result?.trim() ? params.result.trim() : undefined,
    keyName: params.keyName?.trim() ? params.keyName.trim() : undefined,
    model: params.model?.trim() ? params.model.trim() : undefined,
    startTime: params.startTime?.trim() ? params.startTime.trim() : undefined,
    endTime: params.endTime?.trim() ? params.endTime.trim() : undefined,
  });
}

export function listRequestLogs(params: RequestLogListParams, signal?: AbortSignal) {
  return portalRequest(buildRequestLogsUrl(params), requestLogPageSchema, signal ? { signal } : undefined);
}
