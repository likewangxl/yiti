<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-07-12 | Updated: 2026-07-12 -->

# xanzc_frontend

## Purpose

"银行营销 · 业务执行 · 绩效平台" PC 前端工程，Vue 3 单页应用。通过 Vite dev-server 的 `/api` 反向代理对接 `yiti` 后端（`bootstrap` 模块启动的 9 个业务模块 REST 接口），承载八大功能域：工作台、信息聚合（通讯录/文档/产品库/导航）、绩效与考核、内部评价（评价任务 + 奖励分配）、报表分析、历史数据查询（担保/定价审批/业绩调整）、系统设置。上线日 2026-06-10，之后持续迭代（内部评价 EVAL/REWARD 双轨、担保数据导入等为近期热点）。

不是 Maven 多模块的一部分，是独立的 `npm` 工程，与仓库根 `CLAUDE.md` 描述的 Java 后端体系是"前后端分离、REST 对接"的关系，不共享任何构建产物。

## Key Files

| File | Description |
|------|-------------|
| `package.json` | 依赖清单 + npm scripts（`dev`/`build`/`preview`），`engines` 声明 `node>=18.12.1`（与 README 的"注意 Node 16 兼容"存在文字表述差异，实际以 `vite@4.5.14` 锁定为准，见下文"已知约束"） |
| `vite.config.js` | dev server 固定端口 8090、`host 0.0.0.0`、`/api` 代理到 `http://localhost:18081`、`@` 别名指向 `src`、`optimizeDeps` 显式预打包、`allowedHosts` 放行花生壳内网穿透域名 |
| `index.html` | SPA 唯一入口，`<title>银行营销 · 业务执行 · 绩效平台</title>`，挂载点 `#app` |
| `src/main.js` | `createApp` 挂载 `App.vue`；`ElementPlus` **全量注册**（非按需，见"已知约束"）+ `zh-cn` locale；装 Pinia + router |
| `src/App.vue` | 仅一个 `<router-view />`，无全局布局逻辑 |
| `src/router/index.js` | 路由表（`createWebHashHistory`，hash 模式）+ 全局前置守卫（未登录先探测 `GET /api/auth/current-user`，成功则续期 session，失败跳 `#/login?redirect=`）+ 纯 DOM 顶部进度条（未引入 nprogress） |
| `src/stores/user.js` | Pinia `user` store，`sessionStorage` 持久化（key `xanzc:user`），暴露 `isLoggedIn`/`roles`/`activeRoleId`/`isSystemAdmin`（`isSystemAdmin` 判定与后端 RBAC 口径一致：激活角色 `roleCode === 'SYS_ADMIN'`） |
| `src/api/http.js` | 唯一 axios 实例；请求拦截注入 `X-Trace-Id`；响应拦截解包 yiti `ResponseWrapper`（`code==='0'` 成功，分页取 `body.page` 而非 `body.data`）+ 401 自动跳登录；导出 `call(method,url,config,fallback)`（真后端优先，写操作失败必 throw，仅 GET 失败才回退 mock）与 `unwrapPage(r)` |
| `src/api/eval.js` | 内部评价模块封装（标签/人员标签/规则/统一任务列表 + 评价任务导入批次 + 奖励分配导入批次，双轨并存），近期改动最密集的文件之一 |
| `src/views/eval/Tasks.vue` | 评价任务管理页（管理端"统一任务列表" = 规则任务 AUTO + 导入批次 EVAL/REWARD 合并展示），近期热点视图 |
| `src/views/eval/RewardTask.vue` | 奖励分配用户端待处理页（按部门聚合，批量提交分配值） |
| `start-dev.sh` | 后台启动脚本：`start`/`stop`/`restart`/`status`/`tail` 五个子命令，PID 存 `logs/vite.pid`，日志按时间戳滚动到 `logs/dev-*.log`，`status` 会附带 `ss -lntp` 端口占用信息 |
| `README.md` | 技术栈/目录结构/开发启动/后端联调/关键设计点的现状说明，与本文件互补（README 偏"如何用"，本文件偏"给 AI agent 的现状快照"） |

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `src/api/` | 24 个文件：`http.js`（axios 核心）+ `index.js`（仅聚合 `workspace/perf/report/system/auth/metrics/kpi/employees/orgs/customers` 10 个模块，**不含** `eval.js`/`guarantee.js`/`workflow.js`/`announcement.js`/`documents.js`/`history.js`/`nav.js`/`products.js`/`resources.js`/`userDirectory.js`/`users.js`，后者由各 view 直接 `import '@/api/xxx'`）+ 22 个业务模块封装（每个对应后端一个 `*Controller` 分组，注释里标注真实后端路径） |
| `src/components/` | 4 个全局组件：`AppHeader`/`AppSidebar`（读 `getMyMenus()` 动态渲染菜单树，非静态路由表）/`AppBreadcrumb`/`AllocAdjustViewDialog`（业绩调整审批查看弹窗） |
| `src/composables/` | 1 个：`useDict.js`（字典下拉封装，包 `listDictItems(dictType)`，返回 `{options, labelOf, loading, reload}`） |
| `src/layouts/` | 1 个：`DefaultLayout.vue`（Sidebar + Header + Breadcrumb + 内容区，`router-view` 用 `$route.fullPath` 做 key 强制重渲染，配纯 opacity 过渡） |
| `src/mock/` | 1 个：`index.js`，**全部导出空数组/空对象**（工作台/绩效/报表/系统设置各域的占位形态），当前策略是"后端无数据=前端显示空"，禁止再塞假数据 |
| `src/router/` | 1 个：`index.js`（见 Key Files） |
| `src/stores/` | 1 个：`user.js`（见 Key Files），当前唯一 store |
| `src/styles/` | `tokens.scss`（设计 token：色板/文字/边框/侧边栏色，与 `vite.config.js` 的 `additionalData: '@use "@/styles/tokens.scss" as *;'` 全局注入绑定）+ `index.scss`（全局样式入口） |
| `src/utils/` | `datetime.js`（日期格式化）+ `sqlCrypto.js`（AES-128-ECB 加解密 SQL 文本，key 硬编码 `yiti-sql-probe-k` 需与后端 SQL 探查功能一致，供 `views/report/Sql.vue` 用） |
| `src/views/eval/` | 6 个：`Tags`/`UserTags`（标签体系）/`Rules`（评价规则，旧流程）/`Tasks`（统一任务列表，管理端）/`MyTasks`（评价任务用户端待处理，旧流程用户端入口已隐藏）/`RewardTask`（奖励分配用户端） |
| `src/views/guarantee/` | 3 个：`Query`（担保信息查询）/`DataImport`（数据导入查询）/`Notice`（公告查询），对应历史数据查询域 |
| `src/views/history/` | 3 个：`PriceApproval`/`PriceApprovalDetail`（定价审批查询/详情）/`PerfAdjustQuery`（业绩调整查询） |
| `src/views/info/` | 4 个：`NavHub`（网址导航）/`AddressBook`（通讯录）/`ProductLib`（产品资料库）/`DocCenter`（常用文档），对应信息聚合域 |
| `src/views/login/` | 1 个：`Index.vue`（登录页，支持账号登录 / 统一认证 UIAS 切换） |
| `src/views/perf/` | 8 个：`Metrics`（指标库）/`KpiRules`/`KpiScoreDetail`/`Targets`/`TargetValues`/`Import`（数据导入）/`Adjust`（业绩调整）/`Compute`（考核计算） |
| `src/views/report/` | 8 个视图（`Dynamic`/`Dashboard`/`Presets`/`FreeReport`/`FreeReportDetail`/`Sql`/`AmasApprovals`/`AmasApprovalDetail`）+ `components/` 子目录 3 个（`MetricPicker`/`SchemeListDialog`/`SchemeSaveDialog`） |
| `src/views/system/` | 16 个视图（用户/角色/资源菜单/权限配置/字典/工作日历/任务调度/审计日志/通知消息/系统配置/文件管理/超时规则/公告管理及详情/审批流程列表及编辑）+ `flow/` 子目录 6 个（`FlowCanvas`/`FlowNodePanel`/`FlowEdgePanel`/`FlowPalette`/`ConditionBuilder`/`ApproverPicker`，构成审批流程设计器） |
| `src/views/workspace/` | 3 个：`Index`（工作台首页）/`AnnouncementList`/`NotificationList` |
| `docs/` | 仅 1 份设计文档：`superpowers/specs/2026-05-06-merge-report-module-design.md`（报表模块合并设计），非本仓库统一的 `docs/modules/*` 体系 |

