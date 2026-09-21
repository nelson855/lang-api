import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Button } from './ui/Button';
import './CodeBlock.css';

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
    <div className="code-block">
      <pre className="code-block-pre">
        <code data-language={language}>{code}</code>
      </pre>
      <div className="code-block-footer">
        <Button
          type="button"
          intent="neutral"
          emphasis="outline"
          size="sm"
          onClick={copy}
          disabled={disabled}
          title={disabledReason ?? undefined}
        >
          {t('pages.docs.copy')}
        </Button>
        {disabledReason ? <span role="status" className="code-block-status">{disabledReason}</span> : null}
        {status === 'ok' ? <span role="status" className="code-block-status">{t('pages.docs.copied')}</span> : null}
        {status === 'fail' ? <span role="status" className="code-block-status">{t('pages.docs.copyFailed')}</span> : null}
      </div>
    </div>
  );
}
