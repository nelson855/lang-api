import { z } from 'zod';
import { portalRequest } from './portalClient';
import { buildPortalApiUrl } from './portalPath';

const decimalString = z.string().regex(/^\d+(\.\d{1,6})?$/);

export const requestLogResultSchema = z.enum(['SUCCESS', 'ERROR']);

/**
 * Portal 展示词表，仅用于安全展示受控值；不代表上游已提供该协议映射，
 * 也不构成任何公共协议开放承诺。词表外的字符串一律降级为 null，不原样透传。
 */
const CONTROLLED_PROTOCOLS = ['OPENAI', 'ANTHROPIC', 'GEMINI'] as const;
export type ControlledProtocol = (typeof CONTROLLED_PROTOCOLS)[number];

const controlledProtocolSchema = z.custom<string>(
  (value) => typeof value === 'string' && (CONTROLLED_PROTOCOLS as readonly string[]).includes(value),
  { message: '协议不在受控词表内' },
);

/** 缺失、非受控字符串或异常类型 → null；合法受控值原样保留。 */
const optionalProtocolSchema = z
  .unknown()
  .optional()
  .transform((value) => {
    const parsed = controlledProtocolSchema.safeParse(value);
    return parsed.success ? parsed.data : null;
  });

/**
 * 缺失或 null → null；合法非负安全整数毫秒值（含真实零）→ 原值；其余一律降级为 null。
 * 不以总耗时推断上下界：其单位分辨率与测量边界尚不能支撑该推断。
 */
const optionalFirstTokenLatencySchema = z
  .unknown()
  .optional()
  .transform((value) => {
    if (typeof value !== 'number' || !Number.isInteger(value) || value < 0) {
      return null;
    }
    if (!Number.isFinite(value) || !Number.isSafeInteger(value)) {
      return null;
    }
    return value;
  });

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
    protocol: optionalProtocolSchema,
    firstTokenLatencyMs: optionalFirstTokenLatencySchema,
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
