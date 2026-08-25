# 资产立项项目与加急申请表结构候选规格（V1）

> - 日期：2026-08-24
> - 状态：候选设计，待业务、架构与 DBA 审核
> - 归属模块：`business-application-center`
> - 文档性质：增量设计规格，不是现网 schema，也不是可执行 DDL
> - 依据：会议纪要、资产立项需求说明书、当前 `LOAN_APPLY` 代码/历史结构文档及 `yiti_test` 只读盘点

## 1. 设计边界与现状声明

本规格只描述资产立项 V1 的候选数据模型。结构变更须由 DBA 按审批结果在隔离的
`yiti_test` 中实施和验收，再决定是否进入目标库；本文不生成、不执行任何 DDL，也不
把候选字段描述为当前数据库已经存在的事实。

当前只读基线显示：`yiti_test.LOAN_APPLY` 仍为既有 18 列，尚不存在
`LOAN_URGENT_REQUEST`，也不存在 `LOAN_URGENT_REQUEST` 对应的流程数据。现有业务表的
审计列、逻辑删除和流程实例字段继续沿用现行约定；新表不跨模块建立物理外键。

本次采用两层模型：

1. `LOAN_APPLY` 追加项目概况和两个只读业务标签，承载申请当前态；
2. `LOAN_URGENT_REQUEST` 仅承载**中途加急**的独立申请、审批轨迹和幂等控制。

启动时加急不额外创建 `LOAN_URGENT_REQUEST`，而是在主申请提交时由主流程进入于行长审批分支。
中途加急创建独立申请并启动独立流程；只有该独立流程审批通过，才在同一事务内更新
`LOAN_APPLY.is_urgent=1` 和 `urgent_source=MID_PROCESS`。授信部只能读取主申请上的加急/重点标签，不能修改标签、发起加急或绕过流程，也不展示标签产生方式。

## 2. 目标数据关系

```text
LOAN_APPLY (1) ───────< LOAN_URGENT_REQUEST (0..N)
    │                              │
    │ 主流程业务键/状态               │ 中途加急独立流程业务键/状态
    │ is_urgent / is_key_project     │ requested_at_node/task 快照
    └─ 启动时加急走主流程于行长审批     └─ 审批通过后回写主表
```

关系采用逻辑外键：`LOAN_URGENT_REQUEST.loan_apply_id` 必须指向未删除的
`LOAN_APPLY.id`，但不在数据库层建立跨表物理外键，以保持现有模块边界和流程数据迁移的灵活性。

## 3. `LOAN_APPLY` 追加字段候选

以下字段均为对既有 `LOAN_APPLY` 的增量候选。字段名沿用现有下划线命名，金额单位统一为
人民币元，精度统一为 4 位小数；前端展示单位可另行换算，但不得改变落库单位。

| 字段 | 建议类型 | 空值/默认 | 业务语义 | 赋值与可变性 |
|---|---|---|---|---|
| `project_name` | `VARCHAR(200)` | 草稿可空；提交时建议必填 | 项目名称；不存客户全量信息 | 草稿阶段可改；主流程启动后冻结 |
| `project_total_investment` | `DECIMAL(20,4)` | 草稿可空；提交时建议必填 | 项目总投资额，单位元 | 必须为非负数；主流程启动后冻结 |
| `project_loan_amount` | `DECIMAL(20,4)` | 草稿可空；提交时建议必填 | 本申请所对应的项目贷款金额，单位元；具体口径仍需业务确认 | 必须为非负数；主流程启动后冻结 |
| `is_urgent` | `TINYINT(1)` | `NOT NULL DEFAULT 0` | 是否加急：`0=否`，`1=是` | 启动时加急在主流程提交前写为 `1`；中途加急仅独立流程审批通过后由服务端写为 `1`；一旦为 `1` 不回退为 `0` |
| `is_key_project` | `TINYINT(1)` | `NOT NULL DEFAULT 0` | 是否重点项目：`0=否`，`1=是` | 仅有权限的申请/审批业务方在允许阶段维护；主流程启动后冻结；授信部只读 |
| `urgent_source` | `VARCHAR(20)` | 可空 | 加急来源：`INITIATION`（启动时）或 `MID_PROCESS`（中途） | `is_urgent=0` 时必须为 `NULL`；`is_urgent=1` 时必须为两个枚举值之一；主流程启动后不可改 |

### 3.1 主表伪约束

以下约束是候选业务约束，实际实施前必须由 DBA 和业务确认字段口径：

