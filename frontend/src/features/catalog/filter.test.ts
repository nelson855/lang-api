import { describe, expect, it } from 'vitest';
import {
  filterModels,
  providerOptions,
  resolveDocsModelSelection,
  resolveSelectedModel,
} from './filter';
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

  it('未指定模型时回退到按 ID 排序的首个可用模型', () => {
    expect(resolveDocsModelSelection(models, null)).toEqual({
      kind: 'default',
      model: expect.objectContaining({ id: 'GPT-Example' }),
    });
  });

  it('显式指定未知模型时不再静默替换', () => {
    expect(resolveDocsModelSelection(models, 'missing')).toEqual({ kind: 'unavailable' });
  });

  it('显式空值或超长参数视为非法', () => {
    expect(resolveDocsModelSelection(models, '')).toEqual({ kind: 'invalid' });
    expect(resolveDocsModelSelection(models, 'z'.repeat(200))).toEqual({ kind: 'invalid' });
  });

  it('显式指定存在的模型时精确采用', () => {
    expect(resolveDocsModelSelection(models, 'a-model')).toEqual({
      kind: 'explicit',
      model: expect.objectContaining({ id: 'a-model' }),
    });
  });

  it('保留旧的默认选择包装函数', () => {
    expect(resolveSelectedModel(models, 'a-model')?.id).toBe('a-model');
  });
});
