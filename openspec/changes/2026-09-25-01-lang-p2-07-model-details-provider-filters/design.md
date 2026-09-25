## Context

见 `proposal.md` 的动机和 `specs/model-catalog/spec.md` 的行为契约。

现有 `CatalogService` 以 Caffeine 缓存单个 `CatalogData`，由 `CatalogPriceMapper` 将匿名 `/api/pricing` 的模型与厂商数组一次性映射为基础目录。列表响应已经隐藏上游 vendor ID、倍率和渠道信息，并以 `pricingVersion` 标识快照；但缓存值只有列表 DTO，没有详情索引或供应商统计。当前前端从列表中的非空 provider 临时生成筛选项，后端也没有安全表达包含 `/`、`%` 或 Unicode 的模型 ID 路径参数的规则。

`p2-2026-09-22-a` 证明了模型名、vendor 关联、基础价格输入和定价版本，但没有证明上下文窗口、最大输出、模态、工具/推理/结构化输出/附件能力、发布日期、描述、标签、排序或增强价格。`supported_endpoint_types=["openai"]` 只能说明上游端点类型，不能证明模型模态或能力。设计必须在保留现有列表 JSON 的同时为后续 P2-08 提供稳定接口。

## Goals / Non-Goals

**Goals:**

- 用一个不可变目录快照同时服务列表、详情和供应商选项，保证同版本内字段与计数一致。
- 为任意合法模型 ID 定义不会与路由分隔、路径遍历或多次 URL 解码混淆的路径引用。
- 固定详情 DTO 的空值语义和映射白名单，使未来增加可信来源时可以按字段扩展而不改变未知语义。
- 保持匿名只读、失败不缓存、低敏感观测和现有模型列表兼容。

**Non-Goals:**

- 不建立数据库、后台编辑器或完整自有模型主数据，不维护按模型名猜测的静态能力表。
- 不把 `supported_endpoint_types` 转换成模态、能力或 P2-09 的协议说明。
- 不改变基础价格公式、catalog TTL/profile 配置、现有列表字段或第一阶段页面。
- 不在缺少真实证据时启用增强价格，也不在本设计中完成 P2-08 页面。

## Decisions

### 1. 缓存内部 `CatalogSnapshot`，三个公开视图从一次映射原子生成

将缓存值从直接对外的 `CatalogData` 调整为内部不可变 `CatalogSnapshot`，其中包含：

- 保持原 JSON 结构的 `CatalogData`；
- 按精确模型 ID 建立的详情索引；
- 由最终 `CatalogModel.provider` 派生的供应商选项；
- 同一 `pricingVersion`。

快照加载先完成全部条目验证、重复 ID 检查、详情构造和供应商聚合，最后才作为单值写入现有最大容量为 1 的缓存。`CatalogService` 分别暴露列表、详情和 providers 读取方法，但都通过同一个 `cache.get("catalog", loader)` 入口。有效引用的不存在模型在成功快照内得到 404；非法引用在加载快照前直接 400。

**替代方案：** 为详情和供应商建立独立缓存。它会在 TTL 边界产生价格、模型存在性和计数漂移，也会放大 `/api/pricing` 访问，因此不采用。

### 2. `{modelId}` 使用无填充 URL-safe Base64，而不是原始路径文本

公开编码规则为：

```text
modelRef = base64url_without_padding(UTF-8(catalogModel.id))
```

客户端从已有列表 `id` 确定性生成引用，服务端仅接受 `[A-Za-z0-9_-]+`，拒绝 `=` 和额外路径段，使用严格 UTF-8 解码，再要求重新编码结果与输入完全相同，最后复用目录 ID 长度与控制字符校验。解码后的字符串不做 trim、大小写折叠或 Unicode 归一化；它必须与快照键完全一致。

该选择不需要向现有 `/models` item 添加 `detailId`，因而不会破坏当前前端严格 JSON schema；P2-08 使用 `TextEncoder` 和 Base64url 规则即可建立详情路由。服务端错误只记录 requestId 与有限原因枚举，不记录传入引用或解码原文。

**替代方案：** 对原始 ID 使用百分号编码。Servlet 容器对 `%2F`、反斜杠和多次解码的行为依赖部署配置，既可能在进入 Controller 前拒绝，也可能把一个 ID拆成多个路由段；允许 catch-all 又会增加路径规范化攻击面，因此不采用。

### 3. 详情 DTO 固定结构，未知使用 null 而不是缺省值

详情复用列表中的五个基础字段，并增加：

- 限制：`contextWindowTokens`、`maxOutputTokens`；
- 模态：`inputModalities`、`outputModalities`；
- 能力对象：四个独立可空 Boolean；
- 元数据：`releaseDate`、`description`、`tags`、`sortOrder`；
- 增强价格数组：`type/currency/unit/price`。

除 `capabilities` 外新增字段均可空；能力对象始终存在，其内部字段可空。`null` 表示来源缺失或证据不足，空数组只表示可信来源明确给出空集合，`false` 只表示明确不支持。当前映射器只复制 `CatalogModel` 的基础字段，其余字段统一为 null。未来字段启用必须先补基线证据、白名单适配与 delta spec，不能仅因 Jackson 看到了同名上游字段就自动透传。

