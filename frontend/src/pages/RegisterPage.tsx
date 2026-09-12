import { Link } from 'react-router';
import { useTranslation } from 'react-i18next';

export function RegisterPage() {
  const { t } = useTranslation();
  return (
    <>
      <h1>{t('pages.register.title')}</h1>
      <p>{t('pages.register.unavailable')}</p>
      <Link to="/">{t('common.backHome')}</Link>
    </>
  );
}
