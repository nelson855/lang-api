import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router';
import { useModels } from '../api/useModels';
import { filterModels, providerOptions } from '../features/catalog/filter';
import { Empty } from '../components/feedback/Feedback';

function formatPrice(model: { pricing: unknown }): string {
  const pricing = model.pricing as {
    mode?: string;
    input?: string | null;
    output?: string | null;
    request?: string | null;
    unit?: string;
  } | null;
  if (!pricing) {
    return '';
  }
  if (pricing.mode === 'TOKEN') {
    return `${pricing.input} / ${pricing.output} USD per 1M tokens`;
  }
  return `${pricing.request} USD per request`;
}

export function ModelsPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const query = useModels();
  const [search, setSearch] = useState('');
  const [provider, setProvider] = useState<string | null>(null);

  const models = useMemo(() => query.data?.data.models ?? [], [query.data]);
  const providers = useMemo(() => providerOptions(models), [models]);
  const visible = useMemo(() => filterModels(models, search, provider), [models, search, provider]);

  return (
    <>
      <h1>{t('pages.models.title')}</h1>
      <p>{t('pages.models.basePriceNote')}</p>
      {query.isPending ? <p>{t('states.loading')}</p> : null}
      {query.isError ? (
        <div>
          <p>{t('pages.models.loadError')}</p>
          <button type="button" onClick={() => query.refetch()}>
            {t('common.retry')}
          </button>
        </div>
      ) : null}
      {query.isSuccess ? (
        <>
          <label>
            {t('pages.models.searchLabel')}
            <input
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder={t('pages.models.searchPlaceholder')}
            />
          </label>
          <label>
            {t('pages.models.providerLabel')}
            <select
              value={provider ?? ''}
              onChange={(e) => setProvider(e.target.value ? e.target.value : null)}
            >
              <option value="">{t('pages.models.providerAll')}</option>
              {providers.map((p) => (
                <option key={p} value={p}>
                  {p}
                </option>
              ))}
            </select>
          </label>
          {models.length === 0 ? <Empty description={t('pages.models.empty')} /> : null}
          {models.length > 0 && visible.length === 0 ? <p>{t('pages.models.noMatch')}</p> : null}
          <ul>
            {visible.map((m) => (
              <li key={m.id}>
                <strong>{m.id}</strong>
                {m.provider ? <span> {m.provider}</span> : null}
                <span> {m.pricing ? formatPrice(m) : t('pages.models.priceUnavailable')}</span>
                <button
                  type="button"
                  onClick={() => navigate(`/docs?model=${encodeURIComponent(m.id)}`)}
                >
                  {t('pages.models.viewExample')}
                </button>
              </li>
            ))}
          </ul>
        </>
      ) : null}
    </>
  );
}
