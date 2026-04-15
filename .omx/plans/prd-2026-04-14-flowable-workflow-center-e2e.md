# PRD - workflow-center Flowable 真实 E2E 验证

- Status: Approved by ralplan consensus loop
- Profile: deliberate
- Source spec: `.omx/specs/deep-interview-flowable-workflow-center-test.md`
- Context snapshot: `.omx/context/flowable-workflow-center-test-20260414T005842Z.md`

## Requirements Summary

- 目标不是证明“Flowable starter 已接入”，而是证明 `workflow-center` 能在本地真实环境中通过正式启动链跑通 `loan_approve_v1` 的真实验证链：启动、待办、签收、审批、驳回、权限拒绝、SQL/历史表断言。
- 现有运行基础已具备：`bootstrap/src/main/resources/application.yml:24`、`bootstrap/src/main/resources/application.yml:25`、`bootstrap/src/main/resources/application.yml:28` 已启用 Flowable 历史、禁用 IDM、开启自动建表。
- 流程启动主链已存在：`workflow-center/src/main/java/com/bank/branch/platform/workflow/service/ProcessStartService.java:58`、`workflow-center/src/main/java/com/bank/branch/platform/workflow/service/ProcessStartService.java:60`、`workflow-center/src/main/java/com/bank/branch/platform/workflow/service/ProcessStartService.java:80`、`workflow-center/src/main/java/com/bank/branch/platform/workflow/service/ProcessStartService.java:97`、`workflow-center/src/main/java/com/bank/branch/platform/workflow/service/ProcessStartService.java:100`、`workflow-center/src/main/java/com/bank/branch/platform/workflow/service/ProcessStartService.java:106`。
- 任务接口主链已存在：`workflow-center/src/main/java/com/bank/branch/platform/workflow/controller/TaskController.java:36`、`workflow-center/src/main/java/com/bank/branch/platform/workflow/controller/TaskController.java:56`、`workflow-center/src/main/java/com/bank/branch/platform/workflow/controller/TaskController.java:116`、`workflow-center/src/main/java/com/bank/branch/platform/workflow/controller/TaskController.java:133`、`workflow-center/src/main/java/com/bank/branch/platform/workflow/controller/TaskController.java:151`。
- 认证入口已存在且采用 Session：`auth-permission-center/src/main/java/com/bank/branch/platform/auth/controller/AuthController.java:43`、`auth-permission-center/src/main/java/com/bank/branch/platform/auth/controller/AuthController.java:58`、`auth-permission-center/src/main/java/com/bank/branch/platform/auth/controller/AuthController.java:60`。
- 候选组桥接主轴已知：`auth-permission-center/src/main/java/com/bank/branch/platform/auth/service/AuthService.java:116`、`auth-permission-center/src/main/java/com/bank/branch/platform/auth/service/AuthService.java:117`、`docs/modules/auth-permission-center/08-初始化数据清单.md:16`、`docs/schema/seed-v1.sql:314` 共同指向 `ROLE_CODE -> ROLE:{ROLE_CODE}`。
- `loan_approve_v1` 真实主链与角色语义已固定：`docs/modules/workflow-center/08-初始化数据清单.md:252`、`docs/modules/workflow-center/08-初始化数据清单.md:253`、`docs/modules/workflow-center/08-初始化数据清单.md:254`、`docs/modules/workflow-center/08-初始化数据清单.md:255`、`docs/modules/workflow-center/08-初始化数据清单.md:260`、`docs/modules/workflow-center/08-初始化数据清单.md:261`、`docs/modules/workflow-center/08-初始化数据清单.md:262`、`docs/modules/workflow-center/08-初始化数据清单.md:263`。
- 当前两大 P0 风险必须先处理：
  - `workflow-center/src/main/java/com/bank/branch/platform/workflow/service/TodoQueryService.java:96`、`workflow-center/src/main/java/com/bank/branch/platform/workflow/service/TodoQueryService.java:100` 仅按 `empId` 查待办，未使用 `candidateGroupKeys`。
  - `docs/modules/workflow-center/05-表结构DDL.md:390` 宣称驳回终止应写 `CANCELLED`，但 `workflow-center/src/main/java/com/bank/branch/platform/workflow/listener/ProcessCompletedListener.java:52` 对任何结束流程都写 `COMPLETED`。

