import { describe, expect, it, vi, beforeEach } from 'vitest';
import {
  dashboardStatsSchema,
  countMetricSchema,
  moneyMetricSchema,
  ratioMetricSchema,
  trendPointSchema,
  type DashboardMetrics,
  type DashboardStats,
} from './dashboard';
import { portalRequest } from './portalClient';

vi.mock('./portalClient', () => ({
  portalRequest: vi.fn(),
}));

/** 一个合法的六指标齐备快照，用于派生各测试基座。 */
function legalMetrics(): DashboardMetrics {
  return {
    requestTotal: { value: 1200, unit: 'requests', availability: 'AVAILABLE', reasonCode: null },
    tokenUsage: { value: 90000, unit: 'tokens', availability: 'AVAILABLE', reasonCode: null },
    spend: { value: '0.0543', currency: 'USD', availability: 'AVAILABLE', reasonCode: null },
    activeKeys: { value: 3, unit: 'keys', availability: 'AVAILABLE', reasonCode: null },
    successRate: { value: 0.9834, unit: 'ratio', availability: 'AVAILABLE', reasonCode: null },
    averageLatency: { value: 210.5, unit: 'ms', availability: 'AVAILABLE', reasonCode: null },
  };
}

/** 一个合法完整响应，供派生使用。 */
function legalResponse(): DashboardStats {
  return {
    baselineVersion: 'p2-2026-09-22-a',
    range: {
      startTime: '2026-09-01T00:00:00Z',
      endTime: '2026-09-01T02:00:00Z',
      timezone: 'UTC',
      granularity: 'HOUR',
    },
    metrics: legalMetrics(),
    requestTrend: {
      availability: 'AVAILABLE',
      reasonCode: null,
      unit: 'requests',
      points: [
        { bucketStart: '2026-09-01T00:00:00Z', bucketEnd: '2026-09-01T01:00:00Z', value: '120' },
      ],
    },
    spendTrend: {
      availability: 'AVAILABLE',
      reasonCode: null,
      currency: 'USD',
      points: [{ bucketStart: '2026-09-01T00:00:00Z', bucketEnd: '2026-09-01T01:00:00Z', value: '0.0012' }],
    },
    recentRequests: {
      availability: 'PARTIAL',
      reasonCode: 'PARTIAL_SOURCE_COVERAGE',
      items: [
        {
          occurredAt: '2026-09-01T01:59:00Z',
          requestId: 'req-1',
          keyName: 'probe-key-01',
          model: 'gpt-test',
          outcome: 'SUCCESS',
          inputTokens: 10,
          outputTokens: 20,
          durationMs: 300,
          stream: true,
        },
      ],
    },
  };
}

