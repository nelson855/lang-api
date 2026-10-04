import { act, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { PortalApiError } from '../api/envelope';
import { portalRequest } from '../api/portalClient';
import { renderApp } from '../test/renderApp';
import { ModelDetailPage } from './ModelDetailPage';
import { Route, Routes, useNavigate } from 'react-router';

/** 用真实路由包裹详情页，确保 useParams 与路径参数行为和线上一致。 */
function DetailRoute({ path = '/models/:modelRef' }: { path?: string }) {
  return (
    <Routes>
      <Route path={path} element={<ModelDetailPage />} />
    </Routes>
  );
}

vi.mock('../api/portalClient', () => ({
  portalRequest: vi.fn(),
}));

const requestMock = vi.mocked(portalRequest);

const REF_ABC = 'YWJj';
const REF_XYZ = 'eHl6';

const CONFIG = {
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

function detailBody(overrides: Record<string, unknown> = {}) {
  return {
    data: {
      pricingVersion: 'v1',
      model: {
        id: 'abc',
        displayName: null,
        provider: 'Example',
        availability: 'AVAILABLE',
        pricing: {
          mode: 'TOKEN',
          currency: 'USD',
          unit: 'PER_MILLION_TOKENS',
          input: '75.0',
          output: '150.0',
          request: null,
        },
        contextWindowTokens: null,
        maxOutputTokens: null,
        inputModalities: null,
        outputModalities: null,
        capabilities: { toolCalling: null, reasoning: null, structuredOutput: null, attachments: null },
        releaseDate: null,
        description: null,
        tags: null,
        sortOrder: null,
        enhancedPricing: null,
        ...overrides,
      },
    },
    requestId: 'req-detail',
  } as never;
}

type Mode = 'ok' | 'not-found' | 'upstream-fail' | 'contract' | 'list-version-conflict';

function respond(mode: Mode) {
  requestMock.mockImplementation(async (path) => {
    if (path === '/portal/api/public-config') {
      return CONFIG;
    }
    if (path === '/portal/api/models') {
      return {
        data: {
          pricingVersion: mode === 'list-version-conflict' ? 'v-list-old' : 'v1',
          models: [
            {
              id: 'abc',
              displayName: null,
              provider: 'Example',
              availability: 'AVAILABLE',
              pricing: null,
            },
          ],
        },
        requestId: 'req-list',
      } as never;
    }
    if (path.startsWith('/portal/api/models/')) {
      if (mode === 'not-found') {
        throw new PortalApiError(404, 'NOT_FOUND', '模型不存在');
      }
      if (mode === 'upstream-fail') {
        throw new PortalApiError(502, 'UPSTREAM_ERROR', '上游不可用');
      }
      if (mode === 'contract') {
        return detailBody({ id: 'someone-else' });
      }
      const id = path.endsWith(REF_XYZ) ? 'xyz' : 'abc';
      return detailBody({ id });
    }
    throw new Error(`未预期的路径 ${path}`);
  });
}

function detailPaths() {
  return requestMock.mock.calls
    .map((call) => call[0])
    .filter((path) => path.startsWith('/portal/api/models/'));
}

describe('模型详情页', () => {
  beforeEach(() => {
    requestMock.mockReset();
    respond('ok');
  });

  it('直达详情只请求详情接口', async () => {
    renderApp(<DetailRoute />, { initialEntries: [`/models/${REF_ABC}`] });
    expect(await screen.findByText('abc')).toBeInTheDocument();
    expect(detailPaths()).toEqual([`/portal/api/models/${REF_ABC}`]);
    expect(requestMock.mock.calls.map((c) => c[0])).not.toContain('/portal/api/models');
  });

  it('当前全 null 基线下各区展示暂无数据而不塌陷', async () => {
    renderApp(<DetailRoute />, { initialEntries: [`/models/${REF_ABC}`] });
    await screen.findByText('abc');
    expect(screen.getByText('工具调用')).toBeInTheDocument();
    expect(screen.getAllByText('暂无数据').length).toBeGreaterThan(3);
  });

  it('能力 true / false / null 分别展示支持、不支持、暂无数据', async () => {
    requestMock.mockImplementation(async (path) => {
      if (path === '/portal/api/public-config') return CONFIG;
      return detailBody({
        capabilities: { toolCalling: true, reasoning: false, structuredOutput: null, attachments: false },
      });
    });
    renderApp(<DetailRoute />, { initialEntries: [`/models/${REF_ABC}`] });
    await screen.findByText('abc');
    expect(screen.getByText('工具调用').nextElementSibling).toHaveTextContent('支持');
    expect(screen.getByText('推理').nextElementSibling).toHaveTextContent('不支持');
    expect(screen.getByText('结构化输出').nextElementSibling).toHaveTextContent('暂无数据');
  });

  it('空数组与零值分别表达无与零', async () => {
    requestMock.mockImplementation(async (path) => {
      if (path === '/portal/api/public-config') return CONFIG;
      return detailBody({ tags: [], contextWindowTokens: 0, inputModalities: [] });
    });
    renderApp(<DetailRoute />, { initialEntries: [`/models/${REF_ABC}`] });
    await screen.findByText('abc');
    expect(screen.getByText('上下文 Token 上限').nextElementSibling).toHaveTextContent('0');
    expect(screen.getByText('输入模态').nextElementSibling).toHaveTextContent('无');
    expect(screen.getByText('无标签')).toBeInTheDocument();
  });

  it('发布日期按日历日期展示不偏移', async () => {
    requestMock.mockImplementation(async (path) => {
      if (path === '/portal/api/public-config') return CONFIG;
      return detailBody({ releaseDate: '2026-01-01' });
    });
    renderApp(<DetailRoute />, { initialEntries: [`/models/${REF_ABC}`] });
    await screen.findByText('abc');
    expect(screen.getByText('2026-01-01')).toBeInTheDocument();
  });

  it('增强价格 null 显示暂无数据而不是无额外收费', async () => {
    renderApp(<DetailRoute />, { initialEntries: [`/models/${REF_ABC}`] });
    await screen.findByText('abc');
    expect(
      screen.getByText('增强价格暂无数据，不代表该模型没有额外收费。'),
    ).toBeInTheDocument();
  });

  it('增强价格展示项目、单位与价格', async () => {
    requestMock.mockImplementation(async (path) => {
      if (path === '/portal/api/public-config') return CONFIG;
      return detailBody({
        enhancedPricing: [{ type: 'CACHE_INPUT', currency: 'USD', unit: 'PER_MILLION_TOKENS', price: '0.5' }],
      });
    });
    renderApp(<DetailRoute />, { initialEntries: [`/models/${REF_ABC}`] });
    await screen.findByText('abc');
    expect(screen.getByText('缓存输入')).toBeInTheDocument();
    expect(screen.getByText(/0\.5 USD \/ 百万 Token/)).toBeInTheDocument();
  });

  it('非法引用在请求前显示安全错误且不回显解码内容', async () => {
    renderApp(<DetailRoute />, { initialEntries: ['/models/YWJj='] });
    expect(await screen.findByText(/模型链接参数不合法/)).toBeInTheDocument();
    expect(detailPaths()).toHaveLength(0);
  });

  it('404 显示模型不存在或已下架', async () => {
    respond('not-found');
    renderApp(<DetailRoute />, { initialEntries: [`/models/${REF_ABC}`] });
    expect(await screen.findByText('模型不存在或已下架。')).toBeInTheDocument();
  });

  it('上游失败提供重试', async () => {
    respond('upstream-fail');
    renderApp(<DetailRoute />, { initialEntries: [`/models/${REF_ABC}`] });
    expect(await screen.findByText('模型详情加载失败')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '重试' })).toBeInTheDocument();
  });

  it('响应 ID 与引用不一致时按契约错误，不展示另一个模型', async () => {
    respond('contract');
    renderApp(<DetailRoute />, { initialEntries: [`/models/${REF_ABC}`] });
    expect(await screen.findByText(/模型详情与所请求的模型不一致/)).toBeInTheDocument();
    expect(screen.queryByText('someone-else')).not.toBeInTheDocument();
  });

  it('切换模型后旧响应不覆盖新模型页面', async () => {
    const gates: Array<(value: ReturnType<typeof detailBody>) => void> = [];
    requestMock.mockImplementation(async (path) => {
      if (path === '/portal/api/public-config') return CONFIG;
      if (path.endsWith(REF_ABC)) {
        return new Promise((resolve) => gates.push(resolve));
      }
      return detailBody({ id: 'xyz' });
    });

    function Switcher() {
      const navigate = useNavigate();
      return (
        <>
          <button type="button" onClick={() => navigate(`/models/${REF_XYZ}`)}>
            switch
          </button>
          <Routes>
            <Route path="/models/:modelRef" element={<ModelDetailPage />} />
          </Routes>
        </>
      );
    }

    renderApp(<Switcher />, { initialEntries: [`/models/${REF_ABC}`] });
    await screen.findByRole('button', { name: 'switch' });

    // A 的请求挂起时切到 B，B 先展示自己的数据。
    await userEvent.click(screen.getByRole('button', { name: 'switch' }));
    expect(await screen.findByText('xyz')).toBeInTheDocument();

    // A 的迟到响应不能覆盖 B 页面。
    for (const gate of gates) gate(detailBody({ id: 'abc' }));
    await waitFor(() => expect(screen.queryByText('abc')).not.toBeInTheDocument());
    expect(screen.getByText('xyz')).toBeInTheDocument();
  });

  it('返回模型广场使用受控同源路径并保留条件', async () => {
    renderApp(<DetailRoute />, { initialEntries: [`/models/${REF_ABC}?q=flash&provider=DeepSeek`] });
    const back = await screen.findByRole('link', { name: '返回模型广场' });
    expect(back).toHaveAttribute('href', '/models?q=flash&provider=DeepSeek');
  });

  it('提供文档入口使用原始 ID', async () => {
    renderApp(<DetailRoute />, { initialEntries: [`/models/${REF_ABC}`] });
    const docs = await screen.findByRole('link', { name: '查看调用示例' });
    expect(docs).toHaveAttribute('href', '/docs?model=abc');
  });

  it('提供复制原始模型 ID 的操作', async () => {
    renderApp(<DetailRoute />, { initialEntries: [`/models/${REF_ABC}`] });
    await screen.findByText('abc');
    expect(screen.getByRole('button', { name: '复制模型 ID' })).toBeInTheDocument();
  });

  it('缓存列表版本与详情版本冲突时成对重取一轮，仍冲突只提示', async () => {
    respond('ok');
    const { queryClient } = renderApp(<DetailRoute />, { initialEntries: [`/models/${REF_ABC}`] });
    await screen.findByText('abc');

    // 缓存里先放入一个已知版本不同的列表，模拟从列表进入详情后的冲突。
    await act(async () => {
      queryClient.setQueryData(['portal', 'models'], {
        data: { pricingVersion: 'v-stale', models: [] },
        requestId: 'req-stale',
      });
    });

    // 等待实际网络恢复完成，而不是在 effect 触发前断言提示尚未出现。
    await waitFor(() => expect(queryClient.getQueryData<{ data: { pricingVersion: string } }>(['portal', 'models'])?.data.pricingVersion).toBe('v1'));
    // 一轮成对重取后恢复一致：冲突提示消失，详情仍用自己的价格。
    await waitFor(() =>
      expect(screen.queryByText(/暂时无法与目录对照/)).not.toBeInTheDocument(),
    );
    expect(await screen.findByText(/75\.0 USD/)).toBeInTheDocument();
  });

  it('持续冲突时停止自动重取并展示自包含详情与一致性提示', async () => {
    let listCalls = 0;
    requestMock.mockImplementation(async (path) => {
      if (path === '/portal/api/public-config') return CONFIG;
      if (path === '/portal/api/models') {
        listCalls += 1;
        return {
          data: { pricingVersion: `v-list-${listCalls}`, models: [] },
          requestId: 'req-list',
        } as never;
      }
      return detailBody({ id: 'abc' });
    });

    const { queryClient } = renderApp(<DetailRoute />, { initialEntries: [`/models/${REF_ABC}`] });
    await screen.findByText('abc');
    // 模拟从列表进入详情：缓存里已有一个已知版本不同的列表。
    await act(async () => {
      queryClient.setQueryData(['portal', 'models'], {
        data: { pricingVersion: 'v-stale-seed', models: [] },
        requestId: 'req-stale',
      });
    });

    // 列表版本每轮都变，冲突无法自行收敛。
    await waitFor(() => expect(screen.getByText(/暂时无法与目录对照/)).toBeInTheDocument());
    const settled = listCalls;
    await act(async () => { await new Promise((resolve) => setTimeout(resolve, 800)); });
    expect(listCalls).toBe(settled);

    // 详情仍完整展示自己的响应，不混用列表价格。
    expect(screen.getByText('abc')).toBeInTheDocument();
    expect(screen.getByText(/75\.0 USD/)).toBeInTheDocument();
  });

  it('直达详情且缓存无列表时不强制请求列表', async () => {
    renderApp(<DetailRoute />, { initialEntries: [`/models/${REF_ABC}`] });
    await screen.findByText('abc');
    expect(requestMock.mock.calls.map((c) => c[0])).not.toContain('/portal/api/models');
  });

  it('英文界面使用英文文案', async () => {
    renderApp(<DetailRoute />, { locale: 'en-US', initialEntries: [`/models/${REF_ABC}`] });
    expect(await screen.findByText('Back to models')).toBeInTheDocument();
    expect(await screen.findByText('Context token limit')).toBeInTheDocument();
  });
  it.each([404, 502])('成功详情刷新返回 %s 后隐藏旧模型和价格入口', async (status) => {
    const { queryClient } = renderApp(<DetailRoute />, { initialEntries: [`/models/${REF_ABC}`] });
    await screen.findByText('abc');
    requestMock.mockImplementation(async (path) => {
      if (path === '/portal/api/public-config') return CONFIG;
      throw new PortalApiError(status, status === 404 ? 'NOT_FOUND' : 'UPSTREAM_ERROR', 'fail');
    });
    await act(async () => {
      await queryClient.refetchQueries({ queryKey: ['portal', 'models', 'detail', REF_ABC] });
    });
    expect(await screen.findByText(status === 404 ? '模型不存在或已下架。' : '模型详情加载失败')).toBeInTheDocument();
    expect(screen.queryByText('abc')).not.toBeInTheDocument();
    expect(screen.queryByText(/75\.0 USD/)).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: '查看调用示例' })).not.toBeInTheDocument();
  });

  it('空增强价格显示已验证无项目，不显示未知说明', async () => {
    requestMock.mockImplementation(async (path) => path === '/portal/api/public-config' ? CONFIG : detailBody({ enhancedPricing: [] }));
    renderApp(<DetailRoute />, { initialEntries: [`/models/${REF_ABC}`] });
    await screen.findByText('abc');
    expect(screen.getByText('暂无增强计费项目。')).toBeInTheDocument();
    expect(screen.queryByText('增强价格暂无数据，不代表该模型没有额外收费。')).not.toBeInTheDocument();
  });

  it('英文空输入与输出模态显示 None', async () => {
    requestMock.mockImplementation(async (path) => path === '/portal/api/public-config' ? CONFIG : detailBody({ inputModalities: [], outputModalities: [] }));
    renderApp(<DetailRoute />, { locale: 'en-US', initialEntries: [`/models/${REF_ABC}`] });
    await screen.findByText('abc');
    expect(screen.getByText('Input modalities').nextElementSibling).toHaveTextContent('None');
    expect(screen.getByText('Output modalities').nextElementSibling).toHaveTextContent('None');
  });

  it('持续冲突可手动成对重取并恢复一致，之后停止请求', async () => {
    let converged = false;
    requestMock.mockImplementation(async (path) => {
      if (path === '/portal/api/public-config') return CONFIG;
      if (path === '/portal/api/models') return { data: { pricingVersion: converged ? 'v1' : 'v-old', models: [] }, requestId: 'list' } as never;
      return detailBody();
    });
    const { queryClient } = renderApp(<DetailRoute />, { initialEntries: [`/models/${REF_ABC}`] });
    await screen.findByText('abc');
    await act(async () => { queryClient.setQueryData(['portal', 'models'], { data: { pricingVersion: 'v-old', models: [] }, requestId: 'seed' }); });
    const retry = await screen.findByRole('button', { name: '重试一致性校验' });
    await waitFor(() => expect(retry).toBeEnabled());
    converged = true;
    await userEvent.click(retry);
    await waitFor(() => expect(screen.queryByText(/暂时无法与目录对照/)).not.toBeInTheDocument());
    expect(queryClient.getQueryData<{ data: { pricingVersion: string } }>(['portal', 'models'])?.data.pricingVersion).toBe('v1');
  });

  it('A 恢复冲突后切到 B 仍执行 B 的一轮恢复', async () => {
    requestMock.mockImplementation(async (path) => {
      if (path === '/portal/api/public-config') return CONFIG;
      if (path === '/portal/api/models') return { data: { pricingVersion: 'v-old', models: [] }, requestId: 'list' } as never;
      return detailBody({ id: path.endsWith(REF_XYZ) ? 'xyz' : 'abc' });
    });
    function Switcher() {
      const navigate = useNavigate();
      return <><button onClick={() => navigate(`/models/${REF_XYZ}`)}>switch</button><DetailRoute /></>;
    }
    const { queryClient } = renderApp(<Switcher />, { initialEntries: [`/models/${REF_ABC}`] });
    await screen.findByText('abc');
    await act(async () => { queryClient.setQueryData(['portal', 'models'], { data: { pricingVersion: 'v-old', models: [] }, requestId: 'seed' }); });
    await waitFor(() => expect(detailPaths().filter(path => path.endsWith(REF_ABC))).toHaveLength(2));
    await userEvent.click(screen.getByRole('button', { name: 'switch' }));
    await screen.findByText('xyz');
    await waitFor(() => expect(detailPaths().filter(path => path.endsWith(REF_XYZ))).toHaveLength(2));
  });

});
