import { useTranslation } from 'react-i18next';
import type { DashboardReasonCode } from '../../api/dashboard';
import type { MetricDisplayStatus } from './displayModel';

const REASON_KEY: Record<DashboardReasonCode, string> = {
  BASELINE_NOT_VERIFIED: 'pages.dashboard.reasonBaselineNotVerified',
  SOURCE_FIELD_MISSING: 'pages.dashboard.reasonSourceFieldMissing',
  NO_DATA: 'pages.dashboard.reasonNoData',
  PARTIAL_SOURCE_COVERAGE: 'pages.dashboard.reasonPartialSourceCoverage',
};

export function MetricCard({ label, display }: { label: string; display: MetricDisplayStatus }) {
  const { t } = useTranslation();

  let content: string | null = null;
  if (display.status === 'value') {
    content = display.text;
  } else if (display.status === 'no-data') {
    content = t('pages.dashboard.reasonNoData');
  } else if (display.status === 'unavailable') {
    content = REASON_KEY[display.reason] ? t(REASON_KEY[display.reason]) : t('pages.dashboard.reasonNoData');
  }

  return (
    <div className="dashboard-metric" aria-live="polite">
      <dt className="dashboard-metric-label">{label}</dt>
      <dd className="dashboard-metric-value" data-state={display.status}>
        {content}
      </dd>
    </div>
  );
}