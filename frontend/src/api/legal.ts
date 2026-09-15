import { z } from 'zod';
import { useQuery } from '@tanstack/react-query';
import { portalRequest } from './portalClient';

export const legalDocumentSchema = z
  .object({
    type: z.enum(['TERMS', 'PRIVACY']),
    title: z.string().min(1),
    contentHtml: z.string().min(1),
    locale: z.enum(['zh-CN', 'en-US']),
  })
  .strict();

export type LegalDocument = z.output<typeof legalDocumentSchema>;

export const LEGAL_TERMS_PATH = '/portal/api/legal/terms';
export const LEGAL_PRIVACY_PATH = '/portal/api/legal/privacy';
export const LEGAL_TERMS_QUERY_KEY = ['portal', 'legal', 'terms'] as const;
export const LEGAL_PRIVACY_QUERY_KEY = ['portal', 'legal', 'privacy'] as const;

export async function fetchLegalDocument(
  path: typeof LEGAL_TERMS_PATH | typeof LEGAL_PRIVACY_PATH,
  signal?: AbortSignal,
) {
  return portalRequest(path, legalDocumentSchema, signal ? { signal } : undefined);
}

export function useLegalDocument(path: typeof LEGAL_TERMS_PATH | typeof LEGAL_PRIVACY_PATH) {
  const queryKey = path === LEGAL_TERMS_PATH ? LEGAL_TERMS_QUERY_KEY : LEGAL_PRIVACY_QUERY_KEY;
  return useQuery({
    queryKey,
    queryFn: ({ signal }) => fetchLegalDocument(path, signal).then((response) => response.data),
    staleTime: 5 * 60 * 1000,
    gcTime: 30 * 60 * 1000,
    retry: false,
    refetchOnWindowFocus: false,
  });
}
