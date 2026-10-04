/**
 * 精度安全的十进制字符串本地化。
 *
 * 输入始终是服务端下发的非负十进制字符串,绝不经过 JavaScript `number`,
 * 因此超安全整数与长小数不会被截断或四舍五入。
 * 分组/小数分隔符取自活动 locale,数字本身逐字符搬运。
 */

const DECIMAL_PATTERN = /^(\d+)(?:\.(\d{1,18}))?$/;

function localeSeparators(locale: string): { group: string; decimal: string } {
  const parts = new Intl.NumberFormat(locale).formatToParts(1000.5);
  let group = ',';
  let decimal = '.';
  for (const part of parts) {
    if (part.type === 'group') group = part.value;
    if (part.type === 'decimal') decimal = part.value;
  }
  return { group, decimal };
}

/**
 * 按 locale 分组整数部分并转换小数点,小数部分原样保留。
 * 非法输入抛错,调用方不得用 0 或 USD 兜底。
 */
export function formatDecimalString(value: string, locale: string): string {
  const match = DECIMAL_PATTERN.exec(value);
  if (!match) {
    throw new Error(`非法十进制金额: ${value}`);
  }
  const [, intPart, fracPart] = match as unknown as [string, string, string | undefined];
  const { group, decimal } = localeSeparators(locale);
  const grouped = intPart.replace(/\B(?=(\d{3})+(?!\d))/g, group);
  return fracPart === undefined ? grouped : `${grouped}${decimal}${fracPart}`;
}
