/**
 * 模型引用（modelRef）编解码：原始模型 ID 的 UTF-8 字节经网址安全 Base64 无填充编码。
 *
 * 与后端 `ModelRef` 保持同一套规则：只接受 `[A-Za-z0-9_-]`、拒绝填充与非法字符、
 * 严格 UTF-8 解码、要求重编码与输入完全一致（非规范重编码一律拒绝），
 * 并校验目录编号边界（非空、长度 1~128、无控制字符）。不做裁剪、不折叠大小写、
 * 不做 Unicode 归一化。非法输入统一返回 null，由调用方决定展示的安全提示。
 */

export const MODEL_ID_MAX_LENGTH = 128;
export const MODEL_REF_MAX_LENGTH = 512;

const URL_SAFE_ALPHABET = /^[A-Za-z0-9_-]+$/;

export function isValidModelId(id: string): boolean {
  if (typeof id !== 'string' || id.length === 0 || id.length > MODEL_ID_MAX_LENGTH) {
    return false;
  }
  for (let i = 0; i < id.length; i += 1) {
    const code = id.charCodeAt(i);
    if (code <= 0x1f || code === 0x7f) {
      return false;
    }
  }
  return true;
}

function toUrlSafeBase64(bytes: Uint8Array): string {
  let binary = '';
  for (const byte of bytes) {
    binary += String.fromCharCode(byte);
  }
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function fromUrlSafeBase64(ref: string): Uint8Array | null {
  const padded = ref.replace(/-/g, '+').replace(/_/g, '/');
  const binary = atob(padded.padEnd(Math.ceil(ref.length / 4) * 4, '='));
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i += 1) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes;
}

export function encodeModelRef(id: string): string | null {
  if (!isValidModelId(id)) {
    return null;
  }
  return toUrlSafeBase64(new TextEncoder().encode(id));
}

export function decodeModelRef(ref: string): string | null {
  if (typeof ref !== 'string' || ref.length === 0 || ref.length > MODEL_REF_MAX_LENGTH) {
    return null;
  }
  if (!URL_SAFE_ALPHABET.test(ref)) {
    return null;
  }
  if (ref.length % 4 === 1) {
    return null;
  }
  let bytes: Uint8Array | null;
  try {
    bytes = fromUrlSafeBase64(ref);
  } catch {
    return null;
  }
  if (!bytes || bytes.length === 0) {
    return null;
  }
  let id: string;
  try {
    id = new TextDecoder('utf-8', { fatal: true }).decode(bytes);
  } catch {
    return null;
  }
  if (!isValidModelId(id)) {
    return null;
  }
  // 非规范重编码（如带非零尾位）必须拒绝，否则会与目录键失配。
  return toUrlSafeBase64(new TextEncoder().encode(id)) === ref ? id : null;
}
