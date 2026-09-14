import { useEffect, useMemo, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { PortalApiError } from '../api/envelope';
import type { RequestLog } from '../api/requestLogs';
import { Empty } from '../components/feedback/Feedback';
import { Button } from '../components/ui/Button';
import { DataTable } from '../components/ui/DataTable';
import { Pagination } from '../components/ui/Pagination';
import { useAuthProfile } from '../features/auth/authState';
import {
  normalizeRequestLogsParams,
  withFirstPage,
  type NormalizedRequestLogsParams,
} from '../features/requestLogs/requestLogsCache';
import { handleRequestLogsQueryError, useRequestLogsQuery } from '../features/requestLogs/useRequestLogs';
import { normalizeUsageRange } from '../features/usage/usageCache';
import './RequestLogsPage.css';

const PAGE_SIZE = 20;

type TimePreset = '24h' | '7d' | '30d';

const PRESET_HOURS: Record<TimePreset, number> = { '24h': 24, '7d': 24 * 7, '30d': 24 * 30 };

function missingText(value: string | number | null, fallback: string): string {
  if (value === null || value === undefined || value === '') {
    return fallback;
  }
  return String(value);
}

export function RequestLogsPage() {
  const { t } = useTranslation();
  const profile = useAuthProfile();
  const userId = profile?.id ?? 0;
  const queryClient = useQueryClient();

  const [keyInput, setKeyInput] = useState('');
  const [modelInput, setModelInput] = useState('');
  const [resultInput, setResultInput] = useState('SUCCESS');
  const [timePreset, setTimePreset] = useState<TimePreset>('24h');
  const [submitted, setSubmitted] = useState<NormalizedRequestLogsParams>(() =>
    normalizeRequestLogsParams({ page: 1, pageSize: PAGE_SIZE, result: 'SUCCESS' }),
  );

  const query = useRequestLogsQuery(userId, submitted);
  const data = query.data?.data;
  const error = query.error instanceof PortalApiError ? query.error : null;

  useEffect(() => {
    if (error?.code === 'UNAUTHENTICATED') {
      handleRequestLogsQueryError(queryClient, userId, error);
    }
  }, [queryClient, userId, error]);

  const submit = () => {
    const end = Date.now();
    const start = end - PRESET_HOURS[timePreset] * 60 * 60 * 1000;
    const range = normalizeUsageRange(new Date(start).toISOString(), new Date(end).toISOString(), end);
    setSubmitted(
      withFirstPage(
        normalizeRequestLogsParams({
          page: 1,
          pageSize: PAGE_SIZE,
          result: resultInput,
          keyName: keyInput,
          model: modelInput,
          startTime: range.startTime,
          endTime: range.endTime,
        }),
      ),
    );
  };

  const columns = useMemo(
    () => [
      { key: 'occurredAt', header: t('pages.requestLogs.colTime'), render: (row: RequestLog) => row.occurredAt },
      { key: 'keyName', header: t('pages.requestLogs.colKey'), render: (row: RequestLog) => missingText(row.keyName, t('pages.requestLogs.noData')) },
      { key: 'model', header: t('pages.requestLogs.colModel'), render: (row: RequestLog) => missingText(row.model, t('pages.requestLogs.noData')) },
      {
        key: 'tokens',
        header: t('pages.requestLogs.colTokens'),
        render: (row: RequestLog) => `${row.inputTokens}/${row.outputTokens}`,
      },
      { key: 'durationMs', header: t('pages.requestLogs.colDuration'), render: (row: RequestLog) => `${row.durationMs}ms` },
      { key: 'result', header: t('pages.requestLogs.colResult'), render: (row: RequestLog) => row.result },
      {
        key: 'amount',
        header: t('pages.requestLogs.colCost'),
        render: (row: RequestLog) => `${row.amount} ${row.currency}`,
      },
      {
        key: 'requestId',
        header: t('pages.requestLogs.colRequestId'),
        render: (row: RequestLog) => missingText(row.requestId, t('pages.requestLogs.noData')),
      },
    ],
    [t],
  );

  return (
    <div className="request-logs-page">
      <h1>{t('pages.requestLogs.title')}</h1>
      <div className="request-logs-toolbar">
        <label className="request-logs-field" htmlFor="request-logs-key">
          {t('pages.requestLogs.keyLabel')}
        </label>
        <input
          id="request-logs-key"
          className="input"
          type="search"
          placeholder={t('pages.requestLogs.keyPlaceholder')}
          value={keyInput}
          onChange={(event) => setKeyInput(event.target.value)}
        />
        <label className="request-logs-field" htmlFor="request-logs-model">
          {t('pages.requestLogs.modelLabel')}
        </label>
        <input
          id="request-logs-model"
          className="input"
          type="search"
          placeholder={t('pages.requestLogs.modelPlaceholder')}
          value={modelInput}
          onChange={(event) => setModelInput(event.target.value)}
        />
        <label className="request-logs-field" htmlFor="request-logs-result">
          {t('pages.requestLogs.resultLabel')}
        </label>
        <select
          id="request-logs-result"
          className="input"
          value={resultInput}
          onChange={(event) => setResultInput(event.target.value)}
        >
          <option value="SUCCESS">{t('pages.requestLogs.resultSuccess')}</option>
          <option value="ERROR">{t('pages.requestLogs.resultError')}</option>
        </select>
        <label className="request-logs-field" htmlFor="request-logs-range">
          {t('pages.dashboard.rangeLabel')}
        </label>
        <select
          id="request-logs-range"
          className="input"
          value={timePreset}
          onChange={(event) => setTimePreset(event.target.value as TimePreset)}
        >
          <option value="24h">{t('pages.dashboard.range24h')}</option>
          <option value="7d">{t('pages.dashboard.range7d')}</option>
          <option value="30d">{t('pages.dashboard.range30d')}</option>
        </select>
        <Button type="button" variant="primary" onClick={submit}>
          {t('pages.requestLogs.search')}
        </Button>
        <Button type="button" variant="secondary" onClick={() => void query.refetch()}>
          {t('pages.requestLogs.refresh')}
        </Button>
      </div>
      {query.isPending ? <p>{t('pages.requestLogs.loading')}</p> : null}
      {query.isError && error?.code !== 'UNAUTHENTICATED' ? (
        <div className="request-logs-notice" role="alert">
          <p>{t('pages.requestLogs.loadError')}</p>
          <Button type="button" variant="secondary" onClick={() => void query.refetch()}>
            {t('common.retry')}
          </Button>
        </div>
      ) : null}
      {data ? (
        data.total === 0 ? (
          <Empty description={t('pages.requestLogs.empty')} />
        ) : (
          <>
            <DataTable<RequestLog>
              columns={columns}
              rows={data.items}
              rowKey={(row, index) => row.requestId ?? `${data.page}-${index}`}
            />
            <div className="request-logs-cards">
              {data.items.map((row, index) => (
                <article key={row.requestId ?? `${data.page}-${index}`} className="request-logs-card">
                  <p>
                    {t('pages.requestLogs.colTime')}: {row.occurredAt}
                  </p>
                  <p>
                    {t('pages.requestLogs.colKey')}: {missingText(row.keyName, t('pages.requestLogs.noData'))}
                  </p>
                  <p>
                    {t('pages.requestLogs.colModel')}: {missingText(row.model, t('pages.requestLogs.noData'))}
                  </p>
                  <p>
                    {t('pages.requestLogs.colTokens')}: {row.inputTokens}/{row.outputTokens}
                  </p>
                  <p>
                    {t('pages.requestLogs.colResult')}: {row.result}
                  </p>
                  <p>
                    {t('pages.requestLogs.colCost')}: {row.amount} {row.currency}
                  </p>
                  <p>
                    {t('pages.requestLogs.colRequestId')}: {missingText(row.requestId, t('pages.requestLogs.noData'))}
                  </p>
                </article>
              ))}
            </div>
            <Pagination
              page={data.page}
              pageSize={data.pageSize}
              total={data.total}
              onChange={(next) => setSubmitted({ ...submitted, page: next })}
            />
          </>
        )
      ) : null}
    </div>
  );
}
