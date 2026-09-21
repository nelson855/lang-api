import { useRef, useState, type InputHTMLAttributes } from 'react';
import { useTranslation } from 'react-i18next';
import { Eye, EyeOff } from 'lucide-react';
import { IconButton } from './Button';
import './Input.css';
import './PasswordField.css';

export interface PasswordFieldProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'type'> {
  density?: 'standard' | 'compact';
}

export function PasswordField({ density = 'standard', className, ...props }: PasswordFieldProps) {
  const { t } = useTranslation();
  const [visible, setVisible] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);

  const toggle = () => {
    setVisible((previous) => !previous);
    // 切换后保持焦点在输入框，值由受控属性保留
    inputRef.current?.focus();
  };

  return (
    <span className={['password-field', className].filter(Boolean).join(' ')}>
      <input
        {...props}
        ref={inputRef}
        type={visible ? 'text' : 'password'}
        className={['input', `input-${density}`, 'password-input'].join(' ')}
      />
      <IconButton
        aria-label={visible ? t('common.hidePassword') : t('common.showPassword')}
        intent="neutral"
        size="sm"
        className="password-toggle"
        onClick={toggle}
        type="button"
      >
        {visible ? <EyeOff size={16} aria-hidden="true" /> : <Eye size={16} aria-hidden="true" />}
      </IconButton>
    </span>
  );
}

export interface SecretFieldProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'type'> {
  density?: 'standard' | 'compact';
}

export function SecretField({ density = 'standard', className, ...props }: SecretFieldProps) {
  const { t } = useTranslation();
  const [visible, setVisible] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);

  const toggle = () => {
    setVisible((previous) => !previous);
    inputRef.current?.focus();
  };

  return (
    <span className={['password-field', className].filter(Boolean).join(' ')}>
      <input
        {...props}
        ref={inputRef}
        type={visible ? 'text' : 'password'}
        className={['input', `input-${density}`, 'password-input'].join(' ')}
      />
      <IconButton
        aria-label={visible ? t('common.hideSecret') : t('common.showSecret')}
        intent="neutral"
        size="sm"
        className="password-toggle"
        onClick={toggle}
        type="button"
      >
        {visible ? <EyeOff size={16} aria-hidden="true" /> : <Eye size={16} aria-hidden="true" />}
      </IconButton>
    </span>
  );
}
