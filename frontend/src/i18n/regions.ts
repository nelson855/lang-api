import type { SupportedLocale } from './locale';

export function regionName(code: string, locale: SupportedLocale): string {
  const normalized = code.trim().toUpperCase();
  if (normalized === '') {
    return code;
  }
  try {
    return new Intl.DisplayNames([locale], { type: 'region' }).of(normalized) ?? normalized;
  } catch {
    return normalized;
  }
}
