import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

function readSource(path: string) {
  return readFileSync(resolve(process.cwd(), path), 'utf8');
}

const authSource = readSource('src/features/auth/authState.tsx');
const loginSource = readSource('src/pages/LoginPage.tsx');
const registerSource = readSource('src/pages/RegisterPage.tsx');
const clientSource = readSource('src/api/auth.ts');
const source = [authSource, loginSource, registerSource, clientSource].join('\n');

describe('前端认证安全边界', () => {
  it('不将会话或令牌写入 URL、浏览器存储或 Authorization Header', () => {
    expect(source).not.toMatch(/localStorage|sessionStorage|accessToken|Authorization|Bearer\s+/i);
    const navigationLine = loginSource.split('\n').find((line) => line.includes('navigate('));
    expect(navigationLine).toContain("params.get('returnTo')");
    expect(navigationLine).not.toMatch(/username|password/i);
  });

  it('认证写操作只使用同源请求和 CSRF Header，错误界面不读取原始 message', () => {
    expect(clientSource).toContain("'X-XSRF-TOKEN'");
    expect(clientSource).not.toMatch(/https?:\/\//);
    expect(loginSource).not.toMatch(/error\.message|loginMutation\.error\.message/);
    expect(registerSource).not.toMatch(/error\.message|registerMutation\.error\.message/);
  });
});