describe('dashboard api 契约层', () => {
  it('接受完整的可用快照响应', () => {
    const parsed = dashboardStatsSchema.parse(legalResponse());
    expect(parsed.metrics.tokenUsage.value).toBe(90000);
    expect(parsed.metrics.spend.value).toBe('0.0543');
    expect(parsed.metrics.successRate.value).toBe(0.9834);
    expect(parsed.requestTrend.points).toHaveLength(1);
    expect(parsed.recentRequests.items[0].outcome).toBe('SUCCESS');
  });

  it('保留趋势点原始字符串值，不转换为数值', () => {
    const parsed = dashboardStatsSchema.parse(legalResponse());
    expect(typeof parsed.requestTrend.points[0].value).toBe('string');
    expect(parsed.spendTrend.points[0].value).toBe('0.0012');
  });

  it('接受当前部分可用响应（不可用指标 + 部分覆盖最近请求）', () => {
    const payload = legalResponse();
    payload.metrics.requestTotal = {
      value: null,
      unit: 'requests',
      availability: 'UNAVAILABLE',
      reasonCode: 'BASELINE_NOT_VERIFIED',
    };
    payload.metrics.spend = {
      value: null,
      currency: null,
      availability: 'UNAVAILABLE',
      reasonCode: 'BASELINE_NOT_VERIFIED',
    };
    payload.metrics.successRate = {
      value: null,
      unit: 'ratio',
      availability: 'UNAVAILABLE',
      reasonCode: 'BASELINE_NOT_VERIFIED',
    };
    payload.requestTrend = {
      availability: 'UNAVAILABLE',
      reasonCode: 'BASELINE_NOT_VERIFIED',
      unit: 'requests',
      points: [],
    };
    payload.spendTrend = {
      availability: 'UNAVAILABLE',
      reasonCode: 'BASELINE_NOT_VERIFIED',
      currency: null,
      points: [],
    };

    const parsed = dashboardStatsSchema.parse(payload);
    expect(parsed.metrics.requestTotal.availability).toBe('UNAVAILABLE');
    expect(parsed.metrics.requestTotal.value).toBeNull();
    expect(parsed.requestTrend.points).toEqual([]);
    expect(parsed.recentRequests.availability).toBe('PARTIAL');
  });

  it('接受真实零值与空点数组', () => {
    const payload = legalResponse();
    payload.metrics.tokenUsage = { value: 0, unit: 'tokens', availability: 'AVAILABLE', reasonCode: null };
    payload.metrics.spend = { value: '0.0', currency: 'USD', availability: 'AVAILABLE', reasonCode: null };
    payload.metrics.successRate = { value: 1, unit: 'ratio', availability: 'AVAILABLE', reasonCode: null };
    payload.requestTrend = { availability: 'AVAILABLE', reasonCode: null, unit: 'requests', points: [] };

    const parsed = dashboardStatsSchema.parse(payload);
    expect(parsed.metrics.tokenUsage.value).toBe(0);
    expect(parsed.requestTrend.points).toEqual([]);
  });

  it('拒绝 AVAILABLE 指标缺少值', () => {
    expect(() => countMetricSchema.parse({ value: null, unit: 'requests', availability: 'AVAILABLE', reasonCode: null })).toThrow();
    expect(() => moneyMetricSchema.parse({ value: null, currency: 'USD', availability: 'AVAILABLE', reasonCode: null })).toThrow();
    expect(() => ratioMetricSchema.parse({ value: null, unit: 'ratio', availability: 'AVAILABLE', reasonCode: null })).toThrow();
  });

  it('拒绝 UNAVAILABLE 指标携带值或缺少原因', () => {
    expect(() => countMetricSchema.parse({ value: 5, unit: 'requests', availability: 'UNAVAILABLE', reasonCode: 'BASELINE_NOT_VERIFIED' })).toThrow();
    expect(() => countMetricSchema.parse({ value: null, unit: 'requests', availability: 'UNAVAILABLE', reasonCode: null })).toThrow();
    expect(() => moneyMetricSchema.parse({ value: '0.5', currency: 'USD', availability: 'UNAVAILABLE', reasonCode: 'NO_DATA' })).toThrow();
  });

  it('拒绝六指标中出现 PARTIAL', () => {
    expect(() => countMetricSchema.parse({ value: null, unit: 'requests', availability: 'PARTIAL', reasonCode: 'PARTIAL_SOURCE_COVERAGE' })).toThrow();
    expect(() => ratioMetricSchema.parse({ value: null, unit: 'ratio', availability: 'PARTIAL', reasonCode: 'PARTIAL_SOURCE_COVERAGE' })).toThrow();
  });

  it('拒绝未知 availability 或 reasonCode 枚举', () => {
    expect(() => countMetricSchema.parse({ value: 1, unit: 'requests', availability: 'REAL', reasonCode: null })).toThrow();
    const payload = legalResponse();
    expect(() =>
      dashboardStatsSchema.parse({
        ...payload,
        metrics: {
          ...payload.metrics,
          activeKeys: { value: 3, unit: 'keys', availability: 'AVAILABLE', reasonCode: 'MYSTERY_REASON' } as unknown,
        } as unknown,
      }),
    ).toThrow();
  });

  it('拒绝非法趋势点（时间或值非法）', () => {
    expect(() =>
      trendPointSchema.parse({ bucketStart: 'not-a-date', bucketEnd: '2026-09-01T01:00:00Z', value: '120' }),
    ).toThrow();
    expect(() =>
      trendPointSchema.parse({ bucketStart: '2026-09-01T00:00:00Z', bucketEnd: '2026-09-01T01:00:00Z', value: '1e3' }),
    ).toThrow();
  });

  it('拒绝未知响应字段', () => {
    const payload = legalResponse() as Record<string, unknown>;
    payload.extraSecret = 'leak';
    expect(() => dashboardStatsSchema.parse(payload)).toThrow();
  });
});

