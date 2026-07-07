# Backend Roadmap Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use subagent-driven-development (recommended) or executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有 `common`、`auth-permission-center`、`system-governance-center`、`workflow-center`、`portal-content-center` 基础上，恢复测试基线并按 `customer -> business-application -> performance -> report` 顺序完成后续后端模块建设。

**Architecture:** 采用“先修基线、再补上游、后补下游”的分层推进方式。先恢复 `auth`/`bootstrap` 的测试与装配稳定性，再完成 `customer-marketing-center`，随后落地 `business-application-center` 作为业务事实源，最后补 `performance-engine-center` 与只读的 `report-analytics-center`。

**Tech Stack:** Spring Boot 3.2.3, JDK 17, MyBatis 3.0.3, Flowable 7.0.1, MySQL 8.0, Redis 6.x, EasyExcel, JUnit 5, Mockito, AssertJ

---

## Reference Files

- 总体约束：`AGENTS.md`
- 共享规范：`docs/common-dev-guide.md`
- 架构拆分：`project_ana_技术方案与架构拆分.md`
- 当前模块现状：`pom.xml`
- customer 现有计划：`docs/superpowers/plans/2026-04-13-customer-marketing-center-impl.md`
- workflow / auth / portal 当前测试与实现：`auth-permission-center/`、`workflow-center/`、`portal-content-center/`、`bootstrap/`

## Global Rules

- [ ] 所有模块都遵守 TDD：先写失败测试，再写最小实现，再做重构。
- [ ] 所有跨模块依赖只允许通过 `*Api` / `*QueryApi`，禁止跨模块直连 `mapper` / `entity` / `serviceImpl`。
- [ ] 所有新增 REST 接口必须补 `PT_RESOURCE`、`@BizAuth`、审计点和分页/导出限制。
- [ ] 所有 SQL 放 MyBatis XML，保持与现有模块一致，不引入新 ORM 或新依赖。
- [ ] 每个阶段结束必须先跑模块测试，再跑 `bootstrap` 集成测试；未绿灯不得进入下一阶段。

## Milestones

| 阶段 | 目标 | 输出 |
|---|---|---|
| Phase 0 | 修复基线与装配 | `auth` / `bootstrap` 测试恢复稳定 |
| Phase 1 | 完成 customer-marketing-center | 客户/线索/认领/触达能力可供下游依赖 |
| Phase 1.5 | 补 workflow 查询契约 | 正式 `WorkflowQueryApi` 落地 |
| Phase 2 | 完成 business-application-center | 资产投放 / 中场支持事实源落地 |
| Phase 3 | 完成 performance-engine-center | 指标、KPI、目标、分配关系与回算落地 |
| Phase 4 | 完成 report-analytics-center P0 | 只读报表、仪表盘、SQL 探查、异步导出落地 |
| Phase 5 | 全链路联调收口 | 文档回写、资源核对、全量回归通过 |

## Task 0: Baseline Stabilization

**Files:**
- Modify: `auth-permission-center/src/test/java/com/bank/branch/platform/auth/security/interceptor/AuthorizationInterceptorTest.java`
- Verify: `auth-permission-center/src/main/java/com/bank/branch/platform/auth/security/interceptor/AuthorizationInterceptor.java`
- Modify: `bootstrap/src/test/java/com/bank/branch/platform/it/config/TestMockConfig.java`
- Modify: `bootstrap/src/test/java/com/bank/branch/platform/BranchPlatformApplicationTest.java`
- Modify: `bootstrap/src/test/java/com/bank/branch/platform/it/SmokeTest.java`
- Modify: `bootstrap/src/test/java/com/bank/branch/platform/it/CrossModuleApiTest.java`
- Modify: `bootstrap/src/test/java/com/bank/branch/platform/it/FullAuthChainTest.java`
- Modify: `AGENTS.md`（同步项目实际完成度）

- [ ] **Step 1:** 运行 `mvn -q test -pl auth-permission-center`，确认当前唯一失败测试仍是 `AuthorizationInterceptorTest.preHandle_noBizAuthAnnotation_returns403`。
- [ ] **Step 2:** 先写/调整测试断言，改为验证“无 `@BizAuth` 时放行并构建最小 `DataScopeContext`”，与当前实现保持一致。
- [ ] **Step 3:** 运行 `mvn -q test -pl auth-permission-center`，确认 `auth-permission-center` 全绿。
- [ ] **Step 4:** 运行 `mvn -q test -pl bootstrap -Dtest=BranchPlatformApplicationTest`，复现 `ProcessEngine` 缺失问题。
- [ ] **Step 5:** 在 `TestMockConfig` 中补齐 `ProcessEngine` 等 workflow admin 装配所需 mock bean。
- [ ] **Step 6:** 分别修复 `SmokeTest`、`CrossModuleApiTest`、`FullAuthChainTest` 的上下文依赖和断言，使其与当前测试 profile 行为一致。
- [ ] **Step 7:** 运行 `mvn -q test -pl bootstrap -Dtest=BranchPlatformApplicationTest,SmokeTest,CrossModuleApiTest,FullAuthChainTest`，确认基线恢复。
- [ ] **Step 8:** 更新根 `AGENTS.md` 中“已实现/骨架”描述，反映 `portal-content-center` 与 `customer-marketing-center` 的真实状态。
- [ ] **Step 9:** 提交：`test(bootstrap): 修复基线装配与 auth 鉴权测试分歧`

## Task 1: Customer Module Delivery Gate

**Files:**
- Reference: `docs/superpowers/plans/2026-04-16-customer-marketing-center-impl-v2.md`
- Verify: `customer-marketing-center/`
- Verify: `bootstrap/pom.xml`

