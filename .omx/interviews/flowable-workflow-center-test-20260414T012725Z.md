# Deep Interview Transcript - flowable-workflow-center-test

- Profile: standard
- Context type: brownfield
- Final ambiguity: 14.3%
- Threshold: 20%
- Context snapshot: `.omx/context/flowable-workflow-center-test-20260414T005842Z.md`

## Condensed Transcript

### Round 1
- Target: outcome / scope
- Q: 当前仓库没有 BPMN 定义时，是只验证接口层，还是补齐最小可运行流程？
- A: 补齐一个最小可运行流
- Impact: 明确必须让 Flowable 引擎真实跑起来。

### Round 2
- Target: outcome / scope
- Q: 只证明能跑起来，还是做真实端到端联调？
- A: 并做真实端到端联调（启动待办签收/审批/驳回权限校验库表断言）
- Impact: 明确目标是引擎 + 接口 + 权限 + 数据断言的完整链路验证。

### Round 3
- Target: non-goals
- Q: 哪些第一阶段先不做？
- A: 管理端候选人/表单/SLA 配置接口、多业务流程、并行会签/复杂网关、前端页面联调，这些先不做
- Impact: 锁定首阶段只做单流程、用户侧链路验证。

### Round 4
- Target: decision boundaries
- Q: 复用现有业务语义，还是新建纯测试流程 key？
- A: 复用现有业务语义
- Impact: 选择沿用 `LOAN / loan_approve_v1` 的真实业务语义与既有权限上下文。

### Round 5
- Target: decision boundaries / pressure pass
- Q: 走 `LOAN / loan_approve_v1` 时，是压缩成演示版最小节点，还是贴近真实审批链？
- A: 贴近真实链路去补充
- Impact: 完成压力追问，确认真实性优先于最小改动。

### Round 6
- Target: decision boundaries
- Q: 是否允许为联调补齐最小必要的 `PT_*` 测试种子和测试账号/角色/机构绑定？
- A: 允许
- Impact: 允许为了稳定验证链路做最小必要测试数据校准，但不能改变业务含义。

## Readiness Gates
- Non-goals: resolved
- Decision boundaries: resolved
- Pressure pass: completed
