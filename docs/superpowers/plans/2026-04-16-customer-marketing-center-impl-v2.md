# Customer-Marketing-Center Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use subagent-driven-development (recommended) or executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在已有枚举与 Maven 模块基础上，完成 `customer-marketing-center` 的 7 个能力域、外部 API、事件发布与 bootstrap 集成验证，使其成为后续 `business-application-center`、`performance-engine-center`、`report-analytics-center` 的稳定上游。

**Architecture:** 严格沿用 `portal-content-center` 的分层模式：`Entity -> Mapper XML -> Service -> Facade(ApiImpl) -> Controller`。写操作统一走 Service 编排并在事务提交后发布领域事件；跨模块交互只通过 auth / workflow / portal / governance 的 `*Api`。

**Tech Stack:** Spring Boot 3.2.3, JDK 17, MyBatis 3.0.3, MySQL 8.0, H2(test), EasyExcel, JUnit 5, Mockito, AssertJ

---

## Reference Files

- 现有草案：`docs/superpowers/plans/2026-04-13-customer-marketing-center-impl.md`
- 功能规格：`docs/modules/customer-marketing-center/01-功能规格.md`
- 后端架构：`docs/modules/customer-marketing-center/02-后端架构.md`
- 接口设计：`docs/modules/customer-marketing-center/03-接口设计与报文.md`
- 对外契约：`docs/modules/customer-marketing-center/04-对外API契约.md`
- DDL：`docs/modules/customer-marketing-center/05-表结构DDL.md`
- 并发策略：`docs/modules/customer-marketing-center/06-并发与事务策略.md`
- 初始化数据：`docs/modules/customer-marketing-center/08-初始化数据清单.md`
- 依赖摘要：`docs/modules/customer-marketing-center/09-依赖契约摘要.md`
- 参考实现：`portal-content-center/`、`auth-permission-center/`

## Scope Freeze

- 本计划覆盖标签、线索、客户主档、客户池、认领、触达任务、触达报告 7 个能力域。
- 保持 `portal-content-center` 为弱依赖，消费其 `ProductApi` / `AddressBookApi`，但不要求 portal 再新增接口。
- 继续使用已有枚举与错误码，不重复创建基础模型。
- 不新增依赖，不引入 MapStruct，不改现有公共组件接口。

## Task 0: Reconcile Existing Partial Code

**Files:**
- Verify: `customer-marketing-center/pom.xml`
- Verify: `customer-marketing-center/src/main/java/com/bank/branch/platform/customer/enums/*.java`
- Create/Modify: `customer-marketing-center/AGENTS.md`（如缺失）
- Modify: `docs/superpowers/plans/2026-04-13-customer-marketing-center-impl.md`（仅作为历史参考，不覆盖）

- [ ] **Step 1:** 运行 `Get-ChildItem customer-marketing-center -Recurse`，确认当前仅有枚举、错误码和编译产物，无正式业务层代码。
- [ ] **Step 2:** 清理 `target/` 对计划阅读的干扰，不删除源码。
- [ ] **Step 3:** 记录现存可复用文件：`CustomerErrorCode`、状态枚举、`pom.xml`。
- [ ] **Step 4:** 补 `customer-marketing-center/AGENTS.md`（若缺失）或确认根规范已足够覆盖本模块。
- [ ] **Step 5:** 运行 `mvn -q compile -pl customer-marketing-center -am`，确认当前最小模块可编译。

## Task 1: Module Test Infrastructure and Skeleton

**Files:**
- Create: `customer-marketing-center/src/main/java/com/bank/branch/platform/customer/api/`
- Create: `customer-marketing-center/src/main/java/com/bank/branch/platform/customer/controller/`
- Create: `customer-marketing-center/src/main/java/com/bank/branch/platform/customer/facade/`
- Create: `customer-marketing-center/src/main/java/com/bank/branch/platform/customer/service/`
- Create: `customer-marketing-center/src/main/java/com/bank/branch/platform/customer/mapper/`
- Create: `customer-marketing-center/src/main/java/com/bank/branch/platform/customer/entity/`
- Create: `customer-marketing-center/src/main/resources/mapper/customer/`
- Create: `customer-marketing-center/src/test/java/com/bank/branch/platform/customer/support/`
- Create: `customer-marketing-center/src/test/resources/application-test.yml`