```text
project_total_investment IS NULL OR project_total_investment >= 0
project_loan_amount IS NULL OR project_loan_amount >= 0
is_urgent IN (0, 1)
is_key_project IN (0, 1)
(is_urgent = 0 AND urgent_source IS NULL)
OR (is_urgent = 1 AND urgent_source IN ('INITIATION', 'MID_PROCESS'))
```

建议在 Service 层做提交校验，不把尚未确认的跨字段金额关系直接做成数据库 CHECK：

- `project_loan_amount <= project_total_investment` 在两者都表示同一项目、同一币种和同一统计口径时合理，可作为提交时硬校验候选；
- `project_loan_amount <= credit_amount <= project_total_investment` 暂不作为硬约束。`credit_amount` 可能表示本次授信额度，`project_loan_amount` 可能表示项目总融资需求，项目总投资还可能包含非授信资金，三者在不同业务口径下并不必然满足该链式关系；
- 在口径未冻结前，若三者同时有值，建议提交时做一致性提示并记录人工确认结果；待业务确认同口径后，再决定是否升级为 Service 层拒绝规则。无论如何，不应仅凭页面输入或数据库约束强行推断该关系。

### 3.2 主流程行为

#### 启动时加急

1. 申请仍处于允许编辑的草稿状态时，业务方选择加急，主表保存
   `is_urgent=1`、`urgent_source=INITIATION`；未选择时保持 `0/NULL`。
2. 提交主申请时，服务端再次校验当前用户、申请状态和标签一致性，并将加急标记作为流程变量传入主流程。
3. 主流程根据该变量进入于行长审批分支；该审批节点的正式 node key、候选人和办理 SLA 由
   `workflow-center` 配置，不在本表硬编码。
4. 启动时加急不写入 `LOAN_URGENT_REQUEST`，避免把主流程审批与中途独立审批混成一条状态链。

#### 中途加急

1. 主申请必须处于允许中途发起的审批节点；服务端从工作流查询接口取得真实当前节点和任务，不能信任前端传入的节点名。
2. 创建 `LOAN_URGENT_REQUEST`，记录发起时的节点 key 和 task id 快照，主表仍保持
   `is_urgent=0`、`urgent_source=NULL`。
3. 独立加急流程以 `LOAN_URGENT:{request_id}` 作为业务键；独立流程完成前，主流程原状态不被改写。
4. 独立流程审批通过时，服务端在一个事务内锁定加急申请和主申请，确认申请仍未应用，然后：
   - 更新 `LOAN_APPLY.is_urgent=1`；
   - 更新 `LOAN_APPLY.urgent_source=MID_PROCESS`；
   - 将 `LOAN_URGENT_REQUEST.status` 更新为 `APPROVED`，写入审批人/审批时间/意见。
5. 任一步骤失败则整体回滚；重复回调必须幂等，不能重复创建加急结果，也不能将来源覆盖为
   `INITIATION`。
6. 独立流程被驳回或撤回时，主表保持 `is_urgent=0`、`urgent_source=NULL`，申请表保留历史记录供审计。

## 4. `LOAN_URGENT_REQUEST` 表候选

该表只记录中途加急申请，不承担启动时加急的历史记录。所有长度是按当前 UUID、Flowable
标识和机构/员工编码习惯提出的候选值，最终以实际接口契约和 DBA 评估为准。

