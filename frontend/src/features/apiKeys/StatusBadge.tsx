import { AlertTriangle, Ban, Check, Clock } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import type { ApiKeyStatus } from '../../api/apiKeys';
import './StatusBadge.css';

const ICONS = {
  enabled: Check,
  disabled: Ban,
  expired: Clock,
  exhausted: AlertTriangle,
} as const;

export function StatusBadge({ status }: { status: ApiKeyStatus }) {
  const { t } = useTranslation();
  const Icon = ICONS[status];
  return (
    <span className={`status-badge status-${status}`}>
      <Icon aria-hidden="true" focusable="false" size={14} />
      <span>{t(`pages.apiKeys.status_${status}`)}</span>
    </span>
  );
}
