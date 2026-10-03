import { expect, test, type Page } from '@playwright/test';
import { mockAuthenticationApi, mockPublicConfig } from './fixtures';

/** 浏览器使用模拟 API，仅验证取消后的展示与查询，不作为上游字段证据。 */
function baseItem(overrides: Record<string, unknown> = {}) {
  return {
    occurredAt: '2026-09-10T10:00:00Z',
    requestId: 'req-1',
    keyName: 'probe-key-01',
    model: 'gpt-test',
    result: 'SUCCESS',
    inputTokens: 10,
    outputTokens: 20,
    durationMs: 3000,
    stream: true,
    quota: '500000',
    amount: '1.0',
    currency: 'USD',
    ...overrides,
  };
}

async function mockRequestLogs(page: Page, items: unknown[], total = items.length) {
  await page.route('**/portal/api/request-logs*', (route) =>
    route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({
        requestId: 'req-e2e-logs',
        data: { items, page: Number(new URL(route.request().url()).searchParams.get('page') ?? 1), pageSize: 20, total },
      }),
    }),
  );
}

async function loginToRequestLogs(page: Page, query = '', english = false) {
  await mockPublicConfig(page, { siteName: 'E2E 站点', apiBaseUrls: [] });
  await mockAuthenticationApi(page, { profileStatus: 401 });
  // 既有 PublicOnlyRoute 与登录成功回调存在竞态，嵌套 returnTo 不可靠：
  // 先完成登录，再直接进入日志页（增强展示矩阵不依赖 returnTo）。
  await page.goto('/login');
  const username = page.locator('input[name="username"]');
  await username.waitFor({ state: 'visible' });
  await username.fill('ordinary');
  await page.locator('input[name="password"]').fill('correct-horse');
  // 登录页随浏览器语言切换，按钮文案中英文不同。
  await page.locator('form button[type="submit"]').click();
  await expect(page.getByRole('heading', { name: english ? 'Dashboard' : '控制台' })).toBeVisible();
  await page.unroute('**/portal/api/profile');
  await page.route('**/portal/api/profile', (route) =>
    route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({
        requestId: 'req-profile',
        data: { id: 7, username: 'ordinary', displayName: 'Ordinary User', email: 'ordinary@example.test' },
      }),
    }),
  );
  await page.goto(`/dashboard/request-logs${query}`);
  await expect(page.getByRole('heading', { name: english ? 'Request logs' : '请求日志' })).toBeVisible();
}

test.describe('取消协议和 TTFT 后的日志闭环', () => {
  test('非空兼容字段不展示，保留基础列', async ({ page }) => {
    await mockRequestLogs(page, [baseItem({ protocol: 'OPENAI', firstTokenLatencyMs: 820 })]);
    await loginToRequestLogs(page);
    const table = page.getByRole('table');
    await expect(table.getByText('3000ms')).toBeVisible();
    await expect(table.getByText('10/20')).toBeVisible();
    await expect(table.getByText('流式', { exact: true })).toBeVisible();
    await expect(page.getByText('协议', { exact: true })).toHaveCount(0);
    await expect(page.getByText('首 Token 延迟', { exact: true })).toHaveCount(0);
    await expect(page.getByText('OPENAI', { exact: true })).toHaveCount(0);
    await expect(page.getByText('820ms', { exact: true })).toHaveCount(0);
    await expect(page.getByRole('button', { name: '说明', exact: true })).toHaveCount(0);
  });

  test('旧响应缺少兼容字段仍能展示', async ({ page }) => {
    await mockRequestLogs(page, [baseItem()]);
    await loginToRequestLogs(page);
    await expect(page.getByRole('table').getByText('gpt-test')).toBeVisible();
    await expect(page.getByRole('table').getByText('暂无数据')).toHaveCount(0);
  });

  test('精确费用与键盘说明保留', async ({ page }) => {
    await mockRequestLogs(page, [baseItem({ amount: '0.000001', quota: '9007199254740993' })]);
    await loginToRequestLogs(page);
    const table = page.getByRole('table');
    await expect(table.getByText('0.000001 USD')).toBeVisible();
    await expect(table.getByText(/9007199254740993/)).toBeVisible();
    const toggle = page.getByRole('button', { name: '费用说明' });
    await toggle.focus();await page.keyboard.press('Enter');
    await expect(toggle).toHaveAttribute('aria-expanded', 'true');
    await expect(page.getByText(/列表不是额外收费项目或正式账单/)).toBeVisible();
  });

  test('分页和 Dashboard 范围仍从 URL 恢复', async ({ page }) => {
    await mockRequestLogs(page, [baseItem()], 57);
    await loginToRequestLogs(page, '?result=SUCCESS&page=1&keyName=probe-key-01&model=gpt-test&startTime=2026-09-10T00%3A00%3A00Z&endTime=2026-09-11T00%3A00%3A00Z');
    await expect(page.getByText(/Dashboard 所选范围/)).toBeVisible();
    await page.getByRole('button', { name: '下一页' }).click();
    await expect(page.getByRole('navigation', { name: '第 2 / 3 页' })).toBeVisible();
    await page.reload();
    await expect(page.getByRole('navigation', { name: '第 2 / 3 页' })).toBeVisible();
    await expect(page.getByText(/Dashboard 所选范围/)).toBeVisible();
  });
});

test.describe('375px 保留基础字段与费用说明', () => {
  test.use({ viewport: { width: 375, height: 812 } });
  test('中文卡片移除两个指标，保留总耗时且无横向溢出', async ({ page }) => {
    await mockRequestLogs(page, [baseItem({ protocol: 'GEMINI', firstTokenLatencyMs: 45 })]);
    await loginToRequestLogs(page);
    const card = page.locator('.request-logs-card');
    await expect(card.getByText('耗时: 3000ms')).toBeVisible();
    await expect(card.getByText('流式状态: 流式')).toBeVisible();
    await expect(card.getByText(/费用: 1\.0 USD/)).toBeVisible();
    await expect(card.getByText(/额度：500000 quota/)).toBeVisible();
    await expect(card.getByText(/协议|首 Token 延迟|GEMINI|45ms/)).toHaveCount(0);
    const toggle = page.getByRole('button', { name: '费用说明' });
    await toggle.focus();await page.keyboard.press('Enter');
    await expect(toggle).toHaveAttribute('aria-expanded', 'true');
    await page.keyboard.press('Enter');await expect(toggle).toHaveAttribute('aria-expanded', 'false');
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  });
  test('英文卡片和费用说明保持可访问', async ({ page }) => {
    await page.addInitScript(() => localStorage.setItem('lang-api:locale', 'en-US'));
    await mockRequestLogs(page, [baseItem({ protocol: 'OPENAI', firstTokenLatencyMs: 820 })]);
    await loginToRequestLogs(page, '', true);
    const card = page.locator('.request-logs-card');
    await expect(card.getByText('Duration: 3000ms')).toBeVisible();
    await expect(card.getByText(/Protocol|Time to first token|OPENAI|820ms/)).toHaveCount(0);
    const toggle = page.getByRole('button', { name: 'Cost explanation' });
    await toggle.focus();await page.keyboard.press('Enter');
    await expect(toggle).toHaveAttribute('aria-expanded', 'true');
  });
});
