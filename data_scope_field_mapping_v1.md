# DataScopeType 字段映射、矩阵与谓词生成规则（V1）

> 本文件为 `data_scope_field_mapping_v1.md` 与 `data_scope_type_field_mapping_matrix_v1.md` 的合并主规范，用于指导后端在不同业务对象/数据表上如何把 `DataScopeType`（`SELF_CREATED` / `ORG` / `SELF_ASSIGNED` 等）映射到具体字段、生成统一 SQL 过滤条件，并在写操作前执行实体级校验。
>
> 本文件重点回答四类问题：
> 1. 某个业务对象的数据范围字段应该映射到哪里；
> 2. 某个 `DataScopeType` 的通用 SQL 谓词模板应该怎么写；
> 3. `ObjectMeta Registry`、`supportedScopes`、fail-fast 如何落地；
> 4. `SUPPORT_DEPT`、`WORKFLOW_PARTICIPANT`、客户历史只读旁路等特例如何收口。

---

## 1. 统一前提与权限上下文

### 1.1 口径对齐

- 登录用户唯一标识：`emp_id = PT_USER.USER_ID`
- 组织维度统一使用：`EXT_ORG_INFO / EXT_USER_ORG`；“部门/支行/二级分行”均视为组织节点
- 数据范围按 BizType 生效，`(ROLE_ID, BIZ_TYPE) -> DATA_SCOPE` 来自 `PT_ROLE_BIZ_SCOPE`
- 权限上下文统一使用 `CurrentUserContext(empId, mainOrgCode, roleIds, candidateGroupKeys, isSystemAdmin)`
- 候选组按“当前用户现有组身份”计算，不回放历史组身份快照
- V1 强约束：单用户单主机构；`EXT_USER_ORG` 如物理存在多条记录，权限上下文只允许 1 条有效主机构参与数据范围计算

### 1.2 字段映射优先级规则

对任意业务对象（表/逻辑视图），先抽象出一份 `ObjectMeta`，描述它能用哪些字段承载范围过滤：

| 元数据键          | 说明                                   | 典型字段                                            |
| ----------------- | -------------------------------------- | --------------------------------------------------- |
| `ownerOrgCol`     | 归属机构（组织节点）                   | `owner_org_id` / `org_id` / `org_code`              |
| `createdByCol`    | 创建人（工号）                         | `created_by`                                        |
| `assigneeCol`     | 当前执行人/被分配人（工号）            | `assignee_emp_id` / `assigned_emp_id`               |
| `selfCol`         | 对象字段=本人（不等价于 `created_by`） | `emp_id` / `maintainer_emp_id`                      |
| `businessKeyCol`  | 工作流/流程映射键                      | `business_key`                                      |
| `joinPolicy`      | 无法纯字段过滤时的 join/exists 策略    | `TOUCH_LOG -> TOUCH_TASK`、`CUSTOMER -> CUST_CLAIM` |
| `supportedScopes` | 对象显式支持的 `DataScopeType` 集合    | `[SELF_CREATED, ORG, ...]`                          |
| `viewBizType`     | 同一物理表下的逻辑视图业务域           | `SUPPORT` / `SUPPORT_DEPT`                          |

随后按 `DataScopeType` 映射为统一谓词：

| DataScopeType          | 读取过滤字段优先级                                 | 典型 where 形态                               |
| ---------------------- | -------------------------------------------------- | --------------------------------------------- |
| `SELF_CREATED`         | `createdByCol`，必须显式配置                       | `t.created_by = :empId`                       |
| `SELF`                 | `selfCol`，必须显式配置                            | `t.emp_id = :empId`                           |
| `SELF_ASSIGNED`        | `assigneeCol`，或 join 到父对象后用父对象 assignee | `t.assignee_emp_id = :empId` / `exists (...)` |
| `ORG`                  | `ownerOrgCol`，或 join 到归属关系表后用其 org 字段 | `t.owner_org_id = :orgCode` / `exists (...)`  |
| `ORG_SUBTREE`          | `ownerOrgCol + orgSubtree`                         | `t.owner_org_id in (:orgSubtree)`             |
| `ALL`                  | 无过滤                                             | `1=1`                                         |
| `WORKFLOW_PARTICIPANT` | `businessKeyCol + 流程参与者判定`                  | `exists (select 1 from biz_process_map ...)`  |

