import { describe, expect, it } from 'vitest';
import { findApiBoundaryViolations } from './apiBoundaries';

describe('前端 API 访问边界', () => {
  it('放行经统一客户端的相对 Portal 路径', () => {
    expect(
      findApiBoundaryViolations('src/pages/Home.tsx', `import { portalRequest } from '../api/portalClient';\nportalRequest('/portal/api/public-config', schema);`),
    ).toEqual([]);
  });

  it('拦截页面层直接调用全局 fetch', () => {
    const violations = findApiBoundaryViolations('src/pages/Home.tsx', `fetch('/portal/api/x');`);
    expect(violations.length).toBeGreaterThan(0);
  });

  it('拦截绝对地址与原始管理路径', () => {
    expect(
      findApiBoundaryViolations('src/components/Table.tsx', `const url = "https://api.example.com/portal/api/x";`),
    ).not.toEqual([]);
    expect(
      findApiBoundaryViolations('src/layouts/Public.tsx', `const url = "/api/models";`),
    ).not.toEqual([]);
  });

  it('不误伤统一客户端自身的 fetch 实现', () => {
    expect(
      findApiBoundaryViolations('src/api/portalClient.ts', `fetch(path, { credentials: 'same-origin' });`),
    ).toEqual([]);
  });

  it('持豁免标记的安全校验代码不误报', () => {
    expect(
      findApiBoundaryViolations(
        'src/features/auth/returnTo.ts',
        `// api-boundary-exempt: 校验器自身需列举服务端边界前缀\nconst url = "https://x"; fetch(a);`,
      ),
    ).toEqual([]);
  });
});
