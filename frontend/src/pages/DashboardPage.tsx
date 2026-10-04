import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router';
import { useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { PortalApiError } from '../api/envelope';
import type { DashboardReasonCode, DashboardRecentRequestItem, DashboardStats } from '../api/dashboard';
import { Button } from '../components/ui/Button';
import { DataTable, type DataColumn } from '../components/ui/DataTable';
import { Select } from '../components/ui/Select';
import { useAuthProfile } from '../features/auth/authState';
import { useBalanceQuery } from '../features/usage/useUsage';
import {
  buildDashboardRange,
  DASHBOARD_PRESETS,
  type DashboardPreset,
} from '../features/dashboard/dashboardRange';
import { handleDashboardQueryError, useDashboardStatsQuery } from '../features/dashboard/useDashboard';
import { createDisplayFormatters, toMetricDisplay, toCollectionDisplay } from '../features/dashboard/displayModel';
import { MetricCard } from '../features/dashboard/MetricCard';
import { TrendChart, type TrendPointInput } from '../features/dashboard/TrendChart';
import './DashboardPage.css';

function browserTimezone(): string {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';
  } catch {
    return 'UTC';
  }
}

function unauthenticatedOf(...errors: unknown[]): PortalApiError | null {
  for (const error of errors) {
    if (error instanceof PortalApiError && error.code === 'UNAUTHENTICATED') {
      return error;
    }
  }
  return null;
}

const GRANULARITY_LABEL: Record<string, string> = {
  FIVE_MINUTES: 'pages.dashboard.granularityFIVE_MINUTES',
  HOUR: 'pages.dashboard.granularityHOUR',
  DAY: 'pages.dashboard.granularityDAY',
};

function MetricGrid({ stats, t, fmt }: { stats: DashboardStats; t: (k: string) => string; fmt: ReturnType<typeof createDisplayFormatters> }) {
  const m = stats.metrics;
  const items: Array<{
    label: string;
    kind: 'count' | 'money' | 'ratio' | 'average';
    availability: 'AVAILABLE' | 'UNAVAILABLE';
    reasonCode: DashboardReasonCode | null;
    value: number | string | null;
    currency?: string | null;
  }> = [
    { label: t('pages.dashboard.metricRequestTotal'), kind: 'count' as const, availability: m.requestTotal.availability, reasonCode: m.requestTotal.reasonCode, value: m.requestTotal.value, currency: null },
    { label: t('pages.dashboard.metricTokenUsage'), kind: 'count' as const, availability: m.tokenUsage.availability, reasonCode: m.tokenUsage.reasonCode, value: m.tokenUsage.value, currency: null },
    { label: t('pages.dashboard.metricSpend'), kind: 'money' as const, availability: m.spend.availability, reasonCode: m.spend.reasonCode, value: m.spend.value, currency: m.spend.currency },
    { label: t('pages.dashboard.metricActiveKeys'), kind: 'count' as const, availability: m.activeKeys.availability, reasonCode: m.activeKeys.reasonCode, value: m.activeKeys.value, currency: null },
    { label: t('pages.dashboard.metricSuccessRate'), kind: 'ratio' as const, availability: m.successRate.availability, reasonCode: m.successRate.reasonCode, value: m.successRate.value, currency: null },
    { label: t('pages.dashboard.metricAverageLatency'), kind: 'average' as const, availability: m.averageLatency.availability, reasonCode: m.averageLatency.reasonCode, value: m.averageLatency.value, currency: null },
  ];
  return (
    <dl className="dashboard-metrics">
      {items.map((item) => (
        <MetricCard
          key={item.label}
          label={item.label}
          display={toMetricDisplay(
            { kind: item.kind, availability: item.availability, reasonCode: item.reasonCode, value: item.value, currency: item.currency },
            fmt,
          )}
        />
      ))}
    </dl>
  );
}

