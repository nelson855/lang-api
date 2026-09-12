import { fireEvent, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { axe } from 'vitest-axe';
import { Button } from './Button';
import { renderWithLocale } from '../../test/render';

describe('Button 按钮', () => {
  it('四种外观与两种尺寸均可渲染操作', () => {
    const onClick = vi.fn();
    renderWithLocale(
      <>
        <Button variant="primary" onClick={onClick}>主要</Button>
        <Button variant="secondary">次要</Button>
        <Button variant="quiet">安静</Button>
        <Button variant="danger">危险</Button>
        <Button size="sm">小</Button>
      </>,
    );
    fireEvent.click(screen.getByRole('button', { name: '主要' }));
    expect(onClick).toHaveBeenCalledTimes(1);
    expect(screen.getByRole('button', { name: '安静' })).toBeInTheDocument();
  });

  it('加载时保持可访问名称并禁用重复点击', () => {
    const onClick = vi.fn();
    renderWithLocale(
      <Button loading onClick={onClick}>提交</Button>,
    );
    const button = screen.getByRole('button', { name: '提交' });
    expect(button).toBeDisabled();
    expect(button).toHaveAttribute('aria-busy', 'true');
    fireEvent.click(button);
    expect(onClick).not.toHaveBeenCalled();
  });

  it('无严重可访问性问题', async () => {
    const { container } = renderWithLocale(<Button variant="primary">主要</Button>);
    expect(await axe(container)).toHaveNoViolations();
  });
});
