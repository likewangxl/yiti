# 数据权限整改交接文档（2026-07-19）

> 用途：跨会话交接。新会话直接读本文档即可接上下文继续。
> 分支：`feature/redengine-merge`（主分支 master）。工作区尚有 3 个**有意未提交**的本地文件（AGENTS.md / bootstrap application.yml / xanzc_frontend/vite.config.js，本机端口 18081/30523/8091 改动），**勿提交勿回退**。

## 一、背景与结论

调查起点：用户一人多角色，需右上角切换角色换权限，问题集中在绩效/报表模块；猜想"数据权限应由系统设置的 DATA_SCOPE 决定"。

代码验证结论：
- 系统是**会话级单激活角色**模型（`AuthService.switchRole` 重建只含单角色的 `CurrentUserContext` 写回 Session；RBAC 与 DataScope 统一走 `PermissionCacheService.getEffectiveRoleIds`，有会话时只返回激活角色）。DATA_SCOPE 配置在 `PT_ROLE_BIZ_SCOPE`（角色×BizType→一档范围），切角色=切换生效哪套配置，属设计内行为。
- 真正的问题：绩效/报表存在**绕开 DATA_SCOPE 的角色硬编码**与**完全无范围控制的裸查接口**，共整改 7 个任务（下表）。

## 二、已完成（全部 TDD + 逐任务复核 + 已提交）

| 提交 | 任务 | 内容 |
|---|---|---|
| `d94fb033` fix(perf) | A1 | `GET /api/perf/kpi-schemes`：roleId 硬编码(238/129/1/229) → 专用能力位 **`P_PERF_KPI_VALL`**（`ResourceApi.hasResourcePermission` 常量 ID 直查；`KpiSchemeService.pageDtoWithVisibilityGate`）。**不挂** PERF_CONFIG DataScope，原因见"四、关键决策" |
| 同上 | A2 | `GET /api/perf/metrics`：接线既有 `pageWithScope`（新增 `pageWithScopeDto`，pageNo=1+MAX_VALUE 保持全量契约） |
| 同上 | A3 | `GET /api/perf/alloc-relations`(+/history)：新增 `getCurrentAllocationsScopedDto/getAllocationHistoryScopedDto`，按 PERF_CONFIG scope 查后归属过滤（ALL/SELF*/ORG*/无配置 fail-close） |
| `11222799` fix(report) | B1 | 新增 **`report/service/scope/ReportScopeGuard`**（REPORT 机构/员工归属校验器，fail-close；个人级 scope 对机构维度一律拒绝）。Dashboard org/emp/president 三端点接入，越权 `RPT-40301` |
| 同上 | B2 | PerfSummary：subjectIds 按 dim 逐主体过滤，越权**剔除**+warn（不整单 403） |
| 同上 | B3 | Touch/CustPool 汇总：显式 orgId 才校验，越权新错误码 **`RPT-40304`**；空参兜底本机构不校验 |
| 同上 | B4 | FreeReport：操作人角色码 `R_2FAB45A1` → 能力位 **`R_RPT_FREE_OPERATOR`** 常量 ID 直查；前端 `freeReportPermission.js` + `FreeReport.vue` 改 `getMyPermissions().resourceUrls` 判定（刻意不特判 isSystemAdmin，对齐后端无旁路语义） |
| 同上 | 附带 | ① 4 个汇总/仪表盘 `@Cacheable` key 加 `@currentUserApi.getCurrentEmpId()` 前缀（堵越权用户命中他人缓存绕过校验）；② `listBatches` 直出 entity 红线→`FreeReportBatchVO`（隐藏 fileObjectKey/colDefs）；③ Dashboard 系列单测/IT 陈旧断言订正（指标码 M_0265/M_0348、president 缺省回退 ROOT="1" 语义，stash 取证为既存基线红）；④ IT 基建 `BaseControllerIT` 桩 `anyString→any`（anyString 不匹配 null）+ 新增 3 个越权 403 IT |

验证基线（全部本机实跑过）：绩效 `mvn test -pl performance-engine-center` 1128 全绿；报表 `mvn verify -pl report-analytics-center` 380 单测 + 65 IT 全绿；前端 `npx vitest run` 285 全绿；两模块 `scripts/check-contract-docs.sh` OK。契约文档（各模块 03/04）与模块 CLAUDE.md 已同提交更新。

## 三、⚠️ 未完成——上线/联调前置（最重要）

**两个种子 SQL 尚未在任何库执行（包括 yiti 开发库）**，只交付了脚本：

