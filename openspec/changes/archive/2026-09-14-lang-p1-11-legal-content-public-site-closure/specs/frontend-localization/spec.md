# frontend-localization Specification Delta

## ADDED Requirements

### Requirement: 运行时启用语言约束
前端 SHALL 继续维护键集合一致的 `zh-CN` 与 `en-US` 基础文案，但语言切换器 MUST 只显示 `/portal/api/public-config` 的 `enabledLocales`。初始语言解析和已保存选择只能在该集合内生效；不再启用的已保存值 MUST 回退到启用列表第一项。在当前单一法律正文来源下，`PUBLIC` 模式的启用列表 MUST 只有 `legal.source-locale`。

#### Scenario: 公开模式只有中文法律正文
- **WHEN** public-config 返回 `enabledLocales=["zh-CN"]`
- **THEN** 页面使用中文且不提供英文切换入口，即使浏览器或本地保存值偏好英文

#### Scenario: 预览环境切换中英文
- **WHEN** `PREVIEW` 配置同时启用 `zh-CN` 与 `en-US`
- **THEN** 基础页面可以切换两种语言，但法律正文未就绪状态不会伪装成对应语言的正式正文

#### Scenario: 运行时移除已保存语言
- **WHEN** 浏览器保存 `en-US` 而当前部署只启用 `zh-CN`
- **THEN** 应用回退到 `zh-CN`，同步更新根元素、标题和持久化选择

### Requirement: P1-11 公开页面文案完整
首页、法律页、服务地区、公共导航、页脚、注册门禁、支持入口和 404 的所有基础界面文案 SHALL 同时具有 `zh-CN` 与 `en-US` 资源，键集合与插值参数保持一致。法律正文自身 MUST 按服务端返回的 `locale` 标记，不得由前端机器翻译或复用另一语言正文冒充译文。

#### Scenario: 检查公开页面资源
- **WHEN** 自动化测试比较 P1-11 新增或修改的中英文资源
- **THEN** 两种资源的键集合相同，组件没有新增用户可见硬编码文案

#### Scenario: 正文语言与页面语言不一致
- **WHEN** 法律接口返回的 `locale` 不在当前部署启用语言内
- **THEN** 页面拒绝将正文标为当前语言并展示安全的内容不可用状态
