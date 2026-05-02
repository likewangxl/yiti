<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-26 | Updated: 2026-04-26 -->

# report-analytics-center

## Purpose
报表分析中心（支撑域，**只读**模块），提供动态查询 / 仪表盘 / 汇总报表 / SQL 探查 / 异步导出能力。

**基础包名**: `com.bank.branch.platform.report`
**Maven 坐标**: `com.bank.branch.platform:report-analytics-center`
**当前版本**: V1.0（2026-04-25 交付，25 REST + 4 表 + 4 ExportStrategy 异步 + SQL 探查）

## Key Files

| File | Description |
|------|-------------|
| `src/main/java/com/bank/branch/platform/report/controller/MetaController.java` | 查询维度元数据（A.1） |
| `src/main/java/com/bank/branch/platform/report/controller/DynamicQueryController.java` | 动态查询执行（A.2） |
| `src/main/java/com/bank/branch/platform/report/controller/SavedQueryController.java` | 已保存查询 CRUD（B.1-B.5） |
| `src/main/java/com/bank/branch/platform/report/controller/DashboardController.java` | 仪表盘（president/org/emp，C.1-C.3） |
| `src/main/java/com/bank/branch/platform/report/controller/RptSqlProbeController.java` | SQL 探查（D.1-D.4） |
| `src/main/java/com/bank/branch/platform/report/controller/RptExportController.java` | 导出任务管理（E.1-E.3） |
| `src/main/java/com/bank/branch/platform/report/service/export/ExportStrategy.java` | 导出策略接口（4 实现） |
| `src/main/java/com/bank/branch/platform/report/support/SqlSafeValidator.java` | JSqlParser 4.9 AST 校验（≥15 边界用例） |
| `src/main/java/com/bank/branch/platform/report/config/RptReadOnlyDataSourceConfig.java` | 独立只读数据源 |

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `controller/` | 10 个 REST 控制器（24 端点 + 1 占位 = 25 PT_RESOURCE） |
| `service/` | 业务逻辑（DynamicQuery / Dashboard / SavedQuery / 3 Summary + SqlProbe + ExportTask） |
| `service/export/` | 4 个 ExportStrategy 实现 + EasyExcel 行模型 |
| `facade/` | 跨层编排（RptExportFacade） |
| `mapper/` | 4 个 MyBatis Mapper（rpt_saved_query / rpt_snapshot_task / sql_probe_history / rpt_export_task） |
| `entity/` | 4 个数据库实体 |
| `enums/` | 30 条 RPT-* 错误码 + 状态枚举 |
| `support/` | 工具类（SqlSafeValidator / ByteArrayMultipartFile） |
| `config/` | Spring 配置（只读数据源 / Dashboard 预设指标等） |

## For AI Agents

### Working In This Directory
- **红线**: 报表是只读模块，**禁止**被任何业务模块依赖
- 不暴露任何 `*Api` 接口（`api/` 目录仅占位 `package-info.java`）
- 所有 Controller 方法必标 `@BizAuth(bizType = BizType.REPORT, action = ...)`
- 架构守护: `RptModuleStructureArchTest`（api/ 目录禁出现 `*Api.java`）+ 5 个其他守护
- 错误码前缀 `RPT-{HTTP_STATUS}{SEQ}`
- PT_RESOURCE 通过手工 SQL 注册（**Flyway 已彻底废弃**，详见根 CLAUDE.md "Flyway 禁令"红线）

### Testing Requirements
- surefire 103 + failsafe 70 = 173 全绿
- 测试数据使用 `TEST_RPT_*` 前缀
- 错误码测试: `RptErrorCodeTest` 6 case 守护 30 条唯一性 + 中文消息

### Common Patterns
- SQL 探查: 独立只读数据源 + JSqlParser AST 校验 + 双写审计（业务 + 治理）
- 异步导出 V1.0 同步执行（对齐 PerfExport V1.2 模型），V1.1+ 切 @Async + 线程池
- DATA_SCOPE 在仪表盘 / 汇总报表 / SQL 探查 3 处生效
- `R_BACK_TECH` 独占 `R_RPT_SQL_EXEC`

## Dependencies

### Internal（只读消费 4 个上游模块的 10 个 *Api）
- `common-web`, `common-trace`, `common-security`, `common-aop`, `common-db`
- `auth-permission-center`（CurrentUserApi / BizScopeApi / OrgApi）
- `system-governance-center`（DictApi / AuditApi / FileApi）
- `performance-engine-center`（MetricApi / KpiApi）
- `customer-marketing-center`（CustomerQueryApi / TouchTaskQueryApi）

### External
- MyBatis 3.0.3 — ORM
- JSqlParser 4.9 — SQL AST 校验
- EasyExcel — 异步导出
- Redis 6.X — 缓存 + 限流
- MinIO — 导出文件存储（通过 governance FileApi）

## Database Tables (4 张自有表)

| Table | Entity | Description |
|-------|--------|-------------|
| `rpt_saved_query` | RptSavedQuery | 已保存的动态查询 |
| `rpt_snapshot_task` | RptSnapshotTask | 快照任务（V1 仅建表不启用） |
| `sql_probe_history` | SqlProbeHistory | SQL 探查审计历史 |
| `rpt_export_task` | RptExportTask | 异步导出任务 |

## V1.0 已知技术债（15 项）

| # | Title | Priority |
|---|-------|----------|
| 1 | MetricApi.batchGet*MetricValues 性能优化（单条循环 N+1） | 中 |
| 2 | KpiApi.batchGetKpiTotal 同上规划态 | 中 |
| 3 | 异步导出 V1.0 同步 → V1.1 切真异步线程池 | 低 |
| 4 | rpt_snapshot_task V1 不启用 → V2 条件启用 | 低 |
| 5 | 仪表盘数据版本依赖 SysControlApi（当前不存在） | 低 |
| 6 | WORKFLOW_PARTICIPANT 未在 report 落地 | 低 |
| 7-15 | reviewer 发现项（错误码、BizScopeApi 注入等） | 低-中 |

<!-- MANUAL: Any manually added notes below this line are preserved on regeneration -->