1. `docs/superpowers/sql/2026-07-19-perf-kpi-scheme-view-all-capability.sql` — 注册 `P_PERF_KPI_VALL` + 绑定角色 1/129/238（229 已停用不绑）
2. `docs/superpowers/sql/2026-07-19-free-report-operator-resource.sql` — 注册 `R_RPT_FREE_OPERATOR` + 仅绑定角色 237

不执行的后果（fail-close，安全方向但功能降级）：资财部/管理员在 kpi-schemes 只见受限视图（openDetail=1 且 ACTIVE）；自由报表操作人丧失导入/禁用/启用/删除能力且看不到禁用批次。**本地联调这两个页面前先在 yiti + onepl_test_bootstrap 跑脚本；生产必须先脚本后发版。** 执行前按仓库惯例备份到 `docs/superpowers/sql/backup/`。

## 四、关键决策记录（为什么这么改，勿翻案）

1. **A1 为何不挂 PERF_CONFIG DataScope**：yiti 实查（联查 PT_ROLE_RESOURCE × PT_ROLE_BIZ_SCOPE）发现全部 11 个启用角色（含机构员工等普通角色）PERF_CONFIG 均为批量种子 ALL，且 P_PERF_KPI_LIST/ADD/UPD/PUB 四资源全角色绑定——配置层从未区分资财部与普通员工（这正是当年硬编码存在的原因）。按 scope==ALL 判定会把"向员工开放明细=否"的方案暴露给全员；而收窄这些角色的 PERF_CONFIG 会波及 target-plans/target-values 等共用该旋钮的端点。故用独立能力位，行为零漂移。
2. **能力位 SQL 的 PARENT_RESOURCE_ID 必须自引用**：`ResourceMapper.selectPublicInterfaceIds`（NULL/空父资源）命中的行会被 `RoleResourceService.replaceMenus`（分配菜单）自动绑给任意拿到菜单的角色；挂真实菜单则随菜单分配自动带上。自引用是唯一隔离方式。既有 R_RPT_FREE_DISABLE/ENABLE 被 ~20 角色广泛绑定正是 NULL 父资源所致。
3. **能力位判定用常量资源 ID 直查，不用 `matchResource`**：后者是全量资源流 + AntPathMatcher + findFirst() 无特异性排序，未来注册 `/api/reports/free/*` 类通配资源会静默碰撞。注意：直查不检查 PT_RESOURCE.STATUS，停用资源行≠回收能力，回收须删 PT_ROLE_RESOURCE 绑定行。
4. **`hasResourcePermission` 无 SYS_ADMIN 旁路**（旁路只在 AuthorizationInterceptor 的 BizAuth AOP 层），且走 `getEffectiveRoleIds` 按当前激活角色判定——与切角色行为一致，管理员不自动获得操作人能力（产品语义如此）。
5. **B2 选"剔除"而非整单 403**：前端多选主体场景不因个别越权 ID 打碎整页。
6. **ISMENU 真实语义 1=菜单 0=接口**（DDL 注释"0 是 1 不是"是错的，以数据+Mapper 查询为准）。

## 五、建议另立任务的遗留项（业务侧确认后再动）

1. **KPI 写资源全角色绑定**：P_PERF_KPI_ADD/UPD/PUB 在 PT_ROLE_RESOURCE 全角色绑定，任意员工角色理论上可过 RBAC 创建/发布 KPI 方案（预存漏洞，本轮未动）。
2. **PT_ROLE_BIZ_SCOPE 批量 ALL 精细化清理**：普通角色大面积 ALL 是历史种子默认值；收窄会同时影响 target-plans/target-values/metrics 等已走 scope 的端点可见范围，需业务梳理。
3. `AmasPriceApprovalQuery` 无 DATA_SCOPE 是产品明确决策（代码注释有记录），未动。
4. `PERF_METRIC_DEF` 表无机构列，ORG/ORG_SUBTREE 档位降级映射 `created_by`（实际为空列表）；未来若给角色配该档位需先补机构列（03 文档 A.1 已注明）。

## 六、快速自检命令（新会话接手时）

```bash
git log --oneline -3        # 应见 11222799 / d94fb033
mvn test -pl performance-engine-center      # 1128 全绿
mvn verify -pl report-analytics-center      # 380 + 65 全绿（注意勿用 "| tail" 接 mvn，会吞退出码）
cd xanzc_frontend && npx vitest run         # 285 全绿
mysql -uroot -pdjdev yiti -e "SELECT RESOURCE_ID FROM PT_RESOURCE WHERE RESOURCE_ID IN ('P_PERF_KPI_VALL','R_RPT_FREE_OPERATOR');"
# ↑ 查出 0 行 = 种子 SQL 还没跑（见第三节）
```
