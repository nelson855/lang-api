import type { UseQueryResult } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';
import { PortalApiError } from '../../api/envelope';
import type { LegalDocument } from '../../api/legal';
import { SupportLink } from './SupportLink';
import { LegalDocumentContent } from './LegalDocumentContent';

export function LegalDocumentView({
  query,
  supportUrl = '',
}: {
  query: UseQueryResult<LegalDocument>;
  supportUrl?: string;
}) {
  const { t } = useTranslation();

  if (query.isPending) {
    return <p role="status">{t('states.loading')}</p>;
  }

  if (query.isError) {
    if (query.error instanceof PortalApiError && query.error.code === 'NOT_FOUND') {
      return (
        <section aria-labelledby="legal-unpublished-title">
          <h1 id="legal-unpublished-title">{t('pages.legal.unpublishedTitle')}</h1>
          <p>{t('pages.legal.unpublishedBody')}</p>
          <p>
            <SupportLink url={supportUrl}>{t('pages.legal.contactSupport')}</SupportLink>
          </p>
          <p>
            <Link to="/">{t('common.backHome')}</Link>
          </p>
        </section>
      );
    }
    return (
      <section aria-labelledby="legal-unavailable-title">
        <h1 id="legal-unavailable-title">{t('pages.legal.unavailableTitle')}</h1>
        <p>{t('pages.legal.unavailableBody')}</p>
        <p>
          <button type="button" onClick={() => void query.refetch()}>
            {t('common.retry')}
          </button>
        </p>
      </section>
    );
  }

  if (!query.data) {
    return <p role="status">{t('states.loading')}</p>;
  }

  return (
    <LegalDocumentContent title={query.data.title} contentHtml={query.data.contentHtml} locale={query.data.locale} />
  );
}
