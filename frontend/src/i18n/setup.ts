import i18next, { type i18n as I18nInstance } from 'i18next';
import { initReactI18next } from 'react-i18next';
import { enUS } from './resources/enUS';
import { zhCN } from './resources/zhCN';
import type { SupportedLocale } from './locale';
import { DEFAULT_LOCALE } from './locale';

export function createI18nInstance(locale: SupportedLocale): I18nInstance {
  const instance = i18next.createInstance();
  void instance.use(initReactI18next).init({
    lng: locale,
    fallbackLng: DEFAULT_LOCALE,
    resources: {
      'zh-CN': { translation: zhCN },
      'en-US': { translation: enUS },
    },
    // React 渲染文本节点时自带转义，此处关闭 i18next 转义可避免双重转义；
    // 译文一律按文本渲染，禁止传入 dangerouslySetInnerHTML。
    interpolation: { escapeValue: false },
  });
  return instance;
}
