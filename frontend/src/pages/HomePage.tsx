import { Link } from 'react-router';
import { useTranslation } from 'react-i18next';
import { usePublicConfigData } from '../app/providers/publicConfigGate';

export function HomePage() {
  const { t } = useTranslation();
  const config = usePublicConfigData();
  return (
    <>
      <h1>{t('pages.home.title')}</h1>
      <p>{t('pages.home.subtitle', { siteName: config.siteName })}</p>
      <p>{t('pages.home.statusNote')}</p>
      <nav aria-label={t('pages.home.title')}>
        <Link to="/models">{t('nav.models')}</Link> <Link to="/docs">{t('nav.docs')}</Link>
      </nav>
    </>
  );
}
