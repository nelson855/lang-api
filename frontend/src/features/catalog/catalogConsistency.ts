/**
 * 列表与供应商选项的跨响应一致性判定。
 *
 * 只有在非空 `pricingVersion` 相同、且供应商名称与完整计数都能与当前列表对上的
 * 情况下才启用厂商选项。版本为空视为“无法确认”，可继续提供筛选但要提示；
 * 已知版本不同或结构对不上时判为冲突，调用方执行最多一轮成对重取。
 */

import type { CatalogModel, ProviderOption } from '../../api/models';

export type VersionCheck = 'matched' | 'mismatched' | 'unknown';

export interface ConsistencyInput {
  modelsVersion: string | null;
  providersVersion: string | null;
  models: CatalogModel[];
  providers: ProviderOption[];
}

export interface ConsistencyResult {
  status: 'consistent' | 'conflict';
  versionCheck: VersionCheck;
  /** 冲突时为空数组：不暂停使用无法核对的选项。 */
  options: ProviderOption[];
}

function countProviders(models: CatalogModel[]): Map<string, number> {
  const counts = new Map<string, number>();
  for (const model of models) {
    if (model.provider && model.provider.trim()) {
      counts.set(model.provider, (counts.get(model.provider) ?? 0) + 1);
    }
  }
  return counts;
}

/** 结构核对使用全部模型，而不是当前搜索命中数。 */
function structureMatches(models: CatalogModel[], providers: ProviderOption[]): boolean {
  const counts = countProviders(models);
  if (counts.size !== providers.length) {
    return false;
  }
  for (const provider of providers) {
    if (counts.get(provider.value) !== provider.modelCount) {
      return false;
    }
  }
  return true;
}

export function evaluateProviderConsistency(input: ConsistencyInput): ConsistencyResult {
  const versionCheck: VersionCheck =
    input.modelsVersion === null || input.providersVersion === null
      ? 'unknown'
      : input.modelsVersion === input.providersVersion
        ? 'matched'
        : 'mismatched';

  if (versionCheck === 'mismatched') {
    return { status: 'conflict', versionCheck, options: [] };
  }
  if (!structureMatches(input.models, input.providers)) {
    return { status: 'conflict', versionCheck, options: [] };
  }
  return { status: 'consistent', versionCheck, options: input.providers };
}

export function detailVersionRelation(
  catalogVersion: string | null,
  detailVersion: string | null,
): VersionCheck {
  if (catalogVersion === null || detailVersion === null) {
    return 'unknown';
  }
  return catalogVersion === detailVersion ? 'matched' : 'mismatched';
}
