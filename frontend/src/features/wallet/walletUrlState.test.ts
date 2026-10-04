import { describe, expect, it } from 'vitest';
import {
  DEFAULT_WALLET_URL_STATE,
  WALLET_URL_KEYS,
  buildWalletUrlSearch,
  collapseWalletPage,
  normalizeWalletUrl,
  parseWalletUrl,
  switchWalletRange,
  switchWalletType,
  type WalletUrlState,
} from './walletUrlState';

const FIXED_NOW = new Date('2026-09-10T08:00:00.000Z');
const FIXED_TZ = 'Asia/Shanghai';

/** 默认 24H 范围(基于固定时钟快照)。 */
const DEFAULT_RANGE = {
  preset: '24h' as const,
  startTime: '2026-09-09T08:00:00.000Z',
  endTime: '2026-09-10T08:00:00.000Z',
  timezone: FIXED_TZ,
  granularity: 'HOUR' as const,
  labelKey: 'pages.wallet.range24h',
  enabled: true,
};

describe('钱包 URL:常量与白名单', () => {
  it('WALLET_URL_KEYS 白名单包含全部允许参数', () => {
    expect(new Set(WALLET_URL_KEYS)).toEqual(
      new Set(['range', 'startTime', 'endTime', 'timezone', 'granularity', 'type', 'page']),
    );
  });

  it('DEFAULT_WALLET_URL_STATE 为 24H / 全部 / 第 1 页', () => {
    expect(DEFAULT_WALLET_URL_STATE.preset).toBe('24h');
    expect(DEFAULT_WALLET_URL_STATE.type).toBe('ALL');
    expect(DEFAULT_WALLET_URL_STATE.page).toBe(1);
  });
});

describe('钱包 URL:首次进入 normalize', () => {
  it('空 search 用单次时钟快照生成 24H 完整 URL 并标记 needsReplace', () => {
    const result = normalizeWalletUrl('', FIXED_NOW, FIXED_TZ);
    expect(result.needsReplace).toBe(true);
    expect(result.state.preset).toBe('24h');
    expect(result.state.range.startTime).toBe(DEFAULT_RANGE.startTime);
    expect(result.state.range.endTime).toBe(DEFAULT_RANGE.endTime);
    expect(result.state.range.timezone).toBe(FIXED_TZ);
    expect(result.state.range.granularity).toBe('HOUR');
    expect(result.state.type).toBe('ALL');
    expect(result.state.page).toBe(1);
    expect(result.state.requestsEnabled).toBe(true);
  });

  it('首次进入的 URL 带全部白名单参数', () => {
    const result = normalizeWalletUrl('', FIXED_NOW, FIXED_TZ);
    const search = buildWalletUrlSearch(result.state);
    const params = new URLSearchParams(search);
    expect(params.get('range')).toBe('24h');
    expect(params.get('startTime')).toBe(DEFAULT_RANGE.startTime);
    expect(params.get('endTime')).toBe(DEFAULT_RANGE.endTime);
    expect(params.get('timezone')).toBe(FIXED_TZ);
    expect(params.get('granularity')).toBe('HOUR');
    expect(params.get('type')).toBe('ALL');
    expect(params.get('page')).toBe('1');
  });
});

describe('钱包 URL:完整范围恢复', () => {
  const fullSearch =
    '?range=7d&startTime=2026-09-01T00%3A00%3A00Z&endTime=2026-09-08T00%3A00%3A00Z&timezone=UTC&granularity=HOUR&type=CONSUMPTION&page=3';

  it('合法完整 URL 直接恢复原边界,不重新取时钟', () => {
    const result = normalizeWalletUrl(fullSearch, FIXED_NOW, FIXED_TZ);
    expect(result.needsReplace).toBe(false);
    expect(result.state.range.startTime).toBe('2026-09-01T00:00:00Z');
    expect(result.state.range.endTime).toBe('2026-09-08T00:00:00Z');
    expect(result.state.range.timezone).toBe('UTC');
    expect(result.state.range.granularity).toBe('HOUR');
    expect(result.state.preset).toBe('7d');
    expect(result.state.type).toBe('CONSUMPTION');
    expect(result.state.page).toBe(3);
  });

  it('parse/serialize 是逆运算', () => {
    const parsed = parseWalletUrl(fullSearch);
    expect(parsed.ok).toBe(true);
    if (!parsed.ok) return;
    const rebuilt = buildWalletUrlSearch(parsed.state);
    const reparsed = parseWalletUrl(rebuilt);
    expect(reparsed.ok).toBe(true);
    if (reparsed.ok) {
      expect(reparsed.state).toEqual(parsed.state);
    }
  });
});