- [ ] **Step 1:** 参考 `portal-content-center` 建立标准目录结构与 `package-info.java`。
- [ ] **Step 2:** 创建测试基座：模块内 `TestConfiguration`、Mock 当前登录人上下文、H2 初始化配置。
- [ ] **Step 3:** 写第一个 smoke test，断言模块 `ApplicationContext` 可以启动。
- [ ] **Step 4:** 运行 `mvn -q test -pl customer-marketing-center -Dtest=*Smoke*`，确认基础设施可用。
- [ ] **Step 5:** 提交：`test(customer): 建立模块测试基座与目录骨架`

## Task 2: Entity + Mapper Layer

**Files:**
- Create: `customer-marketing-center/src/main/java/com/bank/branch/platform/customer/entity/CustTag.java`
- Create: `customer-marketing-center/src/main/java/com/bank/branch/platform/customer/entity/CustTagRel.java`
- Create: `customer-marketing-center/src/main/java/com/bank/branch/platform/customer/entity/CustLead.java`
- Create: `customer-marketing-center/src/main/java/com/bank/branch/platform/customer/entity/CustMaster.java`
- Create: `customer-marketing-center/src/main/java/com/bank/branch/platform/customer/entity/CustClaim.java`
- Create: `customer-marketing-center/src/main/java/com/bank/branch/platform/customer/entity/TouchTask.java`
- Create: `customer-marketing-center/src/main/java/com/bank/branch/platform/customer/entity/TouchLog.java`
- Create: `customer-marketing-center/src/main/java/com/bank/branch/platform/customer/entity/LeadImportBatch.java`
- Create: `customer-marketing-center/src/main/java/com/bank/branch/platform/customer/mapper/*Mapper.java`
- Create: `customer-marketing-center/src/main/resources/mapper/customer/*Mapper.xml`
- Test: `customer-marketing-center/src/test/java/com/bank/branch/platform/customer/mapper/*MapperTest.java`

- [ ] **Step 1:** 逐表对照 `05-表结构DDL.md` 创建 8 个实体，字段、索引、逻辑删除标记保持一致。
- [ ] **Step 2:** 先写 Mapper 集成测试，覆盖基础 CRUD、分页、唯一性查询和关键统计。
- [ ] **Step 3:** 再实现 Mapper 接口与 XML，统一使用 `classpath*:mapper/**/*Mapper.xml` 约定。
- [ ] **Step 4:** 运行 `mvn -q test -pl customer-marketing-center -Dtest=*MapperTest`，确认红转绿。
- [ ] **Step 5:** 提交：`feat(customer): 完成 8 张核心表实体与 Mapper 层`

## Task 3: Tag Domain

**Files:**
- Create: `customer-marketing-center/src/main/java/com/bank/branch/platform/customer/service/TagService.java`
- Create: `customer-marketing-center/src/main/java/com/bank/branch/platform/customer/controller/TagController.java`
- Create: `customer-marketing-center/src/main/java/com/bank/branch/platform/customer/facade/TagApiImpl.java`
- Create: `customer-marketing-center/src/main/java/com/bank/branch/platform/customer/api/TagApi.java`
- Create: `customer-marketing-center/src/main/java/com/bank/branch/platform/customer/api/dto/TagDTO.java`
- Create: `customer-marketing-center/src/main/java/com/bank/branch/platform/customer/controller/dto/tag/*.java`
- Test: `customer-marketing-center/src/test/java/com/bank/branch/platform/customer/service/TagServiceTest.java`
- Test: `customer-marketing-center/src/test/java/com/bank/branch/platform/customer/controller/TagControllerTest.java`

