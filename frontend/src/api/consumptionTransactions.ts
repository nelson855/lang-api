import { z } from 'zod';

/**
 * P2-05 消费汇总与统一流水 Portal 前端契约。
 *
 * 与 openspec/specs/account-consumption-transactions/spec.md 和
 * docs/14_LANG-P2-05-消费汇总与统一流水接口说明.md 严格一致:
 * - strict object,未知字段一律拒绝;
 * - 十进制金额使用字符串承载,不进入 JavaScript number;
 * - AVAILABLE 必须带值且 reasonCode=null;UNAVAILABLE/PARTIAL 必须给出稳定 reasonCode;
 * - QUOTA 必须 currency=null;CURRENCY 必须显式提供非空币种;
 * - 方向/单位/状态/类型只承认已声明枚举,不使用宽松 string。
 */

/** 非负十进制字符串:整数或最多 18 位小数,拒绝负号、科学计数法、前导+、空串。 */
const decimalString = z
  .string()
  .regex(/^\d+(\.\d{1,18})?$/, '金额必须是非负十进制字符串');

/** 秒级 ISO 8601(允许时区偏移)。 */
const isoSeconds = z
  .string()
  .regex(
    /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d{3})?(Z|[+-]\d{2}:\d{2})$/,
    '时间必须为秒级 ISO 8601',
  );

/** IANA 时区:非空字符串,由调用方在生成时校验;此处只保证 shape。 */
const ianaTimezone = z.string().min(1);

export const aggregationGranularitySchema = z.enum(['FIVE_MINUTES', 'HOUR', 'DAY']);
export type AggregationGranularity = z.output<typeof aggregationGranularitySchema>;

export const availabilitySchema = z.enum(['AVAILABLE', 'PARTIAL', 'UNAVAILABLE']);
export type Availability = z.output<typeof availabilitySchema>;

export const transactionTypeSchema = z.enum(['TOPUP', 'CONSUMPTION', 'REFUND']);
export type TransactionType = z.output<typeof transactionTypeSchema>;

export const transactionDirectionSchema = z.enum(['CREDIT', 'DEBIT']);
export type TransactionDirection = z.output<typeof transactionDirectionSchema>;

export const transactionUnitSchema = z.enum(['QUOTA', 'CURRENCY']);
export type TransactionUnit = z.output<typeof transactionUnitSchema>;

export const transactionStatusSchema = z.enum([
  'PENDING',
  'SUCCEEDED',
  'FAILED',
  'REFUNDED',
  'UNKNOWN',
]);
export type TransactionStatus = z.output<typeof transactionStatusSchema>;

/** 服务端回显的规范化范围。 */
export const aggregationRangeSchema = z
  .object({
    start: isoSeconds,
    end: isoSeconds,
    timezone: ianaTimezone,
    granularity: aggregationGranularitySchema,
  })
  .strict();
export type AggregationRange = z.output<typeof aggregationRangeSchema>;

/** recordCount:value 为十进制字符串,单位固定 records。 */
export const recordCountSchema = z
  .object({
    value: decimalString.nullable(),
    unit: z.literal('records'),
    availability: availabilitySchema,
    reasonCode: z.string().nullable(),
  })
  .strict()
  .superRefine((val, ctx) => {
    if (val.availability === 'AVAILABLE') {
      if (val.value === null) ctx.addIssue({ code: 'custom', message: 'recordCount AVAILABLE 必须带值', path: ['value'] });
      if (val.reasonCode !== null) ctx.addIssue({ code: 'custom', message: 'recordCount AVAILABLE 不得带 reasonCode', path: ['reasonCode'] });
    } else {
      if (val.value !== null) ctx.addIssue({ code: 'custom', message: 'recordCount 非 AVAILABLE 必须 value=null', path: ['value'] });
      if (!val.reasonCode) ctx.addIssue({ code: 'custom', message: 'recordCount 非 AVAILABLE 必须带 reasonCode', path: ['reasonCode'] });
    }
  });

/** quotaTotal:value 为十进制字符串,单位固定 quota。 */
export const quotaTotalSchema = z
  .object({
    value: decimalString.nullable(),
    unit: z.literal('quota'),
    availability: availabilitySchema,
    reasonCode: z.string().nullable(),
  })
  .strict()
  .superRefine((val, ctx) => {
    if (val.availability === 'AVAILABLE') {
      if (val.value === null) ctx.addIssue({ code: 'custom', message: 'quotaTotal AVAILABLE 必须带值', path: ['value'] });
      if (val.reasonCode !== null) ctx.addIssue({ code: 'custom', message: 'quotaTotal AVAILABLE 不得带 reasonCode', path: ['reasonCode'] });
    } else {
      if (val.value !== null) ctx.addIssue({ code: 'custom', message: 'quotaTotal 非 AVAILABLE 必须 value=null', path: ['value'] });
      if (!val.reasonCode) ctx.addIssue({ code: 'custom', message: 'quotaTotal 非 AVAILABLE 必须带 reasonCode', path: ['reasonCode'] });
    }
  });

