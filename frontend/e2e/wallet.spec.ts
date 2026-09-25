import { expect, test, type Page } from '@playwright/test';
import { mockAuthenticationApi, mockPublicConfig, mockWalletApis } from './fixtures';

const RANGE_7D_QUERY =
  'range=7d&startTime=2026-09-01T00%3A00%3A00Z&endTime=2026-09-08T00%3A00%3A00Z&timezone=UTC&granularity=HOUR';
const RANGE_30D_QUERY =
  'range=30d&startTime=2026-08-11T08%3A00%3A00Z&endTime=2026-09-10T08%3A00%3A00Z&timezone=Asia%2FShanghai&granularity=DAY';

async function loginToWallet(page: Page, query = '') {
  await mockPublicConfig(page, { siteName: 'E2E 站点', apiBaseUrls: [] });
  await mockAuthenticationApi(page, { profileStatus: 401 });
  // 既有 PublicOnlyRoute 与登录成功回调存在竞态,嵌套 returnTo 不可靠:
  // 先完成登录,再直接进入钱包页(钱包矩阵不依赖 returnTo)。
  await page.goto('/login');
  await page.getByRole('textbox', { name: '用户名' }).fill('ordinary');
  await page.getByLabel('密码').fill('correct-horse');
  await page.getByRole('button', { name: '登录' }).click();
  await expect(page.getByRole('heading', { name: '控制台' })).toBeVisible();
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
  await page.goto(`/dashboard/wallet${query}`);
  await expect(page.getByRole('heading', { name: '钱包' })).toBeVisible();
}

function accountRequests(page: Page) {
  const seen: string[] = [];
  page.on('request', (request) => {
    const url = request.url();
    if (url.includes('/portal/api/account/')) {
      seen.push(url);
    }
  });
  return seen;
}

function paramsOf(url: string, path: string) {
  const found = url.includes(path) ? url : '';
  return new URLSearchParams(found.split('?')[1] ?? '');
}

