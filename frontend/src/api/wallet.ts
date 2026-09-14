import { z } from 'zod';
import { authProfileSchema, fetchCsrf } from './auth';
import { portalRequest } from './portalClient';
import { buildPortalApiUrl } from './portalPath';

const decimalString = z.string().regex(/^\d+(\.\d{1,6})?$/);

const paymentMethodSchema = z.enum(['ALIPAY', 'WXPAY', 'STRIPE', 'CREEM', 'WAFFO', 'OTHER']);

export const topupOptionsSchema = z
  .object({
    enabled: z.boolean(),
    methods: z.array(paymentMethodSchema),
    currency: z.literal('USD'),
    reason: z.enum(['NOT_CONFIGURED', 'UNSUPPORTED_PROVIDER']),
  })
  .strict();

export const topupRecordSchema = z
  .object({
    orderId: z.string().min(1),
    requestedAmount: decimalString,
    currency: z.literal('USD'),
    paymentMethod: paymentMethodSchema,
    status: z.enum(['PENDING', 'SUCCEEDED', 'FAILED', 'EXPIRED']),
    createdAt: z.string().datetime({ offset: true }),
    completedAt: z.string().datetime({ offset: true }).nullable(),
  })
  .strict();

export const topupPageSchema = z
  .object({
    items: z.array(topupRecordSchema),
    page: z.number().int().min(1),
    pageSize: z.number().int().min(1),
    total: z.number().int().nonnegative(),
  })
  .strict();

export type TopupOptions = z.output<typeof topupOptionsSchema>;
export type TopupRecord = z.output<typeof topupRecordSchema>;
export type TopupPage = z.output<typeof topupPageSchema>;

export interface ProfileUpdateInput {
  username: string;
  displayName: string | null;
  currentPassword: string;
  newPassword?: string;
  confirmPassword?: string;
}

export const TOPUP_OPTIONS_PATH = '/portal/api/account/topup-options';
export const TOPUPS_PATH = '/portal/api/account/topups';
export const PROFILE_UPDATE_PATH = '/portal/api/profile';
export const WALLET_QUERY_KEY = ['portal', 'wallet'] as const;

const TOPUP_MAX_PAGE_SIZE = 100;

export function buildTopupsUrl(page: number, pageSize: number): string {
  if (!Number.isInteger(page) || page < 1) {
    throw new Error('页码必须为正整数');
  }
  if (!Number.isInteger(pageSize) || pageSize < 1 || pageSize > TOPUP_MAX_PAGE_SIZE) {
    throw new Error('每页数量必须在 1～100 之间');
  }
  return buildPortalApiUrl(TOPUPS_PATH, { page, pageSize });
}

export function fetchTopupOptions(signal?: AbortSignal) {
  return portalRequest(TOPUP_OPTIONS_PATH, topupOptionsSchema, signal ? { signal } : undefined);
}

export function fetchTopupPage(page: number, pageSize: number, signal?: AbortSignal) {
  return portalRequest(buildTopupsUrl(page, pageSize), topupPageSchema, signal ? { signal } : undefined);
}

export async function updateProfile(input: ProfileUpdateInput) {
  const csrf = await fetchCsrf();
  const body: Record<string, string | null> = {
    username: input.username,
    displayName: input.displayName,
    currentPassword: input.currentPassword,
  };
  if (input.newPassword) {
    body.newPassword = input.newPassword;
    body.confirmPassword = input.confirmPassword ?? input.newPassword;
  }
  return portalRequest(PROFILE_UPDATE_PATH, authProfileSchema, {
    method: 'PUT',
    body,
    headers: { 'X-XSRF-TOKEN': csrf.data.token },
  });
}
