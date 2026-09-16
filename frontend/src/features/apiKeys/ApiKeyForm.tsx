import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { CreateApiKeyInput } from '../../api/apiKeys';
import { Button } from '../../components/ui/Button';
import { FormField } from '../../components/ui/FormField';
import { Input } from '../../components/ui/Input';

export interface ApiKeyFormInitial {
  name?: string;
  unlimited?: boolean;
  remaining?: number;
  expiresAt?: string | null;
  models?: string[];
  ips?: string[];
}

export interface ApiKeyFormProps {
  mode: 'create' | 'edit';
  initial?: ApiKeyFormInitial;
  pending: boolean;
  serverError?: string | null;
  onSubmit: (input: CreateApiKeyInput) => void;
  onCancel: () => void;
}

const MAX_SAFE_INTEGER = 9_007_199_254_740_991;

export function isIpLiteralInput(value: string): boolean {
  const text = value.trim();
  if (text === '') {
    return false;
  }
  if (/[\s,%;/]/.test(text) || hasControlChars(text)) {
    return false;
  }
  if (isIPv4Literal(text)) {
    return true;
  }
  if (text.includes(':')) {
    return isIPv6Literal(text);
  }
  return false;
}

function isIPv4Literal(text: string): boolean {
  const parts = text.split('.');
  if (parts.length !== 4) {
    return false;
  }
  return parts.every((part) => /^\d{1,3}$/.test(part) && Number(part) <= 255);
}

function isIPv6Literal(text: string): boolean {
  const halves = text.split('::');
  if (halves.length > 2) {
    return false;
  }
  const groups: string[] = [];
  for (const half of halves) {
    if (half === '') {
      continue;
    }
    groups.push(...half.split(':'));
  }
  if (groups.length > 8 || (halves.length === 1 && groups.length !== 8)) {
    return false;
  }
  return groups.every((group) => {
    if (group.includes('.')) {
      return isIPv4Literal(group);
    }
    return /^[0-9a-fA-F]{1,4}$/.test(group);
  });
}

function hasControlChars(text: string): boolean {
  for (const code of Array.from(text, (ch) => ch.codePointAt(0) ?? 0)) {
    if (code < 0x20 || code === 0x7f) {
      return true;
    }
  }
  return false;
}

function splitLines(text: string): string[] {
  return text
    .split(/[\n,]+/)
    .map((item) => item.trim())
    .filter((item) => item !== '');
}

