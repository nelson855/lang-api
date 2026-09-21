import { Link } from 'react-router';
import { useTranslation } from 'react-i18next';
import type { ReactNode } from 'react';
import '../ui/Button.css';
import './Feedback.css';

function StateShell({ children }: { children: ReactNode }) {
  return <div className="page-state">{children}</div>;
}

export function Loading() {
  const { t } = useTranslation();
  return (
    <StateShell>
      <p role="status">{t('states.loading')}</p>
    </StateShell>
  );
}

export function Empty({ title, description }: { title?: string; description?: string }) {
  return (
    <StateShell>
      {title ? <h2 className="page-state-title">{title}</h2> : null}
      {description ? <p className="page-state-description">{description}</p> : null}
    </StateShell>
  );
}

export function RetryableError({
  requestId,
  onRetry,
}: {
  requestId?: string;
  onRetry: () => void;
}) {
  const { t } = useTranslation();
  return (
    <StateShell>
      <h2 className="page-state-title">{t('states.error.title')}</h2>
      {requestId ? (
        <p className="page-state-request-id request-id">
          {t('errors.requestId', { requestId })}
        </p>
      ) : null}
      <button type="button" className="btn btn-md btn-intent-neutral btn-emphasis-outline" onClick={onRetry}>
        {t('common.retry')}
      </button>
    </StateShell>
  );
}

export function Forbidden() {
  const { t } = useTranslation();
  return (
    <StateShell>
      <h2 className="page-state-title">{t('states.forbidden.title')}</h2>
      <p className="page-state-description">{t('states.forbidden.description')}</p>
      <Link to="/">{t('common.backHome')}</Link>
    </StateShell>
  );
}

export function NotFound() {
  const { t } = useTranslation();
  return (
    <StateShell>
      <h2 className="page-state-title">{t('pages.notFound.title')}</h2>
      <p className="page-state-description">{t('pages.notFound.description')}</p>
      <Link to="/">{t('common.backHome')}</Link>
    </StateShell>
  );
}
