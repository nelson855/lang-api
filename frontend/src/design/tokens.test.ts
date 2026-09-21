import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';

const tokensCss = readFileSync(join(process.cwd(), 'src/design/tokens.css'), 'utf8');
const globalCss = readFileSync(join(process.cwd(), 'src/design/global.css'), 'utf8');

function variablesOf(css: string): Map<string, string> {
  const out = new Map<string, string>();
  for (const match of css.matchAll(/--([\w-]+)\s*:\s*([^;]+);/g)) {
    out.set(match[1], match[2].trim());
  }
  return out;
}

function hexOf(vars: Map<string, string>, name: string): string {
  const value = vars.get(name);
  if (!value) {
    throw new Error(`缺失变量 --${name}`);
  }
  const hex = value.match(/#[0-9a-fA-F]{6}\b/)?.[0];
  if (!hex) {
    throw new Error(`变量 --${name} 不是直接十六进制色: ${value}`);
  }
  return hex;
}

function luminance(hex: string): number {
  const channels = [1, 3, 5].map((i) => {
    const channel = parseInt(hex.slice(i, i + 2), 16) / 255;
    return channel <= 0.03928 ? channel / 12.92 : ((channel + 0.055) / 1.055) ** 2.4;
  });
  return 0.2126 * channels[0] + 0.7152 * channels[1] + 0.0722 * channels[2];
}

function contrastRatio(a: string, b: string): number {
  const [lighter, darker] = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return (lighter + 0.05) / (darker + 0.05);
}

const TEXT_PAIRS: Array<[string, string]> = [
  ['color-text', 'color-bg'],
  ['color-text-muted', 'color-bg'],
  ['color-primary-contrast', 'color-primary'],
  ['color-accent', 'color-bg'],
  ['color-success-text', 'color-bg'],
  ['color-warning-text', 'color-bg'],
  ['color-danger-text', 'color-bg'],
];

describe('Clear Circuit 核心色与语义', () => {
  it('定义六个核心 primitive 角色', () => {
    const vars = variablesOf(tokensCss);
    expect(hexOf(vars, 'primitive-canvas-50').toUpperCase()).toBe('#F4F7FB');
    expect(hexOf(vars, 'primitive-paper-0').toUpperCase()).toBe('#FFFFFF');
    expect(hexOf(vars, 'primitive-mist-100').toUpperCase()).toBe('#E9EEF5');
    expect(hexOf(vars, 'primitive-mist-200').toUpperCase()).toBe('#D7DEE9');
    expect(hexOf(vars, 'primitive-graphite-900').toUpperCase()).toBe('#182235');
    expect(hexOf(vars, 'primitive-cobalt-600').toUpperCase()).toBe('#315BE8');
  });

  it('语义色 info/success/warning/danger 独立且不复用 Cobalt', () => {
    const vars = variablesOf(tokensCss);
    for (const name of ['color-info-text', 'color-success-text', 'color-warning-text', 'color-danger-text']) {
      expect(vars.has(name), `缺失语义变量 --${name}`).toBe(true);
      expect(hexOf(vars, name), `${name} 不应复用 Cobalt 主色`).not.toBe('#315BE8');
    }
    expect(hexOf(vars, 'color-info-text').toUpperCase()).not.toBe(hexOf(vars, 'color-success-text').toUpperCase());
    expect(hexOf(vars, 'color-warning-text').toUpperCase()).not.toBe(hexOf(vars, 'color-danger-text').toUpperCase());
  });

  it('具备统一 z-index 层级与动效令牌', () => {
    const vars = variablesOf(tokensCss);
    for (const name of ['z-sticky', 'z-dropdown', 'z-popover', 'z-header', 'z-backdrop', 'z-dialog', 'z-drawer', 'z-toast']) {
      expect(vars.has(name), `缺失层级变量 --${name}`).toBe(true);
    }
    const micro = vars.get('motion-micro') ?? vars.get('duration-fast') ?? '';
    expect(micro, '缺少微动效时长令牌').toMatch(/^\d+ms$/);
    const microMs = parseInt(micro, 10);
    expect(microMs).toBeGreaterThanOrEqual(150);
    expect(microMs).toBeLessThanOrEqual(250);
  });
});

describe('设计变量三层结构', () => {
  it('采用 Clear Circuit 浅色语义基线', () => {
    const vars = variablesOf(tokensCss);
    expect(hexOf(vars, 'color-bg')).toBe('#F4F7FB');
    expect(hexOf(vars, 'color-surface')).toBe('#FFFFFF');
    expect(hexOf(vars, 'color-surface-raised')).toBe('#E9EEF5');
    expect(hexOf(vars, 'color-primary')).toBe('#315BE8');
    expect(globalCss).toMatch(/color-scheme:\s*light/);
  });

  it('具备 primitive、semantic、component 三层命名', () => {
    expect(tokensCss).toMatch(/--primitive-/);
    expect(tokensCss).toMatch(/--color-/);
    expect(tokensCss).toMatch(/--radius-(sm|md|lg)/);
    const vars = variablesOf(tokensCss);
    for (const name of ['color-bg', 'color-text', 'color-primary', 'color-border', 'color-focus']) {
      expect(vars.has(name), `缺失语义变量 --${name}`).toBe(true);
    }
  });

  it('使用 6/10/16 三档圆角与克制阴影', () => {
    const vars = variablesOf(tokensCss);
    expect(vars.get('radius-sm')).toContain('6px');
    expect(vars.get('radius-md')).toContain('10px');
    expect(vars.get('radius-lg')).toContain('16px');
  });

  it('定义标准与紧凑两种控件密度', () => {
    const vars = variablesOf(tokensCss);
    expect(vars.get('control-height-md')).toBe('40px');
    expect(vars.get('control-height-sm')).toBe('36px');
  });

  it('只引用本地字体与站内资源', () => {
    expect(`${tokensCss}\n${globalCss}`).not.toMatch(/https?:\/\//);
    expect(`${tokensCss}\n${globalCss}`).not.toMatch(/fonts\.googleapis|fonts\.gstatic/);
    expect(globalCss).toMatch(/@fontsource-variable\/manrope/);
    expect(globalCss).toMatch(/@fontsource-variable\/jetbrains-mono/);
  });

  it('正文与控件对比度达到 WCAG AA', () => {
    const vars = variablesOf(tokensCss + globalCss);
    for (const [fore, back] of TEXT_PAIRS) {
      const ratio = contrastRatio(hexOf(vars, fore), hexOf(vars, back));
      expect(ratio, `${fore} on ${back} 对比度 ${ratio.toFixed(2)}`).toBeGreaterThanOrEqual(4.5);
    }
  });

  it('具备响应式容器与断点规则', () => {
    expect(globalCss).toMatch(/1180px/);
    expect(globalCss).toMatch(/768px/);
    expect(globalCss).toMatch(/16px/);
    expect(globalCss).toMatch(/overflow-x:\s*auto/);
  });

  it('具备可见焦点与减少动效规则', () => {
    expect(globalCss).toMatch(/:focus-visible/);
    expect(globalCss).toMatch(/prefers-reduced-motion:\s*reduce/);
  });
});
