## MODIFIED Requirements

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

