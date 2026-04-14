# 2026-04-14 workflow-center Flowable E2E 进度记录

## 已完成内容

1. 正式部署链已补齐
   - 在 `workflow-center/src/main/resources/bpmn/loan_approve_v1.bpmn20.xml` 新增真实 BPMN。
   - 在 `bootstrap/src/main/resources/application.yml` 配置 `flowable.process-definition-location-prefix=classpath*:/bpmn/`，使启动时自动部署正式流程定义。

2. workflow-center 核心行为已修正
   - `TodoQueryService` 已支持“未签收 candidate-group 任务对合法角色可见”，且未扩大到已签收任务、已办列表、跨角色可见性。
   - `TaskOperationService.approveTask()` 已显式写入 `approved=true`，与 reject 分支保持一致。
   - `ProcessCompletedListener` 已按 `approved` 变量区分终态：通过写 `COMPLETED`，驳回写 `CANCELLED`。
   - `ProcessStartService` 已在 `biz_process_map` 中写入流程标题。

3. 回归测试已补齐
   - `workflow-center/src/test/java/com/bank/branch/platform/workflow/service/TodoQueryServiceTest.java`
   - `workflow-center/src/test/java/com/bank/branch/platform/workflow/service/TaskOperationServiceTest.java`
   - `workflow-center/src/test/java/com/bank/branch/platform/workflow/listener/ProcessCompletedListenerTest.java`
   - `workflow-center/src/test/java/com/bank/branch/platform/workflow/service/ProcessStartServiceTest.java`

4. bootstrap 级真实 Flowable E2E 已建立
   - `bootstrap/src/test/java/com/bank/branch/platform/it/FlowableWorkflowCenterE2ETest.java`
   - `bootstrap/src/test/java/com/bank/branch/platform/it/config/FlowableE2ETestConfig.java`
   - `bootstrap/src/test/resources/application-flowable-e2e.yml`
   - `bootstrap/src/test/resources/flowable-e2e-data.sql`
   - `bootstrap/src/test/resources/schema.sql`

5. 已验证证据
   - `/tmp/leader-mvn-harness` 中执行：
     - `mvn -pl workflow-center -am -Dtest=ProcessStartServiceTest,TodoQueryServiceTest,TaskOperationServiceTest,ProcessCompletedListenerTest -Dsurefire.failIfNoSpecifiedTests=false test`
   - `/tmp/flowable-e2e-harness` 中执行：
     - `mvn -pl bootstrap -am -Dtest=FlowableWorkflowCenterE2ETest -Dsurefire.failIfNoSpecifiedTests=false -Dspring.profiles.active=flowable-e2e test`
   - Flowable E2E 已覆盖：
     - 正式部署存在
     - 真实登录 Session
     - 无权限用户访问 `/api/workflow/tasks` 返回 403
     - 首节点待办对合法候选组可见
     - 非法办理拒绝
     - 通过链完整流转
     - 驳回链终态为 `CANCELLED`
     - SQL 历史/运行态断言生效

## 未完成内容

1. 未进行本机 MySQL + Redis + MinIO 真实开发环境联调
   - 当前通过的是 H2 + flowable-e2e 测试基线。

2. 未扩展到其他流程定义
   - 当前只覆盖 `loan_approve_v1`。

3. 未处理非本次范围内的工作区遗留变更
   - `bootstrap/pom.xml`
   - `.gitignore`
   - `.omx/*`
   - `.codex`
   这些内容不属于本次 workflow 交付范围。

4. 未补充面向人工联调的 curl / SQL 操作手册
   - 当前主要依赖自动化测试验证。

## 当前建议的后续动作

1. 在本机真实 MySQL/Redis 环境跑一次 smoke。
2. 若后续继续扩流程，优先沿用 `flowable-e2e` 基线补更多场景，而不是直接改已有 workflow 核心语义。
3. 单独评估并处理 `bootstrap/pom.xml` 的遗留变更，不与 workflow 改动混提。
