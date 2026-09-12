import { createContext, useCallback, useContext, useRef, useState, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { useTranslation } from 'react-i18next';
import './Toast.css';

export type ToastKind = 'success' | 'info' | 'warning' | 'error';

export interface ErrorToast {
  message: unknown;
  requestId?: string;
}

interface ToastItem {
  id: number;
  kind: ToastKind;
  text: string;
  requestId?: string;
}

interface ToastApi {
  success: (message: string) => void;
  info: (message: string) => void;
  warning: (message: string) => void;
  error: (toast: ErrorToast) => void;
}

const ToastContext = createContext<ToastApi | null>(null);
const AUTO_DISMISS_MS = 5000;

export function useToast(): ToastApi {
  const api = useContext(ToastContext);
  if (!api) {
    throw new Error('必须在 ToastProvider 内使用通知');
  }
  return api;
}

export function ToastProvider({ children }: { children: ReactNode }) {
  const { t } = useTranslation();
  const [items, setItems] = useState<ToastItem[]>([]);
  const nextId = useRef(1);

  const dismiss = useCallback((id: number) => {
    setItems((current) => current.filter((item) => item.id !== id));
  }, []);

  const push = useCallback(
    (kind: ToastKind, text: string, requestId?: string) => {
      const id = nextId.current;
      nextId.current += 1;
      setItems((current) => [...current, { id, kind, text, requestId }]);
      window.setTimeout(() => dismiss(id), AUTO_DISMISS_MS);
    },
    [dismiss],
  );

  const api: ToastApi = {
    success: (message) => push('success', message),
    info: (message) => push('info', message),
    warning: (message) => push('warning', message),
    error: ({ message, requestId }) =>
      push('error', typeof message === 'string' ? message : t('states.error.title'), requestId),
  };

  return (
    <ToastContext.Provider value={api}>
      {children}
      {createPortal(
        <div className="toast-region" role="status" aria-live="polite">
          {items.map((item) => (
            <div key={item.id} className={`toast toast-${item.kind}`}>
              <p className="toast-text">{item.text}</p>
              {item.requestId ? (
                <p className="toast-request-id request-id">
                  {t('errors.requestId', { requestId: item.requestId })}
                </p>
              ) : null}
              <button type="button" className="toast-close" onClick={() => dismiss(item.id)}>
                {t('common.close')}
              </button>
            </div>
          ))}
        </div>,
        document.body,
      )}
    </ToastContext.Provider>
  );
}
