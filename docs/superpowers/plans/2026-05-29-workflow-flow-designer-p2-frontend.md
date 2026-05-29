# 审批流程设计器 P2（前端设计器）实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development。逐任务执行。
> **子代理红线**：派遣 subagent 时 model 必须 ≥ sonnet（禁 haiku）。
> **前端仓库红线**：前端代码在 **wangyq** 仓库（`/home/djdev/wangyq/yiti/xanzc_frontend`，vite 实跑此处），**不是** lf。所有前端文件创建/编辑/提交都在 wangyq；提交用 `git -C /home/djdev/wangyq/yiti`。本计划文档本身存于 lf docs。

**Goal:** 在 xanzc_frontend 建「审批流程管理」表单式设计器：列表查看所有流程 + 编辑页增删改环节/审批人(角色·机构·人 多条件并存·或签会签)/条件分支，对接 P1 后端 `/api/admin/workflow/flows`。

**Architecture:** 纯前端，Vue3 + Element Plus。新增 1 个 api 封装 + 2 个可复用子组件（审批人选择器、条件构造器）+ 列表页 + 编辑页 + 路由菜单。对接已上线的 P1 后端（dev 18080，会话鉴权）。

**Tech Stack:** Vue3 `<script setup>` + Element Plus 2.5 + axios（`@/api/http` 的 `call`/`unwrapPage`）+ vue-router(hash)。无前端测试框架 → 每任务验证 = `npm run build` 编译通过；最终任务手工冒烟。

**依据 spec:** `docs/superpowers/specs/2026-05-29-workflow-flow-designer-design.md` §9。

## 前端既有约定（必须遵循）
- API 封装：`src/api/*.js`，用 `import { call, unwrapPage } from './http'`；`call(method, path, {params|data}, fallback)` 返回 Promise，已自动 unwrap ResponseWrapper.data；列表分页用 `unwrapPage`（返回 `{records,total}` 或数组）。
- 页面范式：`src/views/<域>/*.vue`，`<script setup>` + `ref/reactive` + `ElMessage`/`ElMessageBox` + `onMounted`；模板用 `.page-h`（标题+actions）+ `.card-section` + `el-table` + `el-dialog` + `el-form`。参照 `src/views/system/Roles.vue`（列表+弹窗+嵌套子项）与 `src/views/perf/KpiRules.vue`。
- 路由/菜单：`src/router/index.js`，在 children 加条目，`meta:{title, group:'系统设置'}` 自动进侧边栏分组。
- 鉴权：`withCredentials` 走 yiti session；管理端点需登录管理员（PT_RESOURCE 已登记并绑 20 角色）。

---

## Task 1: API 封装 `flowDesign.js`

**Files（wangyq）:** Create `src/api/flowDesign.js`

- [ ] **Step 1: 写封装**（对接 P1 的 7 端点；列表用 unwrapPage 取 records）

```js
import { call, unwrapPage } from './http';

// 审批流程设计器（对接 workflow-center FlowDesignController /api/admin/workflow/flows）

/** 流程列表（全部）→ 数组 */
export function listFlows() {
  return call('get', '/admin/workflow/flows', {}, []).then(r => Array.isArray(r) ? r : (r?.records || []));
}
/** 取流程完整模型（节点+审批人+连线） */
export function getFlow(id) {
  return call('get', `/admin/workflow/flows/${id}`, {}, null);
}
/** 新建流程，返回新 flowDefId */
export function createFlow(graph) {
  return call('post', '/admin/workflow/flows', { data: graph }, null);
}
/** 保存草稿（整图替换） */
export function saveFlow(id, graph) {
  return call('put', `/admin/workflow/flows/${id}`, { data: graph }, { ok: true });
}
/** 发布（校验→生成→部署影子 key） */
export function publishFlow(id) {
  return call('post', `/admin/workflow/flows/${id}/publish`, { data: {} }, { ok: true });
}
/** 删除草稿 */
export function deleteFlow(id) {
  return call('delete', `/admin/workflow/flows/${id}`, {}, { ok: true });
}
/** 条件分支可用变量白名单 */
export function listFlowVariables(bizType) {
  return call('get', '/admin/workflow/flows/meta/variables', { params: { bizType } }, []);
}
```

- [ ] **Step 2: 验证编译** Run（wangyq 目录）：`npm run build` → 无报错（新文件被引用前不会 tree-shake 报错，确认语法 OK 即可；本任务后续页面会引用）。
- [ ] **Step 3: Commit**（在 wangyq）
```
git -C /home/djdev/wangyq/yiti add xanzc_frontend/src/api/flowDesign.js
cat > /tmp/p2t1.txt <<'EOF'
feat(perf-fe): 审批流程设计器 API 封装
EOF
git -C /home/djdev/wangyq/yiti commit -F /tmp/p2t1.txt && rm -f /tmp/p2t1.txt
```

