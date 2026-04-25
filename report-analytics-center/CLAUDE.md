# report-analytics-center/ CLAUDE.md

本文件为 `report-analytics-center` 模块提供上下文说明。

## 模块概述

**report-analytics-center** 是报表分析中心（支撑域，**只读**模块），为整个平台提供动态查询 / 仪表盘 / 汇总报表 / SQL 探查 / 异步导出能力。

**当前版本**: V1.0（2026-04-25 交付）

V1.0 交付内容：
- 24 REST 接口（query-dimensions / dynamic-query / saved-queries×4 / dashboard×3 / 3 类汇总×2 + 4 SQL 探查 + 3 export-tasks + 1 占位 = 25 PT_RESOURCE）
- 4 张自有表（rpt_saved_query / rpt_snapshot_task / sql_probe_history / rpt_export_task）
- 4 个 ExportStrategy（DYNAMIC_QUERY / TOUCH_SUMMARY / PERF_SUMMARY / CUSTPOOL_SUMMARY，走 governance.FileApi.upload 真实上传 MinIO）
- SQL 探查（独立 readOnlyDataSource + JSqlParser AST 校验 + 双写审计）
- 30 条 RptErrorCode（25 基线 + 5 J 章扩展）
- DATA_SCOPE 7 类映射在仪表盘 / 汇总报表 / SQL 探查 3 处生效

**基础包名**: `com.bank.branch.platform.report`
**Maven 坐标**: `com.bank.branch.platform:report-analytics-center`

**Spec / Plan**: `docs/superpowers/plans/2026-04-25-report-analytics-center-v1.0-plan.md`

## 红线（不被任何业务模块依赖）

`report-analytics-center` 是**只读**模块，**禁止**被任何业务模块依赖：
- 业务模块如需查询报表数据，**直接调上游 *Api**（不经 report 中转）
- report 没有任何 `*Api` 接口对外暴露（`api/` 目录仅占位 `package-info.java`）
- 架构守护：`RptModuleStructureArchTest`（报表 `api/` 目录禁出现 `*Api.java` 文件）

## 跨模块依赖

V1.0 报表只读消费 4 个上游模块的 *Api（共 10 个接口）：

| 上游模块 | *Api | 用途 |
|---|---|---|
| auth-permission-center | CurrentUserApi | 当前用户 empId（导出归属、审计 operatorId）|
| auth-permission-center | BizScopeApi | DATA_SCOPE 7 类型查询 |
| auth-permission-center | OrgApi | 机构层级（仪表盘 ORG / ORG_SUBTREE）|
| governance | DictApi | 字典翻译（动态查询元数据 metaTree 节点）|
| governance | AuditApi | 双写审计（SQL 探查 / 异步导出 / 仪表盘 READ）|
| governance | FileApi | MinIO 上传（4 ExportStrategy upload + getDownloadUrl 拉预签名 URL）|
| performance | MetricApi | 指标查询（仪表盘 + 动态查询）|
| performance | KpiApi | KPI 查询（PerfSummary）|
| customer-marketing | CustomerQueryApi | 客户池查询（CustPoolSummary）|
| customer-marketing | TouchTaskQueryApi | 触达任务查询（TouchSummary）|

## 包结构

