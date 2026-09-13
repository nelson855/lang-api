## ADDED Requirements

### Requirement: API Key 查询与 mutation 一致性
前端 SHALL 通过统一 Portal API 客户端和运行时 DTO 处理 API Key 列表、详情、创建、编辑、启停、删除和 reveal。查询键 MUST 包含当前用户作用域及规范化分页、名称和状态条件；所有 mutation MUST 禁止自动重试，成功后按资源粒度更新或失效列表/详情，`UNAUTHENTICATED` 时复用统一会话失效流程清除全部用户作用域缓存。

#### Scenario: 搜索条件变化
- **WHEN** 用户修改名称或状态条件
- **THEN** 前端将页码重置为 1、取消过期请求并只展示最新条件对应的响应

#### Scenario: 编辑成功
- **WHEN** API Key 编辑 mutation 成功
- **THEN** 前端使对应详情与受影响列表失效，不继续显示旧限制或错误状态

#### Scenario: 写操作网络失败
- **WHEN** 创建、编辑、启停或删除遇到网络失败、超时或结果未确认错误
- **THEN** 客户端不自动重放请求，页面保留安全上下文并提供刷新核对或用户主动重试

### Requirement: API Key secret 不进入查询缓存
reveal 响应 SHALL 使用独立的非缓存请求路径，完整 `secret` MUST NOT 写入服务端状态查询缓存、持久化缓存、开发工具持久化、URL、错误对象、通知文本或遥测。组件 SHALL 在复制成功、对话框关闭、路由离开、设定短时窗口结束或会话失效时清除内存中的 secret；剪贴板写入失败时不得降级为持久存储。

#### Scenario: reveal 成功
- **WHEN** 用户确认 reveal 且服务端返回完整 secret
- **THEN** secret 只进入当前打开的敏感值组件状态，不出现在任何 API Key 查询缓存快照中

#### Scenario: 复制后清除
- **WHEN** 浏览器成功写入剪贴板
- **THEN** 页面显示不含 secret 的成功提示并立即从组件状态清除完整值

#### Scenario: 页面或会话结束
- **WHEN** 用户关闭对话框、切换路由或会话失效
- **THEN** 前端清除 secret，后续返回页面必须重新确认并请求 reveal

