import { useState, type FormEvent } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate, useSearchParams } from 'react-router';
import { useTranslation } from 'react-i18next';
import { login } from '../api/auth';
import { PortalApiError } from '../api/envelope';
import { cacheAuthenticatedProfile } from '../features/auth/authCache';
import { resolveReturnTo } from '../features/auth/returnTo';

export function loginErrorKind(error: unknown): 'invalidCredentials' | 'requestFailed' {
  return error instanceof PortalApiError && error.code === 'INVALID_CREDENTIALS'
    ? 'invalidCredentials'
    : 'requestFailed';
}

export function LoginPage() {
  const { t } = useTranslation();
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const loginMutation = useMutation({
    mutationFn: login,
    throwOnError: false,
    onSuccess: (result) => {
      cacheAuthenticatedProfile(queryClient, result);
      navigate(resolveReturnTo(params.get('returnTo') ?? '/dashboard'), { replace: true });
    },
  });
  const errorKind = loginMutation.error ? loginErrorKind(loginMutation.error) : null;

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (username.trim().length < 3 || password.length < 8) return;
    loginMutation.mutate({ username: username.trim(), password });
  }

  return (
    <>
      <h1>{t('pages.login.title')}</h1>
      <form onSubmit={submit} noValidate>
        <label>
          {t('pages.auth.username')}
          <input name="username" value={username} onChange={(event) => setUsername(event.target.value)} autoComplete="username" required minLength={3} />
        </label>
        <label>
          {t('pages.auth.password')}
          <input name="password" type="password" value={password} onChange={(event) => setPassword(event.target.value)} autoComplete="current-password" required minLength={8} />
        </label>
        {errorKind === 'invalidCredentials' ? <p role="alert">{t('pages.auth.invalidCredentials')}</p> : null}
        {errorKind === 'requestFailed' ? <p role="alert">{t('pages.auth.requestFailed')}</p> : null}
        <button type="submit" disabled={loginMutation.isPending}>
          {loginMutation.isPending ? t('pages.auth.submitting') : t('pages.login.submit')}
        </button>
      </form>
    </>
  );
}
