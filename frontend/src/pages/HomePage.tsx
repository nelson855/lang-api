import { Link } from 'react-router';
import { useTranslation } from 'react-i18next';
import { usePublicConfigData } from '../app/providers/publicConfigGate';

export function HomePage() {
  const { t } = useTranslation();
  const config = usePublicConfigData();
  const isPreview = (config.publicationMode ?? 'PREVIEW') !== 'PUBLIC';
  const protocols = config.apiBaseUrls ?? [];
  return (
    <>
      <h1>{t('pages.home.title')}</h1>
      <p>{t('pages.home.subtitle', { siteName: config.siteName })}</p>
      {isPreview ? <p>{t('pages.home.previewNote')}</p> : null}
      {protocols.length === 0 ? <p>{t('pages.home.modelsUnavailable')}</p> : null}
      <nav aria-label={t('pages.home.title')}>
        <Link to="/models">{t('nav.models')}</Link> <Link to="/docs">{t('nav.docs')}</Link>{' '}
        <Link to="/regions">{t('nav.regions')}</Link>
      </nav>
      <nav aria-label={t('nav.legal')}>
        <Link to="/terms">{t('nav.terms')}</Link> <Link to="/privacy">{t('nav.privacy')}</Link>
      </nav>
    </>
  );
}