export function ApiKeyForm({ mode, initial, pending, serverError, onSubmit, onCancel }: ApiKeyFormProps) {
  const { t } = useTranslation();
  const [name, setName] = useState(initial?.name ?? '');
  const [unlimited, setUnlimited] = useState(initial?.unlimited ?? false);
  const [remaining, setRemaining] = useState(
    initial?.remaining === undefined || initial?.remaining === null ? '' : String(initial.remaining),
  );
  const [expires, setExpires] = useState(() => (initial?.expiresAt ?? '').slice(0, 16));
  const [modelsText, setModelsText] = useState((initial?.models ?? []).join('\n'));
  const [ipsText, setIpsText] = useState((initial?.ips ?? []).join('\n'));
  const [errors, setErrors] = useState<Record<string, string>>({});

  const submit = (event: React.FormEvent) => {
    event.preventDefault();
    const next: Record<string, string> = {};
    const trimmedName = name.trim();
    if (trimmedName === '') {
      next.name = t('pages.apiKeys.formNameRequired');
    } else if (trimmedName.length > 50) {
      next.name = t('pages.apiKeys.formNameTooLong');
    }
    let remainingValue: number | undefined;
    if (!unlimited) {
      if (remaining.trim() === '' || !/^\d+$/.test(remaining.trim())) {
        next.remaining = t('pages.apiKeys.formRemainingInvalid');
      } else {
        remainingValue = Number(remaining.trim());
        if (remainingValue > MAX_SAFE_INTEGER) {
          next.remaining = t('pages.apiKeys.formRemainingInvalid');
        }
      }
    }
    let expiresIso: string | null = null;
    if (expires.trim() !== '') {
      const instant = new Date(`${expires.trim()}:00`);
      if (Number.isNaN(instant.getTime())) {
        next.expires = t('pages.apiKeys.formExpiresInvalid');
      } else if (instant.getTime() <= Date.now()) {
        next.expires = t('pages.apiKeys.formExpiresPast');
      } else {
        expiresIso = instant.toISOString();
      }
    }
    const models = [...new Set(splitLines(modelsText))];
    for (const model of models) {
      if (model.includes(',') || hasControlChars(model)) {
        next.models = t('pages.apiKeys.formModelsInvalid');
        break;
      }
    }
    const ips = splitLines(ipsText);
    for (const ip of ips) {
      if (!isIpLiteralInput(ip)) {
        next.ips = t('pages.apiKeys.formIpsInvalid');
        break;
      }
    }
    setErrors(next);
    if (Object.keys(next).length > 0) {
      return;
    }
    onSubmit({
      name: trimmedName,
      unlimited,
      remaining: unlimited ? undefined : remainingValue,
      expiresAt: expiresIso,
      models,
      ips,
    });
  };

  return (
    <form onSubmit={submit} noValidate>
      <FormField label={t('pages.apiKeys.formName')} required error={errors.name}>
        <Input value={name} onChange={(event) => setName(event.target.value)} maxLength={60} />
      </FormField>
      <fieldset className="form-field">
        <legend>{t('pages.apiKeys.formQuota')}</legend>
        <label>
          <input
            type="radio"
            name="quota-mode"
            checked={!unlimited}
            onChange={() => setUnlimited(false)}
          />
          {t('pages.apiKeys.formQuotaLimited')}
        </label>
        <label>
          <input
            type="radio"
            name="quota-mode"
            checked={unlimited}
            onChange={() => setUnlimited(true)}
          />
          {t('pages.apiKeys.formUnlimited')}
        </label>
      </fieldset>
      {!unlimited ? (
        <FormField label={t('pages.apiKeys.formRemaining')} required error={errors.remaining}>
          <Input
            value={remaining}
            inputMode="numeric"
            onChange={(event) => setRemaining(event.target.value)}
          />
        </FormField>
      ) : null}
      <FormField
        label={t('pages.apiKeys.formExpires')}
        description={t('pages.apiKeys.formExpiresHint')}
        error={errors.expires}
      >
        <Input type="datetime-local" value={expires} onChange={(event) => setExpires(event.target.value)} />
      </FormField>
      <details>
        <summary>{t('pages.apiKeys.formAdvanced')}</summary>
        <div>
          <label htmlFor="apikey-form-models">{t('pages.apiKeys.formModels')}</label>
          <textarea
            id="apikey-form-models"
            className="input resize-none"
            value={modelsText}
            onChange={(event) => setModelsText(event.target.value)}
          />
          {errors.models ? <p role="alert">{errors.models}</p> : null}
        </div>
        <div>
          <label htmlFor="apikey-form-ips">{t('pages.apiKeys.formIps')}</label>
          <textarea
            id="apikey-form-ips"
            className="input resize-none"
            value={ipsText}
            onChange={(event) => setIpsText(event.target.value)}
          />
          {errors.ips ? <p role="alert">{errors.ips}</p> : null}
        </div>
      </details>
      {serverError ? <p role="alert">{serverError}</p> : null}
      <div className="form-actions">
        <Button type="submit" variant="primary" disabled={pending}>
          {mode === 'create' ? t('pages.apiKeys.formSubmitCreate') : t('pages.apiKeys.formSubmitSave')}
        </Button>
        <Button type="button" variant="secondary" onClick={onCancel}>
          {t('pages.apiKeys.formCancel')}
        </Button>
      </div>
    </form>
  );
}
