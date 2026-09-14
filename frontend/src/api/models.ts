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

export const MODELS_PATH = '/portal/api/models';
export const MODELS_QUERY_KEY = ['portal', 'models'] as const;

export async function fetchModels(signal?: AbortSignal) {
  return portalRequest(MODELS_PATH, catalogResponseSchema, signal ? { signal } : undefined);
}
