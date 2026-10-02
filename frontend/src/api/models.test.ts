import { describe, expect, it } from 'vitest';
import {
  catalogDetailResponseSchema,
  catalogProvidersResponseSchema,
  catalogResponseSchema,
} from './models';

/** 当前冻结基线：增强字段全部为 null 的合法详情样本。 */
const baselineDetail = {
  id: 'deepseek-v4-flash',
  displayName: null,
  provider: 'Example',
  availability: 'AVAILABLE',
  pricing: {
    mode: 'TOKEN',
    currency: 'USD',
    unit: 'PER_MILLION_TOKENS',
    input: '75.0',
    output: '75.0',
    request: null,
  },
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

describe('models api schema', () => {
  it('accepts token and request pricing', () => {
    const payload = {
      pricingVersion: 'v1',
      models: [
        {
          id: 'a-model',
          displayName: null,
          provider: 'Example',
          availability: 'AVAILABLE',
          pricing: {
            mode: 'TOKEN',
            currency: 'USD',
            unit: 'PER_MILLION_TOKENS',
            input: '2.5',
            output: '10.0',
            request: null,
          },
        },
        {
          id: 'b-model',
          displayName: null,
          provider: null,
          availability: 'AVAILABLE',
          pricing: {
            mode: 'REQUEST',
            currency: 'USD',
            unit: 'PER_REQUEST',
            input: null,
            output: null,
            request: '0.003',
          },
        },
      ],
    };
    const parsed = catalogResponseSchema.parse(payload);
    expect(parsed.models).toHaveLength(2);
  });

  it('rejects leaked internal fields', () => {
    const payload = {
      pricingVersion: 'v1',
      models: [
        {
          id: 'a',
          displayName: null,
          provider: null,
          availability: 'AVAILABLE',
          pricing: null,
          model_ratio: 5,
        },
      ],
    };
    expect(() => catalogResponseSchema.parse(payload)).toThrow();
  });
});

describe('model detail api schema', () => {
  it('接受当前全 null 基线详情', () => {
    const parsed = catalogDetailResponseSchema.parse({
      pricingVersion: 'p2-2026-09-22-a',
      model: baselineDetail,
    });
    expect(parsed.model.id).toBe('deepseek-v4-flash');
    expect(parsed.model.enhancedPricing).toBeNull();
    expect(parsed.model.capabilities.toolCalling).toBeNull();
  });

  it('保留列表 schema 不受影响', () => {
    const listOnly = {
      id: 'a-model',
      displayName: null,
      provider: null,
      availability: 'AVAILABLE',
      pricing: null,
    };
    expect(catalogResponseSchema.parse({ pricingVersion: 'v1', models: [listOnly] }).models).toHaveLength(1);
  });

  it('接受能力 true / false / null 三态并存', () => {
    const parsed = catalogDetailResponseSchema.parse({
      pricingVersion: 'v1',
      model: {
        ...baselineDetail,
        capabilities: { toolCalling: true, reasoning: false, structuredOutput: null, attachments: false },
      },
    });
    expect(parsed.model.capabilities).toEqual({
      toolCalling: true,
      reasoning: false,
      structuredOutput: null,
      attachments: false,
    });
  });

  it('接受零值限制、合法模态、空数组集合与日历日期', () => {
    const parsed = catalogDetailResponseSchema.parse({
      pricingVersion: 'v1',
      model: {
        ...baselineDetail,
        contextWindowTokens: 0,
        maxOutputTokens: 8192,
        inputModalities: ['TEXT', 'IMAGE'],
        outputModalities: [],
        releaseDate: '2026-01-31',
        tags: [],
        sortOrder: 0,
      },
    });
    expect(parsed.model.contextWindowTokens).toBe(0);
    expect(parsed.model.outputModalities).toEqual([]);
    expect(parsed.model.releaseDate).toBe('2026-01-31');
    expect(parsed.model.sortOrder).toBe(0);
  });

  it('接受任意精度与未知单位的增强价格', () => {
    const parsed = catalogDetailResponseSchema.parse({
      pricingVersion: 'v1',
      model: {
        ...baselineDetail,
        enhancedPricing: [
          { type: 'CACHE_INPUT', currency: 'USD', unit: 'PER_MILLION_TOKENS', price: '0.00000000000000000001' },
          { type: 'SEARCH', currency: 'USD', unit: 'PER_1K_CALLS', price: '0.0' },
        ],
      },
    });
    expect(parsed.model.enhancedPricing).toHaveLength(2);
  });


  it('接受合法科学计数法增强金额并保留完整原始精度', () => {
    const model = { ...baselineDetail, enhancedPricing: [
      { type: 'SEARCH', currency: 'CNY', unit: 'PER_REQUEST', price: '1.23456789012345678e-8' },
    ] };
    const parsed = catalogDetailResponseSchema.parse({ pricingVersion: 'v1', model });
    expect(parsed.model.enhancedPricing?.[0].price).toBe('1.23456789012345678e-8');
  });
  it('拒绝未知枚举、非法金额、非法日期与多余字段', () => {
    const cases = [
      { ...baselineDetail, inputModalities: ['TEXT', 'HOLOGRAM'] },
      { ...baselineDetail, enhancedPricing: [{ type: 'DISCOUNT', currency: 'USD', unit: 'PER_REQUEST', price: '1' }] },
      { ...baselineDetail, enhancedPricing: [{ type: 'VIDEO', currency: 'USD', unit: 'PER_REQUEST', price: '-1' }] },
      { ...baselineDetail, releaseDate: '2026-13-01' },
      { ...baselineDetail, model_ratio: 3 },
      { ...baselineDetail, contextWindowTokens: 'many' },
    ];
    for (const model of cases) {
      expect(() => catalogDetailResponseSchema.parse({ pricingVersion: 'v1', model })).toThrow();
    }
  });
});

describe('model providers api schema', () => {
  it('接受空供应商数组与完整计数', () => {
    const parsed = catalogProvidersResponseSchema.parse({
      pricingVersion: 'p2-2026-09-22-a',
      providers: [
        { value: 'DeepSeek', label: 'DeepSeek', modelCount: 2 },
        { value: 'OpenAI', label: 'OpenAI', modelCount: 0 },
      ],
    });
    expect(parsed.providers).toHaveLength(2);
    expect(parsed.providers[1].modelCount).toBe(0);
  });

  it('拒绝空值名称、负计数与多余字段', () => {
    const cases = [
      [{ value: '', label: 'DeepSeek', modelCount: 1 }],
      [{ value: 'DeepSeek', label: null, modelCount: 1 }],
      [{ value: 'DeepSeek', label: 'DeepSeek', modelCount: -1 }],
      [{ value: 'DeepSeek', label: 'DeepSeek', modelCount: 1, vendorId: 3 }],
    ];
    for (const providers of cases) {
      expect(() => catalogProvidersResponseSchema.parse({ pricingVersion: 'v1', providers })).toThrow();
    }
  });
});
