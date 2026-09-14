## Context

参见 [proposal.md](./proposal.md) 的动机与范围。P1-04 已建立 `/models`、`/docs` 公开路由、统一 API 客户端、TanStack Query、中英文基础设施和响应式公开布局，但两个页面仍是无伪数据的占位页。P1-07 已通过独立模型域名精确开放 `GET /v1/models` 与 `POST /v1/chat/completions`，并由 `/portal/api/public-config` 在运行时发布已启用的 `OPENAI` Base URL。

冻结版 New API v0.13.2 的 `/api/pricing` 已验证可匿名访问，其 `data` 来源于启用渠道能力，并同时返回厂商、分组倍率、支持端点和定价版本。当前本地样例因为没有配置真实渠道而返回空 `data`，因此实现和自动化测试必须使用脱敏 fixture，最终模型与价格验收则依赖运维配置真实渠道、模型和测试额度。

New API 的定价条目有两种基础计费方式：`quota_type=0` 使用模型倍率与输出倍率按 Token 计费，`quota_type=1` 使用 `model_price` 按次计费。用户组倍率、缓存/图片/音频倍率和未来计费表达式会改变特定请求的最终费用，但第一阶段公共模型广场只展示默认组的基础聊天价格，不复制完整计费引擎。

## Goals / Non-Goals

**Goals:**

- 在 New API 适配边界内把 `/api/pricing` 转换为稳定、最小、无内部字段的公开模型 DTO。
- 保持公开目录与 New API 当前启用能力一致，同时清楚区分“全局已列出”和“某个 Key 最终可调用”。
- 集中完成价格换算、精度控制和单位标注，使页面价格可以通过真实请求日志抽样核对。
- 用一个短期缓存吸收公开页面重复读取，不增加数据库、同步任务或跨节点一致性机制。
- 让模型选择、运行时 Base URL、cURL 和 OpenAI SDK 示例形成可复制的闭环，并覆盖加载失败、空目录和协议未启用状态。

**Non-Goals:**

- 不按登录用户、用户组、余额或 API Key 限制生成个性化模型目录。
- 不建立模型表、厂商表、运营后台或静态厂商推断规则。
- 不展示上下文窗口、发布日期、输入/输出模态、能力标签、缓存价、图片价、音频价或阶梯计费详情。
- 不开放新的模型协议路径，不修改 P1-07 网关，也不实现 Playground、文档搜索、版本化或服务端 MDX 渲染。
- 不保证目录中的每个模型对每个 Key 都可用；最终状态仍由 New API 数据面实时校验。

## Decisions

### 1. 模型目录只调用匿名 `/api/pricing`

Portal API 新增语义化 `pricing` 上游操作，固定使用 `GET /api/pricing`，不携带浏览器 Cookie、Portal 会话、Authorization 或 `New-Api-User`。业务层只接收已解析的上游定价快照，Controller 返回 Lang API DTO。

目录不再与需登录的 `/api/models` 求交集。这样匿名与登录用户看到相同公共目录，前端页面无需引入会话分支，缓存键也只有一个。New API 已根据启用渠道能力和当前可用组裁剪 `/api/pricing`；Portal 将返回条目视为“全局已列出”，页面同时提示 Key 状态、额度和模型限制以真实调用为准。

备选方案是登录时再调用 `/api/models` 或使用用户 Key 请求 `/v1/models`。前者让公共页面产生匿名/登录双重语义和用户级缓存风险，后者要求浏览器处理明文 Key，均不采用。自建模型目录能提供更丰富元数据，但超出 MVP。

### 2. 适配器严格裁剪上游字段并按模型 ID 排序

`upstream.newapi.pricing` 只声明本阶段需要的上游字段：模型的 `model_name`、`vendor_id`、`quota_type`、`model_ratio`、`model_price`、`completion_ratio`，厂商的 `id`、`name`，以及顶层 `pricing_version`。未知字段由 JSON 映射忽略，`group_ratio`、`usable_group`、`enable_groups`、渠道和支持端点不越过适配边界。

映射先建立本次响应内的厂商 ID→名称表，再逐条生成模型 DTO。模型 ID 去除首尾空白后必须非空、长度有界且无控制字符；重复 ID、非法数值或顶层结构矛盾使整个快照失败，避免缓存部分目录。厂商只有唯一有效关联时返回名称，否则为 `null`。`displayName` 在冻结接口没有明确字段，因此第一阶段返回 `null`，页面始终以原始模型 ID 为主标签。

输出按模型 ID 的 Unicode 码点顺序稳定排序，既便于缓存和快照测试，也为文档提供确定性默认模型。备选的名称前缀推断厂商虽然展示更丰富，但会随模型命名变化产生错误事实，不采用。

### 3. 使用显式 DTO 表达全局状态与两种价格模式

接口成功正文采用：

```json
{
  "requestId": "req_xxx",
  "data": {
    "pricingVersion": "...",
    "models": [
      {
        "id": "gpt-example",
        "displayName": null,
        "provider": "Example",
        "availability": "AVAILABLE",
        "pricing": {
          "mode": "TOKEN",
          "currency": "USD",
          "unit": "PER_MILLION_TOKENS",
          "input": "2.500000",
          "output": "10.000000",
          "request": null
        }
      }
    ]
  }
}
```

