# request-log-viewing Specification

## Purpose

让已登录用户分页查看仅属于自己的模型调用结果与基础消耗，同时隔离请求正文、内部渠道、供应商错误和其他敏感日志字段。

## Requirements

### Requirement: 当前用户请求日志接口
系统 SHALL 提供受 Portal 会话保护的 `GET /portal/api/request-logs`，通过统一分页包装返回当前用户的模型请求日志。接口 SHALL 只允许查询 `SUCCESS` 或 `ERROR` 一种结果类型，默认 `SUCCESS`；分别对应 New API 的消费日志和错误日志。充值、退款、管理和系统日志 MUST NOT 出现在该接口中，也不得提供包含两种结果的、不准确的合并分页。

#### Scenario: 默认查询成功请求
- **WHEN** 已登录用户未提供结果类型请求日志
- **THEN** 系统返回该用户的 `SUCCESS` 请求分页，并且每条均来自上游消费日志

#### Scenario: 查询失败请求
- **WHEN** 已登录用户指定 `result=ERROR`
- **THEN** 系统返回该用户的错误请求分页，不混入充值、退款、管理或系统日志

#### Scenario: 匿名查询
- **WHEN** 未登录或会话失效的客户端请求 `/portal/api/request-logs`
- **THEN** 系统返回 `UNAUTHENTICATED`/401，且不访问任何管理员日志接口

#### Scenario: 请求全部类型
- **WHEN** 客户端提交空白之外且不是 `SUCCESS` 或 `ERROR` 的结果类型
- **THEN** 系统返回 `INVALID_ARGUMENT`/400，不降级为查询全部 New API 日志

### Requirement: 服务端分页和筛选
请求日志 SHALL 支持 `page`、`pageSize`、可选 `keyName`、可选 `model`、`result`、`startTime` 与 `endTime`。页码从 1 开始，默认页大小为 20、最大为 100；Key 名称使用精确匹配，模型使用不区分大小写的安全包含匹配。所有筛选 MUST 在 New API 用户日志查询中执行，Portal 与浏览器均不得先拉取未过滤页再计算总数或重建分页。

#### Scenario: 使用组合筛选
- **WHEN** 用户同时指定 Key 名称、模型、结果类型和合法时间范围
- **THEN** 返回的 `items` 与 `total` 均对应同一组上游筛选条件

#### Scenario: 模型筛选包含通配字符
- **WHEN** 模型搜索文本包含 `%`、`_`、`!` 或其他可改变 LIKE 语义的字符
- **THEN** 系统将其作为普通文本安全转义，不能扩大查询范围或产生上游查询错误

#### Scenario: 页大小超过上限
- **WHEN** `pageSize` 大于 100、非正数或不是整数
- **THEN** 系统返回 `INVALID_ARGUMENT`/400，不向上游发送修正后的模糊请求

#### Scenario: 请求空页
- **WHEN** 过滤条件合法但指定页没有结果
- **THEN** 系统返回成功分页、空 `items` 和真实 `total`，不伪造上一页数据

### Requirement: 统一时间范围语义
请求日志的时间参数 SHALL 使用带时区的 ISO 8601 字符串，并采用秒级精度、左闭右开 `[startTime,endTime)`。`startTime` 与 `endTime` 必须同时提供或同时省略；省略时使用服务端当前时间之前的最近 24 小时。开始时间必须早于结束时间，跨度 MUST NOT 超过 30 天；Portal SHALL 将右端不包含语义安全转换为 New API 的秒级右端包含参数。

#### Scenario: 省略时间范围
- **WHEN** 用户未提供开始和结束时间
- **THEN** 系统以同一服务端时钟快照计算最近 24 小时并查询

#### Scenario: 查询边界时刻
- **WHEN** 两条日志分别位于 `startTime` 和 `endTime`
- **THEN** 返回开始时刻的日志且不返回结束时刻的日志

