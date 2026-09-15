// api-boundary-exempt: 协议校验测试自身必须使用绝对地址用例
import { describe, expect, it } from 'vitest';import { render, screen } from '@testing-library/react';
import { I18nProvider } from '../../i18n/i18nProvider';
import { SupportLink } from './SupportLink';

function setup(url: string) {
  return render(
    <I18nProvider initialLocale="zh-CN">
      <SupportLink url={url}>联系支持</SupportLink>
    </I18nProvider>,
  );
}

describe('SupportLink 运行时支持链接', () => {
  it('HTTPS 支持页在新窗口打开并带安全 rel', () => {
    setup('https://support.portal.example/contact');
    const link = screen.getByRole('link', { name: '联系支持' });
    expect(link).toHaveAttribute('href', 'https://support.portal.example/contact');
    expect(link).toHaveAttribute('target', '_blank');
    expect(link).toHaveAttribute('rel', 'noopener noreferrer');
  });

  it('邮件支持入口直接使用 mailto 且不在新窗口打开', () => {
    setup('mailto:support@portal.example');
    const link = screen.getByRole('link', { name: '联系支持' });
    expect(link).toHaveAttribute('href', 'mailto:support@portal.example');
    expect(link).not.toHaveAttribute('target');
  });

  it('空入口显示未公布状态且不生成无行为链接', () => {
    const { container } = setup('');
    expect(screen.queryByRole('link')).not.toBeInTheDocument();
    expect(container.textContent).toMatch(/暂未公布/);
  });

  it('危险协议与带用户信息的地址不渲染为链接', () => {
    const { container, rerender } = setup('javascript:alert(1)');
    expect(screen.queryByRole('link')).not.toBeInTheDocument();
    expect(container.textContent).toMatch(/暂未公布/);
    rerender(
      <I18nProvider initialLocale="zh-CN">
        <SupportLink url="https://user:secret@evil.example/">联系支持</SupportLink>
      </I18nProvider>,
    );
    expect(screen.queryByRole('link')).not.toBeInTheDocument();
  });
});