---

## Task 2: 审批人选择器组件 `ApproverPicker.vue`

**Files（wangyq）:** Create `src/views/system/flow/ApproverPicker.vue`

职责：编辑一个节点的「审批人规则」列表（多行并存=并集）。每行 = `{approverType: ROLE|ORG|USER, approverValue}`。`v-model` 绑定 `List<{approverType,approverValue}>`。

- [ ] **Step 1: 先确认可复用的下拉数据源**（grep wangyq）：角色列表（`src/api/*.js` 里找 listRoles / roles）、机构列表（orgs.js 找 list/tree）、人员检索（users.js / employees.js 找 list/search）。用现有方法；找不到合适的就用 `call('get','/auth/roles'...)` 之类按现有后端端点（先 grep 现有页面怎么取角色/机构/人员下拉，照搬，如 Permission.vue / Users.vue）。

- [ ] **Step 2: 实现组件**（Element Plus）：
  - props: `modelValue: Array`；emits: `update:modelValue`。
  - 一个小 `el-table` 或 `el-row` 列表，每行：`el-select` 选类型(角色/机构/指定人) + 第二个 `el-select`（按类型加载对应选项：ROLE→角色码 select；ORG→机构 select；USER→人员可搜索 select `filterable remote`）+ 删除按钮。
  - 底部「+ 添加审批人」按钮 push 一行。
  - 任何变更 emit 最新数组。
  - 角色/机构选项 onMounted 拉一次缓存；人员用 remote search。

- [ ] **Step 3: 验证** `npm run build` 通过。
- [ ] **Step 4: Commit**：`feat(perf-fe): 审批人选择器组件（角色/机构/人 多条件）`。

---

## Task 3: 条件构造器组件 `ConditionBuilder.vue`

**Files（wangyq）:** Create `src/views/system/flow/ConditionBuilder.vue`

职责：编辑一条连线的分支条件。`v-model` 绑定 `{logic:'AND'|'OR', conditions:[{field,op,value}]}` 或 null（无条件）。

- [ ] **Step 1: 实现**：
  - props: `modelValue`（条件对象或 null）, `variables: Array<{field,label,type}>`（来自 listFlowVariables，父组件传入）；emits update:modelValue。
  - UI：一个「逻辑」单选 AND/OR（多于 1 条件时显示）；条件行列表，每行 = `el-select` 选 field（options=variables，显示 label）+ `el-select` 选 op（EQ/NE/GT/GE/LT/LE/IN/NOT_IN/CONTAINS，中文标签如「等于/不等于/大于…/属于/不属于/包含」）+ `el-input` 值（IN/NOT_IN 提示「逗号分隔」）+ 删除；「+ 添加条件」。
  - 空条件（0 行）→ emit null（表示无条件直连/默认分支）。
- [ ] **Step 2: 验证** `npm run build` 通过。
- [ ] **Step 3: Commit**：`feat(perf-fe): 条件构造器组件（字段+运算符+值, AND/OR）`。

---

## Task 4: 流程列表页 `FlowList.vue` + 路由菜单

**Files（wangyq）:** Create `src/views/system/FlowList.vue`；Modify `src/router/index.js`

