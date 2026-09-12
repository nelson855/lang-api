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
  'pages.home.title',
  'pages.models.title',
  'pages.docs.title',
  'pages.login.title',
  'pages.register.title',
  'pages.dashboard.title',
  'pages.notFound.title',
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
});
