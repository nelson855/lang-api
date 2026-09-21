import { fireEvent, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { axe } from 'vitest-axe';
import { CodeBlock } from './CodeBlock';
import { renderWithLocale } from '../test/render';

describe('CodeBlock 代码块', () => {
  it('使用等宽字体并携带语言标记', () => {
    renderWithLocale(<CodeBlock code="echo hello" language="bash" />);
    const code = screen.getByText('echo hello');
    expect(code.tagName).toBe('CODE');
    expect(code).toHaveAttribute('data-language', 'bash');
  });

  it('长代码块横向滚动由 CSS 类承担而非内联样式', () => {
    const { container } = renderWithLocale(<CodeBlock code={'x'.repeat(500)} language="text" />);
    const pre = container.querySelector('pre');
    expect(pre).not.toBeNull();
    expect(pre?.className).toContain('code-block-pre');
    expect(pre?.getAttribute('style')).toBeNull();
  });

  it('复制按钮可用，成功后显示提示', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.assign(navigator, { clipboard: { writeText } });
    renderWithLocale(<CodeBlock code="abc" language="text" />);
    fireEvent.click(screen.getByRole('button', { name: /复制|Copy/i }));
    await screen.findByText(/已复制|Copied/i);
    expect(writeText).toHaveBeenCalledWith('abc');
  });

  it('禁用原因存在时按钮禁用且不发起复制', () => {
    const writeText = vi.fn();
    Object.assign(navigator, { clipboard: { writeText } });
    renderWithLocale(<CodeBlock code="abc" language="text" disabledReason="协议未开放" />);
    const button = screen.getByRole('button', { name: /复制|Copy/i });
    expect(button).toBeDisabled();
    fireEvent.click(button);
    expect(writeText).not.toHaveBeenCalled();
    expect(screen.getByText('协议未开放')).toBeInTheDocument();
  });

  it('无严重可访问性问题', async () => {
    const { container } = renderWithLocale(<CodeBlock code="echo hello" language="bash" />);
    expect(await axe(container)).toHaveNoViolations();
  });
});
