import { fireEvent, render, screen } from '@testing-library/react';
import { useState } from 'react';
import { describe, expect, it, beforeEach } from 'vitest';
import { useTranslation } from 'react-i18next';
import { I18nProvider } from './i18nProvider';
import { LanguageSwitcher } from './LanguageSwitcher';

function TitleProbe() {
  const { t } = useTranslation();
  return <h1>{t('pages.home.title')}</h1>;
}

function CounterProbe() {
  const [count] = useState(42);
  return <p>{`count=${count}`}</p>;
}

describe('语言切换基础设施', () => {
  beforeEach(() => {
    localStorage.clear();
    document.documentElement.setAttribute('lang', '');
  });

  it('按初始语言渲染并同步页面语言属性', () => {
    render(
      <I18nProvider initialLocale="en-US">
        <TitleProbe />
      </I18nProvider>,
    );
    expect(screen.getByRole('heading', { name: 'Self-hosted language model gateway' })).toBeInTheDocument();
    expect(document.documentElement.getAttribute('lang')).toBe('en-US');
  });

  it('切换语言无刷新更新且保留页面状态', () => {
    render(
      <I18nProvider initialLocale="zh-CN">
        <TitleProbe />
        <CounterProbe />
        <LanguageSwitcher />
      </I18nProvider>,
    );
    expect(screen.getByRole('heading', { name: '自有语言模型网关' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'English' }));
    expect(screen.getByRole('heading', { name: 'Self-hosted language model gateway' })).toBeInTheDocument();
    expect(screen.getByText('count=42')).toBeInTheDocument();
    expect(document.documentElement.getAttribute('lang')).toBe('en-US');
    expect(localStorage.getItem('lang-api:locale')).toBe('en-US');
  });

  it('只展示运行时启用的语言', () => {
    render(
      <I18nProvider initialLocale="zh-CN">
        <TitleProbe />
        <LanguageSwitcher enabledLocales={['en-US']} />
      </I18nProvider>,
    );
    expect(screen.queryByRole('button', { name: '中文' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'English' })).toBeInTheDocument();
  });
});
