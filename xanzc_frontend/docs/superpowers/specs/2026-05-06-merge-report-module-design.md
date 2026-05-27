# 设计：将 xanzc_pc 报表分析模块合并到 xanzc_frontend

- **日期**：2026-05-06
- **目标项目**：`/home/djdev/lf/xanzc_frontend`
- **来源项目**：`/home/djdev/lf/xanzc_pc`
- **范围**：报表分析（`报表分析` / `views/report`）模块及其全部直接依赖
- **不在范围**：auth / workspace / perf / system / 其它模块的差异（`http.js` 的演进版差异保留 xanzc_frontend 现状）

## 1. 背景

两个项目共享同一目录骨架（`api/`、`mock/`、`router/`、`views/`、`components/`、`layouts/`、`stores/`），都基于 Vue 3 + Element Plus + Pinia + ECharts，`package.json` 完全一致。`xanzc_pc/src/views/report/` 的报表分析模块是 `xanzc_frontend/src/views/report/` 的功能超集：

- 多 1 个页面（`Sql.vue` —— SQL 探查）
- 多 3 个子组件（`MetricPicker / SchemeListDialog / SchemeSaveDialog`）
- 共享的 3 个页面（Dashboard / Dynamic / Presets）xanzc_pc 版本是 2 ~ 4 倍体量，绑定了更完整的 API 与 mock 数据形态
- API 依赖：xanzc_pc 的报表视图除了 `api/report.js` 外还使用 `api/metrics.js`、`api/orgs.js`、`api/employees.js`，均为 xanzc_frontend 当前不存在的文件

合并目标：让 xanzc_frontend 拥有与 xanzc_pc 完全一致的报表分析能力，同时不影响其它模块。

## 2. 核心约束 / 决策

| 决策 | 取舍 |
|---|---|
| 视图采用「整体覆盖」而非「字段合并」 | 新视图与 mock 字段强绑定，逐字段对齐易出错；整体覆盖更稳定 |
| `api/http.js` 不动 | xanzc_frontend 的 `http.js` 是更新的演进版（含 `unwrapPage` / 401 跳登录），与新 `report.js` 调用 `call(method,path,config,fallback)` 的签名兼容 |
| 不引入其它模块差异 | auth / workspace / system / perf 在 xanzc_frontend 的版本与新报表模块无依赖关系 |
| `kpi.js` 一并带过来 | xanzc_pc `api/index.js` 引用了它；带过来可让 `api/index.js` 与 xanzc_pc 保持一致，避免维持差异化的导出表 |
| `AppSidebar.vue` 加 1 行 `meta.title` 守卫 | 新增 `report` redirect 路由没有 `meta.title`，xanzc_frontend 现有 sidebar 实现会推出空菜单项；这是合并必要副作用 |
| 冲突的 3 个 mock 导出（reportDynamic / reportDashboard / reportPresets）整体替换 | 新视图依赖新形态；不做字段级合并 |

## 3. 文件改动清单

### 3.1 视图层（`src/views/report/`）

| 文件 | 操作 | 来源 |
|---|---|---|
| `Dashboard.vue` | **覆盖** | `xanzc_pc/src/views/report/Dashboard.vue`（203 行） |
| `Dynamic.vue` | **覆盖** | `xanzc_pc/src/views/report/Dynamic.vue`（408 行） |
| `Presets.vue` | **覆盖** | `xanzc_pc/src/views/report/Presets.vue`（82 行） |
| `Sql.vue` | **新增** | `xanzc_pc/src/views/report/Sql.vue`（282 行） |
| `components/MetricPicker.vue` | **新增** | `xanzc_pc/src/views/report/components/MetricPicker.vue`（161 行） |
| `components/SchemeListDialog.vue` | **新增** | `xanzc_pc/src/views/report/components/SchemeListDialog.vue`（99 行） |
| `components/SchemeSaveDialog.vue` | **新增** | `xanzc_pc/src/views/report/components/SchemeSaveDialog.vue`（84 行） |

### 3.2 API 层（`src/api/`）

下表所有「新增 / 覆盖」均**按 `xanzc_pc/src/api/<同名文件>` 原文复制**，不做改写。

| 文件 | 操作 | 备注 |
|---|---|---|
| `report.js` | **覆盖** | 12 → 93 行；新增 SQL 探查、Dashboard、SavedQuery、Export 等接口 |
| `metrics.js` | **新增**（116 行） | `MetricPicker.vue`、`Dynamic.vue` 依赖 |
| `orgs.js` | **新增** | `Dynamic.vue` 的 `getOrgTree` 依赖 |
| `employees.js` | **新增** | `Dynamic.vue` 的 `listEmployees` 依赖 |
| `kpi.js` | **新增**（11 行） | 报表视图不直接依赖；为保持 `api/index.js` 与 xanzc_pc 一致而带入 |
| `index.js` | **修改** | 在已有 `workspace / perf / report / system / auth` 后追加 `metrics / kpi / employees / orgs` 的 `import * as` 与 `export` |
| `http.js` | **不动** | 保留 xanzc_frontend 的演进版 |

