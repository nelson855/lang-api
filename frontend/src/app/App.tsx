import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { RouterProvider, createBrowserRouter } from 'react-router';
import { ToastProvider } from '../components/feedback/Toast';
import { DefaultAuthProvider } from '../features/auth/authState';
import { I18nProvider } from '../i18n/i18nProvider';
import {
  detectInitialLocale,
  loadSavedLocale,
  normalizeEnabledLocales,
  resolveLocale,
  type SupportedLocale,
} from '../i18n/locale';
import { AppQueryProvider } from './providers/queryProvider';
import { PublicConfigGate, usePublicConfigData } from './providers/publicConfigGate';
import { buildRoutes } from './router/routes';

const browserRouter = createBrowserRouter(buildRoutes());

function ConstrainLocaleToEnabled() {
  const { enabledLocales } = usePublicConfigData();
  const { i18n } = useTranslation();
  const serial = (enabledLocales ?? []).join('|');
  useEffect(() => {
    const allowed = normalizeEnabledLocales(enabledLocales);
    if (allowed.length > 0 && !allowed.includes(i18n.language as SupportedLocale)) {
      void i18n.changeLanguage(resolveLocale(loadSavedLocale(), [...navigator.languages], allowed));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [i18n, serial]);
  return null;
}

export function App({ initialLocale }: { initialLocale?: SupportedLocale }) {
  const [locale] = useState<SupportedLocale>(
    () => initialLocale ?? detectInitialLocale([...navigator.languages]),
  );
  return (
    <I18nProvider initialLocale={locale}>
      <AppQueryProvider>
        <PublicConfigGate>
          <ConstrainLocaleToEnabled />
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
