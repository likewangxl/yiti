# 审批人模型重构：层级角色 / 机构角色 设计规格

- 日期：2026-06-08
- 模块：workflow-center（DTO/表/持久化/运行时）、auth-permission-center（UserApi 加按机构取人）、xanzc_frontend（ApproverPicker）
- 状态：设计已确认（用户逐条拍板），待实现

## 1. 目标

把审批节点的审批人从「类型+值」二元模型，重构为支持**机构归属下放到审批人级**的四类型模型，并**删除节点级「审批机构归属」**（功能并入层级角色的层级下拉）。

## 2. 审批人四类型

| 类型 | 子控件（各占一行竖排） | 运行时语义 |
|---|---|---|
| **层级角色** `LEVEL_ROLE` | 层级(必选，无 label：发起机构=SELF / 发起上级机构=PARENT) + 角色(原角色下拉) | 该角色在【发起机构 / 发起上级机构】的在职持有者。层级即原审批机构归属 |
| **机构角色** `ORG_ROLE` | 机构(原机构下拉) + 角色(可选，状态正常角色) | 选角色：该角色在该机构的持有者；**不选角色：该机构任一角色（= 全部在职人员）** |
| 指定人 `USER` | 远程搜索(原样) | 指定工号 |
| 流程变量 `VAR` | 下拉(原样) | 从流程变量取工号(单值/列表) |

UI：每个审批人行内各子控件**各占一行**竖排（窄面板显示完整）；删除按钮独立一行/角。节点属性面板**移除「审批机构归属」**整块。

## 3. 数据模型

### FlowApproverDTO（+2 字段）
- `approverType`：LEVEL_ROLE / ORG_ROLE / USER / VAR
- `approverValue`：主值 —— 层级角色=角色码 / 机构角色=机构码 / 指定人=工号 / 变量=变量名
- `orgScope`（新）：SELF / PARENT —— 仅层级角色
- `roleCode`（新）：可选角色码 —— 仅机构角色（空=该机构全员）

### WF_FLOW_NODE_APPROVER（设计源表，ALTER）
- `approver_type` varchar(8) → **varchar(16)**（LEVEL_ROLE 10 字符）
- 加 `org_scope varchar(8) NULL`、`role_code varchar(64) NULL`

### WF_NODE_CANDIDATE_CONF（运行时表，ALTER）
- 已有 `approve_org_scope`；加 `org_code varchar(64) NULL`
- `candidate_type` 列宽确认 ≥ varchar(8)（值仍是 ROLE/ORG/USER/VAR，见下）

## 4. 持久化（发布时，每审批人一行，不再按类型合并）

`FlowPublishService.writeCandidateConfs` 改为每个 approver 写一条 `WfNodeCandidateConf`：

| 审批人 | candidate_type | candidate_value | approve_org_scope | org_code |
|---|---|---|---|---|
| 层级角色(role@scope) | ROLE | `["roleCode"]` | SELF/PARENT | null |
| 机构角色(role@org) | ROLE | `["roleCode"]` | null | orgCode |
| 机构角色(无角色) | ORG | `["orgCode"]` | null | null |
| 指定人 | USER | `["empId"]` | null | null |
| 流程变量 | VAR | `["varName"]` | null | null |

> 每行携带各自 scope/org，故不能再合并；保留 sort_no 保序。

## 5. 运行时解析（统一）

新增解析：`CandidateResolverService.resolveApproverSpecs(procKey, nodeKey)` → `List<ApproverSpec{type,value,orgScope,orgCode}>`（直读 conf 行）。

新增/抽取解析器 `expandSpecToEmpIds(spec, startOrgId, varReader)`：
- `ROLE` + orgScope(SELF/PARENT) → `getEmpIdsByRoleCodeAndOrg(role, resolveScopeOrg(scope,startOrgId))`
- `ROLE` + org_code(固定) → `getEmpIdsByRoleCodeAndOrg(role, orgCode)`
- `ROLE`（无 scope 无 org，历史兼容） → `getEmpIdsByRoleCode(role)`
- `ORG` → **`getEmpIdsByOrg(orgCode)`（auth 新增：该机构全部在职员工，任一角色）**
- `USER` → 该工号
- `VAR` → 读流程变量（单值/列表）

`resolveScopeOrg(SELF,startOrg)=startOrg`；`(PARENT,startOrg)=getOrg(startOrg).parentOrgCode`（无上级兜底本机构）。

消费端：
- 或签节点：解析出 empIds → `addCandidateUser` 逐个（设计器流程不再用 candidate group）。
- 会签节点：`MultiInstanceApproverResolver` 解析出 empIds → set `approverEmpIds`。
- 通知展开：同上 empIds。

**节点级 `approveOrgScope` 运行时路径**：保留**仅兼容历史静态 `branch_approve`**（legacy AUTO），设计器不再产出节点级 scope。

## 6. auth-permission-center

`UserApi` 新增 `List<String> getEmpIdsByOrg(String orgCode)` —— 返回该机构（EXT_USER_ORG / 主机构）全部在职员工工号，供机构角色无角色场景。Facade 实现 + 单测。

## 7. 前端

- `ApproverPicker.vue`：类型增 LEVEL_ROLE/ORG_ROLE（替换 ROLE/ORG 标签为层级角色/机构角色），每行竖排：
  - 层级角色：层级 select(SELF/PARENT) → 角色 select。
  - 机构角色：机构 select → 角色 select(可空，状态正常角色)。
  - USER/VAR 原样。
  - 数据写回 `{approverType, approverValue, orgScope?, roleCode?}`。
- `FlowNodePanel.vue`：**删除「审批机构归属」表单项**及 onTypeChange 中的 approveOrgScope 清理。
- `FlowEdit.vue`：approvers 规整/buildPayload 透传 orgScope/roleCode；移除 node.approveOrgScope 透传。
- 角色下拉数据复用既有 `listAllRoles({recordStatus:0})`（状态正常）；机构复用 orgTree。

## 8. 种子迁移

重建的两条流程：`branch_approve_l2/l3` 的审批人由「节点 scope + 纯 ROLE」改为**层级角色**（role + SELF/PARENT），去掉节点 `approve_org_scope`；其余 ROLE 审批人（biz_dept_review 等）按需改为层级角色(SELF=本机构) 或保留按角色全量。更新 `2026-06-08-rebuild-alloc-flows-seed.sql`。

## 9. 测试（TDD）

- auth：`getEmpIdsByOrg` Facade 单测。
- workflow：`CandidateResolverService.resolveApproverSpecs` 解析 conf 行；`expandSpecToEmpIds` 五分支；`FlowPublishService` 每审批人一行 + org_code/scope 落库；`FlowDefService` approver orgScope/roleCode 往返；listener/MI 用新解析。
- 前端：`vite build` + 手工验证矩阵（层级角色两控件、机构角色无角色、节点无机构归属项）。

## 10. 交付顺序

1. auth `getEmpIdsByOrg`（TDD）+ install。
2. schema ALTER（yiti，approver 表 + conf 加 org_code）。
3. workflow DTO/实体/mapper 列 + FlowDefService 往返（TDD）。
4. 持久化 writeCandidateConfs 每行（TDD）。
5. 运行时 resolveApproverSpecs + expandSpecToEmpIds + listener/MI 接入（TDD）。
6. 前端 ApproverPicker 重排 + 删节点机构归属。
7. 种子迁移 + 全量回归 + install + 重启。