## For AI Agents

### Working In This Directory

- **技术栈实际锁定版本**（以 `package-lock.json` 解析结果为准，非 `package.json` 的 `^` 范围声明）：`vue@3.5.34`、`vite@4.5.14`、`element-plus@2.13.7`。README 写"Vue 3.4"是文档滞后，实际已在 3.5.x；不影响兼容性可忽略。
- 新增/修改视图时优先确认对应后端 REST 路径：多数 `api/*.js` 文件头部有注释列出该模块后端真实 `@RequestMapping` 全貌（如 `perf.js` 第 7-18 行），改动前先读这段注释而不是直接猜端点。
- `api/index.js` 只聚合了 10 个模块，其余 12+ 个业务 api 文件（含近期热点的 `eval.js`）**不在**这个聚合导出里；view 里 `import { xxx } from '@/api/eval'` 是常态，不要假设所有 api 都要走 `api/index.js`。
- 路由新增页面时同步维护 `src/router/index.js` 里的 `meta: { title, group }`（侧边栏按 `group` 分组渲染，来自 `AppSidebar.vue` 消费的是 `getMyMenus()` 动态树而非这份静态路由表——路由表的 `group`/`title` 目前只用于面包屑/页面标题，真正决定"菜单里出现什么"的是后端 `PT_RESOURCE` 数据）。
- 修改样式变量统一改 `src/styles/tokens.scss`，不要在组件里硬编码色值——`vite.config.js` 已把该文件设为 SCSS 全局注入，任何 `.vue` 的 `<style lang="scss">` 都能直接用 `$primary`/`$text-1` 等变量。

