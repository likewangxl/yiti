# Business-Application-Center Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 从零创建 `business-application-center`，完成资产投放申请、中场支持申请、承接办理、导出与对外 API，使其成为 `performance-engine-center` 与 `report-analytics-center` 的业务事实源。

**Architecture:** 沿用项目的模块化单体分层：`Entity -> Mapper XML -> Service -> Facade(ApiImpl) -> Controller`。工作流发起与取消只通过 `WorkflowApi`；任务查询与流程详情使用正式 `WorkflowQueryApi`；客户与产品、部门、员工等信息统一从 `customer-marketing-center`、`portal-content-center`、`auth-permission-center`、`system-governance-center` 的 API 获取。

**Tech Stack:** Spring Boot 3.2.3, JDK 17, MyBatis 3.0.3, Flowable 7.0.1（通过 workflow-center 间接使用）, EasyExcel, JUnit 5, Mockito, AssertJ

---

## Reference Files

- 功能规格：`docs/modules/business-application-center/01-功能规格.md`
- 后端架构：`docs/modules/business-application-center/02-后端架构.md`
- 接口设计：`docs/modules/business-application-center/03-接口设计与报文.md`
- 对外 API：`docs/modules/business-application-center/04-对外API契约.md`
- 表结构：`docs/modules/business-application-center/05-表结构DDL.md`
- 并发策略：`docs/modules/business-application-center/06-并发与事务策略.md`
- 审计要求：`docs/modules/business-application-center/07-审计要求.md`
- 初始化数据：`docs/modules/business-application-center/08-初始化数据清单.md`
- 依赖摘要：`docs/modules/business-application-center/09-依赖契约摘要.md`
- 源 DDL：`docs/schema/ddl-bizapp.sql`
- 上游依赖：`customer-marketing-center/`、`portal-content-center/`、`workflow-center/`

## Preconditions

- `customer-marketing-center` 已完成并通过 bootstrap 集成测试。
- `workflow-center` 已暴露正式 `WorkflowQueryApi`，不再依赖旧的规划态名称。
- `portal-content-center` 的 `ProductApi`、`AddressBookApi` 可稳定注入。

## Task 0: Module Scaffolding

**Files:**
- Create: `business-application-center/pom.xml`
- Modify: `pom.xml`
- Modify: `bootstrap/pom.xml`
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/`
- Create: `business-application-center/src/test/java/com/bank/branch/platform/bizapp/support/`
- Create: `business-application-center/src/test/resources/application-test.yml`

- [ ] **Step 1:** 按 `docs/modules/business-application-center/02-后端架构.md` 建立模块目录与基础包结构。
- [ ] **Step 2:** 在根 `pom.xml` 与 `bootstrap/pom.xml` 中注册模块依赖。
- [ ] **Step 3:** 写模块 smoke test，验证 Spring 上下文可以启动。
- [ ] **Step 4:** 运行 `mvn -q test -pl business-application-center -Dtest=*Smoke* -am`。
- [ ] **Step 5:** 提交：`build(bizapp): 创建模块骨架与测试基座`

## Task 1: Entities, Mappers and Shared State Machine

**Files:**
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/entity/LoanApply.java`
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/entity/SupportRequest.java`
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/mapper/LoanApplyMapper.java`
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/mapper/SupportRequestMapper.java`
- Create: `business-application-center/src/main/resources/mapper/bizapp/LoanApplyMapper.xml`
- Create: `business-application-center/src/main/resources/mapper/bizapp/SupportRequestMapper.xml`
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/service/BizStateMachine.java`
- Test: `business-application-center/src/test/java/com/bank/branch/platform/bizapp/mapper/*MapperTest.java`

- [ ] **Step 1:** 依据 `05-表结构DDL.md` 和 `ddl-bizapp.sql` 创建两张主表实体与关键枚举。
- [ ] **Step 2:** 先写 mapper 测试，覆盖草稿查询、分页、状态更新、按流程实例/业务键检索。
- [ ] **Step 3:** 实现 mapper 和 XML。
- [ ] **Step 4:** 实现 `BizStateMachine`，集中校验 `DRAFT/IN_APPROVAL/APPROVED/REJECTED/CANCELLED/COMPLETED` 等迁移规则。
- [ ] **Step 5:** 运行 `mvn -q test -pl business-application-center -Dtest=*MapperTest`。
- [ ] **Step 6:** 提交：`feat(bizapp): 完成主表实体、Mapper 与状态机基础层`

## Task 2: Loan Application Core Flow

