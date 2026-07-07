# Performance-Engine-Center Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use subagent-driven-development (recommended) or executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 从零创建 `performance-engine-center`，完成数据版本、指标定义、KPI 方案、目标值、分配关系、审批调整、KPI 计算与对外只读 API，为 portal 工作台与 report 报表提供统一指标和 KPI 数据源。

**Architecture:** 以“配置元数据层 + 执行引擎层 + 对外查询层”三层推进。所有审批仍由 `workflow-center` 承接，本模块只发起流程、监听完成事件并执行目标修正/分配调整生效与历史回算；Portal 与 Report 通过 `MetricApi` / `KpiApi` / `AllocApi` / `MetricQueryApi` 消费结果。

**Tech Stack:** Spring Boot 3.2.3, JDK 17, MyBatis 3.0.3, Flowable 7.0.1（间接）, EasyExcel, JUnit 5, Mockito, AssertJ, H2(test)

---

## Reference Files

- 功能规格：`docs/modules/performance-engine-center/01-功能规格.md`
- 后端架构：`docs/modules/performance-engine-center/02-后端架构.md`
- 接口设计：`docs/modules/performance-engine-center/03-接口设计与报文.md`
- 对外 API：`docs/modules/performance-engine-center/04-对外API契约.md`
- 表结构：`docs/modules/performance-engine-center/05-表结构DDL.md`
- 并发策略：`docs/modules/performance-engine-center/06-并发与事务策略.md`
- 初始化数据：`docs/modules/performance-engine-center/08-初始化数据清单.md`
- 依赖摘要：`docs/modules/performance-engine-center/09-依赖契约摘要.md`
- 上游依赖：`customer-marketing-center/`、`workflow-center/`、`system-governance-center/`
- 下游适配点：`portal-content-center/src/main/java/com/bank/branch/platform/portal/adapter/MetricApi.java`

## Preconditions

- `customer-marketing-center` 已提供正式 `CustomerQueryApi` / `ClaimApi`。
- `business-application-center` 已落地主体事实源和业务事件。
- `workflow-center` 已能支撑 `perf_target_adjust_v1`、`perf_alloc_adjust_*` 流程发起与回写。

## Task 0: Module Scaffolding

**Files:**
- Create: `performance-engine-center/pom.xml`
- Modify: `pom.xml`
- Modify: `bootstrap/pom.xml`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/`
- Create: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/support/`
- Create: `performance-engine-center/src/test/resources/application-test.yml`

- [ ] **Step 1:** 按 `02-后端架构.md` 建立模块目录、pom 与测试基座。
- [ ] **Step 2:** 编写模块 smoke test，验证上下文、MyBatis、调度/缓存相关配置可启动。
- [ ] **Step 3:** 运行 `mvn -q test -pl performance-engine-center -Dtest=*Smoke* -am`。
- [ ] **Step 4:** 提交：`build(perf): 创建模块骨架与测试基座`

## Task 1: Metadata and Versioning Layer

