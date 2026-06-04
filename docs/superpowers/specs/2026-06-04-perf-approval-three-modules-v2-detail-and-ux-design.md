# 手机端业绩调整三模块 V2：详情接口 + 交互/样式优化 设计稿

- 日期：2026-06-04（承接同日 v1 `2026-06-04-perf-approval-three-modules-design.md`）
- 范围：`front/xazc_transfer_front`（applyAdd / applyInfo / performanceMyList / performanceApproved + 样式）+ `yiti`（perf 详情 Api + 网关 PERF_INFO）
- 已锁定决策：① 我的申请去掉底部「新增」按钮，仅顶部 add-o 入口；② 单个 `applyInfo.vue` 按 `listType` 分支；③ 已审批折叠展示「原分配 + 调整后分配」两份对比。

## 1. 背景

v1 已把列表拆为我的申请/待审批/已审批三页并打通列表/新增/原分配预填。本轮按真实 `applyAdd` 源码优化新增页样式，并补齐三处交互；同时补上**详情数据源**——前端 `applyInfo.vue` 调 `PERF_INFO`，但网关无此分发，perf 也无对外详情 Api，导致详情页、待审批审批、已审批分配展示都缺数据。

## 2. 跨切面后端依赖：`PERF_INFO` 详情接口（必做）

待审批审批（§5）与已审批折叠（§6）共用单据详情。需新增：

### 2.1 perf 对外 Api
`PerfApprovalQueryApi.getAllocAdjustDetail(String perfAdjustNo, String empId)` → 新 DTO `AllocAdjustDetailDTO`，**组合装配**：

| 来源 | 取数 |
|---|---|
| 单据主体 | 复用 `getById` 背后 service → custId/custName/custType/allocDim/bizKind/accountNo/status/remark(=调整理由)/createdBy/createdTime |
| 调整后分配明细 | `perf_alloc_adjust_item`（经 `PerfAllocAdjustItemMapper`）→ 每行 empId/ratio（+反查 username/姓名）；标 `isOriginal=2` |
| 原分配 | `AllocApi.getCurrentAllocations(custId, bizKind)` → 每行 empId/empName/ratio（反查 username）；标 `isOriginal=1` |
| 审批记录 | approval-history → `List<ApprovalLogDTO>`（operatorName/action/opinion/operateTime） |
| 能力标志 | `isCanAppr`=该单是 empId 的 Flowable 待办 且 status=IN_APPROVAL；`isCanDelete`=empId==createdBy 且 status∈{DRAFT,IN_APPROVAL} |

> 实现期确认 `perf_alloc_adjust_item` 的查询入口（若已有 detail service 返回 items 则复用；否则加一个 mapper 查询）。USER_ID→工号(USERNAME) 反查复用 `UserApi.getUserByEmpIds`。

### 2.2 网关分发 `PERF_INFO`
`CallPuDispatchService` 加分支 → `handlePerfInfo(parm)`：`resolveUserId(empId)` → `getAllocAdjustDetail(perfAdjustNo, userId)` → 翻译为前端 `dataForm` 约定回传：
- `applyType`：custType CORP→"1"/RETAIL→"2"
- `applyRule`：allocDim ACCOUNT→"1"/RULE→"2"
- `iouNo`=accountNo、`businessType`=bizKind 反译中文（可选，列表展示用）、`adjustExplain`=remark
- `apprStatus`：APPROVED→"1"/REJECTED→"2"/WITHDRAWN→"3"/else→"0"
- `allocaters`：原分配(isOriginal=1) + 调整后(isOriginal=2) 合并数组，每项 {username,fullname,ratio,isOriginal}
- `isCanAppr`/`isCanDelete`：0/1；`records`：审批记录数组

## 3. 新增页 `applyAdd.vue` 重做 + 样式优化

结构对齐真实源码（客户号/客户名称/申请类型/规则/账号借据号/业务类型/分配调整/调整理由/提交），保留 `fetchOrigAlloc` 预填。视觉优化：

