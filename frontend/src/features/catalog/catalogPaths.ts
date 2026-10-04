/**
 * 模型广场受控路径构造：详情页、返回列表与文档入口的路径只能由原始模型 ID 生成。
 *
 * 不接受显示名、厂商名或任何规范化后的 ID 作为输入；非法 ID 一律返回 null，
 * 由调用方展示安全参数提示，不回显可疑原文。查询参数只承载 q / provider
 * 两个受控条件，不接受可执行的 returnTo。
 */

import { decodeModelRef, encodeModelRef, isValidModelId } from './modelRef';

const MODELS_BASE = '/models';
const DOCS_PATH = '/docs';
const MODEL_PATH_PATTERN = /^\/models\/([^/?#]+)$/;

export interface CatalogConditions {
  search: string;
  provider: string | null;
}

/** 详情链接的路径部分；附加 q / provider 作为查询参数。非法 ID 返回 null。 */
export function buildModelDetailPath(id: string, conditions?: CatalogConditions): string | null {
  const ref = encodeModelRef(id);
  if (!ref) {
    return null;
  }
  const base = `${MODELS_BASE}/${ref}`;
  if (!conditions) {
    return base;
  }
  const search = new URLSearchParams();
  const q = conditions.search.trim();
  if (q) {
    search.set('q', q);
  }
  if (conditions.provider) {
    search.set('provider', conditions.provider);
  }
  const suffix = search.toString();
  return suffix.length === 0 ? base : `${base}?${suffix}`;
}

/** 只保留路径部分，便于在列表与详情间传参。 */
export function detailPathForId(id: string): string | null {
  return buildModelDetailPath(id);
}

export function isModelDetailPath(pathname: string): boolean {
  const match = MODEL_PATH_PATTERN.exec(pathname);
  return match !== null && decodeModelRef(match[1]) !== null;
}

/** 从详情路径解出原始模型 ID；路径不合法或引用非法时返回 null。 */
export function modelIdFromDetailPath(pathname: string): string | null {
  const match = MODEL_PATH_PATTERN.exec(pathname);
  if (!match) {
    return null;
  }
  return decodeModelRef(match[1]);
}

/** 文档入口：模型 ID 作为 URL 查询参数，路径模板与协议范围不变。 */
export function buildDocsPath(id: string): string | null {
  if (!isValidModelId(id)) {
    return null;
  }
  return `${DOCS_PATH}?model=${encodeURIComponent(id)}`;
}

/**
 * 校验详情响应的 model.id 与路径引用解出的 ID 精确一致。
 * 不一致属于契约错误：调用方按错误展示，绝不展示另一个模型。
 */
export function resolveDetailModel<T extends { id: string }>(modelRef: string, model: T): T | null {
  const expected = decodeModelRef(modelRef);
  if (expected === null || model.id !== expected) {
    return null;
  }
  return model;
}
