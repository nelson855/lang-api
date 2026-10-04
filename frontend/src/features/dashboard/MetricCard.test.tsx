import { screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { renderWithLocale } from '../../test/render';
import type { MetricDisplayStatus } from './displayModel';
import { MetricCard } from './MetricCard';

function value(text: string): MetricDisplayStatus {
  return { status: 'value', text };
}

describe('MetricCard', () => {
  it('显示真实值文案', () => {
    renderWithLocale(<MetricCard label="Token 用量" display={value('90')} />);
    expect(screen.getByText('Token 用量')).toBeInTheDocument();
    expect(screen.getByText('90')).toBeInTheDocument();
  });

  it('no-data 显示本地化说明而非零值', () => {
    renderWithLocale(<MetricCard label="平均延迟" display={{ status: 'no-data' }} />);
    expect(screen.getByText(/暂无数据/)).toBeInTheDocument();
  });

  it('BASELINE_NOT_VERIFIED 显示不可用原因', () => {
    renderWithLocale(
      <MetricCard label="请求总数" display={{ status: 'unavailable', reason: 'BASELINE_NOT_VERIFIED' }} />,
    );
    expect(screen.getByText(/基线尚未验证/)).toBeInTheDocument();
  });

  it('SOURCE_FIELD_MISSING 显示不可用原因', () => {
    renderWithLocale(
      <MetricCard label="成功率" display={{ status: 'unavailable', reason: 'SOURCE_FIELD_MISSING' }} />,
    );
    expect(screen.getByText(/来源字段缺失/)).toBeInTheDocument();
  });
});