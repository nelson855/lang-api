import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useLocation, useParams, useSearchParams } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { PortalApiError } from '../api/envelope';
import { MODELS_QUERY_KEY, fetchModels, type ModelModality } from '../api/models';
import { detailVersionRelation } from '../features/catalog/catalogConsistency';
import { buildCatalogListPath, parseCatalogConditions } from '../features/catalog/catalogUrlState';
import { buildDocsPath, modelIdFromDetailPath, resolveDetailModel } from '../features/catalog/catalogPaths';
import {
  describeCapability,
  describeModalities,
  describeOptionalNumber,
  describeOptionalText,
  describeReleaseDate,
  describeTags,
} from '../features/catalog/metadataDisplay';
import { formatBasePricing, formatEnhancedPricing } from '../features/catalog/priceFormat';
import { useModelDetailQuery } from '../features/catalog/useCatalogQueries';
import { CopyModelId } from '../features/catalog/CopyModelId';
import { Button } from '../components/ui/Button';
import './PublicPages.css';

function Field({ label, value }: { label: string; value: string }) {
  return (
    <>
      <dt>{label}</dt>
      <dd>{value}</dd>
    </>
  );
}

export function ModelDetailPage() {
  const { t, i18n } = useTranslation();
  const { modelRef } = useParams();
  const location = useLocation();
  const [params] = useSearchParams();

  const expectedId = useMemo(
    () => (modelRef ? modelIdFromDetailPath(location.pathname) : null),
    [modelRef, location.pathname],
  );
  // 引用非法时 enabled=false，请求不会发出。
  const detail = useModelDetailQuery(expectedId === null ? null : (modelRef ?? null));

  const conditions = useMemo(() => parseCatalogConditions(params.toString()), [params]);
  const backPath = buildCatalogListPath(conditions);

  // 跨请求版本一致性：订阅缓存中的列表，只有它存在时才对照；
  // 直达详情不为一致性强制拉列表，缺少列表不构成错误。
  const cachedListQuery = useQuery({
    queryKey: MODELS_QUERY_KEY,
    queryFn: ({ signal }) => fetchModels(signal),
    staleTime: 30_000,
    gcTime: 5 * 60_000,
    retry: false,
    refetchOnWindowFocus: false,
    enabled: false,
  });
  const cachedList = cachedListQuery.data?.data ?? null;
  const detailVersion = detail.data?.data.pricingVersion ?? null;
  const versionRelation = useMemo(
    () => (cachedList ? detailVersionRelation(cachedList.pricingVersion, detailVersion) : null),
    [cachedList, detailVersion],
  );

  // 已知版本冲突时最多一轮成对重取；仍冲突只提示，不无限请求也不混用两次响应。
  const recoveredModelRef = useRef<string | null>(null);
  const [recoveringModelRef, setRecoveringModelRef] = useState<string | null>(null);
  const retryConsistency = useCallback(async () => {
    if (!modelRef || expectedId === null) return;
    setRecoveringModelRef(modelRef);
    try {
      await Promise.all([cachedListQuery.refetch(), detail.refetch()]);
    } finally {
      setRecoveringModelRef((current) => current === modelRef ? null : current);
    }
  }, [modelRef, expectedId, cachedListQuery, detail]);
  useEffect(() => {
    if (!detail.isSuccess || versionRelation !== 'mismatched' || recoveredModelRef.current === modelRef) {
      return;
    }
    recoveredModelRef.current = modelRef ?? null;
    void retryConsistency();
  }, [detail.isSuccess, versionRelation, modelRef, retryConsistency]);

  const unknown = t('pages.models.unavailable');
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
  const enhancedLabels = useMemo(
    () => ({
      ...priceLabels,
      CACHE_INPUT: t('pages.models.priceType.CACHE_INPUT'),
      CACHE_OUTPUT: t('pages.models.priceType.CACHE_OUTPUT'),
      IMAGE_INPUT: t('pages.models.priceType.IMAGE_INPUT'),
      IMAGE_OUTPUT: t('pages.models.priceType.IMAGE_OUTPUT'),
      AUDIO_INPUT: t('pages.models.priceType.AUDIO_INPUT'),
      AUDIO_OUTPUT: t('pages.models.priceType.AUDIO_OUTPUT'),
      VIDEO: t('pages.models.priceType.VIDEO'),
      SEARCH: t('pages.models.priceType.SEARCH'),
    }),
    [priceLabels, t],
  );
  const modalityLabels = useMemo(
    () => ({
      TEXT: t('pages.models.modality.TEXT'),
      IMAGE: t('pages.models.modality.IMAGE'),
      AUDIO: t('pages.models.modality.AUDIO'),
      VIDEO: t('pages.models.modality.VIDEO'),
      FILE: t('pages.models.modality.FILE'),
    }),
    [t],
  );

  if (expectedId === null) {
    return (
      <div className="public-page">
        <InvalidRef backPath={backPath} />
      </div>
    );
  }

  const error = detail.error;
  const notFound = error instanceof PortalApiError && error.status === 404;

  // 只使用与引用匹配的响应；ID 不符按契约错误处理，不展示另一个模型。
  const model =
    detail.isSuccess && detail.data && modelRef
      ? resolveDetailModel(modelRef, detail.data.data.model)
      : null;
  const contractError = detail.isSuccess && model === null;

  const basePricing = model ? formatBasePricing(model.pricing, priceLabels) : null;
  const enhanced = model ? formatEnhancedPricing(model.enhancedPricing, enhancedLabels) : null;

  return (
    <div className="public-page">
      <div className="public-page-head">
        <p className="page-kicker">Model Catalog</p>
        <h1>{t('pages.models.detail.title')}</h1>
      </div>

      <Link className="detail-back" to={backPath}>
        {t('pages.models.detail.backToCatalog')}
      </Link>

      {detail.isPending ? <p role="status">{t('states.loading')}</p> : null}

      {notFound ? <p role="alert">{t('pages.models.detail.notFound')}</p> : null}

      {error && !notFound ? (
        <div className="detail-error">
          <p role="alert">{t('pages.models.detail.loadError')}</p>
          <Button type="button" intent="neutral" emphasis="outline" onClick={() => detail.refetch()}>
            {t('common.retry')}
          </Button>
        </div>
      ) : null}

      {contractError ? <p role="alert">{t('pages.models.detail.contractError')}</p> : null}

      {versionRelation && versionRelation !== 'matched' && model ? (
        <p role="status" className="catalog-consistency-note">
          {t('pages.models.detail.catalogUnverified')}
          {versionRelation === 'mismatched' ? (
            <Button
              type="button"
              intent="neutral"
              emphasis="outline"
              size="sm"
              disabled={recoveringModelRef === modelRef}
              onClick={() => void retryConsistency()}
            >
              {t('pages.models.retryConflict')}
            </Button>
          ) : null}
        </p>
      ) : null}

      {model ? (
        <div className="detail-stack">
          <section className="docs-section">
            <h2>{t('pages.models.detail.identityTitle')}</h2>
            <dl className="detail-list">
              <Field label={t('pages.models.detail.modelId')} value={model.id} />
              <Field
                label={t('pages.models.detail.displayName')}
                value={describeOptionalText(model.displayName, unknown).text}
              />
              <Field
                label={t('pages.models.detail.provider')}
                value={describeOptionalText(model.provider, unknown).text}
              />
              <Field label={t('pages.models.detail.availability')} value={model.availability} />
            </dl>
            <p className="detail-note">{t('pages.models.detail.availabilityNote')}</p>
            <div className="detail-actions">
              <CopyModelId modelId={model.id} density="md" />
              <Link className="detail-link" to={buildDocsPath(model.id) ?? backPath}>
                {t('pages.models.detail.viewExample')}
              </Link>
            </div>
          </section>

          <section className="docs-section">
            <h2>{t('pages.models.basePriceTitle')}</h2>
            {basePricing && basePricing.rows.length > 0 ? (
              <dl className="detail-list">
                {basePricing.rows.map((row) => (
                  <Field key={row.key} label={row.label} value={`${row.value} ${basePricing.unitLabel}`} />
                ))}
              </dl>
            ) : (
              <p>{t('pages.models.priceUnavailable')}</p>
            )}
            <p className="detail-note">{t('pages.models.basePriceDisclaimer')}</p>
          </section>

          <section className="docs-section">
            <h2>{t('pages.models.detail.limitsTitle')}</h2>
            <dl className="detail-list" aria-label={t('pages.models.detail.limitsTitle')}>
              <Field
                label={t('pages.models.detail.contextWindow')}
                value={describeOptionalNumber(model.contextWindowTokens, unknown).text}
              />
              <Field
                label={t('pages.models.detail.maxOutputTokens')}
                value={describeOptionalNumber(model.maxOutputTokens, unknown).text}
              />
              <Field
                label={t('pages.models.detail.inputModalities')}
                value={describeModalities(model.inputModalities as ModelModality[] | null, modalityLabels, unknown, t('pages.models.none')).text}
              />
              <Field
                label={t('pages.models.detail.outputModalities')}
                value={describeModalities(model.outputModalities as ModelModality[] | null, modalityLabels, unknown, t('pages.models.none')).text}
              />
            </dl>
          </section>

          <section className="docs-section">
            <h2>{t('pages.models.detail.capabilitiesTitle')}</h2>
            <dl className="detail-list" aria-label={t('pages.models.detail.capabilitiesTitle')}>
              {(['toolCalling', 'reasoning', 'structuredOutput', 'attachments'] as const).map((key) => (
                <Field
                  key={key}
                  label={t(`pages.models.detail.${key}`)}
                  value={describeCapability(model.capabilities[key], {
                    supported: t('pages.models.supported'),
                    unsupported: t('pages.models.unsupported'),
                    unknown,
                  })}
                />
              ))}
            </dl>
          </section>

          <section className="docs-section">
            <h2>{t('pages.models.detail.releaseTitle')}</h2>
            <dl className="detail-list">
              <Field
                label={t('pages.models.detail.releaseDate')}
                value={describeReleaseDate(model.releaseDate, i18n.language, unknown).text}
              />
              <Field
                label={t('pages.models.detail.descriptionField')}
                value={describeOptionalText(model.description, unknown).text}
              />
              <Field
                label={t('pages.models.detail.tags')}
                value={describeTags(model.tags, unknown, t('pages.models.noTags')).text}
              />
            </dl>
          </section>

          <section className="docs-section">
            <h2>{t('pages.models.detail.enhancedTitle')}</h2>
            {enhanced && enhanced.state === 'items' ? (
              <dl className="detail-list" aria-label={t('pages.models.detail.enhancedTitle')}>
                {enhanced.rows.map((row) => (
                  <Field key={row.key} label={row.label} value={`${row.value} ${row.unitLabel}`} />
                ))}
              </dl>
            ) : (
              <p>{t(enhanced?.state === 'verified-empty'
                ? 'pages.models.detail.enhancedEmpty'
                : 'pages.models.detail.enhancedUnknown')}</p>
            )}
          </section>
        </div>
      ) : null}
    </div>
  );
}

function InvalidRef({ backPath }: { backPath: string }) {
  const { t } = useTranslation();
  return (
    <div className="detail-state">
      <p role="alert">{t('pages.models.detail.invalidRef')}</p>
      <Link to={backPath}>{t('pages.models.detail.backToCatalog')}</Link>
    </div>
  );
}
