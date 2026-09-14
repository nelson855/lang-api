import { useEffect, useMemo, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { PortalApiError } from '../api/envelope';
import type { TopupRecord } from '../api/wallet';
import { Empty } from '../components/feedback/Feedback';
import { Button } from '../components/ui/Button';
import { DataTable } from '../components/ui/DataTable';
import { Pagination } from '../components/ui/Pagination';
import { useAuthProfile } from '../features/auth/authState';
import { useBalanceQuery } from '../features/usage/useUsage';
import { handleWalletQueryError, useTopupOptionsQuery, useTopupRecordsQuery } from '../features/wallet/useWallet';
import './WalletPage.css';

const PAGE_SIZE = 20;

function nonAuth(error: unknown): boolean {
  return error instanceof PortalApiError && error.code !== 'UNAUTHENTICATED';
}

export function WalletPage() {
  const { t } = useTranslation();
  const profile = useAuthProfile();
  const userId = profile?.id ?? 0;
  const queryClient = useQueryClient();
  const [page, setPage] = useState(1);

  const balance = useBalanceQuery(userId);
  const options = useTopupOptionsQuery(userId);
  const records = useTopupRecordsQuery(userId, page, PAGE_SIZE);

  const sessionError =
    balance.error instanceof PortalApiError && balance.error.code === 'UNAUTHENTICATED'
      ? balance.error
      : options.error instanceof PortalApiError && options.error.code === 'UNAUTHENTICATED'
        ? options.error
        : records.error instanceof PortalApiError && records.error.code === 'UNAUTHENTICATED'
          ? records.error
          : null;
  useEffect(() => {
    if (sessionError) {
      handleWalletQueryError(queryClient, sessionError);
    }
  }, [queryClient, sessionError]);

  const balanceData = balance.data?.data;
  const optionsData = options.data?.data;
  const recordsData = records.data?.data;

  const columns = useMemo(
    () => [
      { key: 'orderId', header: t('pages.wallet.colOrder'), render: (row: TopupRecord) => row.orderId },
      {
        key: 'amount',
        header: t('pages.wallet.colAmount'),
        render: (row: TopupRecord) => `${row.requestedAmount} ${row.currency}`,
      },
      { key: 'method', header: t('pages.wallet.colMethod'), render: (row: TopupRecord) => t(`pages.wallet.method${row.paymentMethod}`) },
      { key: 'status', header: t('pages.wallet.colStatus'), render: (row: TopupRecord) => t(`pages.wallet.status${row.status}`) },
      { key: 'createdAt', header: t('pages.wallet.colCreated'), render: (row: TopupRecord) => row.createdAt },
      {
        key: 'completedAt',
        header: t('pages.wallet.colCompleted'),
        render: (row: TopupRecord) => row.completedAt ?? '—',
      },
    ],
    [t],
  );

  return (
    <div className="wallet-page">
      <h1>{t('pages.wallet.title')}</h1>

      <section aria-label={t('pages.wallet.balanceTitle')}>
        <h2>{t('pages.wallet.balanceTitle')}</h2>
        <p className="wallet-hint">{t('pages.wallet.balanceHint')}</p>
        {balance.isPending ? <p>{t('states.loading')}</p> : null}
        {balance.isError && nonAuth(balance.error) ? (
          <div className="wallet-notice" role="alert">
            <p>{t('pages.wallet.loadError')}</p>
            <Button type="button" variant="secondary" onClick={() => void balance.refetch()}>
              {t('common.retry')}
            </Button>
          </div>
        ) : null}
        {balanceData ? (
          <p>
            {balanceData.amount} {balanceData.currency} <span>({balanceData.quota} quota)</span>
          </p>
        ) : null}
      </section>

      <section aria-label={t('pages.wallet.disabledTitle')}>
        <h2>{t('pages.wallet.disabledTitle')}</h2>
        {options.isPending ? <p>{t('states.loading')}</p> : null}
        {options.isError && nonAuth(options.error) ? (
          <div className="wallet-notice" role="alert">
            <p>{t('pages.wallet.optionsUnavailable')}</p>
            <Button type="button" variant="secondary" onClick={() => void options.refetch()}>
              {t('common.retry')}
            </Button>
          </div>
        ) : null}
        {optionsData && !optionsData.enabled ? <p>{t('pages.wallet.disabledBody')}</p> : null}
      </section>

      <section aria-label={t('pages.wallet.recordsTitle')}>
        <h2>{t('pages.wallet.recordsTitle')}</h2>
        <p className="wallet-hint">{t('pages.wallet.windowNote')}</p>
        {records.isPending ? <p>{t('states.loading')}</p> : null}
        {records.isError && nonAuth(records.error) ? (
          <div className="wallet-notice" role="alert">
            <p>{t('pages.wallet.loadError')}</p>
            <Button type="button" variant="secondary" onClick={() => void records.refetch()}>
              {t('common.retry')}
            </Button>
          </div>
        ) : null}
        {recordsData ? (
          recordsData.total === 0 ? (
            <Empty description={t('pages.wallet.emptyRecords')} />
          ) : (
            <>
              <DataTable<TopupRecord>
                columns={columns}
                rows={recordsData.items}
                rowKey={(row, index) => `${row.orderId}-${index}`}
              />
              <div className="wallet-cards">
                {recordsData.items.map((row, index) => (
                  <article key={`${row.orderId}-${index}`} className="wallet-card">
                    <p>
                      {t('pages.wallet.colOrder')}: {row.orderId}
                    </p>
                    <p>
                      {t('pages.wallet.colAmount')}: {row.requestedAmount} {row.currency}
                    </p>
                    <p>
                      {t('pages.wallet.colMethod')}: {t(`pages.wallet.method${row.paymentMethod}`)}
                    </p>
                    <p>
                      {t('pages.wallet.colStatus')}: {t(`pages.wallet.status${row.status}`)}
                    </p>
                    <p>
                      {t('pages.wallet.colCreated')}: {row.createdAt}
                    </p>
                  </article>
                ))}
              </div>
              <Pagination
                page={recordsData.page}
                pageSize={recordsData.pageSize}
                total={recordsData.total}
                onChange={(next) => setPage(next)}
              />
            </>
          )
        ) : null}
      </section>
    </div>
  );
}
