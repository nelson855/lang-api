/**
 * 模型广场 URL 状态的纯函数适配层。
 *
 * 白名单参数只有 `q` 与 `provider`；未知参数一律不参与筛选，也不被序列化回写。
 * 重复参数取首个。搜索忽略首尾空白，厂商保持大小写敏感的精确名称。
 * 不提供可执行的 `returnTo`。
 */

import type { ProviderOption } from '../../api/models';

export interface CatalogConditions {
  search: string;
  provider: string | null;
}

export const CATALOG_URL_KEYS = ['q', 'provider'] as const;

export const EMPTY_CATALOG_CONDITIONS: Readonly<CatalogConditions> = Object.freeze({
  search: '',
  provider: null,
});

export function parseCatalogConditions(search: string): CatalogConditions {
  const params = new URLSearchParams(search);
  const rawSearch = params.get('q') ?? '';
  const rawProvider = params.get('provider') ?? '';
  return {
    search: rawSearch.trim(),
    provider: rawProvider ? rawProvider : null,
  };
}

export function buildCatalogSearch(conditions: CatalogConditions): string {
  const params = new URLSearchParams();
  const q = conditions.search.trim();
  if (q) {
    params.set('q', q);
  }
  if (conditions.provider) {
    params.set('provider', conditions.provider);
  }
  const suffix = params.toString();
  return suffix.length === 0 ? '' : `?${suffix}`;
}

/** 详情“返回模型广场”只接受受控同源路径，不接受任意返回地址。 */
export function buildCatalogListPath(conditions: CatalogConditions): string {
  return `/models${buildCatalogSearch(conditions)}`;
}

export type ProviderCondition =
  | { status: 'none'; value: null }
  | { status: 'pending'; value: string }
  | { status: 'known'; value: string }
  | { status: 'unknown'; value: string };

/**
 * 判断 URL 中的厂商条件是否仍在当前供应商目录中。
 * 目录尚未加载（options 为 null）时保持条件原样，等待核对而不是静默清除。
 */
export function resolveProviderCondition(
  provider: string | null,
  options: ProviderOption[] | null,
): ProviderCondition {
  if (provider === null) {
    return { status: 'none', value: null };
  }
  if (options === null) {
    return { status: 'pending', value: provider };
  }
  return options.some((option) => option.value === provider)
    ? { status: 'known', value: provider }
    : { status: 'unknown', value: provider };
}
