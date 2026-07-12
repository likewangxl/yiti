<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-04-26 | Updated: 2026-07-12 -->

# report-analytics-center

## Purpose
报表分析中心（支撑域，**只读**模块）。最初以动态查询/仪表盘/汇总报表/SQL 探查/异步导出（V1.0）交付，此后持续扩展为覆盖面更广的只读报表聚合层，新增：自由报表（Excel 导入自定义列展示）、数据湖查询（对公/个人存款贷款账户等）、业绩调整与定价审批历史查询、公告查询、报表数据范围（DataScope）统一入口等。所有能力仍遵循"只读、不暴露 `*Api`、不被业务模块依赖"的红线。

**基础包名**: `com.bank.branch.platform.report`
**Maven 坐标**: `com.bank.branch.platform:report-analytics-center`

## Key Files

| File | Description |
|------|-------------|
| `src/main/java/com/bank/branch/platform/report/controller/MetaController.java` | 查询维度元数据 |
| `src/main/java/com/bank/branch/platform/report/controller/DynamicQueryController.java` | 动态查询执行 |
| `src/main/java/com/bank/branch/platform/report/controller/SavedQueryController.java` | 已保存查询 CRUD |
| `src/main/java/com/bank/branch/platform/report/controller/DashboardController.java` | 仪表盘（president/org/emp） |
| `src/main/java/com/bank/branch/platform/report/controller/TouchSummaryController.java` / `PerfSummaryController.java` / `CustPoolSummaryController.java` | 触达/绩效/客户池 3 类汇总报表（含各自 `/export`） |
| `src/main/java/com/bank/branch/platform/report/controller/RptSqlProbeController.java` | SQL 探查（执行/历史/白名单/异步导出下载） |
| `src/main/java/com/bank/branch/platform/report/controller/RptExportController.java` | 导出任务管理（状态/取消/下载） |
| `src/main/java/com/bank/branch/platform/report/controller/FreeReportController.java` | 自由报表（Excel 导入动态列 + 批次管理 + 下载） |
| `src/main/java/com/bank/branch/platform/report/controller/DataImportQueryController.java` | 数据导入批次查询（数据湖导入结果核对） |
| `src/main/java/com/bank/branch/platform/report/controller/NoticeQueryController.java` | 公告查询（含附件下载） |
| `src/main/java/com/bank/branch/platform/report/controller/AmasPriceApprovalController.java` / `AmasApprovalHistoryController.java` / `AllocAdjustHistoryController.java` | 定价审批 / 业绩调整审批（AMAS）/ 分配调整申请历史查询 |
| `src/main/java/com/bank/branch/platform/report/controller/AllocPreviewController.java` | 业绩分配调整预览（简单直转发端点，非标准 `/api/reports` 前缀，见下文） |
| `src/main/java/com/bank/branch/platform/report/controller/ReportScopeController.java` | 报表数据范围（DataScope）选择器/picker 统一入口 |
| `src/main/java/com/bank/branch/platform/report/service/export/ExportStrategy.java` | 异步导出策略接口（4 个实现：DYNAMIC_QUERY/TOUCH_SUMMARY/PERF_SUMMARY/CUSTPOOL_SUMMARY） |
| `src/main/java/com/bank/branch/platform/report/support/SqlSafeValidator.java` | JSqlParser AST 校验（SQL 探查安全护栏） |
| `src/main/java/com/bank/branch/platform/report/support/SqlCryptoUtil.java` | SQL 探查请求体 AES-128-ECB 加解密（前后端约定密钥） |
| `src/main/java/com/bank/branch/platform/report/config/RptReadOnlyDataSourceConfig.java` | SQL 探查独立只读数据源 |
| `src/main/java/com/bank/branch/platform/report/config/ReportCacheConfig.java` | Caffeine `@Primary` CacheManager（TTL 5 分钟/maxSize 500，整个平台共享） |

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `controller/` | 18 个 REST 控制器（原 V1.0 10 个 + 新增自由报表/数据导入/公告/AMAS 审批/分配预览/DataScope picker 等 8 个） |
| `controller/dto/` | Controller 专属响应模型（如 `AllocPreviewRespDTO`） |
| `dto/req/`、`dto/resp/` | 请求/响应 DTO（req 12 个、resp 30+ 个，按新旧功能划分） |
| `service/` + `service/impl/` | 业务逻辑接口与实现分离（17 个 Service + 对应 Impl），含 `DatalakeQueryService` 等数据湖查询能力 |
| `service/export/` | 异步导出（`RptExportService` 同步执行状态机 + 4 个 `ExportStrategy` 实现 + EasyExcel 行模型） |
| `facade/` | 跨层编排（`RptExportFacade`，entity 不流入 Controller） |
| `mapper/` | 21 个 MyBatis-Plus Mapper（自有表 + AMAS_*/DATALAKE_*/PERF_ALLOC_ADJUST_*/SYS_NOTICE 等外部只读表） |
| `entity/` | 21 个贫血实体，覆盖自有表 + 外部只读表 |
| `enums/` | RptErrorCode（RPT-* 错误码 + 状态枚举） |
| `support/` | 工具类（SqlSafeValidator / SqlSafeResult / SqlCryptoUtil / ByteArrayMultipartFile / TrendFormatter） |
| `config/` | Spring 配置（只读数据源 / Caffeine 缓存 / 异步线程池 / Dashboard 预设指标 / SQL 探查安全配置） |
| `listener/`、`facade/`（除 RptExportFacade） | 仅占位 `package-info.java`，未启用 |

