# 实施顺序与 Portal API 蓝图

> 第一阶段的具体任务边界、依赖关系和逐项验收标准见
> [第一阶段 MVP 开发与子需求拆分](./06_第一阶段MVP开发与子需求拆分.md)。
>
> 第二阶段的具体任务边界、依赖关系和逐项验收标准见
> [第二阶段聚合能力开发与子需求拆分](./11_第二阶段聚合能力开发与子需求拆分.md)。

## 1. 总体实施顺序

开发顺序是本项目的硬性约束：

1. **第一阶段：New API 已经完全或基本具备的能力。** 即使字段和 QinghuaAPI 不同，也优先采用 New API 当前数据完成可用版本。
2. **第二阶段：New API 有底层数据，但需要组合、统计或转换的能力。**
3. **第三阶段：New API 缺失、必须新增数据模型或业务流程的能力。**

不得因为第三阶段的理想设计而阻塞第一阶段上线。

## 2. 实施前置工作

### 2.1 固定 New API 基线

- 使用明确版本或镜像 digest，不使用不可追溯的 `latest`；
- 记录版本、数据库迁移和环境变量；
- 在隔离环境完成初始化；
- 配置独立数据库、Redis 和高强度 `SESSION_SECRET`；
- 关闭不使用的注册方式、支付方式和公开模块；
- 确认商用许可、品牌和源码义务。

### 2.2 建立网络边界

- 公开 Web 入口只能访问前端和 Portal API；
- 公开模型 API 只能访问 Relay 白名单；
- New API 默认前端和管理接口只在私网可达；
- 数据库、Redis 和云元数据地址不可由公网请求链访问；
- New API 出站访问按实际供应商域名和端口限制。

## 3. 第一阶段：直接复用能力

### 3.1 目标

尽快形成具备独立品牌和完整基础闭环的可用平台。第一阶段允许页面指标少于 QinghuaAPI，也允许字段直接反映 New API 的现有能力。

### 3.2 推荐交付批次

#### 批次 1：平台骨架和认证

- 自研前端框架、主题、响应式布局和中英文基础；
- 首页、登录、注册、404 和通用错误页；
- Portal API 网关、统一响应、追踪 ID 和接口白名单；
- New API 登录、注册、刷新、退出、当前用户适配；
- 认证 Cookie 的 Domain、Path、Secure 和 SameSite 验证。

#### 批次 2：API Key 与公开模型调用

- API Key 列表、搜索、分页、创建、编辑、启停、删除和复制；
- 展示独立 OpenAI、Anthropic、Gemini Base URL；
- 公开模型 API 域名及 Relay 路由白名单；
- SSE 流式传输、客户端取消、超时和大请求体验证；
- 开发文档的鉴权和最小调用示例。

#### 批次 3：模型、日志和钱包

- 基于 `/api/pricing` 的基础模型广场；
- 基于 `/api/log/self` 的请求明细；
- 基于 `/api/user/self` 的余额显示；
- 充值配置、已启用支付入口和充值记录；
- 个人设置中的用户名、邮箱、手机号和密码能力，以 New API 实际支持字段为准。

#### 批次 4：公开内容和上线闭环

- 开发文档、用户协议、隐私政策和支持地区占位内容；
- 服务条款和隐私政策通过 Portal API 读取 New API 配置；
- 安全响应头、限流、日志脱敏和监控告警；
- 备份与恢复验证；
- 上游升级契约测试。

### 3.3 第一阶段验收

- 浏览器网络请求中不出现 New API 私网域名；
- 公网不能访问 New API 默认管理页面和任意 `/api/*` 接口；
- 页面、错误信息、响应头和文档中不出现 New API 默认品牌；
- 注册、登录、刷新、退出全流程可用；
- API Key 创建后可以通过公开模型 API 完成一次真实流式调用；
- Key 启停、到期、额度和模型限制至少各验证一次；
- 请求日志和余额在调用后正确变化；
- 支付回调具备验签和幂等能力，若未接支付则明确关闭充值入口；
- New API 管理后台只能通过私网运维入口访问。

## 4. 第二阶段：基于底层数据的聚合能力

### 4.1 Dashboard 聚合

新增稳定的概览接口，提供：

- 请求总数；
- Token 用量；
- 消费总额；
- 活跃 Key；
- 成功率；
- 平均延迟；
- 请求趋势；
- 消费趋势；
- 最近请求。

需要先定义统计口径：

- 成功请求如何识别；
- 取消、超时和上游失败是否计入请求总数；
- 延迟使用总耗时还是首 Token 延迟；
- 费用以扣减额度还是最终结算值为准；
- 时间边界使用用户时区还是统一 UTC。

### 4.2 钱包统一流水

将充值、消费和退款等来源转换为统一只读模型：

```text
transaction_id
occurred_at
type
amount
currency
status
remark
reference_id
```

这只是展示视图，第二阶段仍不改变 New API 作为额度权威来源的地位。

### 4.3 模型目录增强

根据实际产品需要逐步增加：

- 上下文窗口；
- 最大输出；
- 输入和输出模态；
- 工具调用、推理、结构化输出、附件等能力；
- 发布日期、描述、标签和排序；
- 缓存、图片、音频、视频和搜索计费展示。

### 4.4 第二阶段验收

- Dashboard 各指标与抽样日志计算结果一致；
- 30 天查询不会拉取全部明细到浏览器；
- 统计接口具备明确的最大时间范围和粒度；
- 统一交易流水与充值、消费、退款来源可追溯；
- 模型价格显示与 New API 实际扣费口径抽样一致。

## 5. 第三阶段：自有能力

只有当实际运营需求出现时，才按优先级建设：

