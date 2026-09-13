import type { ComponentType } from 'react';
import type { RouteObject } from 'react-router';
import { AuthLayout } from '../../layouts/AuthLayout';
import { ConsoleLayout } from '../../layouts/ConsoleLayout';
import { PublicLayout } from '../../layouts/PublicLayout';
import { PublicOnlyRoute, RequireAuth } from '../../features/auth/authState';
import { getConsoleNavItems, getPublicNavItems, metaForPath, ROUTE_META } from './routeMeta';
import type { AppLayout, RouteAccess, RouteMeta } from './routeMeta';
import { RouteError } from './RouteError';

export { getConsoleNavItems, getPublicNavItems, metaForPath, ROUTE_META };
export type { AppLayout, RouteAccess, RouteMeta };

type PageModule = Record<string, ComponentType>;

async function lazyPage(
  loader: () => Promise<PageModule>,
  namedExport: string,
  wrap?: (page: ComponentType) => ComponentType,
): Promise<{ Component: ComponentType }> {
  const module = await loader();
  const Page = module[namedExport];
  return { Component: wrap ? wrap(Page) : Page };
}

const guardPublicOnly =
  (Page: ComponentType): ComponentType =>
  function GuardedPage() {
    return (
      <PublicOnlyRoute>
        <Page />
      </PublicOnlyRoute>
    );
  };

export function buildRoutes(): RouteObject[] {
  return [
    {
      errorElement: <RouteError />,
      element: <PublicLayout />,
      children: [
        { index: true, lazy: () => lazyPage(() => import('../../pages/HomePage'), 'HomePage') },
        { path: 'models', lazy: () => lazyPage(() => import('../../pages/ModelsPage'), 'ModelsPage') },
        { path: 'docs', lazy: () => lazyPage(() => import('../../pages/DocsPage'), 'DocsPage') },
        { path: '*', lazy: () => lazyPage(() => import('../../pages/NotFoundPage'), 'NotFoundPage') },
      ],
    },
    {
      errorElement: <RouteError />,
      element: <AuthLayout />,
      children: [
        {
          path: 'login',
          lazy: () => lazyPage(() => import('../../pages/LoginPage'), 'LoginPage', guardPublicOnly),
        },
        {
          path: 'register',
          lazy: () => lazyPage(() => import('../../pages/RegisterPage'), 'RegisterPage', guardPublicOnly),
        },
      ],
    },
    {
      path: 'dashboard',
      errorElement: <RouteError />,
      element: (
        <RequireAuth>
          <ConsoleLayout />
        </RequireAuth>
      ),
      children: [
        { index: true, lazy: () => lazyPage(() => import('../../pages/DashboardPage'), 'DashboardPage') },
        { path: 'api-keys', lazy: () => lazyPage(() => import('../../pages/ApiKeysPage'), 'ApiKeysPage') },
      ],
    },
  ];
}
