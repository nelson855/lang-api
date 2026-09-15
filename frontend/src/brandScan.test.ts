import { describe, expect, it } from 'vitest';
import { checkContent } from '../scripts/brand-scan.mjs';

describe('品牌与私网地址扫描', () => {
  it('干净内容直接通过', () => {
    expect(checkContent('src/pages/HomePage.tsx', '<h1>自有语言模型网关</h1>')).toEqual([]);
    expect(checkContent('src/api/portalClient.ts', "fetch('/portal/api/models')")).toEqual([]);
  });

  it('拦截用户可见的上游与参考品牌', () => {
    expect(checkContent('src/components/Brand.tsx', 'QinghuaAPI 风格')).not.toEqual([]);
    expect(checkContent('src/pages/HomePage.tsx', '由 New API 提供')).not.toEqual([]);
    expect(checkContent('src/i18n/resources/zhCN.ts', '青花页面')).not.toEqual([]);
  });

  it('单词边界内代码标识不误杀', () => {
    expect(checkContent('src/api/auth.ts', 'throw new ApiError(code)')).toEqual([]);
  });

  it('拦截私网与回环地址', () => {
    expect(checkContent('src/api/portalClient.ts', 'http://192.168.1.10/v1')).not.toEqual([]);
    expect(checkContent('public/config.json', 'http://127.0.0.1:8080/portal')).not.toEqual([]);
  });

  it('跳过测试与夹具文件', () => {
    expect(
      checkContent('src/api/auth.test.ts', 'QinghuaAPI', { skip: true }),
    ).toEqual([]);
  });
});