### 本地启动 / 联调

```bash
# 首次：安装依赖（README 强调走 --legacy-peer-deps，避免 peer 冲突报错）
npm install --legacy-peer-deps --no-fund --no-audit

# 方式一：前台直接跑（Ctrl+C 停止）
npm run dev

# 方式二：后台脚本（推荐长期联调，含 PID 管理 + 滚动日志）
./start-dev.sh          # 启动，端口固定 8090（vite.config.js strictPort=false 但脚本按 8090 探测）
./start-dev.sh status   # 查看运行状态 + 端口占用
./start-dev.sh tail     # 跟随最新日志
./start-dev.sh restart  # 重启
./start-dev.sh stop     # 停止
```

- 打开 `http://localhost:8090/`，未登录会自动跳 `#/login?redirect=/workspace`；默认账号 `admin / 123456`（由 `yiti` 后端 `PT_USER` 表 seed 数据提供，不是前端写死）。
- **联调路径**：`vite.config.js` 的 `server.proxy['/api'].target` 当前指向 `http://localhost:18081`（注意不是 8080/8081 —— 参见 `git log` 中 `chore(config): 端口避让` 一系列 commit，后端端口已迁移到 18081）；确保 `bootstrap` 模块已在该端口起服务，否则所有真实接口调用会报"网络异常或后端未启动"。
- Linux/沙箱环境若配置了全局 `http_proxy`，需要 `NO_PROXY=localhost,127.0.0.1 npm run dev`（或同前缀跑 curl 探活），否则请求会被代理误拦截。
- 本文件与 `bootstrap`/`performance-engine-center` 等后端模块的 `AGENTS.md` 是并列关系，不是依赖关系；后端真实启动端口以各自模块配置和 `bootstrap/src/main/resources/application.yml` 为准，本文件的 18081 仅反映当前 `vite.config.js` 里写死的代理目标。

### mock 机制

- `.env.development` / `.env.production` 当前均设 `VITE_USE_MOCK=false`（`.env.development` 里的注释"默认开发模式走 mock"已过时，与实际值矛盾，不要被注释误导——实际默认就是走真实后端）。
- 只有显式把 `VITE_USE_MOCK` 改成 `true` 才会让 `api/http.js` 里的 `call()` 直接返回 `fallback` 参数（各 `api/*.js` 调用 `call()` 时传入的最后一个参数），完全跳过网络请求。
- 更常见的是"失败兜底"而非"主动 mock"：`USE_MOCK=false` 时，GET 类查询失败会自动打印 `[api fallback] ... 使用 mock 兜底` 并回退 `fallback` 值；**写操作（POST/PUT/DELETE/PATCH）失败永远 throw**，不会被静默吞掉（`http.js:110-118` 的 `isWrite` 判断），避免弹窗误判成功。
- `src/mock/index.js` 里的兜底数据当前**全部是空数组/空对象**（工作台统计、绩效指标树、报表看板等），是"防止解构越界崩溃的占位形态"，不是"看起来真实的假数据"——如果发现某处又把假数据塞回 `mock/index.js`，属于对当前约定的回退，需要向用户确认后再做。

