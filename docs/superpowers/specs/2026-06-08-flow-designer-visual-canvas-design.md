# 审批流程设计器 - 可视化流程图画布 设计规格

- 日期：2026-06-08
- 模块：workflow-center（后端微调）+ xanzc_frontend（前端重写交互层）
- 状态：设计待评审

## 1. 背景与目标

审批流程设计器后端已完整交付（`FlowDesignController` + 4 张 `WF_FLOW_*` 表 + 草稿/发布/导入全链路），前端当前 `FlowEdit.vue` 是**表格式**编辑器：两张 el-table（节点表、连线表）+ 一段只读文本结构预览，节点/连线靠下拉框选 nodeKey 关联，不直观。

**本次目标**：把前端编辑器重写成**流程图样式的可视化画布**：

- 画布上以流程图节点形态展示 开始 / 审批 / 网关 / 结束 节点，节点可拖拽定位；
- 节点之间用**带方向箭头的连线**连接，表达流转方向；网关出边即**动态条件分支**；
- **选中一个审批节点** → 页面右侧边栏显示该审批环节参数编辑（名称、审批模式、审批人）；
- **选中一条流转连线** → 右侧边栏显示该连线的动态条件分支参数增删改（条件构造 + 默认分支）。

**非目标（YAGNI）**：不做缩放/小地图/撤销重做/自动布局/多选框选/对齐辅助线；不替换后端数据模型；不引入任何图形库。

## 2. 复用与改动边界

### 2.1 后端（唯一必要改动）

`WF_FLOW_NODE` 表与 `WfFlowNode` 实体已有 `pos_x`/`pos_y`，但 `FlowNodeDTO` **未暴露** `posX`/`posY`，导致画布坐标无法随整图存取、刷新后布局丢失。

改动：
- `FlowNodeDTO` 增加 `private Integer posX; private Integer posY;`
- `FlowDefService`（整图存/取的映射处）补 entity ↔ DTO 的 `posX/posY` 双向映射。
- 兼容旧数据：`posX/posY` 为 null 时前端按入场自动布局兜底（见 4.4）。

**不动**：控制器端点、表结构、发布/导入逻辑、`FlowGraphDTO/FlowEdgeDTO/FlowApproverDTO/FlowConditionDTO`、`flowDesign.js` API。

### 2.2 前端复用

- `api/flowDesign.js`：`getFlow / saveFlow / publishFlow / listFlowVariables` 原样复用。
- `flow/ApproverPicker.vue`（v-model = `[{approverType, approverValue}]`）→ 嵌入右栏节点面板。
- `flow/ConditionBuilder.vue`（v-model = `{logic, conditions:[{field,op,value}]}` 或 null，+ `variables` prop）→ 嵌入右栏连线面板。
- 路由 `system/workflow-flows/:id` → `FlowEdit.vue` 不变。
- 业务类型映射、`validateGraph` 校验、`buildPayload` 整图组装、保存/发布交互**逻辑复用**，仅 UI 壳重写。

### 2.3 前端重写

`FlowEdit.vue` 整体重写为「左工具盘 + 中 SVG 画布 + 右属性栏」三栏布局，新增若干画布子组件（见第 3 节）。

## 3. 组件拆分

保持单一职责、小文件、清晰边界：

| 组件 | 路径 | 职责 | 依赖 |
|------|------|------|------|
| `FlowEdit.vue`（重写） | `views/system/FlowEdit.vue` | 页面容器：加载/保存/发布、持有 `graph` 状态、selection 状态、三栏布局编排 | 下列子组件 + flowDesign API |
| `FlowCanvas.vue`（新） | `views/system/flow/FlowCanvas.vue` | 中间 SVG 画布：渲染节点与连线、节点拖拽、拉线建边、点击选中、emit 选中/改动事件 | 无（纯 SVG + Vue） |
| `FlowNodePanel.vue`（新） | `views/system/flow/FlowNodePanel.vue` | 右栏-选中节点时：名称/审批模式/审批人编辑 | `ApproverPicker` |
| `FlowEdgePanel.vue`（新） | `views/system/flow/FlowEdgePanel.vue` | 右栏-选中连线时：默认分支开关 + 条件分支增删改 | `ConditionBuilder` |
| `FlowPalette.vue`（新） | `views/system/flow/FlowPalette.vue` | 左栏：可拖出的节点类型（审批 / 网关），开始/结束节点提示 | 无 |

`FlowCanvas` 不直接改 graph 内部，所有变更通过 emit 上抛由 `FlowEdit` 统一写 `graph`（单向数据流，便于推理与测试）。

## 4. 画布交互设计（FlowCanvas）

### 4.1 节点形态（流程图风格）

- START「开始」：圆角胶囊/圆形，绿色边。
- END「结束」：圆角胶囊/圆形，红/灰边。
- APPROVAL「审批」：矩形卡片，显示节点名 + 审批模式徽标（会签/或签）+ 审批人数。
- GATEWAY「网关」：菱形，表示条件分流。

