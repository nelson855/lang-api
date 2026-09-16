# Lang API 交互约束

本文件记录前端交互后果；接口权限、余额、密钥和法律内容的业务真相仍以现有 API、路由与服务实现为准。

## 路由与导航

- 公开页使用 `PublicLayout`，认证页使用 `AuthLayout`，已登录控制台使用 `ConsoleLayout`。
- 侧栏与移动端抽屉指向同一组路由；当前路由必须有可见选中状态。
- 404 保留可返回公开首页和文档的路径，不能将用户困在空白页。

## 共享能力归属

| Capability | Canonical owner | Source of truth | Allowed variants | Verification |
| --- | --- | --- | --- | --- |
| Form | `FormField`、`Input` 与页面现有 mutation | 本合同与页面 schema | 登录 / 注册 / 编辑 | 组件测试 + 页面测试 |
| Select/Listbox | 原生 `<select>` | 本合同与 `DESIGN.md` | native | 键盘 + 浏览器外观 |
| Date | 原生 `datetime-local` Input | 本合同 | native | 值传递 + 平台弹层 |
| Scrollbar | `src/design/global.css` | `DESIGN.md` | 表格稳定 gutter | 样式守卫 + 浏览器外观 |
| Toast | Toast/Feedback | 本合同 | success / info / warning / error | live-region 测试 |
| CRUD | `ApiKeysPage` 既有动作 | API 契约与本合同 | 创建 / 编辑 / 启停 / 删除 | 页面测试 + E2E |
| Dialog | `Dialog` | 本合同 | 标准 / 移动抽屉 | 焦点 + Escape 测试 |
| Data Table | `DataTable`、`Pagination` | 本合同 | 普通表格 / 局部横向滚动 | 组件测试 + 页面测试 |

## 异步与状态

- 现有查询的 loading、error、empty、success 状态必须都有稳定高度的呈现。
- mutation 期间保持按钮尺寸和标签区域，阻止重复提交；失败后保留可恢复的页面/对话框上下文。
- 搜索和过滤沿用当前页面的请求与状态管理，不引入会覆盖较新结果的本地竞态。

## 可访问性

- 交互使用原生按钮/链接或等价语义，所有图标按钮有中文可访问名称。
- 可见标签与输入关联；错误通过原有组件关联字段，焦点可见。
- 窄屏时表格允许局部横向滚动，主要操作保持可达；减少动效偏好必须被尊重。
