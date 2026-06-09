# 审批流程设计器 - 动态参数能力（Step 1）设计规格

- 日期：2026-06-08
- 模块：workflow-center（后端 + 设计器前端 xanzc_frontend）
- 状态：设计待评审
- 关联：本规格是「用设计器完整实现对公/零售审批流程」的 **Step 1（补能力）**；Step 2（用设计器重建两条流程 + perf 审批页对接 + 发布切换）另立规格。

## 1. 背景与目标

可视化审批流设计器已能表达大部分结构（开始/结束/网关/审批节点、网关条件分支、或签/会签、驳回即终止自动生成、审批机构归属本机构/上级机构）。但要用设计器**完整复现现网对公（`perf_alloc_adjust_corp_v1`）/零售（`perf_alloc_adjust_retail_v1`）**两条流程，还缺把"工作流运行时动态参数"与"流程编辑"结合起来的能力。

本期（Step 1）补齐 4 项能力，使设计器具备复现两条流程所需的全部表达力：

- **A 传入变量审批人（VAR）**：审批人可来自提交方传入的流程变量（如原业绩所属人名单 `originalOwnerEmpIds`）。
- **B 分支连线「输出名称」**：网关每条出边是一个"下一步走向"选项，带可读标签，供审批页动态选择走向并反显。
- **C 条件可引用自定义变量**：条件构造器字段允许自定义输入变量名（如路由变量 `corpRouteTo`），不必预登记变量目录。
- **D 虚拟员工默认通过（泛化）**：任意审批任务受理人为虚拟员工（字典 USER_TYPE=2）即自动「默认同意」，不再硬编码到 `original_owner_approve` 节点。

**非目标（YAGNI / 移交 Step 2）**：不在本期重建对公/零售流程；不改 perf 审批页（Adjust 等）；不实现"经办审批时的走向下拉 UI"的前端消费（Step 1 只把走向选项作为元数据持久化并带入已部署 BPMN，供 Step 2 消费）。

## 2. 现状锚点（关键既有实现）

- `FlowBpmnGenerator`：APPROVAL 节点挂 `${taskAssignmentListener}`；ALL（会签）节点生成多实例，集合变量 `approverEmpIds` 由 `${multiInstanceApproverResolver}` 运行时注入；每个审批节点自动生成"审批结果网关 → `approved==false` → 统一 `rejectedEnd`"。
- `CandidateResolverService.resolveCandidates(procKey,nodeKey)`：读候选配置，按类型加前缀返回 `["ROLE:X","USER:E001",...]`。
- `MultiInstanceApproverResolver` / `TaskAssignmentListener`：各自把带前缀候选 `expandCandidatesToEmpIds`（USER 直取、ROLE 调 `UserApi.getEmpIdsByRoleCode`、ORG 跳过）。
- `TaskAssignmentListener.autoApproveIfVirtualOwner`：当前仅在 `nodeKey=="original_owner_approve"` 时触发，受理人虚拟员工 → `addComment("默认同意") + complete(approved=true)`。
- `FlowConditionExpressionBuilder.toEl`：`{field,op,value}` → `${field op value}`，数字不加引号。
- `WF_FLOW_EDGE` 表已有未使用的 `name varchar(128)` 列（B 复用它，免加列）。
- `WfNodeFormConf`（节点表单配置表）已存在，Step 2 经办走向 UI 可复用，本期不动。

## 3. 能力 A：传入变量审批人（VAR）

### 3.1 数据与设计器
- 审批人项 `{approverType, approverValue}` 的 `approverType` 增加取值 **`VAR`**，`approverValue` = 流程变量名（如 `originalOwnerEmpIds`）。
- 前端 `ApproverPicker.vue` 类型下拉增加「流程变量」；选中后值输入框为普通文本（填变量名），并给提示（"运行时从该流程变量取审批人工号，单值或列表均可"）。

### 3.2 发布与运行时
- `FlowPublishService.writeCandidateConfs` 无需特殊处理：VAR 作为一种 `candidateType` 随候选配置落库（`candidate_value` = `["originalOwnerEmpIds"]`）。
- `CandidateResolverService.resolveCandidates` 原样加前缀 → `"VAR:originalOwnerEmpIds"`。
- `TaskAssignmentListener.expandCandidatesToEmpIds` 与 `MultiInstanceApproverResolver.expandCandidatesToEmpIds` 各增 `case "VAR"`：从执行上下文读流程变量（`delegateTask.getVariable(name)` / `execution.getVariable(name)`）：
  - 值为 `String` → 单个 empId；
  - 值为 `Collection` → 逐个 empId；
  - 空/null → 跳过（记 debug）。
- 会签场景：会签节点用 VAR 审批人时，`MultiInstanceApproverResolver` 读 `originalOwnerEmpIds` 列表 → 写 `approverEmpIds` → 多实例逐人会签，**等价**现网静态 BPMN 的 `collection="${originalOwnerEmpIds}"`。

### 3.3 校验
- `FlowValidator`：APPROVAL 节点"至少 1 审批人"校验中，VAR 项计入有效审批人。

## 4. 能力 B：分支连线「输出名称」