节点用绝对定位的 div 叠在 SVG 之上（节点内容用 HTML 好排版），连线用底层 `<svg>` 的 `<path>` 绘制，二者共用同一坐标系。

### 4.2 拖拽定位

- 鼠标按住节点体拖动 → 实时更新该节点 `posX/posY`（mousemove），松开落定；
- 画布相对坐标，min 边界 0，节点不可拖出画布左/上边界；
- 拖动中重算与该节点相连的所有边路径。

### 4.3 连线（建边 / 方向 / 选中）

- 节点 hover 时显示连接锚点（右侧/下侧小圆点）；从锚点 mousedown 拉出临时线，松开命中目标节点 → 新增一条 `edge{fromNodeKey,toNodeKey,isDefault:false,condition:null}`；
- 边为有向：`<path>` + `<marker>` 三角箭头指向目标；源/目标锚点取两节点中心连线与节点边框交点，走折线或三次贝塞尔；
- 点击边 → 该边高亮（描边加粗变色）并 emit 选中；
- 建边即时校验：禁止自环（from==to）、禁止指向 START、禁止从 END 出边、禁止完全重复边（同 from+to）；命中则 `ElMessage.warning` 拒绝。

### 4.4 入场布局兜底

打开流程时：

- 若节点都带有效 `posX/posY` → 按存储坐标还原；
- 若缺坐标（旧草稿/导入流程）→ 用简单分层布局：按 `sortNo`/拓扑次序纵向（或横向）等距摆放，保证可见可用，用户拖动后保存即固化坐标。

### 4.5 选中与右栏联动

- 画布维护 `selection = {type:'node'|'edge'|null, key}`；
- 选中节点 → `FlowEdit` 右栏挂 `FlowNodePanel`（传该 node 引用）；
- 选中连线 → 右栏挂 `FlowEdgePanel`（传该 edge 引用 + variables）；
- 空白处点击 → 清空选中，右栏显示空态提示；
- 子面板通过 v-model/引用直接写回 node/edge 对象（与现有弹窗式一致），即时反映到画布徽标（审批人数等）。

### 4.6 删除

- 选中节点按 Delete 键或右栏「删除节点」按钮 → 删节点并级联删除其相关边；START/END 视为可删但保存/发布校验会拦（沿用 `validateGraph` 要求恰好 1 START、≥1 END）。
- 选中边 → 右栏「删除连线」。

## 5. 数据流与保存

- `graph = reactive({name,bizType,nodes:[...],edges:[...]})`，节点项含 `posX/posY`。
- 保存：`buildPayload()` 在现有基础上**透传 `posX/posY`**，其余字段组装不变 → `saveFlow(id, payload)`。
- 发布：沿用「先存草稿 → `validateGraph` 强校验 → `publishFlow`」流程，校验项不变（1 START、≥1 END、nodeKey 非空唯一、APPROVAL 至少 1 审批人）。
- 只读导入流程（`readonly`）：画布只读，禁用拖拽/建边/属性编辑，仅供查看，保持与现状一致的「克隆后再编辑」提示。

## 6. 错误处理

- 加载失败：`ElMessage.error`，画布空态。
- 建边非法：`ElMessage.warning` 即时拒绝，不入图。
- 保存/发布失败：沿用现有 `ElMessage` / `ElMessageBox.alert` 展示后端校验明细。
- 变量目录加载失败：`variables=[]`，`ConditionBuilder` 自带空提示。

## 7. 测试

- 后端：`FlowDefService` 整图存取的单测补 `posX/posY` 往返断言（TDD：先红——断言取回坐标，当前 DTO 无字段→失败，再补字段转绿）。
- 前端：无既有单测框架，采用手工验证矩阵——
  1. 新建/打开流程 → 节点按坐标或兜底布局渲染；
  2. 拖动节点 → 连线跟随；保存后刷新 → 坐标保持；
  3. 锚点拉线建边 → 箭头方向正确；非法建边被拒；
  4. 点节点 → 右栏出节点参数，改审批人/模式即时反映徽标；
  5. 点连线 → 右栏出条件构造，增删改条件 + 默认分支开关回写；
  6. 校验不通过时发布被拦截、提示明细；
  7. 只读导入流程不可编辑。

## 8. 交付顺序（供实现计划参考）

1. 后端 `FlowNodeDTO` + `FlowDefService` 加 `posX/posY`（TDD 红→绿）+ install。
2. `FlowCanvas.vue`：节点渲染 + 拖拽 + 入场布局。
3. 连线渲染（path + 箭头）+ 锚点建边 + 选中高亮 + 非法校验。
4. 右栏 `FlowNodePanel` / `FlowEdgePanel` + 左栏 `FlowPalette`。
5. `FlowEdit.vue` 三栏编排：selection 联动、保存/发布接现有 API、只读态。
6. 前端启动手工验证矩阵全过。