| 字段 | 建议类型 | 空值/默认 | 业务语义 |
|---|---|---|---|
| `id` | `VARCHAR(32)` | `NOT NULL`，应用生成 UUID（去连字符） | 加急申请主键 |
| `request_no` | `VARCHAR(100)` | `NOT NULL` | 加急申请编号；建议格式 `UR+yyyyMMdd+6位序号`，全表唯一 |
| `loan_apply_id` | `VARCHAR(32)` | `NOT NULL` | 逻辑关联 `LOAN_APPLY.id` |
| `requested_at_node_key` | `VARCHAR(64)` | `NOT NULL` | 发起中途加急时的真实主流程节点 key；不可由前端自由指定 |
| `requested_at_task_id` | `VARCHAR(64)` | 中途发起时 `NOT NULL` | 发起时的真实 Flowable user task id；用于防止节点漂移和重复发起 |
| `reason` | `VARCHAR(1000)` | `NOT NULL` | 加急原因；创建时必填，审批后不可修改 |
| `status` | `VARCHAR(20)` | `NOT NULL DEFAULT 'DRAFT'` | `DRAFT/IN_APPROVAL/APPROVED/REJECTED/CANCELLED` |
| `business_key` | `VARCHAR(100)` | 审批发起前可空，发起后必填 | 独立流程业务键，建议固定为 `LOAN_URGENT:{id}` |
| `process_instance_id` | `VARCHAR(64)` | 审批发起前可空，发起后必填 | 独立流程实例 ID |
| `requested_by` | `VARCHAR(32)` | `NOT NULL` | 加急申请人工号；从服务端当前用户取得 |
| `requested_org_id` | `VARCHAR(50)` | `NOT NULL` | 加急申请发起机构；从服务端当前用户/数据范围取得 |
| `requested_time` | `DATETIME` | `NOT NULL` | 加急申请时间；以服务端时间为准 |
| `approved_by` | `VARCHAR(32)` | 可空 | 独立流程最终审批人工号 |
| `approved_time` | `DATETIME` | 可空 | 独立流程审批通过并完成主表回写的时间 |
| `approval_comment` | `VARCHAR(1000)` | 可空 | 最终审批意见；敏感内容按审计及日志规范脱敏 |
| `version` | `INT` | `NOT NULL DEFAULT 0` | 乐观锁版本，配合服务端条件更新防止并发审批 |
| `created_by` | `VARCHAR(32)` | `NOT NULL` | 通用创建审计人工号 |
| `created_time` | `DATETIME` | `NOT NULL DEFAULT CURRENT_TIMESTAMP` | 通用创建审计时间 |
| `updated_by` | `VARCHAR(32)` | 可空 | 通用最后更新人工号 |
| `updated_time` | `DATETIME` | `NOT NULL DEFAULT CURRENT_TIMESTAMP`，更新时刷新 | 通用最后更新时间 |
| `deleted` | `TINYINT(1)` | `NOT NULL DEFAULT 0` | 逻辑删除：`0=未删除`，`1=已删除` |

### 4.1 加急申请状态语义

| 状态 | 进入条件 | 允许的下一步 | 主表 `LOAN_APPLY` 影响 |
|---|---|---|---|
| `DRAFT` | 服务端完成当前节点、task、客户和权限校验并保存草稿 | `IN_APPROVAL`、`CANCELLED` | 保持 `is_urgent=0/urgent_source=NULL` |
| `IN_APPROVAL` | 独立流程启动成功并回写业务键/实例 ID | `APPROVED`、`REJECTED`、`CANCELLED` | 保持原值，不提前打标签 |
| `APPROVED` | 独立审批通过且主表标签在同一事务中更新成功 | 终态 | `is_urgent=1`、`urgent_source=MID_PROCESS` |
| `REJECTED` | 独立流程明确驳回 | 终态 | 保持 `is_urgent=0/urgent_source=NULL` |
| `CANCELLED` | 发起人按权限撤回或系统按规则取消，未发生审批通过 | 终态 | 保持原值 |

`APPROVED` 表示“审批通过且已应用到主表”，不另设一个等待回写状态；依靠锁、条件更新和事务
保证审批完成与标签回写原子化。若将来采用异步事件回写，必须另行引入明确的待应用状态和补偿契约，不能复用本表语义。

### 4.2 加急申请伪约束

```text
status IN ('DRAFT', 'IN_APPROVAL', 'APPROVED', 'REJECTED', 'CANCELLED')
deleted IN (0, 1)
loan_apply_id 指向 LOAN_APPLY.id，且目标记录 deleted = 0
status IN ('IN_APPROVAL', 'APPROVED') 时 business_key IS NOT NULL
status IN ('IN_APPROVAL', 'APPROVED') 时 process_instance_id IS NOT NULL
status = 'APPROVED' 时 approved_by IS NOT NULL AND approved_time IS NOT NULL
status = 'IN_APPROVAL' 时 requested_at_task_id IS NOT NULL
```

业务层并发约束：

1. 同一 `loan_apply_id` 同时最多一条 `DRAFT` 或 `IN_APPROVAL` 的未删除中途加急申请；创建前锁定主申请并二次查询，不能只依赖前端禁用按钮。
2. 同一 `loan_apply_id` 最多一条 `APPROVED` 的中途加急申请；主表已经 `is_urgent=1` 时不允许再次创建。
3. `requested_at_node_key` 与 `requested_at_task_id` 必须来自同一次工作流查询；提交办理时重新确认 task 仍属于该主申请且仍处于允许节点。
4. `business_key` 和 `request_no` 全局唯一；流程启动失败时不能留下 `IN_APPROVAL` 的无实例孤儿记录，若出现异常须由事务回滚或进入受控补偿。
5. `deleted=1` 仅表示逻辑删除，不用于绕过上述活动申请的唯一性和审计查询规则。

## 5. 索引候选

索引名称是候选命名，不代表已在任何库中存在。索引需以真实列表查询、流程回调和数据量评估为依据；不要仅凭字段数量盲目增加宽索引。

### 5.1 `LOAN_APPLY` 新增字段相关索引

