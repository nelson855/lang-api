---
version: alpha
name: "Lang API Deep Ink"
description: "面向模型网关与开发者控制台的低噪声深色界面。"
colors:
  background: "#0D1117"
  surface: "#131920"
  surface-raised: "#1B232D"
  text: "#EEF2F4"
  text-muted: "#AAB5C2"
  primary: "#7CF2C7"
  primary-hover: "#4FD9AC"
  border: "#2A3542"
  success: "#7BE0A8"
  warning: "#F3C969"
  danger: "#FF9D99"
typography:
  sans:
    fontFamily: "Manrope Variable, Manrope, system-ui, sans-serif"
  mono:
    fontFamily: "JetBrains Mono Variable, JetBrains Mono, ui-monospace, monospace"
rounded:
  sm: "6px"
  md: "10px"
  lg: "16px"
spacing:
  page-max: "1180px"
  page-gutter: "24px"
  page-gutter-mobile: "16px"
components:
  button: { }
  input: { }
  dialog: { }
  table: { }
  navigation: { }
---

# Lang API Design System

## Overview

### Creative North Star

像一块安静的深色工程工作台：信息有明确层级，代码与请求状态可读，只有可操作或需注意的位置使用 Signal 薄荷色。公共首页借鉴清晰、留白克制的开发者站点节奏；控制台优先保证密度与任务完成效率。

### Product context and register

- **Audience and primary job:** 开发者与运营人员查看可用模型、接入 API、管理密钥、请求与余额。
- **Locale(s) and language policy:** 主界面为中文；品牌、协议、模型和代码保留英文原文。中文使用系统字体回退并保持不小于 16px 的正文基线。
- **Register:** 混合。`/` 与公开资料页可承担品牌表达；`/console/*`、认证页以产品可用性为先。
- **Memorable signature:** 首页和关键状态使用简短“请求轨迹”式等宽文本，而不是霓虹、粒子或发光装饰。
- **Restraint:** 表格、表单、余额、密钥与错误反馈必须平实可扫描；不使用渐变主背景、玻璃拟态或大面积阴影。
- **Anti-references:** 不做赛博朋克仪表盘、不做营销模板式大插画，也不让控制台复用首页的夸张英雄区。
- **Token ownership/runtime mapping:** Model B。`frontend/src/design/tokens.css` 是运行时唯一真实来源；本文件镜像其被接受的语义值。共享组件只引用 `--color-*`、`--radius-*` 与组件令牌，令牌测试负责漂移检查。

## Colors

`background` 是 Void，`surface` 与 `surface-raised` 用于可辨的两级深灰面板。`text`/`text-muted` 承担正文层级，`border` 仅分隔边界。`primary` 只用于主要安全操作、焦点和实时请求轨迹；成功、警告、失败使用独立语义色，绝不只靠颜色传递状态。全站固定深色主题，强制高对比模式仍由浏览器接管。

## Typography

Manrope 用于界面与中文回退文本；JetBrains Mono 仅用于模型 ID、请求 ID、端点与代码。标题靠尺寸与留白建立层级，不依赖全大写或重阴影；控制台表格中的数字保持等宽并允许完整值在窄屏横向访问。

## Layout

公开页最大内容宽度为 1180px，默认 24px 横向留白，768px 以下为 16px。控制台桌面端使用固定侧栏和自然文档滚动；数据表只在自身横向滚动，不能给共享页面外壳施加视口高度或隐藏溢出。异步内容使用兼容高度的骨架或面板，避免按钮和页脚位移。

## Elevation & Depth

层次主要通过 Void、Carbon、Slate 的色调差与细边框表达。静态卡片默认无明显投影；对话框、抽屉和浮层可使用 `--shadow-md`，并保持实色背景。

## Shapes

输入和紧凑控件使用 6px，常规按钮和数据面板使用 10px，重点容器使用 16px。边框细且低对比，避免药丸化的所有元素；仅状态标签可采用更圆的形状。

## Components

### Foundational visual states

所有交互控件都需要默认、悬停、焦点、按下、禁用、忙碌和错误状态；忙碌状态保留原有几何与可访问名称。`focus-visible` 使用 Signal 轮廓，`prefers-reduced-motion` 下取消非必要过渡。

### Buttons and actions

每个决策区仅有一个实心主操作。主按钮使用 Signal 与 Void 文本；次要操作使用深色边框或幽灵样式；撤销和删除始终分离，且保留已有确认和 toast 行为。

### Navigation and data display

公开导航保持轻量；控制台侧栏以选中状态、路由名称和图标共同表达当前位置。表格保持语义 `<table>` 与原有分页、加载、空态和错误态，窄屏显示横向滚动提示而不截断列。

### Forms and overlays

复用既有 `Input`、`FormField`、`Dialog` 和反馈组件。输入字段保留可点击标签、错误关联与键盘行为；对话框、抽屉和 toast 的焦点、恢复和错误处理不因视觉改造而改变。

### Iconography

使用项目现有 lucide 线性图标，默认 18–20px。图标不替代必要文字；仅图标按钮必须具有本地化可访问名称。

### Motion

动效仅提示状态或导航变化，控制在短促淡入和边框/颜色过渡内；不使用循环装饰动画。

### Content and data visualization

文案直接、工程化，按钮使用具体动词。图表若出现，颜色顺序以 Signal、Mist、Warning、Danger 为主，并提供文本数值或表格替代信息。

## Do's and Don'ts

- **Do:** 让深色表面、细边框与明确留白承载秩序，令重点操作自然浮现。
- **Do:** 在所有页面复用同一套语义令牌、焦点和反馈组件。
- **Don't:** 用霓虹、发光、网格背景或伪造的实时数据制造科技感。
- **Don't:** 为了视觉密度牺牲表单标签、表格完整值、错误提示或键盘路径。
