import type { TextareaHTMLAttributes } from 'react';
import './Input.css';

export interface TextareaProps extends TextareaHTMLAttributes<HTMLTextAreaElement> {
  density?: 'standard' | 'compact';
}

export function Textarea({ density = 'standard', className, ...props }: TextareaProps) {
  return (
    <textarea
      {...props}
      className={['input', 'resize-none', `input-${density}`, className].filter(Boolean).join(' ')}
    />
  );
}
