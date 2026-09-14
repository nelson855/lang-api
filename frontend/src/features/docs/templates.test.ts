import { describe, expect, it } from 'vitest';
import { buildCurlNonStreaming, buildCurlStreaming, buildSdkNonStreaming, buildSdkStreaming, normalizeBaseUrl } from './templates';

describe('docs templates', () => {
  it('normalizes base url without duplicating version', () => {
    expect(normalizeBaseUrl('https://api.example.com/v1/')).toBe('https://api.example.com/v1');
  });

  it('curl non-streaming uses env key and selected model', () => {
    const code = buildCurlNonStreaming('https://api.example.com/v1', 'model-x');
    expect(code).toContain('https://api.example.com/v1/chat/completions');
    expect(code).toContain('model-x');
    expect(code).toContain('stream');
    expect(code).not.toContain('sk-');
  });

  it('sdk streaming uses iteration and env key', () => {
    const code = buildSdkStreaming('https://api.example.com/v1', 'model-x');
    expect(code).toContain('https://api.example.com/v1');
    expect(code).toContain('model-x');
    expect(code).toContain('for await');
  });

  it('escapes hostile model ids safely', () => {
    const hostile = `x'; rm -rf / #`;
    const curl = buildCurlNonStreaming('https://api.example.com/v1', hostile);
    expect(curl).not.toContain(`'${hostile}'`);
    const sdkNon = buildSdkNonStreaming('https://api.example.com/v1', hostile);
    expect(sdkNon).toContain(JSON.stringify(hostile));
    expect(buildCurlStreaming('https://api.example.com/v1', hostile)).toContain('stream');
  });
});