> 核心点：不要让“某个 `DataScopeType` 默认能套到所有表”。必须依赖 `ObjectMeta Registry` 明确哪些字段存在、该对象支持哪些 scope；未声明就 fail-fast，不能默认放宽。

---

## 2. DataScopeType 语义（统一）

| DataScopeType          | 语义                                                 | 典型适用对象                       |
| :--------------------- | :--------------------------------------------------- | :--------------------------------- |
| `SELF_CREATED`         | 仅本人创建/填报的数据                                | 线索、申请单、配置草稿             |
| `SELF`                 | 仅“对象字段=本人”的数据，不等价于 `created_by`       | 通知箱、通讯录本人维护、维护人视角 |
| `SELF_ASSIGNED`        | 仅分配给本人的任务/待办/支持单                       | 触达任务、中场支持承接、流程待办   |
| `ORG`                  | 本组织节点数据                                       | 认领关系、本机构客户、本机构任务   |
| `ORG_SUBTREE`          | 本组织节点及下属节点数据                             | 负责人视图、机构管理视图、报表     |
| `ALL`                  | 全量数据                                             | 运维、资财、系统配置               |
| `WORKFLOW_PARTICIPANT` | 流程参与者可读（发起人、当前或历史处理人、候选范围） | 流程类业务单据的只读查询           |

补充口径：

- `READ / LIST / EXPORT` 直接使用 `DATA_SCOPE` 生成谓词
- `WRITE / DELETE / TRANSFER` 在命中 `DATA_SCOPE` 后，还必须叠加状态守卫、实体归属校验和业务规则
- `APPROVE / REJECT` 在命中 `DATA_SCOPE` 后，还必须以 Flowable runtime task 的办理权为准
- `IMPORT / EXECUTE / EXECUTE_SQL / CONFIG / RECALC / JOB_TRIGGER / PERMISSION_CHANGE` 不能仅依赖普通对象范围放行，必须依赖动作授权、业务守卫与审计
- `WORKFLOW_PARTICIPANT` 通常无法仅靠“业务表字段”完成过滤，需要结合 `biz_process_map` 与 `WorkflowParticipantService`

---

## 3. ObjectMeta 与核心字段字典

### 3.1 核心字段约定（V1 强约束）

核心业务表建议都具备：

- `owner_org_id`：归属组织节点（`EXT_ORG_INFO.ORG_CODE`）
- `created_by`：创建人（`emp_id`）
- `updated_by / updated_time`：更新审计
- 任务类对象增加 `assignee_emp_id / assigned_emp_id`
- 流程类对象增加 `business_key`

按对象不同还会补充：

- 触达任务：`assignee_emp_id`
- 中场支持：`assigned_emp_id`、`dispatch_emp_id`、`support_dept_id`
- 流程映射：`biz_process_map.start_user / current_assignee / candidate_groups`

### 3.2 `ObjectMeta` 建议模型

```java
record ObjectMeta(
    String objectKey,
    String tableName,
    String ownerOrgCol,
    String createdByCol,
    String assigneeCol,
    String selfCol,
    String businessKeyCol,
    String joinPolicyKey,
    Set<DataScopeType> supportedScopes,
    String viewBizType
) {}
```

约束规则：

- 每个对象必须显式声明 `supportedScopes`
- 命中未声明的 scope 时必须 fail-fast
- 同一物理表可以注册为多个逻辑对象，但必须用不同 `viewBizType` 区分，例如 `SUPPORT` 与 `SUPPORT_DEPT`
- `CUSTOMER`、`TOUCH_LOG` 这类不能纯字段过滤的对象，必须通过 `joinPolicyKey` 显式声明 join/exists 规则

### 3.3 V1 业务对象字段字典

#### 3.3.1 客户线索（`LEAD` / `cust_lead`）

| Key               | Value                                                        |
| ----------------- | ------------------------------------------------------------ |
| `tableName`       | `cust_lead`                                                  |
| `ownerOrgCol`     | `cust_lead.owner_org_id`                                     |
| `createdByCol`    | `cust_lead.created_by`                                       |
| `businessKeyCol`  | `cust_lead.business_key`                                     |
| `supportedScopes` | `[SELF_CREATED, ORG, ORG_SUBTREE, WORKFLOW_PARTICIPANT, ALL]` |

#### 3.3.2 客户可见性（`CUSTOMER` / `cust_master` + `cust_claim`）

