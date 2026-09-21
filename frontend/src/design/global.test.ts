import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';

const globalCss = readFileSync(join(process.cwd(), 'src/design/global.css'), 'utf8');
const tokensCss = readFileSync(join(process.cwd(), 'src/design/tokens.css'), 'utf8');

describe('Clear Circuit 全局基础', () => {
  it('页面底色为冷调画布且加载无旧主题闪烁', () => {
    expect(globalCss).toMatch(/color-scheme:\s*light/);
    expect(globalCss).toMatch(/background:\s*var\(--color-bg\)/);
    expect(globalCss.toLowerCase()).not.toMatch(/#0d1117|#fbfaf7/);
  });

  it('链接、选区与焦点使用 Cobalt 语义', () => {
    expect(globalCss).toMatch(/::selection/);
    expect(globalCss).toMatch(/:focus-visible/);
  });

  it('全局提供可见浅色滚动条、稳定占位与 forced-colors 回退', () => {
    expect(globalCss).toMatch(/scrollbar-color/);
    expect(globalCss).toMatch(/scrollbar-width/);
    expect(globalCss).toMatch(/::-webkit-scrollbar/);
    expect(globalCss).toMatch(/::-webkit-scrollbar-thumb:hover/);
    expect(globalCss).toMatch(/::-webkit-scrollbar-thumb:active/);
    expect(globalCss).toMatch(/forced-colors:\s*active/);
  });

  it('减少动效时取消位移与缩放只保留即时状态', () => {
    expect(globalCss).toMatch(/prefers-reduced-motion:\s*reduce/);
    expect(globalCss).toMatch(/animation-duration:\s*0\.01ms/);
    expect(globalCss).toMatch(/transition-duration:\s*0\.01ms/);
  });

  it('本地字体与站内资源，无远程字体', () => {
    expect(`${tokensCss}\n${globalCss}`).not.toMatch(/https?:\/\//);
    expect(`${tokensCss}\n${globalCss}`).not.toMatch(/fonts\.googleapis|fonts\.gstatic/);
  });
});
