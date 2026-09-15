import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import type { UseQueryResult } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router';
import { I18nProvider } from '../../i18n/i18nProvider';
import { PortalApiError } from '../../api/envelope';
import type { LegalDocument } from '../../api/legal';
import { LegalDocumentView } from './LegalDocumentView';

function baseQuery(overrides: Partial<UseQueryResult<LegalDocument>>): UseQueryResult<LegalDocument> {
  return {
    data: undefined,
    error: null,
    isPending: false,
    isError: false,
    isSuccess: false,
    refetch: vi.fn(),
    ...overrides,
  } as UseQueryResult<LegalDocument>;
}

function setup(query: UseQueryResult<LegalDocument>) {
  return render(
    <MemoryRouter>
      <I18nProvider initialLocale="zh-CN">
        <LegalDocumentView query={query} />
      </I18nProvider>
    </MemoryRouter>,
  );
}

describe('LegalDocumentView 法律页面状态', () => {
  it('加载中显示等待状态', () => {
    setup(baseQuery({ isPending: true }));
    expect(screen.getByRole('status')).toBeInTheDocument();
  });

  it('正文就绪时只渲染服务端标题与正文', () => {
    setup(
      baseQuery({
        isSuccess: true,
        data: { type: 'TERMS', title: '用户协议', contentHtml: '<p>第一条</p>', locale: 'zh-CN' },
      }),
    );
    expect(screen.getByRole('heading', { name: '用户协议' })).toBeInTheDocument();
    expect(screen.getByText('第一条')).toBeInTheDocument();
  });

  it('未发布时说明尚未就绪且不显示模拟正文', () => {
    const { container } = setup(
      baseQuery({ isError: true, error: new PortalApiError(404, 'NOT_FOUND', '不存在', 'req-1') }),
    );
    expect(screen.getByText(/尚未发布|尚未就绪/)).toBeInTheDocument();
    expect(container.textContent).not.toMatch(/示例条款|第一条/);
  });

  it('暂时失败时提示稍后重试且不展示原始服务消息', () => {
    const { container } = setup(
      baseQuery({ isError: true, error: new PortalApiError(502, 'UPSTREAM_ERROR', 'upstream boom', 'req-2') }),
    );
    expect(screen.getByRole('heading', { name: '法律正文暂时不可用' })).toBeInTheDocument();
    expect(screen.getByText('内容加载失败，请稍后重试。')).toBeInTheDocument();
    expect(container.textContent).not.toMatch(/upstream boom/);
  });
});
