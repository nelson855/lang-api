import type { ButtonHTMLAttributes, ReactNode } from 'react';
import './Button.css';

export type ButtonVariant = 'primary' | 'secondary' | 'quiet' | 'danger';
export type ButtonIntent = 'primary' | 'neutral' | 'danger';
export type ButtonEmphasis = 'solid' | 'outline' | 'ghost';
export type ButtonSize = 'sm' | 'md';

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  intent?: ButtonIntent;
  emphasis?: ButtonEmphasis;
  size?: ButtonSize;
  loading?: boolean;
  busy?: boolean;
}

function resolveIntentEmphasis(
  variant: ButtonVariant | undefined,
  intent: ButtonIntent | undefined,
  emphasis: ButtonEmphasis | undefined,
): { intent: ButtonIntent; emphasis: ButtonEmphasis } {
  if (intent || emphasis) {
    return {
      intent: intent ?? 'primary',
      emphasis: emphasis ?? 'solid',
    };
  }
  switch (variant) {
    case 'secondary':
      return { intent: 'neutral', emphasis: 'outline' };
    case 'quiet':
      return { intent: 'primary', emphasis: 'ghost' };
    case 'danger':
      return { intent: 'danger', emphasis: 'outline' };
    case 'primary':
    default:
      return { intent: 'primary', emphasis: 'solid' };
  }
}

export function Button({
  variant,
  intent,
  emphasis,
  size = 'md',
  loading = false,
  busy = false,
  disabled = false,
  type = 'button',
  children,
  className,
  ...rest
}: ButtonProps) {
  const isBusy = loading || busy;
  const inactive = disabled || isBusy;
  const resolved = resolveIntentEmphasis(variant, intent, emphasis);
  const classes = [
    'btn',
    `btn-${size}`,
    `btn-intent-${resolved.intent}`,
    `btn-emphasis-${resolved.emphasis}`,
    className ?? '',
  ]
    .filter(Boolean)
    .join(' ');
  return (
    <button
      type={type}
      className={classes}
      disabled={inactive}
      aria-busy={isBusy || undefined}
      {...rest}
    >
      {isBusy ? (
        <span className="btn-spinner" aria-hidden="true" />
      ) : null}
      <span className="btn-label">{children}</span>
    </button>
  );
}

export interface IconButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  intent?: ButtonIntent;
  size?: ButtonSize;
  loading?: boolean;
  busy?: boolean;
  children: ReactNode;
  'aria-label': string;
}

export function IconButton({
  intent = 'neutral',
  size = 'md',
  loading = false,
  busy = false,
  disabled = false,
  type = 'button',
  className,
  children,
  ...rest
}: IconButtonProps) {
  const isBusy = loading || busy;
  const inactive = disabled || isBusy;
  const classes = [
    'btn',
    'btn-icon',
    `btn-icon-${size}`,
    `btn-intent-${intent}`,
    'btn-emphasis-ghost',
    className ?? '',
  ]
    .filter(Boolean)
    .join(' ');
  return (
    <button
      type={type}
      className={classes}
      disabled={inactive}
      aria-busy={isBusy || undefined}
      {...rest}
    >
      {isBusy ? (
        <span className="btn-spinner" aria-hidden="true" />
      ) : (
        children
      )}
    </button>
  );
}
