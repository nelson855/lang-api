import { I18nextProvider } from 'react-i18next';
import { useEffect, useState, type ReactNode } from 'react';
import type { i18n as I18nInstance } from 'i18next';
import { createI18nInstance } from './setup';
import { persistLocale, type SupportedLocale } from './locale';

function applyLocaleSideEffects(locale: string): void {
  document.documentElement.setAttribute('lang', locale);
  if (locale === 'zh-CN' || locale === 'en-US') {
    persistLocale(locale);
  }
}

export function I18nProvider({
  initialLocale,
  children,
}: {
  initialLocale: SupportedLocale;
  children: ReactNode;
}) {
  const [instance] = useState<I18nInstance>(() => createI18nInstance(initialLocale));

  useEffect(() => {
    applyLocaleSideEffects(instance.language);
    const handler = (locale: string) => applyLocaleSideEffects(locale);
    instance.on('languageChanged', handler);
    return () => {
      instance.off('languageChanged', handler);
    };
  }, [instance]);

  return <I18nextProvider i18n={instance}>{children}</I18nextProvider>;
}