| Key               | Value                                       |
| ----------------- | ------------------------------------------- |
| `tableName`       | `cust_master`                               |
| `joinPolicyKey`   | `customer-claim-visible`                    |
| `ownerOrgCol`     | `cust_claim.org_id`（在 exists 子句中使用） |
| `supportedScopes` | `[ORG, ORG_SUBTREE, ALL]`                   |

> `cust_master.owner_org_id` 只表示客户来源机构，不承载可见性；V1 正式冻结为通过 `cust_claim` 的有效认领关系表达客户可见性。

#### 3.3.3 机构认领关系（`CUST_CLAIM` / `cust_claim`）

| Key               | Value                                         |
| ----------------- | --------------------------------------------- |
| `tableName`       | `cust_claim`                                  |
| `ownerOrgCol`     | `cust_claim.org_id`                           |
| `createdByCol`    | `cust_claim.claimed_by`                       |
| `selfCol`         | `cust_claim.maintainer_emp_id`                |
| `supportedScopes` | `[SELF_CREATED, SELF, ORG, ORG_SUBTREE, ALL]` |

#### 3.3.4 触达任务（`TOUCH_TASK` / `touch_task`）

| Key               | Value                                                        |
| ----------------- | ------------------------------------------------------------ |
| `tableName`       | `touch_task`                                                 |
| `ownerOrgCol`     | `touch_task.org_id`                                          |
| `assigneeCol`     | `touch_task.assignee_emp_id`                                 |
| `businessKeyCol`  | `touch_task.business_key`                                    |
| `supportedScopes` | `[SELF_ASSIGNED, ORG, ORG_SUBTREE, WORKFLOW_PARTICIPANT, ALL]` |

#### 3.3.5 触达日志（`TOUCH_LOG` / `touch_log` + `touch_task`）

| Key               | Value                                          |
| ----------------- | ---------------------------------------------- |
| `tableName`       | `touch_log`                                    |
| `joinPolicyKey`   | `touch-log-task-scope`                         |
| `ownerOrgCol`     | `touch_task.org_id`（通过 join 使用）          |
| `assigneeCol`     | `touch_task.assignee_emp_id`（通过 join 使用） |
| `supportedScopes` | `[SELF_ASSIGNED, ORG, ORG_SUBTREE, ALL]`       |

> 日志权限以任务为准，不建议单独按 `touch_log.owner_org_id` 判权。

#### 3.3.6 中场支持申请（发起方视图 `SUPPORT` / `support_request`）

| Key               | Value                                                        |
| ----------------- | ------------------------------------------------------------ |
| `tableName`       | `support_request`                                            |
| `viewBizType`     | `SUPPORT`                                                    |
| `ownerOrgCol`     | `support_request.owner_org_id`                               |
| `createdByCol`    | `support_request.created_by`                                 |
| `assigneeCol`     | `support_request.assigned_emp_id`                            |
| `businessKeyCol`  | `support_request.business_key`                               |
| `supportedScopes` | `[SELF_CREATED, ORG, ORG_SUBTREE, WORKFLOW_PARTICIPANT, ALL]` |

#### 3.3.7 中场支持申请（承接方视图 `SUPPORT_DEPT` / `support_request`）

| Key               | Value                                                        |
| ----------------- | ------------------------------------------------------------ |
| `tableName`       | `support_request`                                            |
| `viewBizType`     | `SUPPORT_DEPT`                                               |
| `ownerOrgCol`     | `support_request.support_dept_id`                            |
| `assigneeCol`     | `support_request.assigned_emp_id`                            |
| `businessKeyCol`  | `support_request.business_key`                               |
| `supportedScopes` | `[SELF_ASSIGNED, ORG, ORG_SUBTREE, WORKFLOW_PARTICIPANT, ALL]` |

> `SUPPORT` 与 `SUPPORT_DEPT` 必须作为两个逻辑对象注册，不能把 `owner_org_id` 与 `support_dept_id` 混在一个 BizType 下判权。

#### 3.3.8 资产投放申请（`LOAN` / `loan_apply`）

| Key               | Value                                                        |
| ----------------- | ------------------------------------------------------------ |
| `tableName`       | `loan_apply`                                                 |
| `ownerOrgCol`     | `loan_apply.owner_org_id`                                    |
| `createdByCol`    | `loan_apply.created_by`                                      |
| `businessKeyCol`  | `loan_apply.business_key`                                    |
| `supportedScopes` | `[SELF_CREATED, ORG, ORG_SUBTREE, WORKFLOW_PARTICIPANT, ALL]` |

