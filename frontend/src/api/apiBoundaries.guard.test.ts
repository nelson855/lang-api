import { readdirSync, readFileSync, existsSync } from 'node:fs';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';
import { findApiBoundaryViolations } from './apiBoundaries';

const GUARDED_DIRS = ['src/pages', 'src/components', 'src/layouts', 'src/features'];

function walk(dir: string, out: string[] = []): string[] {
  if (!existsSync(dir)) {
    return out;
  }
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = join(dir, entry.name);
    if (entry.isDirectory()) {
      walk(full, out);
    } else if (/\.(ts|tsx)$/.test(entry.name) && !entry.name.endsWith('.test.ts')) {
      out.push(full);
    }
  }
  return out;
}

describe('真实源码 API 边界守卫', () => {
  it('页面层源码无越界访问', () => {
    const failures: string[] = [];
    for (const dir of GUARDED_DIRS) {
      for (const file of walk(dir)) {
        const source = readFileSync(file, 'utf8');
        const violations = findApiBoundaryViolations(file, source);
        if (violations.length > 0) {
          failures.push(`${file}: ${violations.join('；')}`);
        }
      }
    }
    expect(failures).toEqual([]);
  });
});