**Files:**
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/entity/SysControl.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/entity/PerfMetricDef.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/entity/PerfMetricRef.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/entity/PerfKpiScheme.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/entity/PerfKpiItem.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/entity/PerfTargetPlan.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/entity/PerfTargetValue.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/mapper/*Mapper.java`
- Create: `performance-engine-center/src/main/resources/mapper/performance/*Mapper.xml`
- Test: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/mapper/*MapperTest.java`

- [ ] **Step 1:** 先写 mapper 测试，覆盖版本唯一性、指标依赖层级、方案启停、目标值查询。
- [ ] **Step 2:** 实现以上实体、mapper 和 XML。
- [ ] **Step 3:** 实现 `SysControlService`、`MetricDefService`、`MetricRefService`、`KpiSchemeService`、`TargetPlanService`、`TargetValueService`。
- [ ] **Step 4:** 运行 `mvn -q test -pl performance-engine-center -Dtest=*MapperTest,*ServiceTest`。
- [ ] **Step 5:** 提交：`feat(perf): 完成版本控制与元数据配置层`

## Task 2: Import and External Data Task Layer

**Files:**
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/entity/PerfImportBatch.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/entity/PerfRunTask.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/PerfImportService.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/PerfImportExecutor.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/ExternalDataTaskService.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/controller/PerfImportController.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/controller/DataTaskController.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/api/DataTaskApi.java`
- Test: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/service/PerfImportServiceTest.java`

- [ ] **Step 1:** 写导入与数据任务测试，覆盖文件校验、批次状态流转、运行任务日志、上报幂等。
- [ ] **Step 2:** 实现 `PerfImportService` / `PerfImportExecutor`，先支持 V1 必需的导入类型。
- [ ] **Step 3:** 实现 `ExternalDataTaskService` 与 `DataTaskApi`/`DataTaskController`。
- [ ] **Step 4:** 运行 `mvn -q test -pl performance-engine-center -Dtest=PerfImportServiceTest,*DataTask*`。
- [ ] **Step 5:** 提交：`feat(perf): 完成导入批次与外部任务上报链路`

## Task 3: Target Adjustment and Allocation Adjustment Workflow

**Files:**
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/entity/CustAllocRelation.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/TargetAdjustService.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/AllocRelationService.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/AllocAdjustService.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/controller/TargetAdjustController.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/controller/AllocAdjustController.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/listener/TargetAdjustCompletedListener.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/listener/AllocAdjustCompletedListener.java`
- Test: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/service/TargetAdjustServiceTest.java`
- Test: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/service/AllocAdjustServiceTest.java`

- [ ] **Step 1:** 先写目标修正与分配关系调整服务测试，覆盖发起审批、流程完成回写、错误回滚、幂等监听。
- [ ] **Step 2:** 实现 `TargetAdjustService` 与 `AllocAdjustService`，只通过 `WorkflowApi` 发起流程。
- [ ] **Step 3:** 实现 `AllocRelationService` 和相关 mapper，支持当前归属查询与调整前后对比。
- [ ] **Step 4:** 落地 controller 与流程完成监听器。
- [ ] **Step 5:** 运行 `mvn -q test -pl performance-engine-center -Dtest=TargetAdjustServiceTest,AllocAdjustServiceTest`。
- [ ] **Step 6:** 提交：`feat(perf): 完成目标修正与分配调整审批链路`

## Task 4: KPI Calculation and Historical Recalculation Engine

**Files:**
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/MetricCalcService.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/SqlExecutorService.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/GroovyExecutorService.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/KpiCalcService.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/KpiFormulaService.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/HistoryRecalcService.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/controller/PerfCalcController.java`
- Test: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/service/KpiCalcServiceTest.java`
- Test: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/service/HistoryRecalcServiceTest.java`

- [ ] **Step 1:** 先写 KPI 计算测试，覆盖指标值读取、目标值映射、得分计算、结果持久化和任务日志。
- [ ] **Step 2:** 再写历史回算测试，覆盖目标修正触发、分配调整触发、按有效日期重算。
- [ ] **Step 3:** 实现 `MetricCalcService`、`KpiCalcService`、`KpiFormulaService`。
- [ ] **Step 4:** 实现 `HistoryRecalcService`，统一编排回算窗口与运行任务。
- [ ] **Step 5:** 实现 `PerfCalcController` 与手工触发入口。
- [ ] **Step 6:** 运行 `mvn -q test -pl performance-engine-center -Dtest=KpiCalcServiceTest,HistoryRecalcServiceTest`。
- [ ] **Step 7:** 提交：`feat(perf): 完成 KPI 计算与历史回算引擎`

## Task 5: Query APIs for Portal and Report

**Files:**
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/api/MetricApi.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/api/MetricQueryApi.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/api/KpiApi.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/api/TargetApi.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/api/AllocApi.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/facade/*ApiImpl.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/controller/MetricController.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/controller/TargetController.java`
- Test: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/facade/*ApiImplTest.java`

- [ ] **Step 1:** 先写 API facade 测试，覆盖 portal 所需 `getUserMetricCards` / `getCurrentKpiTotal` 与 report 所需批量指标、历史 KPI 查询。
- [ ] **Step 2:** 实现全部只读 API 与 facade，确保消费端无需读取私有表。
- [ ] **Step 3:** 落地管理侧 controller（指标、方案、目标、sys_control）。
- [ ] **Step 4:** 运行 `mvn -q test -pl performance-engine-center -Dtest=*ApiImplTest,*ControllerTest`。
- [ ] **Step 5:** 提交：`feat(perf): 完成 portal/report 所需查询 API`

## Task 6: Portal Integration and Bootstrap End-to-End

**Files:**
- Modify: `portal-content-center/src/main/java/com/bank/branch/platform/portal/adapter/MetricAdapter.java`
- Modify: `portal-content-center/src/main/java/com/bank/branch/platform/portal/adapter/MetricApi.java`
- Modify: `bootstrap/pom.xml`
- Create/Modify: `bootstrap/src/test/java/com/bank/branch/platform/it/PerformanceEngineCenterIT.java`
- Modify: `bootstrap/src/test/java/com/bank/branch/platform/it/CrossModuleApiTest.java`

- [ ] **Step 1:** 将 portal 的临时占位 `MetricApi` 适配为 performance 正式 API；若需要，保留过渡层但去掉“V1 占位”语义。
- [ ] **Step 2:** 在 bootstrap 中加入 performance 模块依赖和“导入 -> KPI 计算 -> 工作台查询”集成测试。
- [ ] **Step 3:** 在 `CrossModuleApiTest` 中补 `MetricApi`、`KpiApi`、`AllocApi` 等注入与基础查询测试。
- [ ] **Step 4:** 运行 `mvn -q test -pl performance-engine-center,portal-content-center,bootstrap -am`。
- [ ] **Step 5:** 提交：`feat(perf): 完成 portal 集成与 bootstrap 端到端验证`

## Final Verification Checklist

- [ ] `mvn -q compile -pl performance-engine-center -am` 通过
- [ ] `mvn -q test -pl performance-engine-center` 通过
- [ ] `mvn -q test -pl bootstrap -Dtest=CrossModuleApiTest,PerformanceEngineCenterIT -am` 通过
- [ ] portal 工作台已切到 performance 正式 API，不再只依赖降级返回空列表
- [ ] 目标修正与分配调整至少各有 1 组流程回写测试通过
- [ ] 历史回算在单测与集成测试中均有覆盖