describe('dashboard api url 与请求', () => {
  beforeEach(() => {
    vi.mocked(portalRequest).mockReset();
  });

  it('用四个必填查询参数构造相对 stats URL', async () => {
    const { buildDashboardStatsUrl } = await import('./dashboard');
    const url = buildDashboardStatsUrl({
      startTime: '2026-09-01T00:00:00Z',
      endTime: '2026-09-01T02:00:00Z',
      granularity: 'HOUR',
      timezone: 'America/New_York',
    });
    expect(url).toContain('/portal/api/dashboard/stats');
    expect(url).toContain('startTime=');
    expect(url).toContain('endTime=');
    expect(url).toContain('granularity=HOUR');
    expect(url).toContain('timezone=');
    expect(url).not.toContain('://');
  });

  it('fetchDashboardStats 只访问相对 stats 路径并透传取消信号', async () => {
    const { fetchDashboardStats } = await import('./dashboard');
    const stats = {
      baselineVersion: 'p2-2026-09-22-a',
      range: { startTime: '2026-09-01T00:00:00Z', endTime: '2026-09-01T02:00:00Z', timezone: 'UTC', granularity: 'HOUR' },
      metrics: {
        requestTotal: { value: null, unit: 'requests', availability: 'UNAVAILABLE', reasonCode: 'BASELINE_NOT_VERIFIED' },
        tokenUsage: { value: 90, unit: 'tokens', availability: 'AVAILABLE', reasonCode: null },
        spend: { value: null, currency: null, availability: 'UNAVAILABLE', reasonCode: 'BASELINE_NOT_VERIFIED' },
        activeKeys: { value: 2, unit: 'keys', availability: 'AVAILABLE', reasonCode: null },
        successRate: { value: null, unit: 'ratio', availability: 'UNAVAILABLE', reasonCode: 'BASELINE_NOT_VERIFIED' },
        averageLatency: { value: null, unit: 'ms', availability: 'UNAVAILABLE', reasonCode: 'NO_DATA' },
      },
      requestTrend: { availability: 'UNAVAILABLE', reasonCode: 'BASELINE_NOT_VERIFIED', unit: 'requests', points: [] },
      spendTrend: { availability: 'UNAVAILABLE', reasonCode: 'BASELINE_NOT_VERIFIED', currency: null, points: [] },
      recentRequests: { availability: 'PARTIAL', reasonCode: 'PARTIAL_SOURCE_COVERAGE', items: [] },
    };
    vi.mocked(portalRequest).mockResolvedValueOnce({ data: stats, requestId: 'req-1' });
    const controller = new AbortController();

    await fetchDashboardStats(
      {
        startTime: '2026-09-01T00:00:00Z',
        endTime: '2026-09-01T02:00:00Z',
        granularity: 'HOUR',
        timezone: 'UTC',
      },
      controller.signal,
    );

    expect(portalRequest).toHaveBeenCalledTimes(1);
    const [path, , options] = vi.mocked(portalRequest).mock.calls[0];
    expect(String(path)).toMatch(/^\/portal\/api\/dashboard\/stats\?/);
    expect(options?.signal).toBe(controller.signal);
  });
});