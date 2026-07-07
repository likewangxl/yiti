# Report-Analytics-Center Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use subagent-driven-development (recommended) or executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 从零创建 `report-analytics-center`，在 V1 只读边界内完成 P0 能力：动态指标查询、查询方案管理、分行行长仪表盘、SQL 探查、固定汇总报表与异步导出框架。

**Architecture:** 以只读护栏为最高优先级，先建立“查询框架 + 安全校验 + 导出任务骨架”，再分别实现动态查询、仪表盘、SQL 探查和固定报表。所有业务数据只读自 performance/customer/governance 暴露的 API 或只读视图，不反向写业务状态。

**Tech Stack:** Spring Boot 3.2.3, JDK 17, MyBatis 3.0.3, EasyExcel, Redis 6.x, JUnit 5, Mockito, AssertJ

---

## Reference Files

- 功能规格：`docs/modules/report-analytics-center/01-功能规格.md`
- 后端架构：`docs/modules/report-analytics-center/02-后端架构.md`
- 接口设计：`docs/modules/report-analytics-center/03-接口设计与报文.md`
- 表结构：`docs/modules/report-analytics-center/05-表结构DDL.md`
- 并发与事务：`docs/modules/report-analytics-center/06-并发与事务策略.md`
- 审计要求：`docs/modules/report-analytics-center/07-审计要求.md`
- 初始化数据：`docs/modules/report-analytics-center/08-初始化数据清单.md`
- 依赖摘要：`docs/modules/report-analytics-center/09-依赖契约摘要.md`
- 源 DDL：`docs/schema/ddl-report.sql`
- 上游依赖：`performance-engine-center/`、`customer-marketing-center/`、`system-governance-center/`、`auth-permission-center/`

## Scope Freeze

- 严格限制在 `01-功能规格.md` 第 11 章 P0 范围，不做 P1 的快照任务、自定义仪表盘、订阅推送。
- 模块保持纯只读，不暴露对业务模块有写副作用的接口。
- `api/` 目录只保留 V2 预留，不要求当前为其他模块提供 Java Bean。

## Task 0: Module Scaffolding and Read-Only Guard Rails

**Files:**
- Create: `report-analytics-center/pom.xml`
- Modify: `pom.xml`
- Modify: `bootstrap/pom.xml`
- Create: `report-analytics-center/src/main/java/com/bank/branch/platform/report/config/ReportReadOnlyConfig.java`
- Create: `report-analytics-center/src/main/java/com/bank/branch/platform/report/enums/RptErrorCode.java`
- Create: `report-analytics-center/src/test/java/com/bank/branch/platform/report/support/`
- Create: `report-analytics-center/src/test/resources/application-test.yml`

- [ ] **Step 1:** 创建模块骨架、pom 和测试基座。
- [ ] **Step 2:** 建立只读护栏：默认事务 `readOnly = true`、SQL 探查与导出白名单配置、缓存命名规范。
- [ ] **Step 3:** 写 smoke test 验证模块上下文和只读配置生效。
- [ ] **Step 4:** 运行 `mvn -q test -pl report-analytics-center -Dtest=*Smoke* -am`。
- [ ] **Step 5:** 提交：`build(report): 创建模块骨架与只读护栏`

## Task 1: Saved Query and Dynamic Query Foundation

**Files:**
- Create: `report-analytics-center/src/main/java/com/bank/branch/platform/report/entity/RptSavedQuery.java`
- Create: `report-analytics-center/src/main/java/com/bank/branch/platform/report/mapper/RptSavedQueryMapper.java`
- Create: `report-analytics-center/src/main/resources/mapper/report/RptSavedQueryMapper.xml`
- Create: `report-analytics-center/src/main/java/com/bank/branch/platform/report/service/SavedQueryService.java`
- Create: `report-analytics-center/src/main/java/com/bank/branch/platform/report/service/DynamicQueryService.java`
- Create: `report-analytics-center/src/main/java/com/bank/branch/platform/report/controller/SavedQueryController.java`
- Create: `report-analytics-center/src/main/java/com/bank/branch/platform/report/controller/DynamicQueryController.java`
- Test: `report-analytics-center/src/test/java/com/bank/branch/platform/report/service/SavedQueryServiceTest.java`
- Test: `report-analytics-center/src/test/java/com/bank/branch/platform/report/service/DynamicQueryServiceTest.java`

