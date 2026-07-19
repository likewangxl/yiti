# report-analytics-center/ CLAUDE.md

本文件为 `report-analytics-center` 模块（报表分析中心）提供开发指导，是本目录唯一权威来源。

## 模块概述

**report-analytics-center** 是报表分析中心（支撑域，**只读**模块），基础包名 `com.bank.branch.platform.report`，Maven 坐标 `com.bank.branch.platform:report-analytics-center`。

模块覆盖以下子域，均为只读聚合/查询，不承载业务写事务（自身自有表除外）：

- **传统报表 + 导出**：查询维度元数据、动态查询、已保存查询 CRUD、仪表盘（分行/机构/员工三级）、触达/绩效/客户池三类汇总报表，均支持异步导出任务。
- **screen 大屏子域**：经营管理大屏（三级视角 + 全配置化数据源）+ 画布设计器（拖拽式所见即所得，草稿/发布双态 + 乐观锁）+ 大屏运行时渲染。
- **自由报表（FreeReport）**：Excel 导入动态列展示 + 批次管理 + 下载。
- **数据湖查询**：只读消费 `DATALAKE_XAN_*` 系列外部表（对公/个人存贷款账户、资产负债分配关系），当前以 `DatalakeQueryService` 形式存在，尚无独立 REST Controller 暴露；批次核对场景由 `DataImportQueryController`（`amas_dt_import_*` 明细/汇总）承接。
- **AMAS 审批查询**：定价审批、业绩调整审批历史、分配调整申请历史（含预览）、公告查询。
- **DataScope picker**：`ReportScopeController` 提供报表数据范围（数据范围选择器）统一入口供前端复用。

## 依赖关系

`report-analytics-center` 是**只读**模块，**禁止**被任何业务模块依赖：
- 业务模块如需查询报表数据，**直接调上游 `*Api`**（不经 report 中转）
- report 自身**不暴露**任何 `*Api`（`api/` 目录仅占位 `package-info.java`）
- 只读消费 `auth-permission-center`（`CurrentUserApi` / `BizScopeApi` / `OrgApi`）、`system-governance-center`（`DictApi` / `AuditApi` / `FileApi`）、`performance-engine-center`（`MetricApi` / `KpiApi`）、`customer-marketing-center`（`CustomerQueryApi` / `TouchTaskQueryApi`）的 `*Api`

## 架构规则与红线

1. 只读模块定位是根 [CLAUDE.md](../CLAUDE.md) 已声明的架构规则，本模块严格重申：不被业务模块依赖、不对外暴露 `*Api`，架构守护 `RptModuleStructureArchTest` 强制 `api/` 目录禁出现 `*Api.java`
2. Controller 公共方法必须标注 `@BizAuth` 且 `bizType` 必须为 `BizType.REPORT`（`RptBizAuthConsistencyArchTest` 现含两条规则：单档 bizType 校验 + 2026-07-19 起新增"必须标注"强制覆盖率校验，后者由 `AllocPreviewController` 缺鉴权事件触发补上，见下节"关键实现要点与踩坑"）
3. Controller 方法签名/局部变量禁出现 Entity（`RptNoEntityInControllerArchTest` / `RptNoEntityInControllerLocalsArchTest`）
4. facade 测试禁用 `assertThrows(UnsupportedOperationException.class, ...)` 及 "V1.1 delivered" 占位字面量（`RptNoUoeInFacadeTestsArchTest` / `RptNoV11UOEArchTest`）
5. 跨模块调用一律走对方 `*Api`；schema/PT_RESOURCE 变更走手工 SQL（Flyway 已彻底废弃，详见根 CLAUDE.md "Flyway 禁令"红线）

## 关键实现要点与踩坑

