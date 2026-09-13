import { z } from 'zod';
import { fetchCsrf } from './auth';
import { portalRequest } from './portalClient';
import { buildPortalApiUrl } from './portalPath';

export const apiKeyStatusSchema = z.enum(['enabled', 'disabled', 'expired', 'exhausted']);

const quotaUnitSchema = z.literal('quota');

export const apiKeyQuotaSchema = z
  .object({
    unlimited: z.boolean(),
    remaining: z.number().int().nonnegative(),
    unit: quotaUnitSchema,
  })
  .strict();

export const apiKeyUsedQuotaSchema = z
  .object({
    value: z.number().int().nonnegative(),
    unit: quotaUnitSchema,
  })
  .strict();

export const apiKeyModelRestrictionsSchema = z
  .object({
    enabled: z.boolean(),
    models: z.array(z.string().min(1)),
  })
  .strict();

export const apiKeySchema = z
  .object({
    id: z.number().int().positive(),
    name: z.string().min(1),
    maskedKey: z.string().startsWith('sk-'),
    status: apiKeyStatusSchema,
    createdAt: z.string().datetime({ offset: true }),
    expiresAt: z.string().datetime({ offset: true }).nullable(),
    quota: apiKeyQuotaSchema,
    usedQuota: apiKeyUsedQuotaSchema,
    modelRestrictions: apiKeyModelRestrictionsSchema,
    allowedIps: z.array(z.string().min(1)),
  })
  .strict();

export const apiKeyPageSchema = z
  .object({
    items: z.array(apiKeySchema),
    page: z.number().int().min(1),
    pageSize: z.number().int().min(1),
    total: z.number().int().nonnegative(),
  })
  .strict();

export const createApiKeyResponseSchema = z.object({ created: z.literal(true) }).strict();
export const updateApiKeyResponseSchema = z.object({ updated: z.literal(true) }).strict();
export const apiKeyStatusResponseSchema = z.object({ enabled: z.boolean() }).strict();
export const deleteApiKeyResponseSchema = z.object({ deleted: z.literal(true) }).strict();

const singleSkPrefix = z
  .string()
  .startsWith('sk-')
  .refine((value) => !value.startsWith('sk-sk-'), '明文必须为单个 sk- 前缀');

export const revealSecretSchema = z.object({ secret: singleSkPrefix }).strict();

export type ApiKeyStatus = z.output<typeof apiKeyStatusSchema>;
export type ApiKey = z.output<typeof apiKeySchema>;
export type ApiKeyPage = z.output<typeof apiKeyPageSchema>;
export type RevealSecret = z.output<typeof revealSecretSchema>;

export interface ApiKeyListParams {
  page: number;
  pageSize: number;
  name?: string;
  status?: ApiKeyStatus | string;
}

export interface CreateApiKeyInput {
  name: string;
  unlimited?: boolean;
  remaining?: number;
  expiresAt?: string | null;
  models?: string[];
  ips?: string[];
}

export type UpdateApiKeyInput = Partial<CreateApiKeyInput>;

export const API_KEYS_PATH = '/portal/api/api-keys';
export const API_KEYS_QUERY_KEY = ['portal', 'api-keys'] as const;

export function listApiKeys(params: ApiKeyListParams, signal?: AbortSignal) {
  const url = buildPortalApiUrl(API_KEYS_PATH, {
    page: params.page,
    pageSize: params.pageSize,
    name: params.name?.trim() ? params.name.trim() : undefined,
    status: params.status?.trim() ? params.status.trim() : undefined,
  });
  return portalRequest(url, apiKeyPageSchema, signal ? { signal } : undefined);
}

export function fetchApiKey(id: number, signal?: AbortSignal) {
  return portalRequest(`${API_KEYS_PATH}/${id}`, apiKeySchema, signal ? { signal } : undefined);
}

export async function createApiKey(input: CreateApiKeyInput) {
  return apiKeyMutation(API_KEYS_PATH, 'POST', input, createApiKeyResponseSchema);
}

export async function updateApiKey(id: number, input: UpdateApiKeyInput) {
  return apiKeyMutation(`${API_KEYS_PATH}/${id}`, 'PUT', input, updateApiKeyResponseSchema);
}

export async function setApiKeyStatus(id: number, enabled: boolean) {
  return apiKeyMutation(
    `${API_KEYS_PATH}/${id}/status`,
    'PUT',
    { enabled },
    apiKeyStatusResponseSchema,
  );
}

export async function deleteApiKey(id: number) {
  return apiKeyMutation(`${API_KEYS_PATH}/${id}`, 'DELETE', undefined, deleteApiKeyResponseSchema);
}

export async function revealApiKey(id: number, signal?: AbortSignal) {
  const csrf = await fetchCsrf();
  return portalRequest(`${API_KEYS_PATH}/${id}/reveal`, revealSecretSchema, {
    method: 'POST',
    headers: { 'X-XSRF-TOKEN': csrf.data.token },
    signal,
  });
}

async function apiKeyMutation<T>(
  path: string,
  method: string,
  body: unknown,
  schema: z.ZodType<T>,
): Promise<{ data: T; requestId: string }> {
  const csrf = await fetchCsrf();
  return portalRequest(path, schema, {
    method,
    body,
    headers: { 'X-XSRF-TOKEN': csrf.data.token },
  });
}