本次新增字段默认不单独建索引：

- `is_urgent`、`is_key_project` 是低基数字段，单列索引通常收益有限；
- 资产立项列表的主过滤仍以数据范围、状态、创建时间和申请编号为主；
- 若实际查询确认“按机构 + 加急标签 + 状态”是稳定高频场景，再评估复合索引
  `(owner_org_id, is_urgent, status, created_time)`，并用两种真实查询形态的执行计划确认收益。

### 5.2 `LOAN_URGENT_REQUEST` 索引候选

| 候选索引 | 字段 | 用途 | 备注 |
|---|---|---|---|
| `PRIMARY` | `id` | 详情、锁定单条申请 | 必须唯一 |
| `UK_URGENT_REQUEST_NO` | `request_no` | 编号查询和幂等防重 | 全表唯一；逻辑删除后仍保留历史编号 |
| `IDX_URGENT_LOAN_STATUS` | `loan_apply_id, status, deleted` | 查询某主申请的加急历史和活动申请 | 支持状态机及活动申请检查 |
| `IDX_URGENT_PROCESS_INSTANCE` | `process_instance_id` | 流程完成回调反查 | 草稿期为空不影响 |
| `IDX_URGENT_BUSINESS_KEY` | `business_key` | 独立流程业务键回查 | 发起后应唯一；是否做唯一索引待流程重试策略确认 |
| `IDX_URGENT_REQUESTED_BY_TIME` | `requested_by, requested_time` | 发起人历史列表 | 支持“我的加急申请” |
| `IDX_URGENT_ORG_STATUS_TIME` | `requested_org_id, status, requested_time` | 机构审批/审计查询 | 是否需要按实际授权查询保留 |
| `IDX_URGENT_TASK_NODE` | `requested_at_task_id, requested_at_node_key` | 节点任务关联和重复提交排查 | 优先按真实 task id 定位；不代替流程服务端校验 |

“活动申请唯一”属于带状态条件的唯一性，MySQL 常规唯一索引不能直接表达；候选实现为主申请行锁
加 Service 层条件检查。若 DBA 评估后采用生成列/活动标记实现条件唯一，必须补充独立评审，且不能改变历史记录可审计性。

## 6. 权限、展示与审计边界

### 6.1 授信部只读标签

授信部查询资产立项时只看到：

- `is_urgent` 的展示标签（加急/普通）；
- `is_key_project` 的展示标签（重点/普通）；

`urgent_source`、加急原因、审批意见和审批轨迹不向授信部普通列表与详情展示，仅供有明确授权的流程办理和审计角色查询。授信部不可通过列表、详情或接口修改标签，不可直接写 `LOAN_URGENT_REQUEST`，也不能通过篡改前端 payload 绕过 Service 层的流程校验。标签展示应服从资产立项本身的数据范围。

### 6.2 审计要求

- 主表标签变更必须记录业务审计动作，至少包含申请 ID、旧值、新值、来源、操作人、机构、时间和 traceId。
- 中途加急申请的创建、提交、审批通过、驳回、撤回、取消和异常补偿均应有独立审计动作；审计以
  `LOAN_URGENT_REQUEST.id` 和 `loan_apply_id` 双向可追溯。
- 审批通过审计必须能证明“独立流程通过”和“主表标签回写”属于同一事务结果；不能只记录前端按钮点击。
- `reason`、`approval_comment` 不进入普通请求日志；日志和导出按现有金额、账号、客户信息脱敏规则处理。

## 7. 数据迁移与发布顺序

### 7.1 实施前

1. 在 `yiti_test` 只读盘点既有 `LOAN_APPLY` 的字段、索引、数据量、引擎、字符集和锁等待情况，确认新增列/表名不存在冲突。
2. 由业务确认项目金额的单位、口径、必填时点、于行长审批分支和允许中途加急的节点白名单。
3. 由架构/工作流负责人确认独立流程定义、业务键格式、任务办理接口、完成回调和幂等语义。
4. 备份并在隔离环境演练恢复；测试数据不得连接生产会话、锁表、调度、消息或对象存储。

### 7.2 候选发布阶段

1. **扩展结构**：由 DBA 按审批结果给 `LOAN_APPLY` 增加 6 个字段，新增 `LOAN_URGENT_REQUEST` 及获批索引；不删除、不重命名现有列。
2. **旧数据安全初始化**：既有申请的 `is_urgent`、`is_key_project` 统一为 `0`，
   `urgent_source` 保持 `NULL`；项目名称和金额字段保持 `NULL`，不得根据旧文本、流程节点或人工猜测反推加急/重点状态。
