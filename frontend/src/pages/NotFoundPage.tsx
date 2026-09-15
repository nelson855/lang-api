import { useTranslation } from 'react-i18next';
import { NotFound } from '../components/feedback/Feedback';
import { SupportLink } from '../components/legal/SupportLink';
import { usePublicConfigData } from '../app/providers/publicConfigGate';
import './PublicPages.css';

export function NotFoundPage() {
  const { t } = useTranslation();
  const { supportUrl } = usePublicConfigData();
  return (
    <div className="public-page">
      <h1 hidden>404</h1>
      <NotFound />
      <p>
        <SupportLink url={supportUrl}>{t('pages.legal.contactSupport')}</SupportLink>
      </p>
    </div>
  );
}