- [ ] **Step 1:** 先写查询方案和动态查询基础测试，覆盖方案保存、更新、删除、分页、鉴权边界。
- [ ] **Step 2:** 再写动态查询测试，覆盖维度选择、指标组合、DATA_SCOPE 收敛、500 行同步限制。
- [ ] **Step 3:** 实现 `RptSavedQuery` 实体、mapper、`SavedQueryService`。
- [ ] **Step 4:** 实现 `DynamicQueryService` 与 `DynamicQueryController`/`SavedQueryController`。
- [ ] **Step 5:** 运行 `mvn -q test -pl report-analytics-center -Dtest=SavedQueryServiceTest,DynamicQueryServiceTest`。
- [ ] **Step 6:** 提交：`feat(report): 完成查询方案管理与动态查询基础层`

## Task 2: President Dashboard

**Files:**
- Create: `report-analytics-center/src/main/java/com/bank/branch/platform/report/service/DashboardService.java`
- Create: `report-analytics-center/src/main/java/com/bank/branch/platform/report/facade/DashboardFacade.java`
- Create: `report-analytics-center/src/main/java/com/bank/branch/platform/report/controller/DashboardController.java`
- Create: `report-analytics-center/src/main/java/com/bank/branch/platform/report/dto/resp/PresidentDashboardRespDTO.java`
- Test: `report-analytics-center/src/test/java/com/bank/branch/platform/report/service/DashboardServiceTest.java`
- Test: `report-analytics-center/src/test/java/com/bank/branch/platform/report/controller/DashboardControllerTest.java`

- [ ] **Step 1:** 写仪表盘测试，覆盖缓存命中、缓存失效、角色限制、数据版本切换刷新。
- [ ] **Step 2:** 在测试中 mock `performance-engine-center`、`customer-marketing-center` 和 `system-governance-center` 所需依赖 API。
- [ ] **Step 3:** 实现 `DashboardService`、`DashboardFacade`、`DashboardController`。
- [ ] **Step 4:** 运行 `mvn -q test -pl report-analytics-center -Dtest=DashboardServiceTest,DashboardControllerTest`。
- [ ] **Step 5:** 提交：`feat(report): 完成分行行长仪表盘`

## Task 3: SQL Probe with Security and Audit

**Files:**
- Create: `report-analytics-center/src/main/java/com/bank/branch/platform/report/entity/SqlProbeHistory.java`
- Create: `report-analytics-center/src/main/java/com/bank/branch/platform/report/mapper/SqlProbeHistoryMapper.java`
- Create: `report-analytics-center/src/main/resources/mapper/report/SqlProbeHistoryMapper.xml`
- Create: `report-analytics-center/src/main/java/com/bank/branch/platform/report/service/SqlValidationService.java`
- Create: `report-analytics-center/src/main/java/com/bank/branch/platform/report/service/SqlProbeService.java`
- Create: `report-analytics-center/src/main/java/com/bank/branch/platform/report/controller/SqlProbeController.java`
- Test: `report-analytics-center/src/test/java/com/bank/branch/platform/report/service/SqlValidationServiceTest.java`
- Test: `report-analytics-center/src/test/java/com/bank/branch/platform/report/service/SqlProbeServiceTest.java`

- [ ] **Step 1:** 先写 SQL 校验测试，覆盖 schema 白名单、危险关键字、非只读语句拒绝、结果行数限制。
- [ ] **Step 2:** 再写 SQL 探查服务测试，覆盖成功执行、审计落库、失败落日志、角色限制。
- [ ] **Step 3:** 实现 `SqlValidationService`、`SqlProbeService`、`SqlProbeController`。
- [ ] **Step 4:** 运行 `mvn -q test -pl report-analytics-center -Dtest=SqlValidationServiceTest,SqlProbeServiceTest`。
- [ ] **Step 5:** 提交：`feat(report): 完成 SQL 探查与安全审计`

## Task 4: Fixed Summary Reports

