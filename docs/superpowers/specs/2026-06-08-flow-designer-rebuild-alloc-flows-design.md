# 用设计器重建对公/零售分配调整审批流程（Step 2）设计规格

- 日期：2026-06-08
- 模块：workflow-center（设计器数据 + 发布）、performance-engine-center（切换 + 审批页）、xanzc_frontend
- 状态：设计待评审
- 前置：Step 1（动态参数能力）已交付——VAR 审批人、分支输出名称、条件自定义变量、虚拟员工默认通过泛化。

## 1. 背景与目标

Step 1 已让设计器具备复现两条流程的表达力。本期用设计器**按现网结构重建**对公（`perf_alloc_adjust_corp_v1`）/零售（`perf_alloc_adjust_retail_v1`）两条分配关系调整审批流，发布到影子 key `DSN_*`，并在确认等价后切换生产指向影子流程，使两条流程从"静态 BPMN 文件"转为"设计器可视化管理"。

**关键策略——同 nodeKey 重建**：重建时沿用现网 9 个 nodeKey。如此：
- `branch_approve` 无显式机构归属 → 命中 Step 1 保留的 legacy AUTO 路由（3级支行→上级分行 / 2级→本机构），与现网一致；
- `original_owner_approve` 用 **VAR 审批人 `originalOwnerEmpIds`** + 会签，等价现网 BPMN 的 `collection=${originalOwnerEmpIds}`；
- 网关条件用自定义变量 `corpRouteTo`/`finRouteTo`（Step 1 C 已放宽），前端 `Adjust.vue` 既有按 nodeKey 写 `corpRouteTo/finRouteTo` 的逻辑**无需改动**即可继续驱动路由；
- 虚拟员工默认通过已泛化，任意节点生效。

## 2. 现状锚点

- `AllocAdjustService`：`PROCESS_KEY_CORP="perf_alloc_adjust_corp_v1"` / `PROCESS_KEY_RETAIL="perf_alloc_adjust_retail_v1"`（常量，line 71/74）；`resolveProcessKey(custType,bizKind)` 据 bizKind 族返回二者之一；`startCmd.setStartOrgId(cmd.getOwnerOrgId())`、会签名单写 `vars.put("originalOwnerEmpIds", list)`。
- 现网候选配置 `WF_NODE_CANDIDATE_CONF`（两线各 7 行 ROLE/USER）：branch_approve=[BRANCH_HEAD,BRANCH_PRE,R_5F1F1A19]、biz_dept_review=[CORP_DEPT|RETAIL_DEPT]、biz_dept_leader_approve=[CORP_DEPT_LEADER|RETAIL_DEPT_LEADER]、finance_review=[BACK_FINANCE]、finance_leader_approve=[FINANCE_LEADER]、retail 线 original_owner_approve=[R_RM]（+ 两条 branch_mgr_review/hq_mgr_review 孤儿行，重建不带）。
- 前端 `Adjust.vue`：审批弹框 `routeTo`（LEADER/OWNER 单选），`varName = nodeKey==='finance_review' ? 'finRouteTo' : 'corpRouteTo'`，`formData={[varName]:routeTo}`，调 `approveTask`。
- `FlowPublishService.publish`：校验→生成 BPMN→部署到影子 key `DSN_<flowKey>`→整图替换候选配置（含 Step 1 的 approveOrgScope/VAR）→version+1。
- 发布产物在影子 key，**不影响**现网静态流程，直到 perf 显式切换。

## 3. 流程结构（两线一致，重建目标）

9 节点（START + 6 审批 + 2 网关，外加 END/驳回 END 由生成器自动补）：