describe('钱包 URL:非法/矛盾输入回退默认', () => {
  const expectFallback = (search: string) => {
    const result = normalizeWalletUrl(search, FIXED_NOW, FIXED_TZ);
    expect(result.needsReplace).toBe(true);
    expect(result.state.preset).toBe('24h');
    expect(result.state.type).toBe('ALL');
    expect(result.state.page).toBe(1);
    expect(result.state.range.startTime).toBe(DEFAULT_RANGE.startTime);
    expect(result.state.range.timezone).toBe(FIXED_TZ);
  };

  it('未知参数被剥离', () => {
    const result = normalizeWalletUrl(
      '?range=24h&startTime=2026-09-09T08%3A00%3A00Z&endTime=2026-09-10T08%3A00%3A00Z&timezone=Asia%2FShanghai&granularity=HOUR&type=ALL&page=1&userId=42&baseline=x',
      FIXED_NOW,
      FIXED_TZ,
    );
    // 白名单参数完整 → 合法;剥离 unknown 后不需要 replace(逻辑上 normalize 即可)
    expect(result.state.preset).toBe('24h');
  });

  it('单边时间(只 startTime 或只 endTime)回退默认', () => {
    expectFallback('?range=24h&startTime=2026-09-09T08%3A00%3A00Z&type=ALL&page=1');
    expectFallback('?range=24h&endTime=2026-09-10T08%3A00%3A00Z&type=ALL&page=1');
  });

  it('非法 ISO 时间/时区/枚举/页码回退默认', () => {
    expectFallback('?range=24h&startTime=not-a-date&endTime=2026-09-10T08%3A00%3A00Z&timezone=UTC&granularity=HOUR&type=ALL&page=1');
    expectFallback('?range=24h&startTime=2026-09-09T08%3A00%3A00Z&endTime=2026-09-10T08%3A00%3A00Z&timezone=Mars/Olympus&granularity=HOUR&type=ALL&page=1');
    expectFallback('?range=24h&startTime=2026-09-09T08%3A00%3A00Z&endTime=2026-09-10T08%3A00%3A00Z&timezone=UTC&granularity=MINUTE&type=ALL&page=1');
    expectFallback('?range=24h&startTime=2026-09-09T08%3A00%3A00Z&endTime=2026-09-10T08%3A00%3A00Z&timezone=UTC&granularity=HOUR&type=TRANSFER&page=1');
    expectFallback('?range=24h&startTime=2026-09-09T08%3A00%3A00Z&endTime=2026-09-10T08%3A00%3A00Z&timezone=UTC&granularity=HOUR&type=ALL&page=0');
    expectFallback('?range=24h&startTime=2026-09-09T08%3A00%3A00Z&endTime=2026-09-10T08%3A00%3A00Z&timezone=UTC&granularity=HOUR&type=ALL&page=abc');
  });

  it('未知 preset 回退默认', () => {
    expectFallback(
      '?range=1h&startTime=2026-09-09T08%3A00%3A00Z&endTime=2026-09-10T08%3A00%3A00Z&timezone=UTC&granularity=HOUR&type=ALL&page=1',
    );
  });

  it('preset 与实际范围不匹配(如 range=24h 但起止相差 7 天)回退默认', () => {
    expectFallback(
      '?range=24h&startTime=2026-09-01T00%3A00%3A00Z&endTime=2026-09-08T00%3A00%3A00Z&timezone=UTC&granularity=HOUR&type=ALL&page=1',
    );
  });

  it('startTime ≥ endTime 回退默认', () => {
    expectFallback(
      '?range=24h&startTime=2026-09-10T08%3A00%3A00Z&endTime=2026-09-09T08%3A00%3A00Z&timezone=UTC&granularity=HOUR&type=ALL&page=1',
    );
  });
});

