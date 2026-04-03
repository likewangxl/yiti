# Workflow-Center Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the workflow-center module as the single Flowable 7.x integration boundary, providing process initiation, task management, approval workflows, and SLA monitoring.

**Architecture:** Embedded Flowable 7.0.1 engine with custom tables for business-process mapping. Services wrap Flowable RuntimeService/TaskService/HistoryService. Candidate groups resolved from auth-permission-center roles/orgs. SLA computed via system-governance-center CalendarApi. TDD with mocked Flowable services.

**Tech Stack:** Spring Boot 3.2.3, JDK 17, Flowable 7.0.1, MyBatis 3.0.3, Redis 6.x

**Reference files:**
- Spec: `docs/superpowers/specs/2026-04-03-workflow-center-design.md`
- DDL: `docs/modules/workflow-center/05-表结构DDL.md`
- API design: `docs/modules/workflow-center/03-接口设计与报文.md`
- API contract: `docs/modules/workflow-center/04-对外API契约.md`
- Common dev guide: `docs/common-dev-guide.md`
- Reference modules: `auth-permission-center/`, `system-governance-center/` (follow same patterns)

**IMPORTANT - Flowable mocking strategy:**
- Unit tests mock Flowable services (RuntimeService, TaskService, HistoryService, RepositoryService)
- Use `@ExtendWith(MockitoExtension.class)` + `@Mock` for all Flowable services
- Do NOT start the Flowable engine in unit tests
- ProcessDefinition, Task, ProcessInstance are Flowable interfaces — mock them directly

**IMPORTANT - Common module classes:**
- `com.bank.branch.platform.common.web.ResponseWrapper` — success(data), page(pageResult), error(code, msg)
- `com.bank.branch.platform.common.web.PageResult` — of(pageNo, pageSize, total, records)
- `com.bank.branch.platform.common.web.exception.BizException` — BizException(code, message)
- `com.bank.branch.platform.common.security.enums.BizType`, `BizAction`
- `com.bank.branch.platform.common.security.annotation.BizAuth`

---

## Phase 1: 流程发起能力

### Task 1: Module Scaffolding + Enums + Root POM

**Files:**
- Create: `workflow-center/pom.xml`
- Modify: `pom.xml` (root) — add module + dependencyManagement
- Create: `workflow-center/src/main/java/com/bank/branch/platform/workflow/enums/WfErrorCode.java`
- Create: `workflow-center/src/main/java/com/bank/branch/platform/workflow/enums/ProcessStatus.java`
- Create: `workflow-center/src/main/java/com/bank/branch/platform/workflow/enums/SlaStatus.java`

**Context:**
- Read `system-governance-center/pom.xml` for module POM pattern
- Root `pom.xml` already has `<flowable.version>7.0.1</flowable.version>` property
- workflow-center pom needs: all 5 common modules, auth-permission-center, system-governance-center, flowable-spring-boot-starter (version ${flowable.version}), spring-boot-starter-web, spring-boot-starter-data-redis, mybatis-spring-boot-starter, knife4j, lombok, spring-boot-starter-test
- ProcessStatus: RUNNING, COMPLETED, CANCELLED
- SlaStatus: GREEN, YELLOW, RED

- [ ] **Step 1:** Read root pom.xml and system-governance-center/pom.xml for patterns
- [ ] **Step 2:** Create workflow-center/pom.xml with all dependencies including flowable-spring-boot-starter
- [ ] **Step 3:** Modify root pom.xml — add module + dependencyManagement entry
- [ ] **Step 4:** Create WfErrorCode enum (10 error codes from spec: WF-40401 through WF-50001)
- [ ] **Step 5:** Create ProcessStatus enum (RUNNING/COMPLETED/CANCELLED) and SlaStatus enum (GREEN/YELLOW/RED)
- [ ] **Step 6:** Verify compilation: `mvn compile -pl workflow-center -am -q`
- [ ] **Step 7:** Commit: `feat(workflow): Task 1 - 模块脚手架 + 枚举`

---

### Task 2: 4 Custom Table Entities + Mappers (TDD)