## For AI Agents

### Working In This Directory
- **红线**: 报表是只读模块，**禁止**被任何业务模块依赖；不暴露任何 `*Api` 接口（`api/` 目录仅占位 `package-info.java`），架构守护 `RptModuleStructureArchTest` 强制这一点
- 绝大多数 Controller 公共方法标 `@BizAuth(bizType = BizType.REPORT, action = ...)`；`RptBizAuthConsistencyArchTest` 只守护"**若标注了** `@BizAuth`，`bizType` 必须是 `REPORT`"这一单档策略，**不强制每个方法都必须标注**——新增 Controller 请对齐惯例主动补 `@BizAuth`，不要依赖架构测试兜底
- 已知例外：`AllocPreviewController`（`GET /api/report/alloc-preview`）当前**完全没有** `@BizAuth`，也没有类级 `@RequestMapping`（路径直接写在方法注解里，且是单数 `/api/report/` 而非其余端点的 `/api/reports/`），属历史遗留，新增功能不要照抄这个反例
- 错误码前缀 `RPT-{HTTP_STATUS}{SEQ}`；新增错误码需同步 `RptErrorCodeTest`
- PT_RESOURCE 通过手工 SQL 注册（Flyway 已彻底废弃，详见根 CLAUDE.md "Flyway 禁令"红线）
- **外部只读表边界**：`AMAS_*`（业绩调整/定价审批外部系统）、`DATALAKE_XAN_*`（数据湖对公/个人存贷款账户）、`PERF_ALLOC_ADJUST_*`（分配调整申请/明细）、`sys_notice`（公告）均为 report 直接建 Mapper 只读消费的**外部系统表**，不是其他平台业务模块自有表，因此不违反"跨模块必须走 `*Api`"的规则；但也意味着这些表的 schema 变更不受本项目控制，改表结构前先确认外部系统契约

### Testing Requirements
- `src/test/java` 下 43 个测试类文件，覆盖 Service/Controller/Mapper/6 个架构守护测试（`RptBizAuthConsistencyArchTest` / `RptNoEntityInControllerArchTest` / `RptNoEntityInControllerLocalsArchTest` / `RptModuleStructureArchTest` / `RptNoUoeInFacadeTestsArchTest` / `RptNoV11UOEArchTest`）
- 测试数据使用 `TEST_RPT_*` 前缀（导出相关另有 `TEST_RPT_EXP_*` / `TEST_RPT_E2E_*` 子前缀）
- 新增功能务必先跑通对应模块的红-绿-重构闭环（TDD 绝对红线，见根 CLAUDE.md）

### Common Patterns
- SQL 探查：独立只读数据源（`rptReadOnlyDataSource`）+ JSqlParser AST 校验 + 双写审计（业务 `sql_probe_history` + 治理 `governance.audit_log`）+ 请求体 AES 加密传输（`SqlCryptoUtil`）+ 独立线程池异步下载（`sqlProbeExportExecutor`，`RptAsyncConfig`）；执行端点当前**仅 `R_BACK_TECH` 角色**可访问（高危操作 + reason 必填）
- 通用异步导出（`RptExportService.createTask`）仍是**创建即同步执行**模型：INSERT PENDING → UPDATE RUNNING → `strategy.execute` 内联生成 EasyExcel 字节流并经 `governance.FileApi.upload` 落 MinIO → UPDATE SUCCESS；`task.fileKey` 存的是 `FileObjectDTO.id` 而非 MinIO object key，下载走 `fileApi.getDownloadUrl` 拿预签名 URL
- 全平台共享的 Caffeine `@Primary` CacheManager（`ReportCacheConfig`）：TTL 5 分钟 + maxSize 500，`@Cacheable("xxx")` 用到的任意 cache name 动态创建；perf-engine 等其他模块的 `@Cacheable` 也路由到这个 Bean，不是 report 私有
- DATA_SCOPE 在仪表盘 / 3 类汇总报表 / SQL 探查（仅角色限制，不做行级过滤）等处生效；`ReportScopeController` 提供统一的范围选择器（picker）端点供前端复用
- 自由报表（FreeReport）：`importExcel` 在 `@Transactional(rollbackFor = Exception.class)` 内先删同操作人的同名旧批次记录（`RPT_FREE_REPORT_BATCH`/`ROW`），**故意不清理旧 MinIO 文件**——MD5 去重下新旧批次可能共享同一 `FILE_OBJECT`，删旧文件会误删新批次仍在用的对象，且对已不存在记录调 `deleteFile` 会抛异常把事务标成 rollback-only；旧对象留存 MinIO 视为可接受的孤儿