function TrendRegion({
  title,
  description,
  availability,
  reasonCode,
  points,
  timezone,
  locale,
  t,
}: {
  title: string;
  description: string;
  availability: string;
  reasonCode: string | null;
  points: { bucketStart: string; bucketEnd: string; value: string }[];
  timezone: string;
  locale: string;
  t: (k: string) => string;
}) {
  const display = toCollectionDisplay(
    availability as 'AVAILABLE' | 'PARTIAL' | 'UNAVAILABLE',
    (reasonCode ?? null) as DashboardReasonCode | null,
  );
  if (display.status === 'value') {
    return (
      <TrendChart
        title={title}
        description={description}
        points={points as TrendPointInput[]}
        timezone={timezone}
        locale={locale}
      />
    );
  }
  const text = display.status === 'no-data' ? t('pages.dashboard.trendNoData') : t('pages.dashboard.trendUnavailable');
  return (
    <div className="dashboard-status" data-state={display.status}>
      <p>{text}</p>
    </div>
  );
}

function buildRequestLogsHref(range: { startTime: string; endTime: string }, item: DashboardRecentRequestItem): string {
  const params = new URLSearchParams({ startTime: range.startTime, endTime: range.endTime, result: 'SUCCESS', page: '1' });
  if (item.keyName) {
    params.set('keyName', item.keyName);
  }
  if (item.model) {
    params.set('model', item.model);
  }
  return `/dashboard/request-logs?${params.toString()}`;
}

function RecentRequests({
  stats,
  t,
  locale,
}: {
  stats: DashboardStats;
  t: (k: string) => string;
  locale: string;
}) {
  const recent = stats.recentRequests;
  const display = toCollectionDisplay(recent.availability, recent.reasonCode);
  const formatTime = (iso: string) => {
    try {
      return new Intl.DateTimeFormat(locale, { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }).format(new Date(iso));
    } catch {
      return iso;
    }
  };

  const columns: Array<DataColumn<DashboardRecentRequestItem>> = [
    { key: 'time', header: t('pages.dashboard.recentColTime'), render: (r) => formatTime(r.occurredAt) },
    { key: 'keyName', header: t('pages.dashboard.recentColKey'), render: (r) => r.keyName ?? t('pages.requestLogs.noData') },
    { key: 'model', header: t('pages.dashboard.recentColModel'), render: (r) => r.model ?? t('pages.requestLogs.noData') },
    {
      key: 'outcome',
      header: t('pages.dashboard.recentColOutcome'),
      render: (r) =>
        r.outcome === 'SUCCESS' ? t('pages.dashboard.recentOutcomeSuccess') : t('pages.dashboard.recentOutcomeError'),
    },
    { key: 'duration', header: t('pages.dashboard.recentColDuration'), render: (r) => `${r.durationMs} ms` },
    {
      key: 'logs',
      header: t('pages.dashboard.viewLogs'),
      render: (r) => (
        <Link to={buildRequestLogsHref(stats.range, r)}>{t('pages.dashboard.viewLogs')}</Link>
      ),
    },
  ];

  if (display.status === 'value' || display.status === 'partial') {
    return (
      <div>
        {display.status === 'partial' ? (
          <p className="dashboard-partial-note">{t('pages.dashboard.recentPartialNote')}</p>
        ) : null}
        <DataTable columns={columns} rows={recent.items} emptyText={t('pages.dashboard.recentEmpty')} rowKey={(r) => r.requestId ?? r.occurredAt} />
      </div>
    );
  }
  const text = display.status === 'no-data' ? t('pages.dashboard.recentEmpty') : t('pages.dashboard.recentEmpty');
  return (
    <div className="dashboard-status" data-state={display.status}>
      <p>{text}</p>
    </div>
  );
}

