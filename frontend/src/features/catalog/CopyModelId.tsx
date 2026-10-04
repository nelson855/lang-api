import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Button } from '../../components/ui/Button';

/**
 * 列表与详情共用的原始模型 ID 复制操作。
 *
 * 只复制原始 ID，不复制 modelRef、显示名或厂商名称。剪贴板缺失或被拒绝时
 * 就地提示失败并保留可选中的完整 ID，不用浏览器 alert 打断用户。
 */
export function CopyModelId({ modelId, density = 'sm' }: { modelId: string; density?: 'sm' | 'md' }) {
  const { t } = useTranslation();
  const [status, setStatus] = useState<'idle' | 'ok' | 'fail'>('idle');

  async function copy() {
    try {
      const clipboard = (navigator as Navigator & { clipboard?: Clipboard }).clipboard;
      if (!clipboard) {
        throw new Error('no-clipboard');
      }
      await clipboard.writeText(modelId);
      setStatus('ok');
    } catch {
      setStatus('fail');
    }
  }

  return (
    <span className="copy-model-id">
      <Button
        type="button"
        intent="neutral"
        emphasis="outline"
        size={density}
        onClick={copy}
        aria-label={t('pages.models.copyModelId')}
      >
        {t('pages.models.copyModelId')}
      </Button>
      {status !== 'idle' ? (
        <span role="status" className="copy-model-id-status">
          {status === 'ok' ? t('pages.models.copyModelIdDone') : t('pages.models.copyModelIdFailed')}
        </span>
      ) : null}
      {status === 'fail' ? (
        <code className="copy-model-id-fallback">{modelId}</code>
      ) : null}
    </span>
  );
}
