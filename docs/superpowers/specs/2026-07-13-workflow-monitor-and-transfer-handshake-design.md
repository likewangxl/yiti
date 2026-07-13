# 审批流监控 + 转交待认领 设计文档

- **日期**：2026-07-13
- **模块**：workflow-center（后端，唯一可直接调用 Flowable 的模块）、xanzc_frontend（前端）、common-security（枚举）、auth-permission-center（数据范围/机构/角色，仅复用不改核心）
- **状态**：设计已评审，待写实施计划
- **关联**：`docs/superpowers/specs/2026-04-03-workflow-center-design.md`

---

## 1. 背景与现状差距

需求：为**秘书岗**提供"审批流监控"——查看**进行中**与**已完成**的审批流，并可**查看**与发起**转交**；转交为**两阶段待认领**语义。

现状调查结论（一手核对，含文件位置）：

| 事项 | 现状 | 差距 |
|---|---|---|
| 流程实例列表查询 | `ProcessController`/`ProcessMapController` 只能按单个 `processInstanceId`/`businessKey` 查（`ProcessQueryService.java`），无分页列表 | 监控列表页需**新建** Service+Controller |
| `BIZ_PROCESS_MAP` | 仅 `start_user` / `current_assignee` 两个工号列，**无机构列**，Mapper 无分页列表方法（`ddl-workflow.sql:14-37`、`BizProcessMapMapper.java`） | 需新增**参与机构快照** |
| 转交 | 同步单阶段：`TaskOperationService.transferTask` 直接 `taskService.setAssignee` 立即生效，无待认领/拒绝；`TaskTransferredEvent` 无人监听 | 需**重做为两阶段** |
| 待办/已办查询 | 基于 `assignee`/候选组，**无机构过滤参数**；`TaskRespDTO` 有发起人机构、**无当前处理人机构** | 监控查询需按机构过滤 + 补当前处理人机构 |
| 审计 | workflow-center **完全未用 `@AuditLog`**，转交仅落 Flowable comment | 补齐"高危操作单独审计"红线 |
| 前端 | `api/workflow.js` 只有 todo/done/detail/claim/approve/reject，**无 transfer**；无监控页 | 需新页 + inbox/outbox + api 封装 |

可**直接复用**的地基：

- **数据范围体系**：`DataScopeType`（`ORG`本机构 / `ORG_SUBTREE`本机构及下属 / `ALL`全部 / `WORKFLOW_PARTICIPANT` 等 7 值，`common-security`）；`PT_ROLE_BIZ_SCOPE`（角色×BizType×范围）；`@BizAuth` + `BizScopeApi.buildScopeContext()` 自动把范围（含 `orgSubtreeCodes`）注入 `DataScopeContext`（ThreadLocal）。
- **机构树**：`EXT_ORG_INFO(ORG_CODE, P_ID, 层级)` + `EXT_USER_ORG(USER_ID, ORG_CODE)`；`OrgApi.getUserMainOrg / getOrgSubtreeCodes / getOrgsByCodes`（批量，防 N+1）。
- **角色数据驱动**：`PT_ROLE.ROLE_CODE = R_XXXXXXXX`，**无硬编码"秘书岗/行长"**；`CurrentUserApi.isSystemAdmin() / getCurrentOrgCode() / getCurrentRoleCodes()`。
- **审计**：`@AuditLog` + `AuditLogAspect`（common-aop）→ `GovAuditLogHandler` → `audit_log` 表。

---

## 2. 需求与验收标准

1. **秘书岗**能看到「**经过自己机构**的审批流（进行中）」与「**自己机构完成**的审批流（已完成）」。
2. **分行行长、系统管理员**能查看**所有**审批流。
3. 监控中可**查看**流程详情（流程图 / 审批历史 / 节点进度）。
4. 秘书岗可**发起转交**，须**指定接收人**。
5. 接收人在**待处理工作台**看到该待认领任务。
6. 接收人**认领后**转交才生效；**认领后原办理人无法办理**。
7. 接收人**拒绝须填写拒绝理由**。
8. 原办理人在认领后**仍可在自己相关页面看到审批流信息，只是无法操作**（只读）。

---

## 3. 关键决策（已拍板）