> `LOAN` 的办理权通常不依赖业务表 `assigneeCol`，以 Flowable runtime task 的 `assignee / claim` 为准。

#### 3.3.9 通知（`NOTICE` / `user_notification`）

| Key               | Value                      |
| ----------------- | -------------------------- |
| `tableName`       | `user_notification`        |
| `selfCol`         | `user_notification.emp_id` |
| `supportedScopes` | `[SELF]`                   |

#### 3.3.10 审计日志（`AUDIT` / `audit_log`）

| Key               | Value       |
| ----------------- | ----------- |
| `tableName`       | `audit_log` |
| `supportedScopes` | `[ALL]`     |

> 审计日志不建议走普通 `DataScopeType`；统一由系统管理/审计权限访问，再叠加审计查询条件。

#### 3.3.11 流程映射（`WORKFLOW_PARTICIPANT` / `biz_process_map` + Flowable 7.x）

| Key               | Value                              |
| ----------------- | ---------------------------------- |
| `tableName`       | `biz_process_map`                  |
| `businessKeyCol`  | `biz_process_map.business_key`     |
| `assigneeCol`     | `biz_process_map.current_assignee` |
| `supportedScopes` | `[WORKFLOW_PARTICIPANT]`           |

> `WORKFLOW_PARTICIPANT` 不是纯字段映射问题；它依赖 Flowable runtime task、历史 assignee、history identity links 与候选组交集，必须通过统一服务判定。

---

## 4. 按业务对象的字段映射与写前校验

> 本章按“业务对象”组织，侧重对象差异与写前校验。通用 SQL 模板见第 5 章。

### 4.1 客户线索（`cust_lead`）

关键字段：

- `cust_lead.owner_org_id`
- `cust_lead.created_by`
- `cust_lead.business_key`

| Scope                  | SQL 谓词（示例）                                             |
| :--------------------- | :----------------------------------------------------------- |
| `SELF_CREATED`         | `cust_lead.created_by = :empId`                              |
| `ORG`                  | `cust_lead.owner_org_id = :orgCode`                          |
| `ORG_SUBTREE`          | `cust_lead.owner_org_id in (:orgSubtree)`                    |
| `ALL`                  | `1=1`                                                        |
| `WORKFLOW_PARTICIPANT` | `exists (select 1 from biz_process_map m where m.business_key = cust_lead.business_key and isParticipant(:empId, m, flowable))` |

写前校验建议：

- 先按主键取 `created_by / owner_org_id / status`
- `status != DRAFT` 禁止直接编辑
- 再按 `DATA_SCOPE + ActionGuard` 校验 `created_by / owner_org_id`

### 4.2 客户主数据与详情（`cust_master` + 客户详情历史只读特例）

关键字段：

- 认领关系：`cust_claim.org_id`
- 来源属性：`cust_master.owner_org_id`

冻结口径：

- 客户列表（`CUSTOMER`）一律按 `cust_claim` 的有效认领关系过滤
- 客户详情基础可见性一律按 `cust_claim` 的有效认领关系校验
- `cust_master.owner_org_id` 不用于列表过滤、详情基础可见性、导出过滤

| Scope         | 客户列表 SQL 谓词（推荐）                                    |
| :------------ | :----------------------------------------------------------- |
| `ORG`         | `exists (select 1 from cust_claim cc where cc.cust_id = c.id and cc.status = 'CLAIMED' and cc.org_id = :orgCode)` |
| `ORG_SUBTREE` | `exists (select 1 from cust_claim cc where cc.cust_id = c.id and cc.status = 'CLAIMED' and cc.org_id in (:orgSubtree))` |
| `ALL`         | `1=1`                                                        |

客户详情历史只读特例：

1. 先做基础可见性校验
2. 再允许独立的 `history` 只读接口按 `cust_id` 拉取跨机构历史
3. 禁止通过普通详情接口参数旁路，例如 `includeAllHistory=true`
4. 禁止导出、编辑、转交、办理

### 4.3 机构认领关系（`cust_claim`）

关键字段：

- `cust_claim.org_id`
- `cust_claim.claimed_by`
- `cust_claim.maintainer_emp_id`

