import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { Route, Routes } from 'react-router';
import { portalRequest } from '../api/portalClient';
import { PublicLayout } from './PublicLayout';
import { renderApp } from '../test/renderApp';

vi.mock('../api/portalClient', () => ({
  portalRequest: vi.fn(),
}));

beforeEach(() => {
  vi.mocked(portalRequest).mockResolvedValue({
    data: { siteName: '测试站', apiBaseUrls: [] },
    requestId: 'req-layout',
  });
});

function setup(path = '/') {
  return renderApp(
    <Routes>
      <Route element={<PublicLayout />}>
        <Route index element={<h1>首页内容</h1>} />
        <Route path="models" element={<h1>模型内容</h1>} />
        <Route path="docs" element={<h1>文档内容</h1>} />
      </Route>
    </Routes>,
    { initialEntries: [path] },
  );
}

describe('PublicLayout 公开站点壳', () => {
  it('桌面导航展示主要目的地与当前选中', async () => {
    setup('/models');
    expect((await screen.findAllByText('测试站')).length).toBeGreaterThan(0);
    const header = screen.getByRole('banner');
    const modelsLink = within(header).getByRole('link', { name: '模型广场' });
    expect(modelsLink).toHaveAttribute('aria-current', 'page');
    expect(within(header).getByRole('link', { name: '首页' })).not.toHaveAttribute('aria-current');
    expect(within(header).getByRole('link', { name: '登录' })).toHaveAttribute('href', '/login');
    expect(within(header).getByRole('link', { name: '注册' })).toHaveAttribute('href', '/register');
  });

  it('页脚只含真实可用入口', async () => {
    const { container } = setup('/');
    await screen.findAllByText('测试站');
    const footer = container.querySelector('footer');
    expect(footer).not.toBeNull();
    const hrefs = [...(footer as HTMLElement).querySelectorAll('a')].map((a) =>
      a.getAttribute('href'),
    );
    expect(hrefs.length).toBeGreaterThan(0);
    for (const href of hrefs) {
      expect(href?.startsWith('/') || href?.startsWith('mailto:'), href ?? '').toBe(true);
    }
    expect(hrefs).toContain('/terms');
    expect(hrefs).toContain('/privacy');
    expect(footer?.textContent).not.toMatch(/示例条款|占位/);
  });

  it('提供语言切换器', async () => {
    setup('/');
    expect(await screen.findByRole('group', { name: '语言' })).toBeInTheDocument();
  });

  it('注册策略关闭时移除公开导航中的注册入口', async () => {
    vi.mocked(portalRequest).mockImplementation((path) => Promise.resolve(
      path === '/portal/api/auth/options'
        ? {
            data: { registrationEnabled: false, emailVerificationEnabled: false, captchaEnabled: false },
            requestId: 'req-options',
          }
        : { data: { siteName: '测试站', apiBaseUrls: [] }, requestId: 'req-layout' },
    ));
    setup('/');
    await screen.findAllByText('测试站');
    await waitFor(() => expect(screen.queryByRole('link', { name: '注册' })).toBeNull());
  });

  it('页脚提供法律地区支持与账号入口', async () => {
    vi.mocked(portalRequest).mockImplementation((path) => Promise.resolve(
      path === '/portal/api/public-config'
        ? {
            data: {
              siteName: '测试站',
              publicationMode: 'PREVIEW',
              siteUrl: '',
              supportUrl: 'mailto:support@portal.example',
              supportedRegions: ['CN'],
              enabledLocales: ['zh-CN'],
              apiBaseUrls: [],
            },
            requestId: 'req-config',
          }
        : path === '/portal/api/auth/options'
          ? {
              data: {
                registrationEnabled: true,
                registrationDisabledReason: null,
                emailVerificationEnabled: false,
                captchaEnabled: false,
              },
              requestId: 'req-options',
            }
          : { data: { siteName: '测试站', apiBaseUrls: [] }, requestId: 'req-layout' },
    ));
    const { container } = setup('/');
    await screen.findAllByText('测试站');
    const footer = container.querySelector('footer') as HTMLElement;
    const hrefs = [...footer.querySelectorAll('a')].map((a) => a.getAttribute('href'));
    expect(hrefs).toContain('/terms');
    expect(hrefs).toContain('/privacy');
    expect(hrefs).toContain('/regions');
    expect(hrefs).toContain('mailto:support@portal.example');
    expect(hrefs).toContain('/login');
    expect(hrefs).toContain('/register');
  });

  it('移动抽屉包含服务地区与法律入口', async () => {
    const { container } = setup('/');
    await screen.findAllByText('测试站');
    fireEvent.click(screen.getByRole('button', { name: '菜单' }));
    const drawer = container.querySelector('.dialog-drawer') ?? document.body;
    const links = [...drawer.querySelectorAll('a')].map((a) => a.getAttribute('href'));
    expect(links).toContain('/regions');
  });
});
