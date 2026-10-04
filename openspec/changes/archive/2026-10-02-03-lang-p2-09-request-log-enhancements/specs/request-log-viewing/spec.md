## MODIFIED Requirements

### Requirement: 请求日志字段和缺失值
每条日志 SHALL 返回 occurredAt、可空 requestId/keyName/model、result、非负 inputTokens/outputTokens/durationMs、stream、非负 quota、十进制 USD 费用及既有可空 protocol/firstTokenLatencyMs。消费日志映射 SUCCESS，错误日志映射 ERROR；两个增强字段仅保留 API 兼容，当前 SHALL 返回 null，不从模型、路径、流式状态或总耗时推断或新增映射。

前端 SHALL 兼容两个可选字段缺失、null 或异常值，不影响合法基础页；基础时间、Token、总耗时、quota、金额及未声明字段仍严格校验。取消两项展示 MUST NOT 改变分页、错误码、用户隔离或隐私裁剪。

#### Scenario: 映射成功流式调用
- **WHEN** 上游消费日志包含模型、Key 名称、Token、秒级耗时、流式标识、quota 和 requestId
- **THEN** Portal 使用 ISO 8601 UTC 时间、毫秒耗时、十进制 USD 费用和 SUCCESS 返回基础字段，并保留两个增强字段为 null

#### Scenario: 映射错误调用
- **WHEN** 上游错误日志的 Token 与 quota 为零且缺少 requestId 或 Key 名称
- **THEN** Portal 返回 ERROR、真实零值和对应 null 字段，不补造请求标识或消费

#### Scenario: 字段数值非法
- **WHEN** 上游返回负 Token、负耗时、负 quota、非法时间或无法表示的数值
- **THEN** 整个响应按 UPSTREAM_ERROR 安全失败，不返回部分可信日志页

#### Scenario: 历史响应省略增强字段
- **WHEN** 客户端收到基础字段合法但省略 protocol 和 firstTokenLatencyMs 的响应
- **THEN** 页面仍展示基础日志，不显示协议或首 Token 延迟，也不因两个可选字段缺失使整页失败

## ADDED Requirements

### Requirement: 请求日志费用按统一后端口径完整展示
请求日志 SHALL 继续使用原始 quota 和统一服务端换算口径产生十进制字符串 `amount` 与 `currency=USD`。页面 MUST 完整保留金额数值精度和币种，不做浮点转换、二次换算或按模型目录价格重新计算。真实零费用 SHALL 显示零，不推断为免费模型。页面 SHALL 提供可访问的原始 quota 和说明：金额来自日志额度的配置换算，列表不是额外收费项明细或正式账单。

费用核对 SHALL 在相同日志集合与时间范围下比较原始 quota 和统一换算规则；各条已舍入金额的前端求和 MUST NOT 被用作 Dashboard 或消费汇总的权威。独立换算证据不足时 MUST 保留限制，不声称已验证真实扣费与 USD 等值。

#### Scenario: 极小费用和较大额度
- **WHEN** 接口返回 amount="0.000001" 或超过浏览器安全整数范围的 quota 十进制字符串
- **THEN** 页面展示完整原值和 USD/quota 单位，不显示科学计数法、不截断为零且不转换 quota 为浮点数

#### Scenario: 核对同范围费用
- **WHEN** 请求日志与消费汇总覆盖同一组成功消费记录
- **THEN** 验证使用相同原始 quota 与后端换算规则，允许记录逐条舍入与合计后舍入的差异，不在浏览器修改正式汇总

### Requirement: 基础日志页面保持已有查询闭环
/dashboard/request-logs SHALL 展示时间、Key、模型、流式状态、输入/输出 Token、总耗时、结果、费用和请求 ID，MUST NOT 展示协议、首 Token 延迟或对应说明，即使可选响应字段包含非空值。桌面和移动卡片 SHALL 保持一致。

页面 SHALL 沿用已有筛选、服务端分页、URL 恢复、Dashboard 下钻、会话失效清理和防迟到响应覆盖机制；MUST NOT 新增协议/TTFT 筛选。中英文、键盘和 375px 窄屏 SHALL 能访问所有保留字段与费用说明，整页不得横向溢出。加载、空结果、错误和重试 SHALL 保持区域稳定，错误时不把旧结果标为当前成功查询。

#### Scenario: 取消的可选字段包含值
- **WHEN** 接口返回合法基础日志及非空 protocol 或 firstTokenLatencyMs
- **THEN** 页面只展示保留字段，不显示这两项的标题、值、占位或 TTFT 说明

#### Scenario: 下钻与浏览器导航
- **WHEN** 用户从 Dashboard 进入日志，再筛选翻页并前进后退或刷新
- **THEN** 控件与查询恢复为 URL 的同一组条件，不改变 total 或引入额外全量查询

#### Scenario: 窄屏与键盘读取费用说明
- **WHEN** 用户在 375px 或英文界面使用键盘访问费用说明
- **THEN** 完整金额、quota、总耗时、流式状态、长模型与请求 ID 可访问，说明不依赖悬停或颜色

#### Scenario: 认证失效和迟到响应
- **WHEN** 日志加载时会话失效或旧筛选响应晚于当前查询返回
- **THEN** 清理认证缓存并进入登录流程，旧响应不恢复旧用户数据或覆盖当前条件