```
src/main/java/com/bank/branch/platform/report/
├── api/              # 占位（V1.0 不暴露 Api，仅 package-info.java）
├── controller/       # REST 控制器（10 个，对应 24 个端点）
│   ├── MetaController.java                  (A.1)
│   ├── DynamicQueryController.java          (A.2)
│   ├── DynamicQueryExportController.java    (A.3 占位)
│   ├── SavedQueryController.java            (B.1-B.5)
│   ├── DashboardController.java             (C.1-C.3)
│   ├── TouchSummaryController.java          (C.4-view + C.4-export)
│   ├── PerfSummaryController.java           (C.5-view + C.5-export)
│   ├── CustPoolSummaryController.java       (C.6-view + C.6-export)
│   ├── RptSqlProbeController.java           (D.1-D.4)
│   └── RptExportController.java             (E.1-E.3)
├── facade/           # 跨层编排（RptExportFacade）
├── service/          # 业务逻辑 + 4 个 ExportStrategy
│   ├── DashboardService / *Impl
│   ├── DynamicQueryService / *Impl
│   ├── SavedQueryService / *Impl
│   ├── *SummaryService / *Impl × 3（Touch / Perf / CustPool）
│   ├── SqlProbeService / *Impl
│   ├── ExportTaskService / *Impl
│   └── export/
│       ├── ExportStrategy.java（接口）
│       ├── RptExportService / *Impl（同步执行状态机）
│       ├── model/*Row（4 个 EasyExcel 行模型）
│       └── impl/4 个 *ExportStrategy（DYNAMIC / TOUCH / PERF / CUSTPOOL）
├── mapper/           # MyBatis Mapper（4 个：RptSavedQueryMapper / RptSnapshotTaskMapper / SqlProbeHistoryMapper / RptExportTaskMapper）
├── entity/           # 贫血模型（4 个）
├── enums/            # 错误码 + 状态枚举
│   └── RptErrorCode.java（30 条 RPT-* 错误码）
├── exception/
│   └── RptException.java（extends BizException）
├── config/           # Spring 配置
│   ├── RptReadOnlyDataSourceConfig.java（独立只读数据源 rptReadOnlyDataSource）
│   ├── DashboardPresidentMetricsConfig.java
│   └── ...
├── support/          # 工具类
│   ├── SqlSafeValidator.java（JSqlParser 4.9 AST 校验，≥15 边界用例）
│   └── ByteArrayMultipartFile.java（byte[] → MultipartFile 适配 governance.FileApi.upload）
└── listener/         # 占位
```

```
src/main/resources/
├── mapper/                 # MyBatis XML
└── sql/
    └── report/             # Flyway 迁移脚本（独立命名空间避免与 perf V1_0_0 冲突）
        ├── V1_0_0__rpt_init.sql                     # 4 张自有表 DDL
        ├── V1_0_1__rpt_meta_pt_resources.sql        # M1：8 条 PT_RESOURCE
        ├── V1_0_2__rpt_dashboard_pt_resources.sql   # M2：3 条
        ├── V1_0_3__rpt_summary_pt_resources.sql     # M3：6 条
        ├── V1_0_4__rpt_sql_probe_pt_resources.sql   # M4：4 条
        ├── V1_0_5__rpt_export_task.sql              # M5：rpt_export_task DDL
        ├── V1_0_6__rpt_export_pt_resources.sql      # M5：3 条
        └── V1_0_7__rpt_resources_align.sql          # M6：1 条占位（V1.1+ SQL 探查导出）
```

## V1.0 交付的 24 REST 端点（25 PT_RESOURCE = 24 真实 + 1 占位）

| Controller | 路径 | 鉴权 | RESOURCE_ID |
|---|---|---|---|
| MetaController | GET /api/reports/query-dimensions | REPORT/READ | R_RPT_META_QD |
| DynamicQueryController | POST /api/reports/dynamic-query | REPORT/READ | R_RPT_DQ_EXEC |
| DynamicQueryExportController | POST /api/reports/dynamic-query/export | REPORT/EXPORT | R_RPT_DQ_EXPORT |
| SavedQueryController | GET    /api/reports/saved-queries     | REPORT/LIST   | R_RPT_SQ_LIST |
| SavedQueryController | GET    /api/reports/saved-queries/{id}| REPORT/READ   | R_RPT_SQ_GET  |
| SavedQueryController | POST   /api/reports/saved-queries     | REPORT/WRITE  | R_RPT_SQ_SAVE |
| SavedQueryController | PUT    /api/reports/saved-queries/{id}| REPORT/WRITE  | R_RPT_SQ_UPD  |
| SavedQueryController | DELETE /api/reports/saved-queries/{id}| REPORT/DELETE | R_RPT_SQ_DEL  |
| DashboardController | GET /api/reports/dashboard/president  | REPORT/READ | R_RPT_DASH_PRES |
| DashboardController | GET /api/reports/dashboard/org/{orgCode} | REPORT/READ | R_RPT_DASH_ORG  |
| DashboardController | GET /api/reports/dashboard/emp/{empId} | REPORT/READ | R_RPT_DASH_EMP  |
| TouchSummaryController | GET /api/reports/touch-task-summary | REPORT/READ | R_RPT_SUM_TOUCH_VW  |
| TouchSummaryController | POST /api/reports/touch-task-summary/export | REPORT/EXPORT | R_RPT_SUM_TOUCH_EXP |
| PerfSummaryController | GET /api/reports/perf-summary | REPORT/READ | R_RPT_SUM_PERF_VW |
| PerfSummaryController | POST /api/reports/perf-summary/export | REPORT/EXPORT | R_RPT_SUM_PERF_EXP |
| CustPoolSummaryController | GET /api/reports/customer-pool-summary | REPORT/READ | R_RPT_SUM_CUST_VW |
| CustPoolSummaryController | POST /api/reports/customer-pool-summary/export | REPORT/EXPORT | R_RPT_SUM_CUST_EXP |
| RptSqlProbeController | POST /api/reports/sql-probe/execute | REPORT/EXECUTE_SQL | R_RPT_SQL_EXEC |
| RptSqlProbeController | GET  /api/reports/sql-probe/history | REPORT/LIST | R_RPT_SQL_HIST |
| RptSqlProbeController | GET  /api/reports/sql-probe/history/{id} | REPORT/READ | R_RPT_SQL_HIST_DTL |
| RptSqlProbeController | GET  /api/reports/sql-probe/schema-whitelist | REPORT/READ | R_RPT_SQL_WL |
| RptExportController | GET    /api/reports/export-tasks/{taskId} | REPORT/READ | R_RPT_EXP_STATUS |
| RptExportController | DELETE /api/reports/export-tasks/{taskId} | REPORT/WRITE | R_RPT_EXP_CANCEL |
| RptExportController | GET    /api/reports/export-tasks/{taskId}/download | REPORT/EXPORT | R_RPT_EXP_DOWNLOAD |
| （占位） | POST /api/reports/sql-probe/export（V1.1+） | - | R_RPT_SQL_EXP（STATUS=1 disabled） |

