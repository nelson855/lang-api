import { useState } from 'react';
import { Link, NavLink, Outlet } from 'react-router';
import { useNavigate } from 'react-router';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Brand } from '../components/brand/Brand';
import { Dialog } from '../components/ui/Dialog';
import { LanguageSwitcher } from '../i18n/LanguageSwitcher';
import { usePageChrome } from './usePageChrome';
import { logout } from '../api/auth';
import { useAuthProfile } from '../features/auth/authState';
import { clearAuthenticatedScope } from '../features/auth/authCache';
import './ConsoleLayout.css';

export function ConsoleLayout() {
  const { t } = useTranslation();
  const { mainRef } = usePageChrome();
  const [drawerOpen, setDrawerOpen] = useState(false);
  const profile = useAuthProfile();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const logoutMutation = useMutation({
    mutationFn: logout,
    onSettled: () => {
      clearAuthenticatedScope(queryClient);
      navigate('/', { replace: true });
    },
  });

  return (
    <div className="console-shell">
      <aside className="console-sidebar">
        <Link to="/" className="brand-link">
          <Brand />
        </Link>
        <nav aria-label={t('pages.dashboard.title')}>
          <NavLink to="/dashboard" end className="console-nav-link">
            {t('nav.dashboard')}
          </NavLink>
          <NavLink to="/dashboard/api-keys" className="console-nav-link">
            {t('nav.apiKeys')}
          </NavLink>
          <NavLink to="/dashboard/request-logs" className="console-nav-link">
            {t('nav.requestLogs')}
          </NavLink>
        </nav>
        <div className="console-sidebar-foot">
          {profile ? <span>{profile.displayName ?? profile.username}</span> : null}
          <button type="button" onClick={() => logoutMutation.mutate()} disabled={logoutMutation.isPending}>
            {logoutMutation.isPending ? t('pages.auth.submitting') : t('nav.logout')}
          </button>
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
          <Link to="/dashboard/api-keys" className="drawer-link" onClick={() => setDrawerOpen(false)}>
            {t('nav.apiKeys')}
          </Link>
          <Link to="/dashboard/request-logs" className="drawer-link" onClick={() => setDrawerOpen(false)}>
            {t('nav.requestLogs')}
          </Link>
        </nav>
      </Dialog>
    </div>
  );
}
