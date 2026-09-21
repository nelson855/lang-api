import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router';
import { useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { PortalApiError } from '../api/envelope';
import { Empty } from '../components/feedback/Feedback';
import { Button } from '../components/ui/Button';
import { Select } from '../components/ui/Select';
import { useAuthProfile } from '../features/auth/authState';
import { normalizeUsageRange } from '../features/usage/usageCache';
import { handleUsageQueryError, useBalanceQuery, useSummaryQuery, useTimeseriesQuery } from '../features/usage/useUsage';
import './DashboardPage.css';

type RangePreset = '24h' | '7d' | '30d';

const PRESET_HOURS: Record<RangePreset, number> = { '24h': 24, '7d': 24 * 7, '30d': 24 * 30 };

function unauthenticatedOf(...errors: unknown[]): PortalApiError | null {
  for (const error of errors) {
    if (error instanceof PortalApiError && error.code === 'UNAUTHENTICATED') {
      return error;
    }
  }
  return null;
}

export function DashboardPage() {
  const { t } = useTranslation();
  const profile = useAuthProfile();
  const userId = profile?.id ?? 0;
  const queryClient = useQueryClient();
  const [preset, setPreset] = useState<RangePreset>('24h');

  const range = useMemo(() => {
    const end = Date.now();
    const start = end - PRESET_HOURS[preset] * 60 * 60 * 1000;
    return normalizeUsageRange(new Date(start).toISOString(), new Date(end).toISOString(), end);
  }, [preset]);

  const balance = useBalanceQuery(userId);
  const summary = useSummaryQuery(userId, range);
  const timeseries = useTimeseriesQuery(userId, range);

  const sessionError = unauthenticatedOf(balance.error, summary.error, timeseries.error);
  useEffect(() => {
    if (sessionError) {
      handleUsageQueryError(queryClient, sessionError);
    }
  }, [queryClient, sessionError]);

  const balanceData = balance.data?.data;
  const summaryData = summary.data?.data;
  const seriesData = timeseries.data?.data;

  return (
    <>
      <h1>{t('pages.dashboard.title')}</h1>
      <div className="dashboard-toolbar">
        <label className="dashboard-field" htmlFor="dashboard-range">
          {t('pages.dashboard.rangeLabel')}
        </label>
        <Select
          id="dashboard-range"
          density="compact"
          value={preset}
          onValueChange={(value) => setPreset(value as RangePreset)}
          options={[
            { value: '24h', label: t('pages.dashboard.range24h') },
            { value: '7d', label: t('pages.dashboard.range7d') },
            { value: '30d', label: t('pages.dashboard.range30d') },
          ]}
        />
      </div>

      <section aria-label={t('pages.dashboard.balanceTitle')}>
        <h2>{t('pages.dashboard.balanceTitle')}</h2>
        <p className="dashboard-hint">{t('pages.dashboard.balanceHint')}</p>
        {balance.isPending ? <p>{t('states.loading')}</p> : null}
        {balance.isError && !(balance.error instanceof PortalApiError && balance.error.code === 'UNAUTHENTICATED') ? (
          <div className="dashboard-notice" role="alert">
            <p>{t('pages.dashboard.loadError')}</p>
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
        <p>
          <Link to="/dashboard/wallet">{t('pages.dashboard.viewWallet')}</Link>
        </p>
      </section>

      <section aria-label={t('pages.dashboard.summaryTitle')}>
        <h2>{t('pages.dashboard.summaryTitle')}</h2>
        <p className="dashboard-hint">{t('pages.dashboard.summaryHint')}</p>
        {summary.isPending ? <p>{t('states.loading')}</p> : null}
        {summary.isError && !(summary.error instanceof PortalApiError && summary.error.code === 'UNAUTHENTICATED') ? (
          <div className="dashboard-notice" role="alert">
            <p>{t('pages.dashboard.loadError')}</p>
            <Button type="button" variant="secondary" onClick={() => void summary.refetch()}>
              {t('common.retry')}
            </Button>
          </div>
        ) : null}
        {summaryData ? (
          <dl>
            <div>
              <dt>{t('pages.dashboard.costLabel')}</dt>
              <dd>
                {summaryData.amount} {summaryData.currency} <span>({summaryData.quota} quota)</span>
              </dd>
            </div>
            <div>
              <dt>{t('pages.dashboard.rpmLabel')}</dt>
              <dd>{summaryData.rpm}</dd>
            </div>
            <div>
              <dt>{t('pages.dashboard.tpmLabel')}</dt>
              <dd>{summaryData.tpm}</dd>
            </div>
          </dl>
        ) : null}
      </section>

      <section aria-label={t('pages.dashboard.trendTitle')}>
        <h2>{t('pages.dashboard.trendTitle')}</h2>
        <p className="dashboard-hint">{t('pages.dashboard.trendHint')}</p>
        {timeseries.isPending ? <p>{t('states.loading')}</p> : null}
        {timeseries.isError && !(timeseries.error instanceof PortalApiError && timeseries.error.code === 'UNAUTHENTICATED') ? (
          <div className="dashboard-notice" role="alert">
            <p>{t('pages.dashboard.loadError')}</p>
            <Button type="button" variant="secondary" onClick={() => void timeseries.refetch()}>
              {t('common.retry')}
            </Button>
          </div>
        ) : null}
        {seriesData ? (
          seriesData.points.length === 0 ? (
            <Empty description={t('pages.dashboard.emptyTrend')} />
          ) : (
            <ul className="dashboard-trend">
              {seriesData.points.map((point) => (
                <li key={point.bucketStart}>
                  <span>{point.bucketStart}</span>{' '}
                  <span>
                    {t('pages.dashboard.requestsLabel')}: {point.requestCount}
                  </span>{' '}
                  <span>
                    {t('pages.dashboard.costLabel')}: {point.amount} USD
                  </span>
                </li>
              ))}
            </ul>
          )
        ) : null}
      </section>
    </>
  );
}