### Testing

- 本工程**没有任何前端测试框架配置**（无 vitest/jest，`package.json` 无相关 devDependencies），验证方式是启动 dev server 后手工操作 / 浏览器控制台观察网络请求，不要假设存在可运行的 `npm test`。

## Dependencies

### Internal（对接的 yiti 后端模块，通过 `/api/*` REST 而非任何 Java 契约）

| 前端目录 | 对接的 yiti 模块 | 主要 endpoint 前缀 |
|---|---|---|
| `views/workspace/` | portal-content-center | `/api/portal/workspace`, `/api/notifications/*` |
| `views/eval/`（+ `api/eval.js`） | performance-engine-center | `/api/admin/eval/*`, `/api/eval/*` |
| `views/perf/`（+ `api/perf.js`/`metrics.js`/`kpi.js`） | performance-engine-center | `/api/perf/*` |
| `views/report/` | report-analytics-center | `/api/reports/*` |
| `views/guarantee/`、`views/history/` | performance-engine-center / report-analytics-center（担保与业绩调整落在绩效模块，历史查询走报表只读接口） | `/api/perf/*`, `/api/reports/*` |
| `views/info/` | portal-content-center | 通讯录/文档/产品库/导航相关端点 |
| `views/system/` | system-governance-center / auth-permission-center | `/api/admin/sys/*`, `/api/admin/roles/*`, `/api/admin/biz-scopes/*`, `/api/admin/resources/*` |
| `views/login/`、`stores/user.js` | auth-permission-center | `/api/auth/*`（含 `current-user`/`login`/UIAS 统一认证回调） |

### External（npm 依赖，见 `package.json`）

- `vue@^3.3.4`（锁定 3.5.34）+ `vue-router@^4.1.6` + `pinia@^2.0.35` — 核心框架三件套
- `element-plus@^2.5.0`（锁定 2.13.7）+ `@element-plus/icons-vue` — UI 组件库，**全量注册**（`main.js`），非按需
- `echarts@^5.4.3` + `vue-echarts@^6.6.1` — 报表看板图表
- `axios@^1.4.0` — HTTP 客户端（唯一实例见 `api/http.js`）
- `dayjs@^1.11.10` — 日期处理
- `crypto-js@^4.2.0` — SQL 探查页 AES 加解密（`utils/sqlCrypto.js`）
- `xlsx@^0.18.5`、`html2canvas@^1.4.1`、`jspdf@^4.2.1` — 导出相关（Excel/截图转 PDF）
- `sass@^1.69.0` — SCSS 预处理（devDependency）
- `unplugin-auto-import`/`unplugin-vue-components` — 已装但**未启用** Element Plus 按需解析（`vite.config.js` 顶部注释明确说明原因：按需会导致路由切换时"点新菜单闪一下又跳回原页"的 bug）

## 已知约束

- **Vite 必须锁定 4.x**：`vite@5` 依赖 Node `crypto.getRandomValues`，与本工程要求的 Node 16 运行时不兼容；升级前必须先确认目标 Node 版本。
- **Element Plus 必须全量注册**：不要引入 `unplugin-vue-components` 的 `ElementPlusResolver` 做按需加载（见上）。
- **后端表名大小写约定**（影响前端字段/接口联调排错）：业务表全小写，但 `PT_*`/`EXT_*`/`ACT_*`/`QRTZ_*` 系列大写，字段本身通过 MyBatis-Plus `map-underscore-to-camel-case` 转驼峰，前端拿到的 JSON 字段是驼峰。
- **新接口必须先在后端登记 `PT_RESOURCE`**：前端调用一个新增的后端端点前，先确认该端点已挂 `@BizAuth` 并登记 `PT_RESOURCE`，否则会收到 `AUTH-40302 资源未登记`（表现为前端 403 弹窗"没有权限"）。
- **`.env.*` 与代码注释可能不同步**：改动 mock 策略、代理端口等配置前，以 `vite.config.js`/`.env.development` 的实际值为准，不要相信文件内的中文注释（本文件已记录 2026-07-12 发现的一处过时注释，见"mock 机制"一节）。

<!-- MANUAL: 手工补充内容写在此行以下，重新生成时会保留 -->
