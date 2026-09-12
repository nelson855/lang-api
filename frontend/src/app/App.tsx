import { useState } from 'react';
import { RouterProvider, createBrowserRouter } from 'react-router';
import { ToastProvider } from '../components/feedback/Toast';
import { DefaultAuthProvider } from '../features/auth/authState';
import { I18nProvider } from '../i18n/i18nProvider';
import { detectInitialLocale, type SupportedLocale } from '../i18n/locale';
import { AppQueryProvider } from './providers/queryProvider';
import { PublicConfigGate } from './providers/publicConfigGate';
import { buildRoutes } from './router/routes';

const browserRouter = createBrowserRouter(buildRoutes());

export function App({ initialLocale }: { initialLocale?: SupportedLocale }) {
  const [locale] = useState<SupportedLocale>(
    () => initialLocale ?? detectInitialLocale([...navigator.languages]),
  );
  return (
    <I18nProvider initialLocale={locale}>
      <AppQueryProvider>
        <PublicConfigGate>
          <DefaultAuthProvider>
            <ToastProvider>
              <RouterProvider router={browserRouter} />
            </ToastProvider>
          </DefaultAuthProvider>
        </PublicConfigGate>
      </AppQueryProvider>
    </I18nProvider>
  );
}
