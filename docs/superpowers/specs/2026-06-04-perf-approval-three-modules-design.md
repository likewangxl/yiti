# 手机端业绩调整审批拆分三模块（我的申请 / 待审批 / 已审批）设计稿

- 日期：2026-06-04
- 范围：`front/xazc_transfer_front`（仅 `.vue`，路由由对方工程登记）+ `yiti`（soap-gateway-center 网关 + performance-engine-center 查询接口）
- 目标渠道：手机端 callpu 网关（`POST /api/callpu` 与 Netty SOAP 共用 `CallPuDispatchService`）

## 1. 背景与问题

当前手机端"业绩调整"只有一个合并审批列表 `list.vue`，调 `PERF_LIST` →
`PerfApprovalQueryApi.listAllocAdjustApprovals(empId,...)`，返回的是**审批人视角**的
"待审批+已审批合并"列表（`PerfListData` 注释明确）。

由此产生两个缺口：

1. **没有"我的申请"**：后端无任何按申请人（`createdBy`）查询的接口，用户看不到自己提交的单子进展。
2. 三类数据混在一页，用户认知不清；新增不应出现在审批列表里，审批也不应出现在申请列表里。

同时管理端新增时会"先取客户原分配关系并回显，查不到再手动添加"，手机端 `applyAdd.vue`
目前只有空行手动添加，缺这一步。

## 2. 目标

- `index.vue` 首页去掉"业绩调整"入口，新增三块导航：我的申请 / 待审批 / 已审批。
- 三个列表各自独立成页（**不复用** `list.vue`，`list.vue` 删除），便于后续差异化演进。
- 每个模块只操作与自己相关的数据：新增只在"我的申请"，审批只在"待审批"，"已审批"只读。
- 新增页按客户号/类型自动拉取原分配关系并回显，查不到则手动添加，与管理端一致。

## 3. 非目标（YAGNI）

- 列表分页（≤100 条一次性返回，沿用现状）。
- 详情页拆分（三模块共用 `applyInfo.vue` 一份，仅按上下文收敛操作按钮）。
- 路由表本身（在对方工程，本仓只提供交接清单，见 §8）。

## 4. 后端设计

### 4.1 performance-engine-center

- **新增** `PerfApprovalQueryApi.listMyAllocAdjustApplications(String empId, int pageNo, int pageSize)`
  - 申请人视角：按 `createdBy = empId` 过滤，全状态。
  - 复用 `AllocAdjustApprovalItemDTO`（含 `perfAdjustNo/custName/applyFullname/applyTime/status/createdBy`）。
  - 配套 facade/service/mapper + TDD（红-绿-重构）。
- **扩展审批查询的状态过滤**：新增重载
  `listAllocAdjustApprovals(String empId, String statusFilter, int pageNo, int pageSize)`
  - `statusFilter ∈ {PENDING, DONE, null}`；`PENDING` = 待审（DRAFT/IN_APPROVAL），`DONE` = 已审（APPROVED/REJECTED）。
  - 保留旧 3 参重载，委托给 `statusFilter=null`（向后兼容现有调用）。
- `AllocApi.getCurrentAllocations(custId, bizKind)` 已存在，**不改**。
- `UserApi.getUserByEmpIds(List)` 已存在（USER_ID → `username`/`displayName`），原分配回显复用。

### 4.2 网关 soap-gateway-center

DTO：

- `CallPuRequest.Parm` 增字段 `queryStatus`（列表用，值 `PENDING`/`DONE`，`@JsonProperty` 对齐报文）。
- 新增响应 DTO `OrigAllocData`，结构 `{ allocaters: [ {username, fullname, ratio, isOriginal} ] }`。

`CallPuDispatchService.dispatch` 新增/调整三条分支：

| RuleName | handler | 行为 |
|---|---|---|
| `PERF_MY_LIST`（新） | `handleMyList` | `resolveUserId(empId)` → `listMyAllocAdjustApplications` → `toListItem` |
| `PERF_LIST`（改） | `handlePerfList` | 读 `parm.queryStatus` 传入扩展后的 `listAllocAdjustApprovals(userId, statusFilter, ...)` |
| `PERF_ORIG_ALLOC`（新） | `handleOrigAlloc` | `custType=APPLY_TYPE_TO_CUST_TYPE.get(applyType)`、`bizKind=toBizKind(custType, businessType)`（复用现有映射）→ `getCurrentAllocations(custId, bizKind)` → 用 `getUserByEmpIds` 把 `empId(USER_ID)` 反查成 `username`(工号)，`empName` 作姓名 → 回 `OrigAllocData`，每行 `isOriginal=1` |

约束沿用现状：始终 HTTP 200；业务异常经 `dispatch` try-catch 降级为 `CallPuResponse.fail`；跨模块只走 `*Api`。

每个 handler 补 `CallPuDispatchServiceTest` 用例。

### 4.3 状态码映射（我的申请差异化所需）

已确认 `AllocAdjustService` 撤回逻辑：`IN_APPROVAL/DRAFT → WITHDRAWN`（撤回不回草稿，置独立终态）。

现有 `toApprStatus`：APPROVED→"1"、REJECTED→"2"、其余→"0"。在"我的申请"需新增"已撤回"：

