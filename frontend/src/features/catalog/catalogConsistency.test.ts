import { describe, expect, it } from 'vitest';
import {
  detailVersionRelation,
  evaluateProviderConsistency,
  type ConsistencyInput,
} from './catalogConsistency';
import type { CatalogModel, ProviderOption } from '../../api/models';

function model(id: string, provider: string | null): CatalogModel {
  return { id, displayName: null, provider, availability: 'AVAILABLE', pricing: null };
}

function option(value: string, modelCount: number): ProviderOption {
  return { value, label: value, modelCount };
}

function input(overrides: Partial<ConsistencyInput> = {}): ConsistencyInput {
  return {
    modelsVersion: 'v1',
    providersVersion: 'v1',
    models: [model('a', 'Alpha'), model('b', 'Alpha'), model('c', 'Beta')],
    providers: [option('Alpha', 2), option('Beta', 1)],
    ...overrides,
  };
}

describe('已知版本相同', () => {
  it('结构核对通过时启用厂商选项', () => {
    expect(evaluateProviderConsistency(input())).toEqual({
      status: 'consistent',
      versionCheck: 'matched',
      options: [option('Alpha', 2), option('Beta', 1)],
    });
  });
});

describe('已知版本冲突', () => {
  it('不同非空版本进入冲突恢复', () => {
    const result = evaluateProviderConsistency(input({ providersVersion: 'v2' }));
    expect(result.status).toBe('conflict');
    expect(result.versionCheck).toBe('mismatched');
    expect(result.options).toEqual([]);
  });
});

describe('版本缺失', () => {
  it('任一版本为 null 标记为未知，不把 null 相等当证据', () => {
    const modelsNull = evaluateProviderConsistency(input({ modelsVersion: null }));
    expect(modelsNull.versionCheck).toBe('unknown');
    expect(modelsNull.status).toBe('consistent');
    expect(modelsNull.options).toHaveLength(2);

    const providersNull = evaluateProviderConsistency(input({ providersVersion: null }));
    expect(providersNull.versionCheck).toBe('unknown');
    expect(providersNull.status).toBe('consistent');
  });

  it('两版本皆为空同样视为未知', () => {
    const result = evaluateProviderConsistency(input({ modelsVersion: null, providersVersion: null }));
    expect(result.versionCheck).toBe('unknown');
  });
});

describe('结构核对', () => {
  it('计数使用全部模型而不是搜索命中数', () => {
    const result = evaluateProviderConsistency(
      input({ providers: [option('Alpha', 1), option('Beta', 1)] }),
    );
    expect(result.status).toBe('conflict');
    expect(result.versionCheck).toBe('matched');
    expect(result.options).toEqual([]);
  });

  it('厂商名称与列表不一致时判为冲突', () => {
    const result = evaluateProviderConsistency(input({ providers: [option('Gamma', 3)] }));
    expect(result.status).toBe('conflict');
  });

  it('列表中的未知厂商不被伪造进选项', () => {
    const result = evaluateProviderConsistency(
      input({
        models: [model('a', 'Alpha'), model('b', 'Alpha'), model('c', null)],
        providers: [option('Alpha', 2)],
      }),
    );
    expect(result.status).toBe('consistent');
    expect(result.options.map((o) => o.value)).toEqual(['Alpha']);
  });

  it('已知版本相同但结构异常时不启用错项', () => {
    const result = evaluateProviderConsistency(
      input({ models: [model('a', 'Alpha')], providers: [option('Alpha', 5)] }),
    );
    expect(result.status).toBe('conflict');
    expect(result.versionCheck).toBe('matched');
  });
});

describe('列表与详情版本关系', () => {
  it('任一版本未知时无法确认跨请求一致性', () => {
    expect(detailVersionRelation('v1', null)).toBe('unknown');
    expect(detailVersionRelation(null, 'v1')).toBe('unknown');
    expect(detailVersionRelation(null, null)).toBe('unknown');
  });

  it('版本相同视为一致', () => {
    expect(detailVersionRelation('v1', 'v1')).toBe('matched');
  });

  it('已知版本不同视为冲突', () => {
    expect(detailVersionRelation('v1', 'v2')).toBe('mismatched');
  });
});
