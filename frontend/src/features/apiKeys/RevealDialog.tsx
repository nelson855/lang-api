import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { revealApiKey, type ApiKey } from '../../api/apiKeys';
import { PortalApiError } from '../../api/envelope';
import { Button } from '../../components/ui/Button';
import { Dialog } from '../../components/ui/Dialog';

export const REVEAL_TTL_MS = 60_000;

export interface RevealDialogProps {
  apiKey: ApiKey;
  onClose: () => void;
  ttlMs?: number;
}

export function RevealDialog({ apiKey, onClose, ttlMs = REVEAL_TTL_MS }: RevealDialogProps) {
  const { t } = useTranslation();
  const [secret, setSecret] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [copyFailed, setCopyFailed] = useState(false);
  const [copied, setCopied] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const timer = useRef<number | undefined>(undefined);

  const clear = () => {
    window.clearTimeout(timer.current);
    timer.current = undefined;
    setSecret(null);
    setCopyFailed(false);
  };

  useEffect(() => () => window.clearTimeout(timer.current), []);

  const close = () => {
    clear();
    onClose();
  };

  const reveal = async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await revealApiKey(apiKey.id);
      setSecret(result.data.secret);
      window.clearTimeout(timer.current);
      timer.current = window.setTimeout(clear, ttlMs);
    } catch (failure) {
      if (failure instanceof PortalApiError && failure.code === 'UNAUTHENTICATED') {
        close();
        return;
      }
      setError(failure instanceof PortalApiError ? failure.message : t('states.error.title'));
    } finally {
      setLoading(false);
    }
  };

  const copy = async () => {
    if (secret === null) {
      return;
    }
    try {
      await navigator.clipboard.writeText(secret);
      setCopied(true);
      clear();
    } catch {
      setCopyFailed(true);
    }
  };

  return (
    <Dialog open onOpenChange={(open) => { if (!open) { close(); } }} title={t('pages.apiKeys.reveal')}>
      <p>{apiKey.name}</p>
      {secret === null ? <p>{t('pages.apiKeys.revealRisk')}</p> : null}
      {secret === null ? (
        <div className="form-actions">
          <Button type="button" variant="primary" disabled={loading} onClick={() => void reveal()}>
            {t('pages.apiKeys.revealShow')}
          </Button>
        </div>
      ) : (
        <>
          <label htmlFor="apikey-reveal-value">{t('pages.apiKeys.reveal')}</label>
          <input id="apikey-reveal-value" className="input" readOnly value={secret} />
          <div className="form-actions">
            <Button type="button" variant="primary" onClick={() => void copy()}>
              {t('pages.apiKeys.revealCopy')}
            </Button>
          </div>
        </>
      )}
      {copyFailed ? <p role="alert">{t('pages.apiKeys.revealCopyFailed')}</p> : null}
      {copied ? <p role="status">{t('pages.apiKeys.revealCopied')}</p> : null}
      {error ? <p role="alert">{error}</p> : null}
    </Dialog>
  );
}
