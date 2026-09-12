import { Link, Outlet } from 'react-router';
import { useTranslation } from 'react-i18next';
import { Brand } from '../components/brand/Brand';
import { usePageChrome } from './usePageChrome';
import './AuthLayout.css';

export function AuthLayout() {
  const { t } = useTranslation();
  const { mainRef } = usePageChrome();
  return (
    <>
      <header className="auth-header">
        <div className="app-container">
          <Link to="/" className="brand-link">
            <Brand />
          </Link>
        </div>
      </header>
      <main ref={mainRef}>
        <div className="app-container auth-container">
          <Outlet />
          <p>
            <Link to="/">{t('common.backHome')}</Link>
          </p>
        </div>
      </main>
    </>
  );
}
