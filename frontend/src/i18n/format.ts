import type { SupportedLocale } from './locale';

export function formatDate(locale: SupportedLocale, value: Date | number): string {
  return new Intl.DateTimeFormat(locale).format(value);
}

export function formatNumber(locale: SupportedLocale, value: number): string {
  return new Intl.NumberFormat(locale).format(value);
}

export function formatMoney(
  locale: SupportedLocale,
  amount: number,
  currency?: string,
): string | null {
  if (!currency) {
    return null;
  }
  return new Intl.NumberFormat(locale, { style: 'currency', currency }).format(amount);
}

export function formatQuotaAmount(
  locale: SupportedLocale,
  value: number,
  unit?: string,
): string | null {
  if (!unit) {
    return null;
  }
  return `${new Intl.NumberFormat(locale).format(value)} ${unit}`;
}
