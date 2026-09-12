import { z } from 'zod';
import { portalRequest } from './portalClient';

const baseUrlSchema = z
  .string()
  .min(1)
  .refine((url) => /^https?:\/\/[^/@?#\s]+(\/[^?#\s]*)?$/.test(url), '非法公开地址');

export const publicConfigSchema = z
  .object({
    siteName: z.string().min(1),
    apiBaseUrls: z.array(
      z.object({
        protocol: z.string().regex(/^[A-Z][A-Z0-9_]*$/, '非法协议标识'),
        url: baseUrlSchema,
      }),
    ),
  })
  .strict();

export type PublicConfig = z.output<typeof publicConfigSchema>;

export const PUBLIC_CONFIG_PATH = '/portal/api/public-config';
export const PUBLIC_CONFIG_QUERY_KEY = ['portal', 'public-config'] as const;

export async function fetchPublicConfig(signal?: AbortSignal) {
  return portalRequest(PUBLIC_CONFIG_PATH, publicConfigSchema, signal ? { signal } : undefined);
}