**Files:**
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/service/LoanApplyService.java`
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/service/LoanService.java`
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/service/LoanFormValidator.java`
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/controller/LoanController.java`
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/controller/LoanNodeFormController.java`
- Test: `business-application-center/src/test/java/com/bank/branch/platform/bizapp/service/LoanServiceTest.java`
- Test: `business-application-center/src/test/java/com/bank/branch/platform/bizapp/controller/LoanControllerTest.java`

- [ ] **Step 1:** 写贷款域服务测试，覆盖草稿新建、编辑、删除、详情、列表、提交审批、取消流程。
- [ ] **Step 2:** 在测试中显式模拟 `WorkflowApi.startProcess` / `cancelProcess` 成功与失败分支。
- [ ] **Step 3:** 实现 `LoanApplyService` 与 `LoanService`，保持流程发起顺序为“权限校验 -> 表单校验 -> 主表保存 -> WorkflowApi.startProcess”。
- [ ] **Step 4:** 实现 `LoanFormValidator` 和节点表单查询能力。
- [ ] **Step 5:** 实现 `LoanController` / `LoanNodeFormController`。
- [ ] **Step 6:** 运行 `mvn -q test -pl business-application-center -Dtest=LoanServiceTest,LoanControllerTest`。
- [ ] **Step 7:** 提交：`feat(bizapp): 完成资产投放申请主链路`

## Task 3: Support Request Initiation Side

**Files:**
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/service/SupportService.java`
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/service/SupportScenarioRouter.java`
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/service/SupportProductSplitService.java`
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/controller/SupportController.java`
- Test: `business-application-center/src/test/java/com/bank/branch/platform/bizapp/service/SupportServiceTest.java`
- Test: `business-application-center/src/test/java/com/bank/branch/platform/bizapp/controller/SupportControllerTest.java`

- [ ] **Step 1:** 写支持申请测试，覆盖批量新建、场景 A/B 路由、多产品拆单、草稿更新、提交、取消、导出前计数。
- [ ] **Step 2:** 实现 `SupportScenarioRouter` 与 `SupportProductSplitService`，确保场景 A 走 `support_simple_v1`，场景 B 走 `support_complex_v1`。
- [ ] **Step 3:** 实现 `SupportService`，统一处理客户校验、产品校验、组织/候选人校验与流程发起。
- [ ] **Step 4:** 实现 `SupportController`。
- [ ] **Step 5:** 运行 `mvn -q test -pl business-application-center -Dtest=SupportServiceTest,SupportControllerTest`。
- [ ] **Step 6:** 提交：`feat(bizapp): 完成中场支持发起侧链路`

## Task 4: Support Department Acceptance Side

**Files:**
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/service/SupportDispatchService.java`
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/service/SupportDeptService.java`
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/controller/SupportDeptController.java`
- Test: `business-application-center/src/test/java/com/bank/branch/platform/bizapp/service/SupportDeptServiceTest.java`
- Test: `business-application-center/src/test/java/com/bank/branch/platform/bizapp/controller/SupportDeptControllerTest.java`

- [ ] **Step 1:** 写承接侧测试，覆盖待办列表、详情、派单、转交、驳回、办理完成和导出。
- [ ] **Step 2:** 在测试中使用 `WorkflowQueryApi` 获取流程任务详情和可办理信息，避免直接读 workflow 表。
- [ ] **Step 3:** 实现 `SupportDispatchService` 与 `SupportDeptService`。
- [ ] **Step 4:** 实现 `SupportDeptController`，校验高危操作的独立 URL、独立权限与独立审计。
- [ ] **Step 5:** 运行 `mvn -q test -pl business-application-center -Dtest=SupportDeptServiceTest,SupportDeptControllerTest`。
- [ ] **Step 6:** 提交：`feat(bizapp): 完成中场支持承接侧办理链路`

## Task 5: External APIs and Domain Events

**Files:**
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/api/LoanApi.java`
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/api/LoanQueryApi.java`
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/api/SupportApi.java`
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/api/SupportQueryApi.java`
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/api/BizApplyQueryApi.java`
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/facade/*ApiImpl.java`
- Create: `business-application-center/src/main/java/com/bank/branch/platform/bizapp/listener/*.java`
- Test: `business-application-center/src/test/java/com/bank/branch/platform/bizapp/facade/*ApiImplTest.java`

- [ ] **Step 1:** 先写 facade/API 测试，覆盖按 id 查询、批量查询、分页聚合、通用统计和查询边界。
- [ ] **Step 2:** 实现全部对外 API，确保调用方不需要直接依赖 controller。
- [ ] **Step 3:** 实现 workflow 事件监听器：处理贷款/支持申请流程完成后的状态迁移和事件发布。
- [ ] **Step 4:** 发布 `bizapp.loan.*` 与 `bizapp.support.*` 领域事件，供 customer/performance/report 订阅。
- [ ] **Step 5:** 运行 `mvn -q test -pl business-application-center -Dtest=*ApiImplTest`。
- [ ] **Step 6:** 提交：`feat(bizapp): 完成对外 API 与流程完成事件`

## Task 6: Resource Seed, Audit and Bootstrap Integration

**Files:**
- Modify: `docs/modules/business-application-center/08-初始化数据清单.md`
- Modify: `docs/modules/business-application-center/07-审计要求.md`
- Modify: `bootstrap/pom.xml`
- Create/Modify: `bootstrap/src/test/java/com/bank/branch/platform/it/BusinessApplicationCenterIT.java`
- Modify: `bootstrap/src/test/java/com/bank/branch/platform/it/CrossModuleApiTest.java`

- [ ] **Step 1:** 按 `08-初始化数据清单.md` 补齐本模块所有 `PT_RESOURCE`、流程节点表单配置、字典和测试种子数据。
- [ ] **Step 2:** 对照 `07-审计要求.md` 校验所有写接口都有 `@AuditLog` 或等效审计封装。
- [ ] **Step 3:** 在 bootstrap 中添加集成测试：贷款提交审批链路 1 条、中场支持场景 A/B 各 1 条。
- [ ] **Step 4:** 扩展 `CrossModuleApiTest`，验证 bizapp 的全部外部 API Bean 注入。
- [ ] **Step 5:** 运行 `mvn -q test -pl business-application-center,bootstrap -am`。
- [ ] **Step 6:** 提交：`feat(bizapp): 完成资源初始化、审计与 bootstrap 集成`

## Final Verification Checklist

- [ ] `mvn -q compile -pl business-application-center -am` 通过
- [ ] `mvn -q test -pl business-application-center` 通过
- [ ] `mvn -q test -pl bootstrap -Dtest=CrossModuleApiTest,BusinessApplicationCenterIT -am` 通过
- [ ] 所有跨模块交互都只走 `*Api` / `*QueryApi`
- [ ] 贷款申请与中场支持主链路至少各有 1 组 bootstrap 集成用例通过
- [ ] `docs/modules/business-application-center/08-初始化数据清单.md` 中资源与流程配置与代码一致
