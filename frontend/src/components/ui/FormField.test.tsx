import { fireEvent, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { axe } from 'vitest-axe';
import { FormField } from './FormField';
import { Input } from './Input';
import { Textarea } from './Textarea';
import { renderWithLocale } from '../../test/render';

describe('Input 与 FormField', () => {
  it('默认使用标准密度，并可显式切换为紧凑密度', () => {
    renderWithLocale(
      <>
        <Input aria-label="标准输入" />
        <Input aria-label="紧凑输入" density="compact" />
      </>,
    );
    expect(screen.getByRole('textbox', { name: '标准输入' })).toHaveClass('input-standard');
    expect(screen.getByRole('textbox', { name: '紧凑输入' })).toHaveClass('input-compact');
  });

  it('标签、说明、错误与字段自动关联', () => {
    renderWithLocale(
      <FormField label="昵称" description="2 到 20 个字符" error="昵称太短" required>
        <Input placeholder="请输入" />
      </FormField>,
    );
    const input = screen.getByRole('textbox', { name: /昵称/ });
    expect(input).toBeRequired();
    expect(input).toHaveAttribute('aria-invalid', 'true');
    const describedBy = input.getAttribute('aria-describedby') ?? '';
    expect(describedBy.split(' ').length).toBe(2);
    expect(screen.getByText('昵称太短')).toBeInTheDocument();
  });

  it('无错误时不呈现错误状态', () => {
    renderWithLocale(
      <FormField label="昵称">
        <Input />
      </FormField>,
    );
    expect(screen.getByRole('textbox', { name: '昵称' })).toHaveAttribute('aria-invalid', 'false');
  });

  it('可与 React Hook Form 和 Zod 组合校验', () => {
    const schema = z.object({ nickname: z.string().min(2, '昵称太短') });
    function Fixture() {
      const { register, handleSubmit, formState } = useForm<{ nickname: string }>({
        resolver: zodResolver(schema),
      });
      return (
        <form noValidate onSubmit={(event) => void handleSubmit(vi.fn())(event)}>
          <FormField label="昵称" error={formState.errors.nickname?.message}>
            <Input {...register('nickname')} />
          </FormField>
          <button type="submit">保存</button>
        </form>
      );
    }
    renderWithLocale(<Fixture />);
    fireEvent.click(screen.getByRole('button', { name: '保存' }));
    return screen.findByText('昵称太短').then((error) => {
      expect(error).toBeInTheDocument();
    });
  });

  it('无严重可访问性问题', async () => {
    const { container } = renderWithLocale(
      <FormField label="昵称" description="2 到 20 个字符" error="昵称太短" required>
        <Input />
      </FormField>,
    );
    expect(await axe(container)).toHaveNoViolations();
  });
});

describe('Textarea 多行输入', () => {
  it('默认 resize:none 且支持密度切换', () => {
    renderWithLocale(
      <>
        <Textarea aria-label="备注" />
        <Textarea aria-label="紧凑备注" density="compact" />
      </>,
    );
    expect(screen.getByRole('textbox', { name: '备注' })).toHaveClass('input-standard');
    expect(screen.getByRole('textbox', { name: '紧凑备注' })).toHaveClass('input-compact');
  });

  it('与 FormField 组合时错误可关联', () => {
    renderWithLocale(
      <FormField label="说明" error="说明过长">
        <Textarea />
      </FormField>,
    );
    const textarea = screen.getByRole('textbox', { name: '说明' });
    expect(textarea).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByText('说明过长')).toBeInTheDocument();
  });
});
