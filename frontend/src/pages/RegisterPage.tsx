import { useState, type FormEvent } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router';
import { useTranslation } from 'react-i18next';
import { fetchAuthOptions, register as registerAccount } from '../api/auth';

export function RegisterPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const options = useQuery({ queryKey: ['portal', 'auth', 'options'], queryFn: ({ signal }) => fetchAuthOptions(signal) });
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const registerMutation = useMutation({
    mutationFn: registerAccount,
    onSuccess: () => navigate('/login', { replace: true }),
  });

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (username.trim().length < 3 || password.length < 8 || password !== confirmPassword) return;
    registerMutation.mutate({ username: username.trim(), password, confirmPassword });
  }

  if (options.isPending) return <p role="status">{t('states.loading')}</p>;
  if (options.data?.data.registrationEnabled === false) {
    return <><h1>{t('pages.register.title')}</h1><p>{t('pages.register.closed')}</p></>;
  }
  return (
    <>
      <h1>{t('pages.register.title')}</h1>
      <form onSubmit={submit} noValidate>
        <label>{t('pages.auth.username')}<input name="username" value={username} onChange={(event) => setUsername(event.target.value)} autoComplete="username" required minLength={3} /></label>
        <label>{t('pages.auth.password')}<input name="password" type="password" value={password} onChange={(event) => setPassword(event.target.value)} autoComplete="new-password" required minLength={8} /></label>
        <label>{t('pages.auth.confirmPassword')}<input name="confirmPassword" type="password" value={confirmPassword} onChange={(event) => setConfirmPassword(event.target.value)} autoComplete="new-password" required minLength={8} /></label>
        {password && confirmPassword && password !== confirmPassword ? <p role="alert">{t('pages.auth.passwordMismatch')}</p> : null}
        {registerMutation.error ? <p role="alert">{t('pages.auth.requestFailed')}</p> : null}
        <button type="submit" disabled={registerMutation.isPending}>{registerMutation.isPending ? t('pages.auth.submitting') : t('pages.register.submit')}</button>
      </form>
    </>
  );
}
