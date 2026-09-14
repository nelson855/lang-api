# developer-documentation Specification

## Purpose

提供与当前部署配置和 P1-07 实际开放协议一致的开发文档，使用户能够安全复制 Base URL、模型 ID 与最小调用示例完成真实请求。

## Requirements

### Requirement: 文档只宣传运行时已开放协议
`/docs` SHALL 从 `/portal/api/public-config` 获取当前运行环境配置，并只为存在的 `OPENAI` 条目展示可使用的 Base URL 和调用示例。文档 MUST NOT 在构建产物中固化域名、New API 私网地址或运维端口；当 OpenAI 地址未启用时 SHALL 显示“服务尚未开放”，并禁用所有会产生不可用命令的复制入口。

#### Scenario: OpenAI Base URL 已启用
- **WHEN** public-config 返回合法的 `{protocol:"OPENAI",url:"https://api.example.com/v1"}`
- **THEN** 文档展示并使用该完整 Base URL，示例请求路径不会重复或遗漏 `/v1`

#### Scenario: 当前环境未开放模型协议
- **WHEN** public-config 的 `apiBaseUrls` 为空或没有 OPENAI
- **THEN** 文档仍可阅读鉴权和概念说明，但不展示可误复制的有效调用命令，并明确说明服务尚未开放

#### Scenario: 同一前端制品切换环境
- **WHEN** 未重新构建前端而 public-config 返回另一个合法 OpenAI Base URL
- **THEN** 页面展示、Base URL 复制和所有代码示例同步使用新地址

### Requirement: 最小鉴权与接口说明
开发文档 SHALL 说明使用 `Authorization: Bearer <API_KEY>` 鉴权、Key 的安全保管要求、首批开放的 `GET /v1/models` 与 `POST /v1/chat/completions`，并明确当前不支持的路径不得通过公共入口调用。示例 MUST 使用明显的 Key 占位符，不得读取、嵌入或持久化用户真实 Key。

#### Scenario: 阅读鉴权说明
- **WHEN** 用户打开开发文档
- **THEN** 页面说明 Bearer Header 格式、Key 仅展示一次时的保存责任及不得放入浏览器前端代码或版本库的安全边界

#### Scenario: 查看首批接口范围
- **WHEN** 用户查看接口列表
- **THEN** 页面只把 P1-07 已开放的模型列表和聊天完成接口标记为可用，不暗示 Responses、Embeddings、图片、音频、Anthropic 或 Gemini 已开放

#### Scenario: 检查示例中的凭证
- **WHEN** 页面生成或复制任一代码示例
- **THEN** 凭证位置始终为明确占位符或环境变量引用，不包含 Portal 会话、页面缓存或真实 API Key

### Requirement: 可复制的非流式与 SSE 示例
文档 SHALL 至少提供 cURL 和 OpenAI SDK 两类示例，并同时覆盖最小非流式聊天请求与 SSE 流式聊天请求。每个示例 MUST 使用运行时 Base URL、当前选定的真实模型 ID、Bearer Key 占位符和 P1-07 支持的字段；流式示例 MUST 明确展示逐块消费方式，不把流式响应当作一次性 JSON。

#### Scenario: 复制 cURL 非流式示例
- **WHEN** OpenAI 地址可用且用户复制 cURL 非流式示例
- **THEN** 剪贴板内容包含正确的 `/chat/completions` URL、Bearer 环境变量、所选模型和 `stream:false`

#### Scenario: 复制 OpenAI SDK 流式示例
- **WHEN** OpenAI 地址可用且用户复制 SDK 流式示例
- **THEN** 剪贴板内容使用运行时 Base URL、API Key 环境变量、所选模型和流式迭代逻辑

#### Scenario: 复制失败
- **WHEN** 浏览器拒绝剪贴板权限或复制 API 失败
- **THEN** 页面保留可选择的代码文本并显示失败反馈，不宣称复制成功

### Requirement: 模型选择与文档联动
模型广场 SHALL 为每个目录模型提供“查看调用示例”入口，将模型 ID 作为 URL 查询参数导航到 `/docs`。文档 SHALL 仅在该 ID 存在于当前模型目录时采用它；参数缺失、非法或模型已下线时 SHALL 选择当前目录中的确定性默认模型，若目录为空则不生成可调用示例。URL 参数 MUST 经过编码和长度校验，且不得被插入 HTML 或命令结构的非数据位置。

#### Scenario: 从模型广场进入文档
- **WHEN** 用户选择模型 `model-x` 并打开调用示例
- **THEN** 页面导航到带编码模型参数的 `/docs`，文档加载目录后在所有示例中使用精确模型 ID `model-x`

#### Scenario: 查询参数指向未知模型
- **WHEN** `/docs` 收到不在当前模型目录中的模型参数
- **THEN** 页面不使用该值生成命令，并选择按模型 ID 排序后的首个可用目录模型或显示无模型状态

#### Scenario: 模型下线后刷新文档
- **WHEN** 先前选中的模型不再出现在当前公共模型目录
- **THEN** 文档停止宣传该模型并切换到确定性默认模型或无模型状态

### Requirement: 开发文档的内容与交互质量
开发文档 SHALL 使用自有中英文文案和应用设计系统，提供清晰的章节导航、代码语言标识、复制状态和键盘可访问操作。页面 MUST 区分 Base URL、完整端点 URL 和模型 ID，不得复用 New API 默认文档页面、品牌或文案，也不得提供未实现的在线 Playground。

#### Scenario: 键盘复制代码
- **WHEN** 键盘用户聚焦代码块复制按钮并激活
- **THEN** 页面复制对应代码、提供非仅颜色的成功反馈，并将焦点保持在合理位置

#### Scenario: 移动端阅读
- **WHEN** 用户在窄屏查看长代码示例
- **THEN** 代码区域可独立横向滚动且不会导致整页布局溢出，章节和复制操作仍可访问

#### Scenario: 切换语言
- **WHEN** 用户在中文和已开放的英文之间切换
- **THEN** 标题、说明、错误和复制反馈使用当前语言，而协议字段、路径、模型 ID 与代码语法保持原文

