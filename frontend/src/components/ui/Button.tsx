import type { ButtonHTMLAttributes } from 'react';
import './Button.css';

export type ButtonVariant = 'primary' | 'secondary' | 'quiet' | 'danger';
export type ButtonSize = 'sm' | 'md';

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  size?: ButtonSize;
  loading?: boolean;
}

export function Button({
  variant = 'primary',
  size = 'md',
  loading = false,
  disabled = false,
  type = 'button',
  children,
  ...rest
}: ButtonProps) {
  const inactive = disabled || loading;
  return (
    <button
      type={type}
      className={`btn btn-${variant} btn-${size}`}
      disabled={inactive}
      aria-busy={loading || undefined}
      {...rest}
    >
      {loading ? (
        <span className="btn-spinner" aria-hidden="true" />
      ) : null}
      <span className="btn-label">{children}</span>
    </button>
  );
}
