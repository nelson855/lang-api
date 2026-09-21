import { useRef, type ChangeEvent, type InputHTMLAttributes, type KeyboardEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { X } from 'lucide-react';
import { IconButton } from './Button';
import './Input.css';
import './SearchField.css';

export interface SearchFieldProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'type' | 'onSubmit'> {
  value: string;
  onChange: (event: ChangeEvent<HTMLInputElement>) => void;
  onSubmit?: (value: string) => void;
  density?: 'standard' | 'compact';
}

export function SearchField({
  value,
  onChange,
  onSubmit,
  density = 'standard',
  disabled = false,
  className,
  ...props
}: SearchFieldProps) {
  const { t } = useTranslation();
  const inputRef = useRef<HTMLInputElement>(null);
  const composingRef = useRef(false);

  const handleKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    props.onKeyDown?.(event);
    if (event.defaultPrevented) return;
    if (event.key !== 'Enter') return;
    if (composingRef.current) return;
    onSubmit?.(value);
  };

  const handleClear = () => {
    const input = inputRef.current;
    if (!input) return;
    const nativeInputValueSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value')?.set;
    nativeInputValueSetter?.call(input, '');
    input.dispatchEvent(new Event('input', { bubbles: true }));
    input.focus();
  };

  const showClear = !disabled && value.length > 0;

  return (
    <span className={['search-field', className].filter(Boolean).join(' ')}>
      <input
        {...props}
        ref={inputRef}
        type="search"
        value={value}
        onChange={onChange}
        disabled={disabled}
        onKeyDown={handleKeyDown}
        onCompositionStart={() => {
          composingRef.current = true;
        }}
        onCompositionEnd={() => {
          composingRef.current = false;
        }}
        className={['input', `input-${density}`, 'search-input'].join(' ')}
      />
      {showClear ? (
        <IconButton
          aria-label={t('common.clearSearch')}
          intent="neutral"
          size="sm"
          className="search-clear"
          onClick={handleClear}
          type="button"
        >
          <X size={14} aria-hidden="true" />
        </IconButton>
      ) : null}
    </span>
  );
}
