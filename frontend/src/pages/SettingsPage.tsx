import { useEffect, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { AUTH_PROFILE_QUERY_KEY } from '../api/auth';
import { useAuthProfile } from '../features/auth/authState';
import { PortalApiError } from '../api/envelope';
import { useProfileUpdateMutation } from '../features/profile/useProfileUpdate';
import { clearUsageScope } from '../features/usage/usageCache';
import { Button } from '../components/ui/Button';
import { FormField } from '../components/ui/FormField';
import { Input } from '../components/ui/Input';
import { PasswordField } from '../components/ui/PasswordField';
import './SettingsPage.css';

export function SettingsPage() {
  const { t } = useTranslation();
  const profile = useAuthProfile();
  const queryClient = useQueryClient();
  const mutation = useProfileUpdateMutation();

  const [username, setUsername] = useState(profile?.username ?? '');
  const [displayName, setDisplayName] = useState(profile?.displayName ?? '');
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const submitted = mutation.isSuccess || mutation.isError;
  useEffect(() => {
    if (submitted) {
      setCurrentPassword('');
      setNewPassword('');
      setConfirmPassword('');
    }
  }, [submitted]);

  const sessionExpired =
    mutation.error instanceof PortalApiError && mutation.error.code === 'UNAUTHENTICATED';
  useEffect(() => {
    if (sessionExpired) {
      clearUsageScope(queryClient);
    }
  }, [queryClient, sessionExpired]);

  const error =
    mutation.error instanceof PortalApiError && mutation.error.code !== 'UNAUTHENTICATED'
      ? mutation.error
      : null;
  const fieldError = (name: string): string | undefined => {
    if (fieldErrors[name]) {
      return fieldErrors[name];
    }
    if (!error) {
      return undefined;
    }
    if (error.code === 'INVALID_ARGUMENT' && name === 'currentPassword') {
      return t('pages.settings.wrongPassword');
    }
    if (error.code === 'RESOURCE_CONFLICT' && name === 'username') {
      return t('pages.settings.usernameTaken');
    }
    return undefined;
  };

  const submit = (event: React.FormEvent) => {
    event.preventDefault();
    const nextErrors: Record<string, string> = {};
    const name = username.trim();
    if (name.length < 3) {
      nextErrors.username = t('pages.settings.usernameTooShort');
    }
    if (!currentPassword) {
      nextErrors.currentPassword = t('pages.settings.currentPasswordRequired');
    }
    const wantsPasswordChange = newPassword !== '' || confirmPassword !== '';
    if (wantsPasswordChange) {
      if (newPassword.length < 8) {
        nextErrors.newPassword = t('pages.settings.newPasswordTooShort');
      } else if (newPassword !== confirmPassword) {
        nextErrors.confirmPassword = t('pages.settings.passwordMismatch');
      }
    }
    setFieldErrors(nextErrors);
    if (Object.keys(nextErrors).length > 0) {
      return;
    }
    const display = displayName.trim();
    mutation.mutate({
      username: name,
      displayName: display === '' ? null : display,
      currentPassword,
      ...(wantsPasswordChange ? { newPassword, confirmPassword } : {}),
    });
  };

  return (
    <div className="settings-page">
      <h1>{t('pages.settings.title')}</h1>
      <form onSubmit={submit} noValidate>
        <section aria-label={t('pages.settings.basicTitle')}>
          <h2>{t('pages.settings.basicTitle')}</h2>
          <FormField label={t('pages.settings.usernameLabel')} required error={fieldError('username')}>
            <Input
              type="text"
              value={username}
              onChange={(event) => setUsername(event.target.value)}
              autoComplete="username"
              maxLength={32}
            />
          </FormField>
          <FormField label={t('pages.settings.displayNameLabel')} error={fieldError('displayName')}>
            <Input
              type="text"
              value={displayName}
              onChange={(event) => setDisplayName(event.target.value)}
              autoComplete="nickname"
              maxLength={64}
            />
          </FormField>
          <FormField label={t('pages.settings.emailLabel')} description={t('pages.settings.emailLocked')}>
            <Input type="email" value={profile?.email ?? ''} readOnly disabled />
          </FormField>
        </section>

        <section aria-label={t('pages.settings.passwordTitle')}>
          <h2>{t('pages.settings.passwordTitle')}</h2>
          <FormField
            label={t('pages.settings.currentPasswordLabel')}
            required
            error={fieldError('currentPassword')}
          >
            <PasswordField
              value={currentPassword}
              onChange={(event) => setCurrentPassword(event.target.value)}
              autoComplete="current-password"
            />
          </FormField>
          <FormField label={t('pages.settings.newPasswordLabel')} error={fieldError('newPassword')}>
            <PasswordField
              value={newPassword}
              onChange={(event) => setNewPassword(event.target.value)}
              autoComplete="new-password"
            />
          </FormField>
          <FormField label={t('pages.settings.confirmPasswordLabel')} error={fieldError('confirmPassword')}>
            <PasswordField
              value={confirmPassword}
              onChange={(event) => setConfirmPassword(event.target.value)}
              autoComplete="new-password"
            />
          </FormField>
        </section>

        {mutation.isSuccess ? (
          <div className="settings-notice" role="status">
            <p>{t('pages.settings.success')}</p>
          </div>
        ) : null}
        {error && error.code !== 'OPERATION_RESULT_UNKNOWN' ? (
          <div className="settings-notice" role="alert">
            <p>
              {t('pages.settings.loadError')}（{error.code} · {t('pages.settings.requestIdLabel')}：
              {error.requestId ?? '—'}）
            </p>
          </div>
        ) : null}
        {error && error.code === 'OPERATION_RESULT_UNKNOWN' ? (
          <div className="settings-notice" role="alert">
            <p>{t('pages.settings.unknownResult')}</p>
            <p>
              {t('pages.settings.requestIdLabel')}：{error.requestId ?? '—'}
            </p>
            <Button
              type="button"
              variant="secondary"
              onClick={() => void queryClient.refetchQueries({ queryKey: AUTH_PROFILE_QUERY_KEY })}
            >
              {t('pages.settings.reread')}
            </Button>
          </div>
        ) : null}

        <Button type="submit" variant="primary" disabled={mutation.isPending}>
          {mutation.isPending ? t('pages.settings.submitting') : t('pages.settings.submit')}
        </Button>
      </form>
    </div>
  );
}