1. 法律文档版本和用户同意审计；
2. 自有模型展示元数据管理；
3. 更完整的通知、安全和登录设备管理；
4. 独立账务总账、授信或发票能力；
5. 独立管理员后台。

如果 New API 后续已经提供满足要求的能力，应重新评估是否仍需自研。

## 6. Portal API 约定

### 6.1 通用响应

成功响应：

```json
{
  "requestId": "req_xxx",
  "data": {}
}
```

失败响应：

```json
{
  "requestId": "req_xxx",
  "error": {
    "code": "INVALID_ARGUMENT",
    "message": "请求参数不正确"
  }
}
```

禁止返回 New API 内部堆栈、私网地址、渠道 ID、节点名或原始数据库错误。

### 6.2 分页

```json
{
  "requestId": "req_xxx",
  "data": {
    "items": [],
    "page": 1,
    "pageSize": 20,
    "total": 0
  }
}
```

前端不得依赖 New API 原始的 `success/message/data` 包装结构。

### 6.3 第一阶段接口清单

| 方法 | Portal API | New API 来源 | 阶段说明 |
|---|---|---|---|
| POST | `/portal/api/auth/login` | `/api/user/login` 及登录前置流程 | 直接适配 |
| POST | `/portal/api/auth/register` | `/api/user/register` | 直接适配 |
| POST | `/portal/api/auth/refresh` | `/api/user/auth/refresh` | 直接适配 |
| POST | `/portal/api/auth/logout` | `/api/user/auth/logout` | 直接适配 |
| GET | `/portal/api/profile` | `/api/user/self` | 直接适配 |
| PUT | `/portal/api/profile` | `/api/user/self` | 直接适配 |
| GET | `/portal/api/api-keys` | `/api/token/` | 直接适配 |
| POST | `/portal/api/api-keys` | `/api/token/` | 直接适配 |
| GET | `/portal/api/api-keys/{id}` | `/api/token/{id}` | 直接适配 |
| PUT | `/portal/api/api-keys/{id}` | `/api/token/` | 直接适配 |
| PATCH | `/portal/api/api-keys/{id}/status` | `/api/token/?status_only=true` | 语义化包装 |
| DELETE | `/portal/api/api-keys/{id}` | `/api/token/{id}` | 直接适配 |
| POST | `/portal/api/api-keys/{id}/reveal` | `/api/token/{id}/key` | 敏感操作，禁止缓存和日志记录完整 Key |
| GET | `/portal/api/request-logs` | `/api/log/self` | 字段与分页转换 |
| GET | `/portal/api/usage/summary` | `/api/log/self/stat` | 第一阶段基础统计 |
| GET | `/portal/api/usage/timeseries` | `/api/data/self` | 第一阶段使用原始可用粒度 |
| GET | `/portal/api/account/balance` | `/api/user/self` | quota 转金额 |
| GET | `/portal/api/account/topups` | `/api/user/topup/self` | 充值记录 |
| GET | `/portal/api/account/topup-options` | `/api/user/topup/info` | 支付配置 |
| POST | `/portal/api/account/topup-orders` | New API 已启用的支付接口 | 创建充值订单 |
| GET | `/portal/api/models` | `/api/pricing` | 基础模型广场 |
| GET | `/portal/api/legal/terms` | `/api/user-agreement` | 内容代理 |
| GET | `/portal/api/legal/privacy` | `/api/privacy-policy` | 内容代理 |

创建支付订单涉及外部资金。实现时必须针对具体支付渠道单独设计回调、验签、幂等和订单状态映射，不能只做字段转发。

### 6.4 第二阶段接口清单

| 方法 | Portal API | 用途 |
|---|---|---|
| GET | `/portal/api/dashboard/stats` | 六项指标、趋势与最近请求 |
| GET | `/portal/api/account/transactions` | 充值、消费、退款统一流水 |
| GET | `/portal/api/account/consumption-summary` | 时间范围内消费汇总 |
| GET | `/portal/api/models/{modelId}` | 增强模型详情 |
| GET | `/portal/api/model-providers` | 稳定的厂商筛选项 |

Dashboard 推荐参数：

```text
startTime
endTime
granularity
timezone
```

不建议把 `1H/24H/今天/昨天/7D/30D` 作为后端枚举。前端将快捷范围转换为明确时间边界，后端只验证跨度并执行查询。

### 6.5 第三阶段法律接口

```text
GET  /portal/api/legal/documents/{type}?locale=zh-CN
POST /portal/api/legal/consents
GET  /portal/api/legal/consents/status
```

法律文档至少包含：

```text
type
locale
version
effectiveAt
title
content
contentHash
requiresReacceptance
```

同意记录至少包含：

```text
userId
documentType
documentVersion
contentHash
acceptedAt
ip
userAgent
```

## 7. 上游适配约束

- 所有 New API 调用集中在适配模块，业务页面不得拼接上游路径；
- 为上游请求和响应建立契约测试；
- New API 升级时先在测试环境运行认证、Key、日志、余额、模型和支付测试；
- 未通过契约测试不得直接升级生产；
- 不允许前端读取 New API 数据库；
- 第一阶段不允许 Portal API 和 New API 双写余额、Token 或用户记录。

## 8. 暂缓决策

以下问题不阻塞第一阶段，实际开发到相应模块时再确定：

- 具体前端框架和 UI 组件库；
- 是否支持 OAuth、MFA 和 Passkey；
- 第一批支付渠道；
- 模型展示元数据采用配置文件还是独立表；
- 法律条款是否在首次上线即建设版本化同意记录；
- 是否需要独立的运营内容管理后台。

这些决策不得改变“先复用、再聚合、最后自研”的实施顺序。
