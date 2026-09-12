import { expect, test } from '@playwright/test';
import { mockPublicConfig, mockPublicConfigFailure } from './fixtures';

test.describe('桌面公开流程', () => {
  test('启动品牌来自运行时配置并可站内导航', async ({ page }) => {
    await mockPublicConfig(page, { siteName: 'E2E 站点', apiBaseUrls: [] });
    await page.goto('/');
    await expect(page.getByRole('heading', { name: '自有语言模型网关' })).toBeVisible();
    await expect(page).toHaveTitle(/E2E 站点/);
    await page.getByRole('banner').getByRole('link', { name: '模型广场' }).click();
    await expect(page.getByRole('heading', { name: '模型广场' })).toBeVisible();
    await expect(page).toHaveTitle(/模型广场/);
    await page.getByRole('banner').getByRole('link', { name: '开发文档' }).click();
    await expect(page.getByRole('heading', { name: '开发文档' })).toBeVisible();
  });

  test('空地址不展示示例域名', async ({ page }) => {
    await mockPublicConfig(page, { siteName: 'E2E 站点', apiBaseUrls: [] });
    await page.goto('/');
    await expect(page.getByRole('heading', { name: '自有语言模型网关' })).toBeVisible();
    expect(await page.content()).not.toContain('example.com');
  });

  test('配置失败可手动重试恢复', async ({ page }) => {
    await mockPublicConfigFailure(page);
    await page.goto('/');
    await expect(page.getByText('启动失败')).toBeVisible();
    await expect(page.getByText(/req-e2e-fail/)).toBeVisible();
    await mockPublicConfig(page, { siteName: 'E2E 站点', apiBaseUrls: [] });
    await page.getByRole('button', { name: '重试' }).click();
    await expect(page.getByRole('heading', { name: '自有语言模型网关' })).toBeVisible();
  });

  test('未知路径显示自有 404', async ({ page }) => {
    await mockPublicConfig(page, { siteName: 'E2E 站点', apiBaseUrls: [] });
    await page.goto('/no-such-page');
    await expect(page.getByText('页面不存在')).toBeVisible();
  });

  test('深层路由可直接刷新恢复', async ({ page }) => {
    await mockPublicConfig(page, { siteName: 'E2E 站点', apiBaseUrls: [] });
    await page.goto('/models');
    await expect(page.getByRole('heading', { name: '模型广场' })).toBeVisible();
  });

  test('匿名访问控制台跳转登录', async ({ page }) => {
    await mockPublicConfig(page, { siteName: 'E2E 站点', apiBaseUrls: [] });
    await page.goto('/dashboard');
    await expect(page).toHaveURL(/\/login\?returnTo/);
    await expect(page.getByRole('heading', { name: '登录' })).toBeVisible();
  });
});
