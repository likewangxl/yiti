# 资源/菜单改名同步页面显示 设计方案

- 日期：2026-07-16
- 模块：`xanzc_frontend`（前端，报表分析等全模块通用）
- 状态：已评审通过，待写实现计划

## 1. 背景与问题

用户在「系统设置 → 资源/菜单」把菜单「自由报表」改名为「数据公式」后：

- 左侧**侧边栏菜单**已变为「数据公式」（DB 驱动，正常）。
- 进入该页后，**页面左上角 H1** 仍是「自由报表」。
- 顶部**面包屑路径**仍是「/报表分析/自由报表」。

### 根因：同一"名称"有三个互相独立的来源

| 显示位置 | 数据来源 | 改资源名后 |
|---|---|---|
| 侧边栏菜单 | DB：`getMyMenus()` 返回的 `menuName`（PT_RESOURCE） | ✅ 同步 |
| 面包屑路径 | 前端**静态** `route.meta.title` + `meta.group`（`router/index.js` 写死） | ❌ 不变 |
| 页面 H1 | 各页 `.vue` 里**硬编码**的 `<h1>自由报表</h1>` | ❌ 不变 |

侧边栏是 DB 驱动会变；面包屑与 H1 写死在前端代码里，DB 改名影响不到。

相关证据：
- `src/components/AppSidebar.vue`：`getMyMenus()` → `{ resourceId, resourceUrl, menuName, children }`，`:to="m.resourceUrl"` 即路由 path，`{{ m.menuName }}` 显示 DB 名。
- `src/components/AppBreadcrumb.vue`：`items` 来自 `route.matched[...].meta.title / meta.group`。
- `src/router/index.js`：`meta: { title: '自由报表', group: '报表分析' }` 等，写死。
- `src/views/report/FreeReport.vue`：`<h1>自由报表</h1>`（约 53 个页面存在同类 `page-h > h1` 硬编码）。
- `src/stores/user.js`：仅存用户身份，无菜单缓存；菜单每次在 `AppSidebar` 本地 fetch，无共享 store。

## 2. 解决方向

不可能在"写库时"回改前端静态代码。正确做法是反向——让**面包屑与页面标题在运行时也从 DB 菜单名解析**：用 `route.path` 匹配菜单节点 `resourceUrl` 拿 `menuName`（父节点作分组），静态 `meta.title` 退化为兜底。把菜单树收进共享 Pinia store，侧边栏 / 面包屑 / 页面标题统一读取——**DB 菜单名成为唯一显示真源**。

改造范围（用户已确认）：**全局机制 + 全量应用**。面包屑集中在 1 个组件、改一次全站生效；页面 H1 分散在约 53 页，用统一组件逐页替换。纯前端改动，无需后端调整（`my-menus` 已返回 `menuName`）。

## 3. 架构与数据流

```
getMyMenus() ──> stores/menu.js（tree + byUrl 索引）
                     │  resolve(path) → { title: menuName, group: 父 menuName } | null
      ┌──────────────┼───────────────────────┐
  AppSidebar      AppBreadcrumb           <PageTitle>
  (改用 store)    (DB名 ?? meta.title)    (props.title ?? DB名 ?? meta.title)
```

## 4. 组件设计

### 4.1 `stores/menu.js`（新增，Pinia）

- 职责：应用内缓存 `getMyMenus()` 菜单树，提供按路由 path 的名称解析。
- 状态：`tree`（原始树）、`loaded`、`loading`。
- `load(force = false)`：幂等拉取；`loaded && !force` 时直接返回，避免侧边栏 / 面包屑 / PageTitle 各拉一次；`force` 用于改名后强制刷新。
- 内部索引 `byUrl`：`Map<resourceUrl, { title, group }>`，递归遍历树构建（`title = node.menuName`，`group = 父节点 menuName || null`）。树变更后重建。
- `resolve(path)`：返回 `byUrl.get(path) || null`。
- 依赖：`@/api/auth` 的 `getMyMenus`。

### 4.2 `AppBreadcrumb.vue`（改造）

