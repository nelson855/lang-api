import { useTranslation } from 'react-i18next';
import { Empty } from '../components/feedback/Feedback';

export function DashboardPage() {
  const { t } = useTranslation();
  return (
    <>
      <h1>{t('pages.dashboard.title')}</h1>
      <Empty description={t('pages.dashboard.empty')} />
    </>
  );
}