### 4.1 数据与设计器
- `FlowEdgeDTO` 增 `outputName`（映射到既有列 `WF_FLOW_EDGE.name`，**不新增列**）。
- `FlowDefService`：`insertGraphElements` 写边时 `edge.setName(dto.getOutputName())`；`toEdgeDTO` 读回 `dto.setOutputName(edge.getName())`。
- `WfFlowEdgeMapper.xml` 的 BASE_COLUMNS 已含 `name`（确认），select 回得到。
- 前端 `FlowEdgePanel.vue` 增「输出名称」文本输入，绑定 `edge.outputName`；`FlowEdit.vue` 的 edge 规整/`buildPayload` 透传 `outputName`。

### 4.2 语义与运行时承载
- 语义：网关一条出边 = 一个"下一步走向"选项 =「输出名称(标签) + 条件(变量=值)」。
- 发布：`FlowBpmnGenerator` 生成 `SequenceFlow` 时 `sf.setName(outputName)`，把走向标签带入已部署 BPMN。Step 2 据此（结合边条件的变量/值）在经办审批页生成"走向"下拉并反显，本期不实现该 UI。

## 5. 能力 C：条件可引用自定义变量

- 前端 `ConditionBuilder.vue`：字段选择从"仅白名单下拉"改为 **`el-select` 允许 `filterable allow-create`**（可选已知变量，也可手输自定义变量名，如 `corpRouteTo`）。
- 后端 `FlowValidator` 的条件字段白名单校验**放宽**：允许字段不在 `FlowVariableCatalog` 内（路由类变量由本流程运行时产出，无法预登记）。仍保留 `op` 合法性、`value` 非空等基础校验，避免注入由 `FlowConditionExpressionBuilder` 仅生成 `${field op value}` 受控表达式保证。
- `FlowConditionExpressionBuilder` 不变（已支持任意 field）。

## 6. 能力 D：虚拟员工默认通过（泛化）

- `TaskAssignmentListener.notify`：把原 `if ("original_owner_approve".equals(nodeKey) && autoApproveIfVirtualOwner(...))` 的**节点名限制去掉**，改为"任意审批任务"：取任务受理人（assignee；会签多实例下为当前实例审批人 `${approver}`），若该受理人是虚拟员工（`UserApi.getUserByEmpId(empId).getUserType()=="2"`）→ `addComment("默认同意") + complete(approved=true)`，return。
- 无受理人的或签候选组节点：无单一 assignee，不触发（正确——候选组不存在"虚拟"概念）。
- 失败兜底不变：解析/完成异常吞掉转人工，保证流程不中断。

## 7. 影响面与隔离

| 改动点 | 文件 | 类型 |
|---|---|---|
| VAR 展开 | `TaskAssignmentListener` / `MultiInstanceApproverResolver` | 各加 `case "VAR"` |
| 虚拟员工泛化 | `TaskAssignmentListener` | 去 nodeKey 限制 |
| outputName 映射 | `FlowEdgeDTO` / `FlowDefService` | 复用 `name` 列 |
| outputName 入 BPMN | `FlowBpmnGenerator` | `sf.setName` |
| 条件白名单放宽 | `FlowValidator` | 去字段白名单拦截 |
| 设计器 UI | `ApproverPicker.vue` / `FlowEdgePanel.vue` / `ConditionBuilder.vue` / `FlowEdit.vue` | 前端 |

无 schema 变更（B 复用既有列）。无跨模块 API 变更。

## 8. 测试（TDD）

- `CandidateResolverServiceTest`：VAR 加前缀 `"VAR:xxx"`（resolveCandidates 已通用，补 1 case 确认）。
- `MultiInstanceApproverResolverTest`（新增/补）：VAR 变量为 List → approverEmpIds 展开；为 String → 单元素；为空 → 空集。
- `TaskAssignmentListenerTest`：
  - VAR 候选展开（或签路径 addCandidateGroup / 机构过滤路径不冲突）；
  - 虚拟员工泛化：非 `original_owner_approve` 节点、assignee 为虚拟员工 → 自动 complete(approved=true)；普通员工 → 不自动完成；候选组节点（无 assignee）→ 不触发。
- `FlowDefServiceTest`：edge `outputName` 往返（save 落 `name` 列、get 读回）。
- `FlowBpmnGeneratorTest`：边带 outputName → 生成的 SequenceFlow.name 等于 outputName。
- `FlowValidatorTest`：条件字段为自定义变量（不在 catalog）→ 校验通过。
- 前端：`vite build` 通过 + 手工验证矩阵（ApproverPicker 选 VAR、FlowEdgePanel 填输出名称、ConditionBuilder 手输变量名）。

## 9. 交付顺序（供实现计划参考）

1. 后端 D（虚拟员工泛化）：TDD 改 `TaskAssignmentListener` + 测试。
2. 后端 A（VAR 展开）：两个 resolver 加 case + 测试。
3. 后端 B（outputName 映射 + BPMN name）：DTO/Service/Generator + 测试。
4. 后端 C（FlowValidator 放宽）：去字段白名单 + 测试。
5. 前端：ApproverPicker VAR、FlowEdgePanel 输出名称、ConditionBuilder allow-create、FlowEdit 透传；`vite build`。
6. install + 重启 + 手工验证矩阵。

## 10. Step 2 预告（不在本期）

用设计器按现网结构重建对公/零售两条流程（审批人含 VAR=originalOwnerEmpIds、网关分支带输出名称与 `corpRouteTo/finRouteTo` 条件）；perf 审批页读已部署 BPMN 的走向选项做动态选择 + 反显；发布到影子 key `DSN_*`，验证无误后切换替代静态 BPMN。
