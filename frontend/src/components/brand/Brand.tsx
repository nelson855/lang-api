import { usePublicConfigData } from '../../app/providers/publicConfigGate';

export function BrandMark() {
  return (
    <svg width="24" height="24" viewBox="0 0 24 24" aria-hidden="true" focusable="false">
      <rect x="1" y="1" width="22" height="22" rx="6" fill="var(--color-primary)" />
      <path d="M8 5v11h9" stroke="var(--color-primary-contrast)" strokeWidth="2.4" fill="none" strokeLinecap="round" />
      <path d="M17 15l2.4 2.4" stroke="var(--color-primary-contrast)" strokeWidth="2.4" strokeLinecap="round" />
    </svg>
  );
}

export function Brand() {
  const config = usePublicConfigData();
  return (
    <span className="brand">
      <BrandMark />
      <span className="brand-name">{config.siteName}</span>
    </span>
  );
}
