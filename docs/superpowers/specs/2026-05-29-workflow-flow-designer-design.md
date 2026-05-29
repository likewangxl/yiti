# 审批流程设计器（动态流程维护管理）设计

- 日期：2026-05-29
- 模块：workflow-center（后端） + xanzc_frontend（前端）
- 状态：设计已确认，待写实现计划
- 示例流程：目标方案审批（`perf_target_adjust_v1`）、业绩调整审批（`perf_alloc_adjust_corp_v1` / `perf_alloc_adjust_retail_v1`）

## 1. 目标与范围

为审批流程提供前端可视化的维护管理能力，满足：

1. 查看所有审批流程（结构 + 各环节审批人 + 分支）。
2. 对审批流程的环节做增删改。
3. 审批环节可定义审批人为「角色 / 机构 / 指定到人」（多条件并存），并定义「任一人审批（或签）」或「全部人审批（会签）」。
4. 可定义审批环节之间的条件分支。

**首期范围**：把上述两类（三条）示例流程纳入设计器进行查看与编辑演示；引擎与设计器一次性建成，可创建/编辑/发布/试跑任意流程。

## 2. 硬约束：默认零影响

**本功能上线本身不得改变任何现有审批的运行行为。** 据此：

- 现有 BPMN 文件（`*.bpmn20.xml`）与业务启动代码（`AllocAdjustService.submit`、`TargetAdjustService` 等）**完全不动**，现有/在途/新发起审批 100% 照旧。
- 现有 Flowable 监听器（`TaskAssignmentListener` / `ProcessCompletedListener` / 回调）**不改签名、不改语义**，避免波及现有流程。
- 设计器对现有流程默认**只读查看**（反向导入为只读参考模型）。
- 编辑后发布生成的 BPMN 部署到**独立的影子流程 key**（设计器命名空间），业务代码默认**不消费**它，故对现有业务零影响。
- 「把某条现有流程切换为设计器版本生效」是**单独的、显式的、带回归测试的动作**，**不在本期交付范围**。

## 3. 设计决策（已确认）

| 决策点 | 结论 |
|---|---|
| 动态程度 | 全动态流程设计器（分期）：DB 流程模型 → 发布时生成 BPMN → 部署 Flowable |
| 会签（全部人审批）驳回语义 | 任一人驳回即整单驳回、流程终止 |
| 驳回去向 | 终止整单 + 通知申请人（与现有 alloc/target 行为一致） |
| 条件分支判断依据 | 白名单流程变量 + 条件构造器（字段 + 运算符 + 值，多条 AND/OR），不允许裸表达式 |
| 版本与在途实例 | 每次发布 = Flowable 新 deployment；运行中实例走旧版，新发起走最新版（Flowable 按 key 取 latest） |
| BPMN 生成方式 | Flowable `BpmnModel` Java API 编程式构建（非 XML 字符串拼接） |
| 前端编辑器 | 首期表单/列表式（非拖拽画布；bpmn-js 画布留后期） |
| 现有流程处置 | 只读导入查看；编辑发布走影子 key；切换生效另算（本期不做） |

## 4. 数据模型（workflow-center 新增 4 张表）

> 表名大写（与库内现有 `WF_*` 一致；Linux MySQL `lower_case_table_names=0` 大小写敏感）。

### 4.1 WF_FLOW_DEF — 可编辑流程定义
| 列 | 类型 | 说明 |
|---|---|---|
| id | varchar(32) PK | |
| flow_key | varchar(64) | 设计器内逻辑 key（唯一） |
| biz_type | varchar(32) | ALLOC_ADJUST / TARGET_ADJUST 等 |
| name | varchar(128) | 流程名称 |
| description | varchar(512) | |
| status | varchar(16) | DRAFT / PUBLISHED |
| version | int | 已发布次数（发布 +1） |
| deployed_proc_def_key | varchar(64) | 发布部署用的**影子** Flowable key（如 `dsn_alloc_adjust_corp`），与线上 key 不同 |
| deployed_proc_def_id | varchar(64) | 最近一次部署的 Flowable processDefinitionId |
| source_proc_def_key | varchar(64) | 若由现有线上流程导入，记录原 key（只读参考用） |
| is_readonly_import | tinyint | 1=现有线上流程的只读映射 |
| created_time / created_by / updated_time / updated_by | | |