## RALPLAN-DR Summary

### Principles

1. 先锁定决策门与边界，再进入实现。
2. 严格分离认证成功、资源权限拒绝、合法办理、非法办理拒绝四类证据。
3. `TodoQueryService` 只能做最小边界修复，不能演化为待办语义重写。
4. `PT_ROLE_BIZ_SCOPE` 只作为业务矩阵一致性证据，不替代 workflow 访问/办理证据。
5. 终态口径、节点 key、角色编码、验证模板必须在执行前写死。

### Decision Drivers

1. `ProcessStartService` 对“正式部署成功”是硬前提，见 `workflow-center/src/main/java/com/bank/branch/platform/workflow/service/ProcessStartService.java:60`。
2. 候选组桥接基于 `ROLE_CODE`，见 `auth-permission-center/src/main/java/com/bank/branch/platform/auth/service/AuthService.java:116`、`auth-permission-center/src/main/java/com/bank/branch/platform/auth/service/AuthService.java:117`。
3. 首节点待办可见性当前存在实现缺口，见 `workflow-center/src/main/java/com/bank/branch/platform/workflow/service/TodoQueryService.java:100`。

### Viable Options

#### Option A: 集成测试优先
- Approach: 先补正式 BPMN 部署与最小边界修复，再用集成测试锁流程与权限行为。
- Pros: 定位快，最利于控制 `TodoQueryService` 修复边界。
- Cons: 单独不足以构成真实环境联调结论。

#### Option B: HTTP E2E 优先
- Approach: 启动应用直接用真实账号跑完整链路，边跑边修。
- Pros: 最贴近验收。
- Cons: 当前 P0 风险太高，失败归因会混在一起。

#### Option C: 组合方案（Chosen）
- Approach: 先过决策门与真实冒烟门，再用失败测试驱动正式部署、最小边界修复与终态统一，最后以真实 HTTP E2E 收口。
- Pros: 同时满足 deliberate 模式的边界控制、证据分层和真实联调要求。
- Cons: 前置约束多，对执行纪律要求最高。

### Recommendation

选择 Option C，并附带两个硬约束：
- 仅允许修复“未签收候选组任务对合法候选组成员可见”，不得扩展到已签收任务、已办列表、跨角色可见性、非 workflow 接口语义。
- reject 终态必须先统一唯一口径，再写 BPMN/E2E/SQL 断言。

### Pre-mortem

1. 待办可见性修复越界，验证任务滑成行为改造任务。
2. BPMN 驳回已终止，但 `biz_process_map` 终态与文档/代码口径冲突。
3. 为打通验证而扩展到认证机制、菜单体系、多模块 seed 或其他流程，导致任务失焦。

### Expanded Test Plan

- unit: `CandidateResolverService`、`TaskAssignmentListener`、`TodoQueryService` 边界测试、reject 终态测试。
- integration: 正式部署查询、流程启动、首节点可见性、通过链、驳回链、资源权限、办理权限。
- e2e: 认证成功链、资源权限拒绝链、合法办理链、非法办理拒绝链。
- observability: Session/Cookie、`processInstanceId`、`taskId`、`candidateGroupKeys`、`biz_process_map`、`ACT_*` 快照。

## Environment Baseline

- MySQL: `bootstrap/src/main/resources/application.yml:14`
- Redis: `bootstrap/src/main/resources/application.yml:19`、`bootstrap/src/main/resources/application.yml:20`
- Session store: `bootstrap/src/main/resources/application.yml:21`、`bootstrap/src/main/resources/application.yml:22`、`bootstrap/src/main/resources/application.yml:23`
- Formal deployment default: `classpath:bpmn/*.bpmn20.xml`，依据 `docs/modules/business-application-center/08-初始化数据清单.md:128`、`docs/modules/business-application-center/08-初始化数据清单.md:131`、`docs/modules/business-application-center/08-初始化数据清单.md:133`
- Seed load order:
  1. shared DDL
  2. `docs/schema/seed-v1.sql`
  3. `docs/schema/workflow-seed-v1.sql`
  4. application startup formal BPMN deployment
  5. minimal test overlay SQL
- Stop rule: 若需要扩展到认证机制改造、菜单体系、多个模块 seed、非 `loan_approve_v1` 流程或超出最小 PT 绑定范围，立即暂停并重新定界。

