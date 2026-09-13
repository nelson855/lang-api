import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

function readSource(path: string) {
  return readFileSync(resolve(process.cwd(), path), 'utf8');
}

function allSourceFiles(dir: string, out: string[] = []): string[] {
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    if (statSync(full).isDirectory()) {
      allSourceFiles(full, out);
    } else if (/\.(ts|tsx)$/.test(entry)) {
      out.push(full);
    }
  }
  return out;
}

const apiSource = readSource('src/api/apiKeys.ts');
const cacheSource = readSource('src/features/apiKeys/apiKeysCache.ts');
const hooksSource = readSource('src/features/apiKeys/useApiKeys.ts');
const source = [apiSource, cacheSource, hooksSource].join('\n');

describe('API Key 明文安全边界', () => {
  it('明文不进浏览器存储，变更默认不重试', () => {
    expect(source).not.toMatch(/localStorage|sessionStorage/);
    expect(apiSource).not.toMatch(/https?:\/\//);
    expect(hooksSource).not.toMatch(/setQueryData\(.*secret|secret.*setQueryData/);
    expect(hooksSource).toMatch(/retry:\s*false/);
  });

  it('源码不含上游私网路径与内网地址', () => {
    const offenders = allSourceFiles(resolve(process.cwd(), 'src'))
      .filter((file) => !/\.test\.[jt]sx?$/.test(file))
      .filter((file) => {
        const text = readFileSync(file, 'utf8');
        return (
          text.includes('/api/token') ||
          /\b10\.\d{1,3}\.\d{1,3}\.\d{1,3}\b/.test(text) ||
          /\b192\.168\.\d{1,3}\.\d{1,3}\b/.test(text)
        );
      });
    expect(offenders).toEqual([]);
  });
});
