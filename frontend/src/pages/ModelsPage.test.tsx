import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { PortalApiError } from '../api/envelope';
import { portalRequest } from '../api/portalClient';
import { renderApp } from '../test/renderApp';
import { ModelsPage } from './ModelsPage';

vi.mock('../api/portalClient', () => ({
  portalRequest: vi.fn(),
}));

const requestMock = vi.mocked(portalRequest);

function model(id: string, provider: string | null, pricing: unknown = null) {
  return { id, displayName: null, provider, availability: 'AVAILABLE', pricing };
}

const TOKEN_PRICING = {
  mode: 'TOKEN',
  currency: 'USD',
  unit: 'PER_MILLION_TOKENS',
  input: '75.0',
  output: '150.0',
  request: null,
};

function listOk(models = [model('deepseek-v4-flash', 'DeepSeek', TOKEN_PRICING), model('gpt-4o', 'OpenAI')]) {
  return { data: { pricingVersion: 'v1', models }, requestId: 'req-list' };
}

function providersOk(providers = [{ value: 'DeepSeek', label: 'DeepSeek', modelCount: 1 }, { value: 'OpenAI', label: 'OpenAI', modelCount: 1 }]) {
  return { data: { pricingVersion: 'v1', providers }, requestId: 'req-providers' };
}

type Mode = 'ok' | 'list-fail' | 'providers-fail' | 'version-conflict';

function respondByPath(mode: Mode) {
  requestMock.mockImplementation(async (path) => {
    if (path === '/portal/api/public-config') {
      return respondByPath.publicConfig;
    }
    if (path === '/portal/api/models') {
      if (mode === 'list-fail') {
        throw new PortalApiError(502, 'UPSTREAM_ERROR', '上游不可用');
      }
      return listOk() as never;
    }
    if (path === '/portal/api/model-providers') {
      if (mode === 'providers-fail') {
        throw new PortalApiError(502, 'UPSTREAM_ERROR', '上游不可用');
      }
      if (mode === 'version-conflict') {
        return { data: { pricingVersion: 'v2', providers: [{ value: 'DeepSeek', label: 'DeepSeek', modelCount: 1 }] }, requestId: 'req' } as never;
      }
      return providersOk() as never;
    }
    throw new Error(`未预期的路径 ${path}`);
  });
}

respondByPath.publicConfig = {
  data: {
    publicationMode: 'PREVIEW',
    siteUrl: '',
    supportUrl: 'mailto:dev@example.test',
    supportedRegions: ['CN'],
    enabledLocales: ['zh-CN', 'en-US'],
    siteName: 'Test',
    apiBaseUrls: [{ protocol: 'OPENAI', url: '/gateway/v1/example' }],
  },
  requestId: 'req-config',
} as never;

function paths() {
  return requestMock.mock.calls.map((call) => call[0]);
}

