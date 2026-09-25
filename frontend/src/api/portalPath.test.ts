import { describe, expect, it } from 'vitest';
import { buildPortalApiUrl, isPortalApiPath, validatePortalApiPath } from './portalPath';

describe('Portal API 路径白名单', () => {
  it('接受合法的站内 Portal 路径', () => {
    expect(isPortalApiPath('/portal/api/public-config')).toBe(true);
    expect(() => validatePortalApiPath('/portal/api/public-config')).not.toThrow();
  });

  it('接受带合法查询参数结构的路径', () => {
    expect(isPortalApiPath('/portal/api/models?page=1')).toBe(true);
  });

  it('拒绝绝对 URL', () => {
    expect(isPortalApiPath('https://example.com/portal/api/public-config')).toBe(false);
    expect(() => validatePortalApiPath('https://example.com/portal/api/public-config')).toThrow();
  });

  it('拒绝协议相对 URL', () => {
    expect(isPortalApiPath('//evil.com/portal/api/x')).toBe(false);
    expect(() => validatePortalApiPath('//evil.com/portal/api/x')).toThrow();
  });

  it('拒绝反斜线与 fragment', () => {
    expect(isPortalApiPath('/portal/api/x\\evil')).toBe(false);
    expect(isPortalApiPath('/portal/api/x#frag')).toBe(false);
  });

  it('拒绝编码或明文路径穿越', () => {
    expect(isPortalApiPath('/portal/api/../admin')).toBe(false);
    expect(isPortalApiPath('/portal/api/%2e%2e/admin')).toBe(false);
    expect(isPortalApiPath('/portal/api/%2Fevil')).toBe(false);
  });

  it('查询值中的合法编码斜线不视为路径穿越', () => {
    // IANA 时区等合法查询值经 URL 编码后含 %2F,不得误杀。
    expect(isPortalApiPath('/portal/api/account/consumption-summary?timezone=Asia%2FShanghai')).toBe(true);
    expect(() =>
      validatePortalApiPath('/portal/api/account/consumption-summary?timezone=Asia%2FShanghai'),
    ).not.toThrow();
    expect(
      buildPortalApiUrl('/portal/api/account/consumption-summary', { timezone: 'Asia/Shanghai' }),
    ).toBe('/portal/api/account/consumption-summary?timezone=Asia%2FShanghai');
  });

  it('拒绝非 Portal 路径与原始管理路径', () => {
    expect(isPortalApiPath('/api/models')).toBe(false);
    expect(isPortalApiPath('/portal/other')).toBe(false);
    expect(isPortalApiPath('/actuator/health')).toBe(false);
  });
});

describe('Portal API 结构化查询参数', () => {
  it('用结构化值构造查询串并编码', () => {
    expect(buildPortalApiUrl('/portal/api/models', { page: 1, q: 'a b&c' })).toBe(
      '/portal/api/models?page=1&q=a+b%26c',
    );
  });

  it('拒绝在构造阶段传入越界路径', () => {
    expect(() => buildPortalApiUrl('https://evil.com/x', { page: 1 })).toThrow();
  });
});
