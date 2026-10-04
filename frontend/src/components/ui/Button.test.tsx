import { fireEvent, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { axe } from 'vitest-axe';
import { Button, IconButton } from './Button';
import { renderWithLocale } from '../../test/render';

describe('Button 按钮', () => {
  it('四种外观与两种尺寸均可渲染操作', () => {
    const onClick = vi.fn();
    renderWithLocale(
      <>
        <Button variant="primary" onClick={onClick}>主要</Button>
        <Button variant="secondary" onClick={onClick}>次要</Button>
        <Button variant="quiet" onClick={onClick}>安静</Button>
        <Button variant="danger" onClick={onClick}>危险</Button>
        <Button size="sm" onClick={onClick}>小</Button>
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

  it('busy 别名与 loading 等价', () => {
    renderWithLocale(<Button busy type="button" onClick={() => undefined}>保存</Button>);
    const button = screen.getByRole('button', { name: '保存' });
    expect(button).toBeDisabled();
    expect(button).toHaveAttribute('aria-busy', 'true');
  });

  it('新 API：intent × emphasis 可组合', () => {
    renderWithLocale(
      <>
        <Button intent="primary" emphasis="solid" type="button" onClick={() => undefined}>主操作</Button>
        <Button intent="danger" emphasis="outline" type="button" onClick={() => undefined}>危险描边</Button>
        <Button intent="neutral" emphasis="ghost" type="button" onClick={() => undefined}>幽灵</Button>
      </>,
    );
    expect(screen.getByRole('button', { name: '主操作' })).toHaveClass('btn-intent-primary', 'btn-emphasis-solid');
    expect(screen.getByRole('button', { name: '危险描边' })).toHaveClass('btn-intent-danger', 'btn-emphasis-outline');
    expect(screen.getByRole('button', { name: '幽灵' })).toHaveClass('btn-intent-neutral', 'btn-emphasis-ghost');
  });

  it('旧 variant 兼容映射到新 intent/emphasis', () => {
    renderWithLocale(
      <>
        <Button variant="primary" type="button" onClick={() => undefined}>V主</Button>
        <Button variant="secondary" type="button" onClick={() => undefined}>V次</Button>
        <Button variant="quiet" type="button" onClick={() => undefined}>V幽</Button>
        <Button variant="danger" type="button" onClick={() => undefined}>V危</Button>
      </>,
    );
    expect(screen.getByRole('button', { name: 'V主' })).toHaveClass('btn-intent-primary', 'btn-emphasis-solid');
    expect(screen.getByRole('button', { name: 'V次' })).toHaveClass('btn-intent-neutral', 'btn-emphasis-outline');
    expect(screen.getByRole('button', { name: 'V幽' })).toHaveClass('btn-intent-primary', 'btn-emphasis-ghost');
    expect(screen.getByRole('button', { name: 'V危' })).toHaveClass('btn-intent-danger', 'btn-emphasis-outline');
  });

  it('disabled 时不响应点击', () => {
    const onClick = vi.fn();
    renderWithLocale(<Button disabled onClick={onClick}>禁用</Button>);
    const button = screen.getByRole('button', { name: '禁用' });
    expect(button).toBeDisabled();
    fireEvent.click(button);
    expect(onClick).not.toHaveBeenCalled();
  });

  it('无严重可访问性问题', async () => {
    const { container } = renderWithLocale(<Button variant="primary" onClick={() => undefined}>主要</Button>);
    expect(await axe(container)).toHaveNoViolations();
  });
});

describe('IconButton 图标按钮', () => {
  it('要求可访问名称且稳定方形尺寸', () => {
    const onClick = vi.fn();
    renderWithLocale(
      <IconButton aria-label="关闭" onClick={onClick}>
        <span aria-hidden="true">×</span>
      </IconButton>,
    );
    const button = screen.getByRole('button', { name: '关闭' });
    expect(button).toHaveClass('btn-icon');
    fireEvent.click(button);
    expect(onClick).toHaveBeenCalledTimes(1);
  });

  it('支持 sm/md 尺寸与 intent 切换', () => {
    renderWithLocale(
      <>
        <IconButton aria-label="小" size="sm">×</IconButton>
        <IconButton aria-label="危险" intent="danger">!</IconButton>
      </>,
    );
    expect(screen.getByRole('button', { name: '小' })).toHaveClass('btn-icon-sm');
    expect(screen.getByRole('button', { name: '危险' })).toHaveClass('btn-intent-danger');
  });

  it('图标按钮无名称时可访问性检查失败', async () => {
    const { container } = renderWithLocale(
      <IconButton aria-label="ok">×</IconButton>,
    );
    expect(await axe(container)).toHaveNoViolations();
  });
});