## 30 条 RptErrorCode（25 基线 + 5 J 章扩展）

- 40001-40010：业务态错误（saved query / data version / dim mismatch / size limits / export task NOT_FOUND/NOT_READY）
- 40301-40303：权限错误（无访问 / 数据范围不足）
- 42001-42009：SQL 探查（语法 / 白名单 / 关键字 / 行数 / 超时 / 并发 / 仅 SELECT / 长度 / 执行）
- 42207-42211：J 章扩展（行数 / 任务过期 / 下载越权 / DATA_SCOPE / metricCodes）
- 50001-50003：跨模块 / 缓存 / 异步导出启动失败（含 EXPORT_START_FAILED）

守护：`RptErrorCodeTest` 6 case（30 条 + 唯一性 + 中文消息 + EXPORT_START_FAILED 必含）。

## 异步导出（V1.0 同步执行模型）

`RptExportService.createTask` V1.0 同步执行（对齐 PerfExport V1.2 同款模型，BR-2 决策）：
1. INSERT PENDING
2. UPDATE → RUNNING
3. `strategy.execute(task)` 内部生成 EasyExcel 字节流 → 调 `governance.FileApi.upload(MultipartFile, operatorId)` → 返回 `FileObjectDTO.id` 写入 `task.fileKey`
4. UPDATE → SUCCESS（含 fileKey / rowCount / fileSize / expireAt = +7 days）

**关键**：`task.fileKey` 持久化的是 `FileObjectDTO.id`（governance.file_object 主键），不是 MinIO object key；下载链路 `RptExportFacade.getDownloadUrl` 委托 `fileApi.getDownloadUrl(fileKey)` 拿 1 小时预签名 URL。

V1.1+ 切真异步：把"落库 → 执行 → 回填"链路移到 @Async + ThreadPoolExecutor，createTask 立即返回 PENDING 不等 SUCCESS。

## SQL 探查特殊性

- 独立只读数据源 `rptReadOnlyDataSource`（与 Druid 主数据源隔离，强制 readOnly=true）
- JSqlParser 4.9 AST 校验（`SqlSafeValidator` ≥15 边界用例守护）：仅 SELECT / 白名单表 / 禁用关键字 / 子查询深度 ≤3 / 长度 ≤8000 字符
- 双写审计：业务历史 → `sql_probe_history`；治理审计 → `governance.audit_log`
- 角色限制：`R_BACK_TECH` 独占 `R_RPT_SQL_EXEC`（plan BR-1 决策强约束，业务角色不可访问）

## DATA_SCOPE 7 类映射

