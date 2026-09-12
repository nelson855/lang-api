import type { Page } from '@playwright/test';

export interface MockPublicConfig {
  siteName: string;
  apiBaseUrls: Array<{ protocol: string; url: string }>;
}

export async function mockPublicConfig(page: Page, data: MockPublicConfig, status = 200) {
  await page.route('**/portal/api/public-config', (route) =>
    route.fulfill({
      status,
      contentType: 'application/json',
      body: JSON.stringify({ requestId: 'req-e2e', data }),
    }),
  );
}

export async function mockPublicConfigFailure(page: Page, status = 500) {
  await page.route('**/portal/api/public-config', (route) =>
    route.fulfill({
      status,
      contentType: 'application/json',
      body: JSON.stringify({ requestId: 'req-e2e-fail', error: { code: 'E', message: '失败' } }),
    }),
  );
}
