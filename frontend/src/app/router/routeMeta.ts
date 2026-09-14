export type AppLayout = 'public' | 'auth' | 'console';
export type RouteAccess = 'public' | 'public-only' | 'protected';

export interface RouteMeta {
  id: string;
  path: string;
  layout: AppLayout;
  access: RouteAccess;
  titleKey: string;
  navKey?: string;
  navOrder?: number;
}

export const ROUTE_META: RouteMeta[] = [
  { id: 'home', path: '/', layout: 'public', access: 'public', titleKey: 'pages.home.title', navKey: 'nav.home', navOrder: 0 },
  { id: 'models', path: '/models', layout: 'public', access: 'public', titleKey: 'pages.models.title', navKey: 'nav.models', navOrder: 1 },
  { id: 'docs', path: '/docs', layout: 'public', access: 'public', titleKey: 'pages.docs.title', navKey: 'nav.docs', navOrder: 2 },
  { id: 'login', path: '/login', layout: 'auth', access: 'public-only', titleKey: 'pages.login.title' },
  { id: 'register', path: '/register', layout: 'auth', access: 'public-only', titleKey: 'pages.register.title' },
  { id: 'dashboard', path: '/dashboard', layout: 'console', access: 'protected', titleKey: 'pages.dashboard.title' },
  { id: 'apiKeys', path: '/dashboard/api-keys', layout: 'console', access: 'protected', titleKey: 'pages.apiKeys.title', navKey: 'nav.apiKeys', navOrder: 1 },
  { id: 'requestLogs', path: '/dashboard/request-logs', layout: 'console', access: 'protected', titleKey: 'pages.requestLogs.title', navKey: 'nav.requestLogs', navOrder: 2 },
  { id: 'notFound', path: '*', layout: 'public', access: 'public', titleKey: 'pages.notFound.title' },
];

export function metaForPath(pathname: string): RouteMeta {
  return ROUTE_META.find((meta) => meta.path === pathname) ?? ROUTE_META[ROUTE_META.length - 1];
}

export function getPublicNavItems(): RouteMeta[] {
  return ROUTE_META.filter((meta) => meta.layout === 'public' && meta.navKey !== undefined).sort(
    (a, b) => (a.navOrder ?? 0) - (b.navOrder ?? 0),
  );
}

export function getConsoleNavItems(): RouteMeta[] {
  return ROUTE_META.filter((meta) => meta.layout === 'console' && meta.navKey !== undefined).sort(
    (a, b) => (a.navOrder ?? 0) - (b.navOrder ?? 0),
  );
}
