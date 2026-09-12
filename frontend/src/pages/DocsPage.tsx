import { useTranslation } from 'react-i18next';
import { Empty } from '../components/feedback/Feedback';

export function DocsPage() {
  const { t } = useTranslation();
  return (
    <>
      <h1>{t('pages.docs.title')}</h1>
      <Empty description={t('pages.docs.empty')} />
    </>
  );
}
