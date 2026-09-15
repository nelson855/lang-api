import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  LEGAL_PRIVACY_PATH,
  LEGAL_TERMS_PATH,
  fetchLegalDocument,
  legalDocumentSchema,
} from './legal';
import { portalRequest } from './portalClient';

vi.mock('./portalClient', () => ({
  portalRequest: vi.fn(),
}));

const mockedRequest = vi.mocked(portalRequest);

describe('法律正文 schema', () => {
  it('接受服务端净化后的标题、正文与语言', () => {
    expect(() =>
      legalDocumentSchema.parse({
        type: 'TERMS',
        title: '用户协议',
        contentHtml: '<p>safe</p>',
        locale: 'zh-CN',
      }),
    ).not.toThrow();
  });

  it('拒绝缺失字段、未知类型与原文透传字段', () => {
    expect(() =>
      legalDocumentSchema.parse({
        type: 'DEVELOPER',
        title: '用户协议',
        contentHtml: '<p>safe</p>',
        locale: 'zh-CN',
      }),
    ).toThrow();
    expect(() =>
      legalDocumentSchema.parse({ type: 'TERMS', title: '用户协议', locale: 'zh-CN' }),
    ).toThrow();
    expect(() =>
      legalDocumentSchema.parse({
        type: 'TERMS',
        title: '用户协议',
        contentHtml: '<p>safe</p>',
        locale: 'zh-CN',
        raw: 'untrusted',
      }),
    ).toThrow();
  });
});

describe('法律正文查询', () => {
  beforeEach(() => {
    mockedRequest.mockReset();
  });

  it('使用固定匿名路径并透传取消信号', async () => {
    mockedRequest.mockResolvedValue({
      data: { type: 'PRIVACY', title: '隐私政策', contentHtml: '<p>safe</p>', locale: 'zh-CN' },
      requestId: 'req-legal',
    });
    expect(LEGAL_TERMS_PATH).toBe('/portal/api/legal/terms');
    expect(LEGAL_PRIVACY_PATH).toBe('/portal/api/legal/privacy');

    const controller = new AbortController();
    await fetchLegalDocument(LEGAL_PRIVACY_PATH, controller.signal);

    expect(mockedRequest).toHaveBeenCalledTimes(1);
    expect(mockedRequest.mock.calls[0][0]).toBe('/portal/api/legal/privacy');
    expect(mockedRequest.mock.calls[0][2]).toMatchObject({ signal: controller.signal });
  });
});
