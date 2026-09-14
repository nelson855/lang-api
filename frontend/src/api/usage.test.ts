import { describe, expect, it, vi, afterEach } from 'vitest';
import {
  balanceSchema,
  buildSummaryUrl,
  buildTimeseriesUrl,
  summarySchema,
  timeseriesSchema,
} from './usage';

describe('usage api schema', () => {
  it('accepts decimal strings and rate window', () => {
    const summary = summarySchema.parse({
      quota: '1200',
      amount: '0.0024',
      currency: 'USD',
      rpm: 30,
      tpm: 4000,
      rateWindowSeconds: 60,
    });
    expect(summary.amount).toBe('0.0024');

    const series = timeseriesSchema.parse({
      granularity: 'HOUR',
      points: [
        { bucketStart: '2026-09-10T09:00:00Z', requestCount: 8, tokenCount: 150, quota: '700', amount: '0.0014' },
      ],
    });
    expect(series.points).toHaveLength(1);
  });

  it('accepts zero values and empty points', () => {
    const summary = summarySchema.parse({
      quota: '0',
      amount: '0.0',
      currency: 'USD',
      rpm: 0,
      tpm: 0,
      rateWindowSeconds: 60,
    });
    expect(summary.quota).toBe('0');
    const series = timeseriesSchema.parse({ granularity: 'HOUR', points: [] });
    expect(series.points).toEqual([]);
  });

  it('rejects leaked internal fields', () => {
    expect(() =>
      balanceSchema.parse({ quota: '1', amount: '1.0', currency: 'USD', role: 'admin' }),
    ).toThrow();
    expect(() =>
      summarySchema.parse({
        quota: '1',
        amount: '1.0',
        currency: 'USD',
        rpm: 1,
        tpm: 1,
        rateWindowSeconds: 60,
        inputTokens: 5,
      }),
    ).toThrow();
  });

  it('builds shared time range urls', () => {
    const url = buildSummaryUrl({ startTime: '2026-09-10T10:00:00Z', endTime: '2026-09-10T11:00:00Z' });
    expect(url).toContain('/portal/api/usage/summary');
    expect(url).toContain('startTime=');
    const seriesUrl = buildTimeseriesUrl({ startTime: '2026-09-10T10:00:00Z', endTime: '2026-09-10T11:00:00Z' });
    expect(seriesUrl).toContain('/portal/api/usage/timeseries');
  });
});

describe('usage api error handling', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('surfaces unified portal errors', async () => {
    const { fetchBalance } = await import('./usage');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ success: false, error: { code: 'UNAUTHENTICATED', message: 'expired' }, requestId: 'req-1' }), {
          status: 401,
          headers: { 'Content-Type': 'application/json' },
        }),
      ),
    );
    await expect(fetchBalance()).rejects.toMatchObject({ code: 'UNAUTHENTICATED' });
  });
});