#### Scenario: 非法时间范围
- **WHEN** 只提供一个边界、时间不含时区、包含亚秒、开始不早于结束或跨度超过 30 天
- **THEN** 系统返回 `INVALID_ARGUMENT`/400，响应不包含上游原始错误

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

### Requirement: 日志隐私与内部字段隔离
Portal 请求日志 MUST NOT 返回或记录 New API 的 `content`、`other`、IP、username、user ID、token ID、channel ID/name、group、供应商信息、私网地址或上游错误正文。上游分页中的序号 ID 不得作为稳定日志 ID 对外承诺；页面只可将 requestId 用于用户支持定位，且 requestId 缺失时不得生成伪上游标识。

#### Scenario: 上游日志包含调试扩展
- **WHEN** 上游 `other` 或 `content` 包含渠道、节点、供应商消息、提示词或响应片段
- **THEN** Portal 响应、异常和访问日志均不包含这些内容

#### Scenario: 用户尝试越权筛选
- **WHEN** 客户端提交 userId、username、channel、group、tokenId 或其他未声明查询参数
- **THEN** 系统拒绝请求为 `INVALID_ARGUMENT`/400，且用户身份只取自服务端会话上下文

### Requirement: 请求日志页面状态
控制台 SHALL 在 `/dashboard/request-logs` 提供请求日志页面，包含 Key 名称、模型、结果类型和时间范围筛选、分页及刷新。页面 MUST 提供加载、空结果、错误和会话失效状态；筛选提交 SHALL 重置到第一页，单纯翻页不得丢失当前筛选。

页面 SHALL 从 URL 查询参数读取并规范化 `page`、`result`、`keyName`、`model`、`startTime` 和 `endTime`，仅接受请求日志接口支持的值和成对合法时间边界；无效或不完整参数 MUST 安全回退到页面默认值，不得直接转发未知参数。已提交筛选与页码 MUST 回写 URL，使刷新、前进后退和站内链接可以恢复同一查询。通过 Dashboard 最近请求进入时，页面 SHALL 保留 Dashboard 的明确时间边界、`SUCCESS` 结果以及条目中非空的 Key 名称和模型；若该范围不是页面快捷项，时间控件 MUST 显示为来自 Dashboard 的明确范围而不是伪装成另一个预设。

#### Scenario: 提交筛选
- **WHEN** 用户修改一个或多个筛选条件并确认查询
- **THEN** 页面从第一页请求相同条件、展示服务端 total，并把规范化条件写入 URL，不在浏览器重算分页

#### Scenario: 当前范围无日志
- **WHEN** 请求成功且 `items` 为空
- **THEN** 页面说明当前筛选没有请求记录，并保留修改筛选和刷新操作

#### Scenario: 会话在查询时过期
- **WHEN** 日志接口返回 `UNAUTHENTICATED`
- **THEN** 页面清除已认证缓存并进入带安全 returnTo 的登录流程，不继续显示旧日志

#### Scenario: 从 Dashboard 最近请求下钻
- **WHEN** 用户选择一条带有 Key 名称和模型的最近成功请求进入请求日志
- **THEN** 目标 URL 和首次查询使用 Dashboard 响应的起止时间、`result=SUCCESS`、该 Key 名称和模型，并从第 1 页展示匹配日志

#### Scenario: 最近请求缺少可选筛选字段
- **WHEN** 用户下钻的最近请求缺少 Key 名称或模型
- **THEN** 页面省略对应筛选而保留明确时间范围和成功结果，不伪造空字符串以外的标识

#### Scenario: URL 参数非法
- **WHEN** 页面 URL 含未知结果类型、单边时间、非法时间或超范围页码
- **THEN** 页面不把非法值发送给 Portal API，使用安全默认查询并以规范化 URL 替换非法状态

#### Scenario: 浏览器导航恢复查询
- **WHEN** 用户在筛选或翻页后使用浏览器前进、后退或刷新
- **THEN** 筛选控件、页码和服务端查询恢复为 URL 表示的同一状态，不显示较新或较旧条件的竞态结果

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