- [ ] **Step 1: 路由**：在 `src/router/index.js` 系统设置组（system/* 那批）后追加两条：
```js
{ path: 'system/workflow-flows', name: 'SysWorkflowFlows', component: () => import('@/views/system/FlowList.vue'), meta: { title: '审批流程', group: '系统设置' } },
{ path: 'system/workflow-flows/:id', name: 'SysWorkflowFlowEdit', component: () => import('@/views/system/FlowEdit.vue'), meta: { title: '审批流程编辑', group: '系统设置' } },
```
（FlowEdit.vue 在 Task 5 创建；本任务先建 FlowList 与路由，build 时 FlowEdit 的动态 import 不影响编译——但为保险 Task 5 未完成前可先建一个最简占位 FlowEdit.vue，Task 5 再补全。）

- [ ] **Step 2: 实现 FlowList.vue**（参照 Roles.vue 范式）：
  - `.page-h`：标题「审批流程」+ 副标题「查看/编辑审批流程，发布后另行切换生效（不影响现有线上流程）」+ actions：刷新、+ 新建流程。
  - `el-table`（`listFlows()` 数据）：列 名称(name)、业务类型(bizType)、状态(status: DRAFT 草稿/PUBLISHED 已发布 用 el-tag)、版本(version)、最后更新(updatedTime, 用 `@/utils/datetime` fmtDateTime)、只读标(isReadonlyImport=1 显示「只读导入」tag)。
  - 操作列：查看/编辑（`router.push('/system/workflow-flows/'+id)`）、发布（`publishFlow(id)` + 确认框 + 成功/失败 ElMessage，失败把后端校验 message 弹出不消失）、克隆（取 getFlow→去 id→createFlow，跳编辑）、删除（仅 DRAFT 且非只读，ElMessageBox 确认→deleteFlow）。
  - 新建：弹窗填 name + bizType（ALLOC_ADJUST/TARGET_ADJUST 下拉）→ createFlow({name,bizType,nodes:[],edges:[]})→跳编辑页。
- [ ] **Step 3: 验证** `npm run build` 通过。
- [ ] **Step 4: Commit**：`feat(perf-fe): 审批流程列表页 + 路由菜单`。

---

## Task 5: 流程编辑页 `FlowEdit.vue`（核心）

**Files（wangyq）:** Create（或替换占位）`src/views/system/FlowEdit.vue`

参照 Roles.vue/KpiRules.vue 范式，组合 Task 2/3 组件。页面以 nodeKey 关联（与后端 FlowGraphDTO 对齐）。

- [ ] **Step 1: 实现**：
  - onMounted：路由 `:id` → `getFlow(id)` 取 graph（{name,bizType,nodes,edges}）；`listFlowVariables(graph.bizType)` 取变量白名单存 ref。只读导入（后端 readonly）则禁用保存/发布按钮并提示「只读，请用『克隆』另存后编辑」。
  - **基本信息**卡：名称(el-input)、业务类型(只读展示)。
  - **节点列表**卡：`el-table` 绑 `graph.nodes`，列 节点标识(nodeKey)、名称(name)、类型(nodeType: START/APPROVAL/GATEWAY/END 下拉)、审批模式(approveMode: 仅 APPROVAL 显示，或签 ANY/会签 ALL 下拉)。每行可展开/弹窗编辑审批人（APPROVAL 行用 `<ApproverPicker v-model="node.approvers">`）。增/删节点按钮。nodeKey 唯一性前端校验。
  - **连线列表**卡：`el-table` 绑 `graph.edges`，列 起点(fromNodeKey, el-select 选自 nodes)、终点(toNodeKey, el-select)、默认分支(isDefault, el-switch)、条件（弹窗内嵌 `<ConditionBuilder v-model="edge.condition" :variables="variables">`，列表里显示条件摘要文本）。增/删连线。
  - **操作**：保存草稿（`saveFlow(id, graph)` + ElMessage）、发布（`publishFlow(id)`，失败把校验 errors 弹 ElMessageBox 长文不消失）、返回列表。
  - **结构预览**卡（只读）：把 nodes/edges 文字化（如「① start → branch_approve[机构负责人审批/会签] → … 」+ 分支条件文字），首期纯文本/缩进，不画图。
  - 前端轻校验（发布前给提示，真校验以后端为准）：恰好 1 START、≥1 END、APPROVAL 必有审批人。
- [ ] **Step 2: 验证** `npm run build` 通过。
- [ ] **Step 3: Commit**：`feat(perf-fe): 审批流程编辑页（节点/审批人/连线/条件/预览）`。

---

## Task 6: 联调冒烟 + 收尾

- [ ] **Step 1: 构建**：`npm run build` 全量通过（无 TS/编译错误）。
- [ ] **Step 2: 手工冒烟**（对运行中后端 18080，需开发自行登录管理员账号）：
  - 进「系统设置 → 审批流程」，列表能加载（至少空列表不报错）。
  - 新建一条流程 → 编辑加 START/APPROVAL(配审批人)/END + 连线 → 保存草稿 → 发布；发布成功后版本+1、状态 PUBLISHED。
  - 校验失败场景（如 APPROVAL 无审批人）→ 发布被后端拒绝并弹出 errors。
  - 确认**现有审批（业绩调整/目标审批）行为不变**（影子 key，不切换）。
  - 把冒烟结果（每步 OK/异常）写入回报；不能自动登录时，列出需人工验证的清单交用户。
- [ ] **Step 3: Commit（若有冒烟修复）**：`fix(perf-fe): 审批流程设计器联调修复`。

---

## Self-Review
- **Spec §9 覆盖**：列表页=Task4；表单式编辑(节点增删改/审批人多条件/或签会签/连线条件)=Task5 + 组件 Task2/3；结构预览=Task5；对接 7 端点=Task1。
- **零影响**：纯前端新增页面 + 新菜单，不改现有页面/路由语义；调用的是 P1 影子 key 流程，不切换线上。
- **前端仓库**：全部 wangyq 提交（不混入 lf）。
- **类型一致**：graph 以 nodeKey 关联（与后端 FlowGraphDTO 对齐）；condition 形 `{logic,conditions:[{field,op,value}]}`（与后端 FlowConditionDTO 对齐）；approver 形 `{approverType,approverValue}`。
- **缺口**：角色/机构/人员下拉的具体复用 API 在 Task2 Step1 由实现者 grep 现有页面确定（Permission.vue/Users.vue 已有同类下拉）。