| Scope          | SQL 谓词（示例）                        |
| :------------- | :-------------------------------------- |
| `SELF_CREATED` | `cust_claim.claimed_by = :empId`        |
| `SELF`         | `cust_claim.maintainer_emp_id = :empId` |
| `ORG`          | `cust_claim.org_id = :orgCode`          |
| `ORG_SUBTREE`  | `cust_claim.org_id in (:orgSubtree)`    |
| `ALL`          | `1=1`                                   |

写前校验：

- 取消认领/转交维护人前必须校验 `cust_claim.org_id` 命中 `DATA_SCOPE`
- 取消认领还要校验本机构范围内无在途流程（触达/支持/投放）

### 4.4 触达任务（`touch_task`）

关键字段：

- `touch_task.org_id`
- `touch_task.assignee_emp_id`
- `touch_task.business_key`

| Scope                  | SQL 谓词（示例）                                             |
| :--------------------- | :----------------------------------------------------------- |
| `SELF_ASSIGNED`        | `touch_task.assignee_emp_id = :empId`                        |
| `ORG`                  | `touch_task.org_id = :orgCode`                               |
| `ORG_SUBTREE`          | `touch_task.org_id in (:orgSubtree)`                         |
| `ALL`                  | `1=1`                                                        |
| `WORKFLOW_PARTICIPANT` | `exists (select 1 from biz_process_map m where m.business_key = touch_task.business_key and isParticipant(:empId, m, flowable))` |

写前校验：

- 触达成功/取消、日志/照片上传必须满足 `touch_task.assignee_emp_id = 当前用户`
- 日志/照片提交必须做幂等键校验

### 4.5 触达日志（`touch_log`）

关键字段：

- `touch_log.touch_task_id`
- `touch_log.owner_org_id`
- `touch_log.created_by`

建议映射：通过 `touch_task` 进行权限判断，不单独依赖 `touch_log.owner_org_id`。

| Scope               | SQL 谓词（示例）                                             |
| :------------------ | :----------------------------------------------------------- |
| `SELF_ASSIGNED`     | `exists (select 1 from touch_task t where t.id = touch_log.touch_task_id and t.assignee_emp_id = :empId)` |
| `ORG / ORG_SUBTREE` | `exists (select 1 from touch_task t where t.id = touch_log.touch_task_id and t.org_id = :orgCode / in (:orgSubtree))` |
| `ALL`               | `1=1`                                                        |

### 4.6 中场支持申请（发起方视图，`SUPPORT` / `support_request`）

关键字段：

- `support_request.owner_org_id`
- `support_request.created_by`
- `support_request.dispatch_emp_id`
- `support_request.assigned_emp_id`
- `support_request.support_dept_id`
- `support_request.business_key`

| Scope                  | SQL 谓词（示例）                                             |
| :--------------------- | :----------------------------------------------------------- |
| `SELF_CREATED`         | `support_request.created_by = :empId`                        |
| `ORG`                  | `support_request.owner_org_id = :orgCode`                    |
| `ORG_SUBTREE`          | `support_request.owner_org_id in (:orgSubtree)`              |
| `WORKFLOW_PARTICIPANT` | `exists (select 1 from biz_process_map m where m.business_key = support_request.business_key and isParticipant(:empId, m, flowable))` |
| `ALL`                  | `1=1`                                                        |

发起侧说明：

- `SUPPORT` 仅承载发起方查询/发起语义
- 发起机构负责人查看数据时，也按 `owner_org_id` 的 `ORG / ORG_SUBTREE` 判定
- 派单节点虽然发生在同一物理表上，但其承接视图必须切换为 `SUPPORT_DEPT`

### 4.7 中场支持申请（承接方视图，`SUPPORT_DEPT` / `support_request`）

关键字段：

- `support_request.support_dept_id`
- `support_request.assigned_emp_id`
- `support_request.dispatch_emp_id`
- `support_request.business_key`

| Scope                  | SQL 谓词（示例）                                             |
| :--------------------- | :----------------------------------------------------------- |
| `SELF_ASSIGNED`        | `support_request.assigned_emp_id = :empId`                   |
| `ORG`                  | `support_request.support_dept_id = :orgCode`                 |
| `ORG_SUBTREE`          | `support_request.support_dept_id in (:orgSubtree)`           |
| `WORKFLOW_PARTICIPANT` | `exists (select 1 from biz_process_map m where m.business_key = support_request.business_key and isParticipant(:empId, m, flowable))` |
| `ALL`                  | `1=1`                                                        |