| # | 决策 | 取值 | 说明 |
|---|---|---|---|
| D1 | "经过本机构"判定口径 | **参与机构（含发起）** | 任一实际经办人或发起人属于本机构，即视为经过；需维护参与机构快照。进行中/已完成同口径，仅 `process_status` 区分。 |
| D2 | 秘书岗可见层级 | **仅本机构（ORG）** | 复用 `DataScopeType.ORG`，不含下级。 |
| D3 | 转交锁定时机 | **发起即锁定** | 发起转交后原办理人立即只读；接收人认领正式接管，拒绝/撤回则解锁回退。无抢办竞态。 |
| D4 | 接收人范围 | **本机构 ∩ 该节点可办理者** | 服务端二次校验，不信任前端。 |
| D5 | 参与机构写入口径 | **仅在有具体受理人或流程发起时记 org** | 仅挂候选组、无人认领的不计入（避免"挂名即全组机构可见"）。 |
| D6 | 机构↔用户 | **1:1 映射** | 不考虑多机构用户，参与机构快照每人单机构，`getUserMainOrg` 单值。 |

架构选型（各有备选，已选推荐）：

- **参与机构** → 新建 `WF_PROCESS_ORG` 快照表（索引 `EXISTS` 查询）。备选运行时反查 `ACT_HI_TASKINST`+机构会造成列表页 N+1，弃用。
- **两阶段转交** → 新建 `WF_TASK_TRANSFER` 生命周期表。备选 Flowable 局部变量无法为工作台做分页列表/拒绝理由/只读可见，弃用。
- **权限** → 数据驱动（新增 BizType + 数据范围种子），**不**在代码里 `if role==秘书`。

---

## 4. 数据模型

### 4.1 `WF_PROCESS_ORG` — 参与机构快照（新表）

