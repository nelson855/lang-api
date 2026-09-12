import { expect, test } from '@playwright/test';
import { mockPublicConfig } from './fixtures';

test.describe('320px 移动流程', () => {
  test.beforeEach(async ({ page }) => {
    await mockPublicConfig(page, { siteName: 'E2E 站点', apiBaseUrls: [] });
    await page.goto('/');
    await expect(page.getByRole('heading', { name: '自有语言模型网关' })).toBeVisible();
  });

  test('抽屉导航可用且页面无横向滚动', async ({ page }) => {
    await page.getByRole('button', { name: '菜单' }).click();
    const dialog = page.getByRole('dialog');
    await expect(dialog).toBeVisible();
    await dialog.getByRole('link', { name: '模型广场' }).click();
    await expect(page.getByRole('heading', { name: '模型广场' })).toBeVisible();
    const overflow = await page.evaluate(
      () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
    );
    expect(overflow).toBeLessThanOrEqual(1);
  });

  test('Escape 关闭抽屉', async ({ page }) => {
    await page.getByRole('button', { name: '菜单' }).click();
    await expect(page.getByRole('dialog')).toBeVisible();
    await page.keyboard.press('Escape');
    await expect(page.getByRole('dialog')).toHaveCount(0);
  });
});