## Dependencies

### Internal（只读消费上游模块的 *Api，不反向暴露）
- `common-web`, `common-trace`, `common-security`, `common-aop`, `common-db`
- `auth-permission-center`（CurrentUserApi / BizScopeApi / OrgApi）
- `system-governance-center`（DictApi / AuditApi / FileApi）
- `performance-engine-center`（MetricApi / KpiApi）
- `customer-marketing-center`（CustomerQueryApi / TouchTaskQueryApi）

### External
- MyBatis-Plus — ORM（新增功能一律走 BaseMapper + LambdaQueryWrapper，自定义复杂 SQL 才落 XML）
- JSqlParser — SQL AST 校验
- EasyExcel — 报表导入/导出
- Caffeine — 全平台共享缓存（非 Redis）
- MinIO — 导出/自由报表文件存储（通过 governance FileApi）

## Database Tables

### 自有表
| Table | Entity | Description |
|-------|--------|-------------|
| `RPT_SAVED_QUERY` | RptSavedQuery | 已保存的动态查询 |
| `RPT_SNAPSHOT_TASK` | RptSnapshotTask | 快照任务（仅建表未启用） |
| `SQL_PROBE_HISTORY` | SqlProbeHistory | SQL 探查审计历史 |
| `SQL_PROBE_EXPORT_TASK` | SqlProbeExportTask | SQL 探查异步导出任务 |
| `RPT_EXPORT_TASK` | RptExportTask | 通用异步导出任务 |
| `RPT_FREE_REPORT_BATCH` / `RPT_FREE_REPORT_ROW` | RptFreeReportBatch / RptFreeReportRow | 自由报表导入批次与动态列数据行 |

### 外部只读表（非本项目其他模块所有，直接建 Mapper 消费）
| Table | Entity | Description |
|-------|--------|-------------|
| `AMAS_PRICE_APPROVAL` | AmasPriceApproval | 定价审批 |
| `AMAS_PERF_ADJUST_APPROVAL` | AmasPerfAdjustApproval | 业绩调整审批 |
| `AMAS_PERFORMANCE_ALLOCATION` | AmasPerformanceAllocation | 业绩分配 |
| `AMAS_APPR_RECORD` | AmasApprRecord | 审批流程记录 |
| `amas_dt_import_details` / `amas_dt_import_sup` | AmasDtImportDetail / AmasDtImportSup | 数据导入明细/汇总 |
| `PERF_ALLOC_ADJUST_APPLY` / `PERF_ALLOC_ADJUST_ITEM` | PerfAllocAdjustApply / PerfAllocAdjustItem | 分配调整申请与明细 |
| `DATALAKE_XAN_PDL_C03_B_CORP_DEPOSIT_ACCT` / `..._CORP_LOAN_ACCT` | DlCorpDepositAcct / DlCorpLoanAcct | 对公存款/贷款账户（数据湖） |
| `DATALAKE_XAN_C03_B_INDIV_DEPOSIT_ACCT` | DlIndivDepositAcct | 个人存款账户（数据湖） |
| `DATALAKE_XAN_CRM_C06_CORP_ASSET_LIAB_ALLOT` / `..._INDIV_ALLOT_RELA` | DlCorpAssetLiabAllot / DlIndivAllotRela | 对公/个人资产负债分配关系 |
| `sys_notice` | SysNotice | 系统公告 |

## 已知设计取舍与限制

- `rpt_snapshot_task` 仅建表未启用，启用条件（DAU/仪表盘 P99）另议
- 通用异步导出仍是同步执行模型（见"Common Patterns"），SQL 探查导出已切独立线程池异步
- `DataScopeType.WORKFLOW_PARTICIPANT` 未在本模块落地（无 business_key 列）
- SQL 探查执行端点角色限制硬编码在 Controller/Service 注释与鉴权中（`R_BACK_TECH` 独占），调整需求请先确认是否仍符合合规要求
- 外部只读表（AMAS_*/DATALAKE_*/PERF_ALLOC_ADJUST_*/sys_notice）schema 由外部系统/数据湖侧掌控，本模块只做贫血映射，字段命名不统一（部分保留原始大小写 `@TableField` 显式映射）属预期现象

<!-- MANUAL: Any manually added notes below this line are preserved on regeneration -->