describe('模型广场列表', () => {
  beforeEach(() => {
    requestMock.mockReset();
    respondByPath('ok');
  });

  it('展示目录并使用供应商接口的完整计数', async () => {
    renderApp(<ModelsPage />);
    expect(await screen.findByText('deepseek-v4-flash')).toBeInTheDocument();
    expect(screen.getByText('gpt-4o')).toBeInTheDocument();
    expect(paths()).toContain('/portal/api/model-providers');
  });

  it('价格缺失时显示价格暂不可用而不是空白', async () => {
    renderApp(<ModelsPage />);
    const card = (await screen.findByText('gpt-4o')).closest('li') as HTMLElement;
    expect(within(card).getByText('价格暂不可用')).toBeInTheDocument();
  });

  it('目录为空时说明尚未配置公开模型', async () => {
    requestMock.mockImplementation(async (path) => {
      if (path === '/portal/api/public-config') {
        return respondByPath.publicConfig;
      }
      return path === '/portal/api/models' ? (listOk([]) as never) : (providersOk([]) as never);
    });
    renderApp(<ModelsPage />);
    expect(await screen.findByText('当前尚未配置公开模型。')).toBeInTheDocument();
  });

  it('列表失败时显示安全错误与重试', async () => {
    respondByPath('list-fail');
    renderApp(<ModelsPage />);
    expect(await screen.findByText('模型目录加载失败')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '重试' })).toBeInTheDocument();
  });

  it('供应商失败时保留列表与搜索，单独提示厂商失败', async () => {
    respondByPath('providers-fail');
    renderApp(<ModelsPage />);
    expect(await screen.findByText('deepseek-v4-flash')).toBeInTheDocument();
    expect(await screen.findByText('厂商选项加载失败')).toBeInTheDocument();
  });

  it('版本冲突时不启用冲突的厂商选项并允许手动重试', async () => {
    respondByPath('version-conflict');
    renderApp(<ModelsPage />);
    expect(await screen.findByText('deepseek-v4-flash')).toBeInTheDocument();
    expect(await screen.findByText(/目录版本不一致/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '重试一致性校验' })).toBeInTheDocument();
  });

  it('从 URL 恢复搜索与厂商条件', async () => {
    renderApp(<ModelsPage />, { initialEntries: ['/models?q=gpt&provider=OpenAI'] });
    expect(await screen.findByText('gpt-4o')).toBeInTheDocument();
    expect(screen.queryByText('deepseek-v4-flash')).not.toBeInTheDocument();
  });

  it('搜索替换历史项且不新增目录请求', async () => {
    renderApp(<ModelsPage />);
    await screen.findByText('deepseek-v4-flash');
    const before = paths().filter((p) => p === '/portal/api/models').length;

    await userEvent.type(screen.getByRole('searchbox', { name: /搜索模型/ }), 'gpt');

    await waitFor(() => expect(screen.queryByText('deepseek-v4-flash')).not.toBeInTheDocument());
    expect(screen.getByText('gpt-4o')).toBeInTheDocument();
    expect(paths().filter((p) => p === '/portal/api/models').length).toBe(before);
  });

  it('清除搜索立即生效并恢复焦点', async () => {
    renderApp(<ModelsPage />);
    await screen.findByText('deepseek-v4-flash');
    const input = screen.getByRole('searchbox', { name: /搜索模型/ });
    await userEvent.type(input, 'gpt');
    await waitFor(() => expect(screen.queryByText('deepseek-v4-flash')).not.toBeInTheDocument());

    await userEvent.click(screen.getByRole('button', { name: '清除搜索' }));

    expect(await screen.findByText('deepseek-v4-flash')).toBeInTheDocument();
    await waitFor(() => expect(screen.getByRole('searchbox', { name: /搜索模型/ })).toHaveFocus());
  });

  it('URL 中的未知厂商保留为可清除条件且不展示全部模型', async () => {
    renderApp(<ModelsPage />, { initialEntries: ['/models?provider=Gone'] });
    expect(await screen.findByText(/未知厂商：Gone/)).toBeInTheDocument();
    expect(screen.queryByText('deepseek-v4-flash')).not.toBeInTheDocument();
    expect(screen.queryByText('gpt-4o')).not.toBeInTheDocument();
  });

  it('卡片提供详情链接与复制入口，不为每张卡片请求详情', async () => {
    renderApp(<ModelsPage />);
    const card = (await screen.findByText('deepseek-v4-flash')).closest('li') as HTMLElement;
    expect(within(card).getByRole('link', { name: '查看详情' })).toHaveAttribute(
      'href',
      expect.stringContaining('/models/ZGVlcHNlZWstdjQtZmxhc2g'),
    );
    expect(within(card).getByRole('link', { name: '查看调用示例' })).toHaveAttribute(
      'href',
      '/docs?model=deepseek-v4-flash',
    );
    expect(paths().filter((p) => p.startsWith('/portal/api/models/'))).toHaveLength(0);
  });

  it('输入法组合期间不触发导航，组合结束同步搜索', async () => {
    renderApp(<ModelsPage />);
    await screen.findByText('deepseek-v4-flash');
    const input = screen.getByRole('searchbox', { name: /搜索模型/ });

    fireEvent.compositionStart(input);
    fireEvent.change(input, { target: { value: 'gp' } });
    expect(input).toHaveValue('gp');
    // 组合期间结果不变：两条都还在（未提交到 URL）。
    expect(screen.getByText('deepseek-v4-flash')).toBeInTheDocument();
    expect(screen.getByText('gpt-4o')).toBeInTheDocument();

    // 组合完成：输入框拿到最终文本，此时才提交一次搜索。
    fireEvent.change(input, { target: { value: 'gpt' } });
    fireEvent.compositionEnd(input, { data: 'gpt' });
    await waitFor(() => expect(screen.getByText('gpt-4o')).toBeInTheDocument());
    expect(screen.queryByText('deepseek-v4-flash')).not.toBeInTheDocument();
  });

  it('英文界面使用英文文案', async () => {
    renderApp(<ModelsPage />, { locale: 'en-US' });
    expect(await screen.findByText('deepseek-v4-flash')).toBeInTheDocument();
    expect(screen.getAllByRole('link', { name: 'View details' })).toHaveLength(2);
    expect(screen.getAllByRole('button', { name: 'Copy model ID' })).toHaveLength(2);
  });
  it('供应商首次失败时可清除 URL 中已有厂商条件', async () => {
    respondByPath('providers-fail');
    renderApp(<ModelsPage />, { initialEntries: ['/models?provider=DeepSeek'] });
    await screen.findByText('厂商选项加载失败');
    expect(screen.queryByText('gpt-4o')).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: '清除厂商条件' }));
    expect(await screen.findByText('gpt-4o')).toBeInTheDocument();
  });

});
