import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render } from '@testing-library/react';
import type { ReactElement, ReactNode } from 'react';
import { MemoryRouter } from 'react-router';
import { AuthStateProvider, type AuthStatus } from '../features/auth/authState';
import { ToastProvider } from '../components/feedback/Toast';
import { PublicConfigGate } from '../app/providers/publicConfigGate';
import { I18nProvider } from '../i18n/i18nProvider';
import type { SupportedLocale } from '../i18n/locale';

export interface AppRenderOptions {
  locale?: SupportedLocale;
  authStatus?: AuthStatus;
  initialEntries?: string[];
}

export function renderApp(ui: ReactElement, options: AppRenderOptions = {}) {
  const { locale = 'zh-CN', authStatus = 'anonymous', initialEntries = ['/'] } = options;
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  function Wrapper({ children }: { children: ReactNode }) {
    return (
      <MemoryRouter initialEntries={initialEntries}>
        <I18nProvider initialLocale={locale}>
          <QueryClientProvider client={client}>
            <PublicConfigGate>
              <AuthStateProvider status={authStatus}>
                <ToastProvider>{children}</ToastProvider>
              </AuthStateProvider>
            </PublicConfigGate>
          </QueryClientProvider>
        </I18nProvider>
      </MemoryRouter>
    );
  }
  return render(ui, { wrapper: Wrapper });
}