### 4.2 WF_FLOW_NODE — 节点
| 列 | 类型 | 说明 |
|---|---|---|
| id | varchar(32) PK | |
| flow_def_id | varchar(32) | 所属流程 |
| node_key | varchar(64) | 流程内唯一 |
| node_type | varchar(16) | START / APPROVAL / GATEWAY / END |
| name | varchar(128) | 节点名 |
| approve_mode | varchar(8) | ANY（或签）/ ALL（会签）；仅 APPROVAL 有意义 |
| sort_no | int | 列表展示/求值顺序 |
| pos_x / pos_y | int | 布局坐标（首期可空，留给后期画布） |
| created_time / updated_time | | |

### 4.3 WF_FLOW_NODE_APPROVER — 审批人规则（多行并存=并集）
| 列 | 类型 | 说明 |
|---|---|---|
| id | varchar(32) PK | |
| node_id | varchar(32) | 所属节点 |
| approver_type | varchar(8) | ROLE / ORG / USER |
| approver_value | varchar(64) | 角色编码 / 机构编码 / 员工号 |
| sort_no | int | |
| created_time | | |

### 4.4 WF_FLOW_EDGE — 连线 / 条件分支
| 列 | 类型 | 说明 |
|---|---|---|
| id | varchar(32) PK | |
| flow_def_id | varchar(32) | |
| from_node_id | varchar(32) | |
| to_node_id | varchar(32) | |
| name | varchar(128) | 分支标签（可空） |
| is_default | tinyint | 1=默认（else）分支 |
| condition_json | text | 条件，null=无条件直连 |
| sort_no | int | 排他网关的条件求值顺序 |
| created_time | | |

`condition_json` 结构：
```json
{ "logic": "AND",
  "conditions": [ {"field": "bizKind", "op": "EQ", "value": "CORP"} ] }
```
运算符：EQ / NE / IN / NOT_IN / GT / GE / LT / LE / CONTAINS。生成 EL：`${bizKind == 'CORP' && ...}`。

## 5. 条件变量白名单

- 按 `biz_type` 在**代码侧**维护一组可用变量：`{field, label, type}`。
  - ALLOC_ADJUST：`bizKind`(字符串) / `custType`(字符串) / `allocDim`(字符串)（现有 submit 已写入这些流程变量）。
  - TARGET_ADJUST：按其 submit 写入的变量定义。
- `GET /flows/meta/variables?bizType=` 返回白名单，喂给前端条件构造器。
- 新增可用变量须同步保证对应 submit 把它写入流程变量；构造器只允许白名单内字段，杜绝注入。

## 6. 模型 → BPMN 生成映射

发布时用 `BpmnModel` API 构建并 `RepositoryService.deploy()`，部署到 `deployed_proc_def_key`（影子 key）。

| 模型 | BPMN 产物 |
|---|---|
| START 节点 | startEvent |
| APPROVAL + ANY（或签） | userTask + 多候选人（首个签收办理）；任务后隐式排他网关判 `approved`：false→驳回终止节点，true→后继边 |
| APPROVAL + ALL（会签） | multiInstance userTask（并行，集合=运行时解析出的审批人 empId 列表）；任一驳回 → 整单驳回终止 |
| GATEWAY 节点 | exclusiveGateway；出边按 `condition_json` 生成 conditionExpression；`is_default` 边为 default flow |
| END 节点 | endEvent（另含一个 REJECTED 终止节点供驳回路径汇入） |

**复用现有监听器（不改其代码）**：生成的 BPMN 在 userTask 上挂 `${taskAssignmentListener}`、流程结束挂 `${processCompletedListener}`，使影子流程具备与现有一致的待办指派、通知、业务状态回写能力。

**审批人解析（零影响策略）**：发布时把 `WF_FLOW_NODE_APPROVER` 规则**生成为 `WF_NODE_CANDIDATE_CONF` 行**（针对影子 key + node_key），从而**完全复用现有 `TaskAssignmentListener` / `CandidateResolverService`**，不改解析逻辑。
- 或签（ANY）：直接走现有候选组/候选用户解析（已含待办候选用户可见性修复）。
- 会签（ALL）：新增一个**轻量 task/execution 监听器或 MI 集合解析器**，把审批人规则展开成 empId 集合作为 multiInstance 集合变量。该组件仅服务于含 MI 节点的设计器流程，现有流程无 MI 节点，故不受影响。

**驳回**：任一审批节点 `approved=false` → 经排他网关汇入 REJECTED 终止节点 → `processCompletedListener` 置业务 REJECTED 终态并通知申请人（与现有语义一致）。

**已知细节**：现有 `TaskAssignmentListener` 对 `nodeKey=="branch_approve"` 有"按发起人机构过滤候选人"的硬编码特例（见本会话候选用户可见性修复）。设计器流程若希望某审批节点也按机构过滤，需把该节点 `node_key` 取为 `branch_approve`（或后期把"按机构过滤"提升为节点上的可配置开关，单独迭代）。本期不改该监听器。