### 3.3 Mock 层（`src/mock/index.js`）

**追加**（xanzc_frontend 当前缺失，按 xanzc_pc 原文复制）：

- `reportSchemes`
- `reportDimensions`
- `reportSqlWhitelist`
- `reportSqlHistory`
- `reportSqlProbeResult`
- `metricsFlat`
- `employeesList`
- `customersList`
- `orgsTree`

**整体替换**（同名但内容/形态不同）：

- `reportDynamic`
- `reportDashboard`
- `reportPresets`

**保留不动**（xanzc_frontend 已有，与报表无关或与报表无冲突）：

- `workspace / perf* / sys*`

### 3.4 路由层（`src/router/index.js`）

在 `/` 布局路由的 children 内、报表分组的位置：

- **新增** `{ path: 'report', redirect: '/report/dynamic' }`（注意：无 `meta`）
- **新增** `{ path: 'report/sql', name: 'ReportSql', component: () => import('@/views/report/Sql.vue'), meta: { title: 'SQL 探查', group: '报表分析' } }`
- 已有的 `report/dynamic / report/dashboard / report/presets` 三条 `meta.title / group` 与 xanzc_pc 对齐（差异极小，主要是空格规整）

### 3.5 侧边栏（`src/components/AppSidebar.vue`）

在 `for (const r of all)` 循环开头加 1 行：

```js
if (!r.meta?.title) continue; // 跳过 redirect / 无 title 的占位路由
```

`item.title / item.icon` 的可选链可一并去掉（既然现在保证 `r.meta.title` 存在），但**保留可选链亦可，不强求**。

## 4. 数据流（合并完成后）

```
view (Dynamic.vue / Dashboard.vue / Sql.vue / ...)
  → @/api/report.js   (queryDynamic / saveQuery / executeSqlProbe ...)
  → @/api/metrics.js  (listMetrics / getMetricsTree)
  → @/api/orgs.js     (getOrgTree)
  → @/api/employees.js(listEmployees)
        ↓
  @/api/http.js → call(method, path, config, fallback)
        ↓ 成功                                       ↓ 后端不可用
   axios → /api/reports/...                    fallback 数据 (mock)
                                                     ↓
                                          @/mock/index.js (reportXxx / orgsTree / ...)
```

## 5. 风险与对策

| 风险 | 对策 |
|---|---|
| `api/index.js` 新增导出可能与现有代码冲突 | 这 4 个文件原本就不存在于 xanzc_frontend，不可能有 `import { kpi } from '@/api'` 之类的引用 |
| Mock 字段不对齐导致页面空白 | 对 3 个冲突 mock 整体替换、不做字段合并；新视图与新 mock 同源 |
| `report` redirect 路由污染 sidebar 菜单 | 加 `meta.title` 守卫 |
| `http.js` 行为差异 | xanzc_frontend 是更完整版，向下兼容 `call(method, path, config, fallback)` 调用形式；不替换 |
| 其它模块（auth / workspace / sys）功能因依赖差异崩溃 | 本次完全不动这些模块的代码、API、mock；只在 `api/index.js` 末尾追加导出 |

## 6. 验证策略

合并后必须按顺序通过以下检查：

1. **静态检查**：`cd xanzc_frontend && npm run build` —— 通过，无未解析 import / 无重复导出
2. **运行检查**：`npm run dev` 启动，浏览器中：
   - `/report` → 自动跳转到 `/report/dynamic`
   - `/report/dynamic` 动态指标查询页能正常渲染（含 MetricPicker、SchemeSaveDialog、SchemeListDialog 弹窗）
   - `/report/dashboard` 行长仪表盘正常渲染
   - `/report/presets` 预置报表卡片列表正常
   - `/report/sql` SQL 探查页正常（白名单、历史记录、试运行）
3. **侧边栏检查**：「报表分析」分组下显示 `动态指标查询 / 行长仪表盘 / 预置报表 / SQL 探查` 4 项，无空白菜单
4. **回归检查**：原有 `/workspace`、`/perf/*`、`/sys/*`、`/login` 等页面行为与合并前一致

## 7. 不在本次范围

- xanzc_frontend 与 xanzc_pc 在 `auth.js / system.js / workspace.js / mock 中其它模块` 的差异 —— 不评估、不合并
- `stores/user.js`（仅 xanzc_frontend 有）—— 保留
- 其它视图（`workspace / perf / sys / login`）—— 保留 xanzc_frontend 原状
- `http.js` 的"反向迁移"到 xanzc_pc —— 不在本次

## 8. 实施顺序（写计划时细化）

1. 复制视图文件（不会影响构建，因为路由还没引）
2. 新增 API 文件（metrics / orgs / employees / kpi）
3. 覆盖 `api/report.js`
4. 改 `api/index.js` 追加导出
5. 改 `mock/index.js` 追加 + 替换
6. 改 `router/index.js` 新增 2 条路由 + 微调已有 3 条
7. 改 `components/AppSidebar.vue` 加 1 行守卫
8. `npm run build` + `npm run dev` 验证
