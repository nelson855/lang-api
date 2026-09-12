import { useTranslation } from 'react-i18next';
import { Empty } from '../components/feedback/Feedback';

export function ModelsPage() {
  const { t } = useTranslation();
  return (
    <>
      <h1>{t('pages.models.title')}</h1>
      <Empty description={t('pages.models.empty')} />
    </>
  );
}
