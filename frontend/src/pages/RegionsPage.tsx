import { useTranslation } from 'react-i18next';
import { usePublicConfigData } from '../app/providers/publicConfigGate';
import { regionName } from '../i18n/regions';
import type { SupportedLocale } from '../i18n/locale';
import './PublicPages.css';

export function RegionsPage() {
  const { t, i18n } = useTranslation();
  const { supportedRegions } = usePublicConfigData();
  const locale = (i18n.language === 'en-US' ? 'en-US' : 'zh-CN') as SupportedLocale;

  if (supportedRegions.length === 0) {
    return (
      <div className="public-page">
        <div className="public-page-head"><p className="page-kicker">Availability</p><h1>{t('pages.regions.title')}</h1></div>
        <p>{t('pages.regions.empty')}</p>
      </div>
    );
  }

  return (
    <div className="public-page">
      <div className="public-page-head"><p className="page-kicker">Availability</p><h1>{t('pages.regions.title')}</h1></div>
      <ul className="region-list">
        {supportedRegions.map((code) => (
          <li key={code}>
            <span>{regionName(code, locale)}</span> <code>{code}</code>
          </li>
        ))}
      </ul>
    </div>
  );
}
