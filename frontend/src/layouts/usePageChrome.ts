import { useEffect, useRef } from 'react';
import { useLocation } from 'react-router';
import { useTranslation } from 'react-i18next';
import { metaForPath } from '../app/router/routeMeta';
import { usePublicConfigData } from '../app/providers/publicConfigGate';

export interface RouteMetaTags {
  title: string;
  description?: string;
  canonical?: string;
  indexable: boolean;
}

function ensureTag(selector: string, create: () => HTMLElement): HTMLElement {
  const existing = document.head.querySelector(selector);
  if (existing instanceof HTMLElement) {
    return existing;
  }
  const created = create();
  document.head.appendChild(created);
  return created;
}

function removeTag(selector: string): void {
  document.head.querySelector(selector)?.remove();
}

export function applyRouteMeta(tags: RouteMetaTags): void {
  document.title = tags.title;
  if (tags.description) {
    const node = ensureTag('meta[name="description"]', () => {
      const meta = document.createElement('meta');
      meta.setAttribute('name', 'description');
      return meta;
    });
    node.setAttribute('content', tags.description);
  } else {
    removeTag('meta[name="description"]');
  }
  const robots = ensureTag('meta[name="robots"]', () => {
    const meta = document.createElement('meta');
    meta.setAttribute('name', 'robots');
    return meta;
  });
  robots.setAttribute('content', tags.indexable ? 'index,follow' : 'noindex,nofollow');
  if (tags.canonical) {
    const link = ensureTag('link[rel="canonical"]', () => {
      const element = document.createElement('link');
      element.setAttribute('rel', 'canonical');
      return element;
    });
    link.setAttribute('href', tags.canonical);
  } else {
    removeTag('link[rel="canonical"]');
  }
}

function siteOrigin(siteUrl: string | undefined): string | undefined {
  if (!siteUrl) {
    return undefined;
  }
  try {
    return new URL(siteUrl).origin;
  } catch {
    return undefined;
  }
}

export function markRouteErrorIndexed(): void {
  const robots = ensureTag('meta[name="robots"]', () => {
    const meta = document.createElement('meta');
    meta.setAttribute('name', 'robots');
    return meta;
  });
  robots.setAttribute('content', 'noindex,nofollow');
  removeTag('link[rel="canonical"]');
}

export function usePageChrome({ pageIndexable = true }: { pageIndexable?: boolean } = {}) {
  const { t, i18n } = useTranslation();
  const config = usePublicConfigData();
  const location = useLocation();
  const mainRef = useRef<HTMLElement>(null);
  const meta = metaForPath(location.pathname);
  const publicationIndexable = (config.publicationMode ?? 'PREVIEW') === 'PUBLIC';
  const indexable = publicationIndexable && pageIndexable && meta.id !== 'notFound';
  const canonical = indexable ? siteOrigin(config.siteUrl) : undefined;

  useEffect(() => {
    applyRouteMeta({
      title: `${t(meta.titleKey)} - ${config.siteName}`,
      description: meta.descriptionKey ? t(meta.descriptionKey) : undefined,
      canonical: canonical ? `${canonical}${location.pathname}` : undefined,
      indexable,
    });
    const heading = mainRef.current?.querySelector('h1');
    if (heading instanceof HTMLElement) {
      heading.setAttribute('tabindex', '-1');
      heading.focus({ preventScroll: true });
    }
  }, [location.pathname, t, i18n.language, config.siteName, config.publicationMode, config.siteUrl, meta.titleKey, meta.descriptionKey, meta.id, canonical, indexable]);

  return { mainRef };
}
