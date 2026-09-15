// 自动品牌扫描（LANG-P1-11 7.4）。
// 检查用户可见产物中的禁止品牌与私网地址：前端源码、静态资源、生产构建文本、
// 公开 DTO/错误资源。测试、夹具与样例文件跳过；需求与历史文档中的规范性引用不属于
// 出货内容，不在此扫描（出货面出现即失败）。
// 用法：npm run brand-scan（工作目录为 frontend/）。

import { existsSync, readdirSync, readFileSync } from 'node:fs';
import { dirname, join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');

const SCAN_ROOTS = [
  'src',
  'public',
  'dist',
  '../portal-api/src/main/java/com/lang/portal/web/dto',
  '../portal-api/src/main/java/com/lang/portal/base',
  '../portal-api/src/main/java/com/lang/portal/web/legal',
];

const SCAN_EXTENSIONS = new Set(['.ts', '.tsx', '.css', '.svg', '.html', '.js', '.txt', '.java']);

const SKIP_PATTERNS = [/\.test\.[cm]?[tj]sx?$/, /\.spec\.[cm]?[tj]sx?$/, /fixtures?/i, /samples?/i, /\.map$/];

const BANNED_PATTERNS = [
  /青花/,
  /qinghua/i,
  /new\s+api\b/i,
  /\b10\.\d{1,3}\.\d{1,3}\.\d{1,3}\b/,
  /\b172\.(1[6-9]|2\d|3[01])\.\d{1,3}\.\d{1,3}\b/,
  /\b192\.168\.\d{1,3}\.\d{1,3}\b/,
  /\b127\.0\.0\.1\b/,
];

function shouldSkip(relativePath) {
  return SKIP_PATTERNS.some((pattern) => pattern.test(relativePath));
}

export function checkContent(relativePath, content, options = {}) {
  if (options.skip || shouldSkip(relativePath)) {
    return [];
  }
  return BANNED_PATTERNS.filter((pattern) => pattern.test(content)).map((pattern) => String(pattern));
}

function walk(dir, out = []) {
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = join(dir, entry.name);
    if (entry.isDirectory()) {
      if (entry.name === 'node_modules') {
        continue;
      }
      walk(full, out);
    } else {
      out.push(full);
    }
  }
  return out;
}

export function scanTree(root = ROOT) {
  const failures = [];
  for (const scanRoot of SCAN_ROOTS) {
    const dir = join(root, scanRoot);
    if (!existsSync(dir)) {
      continue;
    }
    for (const file of walk(dir)) {
      const rel = relative(root, file);
      if (!SCAN_EXTENSIONS.has(getExtension(file)) || shouldSkip(rel)) {
        continue;
      }
      const matched = checkContent(rel, readFileSync(file, 'utf8'));
      if (matched.length > 0) {
        failures.push(`${rel}: ${matched.join('；')}`);
      }
    }
  }
  return failures;
}

function getExtension(file) {
  const dot = file.lastIndexOf('.');
  return dot >= 0 ? file.slice(dot) : '';
}

const invokedDirectly =
  process.argv[1] !== undefined && fileURLToPath(import.meta.url) === process.argv[1];
if (invokedDirectly) {
  const failures = scanTree();
  if (failures.length > 0) {
    console.error('品牌扫描发现禁用内容：');
    for (const failure of failures) {
      console.error(`- ${failure}`);
    }
    process.exit(1);
  }
  console.log('品牌扫描通过：用户可见产物无禁用品牌与私网地址。');
}
