import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { I18nProvider } from '../../i18n/i18nProvider';
import { LegalDocumentContent } from './LegalDocumentContent';

function setup(html: string, locale: 'zh-CN' | 'en-US' = 'zh-CN') {
  return render(
    <I18nProvider initialLocale="zh-CN">
      <LegalDocumentContent title="用户协议" contentHtml={html} locale={locale} />
    </I18nProvider>,
  );
}

describe('LegalDocumentContent 专用法律正文组件', () => {
  it('只消费服务端正文并标记正文语言', () => {
    const { container } = setup('<p>第一条</p>', 'zh-CN');
    expect(screen.getByRole('heading', { name: '用户协议' })).toBeInTheDocument();
    expect(screen.getByText('第一条')).toBeInTheDocument();
    const article = container.querySelector('article');
    expect(article?.getAttribute('lang')).toBe('zh-CN');
  });

  it('跟随服务端语言标记英文正文', () => {
    const { container } = setup('<p>Terms</p>', 'en-US');
    expect(container.querySelector('article')?.getAttribute('lang')).toBe('en-US');
    expect(screen.getByText('Terms')).toBeInTheDocument();
  });

  it('不渲染占位正文且长内容限制在正文容器内滚动', () => {
    const { container } = setup('<table><tr><td>很长很长的条款表格</td></tr></table>');
    expect(container.textContent).not.toMatch(/示例条款|占位/);
    expect(container.querySelector('.legal-document-body')).not.toBeNull();
  });
});
