# basic-usage-overview Specification

## Purpose

基于 New API 当前用户统计和小时数据提供有限但可解释的消费、RPM/TPM 与趋势概览，不从分页日志重算正式统计。

## Requirements

### Requirement: 当前用户用量摘要
系统 SHALL 提供受保护的 `GET /portal/api/usage/summary`，将 New API `/api/log/self/stat` 返回为当前用户在所选时间范围内的消费 quota、USD 显示金额，以及最近 60 秒窗口的 `rpm` 和 `tpm`。响应 MUST 明确 `rateWindowSeconds=60`，不得把 RPM/TPM 描述为整个查询区间的平均值，也不得承诺冻结版未提供的输入/输出 Token 总计、成功率或平均延迟。

#### Scenario: 查询用量摘要
- **WHEN** 已登录用户使用合法时间范围查询摘要
- **THEN** 系统返回该范围消费和当前一分钟 RPM/TPM，并明确速率窗口为 60 秒

#### Scenario: 当前范围无消费
- **WHEN** 上游返回合法零值
- **THEN** 系统返回零 quota、零金额、零 RPM 和零 TPM，不把零值当作缺失数据

#### Scenario: 上游统计字段缺失
- **WHEN** 上游缺少 quota、rpm 或 tpm，或返回负值、非法类型
- **THEN** 系统返回安全 `UPSTREAM_ERROR`，不以零值掩盖结构异常

### Requirement: 小时级用量趋势
系统 SHALL 提供受保护的 `GET /portal/api/usage/timeseries`，按 New API `/api/data/self` 当前一小时粒度返回 `granularity=HOUR` 和按时间升序的点。Portal SHALL 将相同小时的不同模型行合并，每个点包含桶开始时间、请求数、Token 数、消费 quota 和 USD 金额；不得更改为日/周/月粒度，也不得从分页请求日志生成趋势。

#### Scenario: 合并同一小时的模型行
- **WHEN** 上游在同一 UTC 小时返回多个模型的 quota 数据
- **THEN** Portal 返回一个小时点，其请求数、Token、quota 和金额是这些合法行的精确合计

#### Scenario: 保留小时粒度
- **WHEN** 用户查询跨越多个小时的范围
- **THEN** 返回点均以 UTC 整点开始、按时间升序排列，不插值、不移动到浏览器时区且不补造无数据小时

#### Scenario: 小时数据尚未落盘
- **WHEN** 请求日志已有新消费但 New API 的异步小时数据暂未返回对应点
- **THEN** 趋势按上游现状返回且页面提示趋势可能延迟，不使用分页日志即时补齐

#### Scenario: 上游趋势行非法
- **WHEN** 上游返回非整点时间、负请求数、负 Token、负 quota、用户标识异常或范围外数据
- **THEN** 整个趋势响应按 `UPSTREAM_ERROR` 失败，不展示部分趋势

### Requirement: 用量接口共用时间契约
摘要和趋势 SHALL 共用带时区 ISO 8601、秒级、左闭右开 `[startTime,endTime)` 时间语义；省略两个边界时默认最近 24 小时，跨度最大 30 天。两端和校验结果 MUST 在同一页面查询中保持一致，使摘要与趋势代表相同区间；非法范围 SHALL 在访问 New API 前返回 `INVALID_ARGUMENT`。

#### Scenario: Dashboard 使用同一区间
- **WHEN** 用户在 Dashboard 选择一个时间范围
- **THEN** 摘要和趋势请求使用完全相同的 `startTime` 与 `endTime`

#### Scenario: 时间范围超过上限
- **WHEN** 摘要或趋势请求跨度超过 30 天
- **THEN** 系统返回 `INVALID_ARGUMENT`/400，不自动截断为另一个区间

### Requirement: Dashboard 基础概览与局部失败
`/dashboard` SHALL 保留独立的当前余额区域，并使用 `GET /portal/api/dashboard/stats` 的同一响应展示请求总数、Token 用量、消费总额、活跃 Key、成功率、平均延迟、请求趋势、消费趋势和最多 10 条最近请求。页面 MUST 依据每项或每组数据的 `availability`、`reasonCode`、单位和币种决定展示：真实可用零值 SHALL 显示为零；不可用、无数据和部分来源覆盖 MUST 使用可区分的本地化状态表达，不得以零、100%、空白卡片或推算值冒充。余额与历史消费 MUST 明确分区和说明，历史时间筛选不得改变当前余额含义。