## Minimal Test Account Matrix

| 用途 | USER_ID | USERNAME | ROLE_ID | ORG_CODE |
| --- | --- | --- | --- | --- |
| 发起人 | `E10001` | `rm_zhang` | `R_RM` | `001001` |
| 机构负责人 | `E20001` | `branch_wang` | `R_BRANCH_MGR` | `001001` |
| 公司部 | `E30001` | `corp_zhao` | `R_CORP_DEPT` | `001` |
| 授信审查 | `E60001` | `reviewer_chen` | `R_CREDIT_REVIEWER` | `001` |
| 授信批复 | `E60002` | `approver_he` | `R_CREDIT_APPROVER` | `001` |

- Evidence: `docs/modules/auth-permission-center/08-初始化数据清单.md:233`、`docs/modules/auth-permission-center/08-初始化数据清单.md:235`、`docs/modules/auth-permission-center/08-初始化数据清单.md:236`、`docs/modules/auth-permission-center/08-初始化数据清单.md:242`、`docs/modules/auth-permission-center/08-初始化数据清单.md:243`、`docs/modules/auth-permission-center/08-初始化数据清单.md:271`、`docs/modules/auth-permission-center/08-初始化数据清单.md:272`、`docs/modules/auth-permission-center/08-初始化数据清单.md:278`、`docs/modules/auth-permission-center/08-初始化数据清单.md:279`、`docs/superpowers/sql/2026-04-10-pt-align-and-test-seed.sql:281`、`docs/superpowers/sql/2026-04-10-pt-align-and-test-seed.sql:282`、`docs/superpowers/sql/2026-04-10-pt-align-and-test-seed.sql:287`、`docs/superpowers/sql/2026-04-10-pt-align-and-test-seed.sql:303`、`docs/superpowers/sql/2026-04-10-pt-align-and-test-seed.sql:304`、`docs/superpowers/sql/2026-04-10-pt-align-and-test-seed.sql:310`、`docs/superpowers/sql/2026-04-10-pt-align-and-test-seed.sql:311`。
- Negative sample: 若现有 seed 中无“已认证但无 workflow 资源”的合适用户，则新增一个最小 `no-workflow-resource` 专用测试用户，仅用于 `/api/workflow/tasks` 的 403 负样本。

## Phase 0 Gating Decisions

1. 正式部署默认优先路径采用 `classpath:bpmn/*.bpmn20.xml`；只有正式启动链不能承载时才 fallback 到独立正式部署器。
2. `wf_node_candidate_conf.candidate_value (ROLE)` 对齐 `PT_ROLE.ROLE_CODE`；登录态候选组格式固定为 `ROLE:{ROLE_CODE}`。
3. `TodoQueryService` 修复边界只允许“未签收候选组任务对合法候选组成员可见”。
4. reject 终态必须在执行前统一为唯一口径：文档 `CANCELLED` 或当前代码 `COMPLETED` 二选一，并写死 SQL 断言。
5. HTTP E2E 登录方案固定为真实 `POST /api/auth/login` + `HttpSession` / Cookie 复用，不允许测试后门替代。

## Time Sequencing

- Smoke phase only proves: 登录成功、无资源权限 403、有资源权限接口可达。
- Flow-validation phase starts after formal deployment + process start.
- Once process flow validation starts, if first-node candidate task is invisible to a lawful candidate-group member, treat as P0 `stop-and-fix` and do not continue to downstream approve/reject tests.

## Acceptance Criteria

1. `loan_approve_v1` 必须通过正式启动链可查询，不得依赖测试夹具临时 deploy。
2. 主链节点必须严格对齐：`branch_approve -> corp_review -> credit_check -> credit_approval`。
3. 必须分别以不同证据证明：认证成功、资源权限拒绝、合法办理、非法办理拒绝。
4. 登录态必须真实来自 `/api/auth/login` + `HttpSession`。
5. 候选组桥接必须基于 `ROLE_CODE`。
6. 首节点未签收待办必须对合法候选组成员可见。
7. 该修复不得扩大已签收任务、已办列表、跨角色可见性、非 workflow 接口语义。
8. reject 终态口径必须唯一，`biz_process_map.process_status` SQL 断言必须写死为单值。
9. 必须完成 `biz_process_map`、`ACT_RU_TASK`、`ACT_HI_TASKINST`、`ACT_HI_COMMENT` 的关键 SQL 断言。
10. `PT_ROLE_BIZ_SCOPE` 仅能作为环境/业务矩阵一致性证据，不得作为 workflow 任务权限直接证据。

