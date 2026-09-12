import { describe, expect, it } from 'vitest';
import { z } from 'zod';
import {
  InvalidPortalResponseError,
  PortalApiError,
  parsePortalFailure,
  parsePortalSuccess,
} from './envelope';

const dataSchema = z.object({ siteName: z.string() });

describe('Portal 响应运行时校验', () => {
  it('校验成功包装并返回业务数据与 requestId', () => {
    const result = parsePortalSuccess(
      { requestId: 'req-1', data: { siteName: 'Lang API' } },
      dataSchema,
    );
    expect(result).toEqual({ data: { siteName: 'Lang API' }, requestId: 'req-1' });
  });

  it('成功包装缺 requestId 或业务字段非法时报协议错误', () => {
    expect(() =>
      parsePortalSuccess({ data: { siteName: 'Lang API' } }, dataSchema),
    ).toThrow(InvalidPortalResponseError);
    expect(() =>
      parsePortalSuccess({ requestId: 'req-1', data: {} }, dataSchema),
    ).toThrow(InvalidPortalResponseError);
  });

  it('校验失败包装并保留 code 与 requestId', () => {
    const error = parsePortalFailure({ requestId: 'req-2', error: { code: 'NOT_FOUND', message: '找不到' } }, 404);
    expect(error).toBeInstanceOf(PortalApiError);
    expect(error.code).toBe('NOT_FOUND');
    expect(error.requestId).toBe('req-2');
    expect(error.status).toBe(404);
  });

  it('拒绝 HTML 与非法结构且不泄露原文', () => {
    expect(() => parsePortalSuccess('<html>', dataSchema)).toThrow(InvalidPortalResponseError);
    try {
      parsePortalSuccess({ requestId: 'req-1', data: {} }, dataSchema);
    } catch (e) {
      expect(e).toBeInstanceOf(InvalidPortalResponseError);
      expect(String(e)).not.toContain('<html>');
    }
  });
});