## 7. 发布校验（publish 前）

- 恰好 1 个 START；至少 1 个 END。
- 无孤立节点（除 START 外都有入边，除 END 外都有出边）。
- 每个 APPROVAL 节点至少 1 条审批人规则。
- 每个 GATEWAY 的条件可解析（字段在白名单内、运算符合法）；多分支建议含 1 条 default。
- 不通过则拒绝发布并返回明细。

## 8. 后端 API（workflow-center）

`/api/admin/workflow/flows`，全部 `@BizAuth(bizType=SYS_CONFIG, action=CONFIG)`：

| 方法 | 端点 | 说明 |
|---|---|---|
| GET | `/flows` | 列出所有流程（id/key/biz_type/name/status/version/最后发布时间）— 要求① |
| GET | `/flows/{id}` | 取完整模型（节点+审批人+连线） |
| POST | `/flows` | 新建/克隆流程 |
| PUT | `/flows/{id}` | 保存草稿：整图替换（节点/连线/审批人一次提交）— 要求② |
| POST | `/flows/{id}/publish` | 校验→生成 BPMN→部署影子 key→version+1 |
| DELETE | `/flows/{id}` | 软删除（仅草稿/未被消费的影子流程） |
| GET | `/flows/meta/variables?bizType=` | 条件变量白名单 |
| GET | `/flows/meta/approver-options` | 角色列表/机构树/人员检索（复用现有 auth API） |

> 粗粒度：草稿整图一次 PUT，发布单独触发。只读导入的流程（`is_readonly_import=1`）禁止 PUT/publish 到原 key，只能"另存为新流程"再编辑。

## 9. 前端设计器（xanzc_frontend）

新增「审批流程管理」菜单（建议挂 system 域）：

- **流程列表页**：表格列出所有流程（名称/业务类型/状态/版本/最后发布时间），操作：查看、编辑、发布、克隆。
- **流程编辑页**（首期表单/列表式）：
  - 节点列表：增删改；每节点设 名称 / 类型 / 或签·会签。
  - 审批人规则（节点下子表）：多行并存，每行选 角色 / 机构 / 指定人（选择器复用 `/flows/meta/approver-options`）。
  - 连线列表：起点→终点 + 条件构造器（字段+运算符+值，多条 AND/OR）+ 默认分支勾选。
  - 顶部「保存草稿」「发布」；前端先做一遍校验提示。
  - 只读「结构预览」：文字化顺序 + 分支（首期不画图）。

## 10. 迁移 / 导入（只读反射）

- seed 脚本把现有三条流程的 BPMN 结构 + `WF_NODE_CANDIDATE_CONF` 反向导入到 4 张模型表，`is_readonly_import=1`，供设计器"查看所有流程"。
- 现有流程仍以现有 BPMN 为运行时；导入仅用于展示与"克隆为新流程"。

## 11. 测试（TDD 全程）

- 单元：`BpmnModel` 生成器（模型→BPMN 断言节点/网关/多实例/监听器挂载）、条件 EL 生成、发布校验器。
- 集成：生成 BPMN 部署到内存 Flowable，发起实例，走通 或签 / 会签（任一驳回终止）/ 条件分支 / 驳回终止，断言任务指派与流转、业务状态回写。
- 候选可见性：复用并扩展 `TodoQueryServiceTest`（候选用户/候选组/会签 MI）。
- 迁移：seed 流程能在设计器正确展示；克隆后发布到影子 key 可独立试跑。

## 12. 不在本期范围（明确排除）

- 把现有线上流程**切换/启用**为设计器版本（需改业务启动代码的流程 key 路由 + 全面回归，单独立项）。
- 拖拽画布编辑器（首期表单式；bpmn-js 留后期）。
- 会签的多数决/百分比完成（首期仅"全部通过、任一驳回即终止"）。
- 驳回退回上一环节 / 退回申请人重走（首期仅终止整单）。
- 条件引用业务表字段（首期仅白名单流程变量）。

## 13. 分期建议

- **P1 引擎**：4 张表 DDL + 模型 CRUD Service/API + `BpmnModel` 生成器 + 发布部署（影子 key）+ 会签 MI 解析器 + 发布校验 + 单元/集成测试。
- **P2 前端**：流程列表 + 表单式编辑器 + 条件构造器 + 审批人选择器 + 校验提示。
- **P3 迁移**：seed 只读导入现有三条流程 + 克隆试跑回归。
- （后期，单独立项）切换生效 + 画布编辑器 + 高级会签/驳回路由。
