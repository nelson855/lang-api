import { describe, expect, it } from 'vitest';
import { buildRequestLogsUrl, requestLogPageSchema } from './requestLogs';

describe('request logs api schema', () => {
  it('accepts nullable fields with server total', () => {
    const page = requestLogPageSchema.parse({
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
          quota: '500',
          amount: '0.001',
          currency: 'USD',
          protocol: null,
          firstTokenLatencyMs: null,
        },
        {
          occurredAt: '2026-09-10T10:01:00Z',
          requestId: null,
          keyName: null,
          model: null,
          result: 'ERROR',
          inputTokens: 0,
          outputTokens: 0,
          durationMs: 100,
          stream: false,
          quota: '0',
          amount: '0.0',
          currency: 'USD',
          protocol: null,
          firstTokenLatencyMs: null,
        },
      ],
      page: 1,
      pageSize: 20,
      total: 2,
    });
    expect(page.total).toBe(2);
    expect(page.items[1].requestId).toBeNull();
  });

  it('rejects leaked internal fields', () => {
    expect(() =>
      requestLogPageSchema.parse({
        items: [
          {
            occurredAt: '2026-09-10T10:00:00Z',
            requestId: null,
            keyName: null,
            model: null,
            result: 'SUCCESS',
            inputTokens: 0,
            outputTokens: 0,
            durationMs: 0,
            stream: false,
            quota: '0',
            amount: '0.0',
            currency: 'USD',
            protocol: null,
            firstTokenLatencyMs: null,
            content: 'secret-prompt',
          },
        ],
        page: 1,
        pageSize: 20,
        total: 1,
      }),
    ).toThrow();
  });

  it('serializes combined filters', () => {
    const url = buildRequestLogsUrl({
      page: 2,
      pageSize: 20,
      result: 'ERROR',
      keyName: 'probe-key-01',
      model: 'gpt',
      startTime: '2026-09-10T10:00:00Z',
      endTime: '2026-09-10T11:00:00Z',
    });
    expect(url).toContain('/portal/api/request-logs');
    expect(url).toContain('result=ERROR');
    expect(url).toContain('keyName=probe-key-01');
    expect(url).toContain('page=2');
  });

  it('omits empty filters', () => {
    const url = buildRequestLogsUrl({ page: 1, pageSize: 20, result: 'SUCCESS' });
    expect(url).not.toContain('keyName');
    expect(url).not.toContain('startTime');
  });
});
