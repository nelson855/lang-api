import type { InputHTMLAttributes } from 'react';
import './Input.css';

export type InputProps = InputHTMLAttributes<HTMLInputElement>;

export function Input(props: InputProps) {
  return <input {...props} className={['input', props.className].filter(Boolean).join(' ')} />;
}
