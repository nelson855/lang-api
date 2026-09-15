export const SUPPORTED_LOCALES = ['zh-CN', 'en-US'] as const;
export type SupportedLocale = (typeof SUPPORTED_LOCALES)[number];
export const DEFAULT_LOCALE: SupportedLocale = 'zh-CN';

const STORAGE_KEY = 'lang-api:locale';

function normalizeTag(tag: string): SupportedLocale | undefined {
  const lower = tag.trim().toLowerCase();
  if (lower.startsWith('zh')) {
    return 'zh-CN';
  }
  if (lower.startsWith('en')) {
    return 'en-US';
  }
  return undefined;
}

function normalizeSaved(value: string | null | undefined): SupportedLocale | undefined {
  if (value === null || value === undefined) {
    return undefined;
  }
  const trimmed = value.trim();
  if (trimmed === 'zh-CN' || trimmed === 'en-US') {
    return trimmed;
  }
  return undefined;
}

export function resolveLocale(
  saved: string | null | undefined,
  languages: readonly string[],
  enabled: readonly unknown[] = SUPPORTED_LOCALES,
): SupportedLocale {
  const allowed = normalizeEnabledLocales(enabled);
  if (allowed.length === 0) {
    return DEFAULT_LOCALE;
  }
  const fromStorage = normalizeSaved(saved);
  if (fromStorage && allowed.includes(fromStorage)) {
    return fromStorage;
  }
  for (const language of languages) {
    const mapped = normalizeTag(language);
    if (mapped && allowed.includes(mapped)) {
      return mapped;
    }
  }
  return allowed[0];
}

export function loadSavedLocale(): string | null {
  try {
    return localStorage.getItem(STORAGE_KEY);
  } catch {
    return null;
  }
}

export function persistLocale(locale: SupportedLocale): void {
  try {
    localStorage.setItem(STORAGE_KEY, locale);
  } catch {
    // 隐私模式等写入失败时保持本次会话语言，不阻断启动
  }
}

export function detectInitialLocale(languages: readonly string[]): SupportedLocale {
  return resolveLocale(loadSavedLocale(), languages);
}

export function supportedLanguages(): readonly SupportedLocale[] {
  return SUPPORTED_LOCALES;
}

export function normalizeEnabledLocales(values: readonly unknown[] = []): SupportedLocale[] {
  const allowed: SupportedLocale[] = [];
  for (const value of values) {
    if ((value === 'zh-CN' || value === 'en-US') && !allowed.includes(value)) {
      allowed.push(value);
    }
  }
  return allowed;
}

export function isLocaleAllowed(locale: string, enabled: readonly unknown[] = []): boolean {
  return normalizeEnabledLocales(enabled).includes(locale as SupportedLocale);
}