**Files:**
- Create: `report-analytics-center/src/main/java/com/bank/branch/platform/report/service/ReportSummaryService.java`
- Create: `report-analytics-center/src/main/java/com/bank/branch/platform/report/facade/ReportSummaryFacade.java`
- Create: `report-analytics-center/src/main/java/com/bank/branch/platform/report/controller/ReportSummaryController.java`
- Create: `report-analytics-center/src/main/java/com/bank/branch/platform/report/dto/resp/*.java`（客户池、触达监控、绩效汇总相关 DTO）
- Test: `report-analytics-center/src/test/java/com/bank/branch/platform/report/service/ReportSummaryServiceTest.java`
- Test: `report-analytics-center/src/test/java/com/bank/branch/platform/report/controller/ReportSummaryControllerTest.java`

- [ ] **Step 1:** 写固定报表测试，覆盖客户池统计、触达任务监控、绩效汇总的分页、排序、过滤和权限。
- [ ] **Step 2:** 实现 `ReportSummaryService` 与 facade/controller。
- [ ] **Step 3:** 运行 `mvn -q test -pl report-analytics-center -Dtest=ReportSummaryServiceTest,ReportSummaryControllerTest`。
- [ ] **Step 4:** 提交：`feat(report): 完成固定汇总报表 P0 能力`

## Task 5: Async Export Framework

**Files:**
- Create: `report-analytics-center/src/main/java/com/bank/branch/platform/report/entity/RptSnapshotTask.java`
- Create: `report-analytics-center/src/main/java/com/bank/branch/platform/report/mapper/RptSnapshotTaskMapper.java`
- Create: `report-analytics-center/src/main/resources/mapper/report/RptSnapshotTaskMapper.xml`
- Create: `report-analytics-center/src/main/java/com/bank/branch/platform/report/service/ReportExportService.java`
- Create: `report-analytics-center/src/main/java/com/bank/branch/platform/report/controller/ReportExportController.java`
- Create: `report-analytics-center/src/main/java/com/bank/branch/platform/report/dto/resp/ExportTaskRespDTO.java`
- Test: `report-analytics-center/src/test/java/com/bank/branch/platform/report/service/ReportExportServiceTest.java`
- Test: `report-analytics-center/src/test/java/com/bank/branch/platform/report/controller/ReportExportControllerTest.java`

- [ ] **Step 1:** 先写导出任务测试，覆盖任务创建、轮询、失败状态、下载地址、超行数保护。
- [ ] **Step 2:** 实现异步导出框架，只返回 taskId，不在请求线程中完成大数据量导出。
- [ ] **Step 3:** 将动态查询导出挂接到异步导出框架；固定报表导出先保留 V2 扩展点。
- [ ] **Step 4:** 运行 `mvn -q test -pl report-analytics-center -Dtest=ReportExportServiceTest,ReportExportControllerTest`。
- [ ] **Step 5:** 提交：`feat(report): 完成异步导出任务框架`

## Task 6: Bootstrap Integration and Final Read-Only Verification

**Files:**
- Modify: `bootstrap/pom.xml`
- Create/Modify: `bootstrap/src/test/java/com/bank/branch/platform/it/ReportAnalyticsCenterIT.java`
- Modify: `bootstrap/src/test/java/com/bank/branch/platform/it/CrossModuleApiTest.java`
- Modify: `docs/modules/report-analytics-center/08-初始化数据清单.md`

- [ ] **Step 1:** 在 bootstrap 中接入 report 模块依赖与最小集成测试。
- [ ] **Step 2:** 扩展 `CrossModuleApiTest`，验证 report controller 相关上下文可以加载，且不向外暴露不该有的写 API Bean。
- [ ] **Step 3:** 编写集成测试覆盖：动态查询、仪表盘、SQL 探查、导出任务轮询。
- [ ] **Step 4:** 验证所有 report Service 都是只读，不包含写业务表操作。
- [ ] **Step 5:** 运行 `mvn -q test -pl report-analytics-center,bootstrap -am`。
- [ ] **Step 6:** 提交：`feat(report): 完成 bootstrap 集成与只读边界验收`

## Final Verification Checklist

- [ ] `mvn -q compile -pl report-analytics-center -am` 通过
- [ ] `mvn -q test -pl report-analytics-center` 通过
- [ ] `mvn -q test -pl bootstrap -Dtest=CrossModuleApiTest,ReportAnalyticsCenterIT -am` 通过
- [ ] 模块只实现 P0 范围，没有把 P1/V2 能力误带入
- [ ] SQL 探查具备白名单、危险关键字拦截和审计落库
- [ ] 动态查询与导出遵守同步/异步阈值约束
