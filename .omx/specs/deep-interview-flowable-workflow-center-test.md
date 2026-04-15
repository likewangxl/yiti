# Deep Interview Spec - workflow-center Flowable E2E 验证

## Metadata
- Profile: standard
- Rounds: 6
- Final ambiguity: 14.3%
- Threshold: 20%
- Context type: brownfield
- Context snapshot: `.omx/context/flowable-workflow-center-test-20260414T005842Z.md`
- Transcript: use latest file under `.omx/interviews/` for this slug

## Clarity Breakdown
| Dimension | Score | Notes |
| --- | --- | --- |
| Intent | 0.72 | 用户核心目标是确认 Flowable 7 在当前项目中不是“只接依赖”，而是能结合本地权限与业务语义真实跑通。 |
| Outcome | 0.90 | 目标是补齐并跑通 `loan_approve_v1` 的真实端到端联调，而不是单元测试或 mock。 |
| Scope | 0.95 | 范围已收敛到单流程、用户侧链路、权限与库表断言。 |
| Constraints | 0.88 | 必须适配 `PT_*` 权限表，只允许最小必要测试数据调整，且保持真实业务语义。 |
| Success | 0.85 | 成功判定已能转化为明确测试验收项。 |
| Context | 0.90 | 现有代码、权限链路、种子 SQL、缺失 BPMN 的现状都已确认。 |

## Intent
验证 `workflow-center` 中已集成的 Flowable 7 在当前模块化单体项目里能否真实运转，避免后续业务模块依赖 `WorkflowApi` 时建立在一个“依赖接入了但流程根本跑不起来”的错误前提上。

## Desired Outcome
在 `workflow-center` 内补齐并部署贴近真实业务语义的 `loan_approve_v1` 流程定义及其最小必要配套，使系统能够完成一次真实端到端验证，并以自动化证据证明以下链路成立：
- 流程定义可被加载/部署
- `WorkflowApi.startProcess(...)` / 真实 HTTP 接口可触发流程启动
- 待办可查
- 任务可签收
- 任务可审批通过
- 任务可驳回
- 权限拦截正确依赖 `PT_RESOURCE` + RBAC + `@BizAuth` / DataScope
- 关键业务表与 Flowable `ACT_*` 表状态可被断言验证

## In Scope
- 在 `workflow-center` 中补齐贴近真实链路的 `loan_approve_v1` BPMN 及其部署所需最小代码/资源
- 基于现有 `LOAN` 业务语义和 `PT_*` 权限体系完成真实 E2E 联调
- 覆盖以下主路径：启动、待办、签收、审批通过、驳回、权限校验、库表断言
- 覆盖最小必要的测试账号/角色/机构绑定与 `PT_*` 测试种子校准
- 产出可重复执行的自动化验证证据（测试或脚本，允许组合）

## Out of Scope / Non-goals
- 管理端候选人配置接口、节点表单配置接口、SLA 配置接口
- 多业务流程同时验证
- 并行会签、复杂网关、复杂异常分支
- 前端页面联调
- 说明：`transfer`、`done` 已办列表未出现在用户显式 E2E 范围中，本阶段按“延后”处理。这是基于用户显式验收列表作出的推断。

## Decision Boundaries
以下事项 OMX 可自行决定，无需再次确认：
- `loan_approve_v1` BPMN 文件的具体放置路径、部署方式、监听器绑定方式，只要仍然位于 `workflow-center` 并符合 Flowable/Spring Boot 约定
- E2E 验证的具体载体：可选择 Spring Boot 集成测试、启动后 HTTP 脚本验证，或两者组合，只要证据完整
- 为了联调稳定运行，可增补或微调最小必要的 `PT_RESOURCE`、`PT_ROLE_RESOURCE`、`PT_ROLE_BIZ_SCOPE`、测试账号、角色绑定、机构绑定数据
- 测试中可选定一组最合适的真实角色路径来代表 `loan_approve_v1` 的审批链

以下事项不应自行扩大：
- 不要把范围扩展到管理端配置接口、多流程、复杂网关、前端联调
- 不要将真实业务语义降级为纯测试流程 key
- 不要将 `loan_approve_v1` 压缩为明显失真的演示版流程

## Constraints
- 必须遵守项目模块边界：只有 `workflow-center` 直接调用 Flowable API
- 必须适配本地 `PT_*` 权限表与现有鉴权链路
- 必须复用现有 `LOAN / loan_approve_v1` 业务语义
- 必须走 TDD：先写失败测试，再补实现
- 不新增依赖，除非后续另行批准
- 测试数据调整只能是“最小必要”，且不能改变业务含义

