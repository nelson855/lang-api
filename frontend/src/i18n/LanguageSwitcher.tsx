import { useTranslation } from 'react-i18next';
import { SUPPORTED_LOCALES, normalizeEnabledLocales } from './locale';

export function LanguageSwitcher({ enabledLocales }: { enabledLocales?: readonly unknown[] }) {
  const { t, i18n } = useTranslation();
  const normalized = enabledLocales === undefined ? [...SUPPORTED_LOCALES] : normalizeEnabledLocales(enabledLocales);
  const visible = normalized.length > 0 ? normalized : [...SUPPORTED_LOCALES];
  const current = i18n.language === 'en-US' ? 'en-US' : 'zh-CN';
  return (
    <div role="group" aria-label={t('common.language')}>
      {visible.includes('zh-CN') ? (
        <button
          type="button"
          aria-pressed={current === 'zh-CN'}
          onClick={() => void i18n.changeLanguage('zh-CN')}
        >
          中文
        </button>
      ) : null}
      {visible.includes('en-US') ? (
        <button
          type="button"
          aria-pressed={current === 'en-US'}
          onClick={() => void i18n.changeLanguage('en-US')}
        >
          English
        </button>
      ) : null}
    </div>
  );
}
