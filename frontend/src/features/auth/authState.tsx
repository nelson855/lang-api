import { createContext, useContext, type ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { Forbidden, Loading } from '../../components/feedback/Feedback';
import { PortalApiError } from '../../api/envelope';
import { AUTH_PROFILE_QUERY_KEY, fetchProfile, type AuthProfile } from '../../api/auth';
import { DEFAULT_RETURN_TO, resolveReturnTo } from './returnTo';

export type AuthStatus = 'checking' | 'authenticated' | 'anonymous' | 'forbidden';

const AuthStateContext = createContext<AuthStatus>('anonymous');
const AuthProfileContext = createContext<AuthProfile | null>(null);

export function useAuthState(): AuthStatus {
  return useContext(AuthStateContext);
}

export function useAuthProfile(): AuthProfile | null {
  return useContext(AuthProfileContext);
}

export function AuthStateProvider({
  status,
  profile = null,
  children,
}: {
  status: AuthStatus;
  profile?: AuthProfile | null;
  children: ReactNode;
}) {
  return (
    <AuthStateContext.Provider value={status}>
      <AuthProfileContext.Provider value={profile}>{children}</AuthProfileContext.Provider>
    </AuthStateContext.Provider>
  );
}

/**
 * 会话状态仅由同源 profile 接口决定；浏览器不读取 Cookie，也不保存令牌。
 */
export function DefaultAuthProvider({ children }: { children: ReactNode }) {
  const profileQuery = useQuery({
    queryKey: AUTH_PROFILE_QUERY_KEY,
    queryFn: ({ signal }) => fetchProfile(signal),
    staleTime: 0,
  });
  if (profileQuery.isPending) {
    return <AuthStateProvider status="checking">{children}</AuthStateProvider>;
  }
  if (profileQuery.data) {
    return <AuthStateProvider status="authenticated" profile={profileQuery.data.data}>{children}</AuthStateProvider>;
  }
  const status = profileQuery.error instanceof PortalApiError && profileQuery.error.status === 403
    ? 'forbidden'
    : 'anonymous';
  return <AuthStateProvider status={status}>{children}</AuthStateProvider>;
}

export function RequireAuth({ children }: { children: ReactNode }) {  const status = useAuthState();
  const location = useLocation();

  if (status === 'checking') {
    return <Loading />;
  }
  if (status === 'forbidden') {
    return <Forbidden />;
  }
  if (status === 'anonymous') {
    const returnTo = resolveReturnTo(`${location.pathname}${location.search}`);
    const fallback = returnTo === '/login' ? DEFAULT_RETURN_TO : returnTo;
    return <Navigate to={`/login?returnTo=${encodeURIComponent(fallback)}`} replace />;
  }
  return <>{children}</>;
}

export function PublicOnlyRoute({ children }: { children: ReactNode }) {
  const status = useAuthState();
  if (status === 'authenticated') {
    return <Navigate to={DEFAULT_RETURN_TO} replace />;
  }
  return <>{children}</>;
}
