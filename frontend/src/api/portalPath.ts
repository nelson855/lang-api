const PORTAL_PREFIX = '/portal/api';
const ENCODED_RISK = /%(?:2e|2f|5c|00)/i;
const SCHEME = /^[a-zA-Z][a-zA-Z0-9+.-]*:/;

export function isPortalApiPath(rawPath: string): boolean {
  if (typeof rawPath !== 'string' || rawPath.length === 0) {
    return false;
  }
  if (rawPath.includes('#') || rawPath.includes('\\')) {
    return false;
  }
  if (SCHEME.test(rawPath) || rawPath.startsWith('//')) {
    return false;
  }
  if (!rawPath.startsWith('/')) {
    return false;
  }
  const queryIndex = rawPath.indexOf('?');
  const pathPart = queryIndex === -1 ? rawPath : rawPath.slice(0, queryIndex);
  if (pathPart.includes('//')) {
    return false;
  }
  if (pathPart !== PORTAL_PREFIX && !pathPart.startsWith(`${PORTAL_PREFIX}/`)) {
    return false;
  }
  if (pathPart.includes('@')) {
    return false;
  }
  // 编码风险字符只检查路径部分:查询值经 URL 编码后天然含有 %2F(如 IANA 时区)、
  // %20 等,它们是服务端按值解码的数据,不参与路径解析,不得误杀。
  if (ENCODED_RISK.test(pathPart)) {
    return false;
  }
  const segments = pathPart.split('/');
  if (segments.some((segment) => segment === '..' || segment === '.')) {
    return false;
  }
  return true;
}

export function validatePortalApiPath(rawPath: string): string {
  if (!isPortalApiPath(rawPath)) {
    throw new Error('非法 Portal API 路径，仅允许站内 /portal/api/* 相对路径');
  }
  return rawPath;
}

export function buildPortalApiUrl(
  path: string,
  query?: Record<string, string | number | boolean | undefined>,
): string {
  validatePortalApiPath(path);
  if (!query) {
    return path;
  }
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query)) {
    if (value === undefined) {
      continue;
    }
    params.append(key, String(value));
  }
  const suffix = params.toString();
  return suffix.length === 0 ? path : `${path}?${suffix}`;
}
