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

6. 本机 MySQL + Redis 真实开发环境联调已完成（按本次要求不连接 MinIO）
   - 新增 `bootstrap/src/test/resources/application-flowable-real-env.yml`，直连 `localhost:3306/onepl` 与 `localhost:6379`，并将 Redis 切到隔离库 `db14`；同时关闭 `spring.sql.init.continue-on-error`，避免真实环境初始化错误被吞掉。
   - 新增 `bootstrap/src/test/resources/flowable-real-env-data.sql`，对真实环境缺失的 workflow 测试用户、角色、`PT_RESOURCE` / `PT_ROLE_RESOURCE` / `PT_ROLE_BIZ_SCOPE` 最小闭环、候选组编码以及 `biz_process_map.title` 列做兼容性对齐。
   - 新增 `bootstrap/src/test/java/com/bank/branch/platform/it/FlowableWorkflowCenterRealEnvTest.java`，保留真实 MySQL、Redis Session、Flowable、MVC 鉴权链，只对 MinIO 做 mock。
   - 新增 `bootstrap/src/test/java/com/bank/branch/platform/it/config/FlowableRealEnvTestConfig.java`，仅提供 `MinioClient` mock，不屏蔽真实 Redis。
   - 新增 `bootstrap/src/main/java/com/bank/branch/platform/config/BootstrapMyBatisConfig.java`，解决 bootstrap 运行时未扫描依赖模块 mapper，导致真实环境启动失败的问题。
   - 修复根 `pom.xml` 模块标签损坏，恢复 `portal-content-center` / `customer-marketing-center` 构建链。
   - 在 `create-table.sql` 补齐 `biz_process_map.title` 列定义，使根建表脚本与当前 MyBatis 映射保持一致。

7. 新增真实环境验证证据
   - 执行：
     - `mvn -pl bootstrap -am "-Dtest=FlowableWorkflowCenterRealEnvTest" "-Dsurefire.failIfNoSpecifiedTests=false" "-Dspring.profiles.active=flowable-real-env" test`
     - `mvn -pl bootstrap -am -DskipTests package`
   - 真实环境验证已覆盖：
     - 正式 BPMN 自动部署存在
     - 登录后 Session 落到真实 Redis（隔离库 `db14`）
     - 无工作流权限用户访问 `/api/workflow/tasks` 返回 `AUTH-40301`
     - branch / corp / reviewer / approver 四级审批通过链跑通
     - 驳回链终态写入 `CANCELLED`
     - MySQL 中 `ACT_RU_TASK` / `ACT_HI_TASKINST` / `ACT_HI_COMMENT` / `biz_process_map` 断言生效

8. 已补齐面向真实接口联调的流程命令能力
   - 新增 `workflow-center/src/main/java/com/bank/branch/platform/workflow/controller/ProcessCommandController.java`
     - `POST /api/workflow/processes/submit`：提交流程
     - `POST /api/workflow/processes/{processInstanceId}/cancel`：发起人/管理员撤回流程
   - 新增 `workflow-center/src/main/java/com/bank/branch/platform/workflow/service/ProcessCommandService.java`
     - 提交流程时自动注入当前登录人 `empId/orgCode`
     - 撤回时校验流程仍为 `RUNNING`，且只能由发起人或系统管理员执行
   - `WorkflowApi` / `WorkflowFacade` 已补齐 `cancelProcess(processInstanceId, reason)` 对外契约。
   - `WfErrorCode` 新增 `WF-40905`（流程实例不存在或已结束）。

9. 已补齐提交流程 / 撤回流程的 TDD 回归
   - `workflow-center/src/test/java/com/bank/branch/platform/workflow/controller/ProcessCommandControllerTest.java`
   - `workflow-center/src/test/java/com/bank/branch/platform/workflow/service/ProcessCommandServiceTest.java`
   - 真实环境联调测试 `bootstrap/src/test/java/com/bank/branch/platform/it/FlowableWorkflowCenterRealEnvTest.java` 已改为覆盖：
     - `submit`
     - `cancel`
     - `claim`
     - `transfer`
     - `approve`

10. 已完成真实环境对齐脚本与 curl 验证
   - 新增环境对齐脚本：`docs/superpowers/sql/2026-04-14-workflow-real-env-align.sql`
   - 对齐后在 `bootstrap/` 目录启动：
     - `mvn spring-boot:run`
   - 使用 `curl` 实测通过：
     - `rm_zhang` 提交流程
     - `branch_wang` 签收并转发给 `rm_zhang`
     - `rm_zhang` 审批通过
     - `corp_zhao` / `reviewer_chen` / `approver_he` 逐级签收并审批
     - 第二条流程由 `rm_zhang` 调用撤回接口结束
   - 实测结果：
     - 完成链业务键：`LOAN:CURL73bf4d11`
     - 完成链流程实例：`b31114f0-37e7-11f1-b687-7413ea9d5f70`
     - 转发任务：`b31114fb-37e7-11f1-b687-7413ea9d5f70`
     - 完成链终态：`COMPLETED`
   - 撤回链业务键：`LOAN:CANCEL837d2550`
   - 撤回链流程实例：`b416eb6d-37e7-11f1-b687-7413ea9d5f70`
   - 撤回链终态：`CANCELLED`

11. 已将 submit / cancel 权限增量正式回灌到基线种子
   - `docs/schema/seed-v1.sql` 已补齐：
     - `RES_WF_SUBMIT` → `POST /api/workflow/processes/submit`
     - `RES_WF_CANCEL` → `POST /api/workflow/processes/*/cancel`
     - `R_RM` 对应的 `RR_RM_WF_SUBMIT` / `RR_RM_WF_CANCEL` 资源绑定
   - `data.sql` 已补齐：
     - `W_PROC_SUBMIT`
     - `W_PROC_CANCEL`
     - `R_RM` 对应的 `PT_ROLE_RESOURCE` 绑定
   - 新增回归测试：
     - `auth-permission-center/src/test/java/com/bank/branch/platform/auth/seed/WorkflowPermissionSeedTest.java`
   - 已验证：
     - `mvn -pl auth-permission-center -am "-Dtest=WorkflowPermissionSeedTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`

## 未完成内容

1. 未扩展到其他流程定义
   - 当前只覆盖 `loan_approve_v1`。

2. 未处理非本次范围内的工作区遗留变更
   - `bootstrap/pom.xml`
   - `.gitignore`
   - `.omx/*`
   - `.codex`
   这些内容不属于本次 workflow 交付范围。

3. 未补充面向人工联调的 curl / SQL 操作手册
   - 当前主要依赖自动化测试验证。

4. 未覆盖 MinIO 文件链路
   - 本次按要求显式跳过对象存储，只验证 MySQL + Redis + Flowable 真链路。

## 当前建议的后续动作

1. 后续若要继续跑真实环境联调，优先复用 `flowable-real-env` profile，而不是回到手工点点点。
2. 若继续扩流程定义，沿用 `flowable-e2e` 与 `flowable-real-env` 双基线补场景，避免只在 H2 中验证。
3. 单独评估并处理真实库里的历史脏任务/孤儿运行时数据，减少 `TodoQueryService` 查询时的告警噪音。
4. 若后续希望更多角色直接调用流程 submit/cancel，再扩充 `seed-v1.sql` / `data.sql` 的角色资源绑定，而不是只给 `R_RM`。
5. 单独评估并处理 `bootstrap/pom.xml` 之外的遗留工作区变更，不与 workflow 改动混提。
