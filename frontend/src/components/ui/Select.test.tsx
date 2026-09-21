import { screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { axe } from 'vitest-axe';
import { Select, type SelectOption } from './Select';
import { renderWithLocale } from '../../test/render';

const FRUITS: SelectOption[] = [
  { value: 'apple', label: '苹果' },
  { value: 'banana', label: '香蕉' },
  { value: 'cherry', label: '樱桃' },
];

describe('Select 单选下拉（jsdom 覆盖范围）', () => {
  it('值映射正确，触发器显示当前 label', () => {
    renderWithLocale(
      <Select aria-label="水果" options={FRUITS} value="banana" onValueChange={() => undefined} />,
    );
    expect(screen.getByRole('combobox', { name: '水果' })).toHaveTextContent('香蕉');
  });

  it('aria-label 与触发器关联', () => {
    renderWithLocale(
      <Select aria-label="水果" options={FRUITS} value="apple" onValueChange={() => undefined} />,
    );
    expect(screen.getByRole('combobox', { name: '水果' })).toBeInTheDocument();
  });

  it('禁用时触发器带 data-disabled 且不可聚焦操作', () => {
    renderWithLocale(
      <Select aria-label="水果" options={FRUITS} value="apple" onValueChange={() => undefined} disabled />,
    );
    const trigger = screen.getByRole('combobox', { name: '水果' });
    expect(trigger).toBeDisabled();
    expect(trigger).toHaveAttribute('data-disabled');
  });

  it('选项支持禁用标记', () => {
    renderWithLocale(
      <Select
        aria-label="水果"
        options={[
          { value: 'apple', label: '苹果' },
          { value: 'banana', label: '香蕉', disabled: true },
        ]}
        value="apple"
        onValueChange={() => undefined}
      />,
    );
    expect(screen.getByRole('combobox', { name: '水果' })).toBeInTheDocument();
  });

  it('placeholder 在无值时显示', () => {
    renderWithLocale(
      <Select aria-label="水果" options={FRUITS} value="" onValueChange={() => undefined} placeholder="请选择" />,
    );
    expect(screen.getByRole('combobox', { name: '水果' })).toHaveTextContent('请选择');
  });

  it('onValueChange 回调签名正确', () => {
    const onValueChange = vi.fn();
    renderWithLocale(
      <Select aria-label="水果" options={FRUITS} value="apple" onValueChange={onValueChange} />,
    );
    expect(screen.getByRole('combobox', { name: '水果' })).toBeInTheDocument();
    expect(onValueChange).not.toHaveBeenCalled();
  });

  it('无严重可访问性问题', async () => {
    const { container } = renderWithLocale(
      <Select aria-label="水果" options={FRUITS} value="apple" onValueChange={() => undefined} />,
    );
    expect(await axe(container)).toHaveNoViolations();
  });
});
