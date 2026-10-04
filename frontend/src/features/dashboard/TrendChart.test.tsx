import { screen } from '@testing-library/react';
import { describe, expect, it, vi, afterEach } from 'vitest';
import { renderWithLocale } from '../../test/render';
import { TrendChart, type TrendPointInput } from './TrendChart';

const points: TrendPointInput[] = [
  { bucketStart: '2026-09-01T00:00:00Z', bucketEnd: '2026-09-01T01:00:00Z', value: '10' },
  { bucketStart: '2026-09-01T01:00:00Z', bucketEnd: '2026-09-01T02:00:00Z', value: '20' },
];

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('TrendChart', () => {
  it('有点时渲染 SVG、描述与文本数据表替代', () => {
    renderWithLocale(
      <TrendChart
        title="请求趋势"
        description="按小时的趋势数据"
        points={points}
        timezone="UTC"
        locale="zh-CN"
      />,
    );
    expect(screen.getByText('请求趋势')).toBeInTheDocument();
    expect(document.querySelector('svg')).not.toBeNull();
    // 数据表提供每点的可读替代
    expect(screen.getByRole('table')).toBeInTheDocument();
    expect(screen.getByText('10')).toBeInTheDocument();
  });

  it('横轴标签按用户时区格式化，不显示原始 UTC 字符串', () => {
    renderWithLocale(
      <TrendChart
        title="请求趋势"
        description="趋势数据替代"
        points={points}
        timezone="Asia/Shanghai"
        locale="zh-CN"
      />,
    );
    // 图表说明中出现本地时区时间（08:00 而非 00:00Z）
    expect(screen.getAllByText(/08:00|09:00/).length).toBeGreaterThan(0);
  });

  it('空点数组时不挂载 SVG，直接显示说明', () => {
    renderWithLocale(
      <TrendChart title="请求趋势" description="该范围暂无趋势数据" points={[]} timezone="UTC" locale="zh-CN" />,
    );
    expect(document.querySelector('svg')).toBeNull();
    expect(screen.getByText('该范围暂无趋势数据')).toBeInTheDocument();
  });

  it('reduced-motion 下不执行路径动画', () => {
    vi.stubGlobal('matchMedia', (query: string) => ({
      matches: query.includes('prefers-reduced-motion: reduce'),
      media: query,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      addListener: vi.fn(),
      removeListener: vi.fn(),
      onchange: null,
    }));
    renderWithLocale(
      <TrendChart title="请求趋势" description="趋势数据" points={points} timezone="UTC" locale="zh-CN" />,
    );
    const svg = document.querySelector('svg');
    expect(svg).not.toBeNull();
    expect(svg?.className.baseVal).not.toContain('trend-animate');
  });
});