- **`AllocPreviewController` 鉴权缺失反例（已于 2026-07-19 修复，保留为历史教训）**：该 Controller（`GET /api/report/alloc-preview`）曾**完全没有** `@BizAuth`，也没有类级 `@RequestMapping`（路径直接写在方法注解里）——`PT_RESOURCE` 早已登记 `RES_ALLOC_PREVIEW`，但因方法未标注注解，鉴权 AOP 实际不拦截，运行期访问控制强度弱于本模块其余所有端点。TDD 修复：先在 `RptBizAuthConsistencyArchTest` 新增"Controller 公共方法必须标注 `@BizAuth`"规则并跑 Red（仅 `AllocPreviewController.preview` 命中），再补 `@BizAuth(bizType = BizType.REPORT, action = BizAction.READ)` + 类级 `@RequestMapping("/api/report")`（方法级 `@GetMapping("/alloc-preview")`，总 URL 与修复前完全一致，仍是历史遗留的单数 `/api/report/`，未改为 `/api/reports/`）转 Green。新增功能不要照抄这个历史反例，务必对齐惯例主动补 `@BizAuth`。
- **FreeReport 故意不清理旧 OBS 文件**：`FreeReportServiceImpl.importExcel` 在 `@Transactional` 内先删同操作人的同名旧批次记录（`RPT_FREE_REPORT_BATCH`/`ROW`），但**故意不删旧对象存储文件**——MD5 去重下新旧批次可能共享同一 `FILE_OBJECT`，删旧文件会误删新批次仍在用的对象；且对已不存在记录调 `fileApi.deleteFile` 会抛异常，把事务标记为 rollback-only 致整单回滚。旧对象留存视为可接受的孤儿。
- **外部只读表边界**：`AMAS_*`（定价/业绩调整审批、审批流程记录）、`amas_dt_import_*`（数据导入明细/汇总）、`PERF_ALLOC_ADJUST_*`（分配调整申请/明细）、`DATALAKE_XAN_*`（数据湖对公/个人存贷款账户、资产负债分配关系）、`sys_notice`（公告）均为外部系统/数据湖同步表，不是本仓库其他业务模块的私有表，因此本模块直接建 Mapper 只读消费不违反"跨模块必须走 `*Api`"红线；但这些表 schema 由外部系统掌控，改表结构前须先确认外部契约。
- **SQL 探查仅 `R_BACK_TECH` 角色独占**：`POST /api/reports/sql-probe/execute`（资源 `R_RPT_SQL_EXEC`）在 `PT_ROLE_RESOURCE` 层面只绑定 `R_BACK_TECH`（中后台科技岗），业务角色不可访问；执行前还要求 reason 必填 + 双写审计（业务历史 `SQL_PROBE_HISTORY` + 治理 `governance.audit_log`）。这是强约束，调整前须先确认合规要求。
- **`.scr-block` 必须 `height: 100%`，否则 echarts 静默空白**：大屏运行时/设计器的图表宿主容器若无显式 `height:100%` 会随内容塌陷为 0 高，echarts 组件拿到 0 高度 canvas 后**不报错、静默空白**（纯 DOM 类组件因靠内容撑高不受影响）。回归由前端 `xanzc_frontend/src/views/screen/__tests__/ScreenBlockHeight.spec.js` 断言 `_screen-theme.scss` 中 `.scr-block` 顶层声明段含 `height: 100%` 来守护；大屏空白先量 canvas 高度，不要先怀疑取数逻辑。
- **`ScreenKpiSchemeMapper` 不能改回/合并为 `PerfKpiSchemeMapper`**：大屏 KPI_DETAIL 数据源用的 `ScreenKpiSchemeMapper`（`com.bank.branch.platform.report.mapper`，只读封装 `PerfKpiScheme` 实体）与 performance 模块的 `PerfKpiSchemeMapper`（`com.bank.branch.platform.performance.mapper`）包名不同，但若改成同一个简单类名，`@Mapper` 默认 Bean 名（类名首字母小写）会相同——`bootstrap` 合体启动聚合全部模块到同一 Spring 容器时会抛 `ConflictingBeanDefinitionException`。保持独立类名是有意为之，不要"顺手"合并。
- **Caffeine CacheManager 是全平台共享 Bean**：`ReportCacheConfig#rptCacheManager()` 标注 `@Primary`，未设置 `cacheNames` 走 dynamic 模式，任意 `@Cacheable("xxx")` 用到的 cache 名都会按本模块的 Caffeine spec（TTL 5 分钟 + maxSize 500）自动创建；其他模块（如 performance-engine 的 `perf:metric_def` 等）的 `@Cacheable` 也会路由到这同一个 Bean。改动本配置（TTL/maxSize/移除 `@Primary`）会影响全平台缓存行为，不是本模块私有配置。

