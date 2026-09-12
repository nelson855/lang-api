import { useEffect, useRef } from 'react';
import { useLocation } from 'react-router';
import { useTranslation } from 'react-i18next';
import { metaForPath } from '../app/router/routeMeta';
import { usePublicConfigData } from '../app/providers/publicConfigGate';

export function usePageChrome() {
  const { t, i18n } = useTranslation();
  const config = usePublicConfigData();
  const location = useLocation();
  const mainRef = useRef<HTMLElement>(null);
  const meta = metaForPath(location.pathname);

  useEffect(() => {
    document.title = `${t(meta.titleKey)} - ${config.siteName}`;
    const heading = mainRef.current?.querySelector('h1');
    if (heading instanceof HTMLElement) {
      heading.setAttribute('tabindex', '-1');
      heading.focus({ preventScroll: true });
    }
  }, [location.pathname, t, i18n.language, config.siteName, meta.titleKey]);

  return { mainRef };
}