- `toApprStatus` 扩展：`WITHDRAWN → "3"`（已撤回）。前端"我的申请"据此显灰色"已撤回"，**仅查看、无任何操作按钮**（详情侧 `isCanDelete` 对 WITHDRAWN 本就为否，天然只读）。
- 状态过滤：`PENDING` = DRAFT/IN_APPROVAL，`DONE` = APPROVED/REJECTED；WITHDRAWN 只出现在"我的申请"全量列表，审批人两页不含。
- 待审批/已审批两页只用 0 / 1·2，不受影响。

## 5. 前端设计

### 5.1 `views/index.vue`

`typeList` 删除"业绩调整"项，新增三项（`van-grid` column-num 仍 3，自动换行）：

- 我的申请 → `/performanceMyList`
- 待审批 → `/performancePending`
- 已审批 → `/performanceApproved`

图标先复用 `imgs/index/*`，最终首页为：头寸管理 / 定价审批 / 我的申请 / 待审批 / 已审批。

### 5.2 三个独立列表页（删除 `list.vue`，不复用）

| 文件 | 取数 | 底部动作 | 点击进入 |
|---|---|---|---|
| `performanceAdjustment/performanceMyList.vue` | `PERF_MY_LIST` | 新增 | `/performanceInfo`（可撤回） |
| `performanceAdjustment/performancePending.vue` | `PERF_LIST` + `queryStatus:'PENDING'` | 无 | `/performanceInfo`（可审批） |
| `performanceAdjustment/performanceApproved.vue` | `PERF_LIST` + `queryStatus:'DONE'` | 无 | `/performanceInfo`（只读） |

跳详情时带 `query.listType`（mine/pending/approved），供详情收敛操作按钮。

### 5.3 差异化展示

- **我的申请**：客户名称 + 申请时间 + 状态徽标（待审核/已同意/已拒绝/已撤回，颜色区分）；底部固定"新增"。
- **待审批**：突出申请人 + 客户名称 + 申请时间；左侧蓝色待办竖条/"待审批"角标，不显状态徽标；强调"等我处理"。
- **已审批**：审批结果徽标（已同意=绿 / 已拒绝=红）+ 客户名称 + 审批时间；灰底只读观感。

### 5.4 `applyAdd.vue`（原分配自动预填）

- 新增 `fetchOrigAlloc()`：当 `custId && applyType && businessType` 三者齐备时调 `PERF_ORIG_ALLOC`；
  在 `custIdChange`、`onApplyTypeConfirm`、`businessType1` 三处触发（任一变化重拉）。
- 合并规则：原分配行 `isOriginal=1`（只读底色）、手动行 `isOriginal=2`（可编辑）；重拉只替换
  `isOriginal=1` 部分、保留手动行；查不到保留空行手动添加。

### 5.5 `applyInfo.vue`（微调）

底部"审批/撤回"已由 `isCanAppr/isCanDelete` 标志位控制；在 `listType==='approved'` 上下文兜底隐藏
所有操作按钮，并展示审批意见历史（`records`）。

### 5.6 报文封装小工具（去重，**不走 `@CF`**）

把双层嵌套 `params/Parm/RuleName/IntType/SrvicName1` 封装抽到
`performanceAdjustment/callpu.js`，三个列表页 + 新增/详情用**相对路径** `import { buildParam } from "./callpu"`
调用；展示模板各自独立，不影响差异化。

签名草案：`buildParam(ruleName, parm, intType = 'get')` 返回现有 `{ params: { params:{Parm:parm}, Parm:parm, RuleName, IntType, SrvicName1: sessionStorage.parameters } }` 结构。

## 6. 数据流

```
首页 index.vue ──► /performanceMyList ──PERF_MY_LIST──► listMyAllocAdjustApplications(createdBy=me)
              ├──► /performancePending ─PERF_LIST(PENDING)─► listAllocAdjustApprovals(me, PENDING)
              └──► /performanceApproved ─PERF_LIST(DONE)───► listAllocAdjustApprovals(me, DONE)

新增 applyAdd.vue ─(custId+applyType+businessType 齐备)─► PERF_ORIG_ALLOC
     ► getCurrentAllocations(custId,bizKind) ► USER_ID→username/empName ► 回显 flexList(isOriginal=1)
```

## 7. 测试

- 后端：`CallPuDispatchServiceTest` 为 `handleMyList`/`handlePerfList(queryStatus)`/`handleOrigAlloc`
  各补用例；perf `listMyAllocAdjustApplications` 配套 service/mapper 测试。严格 TDD。
- 本机编译用 JDK 17（lombok 1.18.30，避免 JDK 26 假失败）。
- 前端：手工验证三页取数/动作隔离、新增原分配回显与手动兜底。

## 8. 交接给路由工程

新增三条路由：

- `/performanceMyList` → `performanceMyList.vue`
- `/performancePending` → `performancePending.vue`
- `/performanceApproved` → `performanceApproved.vue`

`/performanceAdd` `/performanceInfo` `/performanceApprovel` 不变（`/performanceInfo` 接受可选
`query.listType`）。原"业绩调整"`/performanceList` 路由可下线（`list.vue` 删除）。

## 9. 风险与开放项

- ~~§4.3 撤回后的 `status` 实际取值~~（已定：`WITHDRAWN`，映射 apprStatus="3"，只读查看）。
- `PERF_ORIG_ALLOC` 的 `bizKind` 依赖业务类型已选；失焦时若未选则不触发（不空拉），符合"自动但信息齐备才拉"。
- 首页五块图标暂复用，需业务侧提供正式图标。
