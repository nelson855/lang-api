import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { CopyModelId } from './CopyModelId';
import { renderWithLocale } from '../../test/render';

function setClipboard(value: Clipboard | undefined) {
  Object.defineProperty(navigator, 'clipboard', { value, configurable: true, writable: true });
}

function workingClipboard() {
  return { writeText: vi.fn().mockResolvedValue(undefined) } as unknown as Clipboard;
}

describe('复制原始模型 ID', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('复制的是原始 ID 而非引用或显示名', async () => {
    const clipboard = workingClipboard();
    setClipboard(clipboard);
    renderWithLocale(<CopyModelId modelId="中文/模型 v1" />);

    await userEvent.click(screen.getByRole('button', { name: '复制模型 ID' }));

    await waitFor(() => expect(clipboard.writeText).toHaveBeenCalledWith('中文/模型 v1'));
    expect(await screen.findByRole('status')).toHaveTextContent('已复制模型 ID');
  });

  it('剪贴板缺失时就地提示失败并保留完整 ID', async () => {
    setClipboard(undefined);
    renderWithLocale(<CopyModelId modelId="abc" />);

    await userEvent.click(screen.getByRole('button', { name: '复制模型 ID' }));

    expect(await screen.findByRole('status')).toHaveTextContent('复制失败，请手动选择并复制完整模型 ID');
    expect(screen.getByText('abc')).toBeInTheDocument();
  });

  it('剪贴板拒绝时提示失败而不是静默', async () => {
    const clipboard = { writeText: vi.fn().mockRejectedValue(new Error('denied')) } as unknown as Clipboard;
    setClipboard(clipboard);
    renderWithLocale(<CopyModelId modelId="abc" />);

    await userEvent.click(screen.getByRole('button', { name: '复制模型 ID' }));

    expect(await screen.findByRole('status')).toHaveTextContent('复制失败，请手动选择并复制完整模型 ID');
    expect(clipboard.writeText).toHaveBeenCalled();
  });

  it('失败提示可由 aria 感知且不使用浏览器 alert', async () => {
    const alertSpy = vi.spyOn(window, 'alert').mockImplementation(() => {});
    setClipboard(undefined);
    renderWithLocale(<CopyModelId modelId="abc" />);
    await userEvent.click(screen.getByRole('button', { name: '复制模型 ID' }));
    await screen.findByRole('status');
    expect(alertSpy).not.toHaveBeenCalled();
  });
});
