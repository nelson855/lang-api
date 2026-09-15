import { z } from 'zod';
import { portalRequest } from './portalClient';

const baseUrlSchema = z
  .string()
  .min(1)
  .refine((url) => /^https?:\/\/[^/@?#\s]+(\/[^?#\s]*)?$/.test(url), '非法公开地址');

const optionalSiteUrlSchema = z
  .string()
  .refine(
    (url) => url === '' || /^https?:\/\/[^/@?#\s]+(\/[^?#\s]*)?$/.test(url),
    '非法站点地址',
  );

const optionalSupportUrlSchema = z.string().refine((url) => {
  if (url === '') return true;
  if (url.startsWith('mailto:')) return url.slice('mailto:'.length).length > 0;
  return /^https:\/\/[^/@?#\s]+(\/[^?#\s]*)?$/.test(url);
}, '非法支持入口');

export const publicConfigSchema = z
  .object({
    siteName: z.string().min(1),
    publicationMode: z.enum(['PREVIEW', 'PUBLIC']),
    siteUrl: optionalSiteUrlSchema,
    supportUrl: optionalSupportUrlSchema,
    supportedRegions: z.array(z.string().regex(/^[A-Z]{2}$/, '非法地区代码')),
    enabledLocales: z.array(z.enum(['zh-CN', 'en-US'])),
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
