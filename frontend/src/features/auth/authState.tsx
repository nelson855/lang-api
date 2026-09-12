import { createContext, useContext, type ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router';
import { Forbidden, Loading } from '../../components/feedback/Feedback';
import { DEFAULT_RETURN_TO, resolveReturnTo } from './returnTo';

export type AuthStatus = 'checking' | 'authenticated' | 'anonymous' | 'forbidden';

const AuthStateContext = createContext<AuthStatus>('anonymous');

export function useAuthState(): AuthStatus {
  return useContext(AuthStateContext);
}

export function AuthStateProvider({
  status,
  children,
}: {
  status: AuthStatus;
  children: ReactNode;
}) {
  return <AuthStateContext.Provider value={status}>{children}</AuthStateContext.Provider>;
}

/**
 * P1-04 默认鉴权状态：尚无会话接口，生产默认为匿名。
 * 不读取 Cookie，不使用 localStorage 或 query 参数伪造登录。
 * P1-05 只需替换这里的数据来源，守卫 API 不变。
 */
export function DefaultAuthProvider({ children }: { children: ReactNode }) {
  return <AuthStateProvider status="anonymous">{children}</AuthStateProvider>;
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
