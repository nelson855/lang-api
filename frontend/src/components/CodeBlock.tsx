import { useState } from 'react';
import { useTranslation } from 'react-i18next';

export function CodeBlock({
  code,
  language,
  disabledReason,
}: {
  code: string;
  language: string;
  disabledReason?: string | null;
}) {
  const { t } = useTranslation();
  const [status, setStatus] = useState<'idle' | 'ok' | 'fail'>('idle');
  const disabled = !!disabledReason;

  async function copy() {
    if (disabled) {
      return;
    }
    try {
      const clipboard = (navigator as Navigator & { clipboard?: Clipboard }).clipboard;
      if (!clipboard) {
        throw new Error('no-clipboard');
      }
      await clipboard.writeText(code);
      setStatus('ok');
    } catch {
      setStatus('fail');
    }
  }

  return (
    <div>
      <pre style={{ overflowX: 'auto' }}>
        <code data-language={language}>{code}</code>
      </pre>
      <button type="button" onClick={copy} disabled={disabled} title={disabledReason ?? undefined}>
        {t('pages.docs.copy')}
      </button>
      {disabledReason ? <span role="status">{disabledReason}</span> : null}
      {status === 'ok' ? <span role="status">{t('pages.docs.copied')}</span> : null}
      {status === 'fail' ? <span role="status">{t('pages.docs.copyFailed')}</span> : null}
    </div>
  );
}