```sql
CREATE TABLE IF NOT EXISTS `WF_PROCESS_ORG` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `process_instance_id` varchar(64) NOT NULL COMMENT 'Flowable流程实例ID',
  `org_code` varchar(32) NOT NULL COMMENT '参与人主机构编码',
  `source` varchar(16) NOT NULL COMMENT '来源：START/ASSIGN/CLAIM/APPROVE/TRANSFER',
  `first_seen_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '首次记录时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pi_org` (`process_instance_id`,`org_code`),
  KEY `idx_org_pi` (`org_code`,`process_instance_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批流参与机构快照';
```

- 写入时机（D5）：`ProcessStartService`（发起人机构，source=START）、`TaskAssignmentListener` create 事件**当解析出具体受理人时**（source=ASSIGN）、`claim`（CLAIM）、`approve`（APPROVE）、转交认领（TRANSFER）。
- 幂等：`uk_pi_org` 保证同实例同机构只一条（`INSERT ... ON DUPLICATE KEY` 或先查后插）。
- **存量回填**：一次性 SQL，从 `ACT_HI_TASKINST.ASSIGNEE_ ⋈ EXT_USER_ORG` + `BIZ_PROCESS_MAP.start_user ⋈ EXT_USER_ORG` 生成（详见 §8）。

### 4.2 `WF_TASK_TRANSFER` — 转交生命周期（新表）

```sql
CREATE TABLE IF NOT EXISTS `WF_TASK_TRANSFER` (
  `id` varchar(32) NOT NULL COMMENT '主键',
  `process_instance_id` varchar(64) NOT NULL COMMENT '流程实例ID',
  `task_id` varchar(64) NOT NULL COMMENT '发起时的Flowable任务ID',
  `business_key` varchar(100) DEFAULT NULL COMMENT '业务键',
  `biz_type` varchar(50) DEFAULT NULL COMMENT '业务类型',
  `node_key` varchar(100) DEFAULT NULL COMMENT '节点定义KEY',
  `node_name` varchar(200) DEFAULT NULL COMMENT '节点名称',
  `from_emp_id` varchar(32) NOT NULL COMMENT '原办理人工号',
  `initiator_emp_id` varchar(32) NOT NULL COMMENT '发起转交者工号（秘书或本人）',
  `to_emp_id` varchar(32) NOT NULL COMMENT '接收人工号',
  `org_code` varchar(32) DEFAULT NULL COMMENT '发起时机构',
  `status` varchar(20) NOT NULL DEFAULT 'PENDING_ACCEPT' COMMENT 'PENDING_ACCEPT/ACCEPTED/REJECTED/CANCELLED',
  `transfer_reason` varchar(500) NOT NULL COMMENT '发起理由',
  `reject_reason` varchar(500) DEFAULT NULL COMMENT '拒绝理由',
  `initiated_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '发起时间',
  `decided_time` datetime DEFAULT NULL COMMENT '认领/拒绝/撤回时间',
  PRIMARY KEY (`id`),
  KEY `idx_task_status` (`task_id`,`status`),
  KEY `idx_to_status` (`to_emp_id`,`status`),
  KEY `idx_from` (`from_emp_id`),
  KEY `idx_pi` (`process_instance_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务转交待认领生命周期';
```

- **单活约束**：同一 `task_id` 至多一条 `PENDING_ACCEPT`。用应用层「查存在 + `UPDATE ... WHERE status='PENDING_ACCEPT'` 乐观流转」保证；发起前校验无进行中的转交。

### 4.3 枚举 / 种子

- `common-security` 新增 `BizType.WORKFLOW_MONITOR("WORKFLOW_MONITOR","审批流监控")`。
- `PT_RESOURCE`：登记新端点（§7），标注 `@BizAuth`。
- `PT_ROLE_BIZ_SCOPE` 种子：秘书岗角色 → (`WORKFLOW_MONITOR`,`ORG`)；分行行长角色 → (`WORKFLOW_MONITOR`,`ALL`)。（具体 ROLE_ID 由环境角色决定，作为可配置种子，管理员亦可在权限配置页调整。）
- `xanzc_frontend` 侧边栏：插入 `M_` 菜单行 + 角色绑定（侧边栏 DB 驱动，仅加路由不显示）。落库到 **yiti** 库。

---

## 5. 权限与数据范围

- 监控列表接口标 `@BizAuth(bizType=WORKFLOW_MONITOR, action=READ)`，鉴权管道自动 `buildScopeContext` 注入 `DataScopeContext`。
- 服务读取 `DataScopeContext.scope` 应用过滤：
  - `ALL` 或 `CurrentUserApi.isSystemAdmin()` → 不加机构过滤（行长 / 管理员）。
  - `ORG` → `EXISTS (SELECT 1 FROM WF_PROCESS_ORG wpo WHERE wpo.process_instance_id = m.process_instance_id AND wpo.org_code = :ctxOrgCode)`（秘书岗）。
  - `ORG_SUBTREE` → `wpo.org_code IN (:orgSubtreeCodes)`（本设计 D2 用不到，但保留支持）。
  - 未知/空范围 → **Fail-Close 拒绝**（返回空 + 记 warn）。
- 转交发起额外规则：发起人须为「该任务原办理人本人」**或**「对该流程有 `WORKFLOW_MONITOR` 且机构覆盖（`WF_PROCESS_ORG` 含发起人机构）的秘书」。

---

## 6. 转交状态机（发起即锁定）

```
[无]  --秘书/本人发起(reason必填)-->  PENDING_ACCEPT  --接收人认领-->  ACCEPTED (assignee=接收人)
                                        |   |
                        接收人拒绝(理由必填)|   | 发起人撤回(可选)
                                        v   v
                                  REJECTED / CANCELLED  --> 解锁，原办理人恢复可办
```

- **锁定实现**：锁 = 存在 `PENDING_ACCEPT` 记录。**不改 Flowable assignee**（仍为原办理人，天然可回退）。在 `TaskOperationService.approve/reject/claim` 入口加卫语句：该 `task_id` 有 `PENDING_ACCEPT` → 抛 `WF-409xx 任务转交待认领中，不可办理`；`getTaskDetail` 的 `canApprove/canReject/canClaim` 对应置 false。
- **接收人可见**：assignee 未变，接收人**不**经普通待办查询；其"待认领"列表由 `WF_TASK_TRANSFER WHERE to_emp_id=我 AND status=PENDING_ACCEPT` 单独驱动（落"待处理工作台"）。
- **认领（accept）**：单 `@Transactional`：`setAssignee(task, 接收人)` → 更新 `BIZ_PROCESS_MAP.current_assignee` → 写 `WF_PROCESS_ORG(接收人机构, TRANSFER)` → `status=ACCEPTED, decided_time`；`addComment(TRANSFER_ACCEPTED)`；发事件；`@AuditLog`。此后原办理人 `verifyAssignee` 失败→无法办理。
- **拒绝（decline）**：`reject_reason @NotBlank` → `status=REJECTED` → 锁消失，原办理人恢复；通知发起人 + 原办理人。
- **撤回（cancel，可选）**：发起人在待认领期可撤回 → `status=CANCELLED` → 解锁。
- **原办理人只读可见**：由 `WF_TASK_TRANSFER WHERE from_emp_id=我`（outbox）驱动只读入口，点进去复用监控详情，无操作按钮。

并发：`accept`/`decline`/`cancel` 均以 `UPDATE ... WHERE id=? AND status='PENDING_ACCEPT'` 影响行数判定，行数=0 视为状态已变，抛冲突。

---

## 7. 后端接口

| 方法 & 路径 | 作用 | 鉴权 / 审计 |
|---|---|---|
| `GET /api/workflow/monitor/processes?status=RUNNING\|COMPLETED&bizType=&keyword=&startedBy=&pageNo=&pageSize=` | 监控列表（分页 + 数据范围） | `@BizAuth(WORKFLOW_MONITOR, READ)` |
| 复用 `GET /api/workflow/processes/{id}` `/history` `/nodes` `/diagram` | 查看详情/流程图/历史/节点 | 已有 |
| `POST /api/workflow/monitor/tasks/{taskId}/transfer` `{toEmpId, reason}` | 发起转交（两阶段，PENDING_ACCEPT） | `@BizAuth` + `@AuditLog(TRANSFER, reasonRequired=true)` |
| `GET /api/workflow/transfers/inbox` | 接收人待认领列表 | 登录 |
| `POST /api/workflow/transfers/{id}/accept` | 认领接管 | `@AuditLog` |
| `POST /api/workflow/transfers/{id}/decline` `{reason}` | 拒绝（理由必填） | `@AuditLog` |
| `GET /api/workflow/transfers/outbox` | 原办理人"我转出的"（只读） | 登录 |
| `POST /api/workflow/transfers/{id}/cancel` | 发起人撤回（可选） | `@AuditLog` |

后端组件：

- 实体/Mapper：`WfProcessOrg` + `WfProcessOrgMapper`、`WfTaskTransfer` + `WfTaskTransferMapper`（MyBatis-Plus `BaseMapper`，自定义列表/EXISTS 查询走 XML）。
- Service：`ProcessMonitorService`（列表 + 数据范围 + 批量补当前处理人机构 `OrgApi.getOrgsByCodes`）、`TaskTransferService`（两阶段状态机）。
- Controller：`ProcessMonitorController`、`TaskTransferController`。
- 改造：`TaskOperationService.approve/reject/claim` 加"转交锁"卫语句；`getTaskDetail` 的 `RuntimeAccessDTO` 结合转交锁计算 `canXxx`。
- 监听：`ProcessStartService` / `TaskAssignmentListener` / claim / approve 处补 `WF_PROCESS_ORG` 写入。
- 现有 `POST /api/workflow/tasks/{taskId}/transfer`（单阶段）**废弃/下线**（前端未接线，语义被两阶段取代），或标注 `@Deprecated` 后移除，避免两套转交并存。

DTO：`ProcessMonitorItemDTO`（含 processInstanceId/businessKey/bizType/title/节点/当前处理人+机构/发起人+机构/发起时间/状态）、`TransferInitiateReqDTO`、`TransferDecisionReqDTO`、`TransferInboxItemDTO`、`TransferOutboxItemDTO`。

---

## 8. 参与机构快照写入与存量回填

- **增量**：在 §4.1 各时机写入（幂等 upsert）。
- **回填脚本**（一次性，落 yiti 库）：
  ```sql
  -- 历史办理人机构
  INSERT IGNORE INTO WF_PROCESS_ORG(id, process_instance_id, org_code, source, first_seen_time)
  SELECT <uuid>, t.PROC_INST_ID_, uo.ORG_CODE, 'BACKFILL', NOW()
  FROM ACT_HI_TASKINST t JOIN EXT_USER_ORG uo ON uo.USER_ID = t.ASSIGNEE_
  WHERE t.ASSIGNEE_ IS NOT NULL
  GROUP BY t.PROC_INST_ID_, uo.ORG_CODE;
  -- 发起人机构
  INSERT IGNORE INTO WF_PROCESS_ORG(...)
  SELECT ... FROM BIZ_PROCESS_MAP m JOIN EXT_USER_ORG uo ON uo.USER_ID = m.start_user
  GROUP BY m.process_instance_id, uo.ORG_CODE;
  ```
  （UUID 生成按项目现有方式；`INSERT IGNORE` 依赖 `uk_pi_org`。Flyway 已废弃，走 SQL 直接执行。）

---

## 9. 前端（xanzc_frontend）

- **新页** `system/workflow-monitor`（审批流监控），进行中/已完成 Tab + 过滤（业务类型 / 关键字 / 发起人）。列：流程·标题 / 业务类型 / 当前节点 / 当前处理人(+机构) / 发起人(+机构) / 发起时间 / 状态 / 操作。
  - **查看** → 抽屉：流程图 + 审批历史 + 节点进度（复用 `ProcessController`）。
  - **转交** → 弹窗：接收人选择器（**仅本机构可办理该节点者**）+ 理由 → 发起。
- **工作台"我的任务"** 增 **"待认领转交"** 区/Tab（inbox）：每行 认领 / 拒绝(填理由)。
- **"我转出的（只读）"**（outbox）：仅查看，无操作按钮。
- `api/workflow.js` 补：`monitorProcesses / transferInitiate / transferInbox / transferAccept / transferDecline / transferOutbox`（现无任何 transfer 封装）。
- 菜单：`M_` 菜单行 + 角色绑定入 yiti 库。

---

## 10. 审计

- 发起 / 认领 / 拒绝 / 撤回均标 `@AuditLog(action=..., resourceType="WORKFLOW_TASK", reasonRequired=true)`，经 `AuditLogAspect → GovAuditLogHandler → audit_log` 落库，满足"高危操作单独审计"红线（现转交完全无审计）。

---

## 11. 合理性验证

| 维度 | 结论 |
|---|---|
| 权限正确性 | 复用 RBAC + 数据范围；秘书=ORG / 行长=ALL / admin 直通；无硬编码角色；未知范围 Fail-Close。 |
| "经过本机构"可判定 | `WF_PROCESS_ORG` 物化参与机构，索引 `EXISTS`，规避逐行反查 N+1；存量有回填。 |
| 转交无竞态 | 发起即锁（PENDING_ACCEPT 存在即锁）；approve/reject/claim 卫语句；accept/decline/cancel 乐观 `UPDATE...WHERE status` 判定行数；同一 task 单活。 |
| 接收人边界 | 服务端二次校验 `to_emp ∈ 本机构 ∩ 节点候选/可办理者`。 |
| 原办理人只读可见 | outbox 驱动只读视图；认领后 `verifyAssignee` 天然挡写。 |
| 拒绝解锁 | REJECTED 移除锁；`reject_reason @NotBlank`。 |
| 审计合规 | 三/四端点 `@AuditLog` 落 `audit_log`。 |
| 一致性 | 认领 = setAssignee + 改 map + 写 org 单事务。 |
| 性能 <500ms | 分页 `BIZ_PROCESS_MAP` + 索引 EXISTS + 批量机构补全。 |
| 1:1 机构简化 | 参与机构每人单值，快照与选择器逻辑无多机构歧义（D6）。 |

---

## 12. 测试策略（TDD 红-绿-重构，*IT 走 failsafe）

1. **数据范围过滤**：秘书(ORG) 只见本机构参与流程；行长(ALL)/admin 见全部；未知范围拒绝。
2. **参与机构监听**：发起/认领/审批时按 D5 口径写入且幂等；仅候选组无受理人不写。
3. **转交状态机**：发起→PENDING_ACCEPT；认领→ACCEPTED 且 assignee 改；拒绝→REJECTED 且理由必填；撤回→CANCELLED。
4. **转交锁**：PENDING_ACCEPT 期间原办理人 approve/reject/claim 均被拒；拒绝/撤回后恢复。
5. **接收人校验**：非本机构或无节点资格的 toEmp 被拒。
6. **并发**：重复 accept / accept 与 decline 竞争，仅一次成功。
7. **回填**：回填脚本对既有实例产出正确参与机构集合。
8. **审计**：发起/认领/拒绝产生 `audit_log` 记录。

跨模块改动后遵守 `mvn clean install -DskipTests` 再 `mvn verify`（防 stale jar）。

---

## 13. 边界与口径（确认项）

- **D5**：参与机构仅在有具体受理人（claim/approve/transfer accept）或流程发起时记 org；仅挂候选组、无人认领的不计入。
- **D6**：机构↔用户 1:1，参与机构每人单值，`getUserMainOrg` 直接取。
- 已完成流程"自己机构完成"与进行中"经过自己机构"**同一参与谓词**，仅 `process_status` 区分 Tab。

---

## 14. 分期交付建议

1. **P1 后端地基**：两张表 + 回填 + 参与机构写入 + `BizType`/种子/资源登记。
2. **P2 监控查询**：`ProcessMonitorService/Controller` + 数据范围 + DTO；前端监控页（查看，复用详情）。
3. **P3 两阶段转交**：`TaskTransferService/Controller` + 锁卫语句 + inbox/outbox + 审计；前端转交弹窗 + 工作台待认领 + 我转出的。
4. **P4 收尾**：下线旧单阶段转交、菜单种子、端到端验证。

---

## 15. 风险 / 未决

- 旧单阶段 `POST /tasks/{taskId}/transfer` 下线需确认无其它调用方（现前端未接线，风险低）。
- `WF_PROCESS_ORG` 写入点分散（多处监听），需集中到一个 `recordParticipantOrg(pi, empId, source)` 工具方法，避免遗漏。
- 秘书岗/行长的 `PT_ROLE_BIZ_SCOPE` 种子需按目标环境真实 ROLE_ID 落库（或由管理员在权限配置页配置）。
