import { describe, expect, it } from 'vitest';
import { filterModels, providerOptions, resolveSelectedModel } from './filter';
import type { CatalogModel } from '../../api/models';

const models: CatalogModel[] = [
  { id: 'GPT-Example', displayName: null, provider: 'Example', availability: 'AVAILABLE', pricing: null },
  { id: 'b-model', displayName: null, provider: null, availability: 'AVAILABLE', pricing: null },
  { id: 'a-model', displayName: null, provider: 'Example', availability: 'AVAILABLE', pricing: null },
];

describe('catalog filter', () => {
  it('searches case-insensitively without new requests', () => {
    expect(filterModels(models, 'gpt', null).map((m) => m.id)).toEqual(['GPT-Example']);
  });

  it('builds provider options from non-empty providers only', () => {
    expect(providerOptions(models)).toEqual(['Example']);
  });

  it('resolves unknown model param to first sorted available', () => {
    expect(resolveSelectedModel(models, 'missing')?.id).toBe('GPT-Example');
  });
});