export function DashboardPage() {
  const { t, i18n } = useTranslation();
  const profile = useAuthProfile();
  const userId = profile?.id ?? 0;
  const queryClient = useQueryClient();
  const locale = i18n.language || 'zh-CN';
  const fmt = createDisplayFormatters(locale);

  const [preset, setPreset] = useState<DashboardPreset>('24h');
  const range = useMemo(() => buildDashboardRange(preset, new Date(), browserTimezone()), [preset]);
  const is30d = preset === '30d';

  const balance = useBalanceQuery(userId);
  const stats = useDashboardStatsQuery(userId, range);

  const sessionError = unauthenticatedOf(balance.error, stats.error);
  useEffect(() => {
    if (sessionError) {
      handleDashboardQueryError(queryClient, sessionError);
    }
  }, [queryClient, sessionError]);

  const statsData = stats.data?.data;
  const balanceData = balance.data?.data;

  return (
    <div className="dashboard-page">
      <h1>{t('pages.dashboard.title')}</h1>

      <div className="dashboard-toolbar">
        <label className="dashboard-field" htmlFor="dashboard-range">
          {t('pages.dashboard.rangeLabel')}
        </label>
        <Select
          id="dashboard-range"
          density="compact"
          value={preset}
          onValueChange={(value) => setPreset(value as DashboardPreset)}
          options={DASHBOARD_PRESETS.map((def) => ({
            value: def.value,
            label: def.enabled ? t(def.labelKey) : `${t(def.labelKey)}（${t('pages.dashboard.range30dDisabled')}）`,
            disabled: !def.enabled,
          }))}
        />
        {is30d ? (
          <p className="dashboard-toolbar-hint" role="note">
            {t('pages.dashboard.range30dBlocked')}
          </p>
        ) : null}
      </div>

      <section className="dashboard-balance" aria-label={t('pages.dashboard.balanceTitle')}>
        <h2>{t('pages.dashboard.balanceTitle')}</h2>
        <p className="dashboard-hint">{t('pages.dashboard.balanceHint')}</p>
        {balance.isPending ? <p>{t('states.loading')}</p> : null}
        {balance.isError && !(balance.error instanceof PortalApiError && balance.error.code === 'UNAUTHENTICATED') ? (
          <div className="dashboard-notice" role="alert">
            <p>{t('pages.dashboard.balanceLoadError')}</p>
            <Button type="button" variant="secondary" onClick={() => void balance.refetch()}>
              {t('common.retry')}
            </Button>
          </div>
        ) : null}
        {balanceData ? (
          <p>
            {fmt.money(balanceData.amount, balanceData.currency)}{' '}
            <span className="dashboard-quota">({fmt.number(Number(balanceData.quota))} quota)</span>
          </p>
        ) : null}
        <p>
          <Link to="/dashboard/wallet">{t('pages.dashboard.viewWallet')}</Link>
        </p>
      </section>

      {stats.isPending ? <p>{t('states.loading')}</p> : null}
      {stats.isError && !(stats.error instanceof PortalApiError && stats.error.code === 'UNAUTHENTICATED') ? (
        <section className="dashboard-stats-error" aria-label={t('pages.dashboard.statsLoadError')} role="alert">
          <h2>{t('pages.dashboard.statsLoadError')}</h2>
          <Button type="button" variant="secondary" onClick={() => void stats.refetch()}>
            {t('common.retry')}
          </Button>
        </section>
      ) : null}

      {statsData ? (
        <>
          <p className="dashboard-range-meta">
            {t('pages.dashboard.rangeSummary', {
              start: range.startTime,
              end: range.endTime,
              timezone: range.timezone,
              granularity: GRANULARITY_LABEL[range.granularity] ? t(GRANULARITY_LABEL[range.granularity]) : range.granularity,
            })}
            <span className="dashboard-baseline">{statsData.baselineVersion}</span>
          </p>

          <section className="dashboard-metrics-section" aria-label={t('pages.dashboard.metricRequestTotal')}>
            <MetricGrid stats={statsData} t={t} fmt={fmt} />
          </section>

          <section className="dashboard-trends" aria-label={t('pages.dashboard.trendRequests')}>
            <TrendRegion
              title={t('pages.dashboard.trendRequests')}
              description={t('pages.dashboard.trendRequests')}
              availability={statsData.requestTrend.availability}
              reasonCode={statsData.requestTrend.reasonCode}
              points={statsData.requestTrend.points}
              timezone={range.timezone}
              locale={locale}
              t={t}
            />
            <TrendRegion
              title={t('pages.dashboard.trendSpend')}
              description={t('pages.dashboard.trendSpend')}
              availability={statsData.spendTrend.availability}
              reasonCode={statsData.spendTrend.reasonCode}
              points={statsData.spendTrend.points}
              timezone={range.timezone}
              locale={locale}
              t={t}
            />
          </section>

          <section className="dashboard-recent" aria-label={t('pages.dashboard.recentTitle')}>
            <h2>{t('pages.dashboard.recentTitle')}</h2>
            <RecentRequests stats={statsData} t={t} locale={locale} />
          </section>
        </>
      ) : null}
    </div>
  );
}