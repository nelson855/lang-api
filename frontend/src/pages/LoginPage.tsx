import { Link } from 'react-router';
import { useTranslation } from 'react-i18next';

export function LoginPage() {
  const { t } = useTranslation();
  return (
    <>
      <h1>{t('pages.login.title')}</h1>
      <p>{t('pages.login.unavailable')}</p>
      <Link to="/">{t('common.backHome')}</Link>
    </>
  );
}
