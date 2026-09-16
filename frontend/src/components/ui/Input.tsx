import type { InputHTMLAttributes } from 'react';
import './Input.css';

export interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  density?: 'standard' | 'compact';
}

export function Input({ density = 'standard', className, ...props }: InputProps) {
  return <input {...props} className={['input', `input-${density}`, className].filter(Boolean).join(' ')} />;
}