## Testable Acceptance Criteria
1. `loan_approve_v1` 能在当前项目启动后被 Flowable 识别为可用流程定义。
2. 存在自动化验证，能证明启动流程成功并生成流程实例，且 `biz_process_map` 与 Flowable `ACT_*` 关键表状态符合预期。
3. 存在自动化验证，能证明待办查询返回当前节点任务。
4. 存在自动化验证，能证明具有正确权限/角色的用户可完成签收。
5. 存在自动化验证，能证明审批通过后流程推进到下一个真实节点，并留下可核查的任务/流程状态变化。
6. 存在自动化验证，能证明驳回路径可执行，并留下可核查的任务/流程状态变化。
7. 存在自动化验证，能证明未授权用户在至少一个 workflow 受保护接口上被 `PT_RESOURCE` / RBAC 拦截，返回预期拒绝结果。
8. 测试或脚本能证明本次联调依赖的 `PT_*` 数据与角色/机构映射是自洽的。
9. 全部新增/修改测试在本地可重复执行，并作为后续业务模块接入 Flowable 的回归基线。

## Assumptions Exposed + Resolutions
- 假设 1：只要引入了 Flowable starter，引擎就一定能跑。
  - 处理：被否定，必须补齐真实 BPMN 与部署/验证链路。
- 假设 2：做一个最小演示流就足以证明系统可用。
  - 假设 3：现有 `PT_*` 种子完全可直接用于联调。
  - 处理：不强假设，允许做最小必要微调以形成稳定 E2E 场景。

## Pressure-pass Findings
- Revisited answer: “补齐一个最小可运行流”
- Pressure question: 是压缩版验证流，还是贴近真实 `loan_approve_v1` 审批链？
- What changed: 范围从“最小可运行”被收紧为“保留最小联调范围，但流程语义需贴近真实业务链路”，直接影响 BPMN 设计、角色映射和测试数据策略。

## Brownfield Evidence vs Inference
### Evidence
- `bootstrap/src/main/resources/application.yml` 已启用 Flowable 基础配置：`history-level: audit`、`idm.enabled: false`、`database-schema-update: true`
- `workflow-center/src/main/java/com/bank/branch/platform/workflow/service/ProcessStartService.java` 使用 `RepositoryService` + `RuntimeService` + `TaskService` 驱动启动与首任务查询
- `workflow-center/src/main/java/com/bank/branch/platform/workflow/controller/TaskController.java` 提供待办/已办/详情/签收/审批/驳回/转交接口
- `workflow-center/src/main/java/com/bank/branch/platform/workflow/controller/WorkflowAdminController.java` 管理端接口使用 `@BizAuth(BizType.SYS_CONFIG, BizAction.CONFIG)`
- `auth-permission-center/src/main/java/com/bank/branch/platform/auth/security/interceptor/AuthorizationInterceptor.java` 明确了 `PT_RESOURCE -> RBAC -> @BizAuth/DataScope` 的鉴权顺序
- `docs/superpowers/sql/2026-04-10-pt-align-and-test-seed.sql` 已含 workflow 相关 `W_*` 资源及角色/范围映射
- `docs/schema/workflow-seed-v1.sql`、`data.sql` 已含 `loan_approve_v1` 相关候选人、表单、SLA 种子
- 当前 `workflow-center/src` 下未发现 BPMN 文件

### Inference
- 由于缺失 BPMN 文件，当前仓库状态下“真实提交并驱动流程”大概率尚不可直接完成
- 由于用户显式 E2E 列表未包含 `transfer` / `done`，本阶段将其视为延后项

## Technical Context Findings
- 最关键缺口不是 Flowable 依赖本身，而是“真实流程定义 + 可重复联调测试基线”缺失
- `TaskController` 用户侧接口本身不依赖 `@BizAuth`，但仍走 `PT_RESOURCE` 与当前用户上下文，因此权限验证必须覆盖资源授权层
- 管理端配置接口虽然现成存在，但已被明确排除在本轮范围外，可避免首轮联调过度扩张
- 现有文档与种子数据更偏向 `loan_approve_v1` 的真实业务链，可作为补齐 BPMN 与 E2E 场景的基座

## Recommended Handoff
首选进入 `$ralplan`，基于本规格产出实施 PRD 与测试规格，再进入执行。
建议调用：
- `$plan --consensus --direct .omx/specs/deep-interview-flowable-workflow-center-test.md`

## Residual Risks
- 当前尚未验证本地 MySQL/Redis/测试账号状态是否与文档种子完全一致
- 真实 `loan_approve_v1` 审批链需要在“贴近真实”与“首轮可交付”之间保持实现克制
- `PT_*` 权限数据若历史漂移较大，可能需要先做数据基线校准再执行 E2E
