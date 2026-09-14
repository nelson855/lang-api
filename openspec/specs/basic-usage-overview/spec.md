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
`/dashboard` SHALL 展示可用余额、区间消费、当前 RPM/TPM、小时请求量和小时消费趋势。余额、摘要和趋势 MUST 作为独立查询与独立状态区域加载；其中一项失败不得清空或阻塞其他已成功区域。页面 SHALL 提供最近 24 小时默认范围及不超过 30 天的时间选择，并使用用户当前时区显示 UTC 数据。

#### Scenario: 三个查询全部成功
- **WHEN** 余额、摘要和趋势均成功返回
- **THEN** Dashboard 展示对应卡片与小时趋势，并标清金额单位、查询区间和 RPM/TPM 的一分钟窗口

#### Scenario: 趋势查询失败
- **WHEN** 余额和摘要成功但趋势接口失败
- **THEN** 页面保留余额与摘要，仅在趋势区域显示错误和重试操作

#### Scenario: 趋势为空
- **WHEN** 趋势接口成功返回空点数组
- **THEN** 页面显示该区间暂无用量，不绘制误导性的零值历史数据

#### Scenario: 切换时间范围
- **WHEN** 用户选择另一个合法范围
- **THEN** 摘要和趋势使用同一新范围重新查询，余额不因范围变化而重复计算历史值
