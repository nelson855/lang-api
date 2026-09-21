import { fireEvent, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { useState } from 'react';
import { axe } from 'vitest-axe';
import { SearchField } from './SearchField';
import { renderWithLocale } from '../../test/render';

describe('SearchField 搜索字段', () => {
  it('非空时显示清除按钮，清空后焦点回到输入框', async () => {
    const user = userEvent.setup();
    function Fixture() {
      const [value, setValue] = useState('abc');
      return <SearchField aria-label="搜索" value={value} onChange={(event) => setValue(event.target.value)} />;
    }
    renderWithLocale(<Fixture />);
    const clear = screen.getByRole('button', { name: /清除|Clear/i });
    await user.click(clear);
    expect(screen.getByLabelText('搜索')).toHaveValue('');
    expect(document.activeElement).toBe(screen.getByLabelText('搜索'));
  });

  it('值为空时不渲染清除按钮', () => {
    renderWithLocale(<SearchField aria-label="搜索" value="" onChange={() => undefined} />);
    expect(screen.queryByRole('button', { name: /清除|Clear/i })).not.toBeInTheDocument();
  });

  it('值非空时清除按钮可访问名称正确', () => {
    renderWithLocale(<SearchField aria-label="搜索" value="x" onChange={() => undefined} />);
    expect(screen.getByRole('button', { name: /清除|Clear/i })).toBeInTheDocument();
  });

  it('本地即时筛选：受控 onChange 实时调用', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    function Fixture() {
      const [value, setValue] = useState('');
      return (
        <SearchField
          aria-label="搜索"
          value={value}
          onChange={(event) => {
            setValue(event.target.value);
            onChange(event.target.value);
          }}
        />
      );
    }
    renderWithLocale(<Fixture />);
    await user.type(screen.getByLabelText('搜索'), 'abc');
    expect(onChange).toHaveBeenCalled();
  });

  it('显式远程提交：回车触发 onSubmit', async () => {
    const user = userEvent.setup();
    const onSubmit = vi.fn();
    function Fixture() {
      const [value, setValue] = useState('');
      return (
        <SearchField
          aria-label="搜索"
          value={value}
          onChange={(event) => setValue(event.target.value)}
          onSubmit={onSubmit}
        />
      );
    }
    renderWithLocale(<Fixture />);
    await user.type(screen.getByLabelText('搜索'), 'abc{Enter}');
    expect(onSubmit).toHaveBeenCalledWith('abc');
  });

  it('IME 组合期间不触发 onSubmit', async () => {
    const onSubmit = vi.fn();
    renderWithLocale(
      <SearchField aria-label="搜索" value="" onChange={() => undefined} onSubmit={onSubmit} />,
    );
    const input = screen.getByLabelText('搜索');
    fireEvent.compositionStart(input);
    fireEvent.keyDown(input, { key: 'Enter' });
    expect(onSubmit).not.toHaveBeenCalled();
    fireEvent.compositionEnd(input);
  });

  it('禁用时不显示清除按钮也不可输入', () => {
    renderWithLocale(<SearchField aria-label="搜索" value="abc" onChange={() => undefined} disabled />);
    expect(screen.getByLabelText('搜索')).toBeDisabled();
    expect(screen.queryByRole('button', { name: /清除|Clear/i })).not.toBeInTheDocument();
  });

  it('无严重可访问性问题', async () => {
    const { container } = renderWithLocale(
      <SearchField aria-label="搜索" value="abc" onChange={() => undefined} />,
    );
    expect(await axe(container)).toHaveNoViolations();
  });
});