承接侧说明：

- 中场支持秘书与承接人员使用 `SUPPORT_DEPT` 判权
- `ORG / ORG_SUBTREE` 一律映射到 `support_dept_id`
- 派单节点校验要点：`dispatch_emp_id` 必须为当前节点处理人，接收人必须属于 `support_dept_id`

### 4.8 资产投放申请（`loan_apply`）

关键字段：

- `loan_apply.owner_org_id`
- `loan_apply.created_by`
- `loan_apply.business_key`

| Scope                  | SQL 谓词（示例）                                             |
| :--------------------- | :----------------------------------------------------------- |
| `SELF_CREATED`         | `loan_apply.created_by = :empId`                             |
| `ORG / ORG_SUBTREE`    | `loan_apply.owner_org_id = :orgCode` / `in (:orgSubtree)`    |
| `WORKFLOW_PARTICIPANT` | `exists (select 1 from biz_process_map m where m.business_key = loan_apply.business_key and isParticipant(:empId, m, flowable))` |
| `ALL`                  | `1=1`                                                        |

审批办理写前校验：

- 以流程权限为主，当前节点 assignee/claim 后办理
- 业务表字段仅允许修改“节点可编辑区字段”

### 4.9 流程映射（`biz_process_map` + Flowable 7.x）

关键字段：

- `biz_process_map.business_key`
- `biz_process_map.process_instance_id`
- `biz_process_map.start_user`
- `biz_process_map.current_assignee`
- `biz_process_map.candidate_groups`

`WORKFLOW_PARTICIPANT` 推荐判定：

1. `start_user == empId`
2. `current_assignee == empId`
3. `HistoryService` 查询到历史 assignee 包含 `empId`
4. `candidate_groups` 与当前用户 `groupKey` 有交集

实现要求：

- 办理权优先以 Flowable runtime task 为准
- 候选组待办查询必须显式组合 `taskAssignee(empId)`、`taskCandidateUser(empId)`、`taskCandidateGroupIn(candidateGroups)`
- 若依赖历史参与者 / history identity links 判定，则 Flowable history level 必须不低于 `audit`
- 不建议业务模块直接联查 `ACT_RU_* / ACT_HI_*`
- 统一通过 `workflow-center` 或 `WorkflowParticipantService` 封装

### 4.10 通知（`user_notification`）

关键字段：`user_notification.emp_id`

| Scope  | SQL 谓词（示例）                    |
| :----- | :---------------------------------- |
| `SELF` | `user_notification.emp_id = :empId` |

### 4.11 审计日志（`audit_log`）

建议口径：

- 仅 `SYS_CONFIG` / 系统管理员访问
- 读范围默认 `ALL`
- 再按审计查询条件过滤，不走普通对象级 `DataScopeType`

---

## 5. 按 DataScopeType 的字段映射与 SQL 模板

> 下文 SQL 仅展示核心谓词形态；实际实现请使用参数化（MyBatis `#{}` / JPA Criteria / QueryDSL），不要字符串拼接。

### 5.1 `SELF_CREATED`（本人创建 / 填报）

字段映射：

- `cust_lead.created_by`
- `support_request.created_by`
- `loan_apply.created_by`
- `cust_claim.claimed_by`

典型 where：

```sql
-- LEAD
where l.created_by = :empId

-- SUPPORT
where s.created_by = :empId

-- LOAN
where a.created_by = :empId
```

写前校验建议：

1. 先查 `created_by / owner_org_id / status`
2. 先过状态守卫
3. 再做 `created_by == empId` 校验

### 5.2 `SELF`（对象字段=本人，不等价 `created_by`）

字段映射：

- `user_notification.emp_id`
- `cust_claim.maintainer_emp_id`
- 其他“个人数据”必须显式提供 `emp_id` 或等价字段

典型 where：

```sql
-- NOTICE
where n.emp_id = :empId

-- CUST_CLAIM
where cc.maintainer_emp_id = :empId
```

### 5.3 `SELF_ASSIGNED`（分配给我 / 我能办理）

字段映射：

- `touch_task.assignee_emp_id`
- `support_request.assigned_emp_id`

典型 where：

```sql
-- TOUCH_TASK
where t.assignee_emp_id = :empId

-- SUPPORT_DEPT
where s.assigned_emp_id = :empId
```

