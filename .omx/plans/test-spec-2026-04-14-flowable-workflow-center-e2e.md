# Test Spec - workflow-center Flowable 真实 E2E 验证

- PRD: `.omx/plans/prd-2026-04-14-flowable-workflow-center-e2e.md`
- Status: Approved by ralplan consensus loop

## Test Scope

- In: `loan_approve_v1` 正式部署、流程启动、首节点待办可见、签收、审批通过、驳回终止、认证成功、资源权限拒绝、非法办理拒绝、SQL/历史表断言。
- Out: 管理端候选人/表单/SLA 配置接口、多流程、并行会签、复杂网关、前端页面联调。

## Required Decisions Before Execution

1. 正式部署路径是否按默认优先路径 `classpath:bpmn/*.bpmn20.xml` 生效。
2. reject 终态唯一口径：`CANCELLED` 或 `COMPLETED`。
3. 非法办理拒绝的固定 HTTP 状态码。
4. 无资源权限负样本是否使用现有用户，还是新增最小 `no-workflow-resource` 测试用户。

## Environment Baseline

- MySQL: `localhost:3306/onepl`
- Redis: `localhost:6379`
- Session: Redis-backed `HttpSession`
- Flowable:
  - `history-level: audit`
  - `database-schema-update: true`
- Seed load order:
  1. shared DDL
  2. `docs/schema/seed-v1.sql`
  3. `docs/schema/workflow-seed-v1.sql`
  4. app startup formal BPMN deployment
  5. minimal test overlay SQL

## Test Accounts

| 用途 | USER_ID | USERNAME | ROLE_ID | ORG_CODE |
| --- | --- | --- | --- | --- |
| 发起人 | `E10001` | `rm_zhang` | `R_RM` | `001001` |
| 机构负责人 | `E20001` | `branch_wang` | `R_BRANCH_MGR` | `001001` |
| 公司部 | `E30001` | `corp_zhao` | `R_CORP_DEPT` | `001` |
| 授信审查 | `E60001` | `reviewer_chen` | `R_CREDIT_REVIEWER` | `001` |
| 授信批复 | `E60002` | `approver_he` | `R_CREDIT_APPROVER` | `001` |
| 403 负样本 | `<to lock>` | `<to lock>` | `<no workflow resource>` | `<to lock>` |

## Contract Tests

### Contract A: Real Login

- Request: `POST /api/auth/login`
- Must assert:
  - HTTP `200`
  - session/cookie created
  - same session can call `GET /api/workflow/tasks` and response is not `401`

### Contract B: Resource Denial

- Request: `GET /api/workflow/tasks` using authenticated negative user
- Must assert:
  - HTTP `403`
  - response comes through authorization denial path

### Contract C: First-Node Visibility

- Preconditions:
  - `loan_approve_v1` formally deployed
  - process started by initiator
- Request: `GET /api/workflow/tasks` using `E20001`
- Must assert:
  - `branch_approve` task visible before claim
  - visibility is due only to lawful candidate-group membership
- Regression asserts:
  - no cross-role leakage
  - no change to done-list semantics
  - no change to signed/assigned task semantics beyond intended scope

### Contract D: Claim / Approve / Reject

- Claim: `POST /api/workflow/tasks/{taskId}/claim`
- Approve: `POST /api/workflow/tasks/{taskId}/approve`
- Reject: `POST /api/workflow/tasks/{taskId}/reject`
- Must assert:
  - lawful handler succeeds
  - unlawful handler gets fixed 4xx
  - state remains unchanged after unlawful handler action

## Process Path Tests

### Pass Chain

1. `E10001` starts `loan_approve_v1`
2. `E20001` sees `branch_approve`, claims, approves
3. `E30001` sees `corp_review`, claims, approves
4. `E60001` sees `credit_check`, claims, approves
5. `E60002` sees `credit_approval`, claims, approves
6. Assert final runtime/history/business-map state per locked final-status contract

### Reject Chain

1. Start a second `loan_approve_v1`
2. Progress to the fixed reject node selected during execution
3. Submit `approved=false`
4. Assert process ends at `EndEvent(REJECTED)`
5. Assert `biz_process_map.process_status = <locked reject terminal status>`

## SQL Assertion Templates

### Business Map

- `process_definition_key = 'loan_approve_v1'`
- `business_key = 'LOAN:{bizId}'`
- During flow: `process_status = 'RUNNING'`
- After reject: `process_status = <locked reject terminal status>`
- `current_assignee` transitions as expected
- `end_time` existence must match locked terminal-status semantics

### Runtime Tasks

- Before claim: expected active node exists in `ACT_RU_TASK`
- After claim: `assignee` equals current lawful handler
- After approve/reject: prior task no longer active
- After reject end: no downstream active approval task remains

### History

- `ACT_HI_TASKINST` records completed nodes in order
- `ACT_HI_COMMENT` contains approve/reject opinions
- Illegal handler attempts do not create new history/comment records tied to illegal operation

## Negative Tests

- unauthenticated access returns `401`
- authenticated but no workflow resource returns `403`
- authenticated with workflow resource but wrong role cannot see first candidate task
- authenticated with workflow resource but non-assignee cannot approve/reject claimed task
- `TodoQueryService` regression: fix must not widen visibility beyond unclaimed candidate-group tasks

## Verification Commands / Evidence Expectations

- Login verification:
  - capture HTTP status
  - capture session cookie
  - reuse cookie for protected endpoint
- Deployment verification:
  - query formal deployment result through preferred verification path selected during execution
- SQL verification:
  - snapshot `biz_process_map`, `ACT_RU_TASK`, `ACT_HI_TASKINST`, `ACT_HI_COMMENT`
- Report verification:
  - evidence separated into four buckets: auth success, resource denial, lawful handling, unlawful handling denial

## Stop Conditions

- `loan_approve_v1` not queryable via formal startup chain
- lawful candidate-group member cannot see first unclaimed task after process start
- `TodoQueryService` fix requires widening scope beyond agreed boundary
- reject terminal status contract remains unresolved
- task requires broader infra changes than minimal PT/bpmn/test-data scope

## Coverage Checklist

- [ ] formal deployment proven
- [ ] login/session proven
- [ ] 403 resource denial proven
- [ ] first-node visibility proven
- [ ] claim proven
- [ ] full approve chain proven
- [ ] reject chain proven
- [ ] illegal handling denial proven
- [ ] SQL/history evidence captured
- [ ] no scope-widening regression introduced
