## ADDED Requirements

### Requirement: 钱包与个人设置路由及导航
控制台 SHALL 增加 `/dashboard/wallet` 和 `/dashboard/settings` 两个受保护路由，并在桌面侧栏与移动抽屉提供“钱包”和“个人设置”导航。两个页面 MUST 复用现有控制台布局、会话守卫、页面标题、移动导航和用户作用域缓存清理行为；不得新增顶层 `/wallet`、`/topup`、`/settings` 或暴露 New API 原始页面。

#### Scenario: 从控制台进入钱包
- **WHEN** authenticated 用户通过桌面或移动导航选择钱包
- **THEN** 应用使用客户端导航进入 `/dashboard/wallet`，同步更新导航选中状态和页面标题

#### Scenario: 从控制台进入个人设置
- **WHEN** authenticated 用户通过桌面或移动导航选择个人设置
- **THEN** 应用使用客户端导航进入 `/dashboard/settings`，并以当前认证 profile 初始化页面

#### Scenario: 匿名直接访问
- **WHEN** anonymous 用户直接访问或刷新钱包或个人设置路由
- **THEN** 会话守卫跳转登录，并保存经过校验的完整站内 returnTo

#### Scenario: 顶层旧式路径
- **WHEN** 用户访问未声明的 `/wallet`、`/topup` 或 `/settings`
- **THEN** 应用按未知前端路由处理，不渲染钱包、资料或上游页面

### Requirement: 钱包页面局部状态与响应式行为
钱包页面 SHALL 独立查询余额、充值能力和充值记录；任一只读请求失败不得清除其他成功数据，只有 `UNAUTHENTICATED` SHALL 触发统一会话失效。充值记录分页、各区域重试和关闭说明 MUST 可通过键盘与触摸操作，并在窄屏下不产生整页横向溢出。

#### Scenario: 充值记录失败但余额成功
- **WHEN** 余额查询成功而充值记录查询失败
- **THEN** 页面保留余额和充值关闭说明，只在记录区域显示错误与重试

#### Scenario: 会话失效
- **WHEN** 任一钱包或设置接口返回 `UNAUTHENTICATED`
- **THEN** 应用清除全部用户作用域缓存并进入登录流程，不继续显示旧余额、记录或 profile

#### Scenario: 窄屏查看钱包
- **WHEN** 用户在窄屏查看余额、关闭说明和充值记录分页
- **THEN** 主要内容不产生整页横向溢出，状态、订单号与分页保持可读可操作
