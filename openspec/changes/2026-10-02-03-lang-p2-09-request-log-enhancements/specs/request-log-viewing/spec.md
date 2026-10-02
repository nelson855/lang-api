## MODIFIED Requirements

### Requirement: 请求日志字段和缺失值
每条日志 SHALL 返回 `occurredAt`、可空 `requestId`、可空 `keyName`、可空 `model`、`result`、非负 `inputTokens`、非负 `outputTokens`、非负 `durationMs`、`stream`、非负额度值、USD 费用，以及可空 `protocol` 和 `firstTokenLatencyMs`。上游消费日志映射为 `SUCCESS`，错误日志映射为 `ERROR`；协议类型和首 Token 延迟 SHALL 只使用当前部署版本已验证的字段映射；没有可靠来源、覆盖范围不适用或历史记录缺失时 MUST 返回 `null`，不得从模型名、路径、端点能力、流式标识或总耗时推断。当前冻结基线 `p2-2026-09-22-a` 未验证这两个字段，MUST 继续返回 `null`。

服务端 SHALL 保持原有字段名称、类型、成功/错误分页及错误码，正常响应明确包含可空增强字段。前端 MUST 兼容旧响应省略 `protocol` 或 `firstTokenLatencyMs`，将缺失值归一为 `null`。基础 Token、总耗时、quota 与金额字段仍采用严格校验，不因增强字段可空而接受非法基础数据。

#### Scenario: 映射成功流式调用
- **WHEN** 上游消费日志包含模型、Key 名称、Token、秒级耗时、流式标识、quota 和 requestId
- **THEN** Portal 使用 ISO 8601 UTC 时间、毫秒耗时、十进制 USD 费用和 `SUCCESS` 返回已声明字段

#### Scenario: 映射错误调用
- **WHEN** 上游错误日志的 Token 与 quota 为零且缺少 requestId 或 Key 名称
- **THEN** Portal 返回 `ERROR`、真实零值和对应 `null` 字段，不补造请求标识或消费

#### Scenario: 字段数值非法
- **WHEN** 上游返回负 Token、负耗时、负 quota、非法时间或无法表示的数值
- **THEN** 整个上游响应按 `UPSTREAM_ERROR` 安全失败，不返回部分可信日志页

#### Scenario: 历史响应省略增强字段
- **WHEN** 客户端收到基础字段合法但省略 protocol 和 firstTokenLatencyMs 的旧响应
- **THEN** 页面正常显示基础日志并将两项显示为暂无数据，不因缺少可选字段使整页失败

#### Scenario: 冻结基线含未经验证的扩展字段
- **WHEN** 当前基线日志出现名为 protocol 或 first_token_time 的值但没有已验证语义和单位
- **THEN** Portal 仍返回两个 null，不按名称相似性自动启用映射


## ADDED Requirements

### Requirement: 日志增强字段启用具有可追溯证据
系统 MUST 在启用任何协议或 TTFT 非空映射前记录部署版本、来源接口、精确字段路径、原始类型、协议枚举映射、耗时单位、测量起止点、适用协议与流式范围及脱敏真实样本。模拟测试和上游源码说明 MUST NOT 替代部署版本的实测证据。证据缺失时 SHALL 交付可空展示并记录外部阻塞，不宣称增强数据验收完成。

系统 MUST NOT 为此采集提示词或响应正文、写入历史日志、增加网关计时埋点或调用管理员日志接口。已有总耗时与 TTFT MUST 各自保持独立含义，不由总耗时计算 TTFT，也不得假定非流式或错误记录具有 TTFT。

#### Scenario: 真实字段证据仍不足
- **WHEN** 实测没有协议或首 Token 延迟字段，或无法证明字段单位和测量含义
- **THEN** 相应字段保持 null，验证记录列出缺失证据，任务保留未完成的外部验收项

#### Scenario: 单项或部分协议具备证据
- **WHEN** 后续证据只支持某个协议的某种流式成功记录的 TTFT
- **THEN** 更新字段映射契约后仅为该范围返回真实值，其他协议、非流式、错误与历史记录保持 null

### Requirement: 可选增强值不得污染基础日志
`firstTokenLatencyMs` SHALL 表达非负整数毫秒；已验证且可表示的零值 MUST 保留为零。缺失、负数、非有限值、非法类型、转换溢出或超出浏览器安全整数范围的增强耗时 MUST 归一为 `null`，不得影响基础字段合法的整页。Portal 协议展示词表 SHALL 限定为 `OPENAI`、`ANTHROPIC`、`GEMINI`，词表本身不证明任何上游映射或公共协议已开放。协议 SHALL 只采用已验证的受控映射值，未知、空白、异常类型或可疑文本 MUST 归一为 `null`，不得原样透传。正常 Portal 响应 SHALL 符合此契约；前端对异常可选值 SHALL 防御性降级，不放宽基础字段校验。