- [ ] **Step 1:** 写 `TagServiceTest`，覆盖新增、编辑、启停、列表、重名/重码校验、标签客户导入预览与执行。
- [ ] **Step 2:** 实现 `TagService`，先保证写接口校验 `@BizAuth` 与 DATA_SCOPE，再补导出与批量导入。
- [ ] **Step 3:** 写 `TagControllerTest`，验证 `ResponseWrapper`、分页、错误码与文件上传接口。
- [ ] **Step 4:** 实现 `TagController` 和 `TagApi`/`TagApiImpl`。
- [ ] **Step 5:** 运行 `mvn -q test -pl customer-marketing-center -Dtest=TagServiceTest,TagControllerTest`。
- [ ] **Step 6:** 提交：`feat(customer): 完成标签域全栈能力`

## Task 4: Lead Domain

**Files:**
- Create: `customer-marketing-center/src/main/java/com/bank/branch/platform/customer/service/LeadService.java`
- Create: `customer-marketing-center/src/main/java/com/bank/branch/platform/customer/service/LeadImportService.java`
- Create: `customer-marketing-center/src/main/java/com/bank/branch/platform/customer/controller/LeadController.java`
- Create: `customer-marketing-center/src/main/java/com/bank/branch/platform/customer/listener/LeadProcessListener.java`
- Create: `customer-marketing-center/src/main/java/com/bank/branch/platform/customer/api/LeadApi.java`
- Create: `customer-marketing-center/src/main/java/com/bank/branch/platform/customer/api/dto/LeadDTO.java`
- Test: `customer-marketing-center/src/test/java/com/bank/branch/platform/customer/service/LeadServiceTest.java`
- Test: `customer-marketing-center/src/test/java/com/bank/branch/platform/customer/controller/LeadControllerTest.java`

- [ ] **Step 1:** 先写 lead 基础流转测试：草稿、新建、更新、删除、提交审批、审批回写、导入预览/执行。
- [ ] **Step 2:** 实现 `LeadService` 与 `LeadImportService`，发起流程时只通过 `WorkflowApi`，不直接碰 Flowable 引擎。
- [ ] **Step 3:** 实现 lead 流程完成监听器，订阅 workflow 事件并回写状态。
- [ ] **Step 4:** 写 controller 层测试并落地 `LeadController`。
- [ ] **Step 5:** 补 `LeadApi` 及对外 DTO。
- [ ] **Step 6:** 运行 `mvn -q test -pl customer-marketing-center -Dtest=LeadServiceTest,LeadControllerTest`。
- [ ] **Step 7:** 提交：`feat(customer): 完成线索域与审批回写链路`

## Task 5: Customer Master + Pool + Claim

**Files:**
- Create: `customer-marketing-center/src/main/java/com/bank/branch/platform/customer/service/CustomerService.java`
- Create: `customer-marketing-center/src/main/java/com/bank/branch/platform/customer/service/ClaimService.java`
- Create: `customer-marketing-center/src/main/java/com/bank/branch/platform/customer/service/CustomerPoolService.java`
- Create: `customer-marketing-center/src/main/java/com/bank/branch/platform/customer/controller/CustomerController.java`
- Create: `customer-marketing-center/src/main/java/com/bank/branch/platform/customer/controller/ClaimController.java`
- Create: `customer-marketing-center/src/main/java/com/bank/branch/platform/customer/api/CustomerQueryApi.java`
- Create: `customer-marketing-center/src/main/java/com/bank/branch/platform/customer/api/ClaimApi.java`
- Test: `customer-marketing-center/src/test/java/com/bank/branch/platform/customer/service/CustomerServiceTest.java`
- Test: `customer-marketing-center/src/test/java/com/bank/branch/platform/customer/service/ClaimServiceTest.java`

- [ ] **Step 1:** 先写 customer/claim 服务测试，覆盖客户主档增改查、客户池过滤、认领/取消认领、并发防重。
- [ ] **Step 2:** 实现 `CustomerService`、`ClaimService`、`CustomerPoolService`。
- [ ] **Step 3:** 落地 `CustomerController`、`ClaimController`，补分页、导出与 DATA_SCOPE 过滤。
- [ ] **Step 4:** 实现 `CustomerQueryApi`、`ClaimApi`，提供下游所需只读契约。
- [ ] **Step 5:** 运行 `mvn -q test -pl customer-marketing-center -Dtest=CustomerServiceTest,ClaimServiceTest`。
- [ ] **Step 6:** 提交：`feat(customer): 完成客户主档、客户池与认领域`