派生对象建议通过父对象判断：

```sql
-- TOUCH_LOG 通过任务做权限判断
where exists (
  select 1
  from touch_task t
  where t.id = l.touch_task_id
    and t.assignee_emp_id = :empId
)
```

流程办理口径：

- “能办理”优先用 runtime task 的 `assignee == empId`
- 不建议仅用业务表字段推导办理权

### 5.4 `ORG`（本机构）

字段映射：

- `cust_lead.owner_org_id`
- `support_request.owner_org_id`
- `support_request.support_dept_id`（承接视图）
- `loan_apply.owner_org_id`
- `touch_task.org_id`
- `cust_claim.org_id`
- `CUSTOMER` 通过 `exists cust_claim.org_id = :orgCode`

典型 where：

```sql
-- LEAD
where l.owner_org_id = :orgCode

-- TOUCH_TASK
where t.org_id = :orgCode

-- CUSTOMER
where exists (
  select 1
  from cust_claim cc
  where cc.cust_id = c.id
    and cc.status = 'CLAIMED'
    and cc.org_id = :orgCode
)
```

### 5.5 `ORG_SUBTREE`（本机构及下属机构）

字段映射同 `ORG`，差异仅为 `orgCode -> orgSubtree`。

典型 where：

```sql
-- LEAD
where l.owner_org_id in (:orgSubtree)

-- CUSTOMER
where exists (
  select 1
  from cust_claim cc
  where cc.cust_id = c.id
    and cc.status = 'CLAIMED'
    and cc.org_id in (:orgSubtree)
)
```

`orgSubtree` 获取建议：

```java
Set<String> resolveOrgSubtree(String orgCode) {
  // EXT_ORG_INFO: ORG_CODE, P_ID
  // V1 可使用应用内 BFS/DFS，并按 orgCode 做缓存
}
```

### 5.6 `ALL`（全量）

字段映射：无。

典型 where：

```sql
where 1=1
```

### 5.7 `WORKFLOW_PARTICIPANT`（流程参与者可读）

字段映射：

- `cust_lead.business_key`
- `touch_task.business_key`
- `support_request.business_key`
- `loan_apply.business_key`

典型 where：

```sql
where exists (
  select 1
  from biz_process_map m
  where m.business_key = t.business_key
    and is_participant(:empId, m.business_key) = true
)
```

参与者判定（伪代码）：

```java
boolean isParticipant(String empId, String businessKey) {
  if (bizProcessMap.startUser(businessKey).equals(empId)) return true;
  if (bizProcessMap.currentAssignee(businessKey).equals(empId)) return true;
  if (history.anyAssignee(businessKey, empId)) return true;
  if (candidateGroupsIntersect(businessKey, resolveCandidateGroups(empId))) return true;
  return false;
}
```

补充约束：

1. 办理权优先以 Flowable runtime task 的 `assignee / claim` 结果为准
2. 若依赖历史 assignee / history identity links，则 Flowable history level 在 V1 中必须不低于 `audit`
3. 不建议在业务 SQL 中直接联查 `ACT_RU_* / ACT_HI_*`
4. 候选组交集按“当前用户现有组身份”判定

---

## 6. 统一 where 生成器、写前校验与 fail-fast

### 6.1 `ObjectMeta Registry` 与统一读谓词