| nodeKey | 类型/模式 | 审批人 | 机构归属 | 备注 |
|---|---|---|---|---|
| start | START | — | — | |
| gw_level | GATEWAY | — | — | 按 startOrgLevel 分流（开始后即分支） |
| branch_approve_l2 | APPROVAL/或签 | ROLE: BRANCH_HEAD,BRANCH_PRE,R_5F1F1A19 | **SELF**（本机构） | 2级机构发起分支 |
| branch_approve_l3 | APPROVAL/或签 | ROLE: BRANCH_HEAD,BRANCH_PRE,R_5F1F1A19 | **PARENT**（上级机构） | 3级机构发起分支 |
| biz_dept_review | APPROVAL/或签 | ROLE: CORP_DEPT(对公)/RETAIL_DEPT(零售) | — | 两分支汇入；经办选 corpRouteTo |
| gw1_route | GATEWAY | — | — | 排他网关 |
| biz_dept_leader_approve | APPROVAL/或签 | ROLE: CORP_DEPT_LEADER/RETAIL_DEPT_LEADER | — | |
| original_owner_approve | APPROVAL/**会签** | **VAR: originalOwnerEmpIds** | — | 全通过后→部门负责人 |
| gw1_join | GATEWAY | — | — | 汇合 |
| finance_review | APPROVAL/或签 | ROLE: BACK_FINANCE | — | 经办选 finRouteTo |
| gw2_route | GATEWAY | — | — | |
| finance_leader_approve | APPROVAL/或签 | ROLE: FINANCE_LEADER | — | |
| end | END | — | — | |

边与条件（输出名称用于经办走向选择/反显）：

| 边 | 条件 | 输出名称 |
|---|---|---|
| start→branch_approve | — | |
| branch_approve→biz_dept_review | — | |
| biz_dept_review→gw1_route | — | |
| gw1_route→biz_dept_leader_approve | `corpRouteTo == 'LEADER'` | 部门负责人审批 |
| gw1_route→original_owner_approve | `corpRouteTo == 'OWNER'` | 原业绩所属人会签 |
| original_owner_approve→biz_dept_leader_approve | — | |
| biz_dept_leader_approve→gw1_join | — | |
| gw1_join→finance_review | — | |
| finance_review→gw2_route | — | |
| gw2_route→finance_leader_approve | `finRouteTo == 'LEADER'` | 资财部负责人审批 |
| gw2_route→end | `finRouteTo == 'END'` | 审批结束 |
| finance_leader_approve→end | — | |

> 注：现网"原所属人会签后再到部门负责人"由 `original_owner_approve→biz_dept_leader_approve` 边表达；生成器对每个审批节点自动补"审批结果网关→approved==false→驳回结束"，故驳回即终止语义自动获得，无需画。

## 4. 分阶段交付

### 2a 重建 + 发布 + 等价校验（无生产影响）
- 以 §3 结构构建两条 `FlowGraphDTO`，经设计器落库（`WF_FLOW_DEF/NODE/NODE_APPROVER/EDGE`）并 `publish` 到影子 key `DSN_perf_alloc_adjust_corp_v1` / `DSN_..._retail_v1`。
  - 落库方式：用幂等 SQL 种子脚本插入设计源四表（带固定 id 便于重跑），再调发布服务生成 BPMN + 候选配置。脚本 `docs/superpowers/sql/2026-06-08-rebuild-alloc-flows-seed.sql`。
- 等价校验：对比影子流程生成的 BPMN 节点/边/条件/会签/候选配置与现网静态流程一致（结构 diff + 候选配置 diff）。
- 验收：影子流程可独立跑通一遍 corp + retail（用测试申请），不触碰现网。

### 2b 经办走向动态化 + 反显（perf 审批页，可选增强）
- workflow 任务详情 `getTaskDetail` 增加"走向选项"：读已部署 BPMN 中当前节点下游网关的出边（`SequenceFlow.name`=输出名称 + 条件变量/值），返回 `routeOptions:[{label,var,value}]`。
- `Adjust.vue` 审批弹框：有 `routeOptions` 时用其渲染走向下拉（替换硬编码 LEADER/OWNER 单选），选中写 `formData[var]=value`；已办按所选 value 的输出名称反显。无 routeOptions 时回退现有硬编码逻辑（兼容现网静态流程）。
- **本阶段是增强**：因 Adjust.vue 既有按 nodeKey 的硬编码逻辑对同 nodeKey 重建流程依然有效，2b 不做也能跑通；做了则走向标签由设计器驱动、可视化可维护。

### 2c 切换生产（高危，独立 gated）
- `AllocAdjustService.PROCESS_KEY_CORP/RETAIL` 改指向 `DSN_*` 影子 key（或加配置开关 `perf.alloc.use-designer-flow` 灰度）。
- 切换后新提交走设计器流程；在途流程不受影响（按启动时的流程定义跑完）。
- 回退：改回常量即可。

## 5. 风险与边界

- 会签 collection：影子流程用 `multiInstanceApproverResolver` 读候选(VAR)→`approverEmpIds`，与现网 BPMN 直接 `collection=${originalOwnerEmpIds}` 路径不同但结果等价；需在 2a 校验会签实例数与名单一致。
- `corpRouteTo/finRouteTo` 由经办 approve 时 formData 传入（Adjust.vue 既有），影子流程网关条件引用之，链路一致。
- 候选配置发布会"整图替换"影子 key 的 `WF_NODE_CANDIDATE_CONF`，不动现网 key 的行，互不干扰。
- branch_approve legacy AUTO 依赖 nodeKey 字面 `branch_approve` + startOrgId 变量（AllocAdjustService 已传），重建沿用同名即生效。

## 6. 测试

- 2a：种子幂等性 + 发布后 BPMN 结构断言（节点/边/条件/会签/候选配置 = 现网）；影子流程端到端跑通（corp OWNER 路径会签、retail LEADER 路径、finance END 直结束）。
- 2b：`getTaskDetail` routeOptions 解析单测；Adjust.vue 走向下拉 + 反显手工验证；无 routeOptions 回退。
- 2c：切换后 `resolveProcessKey` 返回 DSN_*；一笔 corp + 一笔 retail 全链路审批通过/驳回。

## 7. 待评审决策

1. **本期做到哪一阶段？** 建议先做 **2a（重建+发布+等价校验，零生产影响）**，2b/2c 看 2a 结果再定。
2. **2b 是否本期做**（经办走向由 outputName 动态驱动 + 反显，替换硬编码）。
3. **2c 切换方式**：直接改常量 vs 加灰度开关 `perf.alloc.use-designer-flow`。