## Implementation Steps

1. 决策门
- 锁定正式部署路径、candidate group 桥接规则、`TodoQueryService` 修复边界、reject 终态口径、HTTP E2E 登录方案。

2. 环境基线与认证冒烟
- 按既定顺序装载 DDL 和 seed。
- 启动应用。
- 跑“登录成功 / 403 / 接口可达”三项冒烟门。

3. 失败测试
- 先写失败测试覆盖：正式启动链查不到 `loan_approve_v1`、合法候选组成员看不到未签收首节点待办、修复后扩大了其他语义、reject 终态与 SQL 断言冲突。

4. BPMN/部署实现
- 按默认优先路径补齐 `classpath:bpmn/*.bpmn20.xml` 自动部署。
- 仅在正式启动链无法支持时才考虑独立正式部署器 fallback。
- BPMN 节点和驳回分支严格对齐文档。

5. 角色/PT 数据最小校准
- 使用最小测试账号矩阵。
- 最小补齐 `PT_ROLE_RESOURCE`。
- 若缺负样本，则新增最小 `no-workflow-resource` 用户。
- `PT_ROLE_BIZ_SCOPE` 仅做一致性补齐。

6. 集成测试闭环
- 打通部署、启动、首节点可见、签收、审批、驳回。
- 对 `TodoQueryService` 修复边界加回归保护。
- 若首节点待办不可见，按 P0 `stop-and-fix`。

7. HTTP E2E + SQL 断言
- 用真实登录态跑通过链与驳回链。
- 分别验证资源拒绝、合法办理、非法办理拒绝。
- 以固定 SQL 模板断言终态。

8. 回归入口整理
- 整理环境准备、登录冒烟、集成测试、E2E 与 SQL 核验入口。
- 明确开发回归与联调 smoke 的边界。

## Risks and Mitigations

- P0: `TodoQueryService` 首节点候选组待办不可见，见 `workflow-center/src/main/java/com/bank/branch/platform/workflow/service/TodoQueryService.java:96`、`workflow-center/src/main/java/com/bank/branch/platform/workflow/service/TodoQueryService.java:100`。
- P0: reject 终态口径冲突，见 `docs/modules/workflow-center/05-表结构DDL.md:390`、`workflow-center/src/main/java/com/bank/branch/platform/workflow/listener/ProcessCompletedListener.java:52`。
- P0: 正式启动链未落地。
- P1: 验证任务滑向基础设施扩修。
- P1: `PT_ROLE_BIZ_SCOPE` 被误用为直接权限证据。

## Kill Criteria / Stop-and-fix

- 正式启动链无法查询到 `loan_approve_v1`
- `/api/auth/login` 无法建立真实 Session
- 无资源权限用户未返回 403
- 流程已启动后，合法候选组成员看不到未签收首节点待办
- `TodoQueryService` 修复超出“未签收候选组任务可见”边界
- reject 终态口径未统一
- 为打通验证需要扩展到认证机制、菜单体系、多个模块 seed、非 `loan_approve_v1` 流程或超出最小 PT 绑定范围

## Verification Templates

### 登录成功模板
- HTTP: `POST /api/auth/login` 返回 `200`
- Session: 响应后存在 `Session/Cookie`
- Reuse: 使用同一 Session 调 `GET /api/workflow/tasks`，结果必须不是 `401`

### 通过链 / 驳回链模板
- 通过链:
  - `biz_process_map.process_definition_key = 'loan_approve_v1'`
  - 中途 `process_status = 'RUNNING'`
  - `ACT_RU_TASK` 当前节点依次推进到 `branch_approve`、`corp_review`、`credit_check`、`credit_approval`
  - `ACT_HI_TASKINST` 顺序落历史
  - `ACT_HI_COMMENT` 保存审批意见
- 驳回链:
  - 驳回后不存在后续审批节点运行态任务
  - 驳回节点历史完成
  - `biz_process_map.process_status = <Phase 0 locked value>`

