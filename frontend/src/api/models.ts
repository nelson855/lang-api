import { z } from 'zod';
import { portalRequest } from './portalClient';

const pricingSchema = z
  .object({
    mode: z.enum(['TOKEN', 'REQUEST']),
    currency: z.literal('USD'),
    unit: z.enum(['PER_MILLION_TOKENS', 'PER_REQUEST']),
    input: z.string().regex(/^\d+(\.\d{1,6})?$/).nullable(),
    output: z.string().regex(/^\d+(\.\d{1,6})?$/).nullable(),
    request: z.string().regex(/^\d+(\.\d{1,6})?$/).nullable(),
  })
  .strict()
  .refine(
    (p) =>
      (p.mode === 'TOKEN' && p.input !== null && p.output !== null && p.request === null) ||
      (p.mode === 'REQUEST' && p.request !== null && p.input === null && p.output === null),
    '非法价格组合',
  );

const modelSchema = z
  .object({
    id: z.string().min(1).max(128),
    displayName: z.string().nullable(),
    provider: z.string().nullable(),
    availability: z.enum(['AVAILABLE']),
    pricing: pricingSchema.nullable(),
  })
  .strict();

export const catalogResponseSchema = z
  .object({
    pricingVersion: z.string().nullable(),
    models: z.array(modelSchema),
  })
  .strict();

export type CatalogPricing = z.output<typeof pricingSchema>;
export type CatalogModel = z.output<typeof modelSchema>;
export type CatalogResponse = z.output<typeof catalogResponseSchema>;

const MODEL_ID_MAX = 128;
const CALENDAR_DATE = /^\d{4}-\d{2}-\d{2}$/;

const modalitySchema = z.enum(['TEXT', 'IMAGE', 'AUDIO', 'VIDEO', 'FILE']);

const capabilitiesSchema = z
  .object({
    toolCalling: z.boolean().nullable(),
    reasoning: z.boolean().nullable(),
    structuredOutput: z.boolean().nullable(),
    attachments: z.boolean().nullable(),
  })
  .strict();

/** 增强金额是接口合法的任意精度非负十进制，不套用基础价格最多 6 位小数的限制。 */
const nonNegativeDecimalSchema = z
  .string()
  .regex(/^\d+(\.\d+)?([eE][+-]?\d+)?$/, '增强价格必须是非负十进制字符串');

const enhancedPricingItemSchema = z
  .object({
    type: z.enum([
      'CACHE_INPUT',
      'CACHE_OUTPUT',
      'IMAGE_INPUT',
      'IMAGE_OUTPUT',
      'AUDIO_INPUT',
      'AUDIO_OUTPUT',
      'VIDEO',
      'SEARCH',
    ]),
    currency: z.string().min(1).max(16),
    unit: z.string().min(1).max(64),
    price: nonNegativeDecimalSchema,
  })
  .strict();

export const modelDetailsSchema = modelSchema
  .extend({
    contextWindowTokens: z.number().int().nonnegative().nullable(),
    maxOutputTokens: z.number().int().nonnegative().nullable(),
    inputModalities: z.array(modalitySchema).nullable(),
    outputModalities: z.array(modalitySchema).nullable(),
    capabilities: capabilitiesSchema,
    releaseDate: z
      .string()
      .regex(CALENDAR_DATE)
      .refine((value) => !Number.isNaN(Date.parse(`${value}T00:00:00Z`)), '发布日期必须是真实日历日期')
      .nullable(),
    description: z.string().nullable(),
    tags: z.array(z.string().min(1).max(128)).nullable(),
    sortOrder: z.number().int().nullable(),
    enhancedPricing: z.array(enhancedPricingItemSchema).nullable(),
  })
  .strict();

export const catalogDetailResponseSchema = z
  .object({
    pricingVersion: z.string().nullable(),
    model: modelDetailsSchema,
  })
  .strict();

export const providerOptionSchema = z
  .object({
    value: z.string().min(1).max(128),
    label: z.string().min(1).max(128),
    modelCount: z.number().int().nonnegative(),
  })
  .strict();

export const catalogProvidersResponseSchema = z
  .object({
    pricingVersion: z.string().nullable(),
    providers: z.array(providerOptionSchema),
  })
  .strict();

export type ModelDetails = z.output<typeof modelDetailsSchema>;
export type ModelModality = z.output<typeof modalitySchema>;
export type EnhancedPricingItem = z.output<typeof enhancedPricingItemSchema>;
export type ProviderOption = z.output<typeof providerOptionSchema>;
export type CatalogDetailResponse = z.output<typeof catalogDetailResponseSchema>;
export type CatalogProvidersResponse = z.output<typeof catalogProvidersResponseSchema>;

export const MODEL_ID_MAX_LENGTH = MODEL_ID_MAX;

export const MODELS_PATH = '/portal/api/models';
export const MODEL_PROVIDERS_PATH = '/portal/api/model-providers';
export const MODELS_QUERY_KEY = ['portal', 'models'] as const;
export const MODEL_PROVIDERS_QUERY_KEY = ['portal', 'model-providers'] as const;

export function modelDetailPath(modelRef: string): string {
  return `${MODELS_PATH}/${modelRef}`;
}

export function modelDetailQueryKey(modelRef: string) {
  return [...MODELS_QUERY_KEY, 'detail', modelRef] as const;
}

export async function fetchModels(signal?: AbortSignal) {
  return portalRequest(MODELS_PATH, catalogResponseSchema, signal ? { signal } : undefined);
}

export async function fetchModelProviders(signal?: AbortSignal) {
  return portalRequest(
    MODEL_PROVIDERS_PATH,
    catalogProvidersResponseSchema,
    signal ? { signal } : undefined,
  );
}

export async function fetchModelDetail(modelRef: string, signal?: AbortSignal) {
  return portalRequest(
    modelDetailPath(modelRef),
    catalogDetailResponseSchema,
    signal ? { signal } : undefined,
  );
}
