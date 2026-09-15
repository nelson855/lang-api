import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link, NavLink, Outlet, useLocation } from 'react-router';
import { useTranslation } from 'react-i18next';
import { Brand } from '../components/brand/Brand';
import { Dialog } from '../components/ui/Dialog';
import { LanguageSwitcher } from '../i18n/LanguageSwitcher';
import { SupportLink } from '../components/legal/SupportLink';
import { usePublicConfigData } from '../app/providers/publicConfigGate';
import { getPublicNavItems } from '../app/router/routeMeta';
import { usePageChrome } from './usePageChrome';
import { fetchAuthOptions } from '../api/auth';
import './PublicLayout.css';

export function PublicLayout() {
  const { t } = useTranslation();
  const location = useLocation();
  const [legalIndexable, setLegalIndexable] = useState<boolean | null>(null);
  useEffect(() => {
    setLegalIndexable(null);
  }, [location.pathname]);
  const onLegalRoute = location.pathname === '/terms' || location.pathname === '/privacy';
  const { mainRef } = usePageChrome({ pageIndexable: onLegalRoute ? (legalIndexable ?? false) : true });
  const [drawerOpen, setDrawerOpen] = useState(false);
  const navItems = getPublicNavItems();
  const { enabledLocales, supportUrl } = usePublicConfigData();
  const authOptions = useQuery({ queryKey: ['portal', 'auth', 'options'], queryFn: ({ signal }) => fetchAuthOptions(signal) });
  const registrationEnabled = authOptions.data?.data.registrationEnabled !== false;

  return (
    <>
      <header className="public-header">
        <div className="app-container public-header-inner">
          <Link to="/" className="brand-link">
            <Brand />
          </Link>
          <nav className="public-nav-desktop" aria-label={t('nav.home')}>
            {navItems.map((item) => (
              <NavLink key={item.id} to={item.path} className="public-nav-link">
                {t(item.navKey as string)}
              </NavLink>
            ))}
          </nav>
          <div className="public-header-actions">
            <LanguageSwitcher enabledLocales={enabledLocales} />
            <Link to="/login">{t('nav.login')}</Link>
            {registrationEnabled ? <Link to="/register">{t('nav.register')}</Link> : null}
          </div>
          <button
            type="button"
            className="public-menu-button"
            onClick={() => setDrawerOpen(true)}
          >
            {t('nav.menu')}
          </button>
        </div>
      </header>

      <Dialog
        open={drawerOpen}
        onOpenChange={setDrawerOpen}
        title={t('nav.menu')}
        contentClassName="dialog-drawer"
        ariaLabel={t('nav.menu')}
      >
        <nav aria-label={t('nav.menu')}>
          {navItems.map((item) => (
            <Link
              key={item.id}
              to={item.path}
              className="drawer-link"
              onClick={() => setDrawerOpen(false)}
            >
              {t(item.navKey as string)}
            </Link>
          ))}
        </nav>
      </Dialog>

      <main ref={mainRef}>
        <div className="app-container">
          <Outlet context={{ setLegalIndexable }} />
        </div>
      </main>

      <footer className="public-footer">
        <div className="app-container public-footer-inner">
          <Brand />
          <nav aria-label={t('nav.footer')}>
            {navItems.map((item) => (
              <Link key={item.id} to={item.path}>
                {t(item.navKey as string)}
              </Link>
            ))}
            <Link to="/terms">{t('nav.terms')}</Link>
            <Link to="/privacy">{t('nav.privacy')}</Link>
            <SupportLink url={supportUrl}>{t('pages.legal.contactSupport')}</SupportLink>
            <Link to="/login">{t('nav.login')}</Link>
            {registrationEnabled ? <Link to="/register">{t('nav.register')}</Link> : null}
          </nav>
        </div>
      </footer>
    </>
  );
}