- `title = menuStore.resolve(route.path)?.title ?? matched.meta?.title`。
- `group = menuStore.resolve(route.path)?.group ?? matched.meta?.group`。
- `items = !title ? [] : (group ? [group, title] : [title])`（保持现有结构）。
- 挂载时 `menuStore.load()`（幂等）。非菜单路由（详情 / 带参 / `hideInMenu`）解析为 `null` → 自动退回静态 `meta`，行为不回退。

### 4.3 `PageTitle.vue`（新增，全局注册）

- Props：`title?: string`（页面级覆盖，用于动态 / 记录级标题）。
- `display = props.title || menuStore.resolve(route.path)?.title || route.meta?.title || ''`。
- 渲染 `<h1>{{ display }}</h1>`——沿用各页 `.page-h h1` 既有样式，不改 CSS。
- 挂载时 `menuStore.load()`（幂等）。
- 在 `main.js` 全局注册（`app.component('PageTitle', PageTitle)`），各页仅换标签、无需 import。

### 4.4 约 53 个页面（全量应用）

- 静态菜单名的 H1：`<h1>自由报表</h1>` → `<PageTitle />`。
- **动态 / 记录级** H1（如 `FreeReportDetail`「报表详情」、含具体记录名的详情页）：保留原有写法，或 `<PageTitle :title="自定义名" />`，**不硬套菜单名**，避免误伤。
- 判定规则：H1 文本是"该菜单名字面量" → 换 `<PageTitle/>`；H1 是插值 / 计算值 / 与菜单无关 → 保留。

### 4.5 `AppSidebar.vue`（改造）

- 改为消费 `menuStore.tree`（`menuStore.load()` 于挂载），去掉本地重复 fetch，保证三处一致；展开态等交互逻辑不变。

### 4.6 `Resources.vue`（改造）

- 改名成功回调里 `menuStore.load(true)` 强制刷新 → 当前会话侧边栏 / 面包屑 / 当前页 H1 立即更新，无需手动刷新。其他已登录用户在下次导航 / 刷新时生效。

## 5. 边界与兜底

- 匹配：`route.path` 对 `resourceUrl` **精确匹配**（`route.path` 不含 query，天然干净）。
- 带参 / 详情页匹配不到 → 退回 `meta.title`（等于现状，不报错、不清空）。
- 菜单树按角色权限过滤；用户能停留的菜单页必在其树内，正常命中。
- store 加载完成前，`PageTitle` / 面包屑先显示 `meta.title`（与改名前一致），加载完成后仅**被改名项**切换为新名——短暂切换可接受。

## 6. 测试（沿用 vitest + happy-dom + @vue/test-utils 约定）

- `stores/menu.js`：`resolve` 命中返回 `{title, group}`；未命中返回 `null`；父分组解析正确；`load` 幂等 + `force` 重取。
- `AppBreadcrumb.vue`：store 内节点改名后面包屑显示新名；路径不在树内时退回 `meta.title`；`group` 存在 / 缺失两种结构。
- `PageTitle.vue`：显示菜单名；`title` 属性覆盖优先；无菜单命中退回 `meta.title`。

## 7. 非目标（YAGNI）

- 不改后端：`my-menus` 已返回 `menuName`，无需新增接口 / 字段。
- 不做 `document.title`（浏览器标签页标题）同步——本次仅面包屑 + 页面 H1，超出范围不处理。
- 不引入 i18n / 多语言。
- 不改 `router/index.js` 的 `meta.title`（保留为兜底真源，不删除）。

## 8. 影响文件清单

- 新增：`src/stores/menu.js`、`src/components/PageTitle.vue`
- 改造：`src/components/AppBreadcrumb.vue`、`src/components/AppSidebar.vue`、`src/views/system/Resources.vue`、`src/main.js`（全局注册）
- 全量应用：约 53 个 `src/views/**/*.vue`（静态菜单名 H1 换 `<PageTitle/>`）
- 测试：`src/stores/__tests__/menu.spec.js`、`src/components/__tests__/AppBreadcrumb.spec.js`、`src/components/__tests__/PageTitle.spec.js`
