## ADDED Requirements

### Requirement: 基础用量控制台路由与导航
控制台 SHALL 将 `/dashboard` 作为基础用量概览首页，并增加 `/dashboard/request-logs` 受保护路由及“请求日志”导航。两个路由 MUST 复用现有控制台布局、会话守卫、页面标题和移动抽屉行为；不得新增顶层 `/request-logs` 或重复的 `/usage` 页面。

#### Scenario: 已登录访问基础概览
- **WHEN** authenticated 用户进入 `/dashboard`
- **THEN** 页面展示真实余额与基础用量区域，不再显示 Dashboard 阶段占位内容

#### Scenario: 从控制台导航到请求日志
- **WHEN** 用户通过桌面侧栏或移动抽屉选择“请求日志”
- **THEN** 应用使用客户端导航进入 `/dashboard/request-logs`，导航选中状态和页面标题同步更新

#### Scenario: 匿名直接访问请求日志
- **WHEN** anonymous 用户直接访问或刷新 `/dashboard/request-logs`
- **THEN** 会话守卫跳转登录，并保存经过校验的完整站内 returnTo

#### Scenario: 顶层旧式路径
- **WHEN** 用户访问未声明的 `/request-logs` 或 `/usage`
- **THEN** 应用按未知前端路由处理，不渲染控制台数据

### Requirement: 控制台数据局部状态隔离
Dashboard 的余额、摘要和趋势 SHALL 使用独立加载、成功、空数据与错误区域；请求日志页面 SHALL 使用独立分页查询。任一只读接口失败不得清除其他接口已成功的数据，只有 `UNAUTHENTICATED` SHALL 触发统一会话失效流程。所有区域 MUST 使用共享设计系统并在窄屏下保持可读和可操作。

#### Scenario: 单个 Dashboard 区域失败
- **WHEN** 三个 Dashboard 请求中只有一个失败
- **THEN** 页面仅在该区域显示错误和重试，保留其他成功区域

#### Scenario: 会话统一失效
- **WHEN** 任一受保护用量接口返回 `UNAUTHENTICATED`
- **THEN** 应用清除认证范围缓存并进入登录流程，不继续显示余额或日志快照

#### Scenario: 窄屏查看请求日志
- **WHEN** 用户在窄屏访问请求日志并操作筛选与分页
- **THEN** 内容不产生整页横向溢出，主要筛选、状态和分页仍可通过键盘与触摸操作
