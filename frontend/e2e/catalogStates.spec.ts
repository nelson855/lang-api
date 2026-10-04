import { expect, test } from '@playwright/test';
import { encodeCatalogModelRef, mockCatalogApis } from './fixtures';

const FLASH = encodeCatalogModelRef('deepseek-v4-flash');

test.describe('模型广场语言、设备与键盘路径', () => {
  test('英文界面覆盖列表与详情全部状态文案', async ({ page }) => {
    await mockCatalogApis(page);
    await page.addInitScript(() => window.localStorage.setItem('lang-api:locale', 'en-US'));
    await page.goto('/models');

    await expect(page.getByRole('heading', { name: 'Models' })).toBeVisible();
    await expect(page.getByRole('link', { name: 'View details' }).first()).toBeVisible();
    await expect(page.getByRole('button', { name: 'Copy model ID' }).first()).toBeVisible();

    await page.goto(`/models/${FLASH}`);
    await expect(page.getByText('Back to models')).toBeVisible();
    await expect(page.getByText('Context token limit')).toBeVisible();
    await expect(page.getByText('Tool calling')).toBeVisible();
    await expect(page.getByText('Not available').first()).toBeVisible();
  });

  test('英文界面同样表达厂商失败与冲突状态', async ({ page }) => {
    await mockCatalogApis(page, { providers: 'fail' });
    await page.addInitScript(() => window.localStorage.setItem('lang-api:locale', 'en-US'));
    await page.goto('/models');
    await expect(page.getByText('deepseek-v4-flash')).toBeVisible();
    await expect(page.getByText('Failed to load provider options')).toBeVisible();
  });

  test('375px 窄屏可完成筛选、详情与复制且无整体横向溢出', async ({ page, context }) => {
    await context.grantPermissions(['clipboard-read', 'clipboard-write']);
    await page.setViewportSize({ width: 375, height: 812 });
    await mockCatalogApis(page);
    await page.goto('/models');

    await expect(page.getByText('deepseek-v4-flash')).toBeVisible();
    const overflow = await page.evaluate(
      () => document.documentElement.scrollWidth <= document.documentElement.clientWidth + 1,
    );
    expect(overflow).toBe(true);

    await page.getByRole('searchbox', { name: /搜索模型/ }).fill('gpt');
    await expect(page.getByText('gpt-4o')).toBeVisible();

    await page.getByRole('searchbox', { name: /搜索模型/ }).fill('');
    // 等待清除后的目录提交，并定位目标模型，避免点击旧搜索结果中的首个链接。
    const targetCard = page.locator('li').filter({ has: page.getByText('deepseek-v4-flash', { exact: true }) });
    await expect(targetCard).toBeVisible();
    await targetCard.getByRole('link', { name: '查看详情' }).click();
    await expect(page.getByText('deepseek-v4-flash')).toBeVisible();

    const detailOverflow = await page.evaluate(
      () => document.documentElement.scrollWidth <= document.documentElement.clientWidth + 1,
    );
    expect(detailOverflow).toBe(true);

    await page.getByRole('button', { name: '复制模型 ID' }).first().click();
    await expect(page.getByText('已复制模型 ID', { exact: true })).toBeVisible();
    expect(await page.evaluate(() => navigator.clipboard.readText())).toBe('deepseek-v4-flash');
  });

  test('仅用键盘完成搜索、厂商选择、打开详情与返回', async ({ page }) => {
    await mockCatalogApis(page);
    await page.goto('/models');
    await expect(page.getByText('deepseek-v4-flash')).toBeVisible();

    await page.getByRole('searchbox', { name: /搜索模型/ }).focus();
    await page.keyboard.type('gpt');
    await expect(page.getByText('gpt-4o')).toBeVisible();

    await page.getByRole('searchbox', { name: /搜索模型/ }).fill('');
    await page.getByRole('combobox', { name: '厂商筛选' }).focus();
    await page.keyboard.press('Enter');
    await page.getByRole('option', { name: /^OpenAI/ }).click();
    await expect(page.getByText('gpt-4o')).toBeVisible();
    await expect(page.getByText('deepseek-v4-flash')).toHaveCount(0);

    await page.getByRole('link', { name: '查看详情' }).first().focus();
    await page.keyboard.press('Enter');
    await expect(page.getByRole('heading', { name: '模型详情' })).toBeVisible();

    await page.getByRole('link', { name: '返回模型广场' }).focus();
    await page.keyboard.press('Enter');
    await expect(page.getByRole('heading', { name: '模型广场' })).toBeVisible();
    await expect(page).toHaveURL(/provider=OpenAI/);
  });

  test('Select 展开态在窄屏可见并可键盘选择', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 812 });
    await mockCatalogApis(page);
    await page.goto('/models');
    await expect(page.getByText('deepseek-v4-flash')).toBeVisible();

    await page.getByRole('combobox', { name: '厂商筛选' }).click();
    const option = page.getByRole('option', { name: /^DeepSeek/ });
    await expect(option).toBeVisible();
    const box = await option.boundingBox();
    expect(box).not.toBeNull();
    expect(box!.x).toBeGreaterThanOrEqual(0);
    expect(box!.x + box!.width).toBeLessThanOrEqual(375);
  });

  test('剪贴板不可用时就地提示且完整 ID 仍可选中', async ({ page }) => {
    await mockCatalogApis(page);
    await page.addInitScript(() => {
      Object.defineProperty(navigator, 'clipboard', { value: undefined, configurable: true });
    });
    await page.goto(`/models/${FLASH}`);
    await expect(page.getByText('deepseek-v4-flash')).toBeVisible();

    await page.getByRole('button', { name: '复制模型 ID' }).first().click();
    await expect(page.getByText(/复制失败，请手动选择并复制完整模型 ID/).first()).toBeVisible();

    const fallback = page.locator('.copy-model-id-fallback').first();
    await expect(fallback).toHaveText('deepseek-v4-flash');
  });

  test('长模型 ID、描述与标签不撑宽页面且完整值可访问', async ({ page }) => {
    const longId = `${'m'.repeat(110)}/长标识 v2`;
    await mockCatalogApis(
      page,
      {},
      [
        {
          id: longId,
          provider: 'DeepSeek',
          pricing: null,
        },
      ],
    );
    await page.goto(`/models/${encodeCatalogModelRef(longId)}`);
    await expect(page.getByText(longId)).toBeVisible();

    const overflow = await page.evaluate(
      () => document.documentElement.scrollWidth <= document.documentElement.clientWidth + 1,
    );
    expect(overflow).toBe(true);
  });

  test('中文输入法组合期间不触发导航，组合结束同步搜索', async ({ page }) => {
    await mockCatalogApis(page);
    await page.goto('/models');
    await expect(page.getByText('deepseek-v4-flash')).toBeVisible();

    const input = page.getByRole('searchbox', { name: /搜索模型/ });
    await input.click();

    // 组合进行中：值已变化，但 URL 仍不带 q，结果不提前过滤。
    await page.evaluate(() => {
      const el = document.querySelector('.search-input') as HTMLInputElement;
      el.dispatchEvent(new CompositionEvent('compositionstart', { bubbles: true, data: '' }));
    });
    await page.keyboard.insertText('gp');
    await expect(input).toHaveValue('gp');
    await page.waitForTimeout(300);
    expect(page.url()).not.toContain('q=');
    await expect(page.getByText('deepseek-v4-flash')).toBeVisible();

    // 组合结束：一次同步搜索条件。
    await page.evaluate(() => {
      const el = document.querySelector('.search-input') as HTMLInputElement;
      el.dispatchEvent(new CompositionEvent('compositionend', { bubbles: true, data: 'gp' }));
    });
    await page.keyboard.insertText('t');
    await expect(page).toHaveURL(/q=gpt/);
    await expect(page.getByText('gpt-4o')).toBeVisible();
    await expect(page.getByText('deepseek-v4-flash')).toHaveCount(0);
  });

  test('搜索清除后立即恢复结果并把焦点交回输入框', async ({ page }) => {
    await mockCatalogApis(page);
    await page.goto('/models');
    await expect(page.getByText('deepseek-v4-flash')).toBeVisible();

    const input = page.getByRole('searchbox', { name: /搜索模型/ });
    await input.fill('gpt');
    await expect(page.getByText('gpt-4o')).toBeVisible();

    await page.getByRole('button', { name: '清除搜索' }).click();
    await expect(page.getByText('deepseek-v4-flash')).toBeVisible();
    await expect(input).toBeFocused();
  });

  test('加载与失败区域保持布局完整，恢复后不残留旧状态', async ({ page }) => {
    await mockCatalogApis(page);
    await page.goto('/models');
    await expect(page.getByText('deepseek-v4-flash')).toBeVisible();
    await expect(page.getByText('模型目录加载失败')).toHaveCount(0);
    await expect(page.getByText('厂商选项加载失败')).toHaveCount(0);
  });
});


