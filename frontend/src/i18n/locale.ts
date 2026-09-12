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
): SupportedLocale {
  const fromStorage = normalizeSaved(saved);
  if (fromStorage) {
    return fromStorage;
  }
  for (const language of languages) {
    const mapped = normalizeTag(language);
    if (mapped) {
      return mapped;
    }
  }
  return DEFAULT_LOCALE;
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
