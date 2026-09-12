const GUARDED_PREFIXES = ['src/pages/', 'src/components/', 'src/layouts/', 'src/features/'];

function isGuardedFile(filePath: string): boolean {
  const normalized = filePath.replace(/\\/g, '/');
  return GUARDED_PREFIXES.some(
    (prefix) => normalized === prefix.slice(0, -1) || normalized.startsWith(prefix),
  );
}

function hasRawManagementApiPath(source: string): boolean {
  return /["'`]\/api(?=(?:\/|["'`?#\s]|$))/.test(source);
}

export function findApiBoundaryViolations(filePath: string, source: string): string[] {
  if (!isGuardedFile(filePath)) {
    return [];
  }
  if (source.includes('api-boundary-exempt:')) {
    return [];
  }
  const violations: string[] = [];
  if (/\bfetch\s*\(/.test(source)) {
    violations.push('页面层禁止直接调用全局 fetch，请改用 src/api/portalClient');
  }
  if (/https?:\/\/[^\s"'`]+/.test(source)) {
    violations.push('禁止在前端源码中声明绝对 API 地址，只允许站内相对 /portal/api/*');
  }
  if (hasRawManagementApiPath(source)) {
    violations.push('禁止引用原始管理 /api/* 路径，只允许 /portal/api/*');
  }
  return violations;
}
