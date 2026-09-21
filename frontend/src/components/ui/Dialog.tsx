import * as RadixDialog from '@radix-ui/react-dialog';
import { useEffect, useRef, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import './Button.css';
import './Dialog.css';

export interface DialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  description?: string;
  actions?: ReactNode;
  children: ReactNode;
  contentClassName?: string;
  ariaLabel?: string;
}

export function Dialog({ open, onOpenChange, title, description, actions, children, contentClassName, ariaLabel }: DialogProps) {
  const { t } = useTranslation();
  const previousFocus = useRef<Element | null>(null);

  useEffect(() => {
    if (open) {
      previousFocus.current = document.activeElement;
    } else {
      const target = previousFocus.current;
      previousFocus.current = null;
      if (target instanceof HTMLElement && document.contains(target)) {
        target.focus();
      }
    }
  }, [open ]);
  return (
    <RadixDialog.Root open={open} onOpenChange={onOpenChange}>
      <RadixDialog.Portal>
        <RadixDialog.Overlay className="dialog-overlay" />
        <RadixDialog.Content
          className={['dialog-content', contentClassName].filter(Boolean).join(' ')}
          aria-label={ariaLabel}
        >
          <RadixDialog.Title className="dialog-title">{title}</RadixDialog.Title>
          {description ? (
            <RadixDialog.Description className="dialog-description">
              {description}
            </RadixDialog.Description>
          ) : null}
          <div className="dialog-body">{children}</div>
          <div className="dialog-footer">
            {actions}
            <RadixDialog.Close asChild>
              <button type="button" className="btn btn-sm btn-intent-neutral btn-emphasis-outline" onClick={() => onOpenChange(false)}>
                {t('common.close')}
              </button>
            </RadixDialog.Close>
          </div>
        </RadixDialog.Content>
      </RadixDialog.Portal>
    </RadixDialog.Root>
  );
}
