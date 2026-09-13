import { describe, expect, it } from 'vitest';
import { enUS } from './resources/enUS';
import { zhCN } from './resources/zhCN';

type ResourceTree = Record<string, unknown>;

function collectKeys(tree: ResourceTree, prefix = '', out = new Map<string, unknown>()): Map<string, unknown> {
  for (const [key, value] of Object.entries(tree)) {
    const path = prefix === '' ? key : `${prefix}.${key}`;
    if (typeof value === 'object' && value !== null) {
      collectKeys(value as ResourceTree, path, out);
    } else {
      out.set(path, value);
    }
  }
  return out;
}

function placeholdersOf(text: string): string[] {
  const found = text.match(/{{(.*?)}}/g) ?? [];
  return [...new Set(found.map((item) => item.slice(2, -2).trim()))].sort();
}

const REQUIRED_KEYS = [
  'common.retry',
  'common.backHome',
  'common.language',
  'nav.home',
  'nav.models',
  'nav.docs',
  'nav.login',
  'nav.register',
  'nav.dashboard',
  'nav.logout',
  'pages.home.title',
  'pages.models.title',
  'pages.docs.title',
  'pages.login.title',
  'pages.register.title',
  'pages.register.closed',
  'pages.auth.username',
  'pages.auth.password',
  'pages.auth.confirmPassword',
  'pages.auth.invalidCredentials',
  'pages.auth.passwordMismatch',
  'pages.auth.requestFailed',
  'pages.dashboard.title',
  'pages.notFound.title',
  'nav.apiKeys',
  'pages.apiKeys.title',
  'pages.apiKeys.searchLabel',
  'pages.apiKeys.searchPlaceholder',
  'pages.apiKeys.statusLabel',
  'pages.apiKeys.statusAll',
  'pages.apiKeys.status_enabled',
  'pages.apiKeys.status_disabled',
  'pages.apiKeys.status_expired',
  'pages.apiKeys.status_exhausted',
  'pages.apiKeys.loading',
  'pages.apiKeys.emptyFirst',
  'pages.apiKeys.emptyNoMatch',
  'pages.apiKeys.create',
  'pages.apiKeys.colName',
  'pages.apiKeys.colKey',
  'pages.apiKeys.colStatus',
  'pages.apiKeys.colExpires',
  'pages.apiKeys.colActions',
  'pages.apiKeys.neverExpires',
  'pages.apiKeys.edit',
  'pages.apiKeys.enable',
  'pages.apiKeys.disable',
  'pages.apiKeys.delete',
  'pages.apiKeys.reveal',
  'pages.apiKeys.loadError',
  'pages.apiKeys.unknownTitle',
  'pages.apiKeys.unknownHint',
  'pages.apiKeys.createdHint',
  'pages.apiKeys.revealCopied',
  'pages.apiKeys.guideTitle',
  'pages.apiKeys.guideDismiss',
  'pages.apiKeys.formName',
  'pages.apiKeys.formQuota',
  'pages.apiKeys.formQuotaLimited',
  'pages.apiKeys.formUnlimited',
  'pages.apiKeys.formRemaining',
  'pages.apiKeys.formExpires',
  'pages.apiKeys.formExpiresHint',
  'pages.apiKeys.formAdvanced',
  'pages.apiKeys.formModels',
  'pages.apiKeys.formIps',
  'pages.apiKeys.formSubmitCreate',
  'pages.apiKeys.formSubmitSave',
  'pages.apiKeys.formCancel',
  'pages.apiKeys.formNameRequired',
  'pages.apiKeys.formNameTooLong',
  'pages.apiKeys.formRemainingInvalid',
  'pages.apiKeys.formExpiresInvalid',
  'pages.apiKeys.formExpiresPast',
  'pages.apiKeys.formModelsInvalid',
  'pages.apiKeys.formIpsInvalid',
  'pages.apiKeys.confirmDisable',
  'pages.apiKeys.confirmEnable',
  'pages.apiKeys.confirmDelete',
  'pages.apiKeys.disableTitle',
  'pages.apiKeys.enableTitle',
  'pages.apiKeys.deleteTitle',
  'pages.apiKeys.disableRisk',
  'pages.apiKeys.enableRisk',
  'pages.apiKeys.deleteRisk',
  'pages.apiKeys.revealRisk',
  'pages.apiKeys.revealShow',
  'pages.apiKeys.revealCopy',
  'pages.apiKeys.revealCopyFailed',
  'states.loading',
  'states.empty',
  'states.startup.loading',
  'states.startup.error',
  'errors.network',
  'errors.invalidResponse',
];

describe('中英文资源完整性', () => {
  it('两语言键集合完全一致', () => {
    const zhKeys = [...collectKeys(zhCN).keys()].sort();
    const enKeys = [...collectKeys(enUS).keys()].sort();
    expect(enKeys).toEqual(zhKeys);
  });

  it('同键插值参数约定一致', () => {
    const zh = collectKeys(zhCN);
    const en = collectKeys(enUS);
    const mismatched: string[] = [];
    for (const [path, value] of zh) {
      if (typeof value === 'string' && typeof en.get(path) === 'string') {
        const left = placeholdersOf(value).join(',');
        const right = placeholdersOf(en.get(path) as string).join(',');
        if (left !== right) {
          mismatched.push(path);
        }
      }
    }
    expect(mismatched).toEqual([]);
  });

  it('无空白文案且覆盖 P1-04 必需键', () => {
    const zh = collectKeys(zhCN);
    const en = collectKeys(enUS);
    for (const path of REQUIRED_KEYS) {
      expect(zh.has(path), `中文缺键 ${path}`).toBe(true);
      expect(en.has(path), `英文缺键 ${path}`).toBe(true);
    }
    for (const [path, value] of zh) {
      expect(typeof value === 'string' && value.trim().length > 0, `中文空文案 ${path}`).toBe(true);
    }
    for (const [path, value] of en) {
      expect(typeof value === 'string' && value.trim().length > 0, `英文空文案 ${path}`).toBe(true);
    }
  });

  it('认证文案不暴露上游品牌、原始消息或未交付能力', () => {
    const text = [...collectKeys(zhCN).values(), ...collectKeys(enUS).values()]
      .filter((value): value is string => typeof value === 'string')
      .join('\n');
    expect(text).not.toMatch(/new\s*api|access\s*token|oauth|mfa|passkey|private upstream/i);
  });
});