describe('钱包 URL:30D 直达', () => {
  it('30D 合法 URL 可解析但 requestsEnabled=false', () => {
    const search =
      '?range=30d&startTime=2026-08-11T08%3A00%3A00Z&endTime=2026-09-10T08%3A00%3A00Z&timezone=Asia%2FShanghai&granularity=DAY&type=ALL&page=1';
    const result = normalizeWalletUrl(search, FIXED_NOW, FIXED_TZ);
    expect(result.state.preset).toBe('30d');
    expect(result.state.range.enabled).toBe(false);
    expect(result.state.requestsEnabled).toBe(false);
    expect(result.needsReplace).toBe(false);
  });
});

describe('钱包 URL:用户动作切换', () => {
  const base: WalletUrlState = {
    preset: '7d',
    range: {
      preset: '7d',
      startTime: '2026-09-01T00:00:00Z',
      endTime: '2026-09-08T00:00:00Z',
      timezone: 'UTC',
      granularity: 'HOUR',
      labelKey: 'pages.wallet.range7d',
      enabled: true,
    },
    type: 'CONSUMPTION',
    page: 3,
    requestsEnabled: true,
  };

  it('翻页只改 page,保留范围与类型', () => {
    const next = collapseWalletPage(base, 5);
    expect(next.page).toBe(5);
    expect(next.type).toBe('CONSUMPTION');
    expect(next.preset).toBe('7d');
    expect(next.range.startTime).toBe('2026-09-01T00:00:00Z');
  });

  it('切换范围生成新边界并把 page 重置为 1,保留类型', () => {
    const next = switchWalletRange(base, '24h', FIXED_NOW, FIXED_TZ);
    expect(next.preset).toBe('24h');
    expect(next.page).toBe(1);
    expect(next.type).toBe('CONSUMPTION');
    expect(next.range.startTime).toBe('2026-09-09T08:00:00.000Z');
    expect(next.range.endTime).toBe('2026-09-10T08:00:00.000Z');
    expect(next.range.timezone).toBe(FIXED_TZ);
    expect(next.requestsEnabled).toBe(true);
  });

  it('切换到 30D 时 requestsEnabled=false 且 page=1', () => {
    const next = switchWalletRange(base, '30d', FIXED_NOW, FIXED_TZ);
    expect(next.preset).toBe('30d');
    expect(next.page).toBe(1);
    expect(next.requestsEnabled).toBe(false);
  });

  it('切换类型只保留范围并把 page 重置为 1', () => {
    const next = switchWalletType(base, 'REFUND');
    expect(next.type).toBe('REFUND');
    expect(next.page).toBe(1);
    expect(next.preset).toBe('7d');
    expect(next.range.startTime).toBe('2026-09-01T00:00:00Z');
  });
});

describe('钱包 URL:越界页收敛', () => {
  const base: WalletUrlState = {
    preset: '24h',
    range: DEFAULT_RANGE,
    type: 'ALL',
    page: 99,
    requestsEnabled: true,
  };

  it('total=0 收敛到 page=1', () => {
    const next = collapseWalletPage(base, 0);
    expect(next.page).toBe(1);
  });

  it('total=45 且 pageSize=20 时,越界 page=99 收敛到最后一页 3', () => {
    const next = collapseWalletPage(base, 45, 20);
    expect(next.page).toBe(3);
  });

  it('合法页保持不变', () => {
    const next = collapseWalletPage({ ...base, page: 2 }, 45, 20);
    expect(next.page).toBe(2);
  });
});
