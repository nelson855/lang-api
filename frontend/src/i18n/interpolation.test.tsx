import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { useTranslation } from 'react-i18next';
import { I18nProvider } from './i18nProvider';

function SiteNameProbe({ siteName }: { siteName: string }) {
  const { t } = useTranslation();
  return <p>{t('pages.home.subtitle', { siteName })}</p>;
}

describe('插值安全', () => {
  it('类 HTML 的运行时文案只显示为文本', () => {
    render(
      <I18nProvider initialLocale="zh-CN">
        <SiteNameProbe siteName="<img src=x onerror=alert(1)>" />
      </I18nProvider>,
    );
    expect(document.querySelector('img')).toBeNull();
    const paragraph = document.querySelector('p');
    expect(paragraph?.textContent).toContain('<img src=x onerror=alert(1)>');
    expect(screen.getByText(/欢迎来到/, { exact: false })).toBeInTheDocument();
  });
});
