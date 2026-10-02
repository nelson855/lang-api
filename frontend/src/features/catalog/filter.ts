import { MODEL_ID_MAX_LENGTH } from '../../api/models';
import type { CatalogModel } from '../../api/models';

function fold(value: string): string {
  return value.toLocaleLowerCase('en-US');
}

export function filterModels(models: CatalogModel[], query: string, provider: string | null): CatalogModel[] {
  const q = fold(query.trim());
  return models.filter((m) => {
    if (provider && m.provider !== provider) {
      return false;
    }
    if (!q) {
      return true;
    }
    if (fold(m.id).includes(q)) {
      return true;
    }
    if (m.displayName && fold(m.displayName).includes(q)) {
      return true;
    }
    return false;
  });
}

export function providerOptions(models: CatalogModel[]): string[] {
  const set = new Set<string>();
  for (const m of models) {
    if (m.provider && m.provider.trim()) {
      set.add(m.provider);
    }
  }
  return [...set].sort();
}

/**
 * 文档页的模型选择。
 *
 * 显式给出的 `model` 参数必须精确存在于目录中：未知、非法或已下线时都不生成示例，
 * 不静默替换为其他模型。只有未指定参数时，才回退到按 ID 排序的首个可用模型。
 */
export type SelectedModelResolution =
  | { kind: 'default'; model: CatalogModel | null }
  | { kind: 'explicit'; model: CatalogModel }
  | { kind: 'invalid' }
  | { kind: 'unavailable' };

function firstAvailable(models: CatalogModel[]): CatalogModel | null {
  const sorted = [...models].sort((a, b) => (a.id < b.id ? -1 : a.id > b.id ? 1 : 0));
  return sorted.find((m) => m.availability === 'AVAILABLE') ?? null;
}

export function resolveSelectedModel(
  models: CatalogModel[],
  requested: string | null,
): CatalogModel | null {
  const selection = resolveDocsModelSelection(models, requested);
  return selection.kind === 'default' || selection.kind === 'explicit' ? selection.model : null;
}

export function resolveDocsModelSelection(
  models: CatalogModel[],
  requested: string | null,
): SelectedModelResolution {
  if (requested === null) {
    return { kind: 'default', model: firstAvailable(models) };
  }
  if (requested.length === 0 || requested.length > MODEL_ID_MAX_LENGTH) {
    return { kind: 'invalid' };
  }
  const hit = models.find((m) => m.id === requested);
  if (!hit) {
    return { kind: 'unavailable' };
  }
  if (hit.availability !== 'AVAILABLE') {
    return { kind: 'unavailable' };
  }
  return { kind: 'explicit', model: hit };
}
