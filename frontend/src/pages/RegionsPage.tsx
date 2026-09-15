import { useTranslation } from 'react-i18next';
import { usePublicConfigData } from '../app/providers/publicConfigGate';
import { regionName } from '../i18n/regions';
import type { SupportedLocale } from '../i18n/locale';

export function RegionsPage() {
  const { t, i18n } = useTranslation();
  const { supportedRegions } = usePublicConfigData();
  const locale = (i18n.language === 'en-US' ? 'en-US' : 'zh-CN') as SupportedLocale;

  if (supportedRegions.length === 0) {
    return (
      <main>
        <h1>{t('pages.regions.title')}</h1>
        <p>{t('pages.regions.empty')}</p>
      </main>
    );
  }

  return (
    <main>
      <h1>{t('pages.regions.title')}</h1>
      <ul>
        {supportedRegions.map((code) => (
          <li key={code}>
            <span>{regionName(code, locale)}</span> <code>{code}</code>
          </li>
        ))}
      </ul>
    </main>
  );
}
