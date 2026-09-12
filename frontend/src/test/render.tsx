import { render, type RenderOptions } from '@testing-library/react';
import type { ReactElement, ReactNode } from 'react';
import { MemoryRouter } from 'react-router';
import { I18nProvider } from '../i18n/i18nProvider';
import type { SupportedLocale } from '../i18n/locale';

export interface LocaleRenderOptions extends Omit<RenderOptions, 'wrapper'> {
  initialEntries?: string[];
}

export function renderWithLocale(
  ui: ReactElement,
  locale: SupportedLocale = 'zh-CN',
  options?: LocaleRenderOptions,
) {
  const { initialEntries, ...renderOptions } = options ?? {};
  return render(ui, {
    ...renderOptions,
    wrapper: ({ children }: { children: ReactNode }) => (
      <MemoryRouter initialEntries={initialEntries}>
        <I18nProvider initialLocale={locale}>{children}</I18nProvider>
      </MemoryRouter>
    ),
  });
}
