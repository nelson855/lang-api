// 第一阶段 MVP 品牌几何资产（L/ 标识）。
// 站点名称来自运行时配置；视觉标识集中在此一处，正式 Logo 结论确定后只需替换本模块与同源 favicon。
export const BRAND_MARK_VIEWBOX = '0 0 24 24';

export const BRAND_MARK_FRAME = {
  x: 1,
  y: 1,
  width: 22,
  height: 22,
  rx: 6,
} as const;

export const BRAND_MARK_PATHS = ['M8 5v11h9', 'M17 15l2.4 2.4'] as const;

export const BRAND_MARK_STROKE_WIDTH = 2.4;
