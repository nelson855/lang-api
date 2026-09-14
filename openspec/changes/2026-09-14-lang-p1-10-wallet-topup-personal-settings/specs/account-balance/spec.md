## MODIFIED Requirements

### Requirement: 余额页面表达
Dashboard 和 `/dashboard/wallet` SHALL 将余额显示为当前值而不是所选历史区间统计，并各自提供独立加载、错误和刷新状态。页面 MUST 同时标明 USD 与 quota 语义；Dashboard 只展示余额摘要并可导航到钱包，钱包复用同一余额接口展示余额、充值能力状态与充值记录，不得从记录推算或覆盖权威余额。

#### Scenario: Dashboard 显示余额
- **WHEN** 余额接口成功返回
- **THEN** Dashboard 显示当前 USD 金额和 quota 辅助信息，并明确其不随历史时间筛选变化

#### Scenario: 钱包显示同一余额
- **WHEN** 用户从 Dashboard 进入钱包且余额接口成功返回
- **THEN** 钱包显示与重新读取的权威余额一致的 USD 和 quota，不使用 Dashboard 旧快照或充值记录自行计算

#### Scenario: 余额查询失败
- **WHEN** 余额接口失败但同页其他只读接口成功
- **THEN** 余额区域显示错误和重试，其他成功区域继续展示