## Task 6: Touch Task + Touch Report

**Files:**
- Create: `customer-marketing-center/src/main/java/com/bank/branch/platform/customer/service/TouchTaskService.java`
- Create: `customer-marketing-center/src/main/java/com/bank/branch/platform/customer/service/TouchReportService.java`
- Create: `customer-marketing-center/src/main/java/com/bank/branch/platform/customer/controller/TouchTaskController.java`
- Create: `customer-marketing-center/src/main/java/com/bank/branch/platform/customer/controller/TouchReportController.java`
- Create: `customer-marketing-center/src/main/java/com/bank/branch/platform/customer/api/TouchTaskQueryApi.java`
- Test: `customer-marketing-center/src/test/java/com/bank/branch/platform/customer/service/TouchTaskServiceTest.java`
- Test: `customer-marketing-center/src/test/java/com/bank/branch/platform/customer/controller/TouchTaskControllerTest.java`

- [ ] **Step 1:** 写触达状态机测试：待执行、执行中、已完成、已取消、超期 SLA、转交、重复提交防护。
- [ ] **Step 2:** 实现 `TouchTaskService`，消费 `portal-content-center` 的 `ProductApi` / `AddressBookApi`。
- [ ] **Step 3:** 实现 `TouchReportService`，处理日志、图片、导出与敏感字段脱敏。
- [ ] **Step 4:** 落地 controller 与 `TouchTaskQueryApi`，供 `business-application-center` 使用。
- [ ] **Step 5:** 运行 `mvn -q test -pl customer-marketing-center -Dtest=TouchTaskServiceTest,TouchTaskControllerTest`。
- [ ] **Step 6:** 提交：`feat(customer): 完成触达任务与触达报告能力`

## Task 7: Resource Seed, Event Publication and Bootstrap Integration

**Files:**
- Modify: `customer-marketing-center/src/main/resources/`
- Modify: `docs/modules/customer-marketing-center/08-初始化数据清单.md`
- Modify: `bootstrap/pom.xml`
- Modify: `bootstrap/src/test/java/com/bank/branch/platform/it/CrossModuleApiTest.java`
- Create/Modify: `bootstrap/src/test/java/com/bank/branch/platform/it/CustomerMarketingCenterIT.java`

- [ ] **Step 1:** 对照 `08-初始化数据清单.md` 补 `PT_RESOURCE`、字典、流程配置、种子数据脚本。
- [ ] **Step 2:** 补事务后事件发布：标签变更、线索提交/审批、认领变更、触达完成等。
- [ ] **Step 3:** 在 `bootstrap` 中接入 customer 模块依赖与最小真实集成测试。
- [ ] **Step 4:** 扩展 `CrossModuleApiTest`，验证 `CustomerQueryApi`、`ClaimApi`、`TouchTaskQueryApi` 注入与最小查询。
- [ ] **Step 5:** 运行 `mvn -q test -pl customer-marketing-center,bootstrap -am`。
- [ ] **Step 6:** 提交：`feat(customer): 完成资源初始化、事件发布与 bootstrap 集成`

## Final Verification Checklist

- [ ] `mvn -q compile -pl customer-marketing-center -am` 通过
- [ ] `mvn -q test -pl customer-marketing-center` 通过
- [ ] `mvn -q test -pl bootstrap -Dtest=CrossModuleApiTest,CustomerMarketingCenterIT -am` 通过
- [ ] customer 相关所有外部依赖只通过 `*Api` 调用
- [ ] 所有写接口都补齐 `PT_RESOURCE`、`@BizAuth`、审计和数据范围校验
- [ ] `docs/modules/customer-marketing-center/08-初始化数据清单.md` 与代码实现同步
