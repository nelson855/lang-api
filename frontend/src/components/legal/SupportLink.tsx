import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';

function toSafeSupportUrl(url: string): string | null {
  const value = (url ?? '').trim();
  if (value === '') {
    return null;
  }
  if (value.toLowerCase().startsWith('mailto:')) {
    const address = value.slice('mailto:'.length);
    if (address.length === 0 || /\s/.test(address)) {
      return null;
    }
    return value;
  }
  let parsed: URL;
  try {
    parsed = new URL(value);
  } catch {
    return null;
  }
  if (parsed.protocol !== 'https:') {
    return null;
  }
  if (parsed.username !== '' || parsed.password !== '' || parsed.search !== '' || parsed.hash !== '') {
    return null;
  }
  return value;
}

export function SupportLink({ url, children }: { url: string; children: ReactNode }) {
  const { t } = useTranslation();
  const safe = toSafeSupportUrl(url);
  if (safe === null) {
    return <span className="support-unavailable">{t('support.unavailable')}</span>;
  }
  if (safe.toLowerCase().startsWith('mailto:')) {
    return <a href={safe}>{children}</a>;
  }
  return (
    <a href={safe} target="_blank" rel="noopener noreferrer">
      {children}
    </a>
  );
}