test.describe('钱包消费分析与统一流水', () => {
  test('五区域渲染且汇总与流水共享同一范围', async ({ page }) => {
    await mockWalletApis(page);
    const seen = accountRequests(page);
    await loginToWallet(page);

    await expect(page.getByText('1.0 USD', { exact: false })).toBeVisible();
    const summaryRegion = page.getByLabel(/范围内消费/);
    await expect(summaryRegion.getByText('60', { exact: true }).first()).toBeVisible();
    await expect(page.getByText('gpt-4o').first()).toBeVisible();
    await expect(page.getByText(/充值未开放/)).toBeVisible();
    await expect(page.getByText(/暂无充值记录/)).toBeVisible();

    const summary = seen.find((url) => url.includes('/consumption-summary'));
    const tx = seen.find((url) => url.includes('/account/transactions'));
    expect(summary).toBeTruthy();
    expect(tx).toBeTruthy();
    expect(paramsOf(tx!, '/account/transactions').get('startTime')).toBe(
      paramsOf(summary!, '/consumption-summary').get('startTime'),
    );
  });

  test('刷新恢复同一 URL 状态且边界不漂移', async ({ page }) => {
    await mockWalletApis(page);
    const seen = accountRequests(page);
    await loginToWallet(page, `?${RANGE_7D_QUERY}&type=CONSUMPTION&page=1`);

    await expect(page.getByText('gpt-4o').first()).toBeVisible();
    const firstTx = seen.find((url) => url.includes('/account/transactions'))!;
    expect(paramsOf(firstTx, '/account/transactions').get('type')).toBe('CONSUMPTION');

    seen.length = 0;
    await page.reload();
    await expect(page.getByText('gpt-4o').first()).toBeVisible();
    const secondTx = seen.find((url) => url.includes('/account/transactions'))!;
    expect(paramsOf(secondTx, '/account/transactions').get('startTime')).toBe(
      paramsOf(firstTx, '/account/transactions').get('startTime'),
    );
  });

  test('类型切换后前进后退恢复筛选', async ({ page }) => {
    await mockWalletApis(page);
    await loginToWallet(page, `?${RANGE_7D_QUERY}&type=ALL&page=1`);

    const typeBox = page.getByRole('combobox', { name: '交易类型' });
    await expect(typeBox).toContainText('全部');
    await typeBox.click();
    await page.getByRole('option', { name: '消费' }).click();
    await expect(typeBox).toContainText('消费');
    expect(page.url()).toContain('type=CONSUMPTION');

    await page.goBack();
    await expect(typeBox).toContainText('全部');
    await page.goForward();
    await expect(typeBox).toContainText('消费');
  });

  test('范围切换发起新请求,30D 可见被禁且不发聚合请求', async ({ page }) => {
    await mockWalletApis(page);
    const seen = accountRequests(page);
    await loginToWallet(page, `?${RANGE_7D_QUERY}&type=ALL&page=1`);
    await expect(page.getByText('gpt-4o').first()).toBeVisible();

    const rangeBox = page.getByRole('combobox', { name: '时间范围' });
    await rangeBox.click();
    const option30d = page.getByRole('option', { name: '近 30 天' });
    await expect(option30d).toBeDisabled();
    await page.keyboard.press('Escape');

    const before = seen.filter((url) => url.includes('/consumption-summary')).length;
    seen.length = 0;
    await page.goto(`/dashboard/wallet?${RANGE_30D_QUERY}&type=ALL&page=1`);
    await expect(page.getByText(/30 天范围暂不可用/).first()).toBeVisible();
    await expect.poll(() => seen.filter((url) => url.includes('/consumption-summary')).length).toBe(0);
    expect(seen.filter((url) => url.includes('/account/transactions')).length).toBe(0);
    expect(before).toBeGreaterThan(0);
  });

  test('真实空显示空状态而非不可用面板', async ({ page }) => {
    await mockWalletApis(page, { transactions: 'empty' });
    await loginToWallet(page, `?${RANGE_7D_QUERY}&type=CONSUMPTION&page=1`);

    await expect(page.getByText(/暂无流水/)).toBeVisible();
    const ledger = page.getByLabel(/统一流水/);
    await expect(ledger.getByText(/尚未验证|来源不可用/)).toHaveCount(0);
  });

  test('部分覆盖持续可见,充值退款来源分别说明', async ({ page }) => {
    await mockWalletApis(page, { summary: 'partial' });
    await loginToWallet(page);

    await expect(page.getByText(/缺少稳定引用/).first()).toBeVisible();
    const summaryRegion = page.getByLabel(/范围内消费/);
    await expect(summaryRegion.getByText('60', { exact: true }).first()).toBeVisible();
    await expect(page.getByText(/充值流水基线尚未验证/).first()).toBeVisible();
  });

  test('TOPUP 不可用显示阻塞说明,旧充值记录分区展示不混入', async ({ page }) => {
    await mockWalletApis(page, { transactions: 'topupBlocked', topups: 'ok' });
    await loginToWallet(page, `?${RANGE_7D_QUERY}&type=TOPUP&page=1`);

    const ledger = page.getByLabel(/统一流水/);
    await expect(ledger.getByText(/充值流水基线尚未验证/)).toBeVisible();
    await expect(ledger.getByText('ORD-1')).toHaveCount(0);
    await expect(page.getByLabel(/充值记录/).getByText('ORD-1').first()).toBeVisible();
  });

  test('汇总失败可重试且边界不变,充值关闭无写控件', async ({ page }) => {
    await mockWalletApis(page, { summary: 'fail' });
    const seen = accountRequests(page);
    await loginToWallet(page);

    const summary = page.getByLabel(/范围内消费/);
    await expect(summary.getByRole('alert')).toBeVisible();
    const firstStart = paramsOf(
      seen.find((url) => url.includes('/consumption-summary'))!,
      '/consumption-summary',
    ).get('startTime');
    await summary.getByRole('button', { name: '重试' }).click();
    await expect
      .poll(() => seen.filter((url) => url.includes('/consumption-summary')).length)
      .toBe(2);
    const retryStart = paramsOf(
      seen.filter((url) => url.includes('/consumption-summary')).at(-1)!,
      '/consumption-summary',
    ).get('startTime');
    expect(retryStart).toBe(firstStart);

    await expect(page.getByText(/充值未开放/)).toBeVisible();
    expect(await page.getByRole('textbox').count()).toBe(0);
    expect(await page.getByRole('button', { name: /充值|支付|退款|提现/ }).count()).toBe(0);
  });

  test('会话失效不在聚合区域显示本地错误', async ({ page }) => {
    await mockWalletApis(page, { summary: 'unauth', transactions: 'unauth' });
    await loginToWallet(page);

    await expect(page.getByText('1.0 USD', { exact: false })).toBeVisible();
    const summary = page.getByLabel(/范围内消费/);
    await expect(summary.getByRole('alert')).toHaveCount(0);
  });

  test('英文环境展示对应区域标题', async ({ page, context }) => {
    await context.addInitScript(() => {
      Object.defineProperty(window.navigator, 'languages', { value: ['en-US'], configurable: true });
    });
    await mockWalletApis(page);
    await mockPublicConfig(page, { siteName: 'E2E Site', apiBaseUrls: [] });
    await mockAuthenticationApi(page, { profileStatus: 401 });
    await page.goto('/login');
    await page.getByRole('textbox', { name: 'Username' }).fill('ordinary');
    await page.getByLabel('Password').fill('correct-horse');
    await page.getByRole('button', { name: 'Sign in' }).click();
    await page.waitForURL('**/dashboard');
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
    await page.goto('/dashboard/wallet');
    await expect(page.getByRole('heading', { name: 'Wallet' })).toBeVisible();
    await expect(page.getByText('Consumption in range')).toBeVisible();
    await expect(page.getByText('Unified ledger')).toBeVisible();
  });

  test('慢速网络先显示加载态再呈现数据', async ({ page }) => {
    await mockWalletApis(page);
    await page.unroute('**/portal/api/account/transactions*');
    await page.route('**/portal/api/account/transactions*', (route) =>
      setTimeout(() => {
        void route.fulfill({
          contentType: 'application/json',
          body: JSON.stringify({
            requestId: 'req-e2e',
            data: {
              baselineVersion: 'p2-2026-09-22-a',
              range: {
                start: '2026-09-01T00:00:00Z',
                end: '2026-09-08T00:00:00Z',
                timezone: 'UTC',
                granularity: 'HOUR',
              },
              type: null,
              page: 1,
              pageSize: 20,
              total: 21,
              availability: 'PARTIAL',
              reasonCode: null,
              coverage: [
                { type: 'TOPUP', availability: 'UNAVAILABLE', reasonCode: 'BASELINE_NOT_VERIFIED' },
                { type: 'CONSUMPTION', availability: 'AVAILABLE', reasonCode: null },
                { type: 'REFUND', availability: 'UNAVAILABLE', reasonCode: 'SOURCE_NOT_AVAILABLE' },
              ],
              items: [
                {
                  transactionId: 'CONSUMPTION_abc123',
                  occurredAt: '2026-09-01T12:00:00Z',
                  type: 'CONSUMPTION',
                  direction: 'DEBIT',
                  amount: '60',
                  unit: 'QUOTA',
                  currency: null,
                  status: 'SUCCEEDED',
                  remark: 'gpt-4o',
                  referenceId: 'req-abc',
                },
              ],
            },
          }),
        });
      }, 800),
    );
    await loginToWallet(page);

    const ledger = page.getByLabel(/统一流水/);
    await expect(ledger.getByRole('status')).toBeVisible();
    await expect(ledger.getByText('gpt-4o').first()).toBeVisible();
  });

  test('200% 缩放无横向溢出且等价内容可达', async ({ page }) => {    await mockWalletApis(page);
    await loginToWallet(page);
    await expect(page.getByText('gpt-4o').first()).toBeVisible();

    await page.evaluate(() => {
      document.body.style.zoom = '200%';
    });
    const overflow = await page.evaluate(
      () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
    );
    expect(overflow).toBeLessThanOrEqual(1);
    await expect(page.getByText('gpt-4o').first()).toBeVisible();
  });

  test.describe('窄屏等价卡片', () => {
    test.use({ viewport: { width: 390, height: 844 } });

    test('窄屏显示卡片且无横向滚动', async ({ page }) => {
      await mockWalletApis(page);
      await loginToWallet(page);

      const cards = page.locator('.wallet-cards');
      await expect(cards.first()).toBeVisible();
      await expect(cards.first().getByText('gpt-4o')).toBeVisible();
      const overflow = await page.evaluate(
        () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
      );
      expect(overflow).toBeLessThanOrEqual(1);
    });
  });
});
