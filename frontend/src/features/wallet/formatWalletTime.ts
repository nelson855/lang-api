/**
 * 按活动 locale 与响应 timezone 格式化 ISO 时间。
 *
 * 返回面向用户的本地化文本;调用方另行保留原始 ISO 用于 `dateTime`
 * 属性或辅助文本。非法 ISO 回退为原字符串,不抛异常。
 */

export function formatWalletTime(iso: string, timezone: string, locale: string): string {
  const ms = Date.parse(iso);
  if (!Number.isFinite(ms)) {
    return iso;
  }
  try {
    return new Intl.DateTimeFormat(locale, {
      timeZone: timezone,
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      hour12: false,
    }).format(new Date(ms));
  } catch {
    return iso;
  }
}