DATA_SCOPE 类型在 3 处生效：
- 仪表盘（DashboardServiceImpl）：`R_PRESIDENT` 映射 ALL，其他角色按 ORG_SUBTREE / SELF / SELF_ASSIGNED / ORG / SELF_CREATED
- 汇总报表（3 个 *SummaryServiceImpl）：ORG_SUBTREE / SELF
- SQL 探查：仅角色限制（`R_BACK_TECH` 独占），不走 DATA_SCOPE 行级过滤

`WORKFLOW_PARTICIPANT` 类型 V1.0 在 report 不落地（无 business_key 列），V2 引入快照表后再考虑。

## 测试数据前缀约定

| 前缀 | 用途 |
|------|------|
| `TEST_RPT_*` | 通用测试数据（saved_query / dashboard / summary 等）|
| `CONCUR_RPT_*` | 并发场景（V2+ 启用，V1.0 暂未引入）|
| `TEST_RPT_EXP_M5_*` | M5 RptExportTaskMapperIT |
| `TEST_RPT_E2E_*` | M6.0.2 StrategyEndToEndUploadIT |

## 6 架构守护

| 测试 | 守护内容 |
|------|---------|
| `RptBizAuthConsistencyArchTest` | 所有 RestController 公共方法必有 `@BizAuth(bizType = BizType.REPORT)` |
| `RptNoEntityInControllerArchTest` | Controller 签名禁出现 Entity |
| `RptNoEntityInControllerLocalsArchTest` | Controller 局部变量禁出现 Entity |
| `RptModuleStructureArchTest` | `api/` 目录无 `*Api.java`（不暴露契约 + 严格只读）|
| `RptNoUoeInFacadeTestsArchTest` | facade 测试禁 `assertThrows(UnsupportedOperationException.class, ...)` |
| `RptNoV11UOEArchTest` | facade/*.java 不出现 "V1.1 delivered" 占位字面量 |

## 环境依赖

- MySQL 8.0：`onepl`（生产）/ `onepl_test_v103`（测试 IT）
- Redis 6.X：localhost:6379（缓存 + 限流 + 锁）
- MinIO（governance.FileApi.upload 实际依赖，文件 bucket 由 governance 管理）
- 上游模块依赖（10 个 *Api，见上文）

## 开发 Checklist（新增功能时）

1. ✅ 严格 TDD 红-绿-重构闭环（每步独立 commit）
2. ✅ 所有 Controller 方法必标 `@BizAuth(bizType = BizType.REPORT, action = ...)`
3. ✅ 写操作必标 `@AuditLog(action, resourceType)`，高危操作 `reasonRequired=true`
4. ✅ Service 层 public 写方法 `@Transactional(rollbackFor = Exception.class)`
5. ✅ Mapper XML 使用 `#{}` 不用 `${}`（数据范围片段除外）
6. ✅ 跨模块调用走对方 `*Api` 接口；report 自身**不暴露** `*Api`
7. ✅ 中文注释 + UTF-8 编码
8. ✅ 测试数据使用约定前缀（`TEST_RPT_*` / `CONCUR_RPT_*`）
9. ✅ 新增 PT_RESOURCE 通过 Flyway 脚本（V1_0_X__rpt_*.sql 命名）
10. ✅ 新增错误码 RPT-* 唯一不重复 + 中文消息 + RptErrorCodeTest 同步守护

## V1.0 已知技术债（待 V1.1+ 处理）

| 序号 | 标题 | 优先级 | 来源 | 状态 |
|---|---|---|---|---|
| 1 | MetricApi.batchGet*MetricValues 性能优化（M1.2 / M2.2 当前用单条循环，规模大时 N+1 风险）| 中 | 09 文档规划态 + M1.2 实现 | 待 V1.1 上游 batchGet 提供后切换 |
| 2 | KpiApi.batchGetKpiTotal / getKpiRanking 同上规划态 | 中 | 09 文档规划态 + M3.2 实现 | 待 V1.1 上游 batchGet 提供后切换 |
| 3 | 异步导出 V1.0 同步执行（PerfExport V1.2 同模型）→ V1.1 切真异步线程池 | 低 | M5 决策 BR-2 | V1.1 引入 @Async + ThreadPoolExecutor |
| 4 | rpt_snapshot_task V1 仅建表不启用 → V2 启用条件：DAU > 200 或仪表盘 P99 > 1s | 低 | 决策 BR-3 + 05 §7 | V2 启用时间另议 |
| 5 | 仪表盘 V1.0 数据版本依赖 performance.SysControlApi（当前不存在），临时用 MetricApi 兜底；待 V1.1 SysControlApi 暴露后切换 | 低 | 调研发现 | 待 V1.1 上游 |
| 6 | DataScope WORKFLOW_PARTICIPANT 类型暂未在 report 落地（无 business_key 列）| 低 | 09 §X.6 | V2 引入快照表后再考虑 |
| 7 | M3 reviewer #1：RPT-40006 语义偏差（日期超限误用 METRIC_DIM_MISMATCH） | 低 | M3 reviewer | V1.1 新增 RPT-40011 DATE_RANGE_EXCEEDED 替代 |
| 8 | M3 reviewer #2：failedCount = cancelledCount V1.0 简化 | 低 | M3 reviewer | V1.1 拆分两字段 |
| 9 | M3 reviewer #3：3 个 Summary Service 未应用 BizScopeApi（仅 SELF / ORG_SUBTREE 简化）| 中 | M3 reviewer | V1.1 引入 BizScopeApi 注入 |
| 10 | M5 reviewer #2：错误码 42210/42211/42207/42208 当前 0 消费（M6.0 切真 upload 后 42210/42211 仍未消费）| 低 | M5 reviewer | V1.1 真正调用方落地后激活 |
| 11 | M5 reviewer #3：error_msg truncate 阈值 3900 提取 common 常量 | 低 | M5 reviewer | V1.1 收敛 |
| 12 | M5 reviewer #4：ObjectMapper 独立实例 → 共享 Spring Bean | 低 | M5 reviewer | V1.1 替换为 @Qualifier 注入 |
| 13 | SqlProbeServiceImpl#getHistoryDetail 不存在时错误码错配（用 SAVED_QUERY_NOT_FOUND） | 中 | code 审查 I-1 | 待 V1.1 新增 SQL_PROBE_HISTORY_NOT_FOUND |
| 14 | PerfSummaryServiceImpl#L75 subjectName = subjectId 兜底，未通过 OrgApi/CustomerQueryApi 翻译 | 低 | code 审查 M-2 | V1.1 引入 OrgApi/CustQueryApi 翻译 |
| 15 | 仪表盘 Caffeine 单实例（V1.1 切 Redis 共享缓存） | 低 | code 审查观察 | V1.1 切共享缓存 |

## V1.1 规划

- 性能优化：批量 Api 切换（M1.2 + M3.2）
- 异步导出：@Async + 线程池切换（参考 PerfExport V1.3+）
- C.x 固定报表导出端点：03 §I.5 V2 4 个 /export 端点（dashboard / touch / perf / custpool）
- C.bis 报表订阅推送（订阅 sys_control 事件后预热缓存）
- M3 reviewer 系列消化：DATE_RANGE_EXCEEDED 错误码拆分 / failedCount/cancelledCount 拆分 / Summary Service BizScopeApi 注入

## V1.0 plan 撰写期 reviewer 误判记录（2026-04-25）

| 误判项 | reviewer 误读 | 实际真相 | 根因 |
|---|---|---|---|
| F2 | M2/M3 调 MetricApi 仍是 UOE 占位 | V1.1 P2.6 已 Green | MetricApi.java:18 Javadoc 未与实现同步（M6.4.1 已修） |
| F4 | M3 调 KpiApi 仍是 UOE 占位 | V1.1 P2.6 已 Green | KpiApi.java:17 Javadoc 未与实现同步（M6.4.1 已修） |

启示：跨模块依赖的 *Api 接口 Javadoc 必须与 *Impl 状态同步，避免后续模块的 plan 撰写/审查被过时注释误导。

## 相关文档

- Plan: `docs/superpowers/plans/2026-04-25-report-analytics-center-v1.0-plan.md`
- 权威功能规格: `docs/modules/report-analytics-center/`（9 份）
- 对外 API 契约: `docs/modules/report-analytics-center/04-对外API契约.md`（V1.0 维持"不暴露 Api"）
- DDL 权威源: V1_0_0__rpt_init.sql（自有 4 表）
- 共通开发规范: `docs/common-dev-guide.md`
