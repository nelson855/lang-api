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

export async function mockAuthenticationApi(
  page: Page,
  options: { registrationEnabled?: boolean; profileStatus?: number; logoutStatus?: number } = {},
) {
  const profile = { id: 7, username: 'ordinary', displayName: 'Ordinary User', email: 'ordinary@example.test' };
  const registrationEnabled = options.registrationEnabled ?? true;
  await page.route('**/portal/api/auth/options', (route) => route.fulfill({
    contentType: 'application/json',
    body: JSON.stringify({ requestId: 'req-options', data: { registrationEnabled, emailVerificationEnabled: false, captchaEnabled: false } }),
  }));
  await page.route('**/portal/api/auth/csrf', (route) => route.fulfill({
    contentType: 'application/json',
    body: JSON.stringify({ requestId: 'req-csrf', data: { token: 'csrf-token' } }),
  }));
  await page.route('**/portal/api/auth/login', (route) => route.fulfill({
    contentType: 'application/json',
    body: JSON.stringify({ requestId: 'req-login', data: profile }),
  }));
  await page.route('**/portal/api/auth/register', (route) => route.fulfill({
    contentType: 'application/json', body: JSON.stringify({ requestId: 'req-register', data: null }),
  }));
  await page.route('**/portal/api/auth/refresh', (route) => route.fulfill({
    contentType: 'application/json', body: JSON.stringify({ requestId: 'req-refresh', data: profile }),
  }));
  await page.route('**/portal/api/profile', (route) => {
    const status = options.profileStatus ?? 200;
    route.fulfill({
      status,
      contentType: 'application/json',
      body: JSON.stringify(status === 200
        ? { requestId: 'req-profile', data: profile }
        : { requestId: 'req-profile', error: { code: 'UNAUTHENTICATED', message: 'expired' } }),
    });
  });
  await page.route('**/portal/api/auth/logout', (route) => {
    const status = options.logoutStatus ?? 200;
    route.fulfill({
      status,
      contentType: 'application/json',
      body: JSON.stringify(status === 200
        ? { requestId: 'req-logout', data: null }
        : { requestId: 'req-logout', error: { code: 'UPSTREAM_UNAVAILABLE', message: 'unavailable' } }),
    });
  });
}
