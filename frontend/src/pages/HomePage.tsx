import { Link } from 'react-router';
import { useTranslation } from 'react-i18next';
import { usePublicConfigData } from '../app/providers/publicConfigGate';
import './PublicPages.css';

const trace = `{
  "model": "your-model-id",
  "messages": [{ "role": "user", "content": "Hello" }],
  "stream": true
}`;

export function HomePage() {
  const { t } = useTranslation();
  const config = usePublicConfigData();
  const isPreview = (config.publicationMode ?? 'PREVIEW') !== 'PUBLIC';
  const protocols = config.apiBaseUrls ?? [];
  return (
    <div className="public-page">
      <section className="home-hero">
        <div>
          <p className="page-kicker">API Gateway</p>
          <h1 className="home-title">{t('pages.home.title')}</h1>
          <p className="home-copy">{t('pages.home.subtitle', { siteName: config.siteName })}</p>
          {isPreview ? <p className="home-copy">{t('pages.home.previewNote')}</p> : null}
          <div className="home-actions">
            <Link to="/docs">{t('nav.docs')}</Link>
            <Link to="/models">{t('nav.models')}</Link>
          </div>
        </div>
        <div className="request-trace" aria-label="API request trace">
          <div className="request-trace-head">
            <span>POST /v1/chat/completions</span>
            <span className="request-trace-status">{t('pages.home.requestExample')}</span>
          </div>
          <pre><code>{trace}</code></pre>
        </div>
      </section>
      <div className="home-trust"><span>OPENAI COMPATIBLE</span><span>STREAMING READY</span><span>KEY-BASED ACCESS</span><span>REQUEST OBSERVABILITY</span></div>
      <section className="home-section">
        <div className="home-section-head"><div><p className="page-kicker">Workflow</p><h2>从选择模型到发出第一条请求。</h2></div><p>只呈现实际可用的能力与接入路径，不用虚构指标替代产品信息。</p></div>
        <div className="workflow-grid">
          <article className="workflow-card"><span className="workflow-step">01 / Explore</span><h3>{t('nav.models')}</h3><p>查看当前可用的模型、提供方和公开价格信息。</p></article>
          <article className="workflow-card"><span className="workflow-step">02 / Integrate</span><h3>{t('nav.docs')}</h3><p>用兼容 OpenAI 的端点与示例快速接入。</p></article>
          <article className="workflow-card"><span className="workflow-step">03 / Operate</span><h3>Console</h3><p>登录后管理密钥、请求记录、用量与账户设置。</p></article>
        </div>
      </section>
      {protocols.length === 0 ? <p className="home-copy">{t('pages.home.modelsUnavailable')}</p> : null}
      <nav className="home-trust" aria-label={t('nav.legal')}>
        <Link to="/terms">{t('nav.terms')}</Link>
        <Link to="/privacy">{t('nav.privacy')}</Link>
        <Link to="/regions">{t('nav.regions')}</Link>
      </nav>
    </div>
  );
}