- **卡片化分段**：基本信息 / 业务类型 / 分配调整 三段独立 `van-cell-group`，圆角 8px、白底、段间 10px 浅灰留白。
- 字段统一 `input-align="right"`；申请类型/规则保持 `is-link` 选择器。
- 业务类型药丸：选中蓝色描边 `#1a93f9` + 蓝字。
- **分配调整内联表格卡**（替代 `van-dialog`）：表头 工号/姓名/比例/操作；原分配行浅灰底 `#f7f7f7` + 「原」标签只读，手动行可编辑 + 删除；底部「+ 添加分配对象」。
- 提交按钮固定底部、主蓝、disabled 浅蓝 `rgba(26,147,249,.55)`。

## 4. 我的申请 `performanceMyList.vue`

- **顶部蓝色固定条**（`position:fixed`，高 44px，背景 `#1a93f9`，白字）：左标题「我的申请」，右末尾 `<van-icon name="add-o" />`（白色，`@click`→`/performanceAdd`）。列表 `padding-top:50px`。
- **去掉底部「新增」按钮**（决策①）。
- **列表项右下角「撤回」**：仅 `apprStatus==='0'` 显示（1/2/3 不显示）；`@click.stop` → 二次确认 `van-dialog` → `PERF_RECALL`（perfAdjustNo+EmployeeNo）→ 成功 toast + 就地 `getList()` 刷新，不跳转。

## 5. 待审批 详情（`applyInfo.vue`，listType=pending）

- 进入即 `PERF_INFO` 取详情渲染（含 allocaters 折叠可看，复用 §6 折叠组件）。
- 底部**不显示撤回**；显示**审批区**：`审批意见` `van-field type=textarea`（**必填**）+ 两按钮「审批通过」(绿 `#07c160`)/「审批不通过」(红 `#ee0a24`)。
- 提交校验：意见为空 → `this.$toast('请填写审批意见')` 拦截；通过=`PERF_APPR` apprStatus=1、不通过=2，带 apprOpinion → 成功返回列表。
- 退役「审批」跳 `approvel.vue` 的旧流程（审批内联到详情）。

## 6. 已审批 详情（`applyInfo.vue`，listType=approved）

- `van-collapse` 两个折叠项对比（决策③）：
  - 「原分配」= allocaters.filter(isOriginal==1)
  - 「调整后分配」= allocaters.filter(isOriginal==2)
  - 每项行展示：分配规则(allocDim 文案)、分配对象(工号 + 姓名)、分配比例(ratio%)。
- **不再「查看」跳 `radioList`**；只读，无操作按钮，无审批区。

## 7. 详情页结构（决策②）

单个 `applyInfo.vue`，`created` 读 `this.$route.query.listType`，分支：
- `pending`：审批区（§5）+ 折叠分配（只读）。
- `approved`：折叠分配对比（§6），无操作。
- `mine`：只读详情（撤回入口在列表，不在详情）。
公共：顶部单据主体字段 + 调整理由 + 审批记录折叠。`radioList.vue` / `approvel.vue` 随之退役（前端删除；路由由对方工程下线）。

## 8. 前端组件依赖
新引入 `Collapse`/`CollapseItem`、`Icon`（add-o）。其余沿用现有 Vant 组件。

## 9. 后端测试
- perf `getAllocAdjustDetail`：装配 + 能力标志 + USER_ID 反查 的单测（TDD）。
- 网关 `PERF_INFO`：字段/状态翻译、allocaters 合并、isCanAppr/isCanDelete 透传 的 `CallPuDispatchServiceTest` 用例。
- 前端人工验证。

## 10. 交接给路由工程
新增无新路由（沿用 `/performanceInfo?listType=`、`/performanceAdd`）；下线 `/apprHistory`(radioList) 与 `/performanceApprovel`(approvel) —— 如确认退役。

## 11. 风险/开放项
- §2.1 `perf_alloc_adjust_item` 的查询入口需实现期确认（复用已有 detail service 或新增 mapper 查询）。
- `isCanAppr` 依赖 Flowable 待办判定，复用 v1 `AllocAdjustTodoService` 候选组 ByEmp 逻辑；实现期确认可单条判定或用 listMyTodosByEmp 命中。
- businessType 中文反译（bizKind→中文）仅列表展示用，缺失不阻塞审批。
