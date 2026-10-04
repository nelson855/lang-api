import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useSearchParams } from 'react-router';
import { useModels } from '../api/useModels';
import { usePublicConfig } from '../api/usePublicConfig';
import { resolveDocsModelSelection } from '../features/catalog/filter';
import {
  buildCurlNonStreaming,
  buildCurlStreaming,
  buildSdkNonStreaming,
  buildSdkStreaming,
  normalizeBaseUrl,
} from '../features/docs/templates';
import { CodeBlock } from '../components/CodeBlock';
import './PublicPages.css';

function openAiBaseUrl(apiBaseUrls: { protocol: string; url: string }[]): string | null {
  const hit = apiBaseUrls.find((e) => e.protocol === 'OPENAI');
  return hit ? hit.url : null;
}

export function DocsPage() {
  const { t } = useTranslation();
  const [params] = useSearchParams();
  const modelsQuery = useModels();
  const configQuery = usePublicConfig();

  const models = useMemo(
    () => modelsQuery.data?.data.models ?? [],
    [modelsQuery.data],
  );
  // 参数缺失与参数显式为空是两种状态：前者走默认选择，后者视为非法。
  const selection = useMemo(
    () => resolveDocsModelSelection(models, params.has('model') ? params.get('model') : null),
    [models, params],
  );
  const selected = selection.kind === 'default' || selection.kind === 'explicit' ? selection.model : null;
  const baseUrlRaw = configQuery.data ? openAiBaseUrl(configQuery.data.data.apiBaseUrls) : null;
  const baseUrl = baseUrlRaw ? normalizeBaseUrl(baseUrlRaw) : null;
  const ready = !!baseUrl && !!selected;

  return (
    <div className="public-page">
      <div className="public-page-head"><p className="page-kicker">Documentation</p><h1>{t('pages.docs.title')}</h1></div>
      <div className="docs-stack"><section className="docs-section">
        <h2>{t('pages.docs.authTitle')}</h2>
        <p>{t('pages.docs.authBody')}</p>
      </section>
      <section className="docs-section">
        <h2>{t('pages.docs.baseUrlTitle')}</h2>
        {baseUrl ? (
          <CodeBlock code={baseUrl} language="text" />
        ) : (
          <p>{t('pages.docs.baseUrlUnavailable')}</p>
        )}
      </section>
      <section className="docs-section">
        <h2>{t('pages.docs.openSection')}</h2>
        <p>{t('pages.docs.openModels')}</p>
        <p>{t('pages.docs.openChat')}</p>
        <p>{t('pages.docs.closedNote')}</p>
      </section>
      {selection.kind === 'invalid' ? (
        <div className="detail-state" role="alert">
          <p>{t('pages.docs.selectedModelInvalid')}</p>
          <Link to="/models">{t('pages.docs.backToCatalog')}</Link>
        </div>
      ) : null}
      {selection.kind === 'unavailable' ? (
        <div className="detail-state" role="alert">
          <p>{t('pages.docs.selectedModelUnavailable')}</p>
          <Link to="/models">{t('pages.docs.backToCatalog')}</Link>
        </div>
      ) : null}
      {!ready && selection.kind === 'default' ? (
        <p>{t('pages.docs.noModel')}</p>
      ) : null}
      {ready ? (
        <>
          <section className="docs-section">
            <h2>{t('pages.docs.nonStreamingTitle')}</h2>
            <h3>cURL</h3>
            <CodeBlock code={buildCurlNonStreaming(baseUrl, selected.id)} language="bash" />
            <h3>OpenAI SDK</h3>
            <CodeBlock code={buildSdkNonStreaming(baseUrl, selected.id)} language="typescript" />
          </section>
          <section className="docs-section">
            <h2>{t('pages.docs.streamingTitle')}</h2>
            <h3>cURL</h3>
            <CodeBlock code={buildCurlStreaming(baseUrl, selected.id)} language="bash" />
            <h3>OpenAI SDK</h3>
            <CodeBlock code={buildSdkStreaming(baseUrl, selected.id)} language="typescript" />
          </section>
        </>
      ) : null}
      </div>
    </div>
  );
}