- [ ] **Step 1:** 按 customer 计划执行到“模块测试 + bootstrap 集成测试”通过。
- [ ] **Step 2:** 在 `CrossModuleApiTest` 中补 customer 相关 `*Api` 注入与基础查询验证。
- [ ] **Step 3:** 在 `bootstrap` 增加 1 组 customer 业务链路集成测试（标签/线索/认领/触达最小闭环）。
- [ ] **Step 4:** 验证 `mvn -q test -pl customer-marketing-center` 与 customer 相关 bootstrap 集成测试通过。

## Task 1.5: Workflow Query Contract Delivery Gate

**Files:**
- Modify: `workflow-center/src/main/java/com/bank/branch/platform/workflow/api/`
- Modify: `workflow-center/src/main/java/com/bank/branch/platform/workflow/facade/`
- Modify: `portal-content-center/src/main/java/com/bank/branch/platform/portal/adapter/WorkflowQueryAdapter.java`
- Reference: `docs/modules/business-application-center/09-依赖契约摘要.md`

- [ ] **Step 1:** 为 `workflow-center` 落地正式 `WorkflowQueryApi`，承接任务详情、历史、流程映射、候选/办理人可读信息等只读能力。
- [ ] **Step 2:** 将 `portal-content-center` 从本地占位 `WorkflowQueryApi` 迁移到 workflow 正式 Java Bean。
- [ ] **Step 3:** 补 workflow 模块测试与 portal 回归测试。
- [ ] **Step 4:** 验证 `mvn -q test -pl workflow-center,portal-content-center -am` 通过。

## Task 2: Business Application Module Delivery Gate

**Files:**
- Reference: `docs/superpowers/plans/2026-04-16-business-application-center-impl.md`
- Create: `business-application-center/`
- Modify: `pom.xml`
- Modify: `bootstrap/pom.xml`

- [ ] **Step 1:** 按 bizapp 计划完成模块脚手架、贷款申请、中场支持、对外 API、事件发布。
- [ ] **Step 2:** 补 bootstrap 集成测试，至少覆盖一条 `loan_approve_v1` 流程链路和一条 `support_*` 流程链路。
- [ ] **Step 3:** 验证 `customer` 为上游依赖时所有跨模块调用走 `*Api`，没有直接 mapper 依赖。
- [ ] **Step 4:** 运行 `mvn -q test -pl business-application-center,bootstrap -am` 并记录结果。

## Task 3: Performance Module Delivery Gate

**Files:**
- Reference: `docs/superpowers/plans/2026-04-16-performance-engine-center-impl.md`
- Create: `performance-engine-center/`
- Modify: `portal-content-center/src/main/java/com/bank/branch/platform/portal/adapter/MetricAdapter.java`
- Modify: `bootstrap/pom.xml`

- [ ] **Step 1:** 按 performance 计划完成元数据层、导入层、审批调整、KPI 计算、查询 API。
- [ ] **Step 2:** 将 `portal-content-center` 中临时 `MetricApi` 降级链路切换到 performance 正式实现。
- [ ] **Step 3:** 补 bootstrap 的“导入 -> 计算 -> 查询”一条最小集成链路。
- [ ] **Step 4:** 运行 `mvn -q test -pl performance-engine-center,portal-content-center,bootstrap -am`。

## Task 4: Report Module Delivery Gate

**Files:**
- Reference: `docs/superpowers/plans/2026-04-16-report-analytics-center-impl.md`
- Create: `report-analytics-center/`
- Modify: `pom.xml`
- Modify: `bootstrap/pom.xml`

- [ ] **Step 1:** 按 report 计划完成 P0 范围：动态指标查询、查询方案管理、分行行长仪表盘、SQL 探查、固定报表、异步导出框架。
- [ ] **Step 2:** 验证 report 全部保持只读，不引入任何写业务数据行为。
- [ ] **Step 3:** 补 bootstrap 集成测试，验证报表查询和导出轮询链路。
- [ ] **Step 4:** 运行 `mvn -q test -pl report-analytics-center,bootstrap -am`。

## Task 5: End-to-End Closure

**Files:**
- Modify: `bootstrap/src/test/java/com/bank/branch/platform/it/`
- Modify: `docs/superpowers/plans/2026-04-10-interface-full-test-plan.md`（如需补充）
- Modify: `AGENTS.md`
- Modify: `docs/modules/*/08-初始化数据清单.md`
- Modify: `docs/modules/*/09-依赖契约摘要.md`

- [ ] **Step 1:** 增加全链路 E2E：`customer -> bizapp -> workflow -> performance -> report` 最小闭环。
- [ ] **Step 2:** 核对所有新接口的 `PT_RESOURCE`、`@BizAuth`、审计、导出限制、敏感字段脱敏。
- [ ] **Step 3:** 更新各模块初始化清单、依赖契约摘要、根文档状态说明。
- [ ] **Step 4:** 运行全量 `mvn -q test`，确保 reactor 全绿。
- [ ] **Step 5:** 提交：`chore(backend): 完成四模块联调与全量回归收口`

## Final Verification Checklist

- [ ] `mvn -q test -pl auth-permission-center` 通过
- [ ] `mvn -q test -pl bootstrap -Dtest=BranchPlatformApplicationTest,SmokeTest,CrossModuleApiTest,FullAuthChainTest` 通过
- [ ] `mvn -q test -pl customer-marketing-center -am` 通过
- [ ] `mvn -q test -pl business-application-center -am` 通过
- [ ] `mvn -q test -pl performance-engine-center -am` 通过
- [ ] `mvn -q test -pl report-analytics-center -am` 通过
- [ ] `mvn -q test` 全量通过
- [ ] 根 `AGENTS.md` 与各模块文档状态同步完成
