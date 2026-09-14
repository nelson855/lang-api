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

export function resolveSelectedModel(
  models: CatalogModel[],
  requested: string | null,
): CatalogModel | null {
  const sorted = [...models].sort((a, b) => (a.id < b.id ? -1 : a.id > b.id ? 1 : 0));
  if (requested && requested.length <= 128) {
    const hit = sorted.find((m) => m.id === requested);
    if (hit && hit.availability === 'AVAILABLE') {
      return hit;
    }
  }
  return sorted.find((m) => m.availability === 'AVAILABLE') ?? null;
}
