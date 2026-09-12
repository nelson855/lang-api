import { fireEvent, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { axe } from 'vitest-axe';
import { Empty, Forbidden, Loading, NotFound, RetryableError } from './Feedback';
import { renderWithLocale } from '../../test/render';

describe('通用页面状态', () => {
  it('加载态使用状态语义而不转圈无说明', () => {
    renderWithLocale(<Loading />);
    expect(screen.getByRole('status')).toHaveTextContent('加载中');
  });

  it('空态说明下一步而不伪造数据', () => {
    renderWithLocale(<Empty title="暂无模型" description="接入后显示真实模型" />);
    expect(screen.getByText('暂无模型')).toBeInTheDocument();
  });

  it('可重试错误展示 requestId 并可重试', () => {
    const onRetry = vi.fn();
    renderWithLocale(<RetryableError requestId="req-3" onRetry={onRetry} />);
    expect(screen.getByText(/req-3/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '重试' }));
    expect(onRetry).toHaveBeenCalledTimes(1);
  });

  it('无权限与 404 语义不同且可返回安全页', () => {
    const { unmount } = renderWithLocale(<Forbidden />);
    expect(screen.getByText('无权限')).toBeInTheDocument();
    unmount();
    renderWithLocale(<NotFound />, 'en-US');
    expect(screen.getByRole('link', { name: 'Back to home' })).toHaveAttribute('href', '/');
  });

  it('无严重可访问性问题', async () => {
    const { container } = renderWithLocale(
      <>
        <Loading />
        <Empty title="空" />
        <RetryableError onRetry={() => {}} />
        <Forbidden />
        <NotFound />
      </>,
    );
    expect(await axe(container)).toHaveNoViolations();
  });
});