#### Scenario: 已验证 TTFT 为零
- **WHEN** 受支持记录的合法 firstTokenLatencyMs 为 0
- **THEN** 页面显示 0 ms，不显示暂无数据，也不将其解释为总耗时为零

#### Scenario: 增强字段异常而基础字段合法
- **WHEN** 一页中某条记录的可选协议未知或 TTFT 为负数、异常类型或不安全整数
- **THEN** 仅将对应增强字段降级为 null，基础日志、分页总数与其他条目仍可展示

#### Scenario: 基础字段非法
- **WHEN** 同一记录的 Token、quota、时间或总耗时违反原有基础契约
- **THEN** 请求保持原有安全失败行为，不用可选字段降级规则掩盖基础数据错误

### Requirement: 请求日志费用按统一后端口径完整展示
请求日志 SHALL 继续使用原始 quota 和统一服务端换算口径产生十进制字符串 `amount` 与 `currency=USD`。页面 MUST 完整保留金额数值精度和币种，不做浮点转换、二次换算或按模型目录价格重新计算。真实零费用 SHALL 显示零，不推断为免费模型。页面 SHALL 提供可访问的原始 quota 和说明：金额来自日志额度的配置换算，列表不是额外收费项明细或正式账单。

费用核对 SHALL 在相同日志集合与时间范围下比较原始 quota 和统一换算规则；各条已舍入金额的前端求和 MUST NOT 被用作 Dashboard 或消费汇总的权威。独立换算证据不足时 MUST 保留限制，不声称已验证真实扣费与 USD 等值。

#### Scenario: 极小费用和较大额度
- **WHEN** 接口返回 amount="0.000001" 或超过浏览器安全整数范围的 quota 十进制字符串
- **THEN** 页面展示完整原值和 USD/quota 单位，不显示科学计数法、不截断为零且不转换 quota 为浮点数

#### Scenario: 核对同范围费用
- **WHEN** 请求日志与消费汇总覆盖同一组成功消费记录
- **THEN** 验证使用相同原始 quota 与后端换算规则，允许记录逐条舍入与合计后舍入的差异，不在浏览器修改正式汇总

### Requirement: 增强日志页面保持已有查询闭环
`/dashboard/request-logs` SHALL 增加协议、TTFT 与流式状态，保留时间、Key、模型、结果、输入/输出 Token、总耗时、费用与请求 ID。TTFT SHALL 有本地化说明并与总耗时分别标注毫秒单位；未知值显示“暂无数据”，不得回填为零、OpenAI 或总耗时。真实协议展示 MUST NOT 暗示公共网关已开放该协议。

页面 SHALL 沿用已有筛选、服务端分页、URL 恢复、Dashboard 下钻、会话失效清理和防迟到响应覆盖机制。本变更 MUST NOT 增加协议或 TTFT 的前端页内筛选及未被上游支持的查询参数。中英文、键盘和 375px 窄屏 MUST 能访问所有字段及说明；表格可局部滚动，整页不得横向溢出。加载、空结果、错误和重试 SHALL 保持控件和表格区域稳定，错误时不得把旧结果标为当前成功查询。

#### Scenario: 全部增强字段缺失
- **WHEN** 当前基线返回合法基础日志且所有协议和 TTFT 都为 null
- **THEN** 页面展示基础数据、两个暂无数据及字段说明，保持筛选、刷新和翻页可用

#### Scenario: 混合可用性
- **WHEN** 一页包含已验证协议、合法 TTFT、历史 null、流式与非流式记录
- **THEN** 页面逐条展示准确值和状态，不把其他条目的增强值套用于缺失记录

#### Scenario: 下钻与浏览器导航
- **WHEN** 用户从 Dashboard 进入日志，再筛选翻页并前进后退或刷新
- **THEN** 控件与查询仍恢复为 URL 的同一组条件，增强展示不改变 total、不引入额外的全量日志请求

#### Scenario: 窄屏与键盘读取说明
- **WHEN** 用户在 375px 宽度或英文界面使用键盘访问 TTFT 与费用说明
- **THEN** 说明不依赖悬停或颜色，完整金额、quota、长模型 ID 和请求 ID 可访问，滚动局限于表格区域

#### Scenario: 认证失效和迟到响应
- **WHEN** 增强日志加载时会话失效或先前筛选的响应晚于当前查询返回
- **THEN** 页面沿用安全登录与缓存清理，旧响应不会恢复旧用户数据或覆盖当前条件