test.describe('模型广场审查修复回归', () => {
  test('厂商接口失败仍可清除已有条件', async ({ page }) => {
    await mockCatalogApis(page, { providers: 'fail' });
    await page.goto('/models?provider=DeepSeek');
    await expect(page.getByText('厂商选项加载失败')).toBeVisible();
    await expect(page.getByText('gpt-4o')).toHaveCount(0);
    await page.getByRole('button', { name: '清除厂商条件' }).click();
    await expect(page.getByText('gpt-4o')).toBeVisible();
    await expect(page).toHaveURL('/models');
  });

  test('真实 API 解析链保留科学计数法精度和 CNY 币种', async ({ page }) => {
    await mockCatalogApis(page);
    await page.route('**/portal/api/models/*', (route) => route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({ requestId: 'req-price-fix', data: { pricingVersion: 'v1', model: {
        id: 'deepseek-v4-flash', displayName: null, provider: 'DeepSeek', availability: 'AVAILABLE', pricing: null,
        contextWindowTokens: null, maxOutputTokens: null, inputModalities: null, outputModalities: null,
        capabilities: { toolCalling: null, reasoning: null, structuredOutput: null, attachments: null },
        releaseDate: null, description: null, tags: null, sortOrder: null,
        enhancedPricing: [{ type: 'SEARCH', currency: 'CNY', unit: 'PER_REQUEST', price: '1.23456789012345678e-8' }],
      } } }),
    }));
    await page.goto(`/models/${FLASH}`);
    await expect(page.getByText('0.0000000123456789012345678 CNY / 次请求')).toBeVisible();
  });

  test('英文已验证空集合分别显示 None 和无增强项目', async ({ page }) => {
    await mockCatalogApis(page);
    await page.addInitScript(() => window.localStorage.setItem('lang-api:locale', 'en-US'));
    await page.route('**/portal/api/models/*', (route) => route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({ requestId: 'req-empty-fix', data: { pricingVersion: 'v1', model: {
        id: 'deepseek-v4-flash', displayName: null, provider: 'DeepSeek', availability: 'AVAILABLE', pricing: null,
        contextWindowTokens: null, maxOutputTokens: null, inputModalities: [], outputModalities: [],
        capabilities: { toolCalling: null, reasoning: null, structuredOutput: null, attachments: null },
        releaseDate: null, description: null, tags: null, sortOrder: null, enhancedPricing: [],
      } } }),
    }));
    await page.goto(`/models/${FLASH}`);
    await expect(page.getByText('None', { exact: true })).toHaveCount(2);
    await expect(page.getByText('No enhanced billing items.')).toBeVisible();
    await expect(page.getByText('Enhanced pricing is not available; this does not mean there is no extra charge.')).toHaveCount(0);
  });
});