凡是合法出现在 `/api/pricing.data` 的条目，`availability` 为 `AVAILABLE`，其含义固定为“New API 当前公共定价目录已列出”，不是 Key 级承诺。若模型本身合法但价格无法安全换算，保留模型并返回 `pricing=null`；页面展示“价格暂不可用”，不把零值解释成免费。

价格金额使用非负十进制字符串，固定最多六位小数、去除无意义尾零但至少保留一位小数。`TOKEN` 只使用 `input`、`output`，单位是每百万 Token；`REQUEST` 只使用 `request`，单位是每次请求。JSON 使用一致字段并以 `null` 表示不适用，降低前端联合类型解析歧义。

备选的原样返回倍率最接近上游，但用户无法将“倍率”理解为价格且会泄露内部计费结构；返回 JavaScript number 则有精度和格式漂移，不采用。

### 4. 价格换算只覆盖默认组基础聊天价格

后端使用 `BigDecimal` 完成换算。共享配置新增 `lang.portal.catalog.quota-per-usd`，其值必须与目标 New API 的 `quota_per_unit` 一致且为正整数；默认采用冻结环境已验证的 `500000`。`quota_type=0` 的基础输入价计算为：

```text
input USD / 1M tokens = model_ratio × 1,000,000 ÷ quota_per_usd
output USD / 1M tokens = input price × completion_ratio
```

`quota_type=1` 的基础每请求价格直接使用 `model_price`。两种模式均不叠加用户组倍率；页面标为“默认组基础价格”，真实抽样使用默认组测试用户。若运营修改 New API 的 quota 换算参数，必须在同次部署中同步 Portal `.properties` 并重新执行价格抽样，否则不得对外宣称价格已核对。

`application.properties` 保存默认单位和 60 秒缓存时长；`application-dev.properties`、`application-test.properties`、`application-prod.properties` 显式声明各环境值，其中 test 使用确定值，prod 由部署变量提供并启动校验。前端不接触 quota 或倍率公式。

备选方案是新增对 `/api/status` 的依赖以动态读取 `quota_per_unit`，但 P1-03 已刻意禁止公开配置依赖 New API status，且额外请求会扩大上游契约和信息披露面。第一阶段采用显式部署配置与真实抽样门禁。

### 5. 使用单键 Caffeine 缓存，不提供陈旧价格降级

模型目录是全局公共快照，缓存只使用一个固定键，默认写入后 60 秒过期、最大条目数为 1。加载函数完成上游校验、映射和排序后才原子写入；异常不缓存。Caffeine 的原子加载合并并发未命中，避免公开页面突发流量放大到 New API。

缓存过期后的第一次请求同步刷新。刷新失败返回现有 `UPSTREAM_UNAVAILABLE`、`UPSTREAM_TIMEOUT` 或 `UPSTREAM_ERROR`，不继续展示过期价格；后续请求仍可重试加载。响应使用 `Cache-Control: no-store`，防止浏览器和共享代理在服务端缓存失效后继续持有旧价格。

备选的 stale-while-revalidate 可提高可用性，但需要定义最长陈旧时间、警告字段和后台刷新并发，对 MVP 增加状态复杂度；价格属于敏感事实，因此本阶段选择失败可见。

### 6. 搜索与厂商筛选在前端快照上完成

`frontend/src/api/models.ts` 使用 Zod 严格解析 Portal DTO，TanStack Query 负责请求状态。`features/catalog` 维护纯函数搜索、厂商筛选、排序和模型选择；`ModelsPage` 只组合页面结构和状态。搜索对模型 ID 与非空显示名称做 locale-independent 大小写折叠，厂商选项仅由非空 provider 去重生成。目录规模在 MVP 内直接客户端过滤，不增加分页、查询参数或服务端搜索。

模型条目主要展示模型 ID、可选厂商和明确单位的价格；`pricing=null` 使用文本状态。每个条目提供“查看调用示例”，导航到 `/docs?model=<encoded-id>`。桌面使用紧凑列表/卡片网格，移动端单列，复用现有设计变量和反馈组件，不引入新的 UI 框架。

### 7. 开发文档使用受控模板而不是运行时 Markdown

文档结构和说明作为 React/TypeScript 组件及 i18n 文案随前端版本发布；代码示例由无副作用模板函数从三个受控输入生成：public-config 的 OpenAI Base URL、目录中已验证的模型 ID、固定 Key 环境变量。Base URL 在 API schema 已验证，生成端点时只移除末尾斜杠后追加 `/chat/completions`；模型 ID 始终经过 JSON 字符串序列化或 shell 单引号安全转义，不能进入命令结构位置。

首批 SDK 选择官方 OpenAI JavaScript/TypeScript 客户端，因为当前前端团队可直接测试其代码形态；文档同时给出 cURL。两类均包含非流式与 `stream: true` 示例，SDK 流式示例使用异步迭代，cURL 使用禁用输出缓冲的参数。只展示 P1-07 已开放字段，不提供 Responses API 或在线执行按钮。

