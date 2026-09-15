import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { useSearchParams } from 'react-router';
import { useModels } from '../api/useModels';
import { usePublicConfig } from '../api/usePublicConfig';
import { resolveSelectedModel } from '../features/catalog/filter';
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
  const requested = params.get('model');
  const safeRequested = requested && requested.length <= 128 ? requested : null;
  const selected = useMemo(
    () => resolveSelectedModel(models, safeRequested),
    [models, safeRequested],
  );
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
      {!ready ? (
        <p>{t('pages.docs.noModel')}</p>
      ) : (
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
      )}
      </div>
    </div>
  );
}