/** moneyTotal:正式货币金额,unit=CURRENCY 时 currency 必填;UNAVAILABLE 时 value/currency 均为 null。 */
export const moneyTotalSchema = z
  .object({
    value: decimalString.nullable(),
    currency: z.string().min(1).nullable(),
    availability: availabilitySchema,
    reasonCode: z.string().nullable(),
  })
  .strict()
  .superRefine((val, ctx) => {
    if (val.availability === 'AVAILABLE') {
      if (val.value === null) ctx.addIssue({ code: 'custom', message: 'moneyTotal AVAILABLE 必须带 value', path: ['value'] });
      if (val.currency === null || val.currency === '') ctx.addIssue({ code: 'custom', message: 'moneyTotal AVAILABLE 必须带 currency', path: ['currency'] });
      if (val.reasonCode !== null) ctx.addIssue({ code: 'custom', message: 'moneyTotal AVAILABLE 不得带 reasonCode', path: ['reasonCode'] });
    } else {
      if (val.value !== null) ctx.addIssue({ code: 'custom', message: 'moneyTotal 非 AVAILABLE 必须 value=null', path: ['value'] });
      if (val.currency !== null) ctx.addIssue({ code: 'custom', message: 'moneyTotal 非 AVAILABLE 必须 currency=null', path: ['currency'] });
      if (!val.reasonCode) ctx.addIssue({ code: 'custom', message: 'moneyTotal 非 AVAILABLE 必须带 reasonCode', path: ['reasonCode'] });
    }
  });

/** 消费汇总的 coverage:只有 CONSUMPTION 一种来源。 */
export const consumptionCoverageSchema = z
  .object({
    type: z.literal('CONSUMPTION'),
    availability: availabilitySchema,
    reasonCode: z.string().nullable(),
  })
  .strict()
  .superRefine((val, ctx) => {
    if (val.availability === 'AVAILABLE' && val.reasonCode !== null) {
      ctx.addIssue({ code: 'custom', message: 'AVAILABLE 不得带 reasonCode', path: ['reasonCode'] });
    }
    if (val.availability !== 'AVAILABLE' && !val.reasonCode) {
      ctx.addIssue({ code: 'custom', message: '非 AVAILABLE 必须带 reasonCode', path: ['reasonCode'] });
    }
  });

export const consumptionSummarySchema = z
  .object({
    baselineVersion: z.string().min(1),
    range: aggregationRangeSchema,
    recordCount: recordCountSchema,
    quotaTotal: quotaTotalSchema,
    moneyTotal: moneyTotalSchema,
    coverage: consumptionCoverageSchema,
  })
  .strict();
export type ConsumptionSummary = z.output<typeof consumptionSummarySchema>;

/** 单条统一流水。 */
export const transactionItemSchema = z
  .object({
    transactionId: z.string().min(1),
    occurredAt: isoSeconds,
    type: transactionTypeSchema,
    direction: transactionDirectionSchema,
    amount: decimalString,
    unit: transactionUnitSchema,
    currency: z.string().min(1).nullable(),
    status: transactionStatusSchema,
    remark: z.string().nullable(),
    referenceId: z.string().nullable(),
  })
  .strict()
  .superRefine((val, ctx) => {
    if (val.unit === 'QUOTA' && val.currency !== null) {
      ctx.addIssue({ code: 'custom', message: 'QUOTA 必须 currency=null', path: ['currency'] });
    }
    if (val.unit === 'CURRENCY' && (val.currency === null || val.currency === '')) {
      ctx.addIssue({ code: 'custom', message: 'CURRENCY 必须提供非空 currency', path: ['currency'] });
    }
  });
export type TransactionItem = z.output<typeof transactionItemSchema>;

/** 流水 coverage:逐来源可用性,reasonCode 规则与汇总一致。 */
export const transactionCoverageSchema = z
  .object({
    type: transactionTypeSchema,
    availability: availabilitySchema,
    reasonCode: z.string().nullable(),
  })
  .strict()
  .superRefine((val, ctx) => {
    if (val.availability === 'AVAILABLE' && val.reasonCode !== null) {
      ctx.addIssue({ code: 'custom', message: 'AVAILABLE 不得带 reasonCode', path: ['reasonCode'] });
    }
    if (val.availability !== 'AVAILABLE' && !val.reasonCode) {
      ctx.addIssue({ code: 'custom', message: '非 AVAILABLE 必须带 reasonCode', path: ['reasonCode'] });
    }
  });
export type TransactionCoverage = z.output<typeof transactionCoverageSchema>;

/** 统一流水响应。coverage 至少 1 项,且同一 type 不得重复。 */
export const transactionsResponseSchema = z
  .object({
    baselineVersion: z.string().min(1),
    range: aggregationRangeSchema,
    type: transactionTypeSchema.nullable(),
    page: z.number().int().min(1),
    pageSize: z.number().int().min(1),
    total: z.number().int().nonnegative(),
    availability: availabilitySchema,
    reasonCode: z.string().nullable(),
    coverage: z.array(transactionCoverageSchema).min(1),
    items: z.array(transactionItemSchema),
  })
  .strict()
  .superRefine((val, ctx) => {
    const seen = new Set<string>();
    for (const cov of val.coverage) {
      if (seen.has(cov.type)) {
        ctx.addIssue({ code: 'custom', message: `coverage 类型 ${cov.type} 重复`, path: ['coverage'] });
      }
      seen.add(cov.type);
    }
    if (val.availability === 'AVAILABLE' && val.reasonCode !== null) {
      ctx.addIssue({ code: 'custom', message: 'AVAILABLE 不得带 reasonCode', path: ['reasonCode'] });
    }
    if (val.availability !== 'AVAILABLE' && val.reasonCode !== null && val.reasonCode === '') {
      ctx.addIssue({ code: 'custom', message: 'reasonCode 不允许空字符串', path: ['reasonCode'] });
    }
  });
export type TransactionsResponse = z.output<typeof transactionsResponseSchema>;
