import { useEffect, useMemo, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { useSearchParams } from 'react-router';
import { PortalApiError } from '../api/envelope';
import type { RequestLog } from '../api/requestLogs';
import { Empty } from '../components/feedback/Feedback';
import { Button } from '../components/ui/Button';
import { DataTable } from '../components/ui/DataTable';
import { Pagination } from '../components/ui/Pagination';
import { SearchField } from '../components/ui/SearchField';
import { Select } from '../components/ui/Select';
import { useAuthProfile } from '../features/auth/authState';
import {
  normalizeRequestLogsParams,
  withFirstPage,
  type NormalizedRequestLogsParams,
} from '../features/requestLogs/requestLogsCache';
import {
  isDashboardDrilldownRange,
  parseRequestLogsSearch,
  serializeRequestLogsSearch,
} from '../features/requestLogs/requestLogsUrl';
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
  const [searchParams, setSearchParams] = useSearchParams();

  // URL 即筛选状态：进入、刷新、前进/后退都从 URL 恢复。
  // parseRequestLogsSearch 会把非法参数规范化，保证后续查询不发送非法请求。
  const submitted: NormalizedRequestLogsParams = useMemo(
    () => normalizeRequestLogsParams(parseRequestLogsSearch(searchParams.toString())),
    [searchParams],
  );

  // 输入框本地草稿：仅在提交时覆盖 URL。
  const [keyInput, setKeyInput] = useState(submitted.keyName);
  const [modelInput, setModelInput] = useState(submitted.model);
  const [resultInput, setResultInput] = useState(submitted.result);
  const [timePreset, setTimePreset] = useState<TimePreset>('24h');

  // URL 变化（前进/后退、Dashboard 下钻）时同步草稿。
  useEffect(() => {
    setKeyInput(submitted.keyName);
    setModelInput(submitted.model);
    setResultInput(submitted.result);
  }, [submitted.keyName, submitted.model, submitted.result]);

  const query = useRequestLogsQuery(userId, submitted);
  const data = query.data?.data;
  const error = query.error instanceof PortalApiError ? query.error : null;

  useEffect(() => {
    if (error?.code === 'UNAUTHENTICATED') {
      handleRequestLogsQueryError(queryClient, userId, error);
    }
  }, [queryClient, userId, error]);

  // 首次进入：若 URL 携带非法参数被规范化，使用 replace 回写，不留下脏历史。
  useEffect(() => {
    const rawSearch = searchParams.toString();
    const normalized = serializeRequestLogsSearch(submitted);
    const normalizedRaw = normalized.startsWith('?') ? normalized.slice(1) : normalized;
    if (rawSearch !== normalizedRaw) {
      setSearchParams(new URLSearchParams(normalizedRaw), { replace: true });
    }
    // 仅在 URL 文本变化时对比一次，避免 setSearchParams 触发循环。
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchParams]);

  const submit = () => {
    // 若当前是 Dashboard 下钻范围（URL 自带 startTime/endTime）且用户未改时间 preset，
    // 保留 Dashboard 范围，仅覆盖 result/keyName/model。
    let startTime = submitted.startTime;
    let endTime = submitted.endTime;
    if (!isDashboardDrilldownRange(submitted)) {
      const end = Date.now();
      const start = end - PRESET_HOURS[timePreset] * 60 * 60 * 1000;
      const range = normalizeUsageRange(new Date(start).toISOString(), new Date(end).toISOString(), end);
      startTime = range.startTime;
      endTime = range.endTime;
    }
    const next = withFirstPage(
      normalizeRequestLogsParams({
        page: 1,
        pageSize: PAGE_SIZE,
        result: resultInput,
        keyName: keyInput,
        model: modelInput,
        startTime,
        endTime,
      }),
    );
    setSearchParams(new URLSearchParams(serializeRequestLogsSearch(next).replace(/^\?/, '')));
  };

  const changePage = (next: number) => {
    // 翻页只更新 page，保留其余筛选。
    setSearchParams(new URLSearchParams(serializeRequestLogsSearch({ ...submitted, page: next }).replace(/^\?/, '')));
  };

  const isDrilldown = isDashboardDrilldownRange(submitted);

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
        <SearchField
          id="request-logs-key"
          density="compact"
          placeholder={t('pages.requestLogs.keyPlaceholder')}
          value={keyInput}
          onChange={(event) => setKeyInput(event.target.value)}
        />
        <label className="request-logs-field" htmlFor="request-logs-model">
          {t('pages.requestLogs.modelLabel')}
        </label>
        <SearchField
          id="request-logs-model"
          density="compact"
          placeholder={t('pages.requestLogs.modelPlaceholder')}
          value={modelInput}
          onChange={(event) => setModelInput(event.target.value)}
        />
        <label className="request-logs-field" htmlFor="request-logs-result">
          {t('pages.requestLogs.resultLabel')}
        </label>
        <Select
          id="request-logs-result"
          density="compact"
          value={resultInput}
          onValueChange={setResultInput}
          options={[
            { value: 'SUCCESS', label: t('pages.requestLogs.resultSuccess') },
            { value: 'ERROR', label: t('pages.requestLogs.resultError') },
          ]}
        />
        <label className="request-logs-field" htmlFor="request-logs-range">
          {t('pages.dashboard.rangeLabel')}
        </label>
        {isDrilldown ? (
          <p className="request-logs-drilldown" role="note">
            {t('pages.requestLogs.drilldownRange', {
              start: submitted.startTime,
              end: submitted.endTime,
            })}
          </p>
        ) : (
          <Select
            id="request-logs-range"
            density="compact"
            value={timePreset}
            onValueChange={(value) => setTimePreset(value as TimePreset)}
            options={[
              { value: '24h', label: t('pages.dashboard.range24h') },
              { value: '7d', label: t('pages.dashboard.range7d') },
              { value: '30d', label: t('pages.dashboard.range30d') },
            ]}
          />
        )}
        <Button type="button" intent="primary" onClick={submit}>
          {t('pages.requestLogs.search')}
        </Button>
        <Button type="button" intent="neutral" emphasis="outline" onClick={() => void query.refetch()}>
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
            <Pagination page={data.page} pageSize={data.pageSize} total={data.total} onChange={changePage} />
          </>
        )
      ) : null}
    </div>
  );
}
