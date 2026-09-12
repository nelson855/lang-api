import { act, fireEvent, screen } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { ToastProvider, useToast } from './Toast';
import { renderWithLocale } from '../../test/render';

function Trigger() {
  const toast = useToast();
  return (
    <>
      <button type="button" onClick={() => toast.success('保存成功')}>
        成功
      </button>
      <button
        type="button"
        onClick={() => toast.error({ message: '加载失败', requestId: 'req-7' })}
      >
        失败
      </button>
      <button type="button" onClick={() => toast.error({ message: new Error('原始异常') })}>
        异常对象
      </button>
    </>
  );
}

describe('Toast 通知', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it('成功通知出现在 live region 并可手动关闭', () => {
    renderWithLocale(
      <ToastProvider>
        <Trigger />
      </ToastProvider>,
    );
    fireEvent.click(screen.getByRole('button', { name: '成功' }));
    const region = screen.getByRole('status');
    expect(region).toHaveTextContent('保存成功');
    fireEvent.click(screen.getByRole('button', { name: '关闭' }));
    expect(screen.queryByText('保存成功')).toBeNull();
  });

  it('错误通知显示 requestId 且不渲染原始异常', () => {
    renderWithLocale(
      <ToastProvider>
        <Trigger />
      </ToastProvider>,
    );
    fireEvent.click(screen.getByRole('button', { name: '失败' }));
    expect(screen.getByText(/req-7/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '异常对象' }));
    expect(document.body.textContent).not.toMatch(/原始异常/);
  });

  it('超时后自动关闭', () => {
    renderWithLocale(
      <ToastProvider>
        <Trigger />
      </ToastProvider>,
    );
    fireEvent.click(screen.getByRole('button', { name: '成功' }));
    expect(screen.getByText('保存成功')).toBeInTheDocument();
    act(() => {
      vi.advanceTimersByTime(6000);
    });
    expect(screen.queryByText('保存成功')).toBeNull();
  });
});
