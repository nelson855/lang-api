## Why

P2-05 已交付消费汇总与统一流水只读接口，但现有钱包页仍只展示当前余额、充值关闭状态和第一阶段充值记录，用户无法在同一页面理解“现在还剩多少”“所选范围消费了多少”和“历史发生了什么”。现在需要完成钱包前端闭环，同时诚实呈现充值、退款和正式货币金额尚不可用的基线限制。

## What Changes

- 将 `/dashboard/wallet` 重组为当前余额、范围内消费、统一流水和既有充值能力说明四个清晰区域，保留第一阶段余额、充值状态与充值记录行为。
- 接入 `GET /portal/api/account/consumption-summary` 与 `GET /portal/api/account/transactions`，使用同一规范化时间范围驱动汇总和流水，前端不合并充值记录、消费日志或推算余额。
- 提供 24H、今天、昨天、7D 和可发现但禁用的 30D 范围，以及全部/充值/消费/退款类型筛选；筛选与分页由 URL 查询参数持久化并可通过刷新、前进和后退恢复。
- 将 quota、正式货币金额、交易方向、单位、状态、备注、来源引用和逐来源 coverage 映射为明确的本地化展示；`UNAVAILABLE`/`PARTIAL` 不得显示成真实零值或普通空列表。
- 统一流水桌面端使用语义表格，窄屏使用等价卡片；覆盖加载、刷新、空数据、不可用、部分覆盖、失败、重试、字段缺失与会话失效状态。
- 支付入口关闭时继续只显示不可操作说明，不新增充值、退款、提现、导出、发票或调账入口，也不把统一流水描述为银行账单或正式发票。
- 增加 API 边界、查询缓存、URL 状态、页面交互、响应式、本地化、可访问性与浏览器状态矩阵验证，并同步 P2-06 说明和阶段状态。

## Capabilities

### New Capabilities

- `wallet-consumption-transactions-page`: 规定钱包页如何组合当前余额、消费汇总、统一流水和既有充值能力，并定义筛选、分页、可用性、响应式与安全交互行为。

### Modified Capabilities

无。现有 `account-balance`、`account-topups` 与 `account-consumption-transactions` 接口和既有页面安全约束保持不变；本变更只新增它们在增强钱包页中的组合展示契约。

## Impact

- 前端 API 边界：扩展 `frontend/src/api/wallet.ts` 的 P2-05 严格 schema、URL 构造与读取函数。
- 前端状态层：扩展 `frontend/src/features/wallet/` 的范围/URL 适配、展示模型、查询键、取消和会话清理。
- 页面与样式：重构 `WalletPage.tsx`/`WalletPage.css`，复用现有 `Button`、`Select`、`DataTable`、`Pagination`、`Feedback`、i18next 与 Clear Circuit 设计令牌。
- 契约文档：新增 P2-06 页面说明并更新阶段总纲；如确认项目 canonical Select 已由原生控件演进为共享 Radix `Select`，实施时同步修正 `UX-CONTRACT.md` 与 `premium-ui.json` 的所有权记录，避免文档与运行时漂移。
- 不修改 Portal API、数据库、后端配置、支付开放条件或第三方依赖。