## 已知技术债 / 例外

- 通用异步导出（`RptExportService.createTask`）仍是创建即同步执行模型（INSERT PENDING → UPDATE RUNNING → `strategy.execute` 内联生成字节流并调 `governance.FileApi.upload` → UPDATE SUCCESS），未切真异步线程池；SQL 探查导出已独立线程池异步（`sqlProbeExportExecutor`，`RptAsyncConfig`）。`task.fileKey` 持久化的是 `FileObjectDTO.id` 而非对象存储 key，下载走 `fileApi.getDownloadUrl` 拿预签名 URL。
- `RPT_SNAPSHOT_TASK` 仅建表未启用，启用条件（DAU/仪表盘 P99）另议。
- `DataScopeType.WORKFLOW_PARTICIPANT` 未在本模块落地（无 `business_key` 列）。
- 数据湖查询（`DatalakeQueryService`）当前无独立 REST Controller/PT_RESOURCE 暴露，也无对应测试覆盖，属基础设施先行、对外查询接口待补。
- 大屏画布 `?preview=draft` 当前仅"登录 + 管理端菜单门禁"即可见草稿，未做独立资源校验；`discardDraft` 未做前置状态校验也绕过乐观锁（均为已知遗留，非阻塞）。
- 详细版本演进（screen 子域历次扩展、错误码计数订正等）一律以 `git log report-analytics-center/` 为准，本文件不维护变更叙事。

## 清单与契约指引

- Controller/端点/错误码/表结构的完整清单以源码目录为准，不在本文件重复列举：`src/main/java/com/bank/branch/platform/report/controller/`、`enums/RptErrorCode.java`、`entity/`、`mapper/`
- 接口设计与报文契约：`docs/modules/report-analytics-center/03-接口设计与报文.md`
- 对外 API 契约（V1.0 维持"不暴露 `*Api`"结论）：`docs/modules/report-analytics-center/04-对外API契约.md`
- screen 大屏设计器操作指南（含画布双态、数据源配置、KPI 明细数据源等）：`docs/modules/report-analytics-center/10-大屏设计器操作指南.md`
- 示例代码统一登记于 `docs/code-examples.md`（如只读模块消费上游 `*Api` 的典范：`report/service/impl/CustPoolSummaryServiceImpl.java`；异步导出任务框架：`report/export/impl/RptExportServiceImpl.java`），本文件不复制代码块
- 共通开发规范：`docs/common-dev-guide.md`

## 测试指引

- 新增功能先跑通红-绿-重构闭环（TDD 绝对红线，见根 CLAUDE.md）
- 测试数据统一使用 `TEST_RPT_*` 前缀（导出相关另有 `TEST_RPT_EXP_*` / `TEST_RPT_E2E_*` 子前缀，并发场景预留 `CONCUR_RPT_*`）
- 架构守护测试位于 `src/test/java/com/bank/branch/platform/report/arch/`：`RptBizAuthConsistencyArchTest` / `RptNoEntityInControllerArchTest` / `RptNoEntityInControllerLocalsArchTest` / `RptModuleStructureArchTest` / `RptNoUoeInFacadeTestsArchTest` / `RptNoV11UOEArchTest`
- 前端 screen 子域测试见 `xanzc_frontend/src/views/screen/__tests__/`（含 `ScreenBlockHeight.spec.js` 高度契约守护）与 `designer/__tests__/`；改动大屏渲染/画布前先看既有 vitest 用例了解拖拽/保存/渲染三态一致性约定
