// api-boundary-exempt: favicon 无外部引用断言自身需写出地址匹配模式
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import type { ReactNode } from 'react';
import { portalRequest } from '../../api/portalClient';
import { PublicConfigGate } from '../../app/providers/publicConfigGate';
import { Brand } from './Brand';

vi.mock('../../api/portalClient', () => ({
  portalRequest: vi.fn(),
}));

function setup(siteName: string, children?: ReactNode) {
  vi.mocked(portalRequest).mockResolvedValue({
    data: { siteName, apiBaseUrls: [] },
    requestId: 'req-brand',
  });
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <PublicConfigGate>{children ?? <Brand />}</PublicConfigGate>
    </QueryClientProvider>,
  );
}

describe('临时品牌标识', () => {
  beforeEach(() => {
    vi.mocked(portalRequest).mockReset();
  });

  it('显示名只读取运行时 siteName', async () => {
    setup('测试站点');
    expect(await screen.findByText('测试站点')).toBeInTheDocument();
  });

  it('使用自有几何标记且不含参考站元素', async () => {
    const { container } = setup('Lang API');
    await screen.findByText('Lang API');
    const svg = container.querySelector('svg');
    expect(svg).not.toBeNull();
    expect(svg?.getAttribute('aria-hidden')).toBe('true');
    expect(document.body.textContent).not.toMatch(/Qinghua|New API/i);
  });

  it('品牌标识具有可访问名称', async () => {
    setup('测试站点');
    expect(await screen.findByRole('img', { name: '测试站点' })).toBeInTheDocument();
  });

  it('同源 favicon 与组件共用同一几何资产且无外部引用', async () => {
    const [{ BRAND_MARK_PATHS }, fs] = await Promise.all([
      import('./brandAsset'),
      import('node:fs'),
    ]);
    const favicon = fs.readFileSync('public/favicon.svg', 'utf8');
    expect(favicon).toContain('<svg');
    for (const path of BRAND_MARK_PATHS) {
      expect(favicon).toContain(path);
    }
    const withoutNamespace = favicon.replace('xmlns="http://www.w3.org/2000/svg"', '');
    expect(withoutNamespace).not.toMatch(/https?:\/\//);
  });
});
