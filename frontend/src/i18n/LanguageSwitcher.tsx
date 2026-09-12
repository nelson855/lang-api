import { useTranslation } from 'react-i18next';

export function LanguageSwitcher() {
  const { t, i18n } = useTranslation();
  const current = i18n.language === 'en-US' ? 'en-US' : 'zh-CN';
  return (
    <div role="group" aria-label={t('common.language')}>
      <button
        type="button"
        aria-pressed={current === 'zh-CN'}
        onClick={() => void i18n.changeLanguage('zh-CN')}
      >
        中文
      </button>
      <button
        type="button"
        aria-pressed={current === 'en-US'}
        onClick={() => void i18n.changeLanguage('en-US')}
      >
        English
      </button>
    </div>
  );
}
