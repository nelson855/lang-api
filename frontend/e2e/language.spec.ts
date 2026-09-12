import { expect, test } from '@playwright/test';
import { mockPublicConfig } from './fixtures';

test.describe('语言流程', () => {
  test('浏览器英文偏好启动英文，切换后刷新持久且路由保持', async ({ page, context }) => {
    await context.addInitScript(() => {
      Object.defineProperty(window.navigator, 'languages', { value: ['en-US'], configurable: true });
    });
    await mockPublicConfig(page, { siteName: 'E2E Site', apiBaseUrls: [] });
    await page.goto('/models');
    await expect(page.getByRole('heading', { name: 'Models' })).toBeVisible();
    await expect(page.locator('html')).toHaveAttribute('lang', 'en-US');
    await page.getByRole('button', { name: '中文', exact: true }).click();
    await expect(page.getByRole('heading', { name: '模型广场' })).toBeVisible();
    expect(page.url()).toContain('/models');
    await page.reload();
    await expect(page.getByRole('heading', { name: '模型广场' })).toBeVisible();
    await expect(page.locator('html')).toHaveAttribute('lang', 'zh-CN');
  });
});