**替代方案：** 当前完全省略未知字段。虽然响应更短，但会让 P2-08 无法区分旧服务端与字段未知，也会导致每次增加证据都改变 JSON 形状，故选择固定可空结构。

### 4. 供应商筛选以公开名称为值，从最终模型数组派生

供应商选项不再次遍历原始 vendors，也不把内部整数 ID 派生为公开 key。它只读取最终模型数组中的非空 provider，以 trim 后的完整名称作为 `value` 和 `label`，大小写敏感去重、累计 `modelCount`，再按 Java 自然字符串顺序稳定排序。

这样 provider item 与列表已有字符串可以直接精确匹配，同名的多个内部 vendor ID 自然合并，未知 provider 不会变成猜测的“其他”。响应携带快照 `pricingVersion`；P2-08 若发现列表与 providers 版本不同，应成对重取，而不是把两个版本拼接使用。

**替代方案：** 暴露上游 vendor ID 或基于名称生成 slug/hash。前者泄露内部标识并把 Portal 绑定到上游，后者要求列表新增 provider key 才能可靠关联；当前都没有必要。

### 5. 基础价格复用既有对象，增强价格使用独立白名单列表

详情直接引用快照内 `CatalogModel.pricing`，避免第二次计算导致舍入或 null 判定不一致。增强价格不混入 `CatalogPricing`，而是独立项目数组；允许的 type 和单位由 Portal 枚举限定，同一 type/currency/unit 必须唯一，任何负数、冲突、单位缺失或部分映射都使详情快照加载失败。

当前 `/api/pricing` 的 ratio 与固定价只能支撑既有基础价格，不能证明 cache/image/audio/video/search 价格，因此 `enhancedPricing=null`。真实调用核对属于证据闭环：基础价按 Token 或请求数核对，用户组及特殊计费维度单列，不能为了通过核对把差额塞入基础价。

**替代方案：** 返回空增强价格数组或值为 0 的项目。两者都会被下游解释为已确认不收费，与“尚无证据”不同，因此不采用。

### 6. 新路由沿用匿名目录安全边界和失败链

安全配置只放行两个只读 GET 路由；查询参数白名单为空，错误方法仍由统一异常处理返回 JSON 405。Controller 只负责引用解析、服务调用、requestId 和 `Cache-Control: no-store`，不接触 New API DTO。响应白名单测试覆盖 vendor ID/icon、倍率、分组、渠道、owner、endpoint 和原始包装。

缓存 TTL、最大容量、上游超时和错误映射继续使用现有 `.properties`；dev/test/prod 不新增配置。成功快照可复用，同键并发加载由现有缓存合并；异常不写缓存，到期刷新失败不退回无期限旧快照。观测只增加固定操作和结果标签，不使用模型 ID、模型引用、供应商名或 pricingVersion 作为指标标签。

**替代方案：** 为匿名详情设置浏览器缓存。它会让浏览器中的详情跨越后端 pricingVersion，且需要额外 ETag/失效规则；本阶段继续 `no-store`，由服务端短缓存吸收上游压力。

## Risks / Trade-offs

- **[Base64url 链接不如原始模型名可读]** → P2-08 在标题和页面正文始终展示真实模型 ID，URL token 只承担无歧义路由职责，并集中提供单一编解码工具。
- **[列表与后续请求可能跨越 TTL 边界]** → 三个响应都返回 `pricingVersion`；同一快照内部严格一致，客户端检测版本差异后成对重取，不承诺跨 HTTP 请求事务。
- **[当前详情新增字段大多为 null]** → 页面以“暂无可靠数据”表达，不用空集合或 false 美化；新证据必须通过新 baseline 和显式适配逐项开放。
- **[供应商名称变化会改变筛选 value]** → 当前唯一可信公开标识就是名称；通过 pricingVersion 检测快照变化，不暴露不稳定的上游 vendor ID。
- **[现有基础 USD 换算仍缺 P2 独立 quotaPerUsd 证据]** → 本变更不扩大其语义，只保证详情与列表一致；真实扣费任务未完成时阶段保持受阻。
- **[单快照增加索引和详情对象的内存]** → 当前目录有界且缓存容量仍为 1；索引引用不可变 DTO，禁止缓存原始上游响应与重复敏感结构。

## Migration Plan

1. 先增加模型引用编解码器、详情/供应商 DTO 与纯映射测试，冻结 JSON、空值和字段白名单。
2. 将现有缓存内部值迁移为不可变 `CatalogSnapshot`，保持 `GET /portal/api/models` 输出逐字兼容，再增加详情索引和供应商派生。
3. 增加两个匿名 GET 路由、安全规则、统一错误与纵向 MockWebServer 测试，验证三类请求共享一次加载。
4. 补充接口文档和阶段总纲，保存能取得的真实扣费脱敏证据；证据不足的能力与阶段状态继续标记为受阻。
5. 部署 Portal API 后由 P2-08 前端接入。回滚到上一版本只会移除两个新 GET 路由，不涉及数据库、配置或上游数据恢复。