```java
record ObjectMeta(
    String name,
    String tableAlias,
    String ownerOrgCol,
    String createdByCol,
    String assigneeCol,
    String selfCol,
    String businessKeyCol,
    Set<DataScopeType> supportedScopes,
    String viewBizType,
    JoinPolicy joinPolicy
) {}

interface JoinPolicy {
  Predicate build(DataScope scope, DataScopeType type);
}

Predicate buildReadPredicate(ObjectMeta meta, DataScope scope) {
  if (!meta.supportedScopes().contains(scope.type())) {
    throw new IllegalStateException("Scope not supported for " + meta.name());
  }
  return switch (scope.type()) {
    case ALL -> Predicates.alwaysTrue();
    case SELF_CREATED -> Predicates.eq(require(meta.createdByCol), scope.empId());
    case SELF -> Predicates.eq(require(meta.selfCol), scope.empId());
    case SELF_ASSIGNED -> {
      if (meta.assigneeCol != null) yield Predicates.eq(meta.assigneeCol, scope.empId());
      if (meta.joinPolicy != null) yield meta.joinPolicy.build(scope, DataScopeType.SELF_ASSIGNED);
      throw new IllegalStateException("SELF_ASSIGNED not supported for " + meta.name());
    }
    case ORG -> {
      if (meta.ownerOrgCol != null) yield Predicates.eq(meta.ownerOrgCol, scope.orgCode());
      if (meta.joinPolicy != null) yield meta.joinPolicy.build(scope, DataScopeType.ORG);
      throw new IllegalStateException("ORG not supported for " + meta.name());
    }
    case ORG_SUBTREE -> {
      var subtree = require(scope.orgSubtree());
      if (meta.ownerOrgCol != null) yield Predicates.in(meta.ownerOrgCol, subtree);
      if (meta.joinPolicy != null) yield meta.joinPolicy.build(scope, DataScopeType.ORG_SUBTREE);
      throw new IllegalStateException("ORG_SUBTREE not supported for " + meta.name());
    }
    case WORKFLOW_PARTICIPANT ->
        Predicates.existsWorkflowParticipant(require(meta.businessKeyCol), scope.empId());
  };
}
```

### 6.2 写前校验（实体级强校验）

```java
void assertWritable(ObjectMeta meta, DataScope scope, ObjectId id) {
  var row = repo.selectScopeColumnsById(id); // created_by / owner_org_id / assignee / 状态等
  assertStatusAllowed(row.status());
  if (!matches(scope, row)) {
    throw new AccessDeniedException("DATA_SCOPE_DENIED");
  }
}
```

落地要求：

- 所有写操作必须先查真实实体状态，再做实体级范围校验
- 列表命中权限不等于写操作自动放行
- `APPROVE / REJECT / CLAIM / TRANSFER` 这类流程动作还要额外过 runtime task 守卫

### 6.3 多角色并集策略（V1）

```java
DataScope mergeScopes(List<DataScope> scopes) {
  // 统一 DATA_SCOPE 取更大可见边界；但不放宽高危动作和流程办理权
}
```

冻结口径：

- `DATA_SCOPE` 取更大可见边界
- 资源权限取并集
- `TRANSFER / DELETE / APPROVE / REJECT / IMPORT / RECALC / JOB_TRIGGER / PERMISSION_CHANGE` 不因范围更大而自动放宽

### 6.4 `WorkflowParticipantService` 最小职责

统一由工作流中心暴露：

```java
public interface WorkflowParticipantService {
  boolean isParticipant(String empId, String businessKey, String viewBizType);
  RuntimeAccess resolveRuntimeAccess(String taskId, String empId);
  Set<String> resolveCandidateGroups(String empId);
  Set<String> listReadableBusinessKeys(String empId, String bizType);
}
```

服务边界：

- 统一判定流程参与者可读
- 统一判定当前节点 claim / approve / reject / transfer 的办理权
- 统一计算候选组，不允许业务模块自行实现“谁算参与者”

---

## 7. 常见坑、实现限制与边界

1. **子表不要直接按子表字段做范围**：例如 `touch_log` 应回到 `touch_task` 判权。
2. **客户可见性不要用 `cust_master.owner_org_id` 偷懒**：V1 正式冻结为通过 `cust_claim` 承载可见性。
3. **`WORKFLOW_PARTICIPANT` 不是纯字段映射问题**：必须引入 `WorkflowParticipantService` 或统一工作流参与者判定服务。
4. **未声明的 scope 必须 fail-fast**：对象缺字段时抛错/拒绝，不允许默认降级为 `ALL`。
5. **`SUPPORT` 与 `SUPPORT_DEPT` 必须双视图建模**：发起侧看 `owner_org_id`，承接侧看 `support_dept_id`，不得混用。
6. **客户历史全量必须独立只读接口**：禁止通过普通详情接口加参数旁路。
7. **办理权必须以 Flowable runtime task 为准**：不要仅用业务表里的 `assigned_emp_id` 一类字段推导办理权。
8. **Flowable history level 必须不低于 `audit`**：否则历史参与者与候选范围判定不完整。
9. **不建议业务 SQL 直接联查 `ACT_RU_* / ACT_HI_*`**：统一通过工作流中心封装。
10. **导出仍要受 `DATA_SCOPE + 查询条件` 双重约束**：不能因为用户能看列表就默认可以导出全量。