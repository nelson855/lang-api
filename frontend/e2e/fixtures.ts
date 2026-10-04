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

export interface CatalogModelSeed {
  id: string;
  displayName?: string | null;
  provider?: string | null;
  pricing?: Record<string, unknown> | null;
}

export interface CatalogApiModes {
  models?: 'ok' | 'empty' | 'fail';
  providers?: 'ok' | 'empty' | 'fail' | 'version-conflict';
  detail?: 'ok' | 'not-found' | 'fail' | 'contract-mismatch';
  /** 列表与供应商使用的 pricingVersion，用于制造版本冲突。 */
  modelsVersion?: string | null;
  providersVersion?: string | null;
}

const CATALOG_TOKEN_PRICING = {
  mode: 'TOKEN',
  currency: 'USD',
  unit: 'PER_MILLION_TOKENS',
  input: '75.0',
  output: '150.0',
  request: null,
};

const CATALOG_SEEDS: CatalogModelSeed[] = [
  { id: 'deepseek-v4-flash', provider: 'DeepSeek', pricing: CATALOG_TOKEN_PRICING },
  { id: 'gpt-4o', provider: 'OpenAI', pricing: null },
  { id: '中文/模型 v1', provider: 'DeepSeek', pricing: CATALOG_TOKEN_PRICING },
];

function catalogListModel(seed: CatalogModelSeed) {
  return {
    id: seed.id,
    displayName: seed.displayName ?? null,
    provider: seed.provider ?? null,
    availability: 'AVAILABLE',
    pricing: seed.pricing ?? null,
  };
}

function catalogDetailModel(seed: CatalogModelSeed) {
  return {
    ...catalogListModel(seed),
    contextWindowTokens: null,
    maxOutputTokens: null,
    inputModalities: null,
    outputModalities: null,
    capabilities: { toolCalling: null, reasoning: null, structuredOutput: null, attachments: null },
    releaseDate: null,
    description: null,
    tags: null,
    sortOrder: null,
    enhancedPricing: null,
  };
}

/** 与前端 modelRef 规则一致的 UTF-8 网址安全 Base64 无填充编码。 */
export function encodeCatalogModelRef(id: string): string {
  return Buffer.from(id, 'utf8').toString('base64url');
}

function catalogProviderOptions(seeds: CatalogModelSeed[]) {
  const counts = new Map<string, number>();
  for (const seed of seeds) {
    if (seed.provider) {
      counts.set(seed.provider, (counts.get(seed.provider) ?? 0) + 1);
    }
  }
  return [...counts.entries()]
    .sort((a, b) => a[0].localeCompare(b[0]))
    .map(([value, modelCount]) => ({ value, label: value, modelCount }));
}

export async function mockCatalogApis(
  page: Page,
  modes: CatalogApiModes = {},
  seeds: CatalogModelSeed[] = CATALOG_SEEDS,
) {
  const {
    models = 'ok',
    providers = 'ok',
    detail = 'ok',
    modelsVersion = 'p2-2026-09-22-a',
    providersVersion = modelsVersion,
  } = modes;

  await mockPublicConfig(page, {
    siteName: 'E2E 站点',
    apiBaseUrls: [{ protocol: 'OPENAI', url: 'https://api.example.test/v1' }],
  });

  await page.route('**/portal/api/models', (route) => {
    if (models === 'fail') {
      return route.fulfill({
        status: 502,
        contentType: 'application/json',
        body: JSON.stringify({ requestId: 'req-e2e-catalog', error: { code: 'UPSTREAM_ERROR', message: '上游不可用' } }),
      });
    }
    return route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({
        requestId: 'req-e2e-catalog',
        data: {
          pricingVersion: modelsVersion,
          models: models === 'empty' ? [] : seeds.map(catalogListModel),
        },
      }),
    });
  });

  await page.route('**/portal/api/model-providers', (route) => {
    if (providers === 'fail') {
      return route.fulfill({
        status: 502,
        contentType: 'application/json',
        body: JSON.stringify({ requestId: 'req-e2e-catalog', error: { code: 'UPSTREAM_ERROR', message: '上游不可用' } }),
      });
    }
    return route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({
        requestId: 'req-e2e-catalog',
        data: {
          pricingVersion: providers === 'version-conflict' ? 'p2-conflict' : providersVersion,
          providers: providers === 'empty' ? [] : catalogProviderOptions(seeds),
        },
      }),
    });
  });

  // 详情路由在列表路径之后注册，避免被 '**/portal/api/models' 吞掉。
  await page.route('**/portal/api/models/*', (route) => {
    if (detail === 'fail') {
      return route.fulfill({
        status: 502,
        contentType: 'application/json',
        body: JSON.stringify({ requestId: 'req-e2e-catalog', error: { code: 'UPSTREAM_ERROR', message: '上游不可用' } }),
      });
    }
    const ref = decodeURIComponent(new URL(route.request().url()).pathname.split('/').pop() ?? '');
    const seed = seeds.find((item) => encodeCatalogModelRef(item.id) === ref);
    if (!seed || detail === 'not-found') {
      return route.fulfill({
        status: 404,
        contentType: 'application/json',
        body: JSON.stringify({ requestId: 'req-e2e-catalog', error: { code: 'NOT_FOUND', message: '模型不存在' } }),
      });
    }
    return route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({
        requestId: 'req-e2e-catalog',
        data: {
          pricingVersion: modelsVersion,
          model: { ...catalogDetailModel(seed), ...(detail === 'contract-mismatch' ? { id: 'someone-else' } : {}) },
        },
      }),
    });
  });
}