Dashboard SHALL 提供 1H、24H、今天、昨天、7D 和 30D 快捷范围。前端 MUST 使用一次时钟快照和浏览器解析出的 IANA 时区将快捷项转换为秒级、左闭右开 `[startTime,endTime)`；无法取得有效 IANA 时区时使用 `UTC`。1H 使用 `FIVE_MINUTES`，24H、今天、昨天和 7D 使用 `HOUR`，30D 使用 `DAY`；今天为本地自然日开始至当前时刻，昨天为完整前一自然日，滚动范围以当前时刻为右边界。当前服务端仍限制 7 天实时扫描时，30D 项 MUST 可见但标记为暂不可查询并阻止请求；不得改用旧用量接口、浏览器日志汇总或缩短范围降级。

#### Scenario: Dashboard 加载统一统计快照
- **WHEN** 已登录用户进入 Dashboard 且余额与统计接口均成功
- **THEN** 页面独立展示当前余额，并用一个统计响应中的规范化范围和口径版本呈现六项指标、双趋势状态和最近请求，不再调用旧摘要与小时趋势接口生成正式概览

#### Scenario: 三个查询全部成功
- **WHEN** 第一阶段兼容页面仍使用余额、摘要和趋势三个查询且均成功返回
- **THEN** 旧页面继续按原契约展示余额、摘要与小时趋势；升级到 P2-04 后由独立余额和单一 stats 查询取代该三查询编排

#### Scenario: 快捷滚动范围
- **WHEN** 用户选择 1H、24H 或 7D
- **THEN** 页面以同一秒级当前时刻为右边界生成对应跨度和规定粒度，并把解析出的 IANA 时区随四个必填参数发送给统计接口

#### Scenario: 用户时区中的自然日
- **WHEN** 用户在存在夏令时变化的 IANA 时区选择今天或昨天
- **THEN** 页面按该时区的自然日边界生成 23、24 或 25 小时的真实区间，横轴和范围说明使用同一时区，且相邻昨天与今天边界不重叠也不留空

#### Scenario: 当前 30 天能力受阻
- **WHEN** 当前发布仍采用 P2-03 的 7 天实时扫描保护边界
- **THEN** 30D 快捷项保持可发现并说明暂不可查询，页面不发送已知无效请求、不回退旧接口，也不展示伪造的 30 天数据

#### Scenario: 指标不可用与真实零值
- **WHEN** 同一响应中 Token 用量为 `AVAILABLE` 且值为零，而请求总数为 `UNAVAILABLE + BASELINE_NOT_VERIFIED`
- **THEN** Token 卡片显示带单位的零值，请求总数卡片显示不可用原因，不将两者绘制成相同的“0”状态

#### Scenario: 趋势不可用
- **WHEN** 请求趋势或消费趋势返回 `UNAVAILABLE` 和空点数组
- **THEN** 对应趋势区域展示本地化不可用说明和口径提示，不绘制全零折线、柱形或其他推断历史

#### Scenario: 趋势查询失败
- **WHEN** 统一 stats 查询失败而无法取得趋势状态，但余额查询成功
- **THEN** 页面保留余额，只在统计内容区域显示错误和重试，不调用旧趋势接口或绘制缓存中的其他范围趋势

#### Scenario: 趋势为空
- **WHEN** 趋势标记为 `AVAILABLE` 但合法返回空点数组
- **THEN** 页面显示所选范围暂无趋势数据，不绘制误导性的零值历史；若趋势为 `UNAVAILABLE`，则优先显示其不可用原因

#### Scenario: 最近请求部分覆盖
- **WHEN** 最近请求返回 `PARTIAL + PARTIAL_SOURCE_COVERAGE`
- **THEN** 页面展示安全裁剪条目并在同一区域明确提示当前仅覆盖已验证来源，不将列表描述为全部请求

#### Scenario: 统一统计失败但余额成功
- **WHEN** 当前余额加载成功而 Dashboard 统计查询失败
- **THEN** 页面保留余额与钱包入口，在统计内容区域显示稳定错误和单次重试操作，不用旧接口拼接降级结果

#### Scenario: 切换时间范围
- **WHEN** 用户选择另一个当前可查询的快捷范围
- **THEN** 页面生成一次新的明确范围并只重新查询 stats，余额不因历史范围变化而重新定义或清空

#### Scenario: 会话失效
- **WHEN** 余额或统计请求返回 `UNAUTHENTICATED`
- **THEN** 页面执行统一会话失效流程、清除用户作用域缓存并进入带安全 returnTo 的登录流程，不继续显示旧用户快照
