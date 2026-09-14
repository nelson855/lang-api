import { describe, expect, it } from 'vitest';
import { catalogResponseSchema } from './models';

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
