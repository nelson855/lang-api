import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useSearchParams } from 'react-router';
import { useModels } from '../api/useModels';
import type { ProviderOption } from '../api/models';
import { evaluateProviderConsistency } from '../features/catalog/catalogConsistency';
import {
  buildCatalogSearch,
  parseCatalogConditions,
  resolveProviderCondition,
  type CatalogConditions,
} from '../features/catalog/catalogUrlState';
import { buildDocsPath, buildModelDetailPath } from '../features/catalog/catalogPaths';
import { filterModels } from '../features/catalog/filter';
import { formatBasePricing, type PriceRow } from '../features/catalog/priceFormat';
import { useModelProvidersQuery } from '../features/catalog/useCatalogQueries';
import { CopyModelId } from '../features/catalog/CopyModelId';
import { Empty } from '../components/feedback/Feedback';
import { SearchField } from '../components/ui/SearchField';
import { Select } from '../components/ui/Select';
import { Button } from '../components/ui/Button';
import './PublicPages.css';

export function ModelsPage() {
  const { t } = useTranslation();
  const [params, setParams] = useSearchParams();
  const query = useModels();
  const providersQuery = useModelProvidersQuery();

  const conditions = useMemo(() => parseCatalogConditions(params.toString()), [params]);
  const [searchDraft, setSearchDraft] = useState(conditions.search);
  // 输入法组合期间不触发导航：组合结束再一次性同步搜索条件。
  // 必须用 ref：组合开始与随后的 input 事件在同一批次内，state 尚未提交。
  const composingRef = useRef(false);

  // URL 是搜索与厂商条件的唯一来源：前进后退与刷新都能恢复。
  useEffect(() => {
    setSearchDraft(conditions.search);
  }, [conditions.search]);

  const commit = useCallback(
    (next: CatalogConditions, replace: boolean) => {
      const search = buildCatalogSearch(next);
      setParams(search, { replace });
    },
    [setParams],
  );

  const models = useMemo(() => query.data?.data.models ?? [], [query.data]);
  const providersData = providersQuery.data?.data ?? null;

  // 版本冲突时暂停使用冲突选项：最多一轮成对重取，仍冲突则交给手动重试。
  const [conflictRetried, setConflictRetried] = useState(false);
  const recoveryStartedRef = useRef(false);
  const consistency = useMemo(() => {
    if (!query.data || !providersData || !Array.isArray(providersData.providers)) {
      return null;
    }
    return evaluateProviderConsistency({
      modelsVersion: query.data.data.pricingVersion,
      providersVersion: providersData.pricingVersion,
      models,
      providers: providersData.providers,
    });
  }, [query.data, providersData, models]);

  useEffect(() => {
    if (consistency?.status !== 'conflict' || conflictRetried || recoveryStartedRef.current) {
      return;
    }
    recoveryStartedRef.current = true;
    void Promise.all([query.refetch(), providersQuery.refetch()]).then(() => {
      setConflictRetried(true);
    });
  }, [consistency, conflictRetried, query, providersQuery]);

  const providerOptions: ProviderOption[] | null = useMemo(() => {
    if (!consistency) {
      return providersQuery.isSuccess ? (providersData?.providers ?? []) : null;
    }
    return consistency.status === 'consistent' ? consistency.options : [];
  }, [consistency, providersQuery.isSuccess, providersData]);

  const providerCondition = resolveProviderCondition(conditions.provider, providerOptions);
  const unknownProvider = providerCondition.status === 'unknown' ? providerCondition.value : null;

  const visible = useMemo(
    () => filterModels(models, conditions.search, conditions.provider),
    [models, conditions.search, conditions.provider],
  );

  const priceLabels = useMemo(
    () => ({
      input: t('pages.models.inputPrice'),
      output: t('pages.models.outputPrice'),
      request: t('pages.models.perRequestPrice'),
      perMillionTokens: t('pages.models.perMillionTokens'),
      perRequest: t('pages.models.perRequestUnit'),
      unknownUnit: (unit: string) => t('pages.models.unknownUnit', { unit }),
    }),
    [t],
  );

  const providerSelectValue = unknownProvider
    ? ''
    : conditions.provider && providerCondition.status === 'known'
      ? conditions.provider
      : '';

  return (
    <div className="public-page">
      <div className="public-page-head"><p className="page-kicker">Model Catalog</p><h1>{t('pages.models.title')}</h1><p>{t('pages.models.basePriceNote')}</p></div>
      {query.isPending ? <p>{t('states.loading')}</p> : null}
      {query.isError ? (
        <div>
          <p>{t('pages.models.loadError')}</p>
          <Button type="button" intent="neutral" emphasis="outline" onClick={() => query.refetch()}>
            {t('common.retry')}
          </Button>
        </div>
      ) : null}
      {consistency?.status === 'conflict' && conflictRetried ? (
        <p role="status" className="catalog-consistency-note">
          {t('pages.models.versionConflict')}{' '}
          <Button
            type="button"
            intent="neutral"
            emphasis="outline"
            size="sm"
            onClick={() => {
              setConflictRetried(false);
              recoveryStartedRef.current = false;
              void Promise.all([query.refetch(), providersQuery.refetch()]);
            }}
          >
            {t('pages.models.retryConflict')}
          </Button>
        </p>
      ) : null}
      {consistency?.status === 'consistent' && consistency.versionCheck === 'unknown' ? (
        <p role="status" className="catalog-consistency-note">{t('pages.models.versionUnknown')}</p>
      ) : null}
      {query.isSuccess ? (
        <>
          <div className="catalog-controls"><label>
            {t('pages.models.searchLabel')}
            <SearchField
              value={searchDraft}
              onChange={(event) => {
                setSearchDraft(event.target.value);
                // 搜索输入替换当前历史项；输入法组合期间等 compositionend 再同步。
                if (!composingRef.current) {
                  commit({ search: event.target.value, provider: conditions.provider }, true);
                }
              }}
              onCompositionStart={() => {
                composingRef.current = true;
              }}
              onCompositionEnd={(event) => {
                composingRef.current = false;
                commit({ search: event.currentTarget.value, provider: conditions.provider }, true);
              }}
              density="compact"
              placeholder={t('pages.models.searchPlaceholder')}
            />
          </label>
          <label>
            {t('pages.models.providerLabel')}
            <Select
              density="compact"
              value={providerSelectValue}
              onValueChange={(value) => {
                // 明确选择形成历史项，返回列表可回到该筛选。
                commit({ search: conditions.search, provider: value ? value : null }, false);
              }}
              disabled={providersQuery.isError || consistency?.status === 'conflict'}
              options={[
                { value: '', label: t('pages.models.providerAll') },
                ...(providerOptions ?? []).map((option) => ({
                  value: option.value,
                  label: `${option.label} (${t('pages.models.providerCount', { count: option.modelCount })})`,
                })),
              ]}
            />
          </label></div>
          {providersQuery.isError ? (
            <div className="catalog-providers-error">
              <p>{t('pages.models.providerLoadError')}</p>
              <Button type="button" intent="neutral" emphasis="outline" size="sm" onClick={() => providersQuery.refetch()}>
                {t('common.retry')}
              </Button>
            </div>
          ) : null}
          {conditions.provider ? (
            <p className="catalog-unknown-provider">
              {unknownProvider
                ? t('pages.models.providerUnknown', { value: unknownProvider })
                : conditions.provider}{' '}
              <Button
                type="button"
                intent="neutral"
                emphasis="outline"
                size="sm"
                onClick={() => commit({ search: conditions.search, provider: null }, false)}
              >
                {t('pages.models.clearProvider')}
              </Button>
            </p>
          ) : null}
          {models.length === 0 ? <Empty description={t('pages.models.empty')} /> : null}
          {models.length > 0 && visible.length === 0 ? <p>{t('pages.models.noMatch')}</p> : null}
          {visible.length > 0 ? (
            <p className="catalog-result-count">{t('pages.models.resultCount', { count: visible.length })}</p>
          ) : null}
          <ul className="model-list">
            {visible.map((m) => {
              const detailPath = buildModelDetailPath(m.id, conditions);
              const docsPath = buildDocsPath(m.id);
              const pricing = formatBasePricing(m.pricing, priceLabels);
              return (
                <li key={m.id} className="model-card">
                  <div className="model-card-head"><strong>{m.id}</strong>{m.provider ? <span>{m.provider}</span> : null}</div>
                  {pricing ? (
                    <span className="model-card-price">
                      {pricing.rows.map((row: PriceRow) => `${row.label} ${row.value}`).join(' / ')}
                      {pricing.rows.length > 0 ? ` ${pricing.unitLabel}` : ''}
                    </span>
                  ) : (
                    <span>{t('pages.models.priceUnavailable')}</span>
                  )}
                  <div className="model-card-actions">
                    {detailPath ? (
                      <Link to={detailPath} className="model-card-link">
                        {t('pages.models.detailLink')}
                      </Link>
                    ) : null}
                    {docsPath ? (
                      <Link to={docsPath} className="model-card-link">
                        {t('pages.models.viewExample')}
                      </Link>
                    ) : null}
                    <CopyModelId modelId={m.id} />
                  </div>
                </li>
              );
            })}
          </ul>
        </>
      ) : null}
    </div>
  );
}