**Files:**
- Create: entity/BizProcessMap.java, entity/WfNodeCandidateConf.java, entity/WfNodeFormConf.java, entity/WfTimeoutRule.java
- Create: mapper/BizProcessMapMapper.java, mapper/NodeCandidateConfMapper.java, mapper/NodeFormConfMapper.java, mapper/TimeoutRuleMapper.java
- Create: resources/mapper/workflow/*.xml (4 files)

**Context:**
- Read `docs/modules/workflow-center/05-表结构DDL.md` for exact column names and types
- Follow system-governance-center entity/mapper patterns exactly
- BizProcessMap: key fields are businessKey (unique when status=RUNNING), processInstanceId
- BizProcessMapMapper needs: insert, selectByBusinessKey, selectByProcessInstanceId, selectByBizTypeAndBizId, updateById, existsRunningByBusinessKey
- NodeCandidateConfMapper: selectByProcessDefKeyAndNodeId, insert, update, deleteById, selectByProcessDefKey
- NodeFormConfMapper: selectByProcessDefKeyAndNodeId, insert, update
- TimeoutRuleMapper: selectByProcessDefKeyAndNodeId, insert, update

- [ ] **Step 1:** Read DDL file for exact table structures
- [ ] **Step 2:** Create all 4 entity classes (@Data, matching DDL columns)
- [ ] **Step 3:** Create all 4 mapper interfaces (@Mapper)
- [ ] **Step 4:** Create all 4 mapper XML files (with BASE_COLUMNS, dynamic SQL)
- [ ] **Step 5:** Verify compilation: `mvn compile -pl workflow-center -am -q`
- [ ] **Step 6:** Commit: `feat(workflow): Task 2 - 4 自定义表 Entity + Mapper`

---

### Task 3: CandidateResolverService (TDD)

**Files:**
- Create: `workflow-center/src/main/java/.../workflow/service/CandidateResolverService.java`
- Create: `workflow-center/src/test/java/.../workflow/service/CandidateResolverServiceTest.java`

**Context:**
- Inject: NodeCandidateConfMapper
- `resolveCandidates(String processDefinitionKey, String nodeId)` → `List<String>`
  - Query wf_node_candidate_conf by processDefKey + nodeId
  - Parse candidateValue JSON array
  - ROLE type: return `ROLE:{value}` for each
  - ORG type: return `ORG:{value}` for each
  - USER type: return `USER:{value}` for each
  - No config found → return empty list (not exception)

Tests (at least 4):
1. `resolve_roleType_returnsRolePrefixed` — config has ROLE type with ["CUST_MANAGER","TEAM_LEAD"], returns ["ROLE:CUST_MANAGER","ROLE:TEAM_LEAD"]
2. `resolve_orgType_returnsOrgPrefixed` — ORG type
3. `resolve_userType_returnsUserPrefixed` — USER type
4. `resolve_noConfig_returnsEmptyList` — no matching config in DB
5. `resolve_multipleConfigs_mergesAll` — multiple configs for same node (ROLE + ORG)

- [ ] **Step 1:** Write CandidateResolverServiceTest (RED)
- [ ] **Step 2:** Run tests to verify fail
- [ ] **Step 3:** Implement CandidateResolverService
- [ ] **Step 4:** Run `mvn test -pl workflow-center` — all pass
- [ ] **Step 5:** Commit: `feat(workflow): Task 3 - CandidateResolverService (TDD)`

---

### Task 4: ProcessStartService + WorkflowApi (TDD)

**Files:**
- Create: service/ProcessStartService.java
- Create: api/WorkflowApi.java
- Create: api/dto/StartProcessCmd.java, api/dto/WorkflowLaunchResp.java, api/dto/BizProcessMapDTO.java
- Create: facade/WorkflowFacade.java
- Create: test: service/ProcessStartServiceTest.java

**Context:**
- Inject: RepositoryService (Flowable), RuntimeService (Flowable), TaskService (Flowable), BizProcessMapMapper
- `startProcess(StartProcessCmd cmd)` → WorkflowLaunchResp:
  1. RepositoryService.createProcessDefinitionQuery().processDefinitionKey(key).latestVersion().singleResult() — null → WF-40401
  2. BizProcessMapMapper.existsRunningByBusinessKey(businessKey) — true → WF-40901
  3. RuntimeService.startProcessInstanceByKey(key, businessKey, variables) — returns ProcessInstance
  4. Create BizProcessMap entity, status=RUNNING, insert
  5. TaskService.createTaskQuery().processInstanceId(pid).singleResult() — get firstTaskId (may be null)
  6. Publish event via ApplicationEventPublisher
  7. Return WorkflowLaunchResp(processInstanceId, businessKey, firstTaskId)

- WorkflowApi: startProcess(), getProcessByBusinessKey(), getProcessByBizTypeAndBizId()
- WorkflowFacade: @Service implements WorkflowApi

StartProcessCmd fields: bizType, bizId, businessKey, processDefinitionKey, startUser, startOrgId, title, variables(Map<String,Object>)

Tests (at least 5):
1. `startProcess_defNotFound_throwsWf40401` — RepositoryService returns null
2. `startProcess_businessKeyAlreadyRunning_throwsWf40901` — mapper returns true
3. `startProcess_success_createsMapAndReturnsResp` — verify BizProcessMap inserted, resp has processInstanceId
4. `startProcess_success_publishesEvent` — verify eventPublisher.publishEvent called
5. `getProcessByBusinessKey_found_returnsDto` — mapper returns entity, facade converts to DTO
6. `getProcessByBusinessKey_notFound_throwsWf40402`

- [ ] **Step 1:** Write ProcessStartServiceTest (RED)
- [ ] **Step 2:** Run tests to verify fail
- [ ] **Step 3:** Implement StartProcessCmd, WorkflowLaunchResp, BizProcessMapDTO, WorkflowApi, ProcessStartService, WorkflowFacade
- [ ] **Step 4:** Run `mvn test -pl workflow-center` — all pass
- [ ] **Step 5:** Commit: `feat(workflow): Task 4 - ProcessStartService + WorkflowApi (TDD)`

---

## Phase 2: 审批 / SLA 能力

### Task 5: TaskOperationService (TDD)

**Files:**
- Create: service/TaskOperationService.java
- Create: api/dto/ApproveReqDTO.java, api/dto/RejectReqDTO.java, api/dto/TransferReqDTO.java
- Create: test: service/TaskOperationServiceTest.java

**Context:**
- Inject: TaskService (Flowable), BizProcessMapMapper
- Mock Flowable Task interface for tests

Methods:
- `claimTask(taskId, empId)`:
  1. TaskService.createTaskQuery().taskId(taskId).singleResult() — null → WF-40403
  2. Check task.getAssignee() is null (already claimed → WF-40904)
  3. TaskService.claim(taskId, empId)
  4. Update biz_process_map.currentAssignee

- `approveTask(taskId, empId, variables, comment)`:
  1. Query task, verify assignee == empId (WF-40903)
  2. TaskService.addComment(taskId, processInstanceId, comment)
  3. TaskService.complete(taskId, variables)
  4. Publish workflow.task.approved.v1 event

- `rejectTask(taskId, empId, comment)`:
  1. Verify assignee
  2. addComment + complete with approved=false variable
  3. Publish workflow.task.rejected.v1 event

- `transferTask(taskId, fromEmpId, toEmpId, reason)`:
  1. Verify assignee == fromEmpId
  2. TaskService.setAssignee(taskId, toEmpId)
  3. addComment
  4. Update biz_process_map.currentAssignee
  5. Publish event

Tests (at least 6):
1. `claimTask_success` — task exists, no assignee → claim succeeds
2. `claimTask_alreadyClaimed_throwsWf40904` — task.getAssignee() != null
3. `claimTask_taskNotFound_throwsWf40403`
4. `approveTask_notAssignee_throwsWf40903` — task.getAssignee() != empId
5. `approveTask_success_completesTask` — verify TaskService.complete called
6. `transferTask_success_changesAssignee` — verify setAssignee + addComment

- [ ] **Step 1:** Write TaskOperationServiceTest (RED)
- [ ] **Step 2:** Run tests to verify fail
- [ ] **Step 3:** Implement TaskOperationService + DTOs
- [ ] **Step 4:** Run `mvn test -pl workflow-center` — all pass
- [ ] **Step 5:** Commit: `feat(workflow): Task 5 - TaskOperationService (TDD)`

---

### Task 6: SlaCalculationService (TDD)

**Files:**
- Create: service/SlaCalculationService.java
- Create: test: service/SlaCalculationServiceTest.java

**Context:**
- Inject: TimeoutRuleMapper, CalendarApi (from system-governance-center)
- `calculateSlaStatus(processDefinitionKey, nodeId, taskCreateTime)` → SlaStatus:
  1. Query wf_timeout_rule for warningHours/timeoutHours
  2. No rule found → return GREEN (no SLA configured)
  3. Calculate elapsed working hours: use CalendarApi.countWorkingDays(taskCreateDate, today) to get working days, multiply by 8 (hours/day), add partial day hours
  4. elapsed < warningHours → GREEN
  5. elapsed >= warningHours && < timeoutHours → YELLOW
  6. elapsed >= timeoutHours → RED

Tests (at least 4):
1. `calculate_noRule_returnsGreen` — no timeout rule configured
2. `calculate_withinWarning_returnsGreen` — elapsed < warningHours
3. `calculate_betweenWarningAndTimeout_returnsYellow` — warningHours <= elapsed < timeoutHours
4. `calculate_exceedsTimeout_returnsRed` — elapsed >= timeoutHours

- [ ] **Step 1:** Write SlaCalculationServiceTest (RED)
- [ ] **Step 2:** Run tests to verify fail
- [ ] **Step 3:** Implement SlaCalculationService
- [ ] **Step 4:** Run `mvn test -pl workflow-center` — all pass
- [ ] **Step 5:** Commit: `feat(workflow): Task 6 - SlaCalculationService (TDD)`

---

### Task 7: TodoQueryService (TDD)

**Files:**
- Create: service/TodoQueryService.java
- Create: api/dto/TaskRespDTO.java, api/dto/TaskDetailRespDTO.java
- Create: test: service/TodoQueryServiceTest.java

**Context:**
- Inject: TaskService, HistoryService (Flowable), BizProcessMapMapper, SlaCalculationService, NodeFormConfMapper
- Mock Flowable TaskQuery, HistoricTaskInstanceQuery for tests

Methods:
- `queryTodoList(empId, bizType, keyword, pageNo, pageSize)` → PageResult<TaskRespDTO>:
  1. TaskService.createTaskQuery().taskCandidateOrAssigned(empId)
  2. Apply bizType filter by joining with biz_process_map
  3. For each task, load biz_process_map for business context
  4. Calculate SLA status via SlaCalculationService
  5. Set claimable = (assignee == null && user in candidateGroups)

- `queryDoneList(empId, bizType, keyword, pageNo, pageSize)` → PageResult<TaskRespDTO>:
  1. HistoryService.createHistoricTaskInstanceQuery().taskAssignee(empId).finished()
  2. Order by endTime DESC

- `getTaskDetail(taskId, empId)` → TaskDetailRespDTO:
  1. Load task + biz_process_map + form config + SLA + approval logs
  2. Build runtime access flags (canClaim, canApprove, etc.)

TaskRespDTO fields: taskId, processInstanceId, businessKey, bizType, bizId, title, startUser, startUserName, taskName, taskCreateTime, assignee, candidateGroups, slaStatus, claimable

Tests (at least 4):
1. `queryTodoList_returnsTasksWithSla` — mock TaskService query, verify SLA calculated
2. `queryTodoList_filterByBizType` — only matching bizType returned
3. `queryDoneList_returnsFinishedTasks` — mock HistoryService
4. `getTaskDetail_loadsFullContext` — verify biz map, form config, SLA all loaded

- [ ] **Step 1:** Write TodoQueryServiceTest (RED)
- [ ] **Step 2:** Run tests to verify fail
- [ ] **Step 3:** Implement TodoQueryService + DTOs
- [ ] **Step 4:** Run `mvn test -pl workflow-center` — all pass
- [ ] **Step 5:** Commit: `feat(workflow): Task 7 - TodoQueryService (TDD)`

---

### Task 8: Flowable Listeners (TDD)

**Files:**
- Create: listener/TaskAssignmentListener.java
- Create: listener/ProcessCompletedListener.java
- Create: test: listener/TaskAssignmentListenerTest.java
- Create: test: listener/ProcessCompletedListenerTest.java

**Context:**
- TaskAssignmentListener implements org.flowable.task.service.delegate.TaskListener
  - `notify(DelegateTask delegateTask)`:
    1. Get processDefinitionKey + nodeId (taskDefinitionKey) from delegateTask
    2. Call CandidateResolverService.resolveCandidates()
    3. For each candidate: delegateTask.addCandidateGroup(group)
    4. Optionally send notification via NotifyApi

- ProcessCompletedListener implements org.flowable.engine.delegate.ExecutionListener
  - `notify(DelegateExecution execution)`:
    1. Get processInstanceId from execution
    2. Query BizProcessMap by processInstanceId
    3. Update status = COMPLETED, updatedTime
    4. Publish workflow.process.completed.v1 event

Tests:
1. TaskAssignment: `notify_resolvesCandidatesAndSetsOnTask` — mock DelegateTask, verify addCandidateGroup called
2. TaskAssignment: `notify_noCandidates_noGroupsAdded`
3. ProcessCompleted: `notify_updatesMapToCompleted` — verify mapper.updateById with COMPLETED status
4. ProcessCompleted: `notify_publishesEvent`

- [ ] **Step 1:** Write listener tests (RED)
- [ ] **Step 2:** Run tests to verify fail
- [ ] **Step 3:** Implement both listeners
- [ ] **Step 4:** Run `mvn test -pl workflow-center` — all pass
- [ ] **Step 5:** Commit: `feat(workflow): Task 8 - Flowable Listeners (TDD)`

---

## Phase 3: Controllers + Config

### Task 9: All Controllers (TDD)

**Files:**
- Create: controller/TaskController.java, controller/ProcessController.java, controller/WorkflowAdminController.java
- Create: test files for each controller

**Context:**
- Read `docs/modules/workflow-center/03-接口设计与报文.md` for exact endpoints
- TaskController (`/api/workflow/tasks`): GET /todo, GET /done, GET /{taskId}, POST /{taskId}/claim, POST /{taskId}/approve, POST /{taskId}/reject, POST /{taskId}/transfer — NO @BizAuth (natural filtering)
- ProcessController (`/api/workflow/processes`): GET /{businessKey}, GET /biz/{bizType}/{bizId}
- WorkflowAdminController (`/api/admin/workflow`): CRUD for candidate-configs, form-configs, timeout-rules — @BizAuth(SYS_CONFIG, CONFIG)
- Use MockMvcBuilders.standaloneSetup for tests

Tests per controller (at least 2 each, total ~8):

- [ ] **Step 1:** Write controller tests (RED)
- [ ] **Step 2:** Run tests to verify fail
- [ ] **Step 3:** Implement 3 controllers
- [ ] **Step 4:** Run `mvn test -pl workflow-center` — all pass
- [ ] **Step 5:** Commit: `feat(workflow): Task 9 - Controllers (TDD)`

---

### Task 10: FlowableConfig + AutoConfiguration

**Files:**
- Create: config/FlowableConfig.java
- Create: config/WorkflowAutoConfiguration.java (or AutoConfiguration.imports)

**Context:**
- FlowableConfig:
  - Disable Flowable IDM engine: set property `flowable.idm.enabled=false` in application.yml or programmatically
  - Set history-level=audit: `flowable.history-level=audit`
  - Register TaskAssignmentListener and ProcessCompletedListener as global listeners
  - NOTE: For V1, listener registration can be done via BPMN process definition XML or programmatically via ProcessEngineConfiguration

- AutoConfiguration.imports: register FlowableConfig

- [ ] **Step 1:** Create FlowableConfig with engine settings
- [ ] **Step 2:** Create AutoConfiguration.imports
- [ ] **Step 3:** Verify compilation: `mvn compile -pl workflow-center -am`
- [ ] **Step 4:** Run all tests: `mvn test -pl workflow-center`
- [ ] **Step 5:** Commit: `feat(workflow): Task 10 - FlowableConfig + AutoConfiguration`

---

### Task 11: Final Verification

- [ ] **Step 1:** Run full module tests: `mvn test -pl workflow-center` — all pass
- [ ] **Step 2:** Run full project compilation: `mvn compile -q` — no errors
- [ ] **Step 3:** Verify test count and 0 failures
- [ ] **Step 4:** Final commit if cleanup needed
