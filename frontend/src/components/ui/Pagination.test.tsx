import { fireEvent, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { Pagination } from './Pagination';
import { renderWithLocale } from '../../test/render';

describe('Pagination 分页', () => {
  it('第一页禁用上一页，末页禁用下一页', () => {
    const onChange = vi.fn();
    const { rerender } = renderWithLocale(
      <Pagination page={1} pageSize={10} total={95} onChange={onChange} />,
    );
    expect(screen.getByRole('button', { name: '上一页' })).toBeDisabled();
    fireEvent.click(screen.getByRole('button', { name: '下一页' }));
    expect(onChange).toHaveBeenCalledWith(2);
    rerender(<Pagination page={10} pageSize={10} total={95} onChange={onChange} />);
    expect(screen.getByRole('button', { name: '下一页' })).toBeDisabled();
  });

  it('零记录时不产生无效页码请求', () => {
    const onChange = vi.fn();
    renderWithLocale(<Pagination page={1} pageSize={10} total={0} onChange={onChange} />);
    fireEvent.click(screen.getByRole('button', { name: '下一页' }));
    fireEvent.click(screen.getByRole('button', { name: '上一页' }));
    expect(onChange).not.toHaveBeenCalled();
  });

  it('非法参数被钳制且不回调越界页码', () => {
    const onChange = vi.fn();
    renderWithLocale(<Pagination page={99} pageSize={10} total={30} onChange={onChange} />);
    expect(screen.getByText(/第 3 \/ 3 页/)).toBeInTheDocument();
    expect(onChange).not.toHaveBeenCalled();
  });
});
