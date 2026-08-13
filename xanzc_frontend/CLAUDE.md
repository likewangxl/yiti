# xanzc_frontend/ CLAUDE.md

本文件为 `xanzc_frontend` 前端工程提供上下文说明。

## 工程概述

"银行营销 · 业务执行 · 绩效平台" PC 前端，Vue 3 单页应用，通过 Vite dev-server 的 `/api` 反向代理对接 `yiti` 后端（`bootstrap` 模块启动的全部业务模块 REST 接口）。承载工作台、信息聚合、绩效与考核、内部评价、报表分析、历史数据查询、系统设置、大屏设计器、红色引擎党建管理等功能域。

不是 Maven 多模块的一部分，是独立的 `npm` 工程，与仓库根 `CLAUDE.md` 描述的 Java 后端体系是"前后端分离、REST 对接"的关系，不共享任何构建产物。

**技术栈实际锁定版本**（以 `package-lock.json` 解析结果为准，非 `package.json` 的 `^` 范围声明）：`vue@3.5.34`、`vite@4.5.14`、`element-plus@2.13.7`。

## Key Files

| File | Description |
|------|------|
| `vite.config.js` | dev server 端口 + `/api` 代理目标 + `@` 别名 + `optimizeDeps` 显式预打包 + `allowedHosts`（**只读参考，不要改**——本文件在仓库基线与本机之间存在双轨，见下文「端口双轨」） |
| `src/main.js` | `createApp` 挂载 `App.vue`；`ElementPlus` **全量注册**（非按需）+ `zh-cn` locale；装 Pinia + router |
| `src/router/index.js` | 路由表（`createWebHashHistory`，hash 模式）+ 全局前置守卫（未登录先探测 `GET /api/auth/current-user`） |
| `src/stores/user.js` | Pinia `user` store，`sessionStorage` 持久化；全部已分配角色同时生效，暴露 `roles`/`roleSummary`/`hasRoleCode`/`isSystemAdmin`，不维护激活角色 |
| `src/stores/menu.js` / `src/stores/permission.js` | 严格加载当前用户菜单与资源 URL；请求带代际控制，登录、换用户、退出和 401 会废弃旧 pending，迟到响应不能覆盖新会话 |
| `src/api/http.js` | 唯一 axios 实例；响应拦截解包 yiti `ResponseWrapper`；导出 `call(method,url,config,fallback)`（见下「mock 机制」） |
| `src/mock/index.js` | 兜底数据，**全部是空数组/空对象**（占位形态，防止解构越界崩溃），不是仿真假数据 |

## 功能域：`src/views/screen/`（大屏画布设计器）

面向"数据大屏"场景的可视化拖拽设计器整套子系统（含设计态 `designer/`、画布 `designer/canvas/`、组件面板 `designer/panels/`、可拖拽组件库 `designer/widgets/`、运行时 `components/`、后台管理 `admin/`），文件数量较多，具体以目录实际内容为准。对应后端 `report-analytics-center` 大屏相关接口，前端封装见 `src/api/screen.js`。改动前先看 `designer/__tests__/`、`__tests__/` 下既有 vitest 用例了解画布状态管理约定，避免破坏拖拽/保存/渲染三态一致性；高度契约见 `.scr-block` 相关坑（大屏空白排查先量 canvas 高度，不要先怀疑数据）。

## 功能域：`src/views/redengine/`（红色引擎党建子系统）

红色引擎前端子系统，对接 `red-engine-center` 模块，路由前缀 `/redengine/**`。平台动态菜单通过
`M_RE_ENGINE`（`/redengine/dashboard`）进入；认证统一复用平台 `PT_USER` 与 Spring Session，
同时保留 `/redengine/login` 作为红色引擎专属外观的登录入口。红色引擎顶栏退出后必须整页跳转
该入口，不得跳转平台 `/login`：

- `layout/RedEngineLayout.vue`：独立红色主题布局，`re-` 类名前缀隔离平台全局样式，不复用 `DefaultLayout`。
- `login/LoginView.vue`：红色引擎专属登录页，提交仍调用平台 `/api/auth/login`，不恢复独立账号或 JWT；已授权 redirect 优先，否则拥有 `M_RE_ENGINE` 时优先进入 `/redengine/dashboard`。
- `layout/canSee.js`：纯函数模块，菜单项按 `res`（后端资源 URL）字段过滤可见性——精确匹配或按路径边界匹配 `/**`/`/*` 前缀；`resourceUrls` 未就绪（拉取中/失败）时 fail-close，只保留无需资源的工作台。红色引擎敏感子路由同时声明 `meta.requiredResource`，由全局守卫在挂载业务视图前校验。**这套前端资源过滤只用于改善体验，后端 RBAC 仍是最终安全边界**；当前 permissions DTO 只有资源 URL 集合，没有 HTTP method 维度，不能用前端判断代替后端的 method + URL 授权。主平台 `DefaultLayout`/`AppSidebar` 按后端 `getMyMenus()` 动态树渲染。
- 各业务视图（`report`/`branch-review`/`cockpit`/`warning`/`review`/`archive`/`export`/`system/OrgManageView`/`system/UserMapView`）对应 `red-engine-center` 的材料上报/两级审核/驾驶舱/预警/归档/导出/党组织管理能力，API 封装见 `src/api/redengine.js`（`docs/code-examples.md` 收录了其中 blob 导出与 multipart 上传两种写法范例）。

