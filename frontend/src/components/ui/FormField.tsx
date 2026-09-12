import { cloneElement, useId, type ReactElement } from 'react';
import type { InputProps } from './Input';
import './Input.css';

export interface FormFieldProps {
  label: string;
  description?: string;
  error?: string;
  required?: boolean;
  children: ReactElement<InputProps>;
}

export function FormField({ label, description, error, required = false, children }: FormFieldProps) {
  const fieldId = useId();
  const descriptionId = `${fieldId}-description`;
  const errorId = `${fieldId}-error`;
  const describedBy = [description ? descriptionId : null, error ? errorId : null]
    .filter(Boolean)
    .join(' ');
  return (
    <div className="form-field">
      <label className="form-field-label" htmlFor={fieldId}>
        {label}
        {required ? (
          <span className="form-field-required" aria-hidden="true">
            *
          </span>
        ) : null}
      </label>
      {description ? (
        <p className="form-field-description" id={descriptionId}>
          {description}
        </p>
      ) : null}
      {cloneElement(children, {
        id: fieldId,
        required,
        'aria-invalid': error ? true : false,
        ...(describedBy ? { 'aria-describedby': describedBy } : {}),
      })}
      {error ? (
        <p className="form-field-error" id={errorId} role="alert">
          {error}
        </p>
      ) : null}
    </div>
  );
}