备选 Markdown/MDX 更方便长文维护，但当前只有一个短文档页，引入解析、代码组件注入和内容安全边界收益不足；未来文档规模扩大时再迁移。

### 8. 文档模型参数必须经当前目录确认

`/docs` 读取 `model` 查询参数后先做长度和控制字符检查，再与当前模型目录精确匹配。匹配成功时作为选择值；否则选择按 ID 排序后的首个 `availability=AVAILABLE` 模型。目录为空时只展示鉴权和协议说明，不生成可调用代码。

URL 中的模型参数只用于选择，不直接信任并插入示例。这样即使用户构造引号、换行或 shell 片段，也不会生成注入式命令。模型下线后刷新会自然回退，不长期宣传旧模型。

### 9. 复制交互集中封装并保留手动选择退路

新增通用代码块组件，代码文本始终可见、可选择；复制按钮优先使用 Clipboard API，成功和失败通过现有 Toast/可访问 live region 反馈。Base URL 与每个代码块分别复制，按钮在协议未启用或模型未选定时禁用并给出原因。不得把复制内容写入日志、localStorage、URL 或分析事件。

测试注入剪贴板实现，覆盖成功、权限拒绝、重复操作和键盘触发。移动端代码区独立横向滚动，避免整页溢出。

### 10. 分层验证区分契约、页面行为和真实计费

后端契约测试使用 P1-02 基座模拟 `/api/pricing`，覆盖匿名 Header 白名单、两种计费模式、厂商关联、未知字段、非法/重复条目、空目录、HTTP 200 业务失败、超时和无重试。缓存测试使用可控时钟与并发屏障证明 60 秒命中、单次加载和失败不缓存。

前端单元/组件测试覆盖 Zod 边界、搜索筛选、价格格式、空/错状态、模型参数验证、四类代码模板、运行时 Base URL、复制失败、i18n 和可访问性；Playwright 覆盖桌面与移动端的“模型广场选择→文档→复制”流程。构建守卫扫描私网地址、New API 品牌、硬编码公开域名和真实 Key 形态。

真实验收使用默认组测试账号与 P1-06 Key：从页面选择 `/api/pricing` 返回的聊天模型，复制示例经 P1-07 发起非流式与 SSE 请求，再使用 P1-09 可用的请求日志或 New API 私网管理记录核对输入/输出 Token、基础价格和实际扣费。若 P1-09 尚未交付，可由运维在私网导出脱敏证据；没有真实渠道或可核对日志时，对应任务保持未完成。

## Risks / Trade-offs

- [公共目录不是用户级可调用清单] → 页面始终写明 Key 状态、额度、用户组和模型限制以真实调用为准，不使用“保证可用”等文案。
- [Portal 的 `quota-per-usd` 与 New API 配置漂移] → prod 必须显式配置；发布清单要求同次核对并用默认组真实调用抽样，失败时停止价格发布。
- [冻结版 `/api/pricing` 可能返回未配置好价格的条目] → 严格校验计费模式和数值，无法换算时保留模型但显示“价格暂不可用”，绝不显示零价。
- [60 秒缓存导致短暂价格延迟] → 明确页面为基础价格，TTL 与上游自身分钟级缓存一致；紧急价格变更可重启应用清空缓存。
- [不提供陈旧缓存会在上游抖动时使目录短暂不可用] → 返回可重试稳定错误，避免错误价格；模型数据面不依赖 Portal 目录，已知模型调用不受影响。
- [代码示例随 SDK 版本变化] → 锁定并测试当前 package-lock 对应 SDK 语法；升级示例或依赖时作为受控变更处理。
- [查询参数进入 shell/JSON 示例存在注入风险] → 只接受当前目录精确匹配的模型 ID，并使用上下文对应的转义函数生成文本。

## Migration Plan

1. 先增加脱敏 pricing fixture、价格换算测试和模型目录契约测试，冻结 v0.13.2 的已用字段与两种计费模式。
2. 增加 catalog `.properties`、启动校验、New API pricing 适配器、单键缓存、应用服务和 `GET /portal/api/models`；确认匿名访问、错误翻译和字段裁剪。
3. 增加前端模型 API schema/query、目录组件、搜索筛选和模型到文档的安全导航，替换 `/models` 占位页。
4. 增加代码模板、代码块复制组件和开发文档章节，接入 public-config 与目录选择，替换 `/docs` 占位页。
5. 运行 Maven 正式构建、后端契约/缓存测试、Vitest、Playwright、可访问性和产物敏感信息扫描。
6. 在目标环境配置真实渠道、默认组测试账号和可用模型，核对 Portal 配置与 New API quota 换算参数，完成页面示例的非流式、SSE 和基础价格扣费抽样。

该变更没有数据库迁移。回滚时部署上一版 `lang-api` 制品即可恢复模型广场和文档占位页；P1-07 模型网关、New API 渠道、API Key 和计费数据均不变。若仅价格配置无法确认，可先不发布新制品，不应通过前端隐藏警告继续展示未经核对的价格。
