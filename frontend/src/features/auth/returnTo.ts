// api-boundary-exempt: 站内返回地址校验器自身需列举服务端边界前缀以拒绝它们
export const DEFAULT_RETURN_TO = '/dashboard';

const ENCODED_RISK = /%(?:2e|2f|5c|00)/i;
// eslint-disable-next-line no-restricted-syntax -- 校验器列举需拒绝的服务端边界前缀
const SERVER_PREFIXES = ['/api', '/portal', '/actuator'];

function isServerBoundary(pathname: string): boolean {
  return SERVER_PREFIXES.some((prefix) => pathname === prefix || pathname.startsWith(`${prefix}/`));
}

export function resolveReturnTo(raw: string | null | undefined): string {
  if (!raw) {
    return DEFAULT_RETURN_TO;
  }
  if (raw.includes('\\') || ENCODED_RISK.test(raw)) {
    return DEFAULT_RETURN_TO;
  }
  const rawPath = raw.split(/[?#]/)[0];
  if (rawPath.split('/').some((segment) => segment === '..' || segment === '.')) {
    return DEFAULT_RETURN_TO;
  }
  let url: URL;
  try {
    // eslint-disable-next-line no-restricted-syntax -- URL 解析需要哑基准源，仅用于同源比较
    url = new URL(raw, 'http://localhost');
  } catch {
    return DEFAULT_RETURN_TO;
  }
  // eslint-disable-next-line no-restricted-syntax -- 同上，与哑基准源比较即判定站内相对路径
  if (url.origin !== 'http://localhost') {
    return DEFAULT_RETURN_TO;
  }
  const { pathname } = url;
  if (!pathname.startsWith('/') || pathname.startsWith('//')) {
    return DEFAULT_RETURN_TO;
  }
  if (pathname.split('/').some((segment) => segment === '..' || segment === '.')) {
    return DEFAULT_RETURN_TO;
  }
  if (isServerBoundary(pathname)) {
    return DEFAULT_RETURN_TO;
  }
  return `${pathname}${url.search}`;
}
