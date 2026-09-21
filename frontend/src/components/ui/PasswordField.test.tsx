import { fireEvent, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { axe } from 'vitest-axe';
import { useState } from 'react';
import { PasswordField, SecretField } from './PasswordField';
import { renderWithLocale } from '../../test/render';

describe('PasswordField 密码字段', () => {
  it('默认遮蔽输入，切换按钮可访问名不含字段标签词', () => {
    renderWithLocale(<PasswordField aria-label="登录密码" />);
    const input = screen.getByLabelText('登录密码');
    expect(input).toHaveAttribute('type', 'password');
    const toggle = screen.getByRole('button', { name: /显示输入内容|Show input content/i });
    expect(toggle).toBeInTheDocument();
  });

  it('点击切换显示/隐藏，类型与按钮名同步变化', () => {
    renderWithLocale(<PasswordField aria-label="登录密码" />);
    const input = screen.getByLabelText('登录密码');
    fireEvent.click(screen.getByRole('button', { name: /显示输入内容|Show input content/i }));
    expect(input).toHaveAttribute('type', 'text');
    expect(screen.getByRole('button', { name: /遮蔽输入内容|Hide input content/i })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /遮蔽输入内容|Hide input content/i }));
    expect(input).toHaveAttribute('type', 'password');
  });

  it('切换时保留输入值与焦点', () => {
    function Fixture() {
      const [value, setValue] = useState('abc12345');
      return <PasswordField aria-label="登录密码" value={value} onChange={(event) => setValue(event.target.value)} />;
    }
    renderWithLocale(<Fixture />);
    const input = screen.getByLabelText('登录密码') as HTMLInputElement;
    input.focus();
    fireEvent.click(screen.getByRole('button', { name: /显示输入内容|Show input content/i }));
    expect(input.value).toBe('abc12345');
    expect(document.activeElement).toBe(input);
  });

  it('透传 autocomplete 与其他 input 属性', () => {
    renderWithLocale(
      <PasswordField
        aria-label="新密码"
        autoComplete="new-password"
        minLength={8}
        required
        name="newPassword"
      />,
    );
    const input = screen.getByLabelText('新密码');
    expect(input).toHaveAttribute('autocomplete', 'new-password');
    expect(input).toHaveAttribute('minlength', '8');
    expect(input).toBeRequired();
    expect(input).toHaveAttribute('name', 'newPassword');
  });

  it('无严重可访问性问题', async () => {
    const { container } = renderWithLocale(<PasswordField aria-label="登录密码" />);
    expect(await axe(container)).toHaveNoViolations();
  });
});

describe('SecretField 密钥字段', () => {
  it('默认遮蔽值且输入禁用', () => {
    renderWithLocale(<SecretField aria-label="API 密钥" value="sk-abc-123" readOnly />);
    const input = screen.getByLabelText('API 密钥') as HTMLInputElement;
    expect(input).toHaveAttribute('type', 'password');
    expect(input.value).toBe('sk-abc-123');
    expect(input).toHaveAttribute('readonly');
  });

  it('点击切换可见性，按钮名与字段标签词分离', () => {
    renderWithLocale(<SecretField aria-label="API 密钥" value="sk-abc-123" readOnly />);
    const input = screen.getByLabelText('API 密钥') as HTMLInputElement;
    fireEvent.click(screen.getByRole('button', { name: /显示密钥内容|Show secret content/i }));
    expect(input).toHaveAttribute('type', 'text');
    fireEvent.click(screen.getByRole('button', { name: /遮蔽密钥内容|Hide secret content/i }));
    expect(input).toHaveAttribute('type', 'password');
  });
});