3. **兼容应用**：先发布能读取空值和默认值的应用版本，功能开关保持关闭；验证实体、查询、详情和导出在新增列为空时不报错。
4. **启动时加急**：确认主流程于行长审批分支和变量契约后再开放；提交接口必须服务端重查标签和状态。
5. **中途加急**：确认独立流程、节点白名单、完成回调和主表事务回写后再开放；先用隔离测试数据验证并发与重试。
6. **观察期**：监控活动加急数量、孤儿流程、重复业务键、回写失败、审计缺失和授信部越权写入；异常时关闭功能开关并保留数据供诊断。

### 7.3 回滚/前向修复

- 若仅结构已扩展、功能尚未开启：优先回退应用版本并保留新增结构，避免无必要的破坏性删列/删表。
- 若已产生加急申请数据：优先关闭入口并采用前向修复；不得通过物理删除申请表或直接把已审批主表标签改回 `0` 来掩盖问题。
- 只有在停写、备份、恢复演练、数据归档和 DBA 二次审批均完成后，才可评估移除新增结构；移除不是默认回滚手段。
- 任何回滚都必须复核主表与加急申请表的对应关系、流程实例、业务键和审计日志，确保授信部读到的标签不与审批事实矛盾。

## 8. 验收清单

### 8.1 结构验收（隔离 `yiti_test`）

- [ ] 新增 6 个 `LOAN_APPLY` 字段的名称、类型、精度、默认值、可空性与本文获批版本一致。
- [ ] 新表包含主键、申请编号、主申请关联、节点/task 快照、原因、状态、流程关联、发起人/机构/时间、审批时间及通用审计字段。
- [ ] 新表使用与现有业务表一致的存储引擎、字符集和逻辑删除约定；未偷偷添加跨模块物理外键。
- [ ] `request_no`、活动申请规则、流程业务键和节点/task 关联的索引/服务端校验均已验收。
- [ ] 既有 `LOAN_APPLY` 行初始化为 `is_urgent=0`、`is_key_project=0`、`urgent_source=NULL`，项目字段不被猜测填充。
- [ ] 新增索引经真实列表、详情、流程回调和审计查询的执行计划确认，没有仅凭低基数字段添加单列索引。

### 8.2 业务与流程验收

- [ ] 普通启动：主流程不进入于行长审批分支，主表保持 `is_urgent=0/NULL`。
- [ ] 启动时加急：主流程进入于行长审批分支，主表为 `1/INITIATION`，不生成中途加急申请。
- [ ] 启动时重点：主表重点标签可展示；授信部可读但不可修改。
- [ ] 中途加急：只能从服务端确认的允许节点发起，并正确保存节点 key 与 task id。
- [ ] 中途加急审批中：主表标签不提前变化，独立申请状态为 `IN_APPROVAL`。
- [ ] 中途加急通过：同一事务内主表变为 `1/MID_PROCESS`，申请状态变为 `APPROVED`，审批人/时间/意见齐全。
- [ ] 中途加急驳回、撤回或取消：主表保持 `0/NULL`，申请历史可查询。
- [ ] 重复点击、重复回调、并发审批、流程启动失败和回写失败均可证明幂等或整体回滚。
- [ ] 授信部尝试修改标签或直接创建加急申请时，服务端拒绝并产生审计记录。

### 8.3 安全、审计与回滚验收

- [ ] 申请人、审批人、机构和数据范围均由服务端上下文取得并二次校验，不信任前端的 user/org/node/task 字段。
- [ ] 普通查询不泄露加急原因和审批意见；授权审批/审计角色可按业务范围追溯。
- [ ] 主表标签审计与中途加急申请审计可以按申请 ID、加急申请 ID、业务键、流程实例 ID 互相定位。
- [ ] 在隔离环境完成备份恢复和“结构已扩展但功能关闭”“已有数据后关闭入口”两种回滚演练。
- [ ] `project_loan_amount <= credit_amount <= project_total_investment` 的最终处理口径已由业务签字确认；未确认前只做提示/人工确认，不做错误硬拒绝。

## 9. 待确认事项

1. 于行长审批节点的正式 node key、候选人范围、办理时限和抄送范围。
2. 哪些主流程节点允许中途加急；节点完成、退回、撤回和并行任务场景如何处理。
3. `project_loan_amount` 是本次申请贷款金额、项目总融资需求，还是某种产品子项金额；该定义决定金额链式校验能否升级为硬规则。
4. 重点项目是否需要独立审批分支，还是只作为授信部和报表的标签；本版只建主表标签，不擅自增加重点项目流程。
5. 加急原因和审批意见的最大长度、敏感等级、查看角色及导出规则。