## mock 机制（`src/api/http.js` 的 `call()`）

- `VITE_USE_MOCK` 环境变量显式为 `true` 时，`call()` 直接返回调用方传入的 `fallback`，完全跳过网络请求（当前 `.env.development`/`.env.production` 均为 `false`，默认走真实后端）。
- 更常见的是"失败兜底"而非"主动 mock"：**GET 类查询失败**会自动打印 `[api fallback] ... 使用 mock 兜底` 并回退 `fallback` 值，用于后端不可用时也能展示骨架；**写操作（POST/PUT/DELETE/PATCH）失败永远 `throw`**，不会被静默吞掉——这是刻意设计，避免弹窗把失败误判成功。改动 `call()` 或新增 API 封装时必须保持这条语义边界，不要为了"统一处理"把写操作也接上 fallback。
- 安全关键查询 `current-user`、`my-menus`、`permissions` 是例外：真实模式直接使用 axios，401/403/网络错误必须抛出，禁止用 mock 或旧缓存掩盖授权失败。
- 登录 redirect 必须同时满足本地路径和菜单授权：允许精确菜单 URL、授权叶子菜单的详情子路径，以及持有 `M_RE_ENGINE` 后的 `/redengine/**`（敏感子路由再由 `requiredResource` 守卫校验）。UIAS 固定回 `/workspace` 时若用户没有工作台菜单，守卫改投首个授权叶子。

## Element Plus 必须全量注册（不要改成按需加载）

`main.js` 用 `app.use(ElementPlus)` 全量注册而非 `unplugin-vue-components` 的 `ElementPlusResolver` 按需解析。原因：按需加载会让 Vite 在路由导航时"懒发现"新依赖触发 full reload，表现为"点新菜单 URL 闪一下却跳回原页面，再点一次才真正进去"。`unplugin-auto-import`/`unplugin-vue-components` 已装但未启用这项能力，不要重新接上。

## Vite 锁定 4.x（不要升级到 5）

`vite@5` 依赖的 Node `crypto.getRandomValues` 特性与本工程要求的 Node 16 运行时不兼容；升级前必须先确认目标部署环境的 Node 版本已整体抬升，否则会在 Node 16 环境下直接启动失败。

## 端口双轨

`vite.config.js` 当前存在仓库基线（`server.port: 8090`、`/api` 代理目标 `http://localhost:18080`，已提交）与本机有意未提交改动（`8091`、`18081`）两轨并存，与后端 `bootstrap/application.yml` 的端口双轨一一对应（见根 `CLAUDE.md`「端口与 API 文档」节）。改代理配置前先确认当前是哪一轨在跑，**不要直接改这个文件**（任务红线）。

## 测试与示例

- 本工程有 vitest（`vitest.config.js`），但并非所有视图都有测试覆盖；新增/修改组件时，测试写法与规范实现范例统一登记在 `docs/code-examples.md`「前端（xanzc_frontend）」一节，不在本文件重复列举，避免与该索引产生第二份可能过期的清单。
- `src/views/redengine/__tests__/`、`src/views/screen/**/__tests__/`、`src/stores/__tests__/` 下的既有用例是最快的写法参考。

## Dependencies（对接的 yiti 后端模块，通过 `/api/*` REST 而非任何 Java 契约）

| 前端目录 | 对接的 yiti 模块 |
|---|---|
| `views/workspace/` | portal-content-center |
| `views/eval/`、`views/perf/` | performance-engine-center |
| `views/report/`、`views/screen/` | report-analytics-center |
| `views/guarantee/`、`views/history/` | performance-engine-center / report-analytics-center |
| `views/info/` | portal-content-center |
| `views/system/` | system-governance-center / auth-permission-center |
| `views/login/`、`stores/user.js` | auth-permission-center |
| `views/redengine/` | red-engine-center |

<!-- MANUAL: 手工补充内容写在此行以下，重新生成时会保留 -->
