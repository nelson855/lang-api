import { describe, expect, it } from 'vitest';
import { requestLogPageSchema } from './requestLogs';

/** 基础字段完整，仅两��增强字段可变，便于单独断言归一规则。 */
function page(overrides: Record<string, unknown> = {}, pageOverrides: Record<string, unknown> = {}) {
  return {
    items: [
      {
        occurredAt: '2026-09-10T10:00:00Z',
        requestId: 'req-1',
        keyName: 'probe-key-01',
        model: 'gpt-test',
        result: 'SUCCESS',
        inputTokens: 10,
        outputTokens: 20,
        durationMs: 3000,
        stream: true,
        quota: '500000',
        amount: '1.0',
        currency: 'USD',
        ...overrides,
      },
    ],
    page: 1,
    pageSize: 20,
    total: 1,
    ...pageOverrides,
  };
}

function firstItem(raw: Record<string, unknown>) {
  return requestLogPageSchema.parse(page(raw)).items[0];
}

describe('增强字段归一：受控协议', () => {
  it('保留受控词表内的合法协议', () => {
    for (const protocol of ['OPENAI', 'ANTHROPIC', 'GEMINI']) {
      expect(firstItem({ protocol, firstTokenLatencyMs: null }).protocol).toBe(protocol);
    }
  });

  it('把未知、空白与异常类型的协议降级为 null', () => {
    expect(firstItem({ protocol: 'META_LLAMA', firstTokenLatencyMs: null }).protocol).toBeNull();
    expect(firstItem({ protocol: '', firstTokenLatencyMs: null }).protocol).toBeNull();
    expect(firstItem({ protocol: '   ', firstTokenLatencyMs: null }).protocol).toBeNull();
    expect(firstItem({ protocol: 42, firstTokenLatencyMs: null }).protocol).toBeNull();
    expect(firstItem({ protocol: ['OPENAI'], firstTokenLatencyMs: null }).protocol).toBeNull();
    expect(firstItem({ protocol: { value: 'OPENAI' }, firstTokenLatencyMs: null }).protocol).toBeNull();
    expect(firstItem({ protocol: 'openai', firstTokenLatencyMs: null }).protocol).toBeNull();
  });

  it('缺失与显式 null 都归一为 null', () => {
    expect(firstItem({ firstTokenLatencyMs: null }).protocol).toBeNull();
    expect(firstItem({ protocol: null, firstTokenLatencyMs: null }).protocol).toBeNull();
  });

  it('疑似注入文本的协议不原样透传', () => {
    expect(firstItem({ protocol: '<script>alert(1)</script>', firstTokenLatencyMs: null }).protocol).toBeNull();
  });
});

describe('增强字段归一：首 Token 延迟', () => {
  it('保留合法的非负整数毫秒值', () => {
    expect(firstItem({ protocol: null, firstTokenLatencyMs: 820 }).firstTokenLatencyMs).toBe(820);
  });

  it('合法的零值保留为零而不是降级', () => {
    expect(firstItem({ protocol: null, firstTokenLatencyMs: 0 }).firstTokenLatencyMs).toBe(0);
  });

  it('负数、小数与非整数降级为 null', () => {
    expect(firstItem({ protocol: null, firstTokenLatencyMs: -1 }).firstTokenLatencyMs).toBeNull();
    expect(firstItem({ protocol: null, firstTokenLatencyMs: 12.5 }).firstTokenLatencyMs).toBeNull();
  });

  it('字符串与非有限值降级为 null', () => {
    expect(firstItem({ protocol: null, firstTokenLatencyMs: '820' }).firstTokenLatencyMs).toBeNull();
    expect(firstItem({ protocol: null, firstTokenLatencyMs: Number.NaN }).firstTokenLatencyMs).toBeNull();
    expect(firstItem({ protocol: null, firstTokenLatencyMs: Number.POSITIVE_INFINITY }).firstTokenLatencyMs).toBeNull();
  });

  it('超出浏览器安全整数范围的值降级为 null', () => {
    expect(firstItem({ protocol: null, firstTokenLatencyMs: Number.MAX_SAFE_INTEGER }).firstTokenLatencyMs)
      .toBe(Number.MAX_SAFE_INTEGER);
    expect(
      firstItem({ protocol: null, firstTokenLatencyMs: Number.MAX_SAFE_INTEGER + 2 })
        .firstTokenLatencyMs,
    ).toBeNull();
  });

  it('缺失与显式 null 都归一为 null', () => {
    expect(firstItem({ protocol: null }).firstTokenLatencyMs).toBeNull();
    expect(firstItem({ protocol: null, firstTokenLatencyMs: null }).firstTokenLatencyMs).toBeNull();
  });
});

describe('增强字段归一：旧响应与混合可用性', () => {
  it('旧响应省略两个增强字段时整页仍可解析', () => {
    const page1 = requestLogPageSchema.parse(page());
    expect(page1.total).toBe(1);
    expect(page1.items[0].protocol).toBeNull();
    expect(page1.items[0].firstTokenLatencyMs).toBeNull();
  });

  it('异常增强值不影响同页合法条目与真实 total', () => {
    const parsed = requestLogPageSchema.parse({
      items: [
        { ...page().items[0], requestId: 'req-a', protocol: 'UNKNOWN_VENDOR', firstTokenLatencyMs: -5 },
        { ...page().items[0], requestId: 'req-b', protocol: 'OPENAI', firstTokenLatencyMs: 0 },
      ],
      page: 2,
      pageSize: 20,
      total: 57,
    });
    expect(parsed.total).toBe(57);
    expect(parsed.items[0].protocol).toBeNull();
    expect(parsed.items[0].firstTokenLatencyMs).toBeNull();
    expect(parsed.items[1].protocol).toBe('OPENAI');
    expect(parsed.items[1].firstTokenLatencyMs).toBe(0);
  });

  it('基础字段非法仍整页失败，不被增强字段降级规则掩盖', () => {
    expect(() =>
      requestLogPageSchema.parse(page({ protocol: 'META', firstTokenLatencyMs: -3, inputTokens: -1 })),
    ).toThrow();
  });

  it('基础金额非法仍整页失败', () => {
    expect(() =>
      requestLogPageSchema.parse(page({ protocol: 'OPENAI', firstTokenLatencyMs: 1, amount: '-0.5' })),
    ).toThrow();
  });

  it('未声明字段仍被严格校验拒绝', () => {
    expect(() =>
      requestLogPageSchema.parse(page({ protocol: null, firstTokenLatencyMs: null, content: 'secret' })),
    ).toThrow();
  });
});