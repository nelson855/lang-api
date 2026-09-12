import { useState } from 'react';
import { Link, NavLink, Outlet } from 'react-router';
import { useTranslation } from 'react-i18next';
import { Brand } from '../components/brand/Brand';
import { Dialog } from '../components/ui/Dialog';
import { LanguageSwitcher } from '../i18n/LanguageSwitcher';
import { usePageChrome } from './usePageChrome';
import './ConsoleLayout.css';

export function ConsoleLayout() {
  const { t } = useTranslation();
  const { mainRef } = usePageChrome();
  const [drawerOpen, setDrawerOpen] = useState(false);

  return (
    <div className="console-shell">
      <aside className="console-sidebar">
        <Link to="/" className="brand-link">
          <Brand />
        </Link>
        <nav aria-label={t('pages.dashboard.title')}>
          <NavLink to="/dashboard" className="console-nav-link">
            {t('nav.dashboard')}
          </NavLink>
        </nav>
        <div className="console-sidebar-foot">
          <LanguageSwitcher />
        </div>
      </aside>

      <div className="console-main">
        <header className="console-topbar">
          <button type="button" className="console-menu-button" onClick={() => setDrawerOpen(true)}>
            {t('nav.menu')}
          </button>
          <span className="console-context">{t('pages.dashboard.title')}</span>
        </header>
        <main ref={mainRef}>
          <div className="app-container">
            <Outlet />
          </div>
        </main>
      </div>

      <Dialog
        open={drawerOpen}
        onOpenChange={setDrawerOpen}
        title={t('nav.menu')}
        contentClassName="dialog-drawer"
        ariaLabel={t('nav.menu')}
      >
        <nav aria-label={t('nav.menu')}>
          <Link to="/dashboard" className="drawer-link" onClick={() => setDrawerOpen(false)}>
            {t('nav.dashboard')}
          </Link>
        </nav>
      </Dialog>
    </div>
  );
}
