import { existsSync, readdirSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';

const SCANNED_DIRS = ['src/components', 'src/layouts', 'src/pages', 'src/features'];
const HEX_PATTERN = /#[0-9a-fA-F]{3,8}\b/;
const SHADOW_PATTERN = /(box-shadow|text-shadow)\s*:/;

function walk(dir: string, out: string[] = []): string[] {
  if (!existsSync(dir)) {
    return out;
  }
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = join(dir, entry.name);
    if (entry.isDirectory()) {
      walk(full, out);
    } else if (/\.(ts|tsx)$/.test(entry.name) && !entry.name.includes('.test.')) {
      out.push(full);
    }
  }
  return out;
}

describe('组件样式纪律守卫', () => {
  it('组件层不散落十六进制颜色与自定义阴影', () => {
    const failures: string[] = [];
    for (const dir of SCANNED_DIRS) {
      for (const file of walk(join(process.cwd(), dir))) {
        const source = readFileSync(file, 'utf8');
        if (HEX_PATTERN.test(source)) {
          failures.push(`${file}: 发现十六进制颜色，请改用设计变量`);
        }
        if (SHADOW_PATTERN.test(source)) {
          failures.push(`${file}: 发现自定义阴影，请改用设计变量`);
        }
      }
    }
    expect(failures).toEqual([]);
  });
});
