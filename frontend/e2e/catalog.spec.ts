import { expect, test } from '@playwright/test';
import { encodeCatalogModelRef, mockCatalogApis } from './fixtures';

const FLASH = encodeCatalogModelRef('deepseek-v4-flash');
const SPECIAL = encodeCatalogModelRef('中文/模型 v1');

test.describe('模型广场闭环', () => {
  test('搜索、厂商筛选、详情、复制与文档入口串成闭环', async ({ page, context }) => {
    await context.grantPermissions(['clipboard-read', 'clipboard-write']);
    await mockCatalogApis(page);
    await page.goto('/models');

    await expect(page.getByRole('heading', { name: '模型广场' })).toBeVisible();
    await expect(page.getByText('deepseek-v4-flash')).toBeVisible();

    // 搜索：URL 可恢复且不新增目录请求。
    await page.getByRole('searchbox', { name: /搜索模型/ }).fill('gpt');
    await expect(page).toHaveURL(/q=gpt/);
    await expect(page.getByText('gpt-4o')).toBeVisible();
    await expect(page.getByText('deepseek-v4-flash')).toHaveCount(0);

    // 厂商筛选：形成历史项并与搜索条件叠加。
    await page.getByRole('searchbox', { name: /搜索模型/ }).fill('');
    await page.getByRole('combobox', { name: '厂商筛选' }).click();
    await page.getByRole('option', { name: /^DeepSeek/ }).click();
    await expect(page).toHaveURL(/provider=DeepSeek/);
    await expect(page.getByText('gpt-4o')).toHaveCount(0);
    await expect(page.getByText('deepseek-v4-flash')).toBeVisible();

    // 进入详情并携带条件。
    await page.getByRole('link', { name: '查看详情' }).first().click();
    await expect(page.getByRole('heading', { name: '模型详情' })).toBeVisible();
    await expect(page.getByText('deepseek-v4-flash')).toBeVisible();

    // 复制原始模型 ID。
    await page.getByRole('button', { name: '复制模型 ID' }).first().click();
    await expect(page.getByText('已复制模型 ID').first()).toBeVisible();
    const clipboard = await page.evaluate(() => navigator.clipboard.readText());
    expect(clipboard).toBe('deepseek-v4-flash');

    // 进入文档并沿用条件返回。
    await page.getByRole('link', { name: '查看调用示例' }).click();
    await expect(page.getByRole('heading', { name: '开发文档' })).toBeVisible();
    await page.goBack();
    await page.goBack();
    await expect(page).toHaveURL(/provider=DeepSeek/);
    await expect(page.getByText('deepseek-v4-flash')).toBeVisible();
  });

  test('带条件刷新与前进后退可恢复搜索和厂商', async ({ page }) => {
    await mockCatalogApis(page);
    await page.goto('/models?q=gpt&provider=OpenAI');
    await expect(page.getByText('gpt-4o')).toBeVisible();
    await expect(page.getByText('deepseek-v4-flash')).toHaveCount(0);

    await page.getByRole('searchbox', { name: /搜索模型/ }).fill('');
    await page.getByRole('combobox', { name: '厂商筛选' }).click();
    await page.getByRole('option', { name: /^DeepSeek/ }).click();
    await expect(page).toHaveURL(/provider=DeepSeek/);

    await page.goBack();
    await expect(page).toHaveURL(/provider=OpenAI/);
    await page.goForward();
    await expect(page).toHaveURL(/provider=DeepSeek/);
  });

  test('详情深链接可直接刷新且只请求详情', async ({ page }) => {
    await mockCatalogApis(page);
    let listCalls = 0;
    page.on('request', (request) => {
      if (new URL(request.url()).pathname === '/portal/api/models') listCalls += 1;
    });

    await page.goto(`/models/${FLASH}`);
    await page.reload();
    await expect(page.getByText('deepseek-v4-flash')).toBeVisible();
    expect(listCalls).toBe(0);
  });

  test('特殊字符模型 ID 在详情页与复制中完全一致', async ({ page, context }) => {
    await context.grantPermissions(['clipboard-read', 'clipboard-write']);
    await mockCatalogApis(page);
    await page.goto(`/models/${SPECIAL}`);

    await expect(page.getByText('中文/模型 v1')).toBeVisible();
    await page.getByRole('button', { name: '复制模型 ID' }).first().click();
    expect(await page.evaluate(() => navigator.clipboard.readText())).toBe('中文/模型 v1');
    await expect(page.getByRole('link', { name: '查看调用示例' })).toHaveAttribute(
      'href',
      `/docs?model=${encodeURIComponent('中文/模型 v1')}`,
    );
  });

  test('非法引用在请求前显示安全错误', async ({ page }) => {
    await mockCatalogApis(page);
    let detailCalls = 0;
    page.on('request', (request) => {
      if (/\/portal\/api\/models\/.+/.test(new URL(request.url()).pathname)) detailCalls += 1;
    });

    await page.goto('/models/YWJj%3D');
    await expect(page.getByText(/模型链接参数不合法/)).toBeVisible();
    await expect(page.getByRole('link', { name: '返回模型广场' })).toBeVisible();
    expect(detailCalls).toBe(0);
  });

  test('合法引用但模型不存在显示已下架', async ({ page }) => {
    await mockCatalogApis(page, { detail: 'not-found' });
    await page.goto(`/models/${FLASH}`);
    await expect(page.getByText('模型不存在或已下架。')).toBeVisible();
  });

  test('响应 ID 与引用不一致按契约错误展示', async ({ page }) => {
    await mockCatalogApis(page, { detail: 'contract-mismatch' });
    await page.goto(`/models/${FLASH}`);
    await expect(page.getByText(/模型详情与所请求的模型不一致/)).toBeVisible();
    await expect(page.getByText('someone-else')).toHaveCount(0);
  });

  test('详情失败可手动重试恢复', async ({ page }) => {
    let failNext = true;
    await mockCatalogApis(page);
    await page.route('**/portal/api/models/*', (route) =>
      failNext
        ? route.fulfill({
            status: 502,
            contentType: 'application/json',
            body: JSON.stringify({ requestId: 'req-e2e-catalog', error: { code: 'UPSTREAM_ERROR', message: '上游不可用' } }),
          })
        : route.fallback(),
    );

    await page.goto(`/models/${FLASH}`);
    await expect(page.getByText('模型详情加载失败')).toBeVisible();
    failNext = false;
    await page.getByRole('button', { name: '重试' }).click();
    await expect(page.getByText('deepseek-v4-flash')).toBeVisible();
  });

  test('列表失败与供应商失败相互独立', async ({ page }) => {
    await mockCatalogApis(page, { providers: 'fail' });
    await page.goto('/models');
    await expect(page.getByText('deepseek-v4-flash')).toBeVisible();
    await expect(page.getByText('厂商选项加载失败')).toBeVisible();

    await mockCatalogApis(page, { models: 'fail' });
    await page.goto('/models');
    await expect(page.getByText('模型目录加载失败')).toBeVisible();
  });

  test('版本冲突停止自动重取并提供手动重试', async ({ page }) => {
    let providerCalls = 0;
    await mockCatalogApis(page);
    await page.route('**/portal/api/model-providers', (route) => {
      providerCalls += 1;
      return route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify({
          requestId: 'req-e2e-catalog',
          data: { pricingVersion: 'p2-conflict', providers: [{ value: 'DeepSeek', label: 'DeepSeek', modelCount: 2 }] },
        }),
      });
    });

    await page.goto('/models');
    await expect(page.getByText(/目录版本不一致/)).toBeVisible();
    // 最多一轮成对重取，之后停止自动请求。
    await page.waitForTimeout(1200);
    const settled = providerCalls;
    await page.waitForTimeout(1200);
    expect(providerCalls).toBe(settled);

    await page.getByRole('button', { name: '重试一致性校验' }).click();
    await expect(page.getByText(/目录版本不一致/)).toBeVisible();
  });

  test('空目录与筛选无结果分别提示', async ({ page }) => {
    await mockCatalogApis(page, { models: 'empty' });
    await page.goto('/models');
    await expect(page.getByText('当前尚未配置公开模型。')).toBeVisible();

    await mockCatalogApis(page);
    await page.goto('/models?q=不存在的模型');
    await expect(page.getByText('没有符合条件的模型，换个条件试试。')).toBeVisible();
  });

  test('未知厂商保留为可清除条件', async ({ page }) => {
    await mockCatalogApis(page);
    await page.goto('/models?provider=Gone');
    await expect(page.getByText(/未知厂商：Gone/)).toBeVisible();
    await expect(page.getByText('deepseek-v4-flash')).toHaveCount(0);
    await page.getByRole('button', { name: '清除厂商条件' }).click();
    await expect(page.getByText('deepseek-v4-flash')).toBeVisible();
  });

  test('文档指定未知模型时停止生成示例并提供返回入口', async ({ page }) => {
    await mockCatalogApis(page);
    await page.goto('/docs?model=gone-model');
    await expect(page.getByText(/所选模型不可用/)).toBeVisible();
    await expect(page.getByRole('heading', { name: '非流式示例' })).toHaveCount(0);
    await page.getByRole('link', { name: '返回模型广场' }).click();
    await expect(page.getByRole('heading', { name: '模型广场' })).toBeVisible();
  });

  test('详情标题公告与公开导航保持只有模型广场入口', async ({ page }) => {
    await mockCatalogApis(page);
    await page.goto(`/models/${FLASH}`);
    await expect(page).toHaveTitle(/模型详情/);
    const nav = page.getByRole('banner');
    await expect(nav.getByRole('link', { name: '模型广场' })).toBeVisible();
    await expect(nav.getByRole('link', { name: '模型详情' })).toHaveCount(0);
  });
});
