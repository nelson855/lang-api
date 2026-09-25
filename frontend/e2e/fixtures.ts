import type { Page } from '@playwright/test';

export interface MockPublicConfig {
  siteName: string;
  apiBaseUrls: Array<{ protocol: string; url: string }>;
  publicationMode?: 'PREVIEW' | 'PUBLIC';
  siteUrl?: string;
  supportUrl?: string;
  supportedRegions?: string[];
  enabledLocales?: Array<'zh-CN' | 'en-US'>;
}

export async function mockPublicConfig(page: Page, data: MockPublicConfig, status = 200) {
  await page.route('**/portal/api/public-config', (route) =>
    route.fulfill({
      status,
      contentType: 'application/json',
      body: JSON.stringify({
        requestId: 'req-e2e',
        data: {
          publicationMode: 'PREVIEW',
          siteUrl: '',
          supportUrl: 'mailto:dev@example.test',
          supportedRegions: ['CN'],
          enabledLocales: ['zh-CN', 'en-US'],
          ...data,
        },
      }),
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

export interface WalletApiModes {
  balanceStatus?: number;
  summary?: 'ok' | 'zero' | 'partial' | 'fail' | 'unauth';
  transactions?: 'ok' | 'empty' | 'topupBlocked' | 'fail' | 'unauth';
  optionsStatus?: number;
  topups?: 'ok' | 'empty';
}

const WALLET_RANGE = {
  start: '2026-09-01T00:00:00Z',
  end: '2026-09-08T00:00:00Z',
  timezone: 'UTC',
  granularity: 'HOUR',
};

function walletSummaryBody(mode: NonNullable<WalletApiModes['summary']>) {
  const base = {
    baselineVersion: 'p2-2026-09-22-a',
    range: WALLET_RANGE,
    recordCount: { value: '3', unit: 'records', availability: 'AVAILABLE', reasonCode: null },
    quotaTotal: { value: '60', unit: 'quota', availability: 'AVAILABLE', reasonCode: null },
    moneyTotal: {
      value: null,
      currency: null,
      availability: 'UNAVAILABLE',
      reasonCode: 'CURRENCY_CONVERSION_NOT_VERIFIED',
    },
    coverage: { type: 'CONSUMPTION', availability: 'AVAILABLE', reasonCode: null },
  };
  if (mode === 'zero') {
    return {
      ...base,
      recordCount: { value: '0', unit: 'records', availability: 'AVAILABLE', reasonCode: null },
      quotaTotal: { value: '0', unit: 'quota', availability: 'AVAILABLE', reasonCode: null },
    };
  }
  if (mode === 'partial') {
    return {
      ...base,
      coverage: {
        type: 'CONSUMPTION',
        availability: 'PARTIAL',
        reasonCode: 'MISSING_STABLE_REFERENCE',
      },
    };
  }
  return base;
}

function walletTransactionsBody(mode: NonNullable<WalletApiModes['transactions']>) {
  const item = {
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
  };
  const base = {
    baselineVersion: 'p2-2026-09-22-a',
    range: WALLET_RANGE,
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
    items: [item],
  };
  if (mode === 'empty') {
    return {
      ...base,
      type: 'CONSUMPTION',
      items: [],
      total: 0,
      availability: 'AVAILABLE',
      coverage: [{ type: 'CONSUMPTION', availability: 'AVAILABLE', reasonCode: null }],
    };
  }
  if (mode === 'topupBlocked') {
    return {
      ...base,
      type: 'TOPUP',
      items: [],
      total: 0,
      availability: 'UNAVAILABLE',
      reasonCode: 'BASELINE_NOT_VERIFIED',
      coverage: [{ type: 'TOPUP', availability: 'UNAVAILABLE', reasonCode: 'BASELINE_NOT_VERIFIED' }],
    };
  }
  return base;
}

function errorBody(code: string) {
  return { requestId: 'req-e2e-wallet', error: { code, message: code } };
}

export async function mockWalletApis(page: Page, modes: WalletApiModes = {}) {
  const {
    balanceStatus = 200,
    summary = 'ok',
    transactions = 'ok',
    optionsStatus = 200,
    topups = 'empty',
  } = modes;
  await page.route('**/portal/api/account/balance', (route) =>
    route.fulfill({
      status: balanceStatus,
      contentType: 'application/json',
      body: JSON.stringify(
        balanceStatus === 200
          ? { requestId: 'req-e2e', data: { quota: '500000', amount: '1.0', currency: 'USD' } }
          : errorBody('UPSTREAM_ERROR'),
      ),
    }),
  );
  await page.route('**/portal/api/account/consumption-summary*', (route) => {
    if (summary === 'fail') {
      return route.fulfill({ status: 502, contentType: 'application/json', body: JSON.stringify(errorBody('UPSTREAM_ERROR')) });
    }
    if (summary === 'unauth') {
      return route.fulfill({ status: 401, contentType: 'application/json', body: JSON.stringify(errorBody('UNAUTHENTICATED')) });
    }
    return route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({ requestId: 'req-e2e', data: walletSummaryBody(summary) }),
    });
  });
  await page.route('**/portal/api/account/transactions*', (route) => {
    if (transactions === 'fail') {
      return route.fulfill({ status: 502, contentType: 'application/json', body: JSON.stringify(errorBody('UPSTREAM_ERROR')) });
    }
    if (transactions === 'unauth') {
      return route.fulfill({ status: 401, contentType: 'application/json', body: JSON.stringify(errorBody('UNAUTHENTICATED')) });
    }
    return route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({ requestId: 'req-e2e', data: walletTransactionsBody(transactions) }),
    });
  });
  await page.route('**/portal/api/account/topup-options', (route) =>
    route.fulfill({
      status: optionsStatus,
      contentType: 'application/json',
      body: JSON.stringify(
        optionsStatus === 200
          ? {
              requestId: 'req-e2e',
              data: { enabled: false, methods: [], currency: 'USD', reason: 'NOT_CONFIGURED' },
            }
          : errorBody('UPSTREAM_ERROR'),
      ),
    }),
  );
  await page.route('**/portal/api/account/topups*', (route) =>
    route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({
        requestId: 'req-e2e',
        data:
          topups === 'ok'
            ? {
                items: [
                  {
                    orderId: 'ORD-1',
                    requestedAmount: '10.5',
                    currency: 'USD',
                    paymentMethod: 'ALIPAY',
                    status: 'SUCCEEDED',
                    createdAt: '2023-11-14T22:13:20Z',
                    completedAt: '2023-11-14T22:15:00Z',
                  },
                ],
                page: 1,
                pageSize: 20,
                total: 1,
              }
            : { items: [], page: 1, pageSize: 20, total: 0 },
      }),
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
    body: JSON.stringify({
      requestId: 'req-options',
      data: {
        registrationEnabled,
        registrationDisabledReason: registrationEnabled ? null : 'ADMIN_DISABLED',
        emailVerificationEnabled: false,
        captchaEnabled: false,
      },
    }),
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
