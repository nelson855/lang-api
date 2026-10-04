import { describe, expect, it } from 'vitest';
import {
  MODEL_ID_MAX_LENGTH,
  MODEL_REF_MAX_LENGTH,
  decodeModelRef,
  encodeModelRef,
  isValidModelId,
} from './modelRef';

/** 用与后端 Base64 一致的规则重编码，构造填充与非零尾位等非规范输入。 */
function b64url(bytes: Uint8Array, withPadding = false): string {
  let binary = '';
  for (const byte of bytes) {
    binary += String.fromCharCode(byte);
  }
  const base64 = btoa(binary).replace(/\+/g, '-').replace(/\//g, '_');
  return withPadding ? base64 : base64.replace(/=+$/, '');
}

const ID_WITH_CONTROL = `a\u0001b`;
const ID_WITH_TAB = `a\u0009b`;

describe('模型 ID 边界校验', () => {
  it('接受 1 到 128 个字符的普通标识', () => {
    expect(isValidModelId('a')).toBe(true);
    expect(isValidModelId('a'.repeat(MODEL_ID_MAX_LENGTH))).toBe(true);
  });

  it('拒绝空值、超长与含控制字符的标识', () => {
    expect(isValidModelId('')).toBe(false);
    expect(isValidModelId('a'.repeat(MODEL_ID_MAX_LENGTH + 1))).toBe(false);
    expect(isValidModelId(ID_WITH_CONTROL)).toBe(false);
    expect(isValidModelId(ID_WITH_TAB)).toBe(false);
  });
});

describe('模型引用编码', () => {
  it.each([
    ['abc', 'YWJj'],
    ['deepseek-v4-flash', 'ZGVlcHNlZWstdjQtZmxhc2g'],
    ['a/b', 'YS9i'],
    ['a%b', 'YSVi'],
    ['a b', 'YSBi'],
    ['中文模型', '5Lit5paH5qih5Z6L'],
    ['emoji-😀', 'ZW1vamkt8J-YgA'],
  ])('编码 %s 为单一路径段引用并可往返', (id, ref) => {
    expect(encodeModelRef(id)).toBe(ref);
    expect(decodeModelRef(ref)).toBe(id);
  });

  it('保持大小写与空白原值，不折叠不裁剪', () => {
    expect(decodeModelRef(encodeModelRef('  MixedCase ID  ') as string)).toBe('  MixedCase ID  ');
  });

  it('达到 128 字符边界时仍可往返', () => {
    const id = 'x'.repeat(MODEL_ID_MAX_LENGTH);
    expect(decodeModelRef(encodeModelRef(id) as string)).toBe(id);
  });

  it('拒绝空引用、填充符与非法字母表字符', () => {
    expect(decodeModelRef('')).toBeNull();
    expect(decodeModelRef('YWJj=')).toBeNull();
    expect(decodeModelRef('YWJ!')).toBeNull();
    expect(decodeModelRef('YWJ j')).toBeNull();
    expect(decodeModelRef('YWJ+j')).toBeNull();
    expect(decodeModelRef('YWJ/j')).toBeNull();
  });

  it('拒绝非规范重编码（含填充与非零尾位）', () => {
    expect(decodeModelRef(b64url(new TextEncoder().encode('ab'), true))).toBeNull();
    expect(decodeModelRef(b64url(new TextEncoder().encode('a'), true))).toBeNull();
    expect(decodeModelRef('YWJjZB')).toBeNull();
  });

  it('拒绝非法 UTF-8 字节序列', () => {
    expect(decodeModelRef(b64url(new Uint8Array([0xff, 0xfe])))).toBeNull();
    expect(decodeModelRef(b64url(new Uint8Array([0xc3])))).toBeNull();
    expect(decodeModelRef(b64url(new Uint8Array([0xed, 0xa0, 0x80])))).toBeNull();
  });

  it('拒绝解码后含控制字符的引用', () => {
    expect(decodeModelRef(b64url(new TextEncoder().encode(ID_WITH_CONTROL)))).toBeNull();
    expect(decodeModelRef(b64url(new TextEncoder().encode(ID_WITH_TAB)))).toBeNull();
  });

  it('拒绝解码后超长与超过引用长度上限的输入', () => {
    const maxIdRef = encodeModelRef('x'.repeat(MODEL_ID_MAX_LENGTH)) as string;
    expect(decodeModelRef(`${maxIdRef}AA`)).toBeNull();
    expect(decodeModelRef('A'.repeat(MODEL_REF_MAX_LENGTH + 1))).toBeNull();
  });
});

describe('模型引用编码入口校验', () => {
  it('对非法 ID 返回 null 而不抛错', () => {
    expect(encodeModelRef('')).toBeNull();
    expect(encodeModelRef('a'.repeat(MODEL_ID_MAX_LENGTH + 1))).toBeNull();
    expect(encodeModelRef(ID_WITH_CONTROL)).toBeNull();
  });
});
