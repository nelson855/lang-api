import type { SupportedLocale } from '../../i18n/locale';
import './LegalDocumentContent.css';

export function LegalDocumentContent({
  title,
  contentHtml,
  locale,
}: {
  title: string;
  contentHtml: string;
  locale: SupportedLocale;
}) {
  return (
    <article className="legal-document" lang={locale}>
      <h1 className="legal-document-title">{title}</h1>
      {/* 正文 HTML 来自服务端白名单净化结果，此处是全站唯一允许的受控渲染入口 */}
      <div className="legal-document-body" dangerouslySetInnerHTML={{ __html: contentHtml }} />
    </article>
  );
}
