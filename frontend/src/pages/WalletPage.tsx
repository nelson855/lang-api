import { useEffect, useMemo, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { useSearchParams } from 'react-router';
import { PortalApiError } from '../api/envelope';
import { WALLET_TRANSACTIONS_PAGE_SIZE } from '../api/consumptionTransactionsFetch';
import type { TopupRecord } from '../api/wallet';
import { Empty } from '../components/feedback/Feedback';
import { Button } from '../components/ui/Button';
import { DataTable } from '../components/ui/DataTable';
import { Pagination } from '../components/ui/Pagination';
import { Select } from '../components/ui/Select';
import { useAuthProfile } from '../features/auth/authState';
import { useBalanceQuery } from '../features/usage/useUsage';
import {
  useConsumptionSummaryQuery,
  useTransactionsQuery,
} from '../features/wallet/useConsumptionTransactions';
import { handleWalletQueryError, useTopupOptionsQuery, useTopupRecordsQuery } from '../features/wallet/useWallet';
import {
  toConsumptionSummaryDisplay,
  toTransactionsDisplay,
  type MetricDisplay,
  type TransactionItemDisplay,
} from '../features/wallet/walletDisplay';
import { WALLET_RANGE_PRESETS, type WalletRangePreset } from '../features/wallet/walletRange';
import {
  buildWalletUrlSearch,
  collapseWalletPage,
  normalizeWalletUrl,
  switchWalletRange,
  switchWalletType,
  type WalletTypeFilter,
  type WalletUrlState,
} from '../features/wallet/walletUrlState';
import './WalletPage.css';

const TOPUP_PAGE_SIZE = 20;

function nonAuth(error: unknown): boolean {
  return error instanceof PortalApiError && error.code !== 'UNAUTHENTICATED';
}

function isSessionError(error: unknown): error is PortalApiError {
  return error instanceof PortalApiError && error.code === 'UNAUTHENTICATED';
}

function MetricLine({ label, metric }: { label: string; metric: MetricDisplay }) {
  const { t } = useTranslation();
  if (metric.status === 'value') {
    return (
      <p>
        {label}：<span>{metric.text}</span>
        {metric.unitKey ? <span> {t(metric.unitKey)}</span> : null}
        {metric.currency ? <span> {metric.currency}</span> : null}
      </p>
    );
  }
  if (metric.status === 'unavailable') {
    return (
      <p>
        {label}：<span>{t(metric.reasonKey)}</span>
      </p>
    );
  }
  return (
    <p role="alert">
      {label}：{t('pages.wallet.dataInvalid')}
    </p>
  );
}

export function WalletPage() {
  const { t, i18n } = useTranslation();
  const profile = useAuthProfile();
  const userId = profile?.id ?? 0;
  const queryClient = useQueryClient();
  const [searchParams, setSearchParams] = useSearchParams();
  const [topupPage, setTopupPage] = useState(1);

  // 单次时钟快照:首次进入与非法回退共用,刷新/前进/后退复用 URL 原边界。
  const [snapshot] = useState(() => new Date());
  const timezone = useMemo(() => {
    try {
      return Intl.DateTimeFormat().resolvedOptions().timeZone ?? 'UTC';
    } catch {
      return 'UTC';
    }
  }, []);

  const normalized = useMemo(
    () => normalizeWalletUrl(searchParams.toString(), snapshot, timezone),
    [searchParams, snapshot, timezone],
  );
  const urlState: WalletUrlState = normalized.state;
  const ready = !normalized.needsReplace;

  // 首次进入或非法输入:replace 写回规范化 URL,不留下脏历史。
  useEffect(() => {
    if (!normalized.needsReplace) {
      return;
    }
    const search = buildWalletUrlSearch(normalized.state).replace(/^\?/, '');
    if (searchParams.toString() !== search) {
      setSearchParams(new URLSearchParams(search), { replace: true });
    }
  }, [searchParams, normalized, setSearchParams]);

  const balance = useBalanceQuery(userId);
  const options = useTopupOptionsQuery(userId);
  const records = useTopupRecordsQuery(userId, topupPage, TOPUP_PAGE_SIZE);
  const summary = useConsumptionSummaryQuery(userId, urlState.range, { enabled: ready });
  const transactions = useTransactionsQuery(userId, urlState.range, urlState.type, urlState.page, {
    enabled: ready,
  });

  // 响应 total 收敛越界页:replace 到最后有效页再查询。
  const txTotal = transactions.data?.data.total;
  const txSuccess = transactions.isSuccess;
  useEffect(() => {
    if (!ready || !txSuccess || txTotal === undefined) {
      return;
    }
    const last = Math.max(1, Math.ceil(txTotal / WALLET_TRANSACTIONS_PAGE_SIZE));
    if (urlState.page > last) {
      const next = collapseWalletPage(urlState, txTotal, WALLET_TRANSACTIONS_PAGE_SIZE);
      setSearchParams(new URLSearchParams(buildWalletUrlSearch(next).replace(/^\?/, '')), {
        replace: true,
      });
    }
  }, [ready, txSuccess, txTotal, urlState, setSearchParams]);

  const sessionError = [balance.error, options.error, records.error, summary.error, transactions.error].find(
    isSessionError,
  );
  useEffect(() => {
    if (sessionError) {
      handleWalletQueryError(queryClient, sessionError);
    }
  }, [queryClient, sessionError]);

  const setUrlState = (next: WalletUrlState) => {
    setSearchParams(new URLSearchParams(buildWalletUrlSearch(next).replace(/^\?/, '')));
  };

  const locale = i18n.language;
  const summaryDisplay = summary.data
    ? toConsumptionSummaryDisplay(summary.data.data, locale)
    : null;
  const txDisplay = transactions.data ? toTransactionsDisplay(transactions.data.data, locale) : null;
  const summaryRange = summary.data?.data.range;

  const rangeOptions = WALLET_RANGE_PRESETS.map((preset) => ({
    value: preset.value,
    label: t(preset.labelKey),
    disabled: !preset.enabled,
  }));
  const typeOptions = (['ALL', 'TOPUP', 'CONSUMPTION', 'REFUND'] as const).map((value) => ({
    value,
    label: value === 'ALL' ? t('pages.wallet.typeAll') : t(`pages.wallet.txType${value.charAt(0)}${value.slice(1).toLowerCase()}`),
  }));

  const txColumns = useMemo(
    () => [
      {
        key: 'occurredAt',
        header: t('pages.wallet.colTime'),
        render: (row: TransactionItemDisplay & { status: 'ok' }) => (
          <time dateTime={row.iso}>{row.timeText}</time>
        ),
      },
      {
        key: 'type',
        header: t('pages.wallet.colType'),
        render: (row: TransactionItemDisplay & { status: 'ok' }) => t(row.typeKey),
      },
      {
        key: 'direction',
        header: t('pages.wallet.colDirection'),
        render: (row: TransactionItemDisplay & { status: 'ok' }) => (
          <>
            <span aria-hidden="true">{row.isIncome ? '▲' : '▼'}</span> <span>{t(row.directionKey)}</span>
          </>
        ),
      },
      {
        key: 'amount',
        header: t('pages.wallet.colTxAmount'),
        render: (row: TransactionItemDisplay & { status: 'ok' }) =>
          `${row.amountText} ${row.amountUnitKey ? t(row.amountUnitKey) : (row.amountCurrency ?? '')}`.trim(),
      },
      {
        key: 'status',
        header: t('pages.wallet.colTxStatus'),
        render: (row: TransactionItemDisplay & { status: 'ok' }) => t(row.statusKey),
      },
      {
        key: 'remark',
        header: t('pages.wallet.colRemark'),
        render: (row: TransactionItemDisplay & { status: 'ok' }) =>
          row.remark.status === 'value' ? row.remark.text : t('pages.wallet.noValue'),
      },
      {
        key: 'referenceId',
        header: t('pages.wallet.colSource'),
        render: (row: TransactionItemDisplay & { status: 'ok' }) =>
          row.reference.status === 'value' ? row.reference.text : t('pages.wallet.noValue'),
      },
    ],
    [t],
  );

  const topupColumns = useMemo(
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

  const balanceData = balance.data?.data;
  const optionsData = options.data?.data;
  const recordsData = records.data?.data;

  const okItems =
    txDisplay?.status === 'ok'
      ? txDisplay.items.filter(
          (item): item is TransactionItemDisplay & { status: 'ok' } => item.status === 'ok',
        )
      : [];

  return (
    <div className="wallet-page">
      <h1>{t('pages.wallet.title')}</h1>
      <p className="wallet-hint">{t('pages.wallet.pageIntro')}</p>

      <section aria-label={t('pages.wallet.balanceTitle')}>
        <h2>{t('pages.wallet.balanceTitle')}</h2>
        <p className="wallet-hint">{t('pages.wallet.balanceHint')}</p>
        {balance.isPending ? <p role="status">{t('states.loading')}</p> : null}
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

      <div className="wallet-ledger-rail" aria-hidden="true" />

      <section aria-label={t('pages.wallet.summaryTitle')}>
        <h2>{t('pages.wallet.summaryTitle')}</h2>
        <div className="wallet-toolbar">
          <Select
            aria-label={t('pages.wallet.rangeLabel')}
            options={rangeOptions}
            value={urlState.preset}
            onValueChange={(value) =>
              setUrlState(switchWalletRange(urlState, value as WalletRangePreset, new Date(), timezone))
            }
          />
        </div>
        {!ready ? <p role="status">{t('states.loading')}</p> : null}
        {ready && !urlState.requestsEnabled ? (
          <p role="note">{t('pages.wallet.range30dBlocked')}</p>
        ) : null}
        {ready && urlState.requestsEnabled && summary.isPending ? (
          <p role="status">{t('states.loading')}</p>
        ) : null}
        {ready && summary.isError && nonAuth(summary.error) ? (
          <div className="wallet-notice" role="alert">
            <p>{t('pages.wallet.loadError')}</p>
            <Button type="button" variant="secondary" onClick={() => void summary.refetch()}>
              {t('common.retry')}
            </Button>
          </div>
        ) : null}
        {ready && summaryDisplay?.status === 'invalid' ? (
          <div className="wallet-notice" role="alert">
            <p>{t('pages.wallet.dataInvalid')}</p>
            <Button type="button" variant="secondary" onClick={() => void summary.refetch()}>
              {t('common.retry')}
            </Button>
          </div>
        ) : null}
        {ready && summaryDisplay?.status === 'ok' ? (
          <>
            {summaryRange ? (
              <p className="wallet-hint">
                {t('pages.wallet.summaryRangeNote', {
                  start: summaryRange.start,
                  end: summaryRange.end,
                  timezone: summaryRange.timezone,
                })}
              </p>
            ) : null}
            <MetricLine label={t('pages.wallet.summaryRecords')} metric={summaryDisplay.recordCount} />
            <MetricLine label={t('pages.wallet.summaryQuota')} metric={summaryDisplay.quotaTotal} />
            <MetricLine label={t('pages.wallet.summaryMoney')} metric={summaryDisplay.moneyTotal} />
            {summaryDisplay.coverage.status === 'partial' ? (
              <p role="note">
                {t('pages.wallet.coveragePartial', { reason: t(summaryDisplay.coverage.reasonKey) })}
              </p>
            ) : null}
            {summaryDisplay.coverage.status === 'unavailable' ? (
              <p role="note">
                {t('pages.wallet.coverageUnavailable', {
                  reason: t(summaryDisplay.coverage.reasonKey),
                })}
              </p>
            ) : null}
          </>
        ) : null}
      </section>

      <section aria-label={t('pages.wallet.txTitle')}>
        <h2>{t('pages.wallet.txTitle')}</h2>
        <p className="wallet-hint">{t('pages.wallet.txHint')}</p>
        <div className="wallet-toolbar">
          <Select
            aria-label={t('pages.wallet.typeLabel')}
            options={typeOptions}
            value={urlState.type}
            onValueChange={(value) => setUrlState(switchWalletType(urlState, value as WalletTypeFilter))}
          />
        </div>
        {!ready ? <p role="status">{t('states.loading')}</p> : null}
        {ready && !urlState.requestsEnabled ? (
          <p role="note">{t('pages.wallet.range30dBlocked')}</p>
        ) : null}
        {ready && urlState.requestsEnabled && transactions.isPending ? (
          <p role="status">{t('states.loading')}</p>
        ) : null}
        {ready && transactions.isError && nonAuth(transactions.error) ? (
          <div className="wallet-notice" role="alert">
            <p>{t('pages.wallet.loadError')}</p>
            <Button type="button" variant="secondary" onClick={() => void transactions.refetch()}>
              {t('common.retry')}
            </Button>
          </div>
        ) : null}
        {ready && txDisplay?.status === 'invalid' ? (
          <div className="wallet-notice" role="alert">
            <p>{t('pages.wallet.dataInvalid')}</p>
            <Button type="button" variant="secondary" onClick={() => void transactions.refetch()}>
              {t('common.retry')}
            </Button>
          </div>
        ) : null}
        {ready && txDisplay?.status === 'ok' ? (
          <>
            {txDisplay.overall !== 'unavailable'
              ? txDisplay.coverage
                  .filter((entry) => entry.availability !== 'available')
                  .map((entry) => (
                    <p key={entry.type} role="note">
                      {t(entry.availability === 'partial' ? 'pages.wallet.coveragePartial' : 'pages.wallet.coverageUnavailable', {
                        reason: `${t(entry.typeKey)}: ${entry.reasonKey ? t(entry.reasonKey) : ''}`,
                      })}
                    </p>
                  ))
              : null}
            {txDisplay.overall === 'unavailable' ? (
              <div className="wallet-notice" role="note">
                <p>
                  {txDisplay.overallReasonKey
                    ? t(txDisplay.overallReasonKey)
                    : t('pages.wallet.dataInvalid')}
                </p>
              </div>
            ) : null}
            {txDisplay.overall !== 'unavailable' && txDisplay.isRealEmpty ? (
              <Empty description={t('pages.wallet.emptyTransactions')} />
            ) : null}
            {txDisplay.overall !== 'unavailable' && !txDisplay.isRealEmpty ? (
              <>
                <DataTable<(typeof okItems)[number]>
                  columns={txColumns}
                  rows={okItems}
                  rowKey={(row, index) => `${row.iso}-${index}`}
                />
                <div className="wallet-cards">
                  {okItems.map((item, index) => (
                    <article key={`${item.iso}-${index}`} className="wallet-card">
                      <p>
                        {t('pages.wallet.colTime')}: <time dateTime={item.iso}>{item.timeText}</time>
                      </p>
                      <p>
                        {t('pages.wallet.colType')}: <span>{t(item.typeKey)}</span>
                      </p>
                      <p>
                        {t('pages.wallet.colDirection')}: <span>{t(item.directionKey)}</span>
                      </p>
                      <p>
                        {t('pages.wallet.colTxAmount')}:{' '}
                        <span>
                          {item.amountText} {item.amountUnitKey ? t(item.amountUnitKey) : (item.amountCurrency ?? '')}
                        </span>
                      </p>
                      <p>
                        {t('pages.wallet.colTxStatus')}: <span>{t(item.statusKey)}</span>
                      </p>
                      <p>
                        {t('pages.wallet.colRemark')}:{' '}
                        <span>
                          {item.remark.status === 'value' ? item.remark.text : t('pages.wallet.noValue')}
                        </span>
                      </p>
                      <p>
                        {t('pages.wallet.colSource')}:{' '}
                        <span>
                          {item.reference.status === 'value'
                            ? item.reference.text
                            : t('pages.wallet.noValue')}
                        </span>
                      </p>
                    </article>
                  ))}
                </div>
                <Pagination
                  page={urlState.page}
                  pageSize={WALLET_TRANSACTIONS_PAGE_SIZE}
                  total={txDisplay.total}
                  onChange={(next) => setUrlState(collapseWalletPage(urlState, next))}
                />
              </>
            ) : null}
          </>
        ) : null}
      </section>

      <section aria-label={t('pages.wallet.disabledTitle')}>
        <h2>{t('pages.wallet.disabledTitle')}</h2>
        {options.isPending ? <p role="status">{t('states.loading')}</p> : null}
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
        {records.isPending ? <p role="status">{t('states.loading')}</p> : null}
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
                columns={topupColumns}
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
                onChange={(next) => setTopupPage(next)}
              />
            </>
          )
        ) : null}
      </section>
    </div>
  );
}