### 非法办理拒绝模板
- HTTP: 非法办理人调用 `/approve` 或 `/reject`，返回固定 4xx（执行前钉死具体值）
- State unchanged:
  - `ACT_RU_TASK` 仍在原节点
  - `assignee` 不变
  - `biz_process_map.current_assignee` 不变
  - `ACT_HI_COMMENT` 不新增非法操作记录

## ADR

### Decision
- 采用“决策门 + 冒烟门 + 最小边界修复 + 真实 E2E 收口”的执行路线。

### Drivers
- 正式部署链、候选组桥接和 reject 终态均是计划级硬前置。
- `TodoQueryService` 当前实现不足以支撑真实首节点待办可见性。
- 本任务要求真实验证，不接受顺带升级 workflow 查询模型。

### Alternatives Considered
- 只做集成测试：不能证明真实 Session/HTTP 联调。
- 只做 HTTP 联调：P0 风险过多，容易边跑边失控。
- 扩大修复范围重做待办查询语义：会把验证任务演化为行为改造任务。

### Why Chosen
- 同时保证证据分层与改动边界控制。
- 最适合当前存在 P0 风险但仍要求 execution-ready 的场景。

### Consequences
- 必须严格遵守“最小待办可见性修复”边界。
- reject 终态必须先统一，再进入实现。
- 若 `TodoQueryService` 修复越界，任务必须重新定界。

### Follow-ups
- 执行模式接手后，先显式打钩 Phase 0 五项 gating decisions。
- 第一批失败测试优先覆盖：正式部署查询、首节点候选组可见性、reject 终态口径。
- 若冒烟门通过而流程链在首节点待办可见性失败，立即按 P0 `stop-and-fix`，不进入后续审批链验证。

## Available-Agent-Types Roster

- `executor`: 实现 BPMN、部署链、服务修复
- `debugger`: 处理正式部署或状态流转异常
- `test-engineer`: 设计与补齐 unit/integration/E2E 测试
- `verifier`: 收集完成证据并核验断言
- `architect`: 处理 reject 终态、部署责任边界这类架构分歧
- `build-fixer`: 处理测试/构建失败
- `code-reviewer`: 执行后质量复核
- `researcher`: 查 Flowable 正式部署约定或项目内文档细节

## Follow-up Staffing Guidance

- `ralph` path:
  - Lead lane: `executor`（high）负责按 Phase 0 → TDD → E2E 顺序推进
  - On-demand lane: `debugger`（high）处理部署/流转阻塞
  - Evidence lane: `verifier`（high）在实现后核验 SQL/HTTP/ACT_* 证据
- `team` path:
  - Lane 1: BPMN 与正式部署链，`executor`（high）
  - Lane 2: Auth/PT 最小数据与负样本，`executor`（high）或 `debugger`（high）
  - Lane 3: 测试与 E2E 模板，`test-engineer`（medium）+
    `executor`（high）
  - Lane 4: 终态/证据复核，`verifier`（high）

## Launch Hints

- Sequential execution: `$ralph .omx/plans/prd-2026-04-14-flowable-workflow-center-e2e.md`
- Team execution: `$team .omx/plans/prd-2026-04-14-flowable-workflow-center-e2e.md`
- Team with paired test spec: `$team .omx/plans/prd-2026-04-14-flowable-workflow-center-e2e.md .omx/plans/test-spec-2026-04-14-flowable-workflow-center-e2e.md`

## Team Verification Path

- Team must prove before shutdown:
  - Phase 0 五项决策已锁定
  - 正式部署链可查询 `loan_approve_v1`
  - 首节点合法候选组可见性已按最小边界修复
  - 通过链与驳回链各至少一条自动化证据存在
- Ralph/verifier must confirm after handoff:
  - SQL 终态与锁定口径一致
  - 非法办理拒绝与资源权限拒绝证据完整
  - 未出现超出边界的基础设施扩修

## Applied Improvements Changelog

- Added Phase 0 gating decisions for deployment path, candidate group bridge, `TodoQueryService` fix boundary, reject terminal status, and real login path.
- Added explicit minimum test account matrix and negative-sample policy.
- Added strict boundary for `TodoQueryService` fix scope and regression protection.
- Added explicit treatment of reject terminal-status conflict between docs and code.
- Added smoke-phase vs flow-validation sequencing.
- Added anti-scope-creep stop rule.
- Added concrete verification templates and follow-up staffing guidance.
