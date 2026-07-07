# 三模块 V2（详情接口 + 交互/样式）实现计划

> REQUIRED SUB-SKILL: subagent-driven-development. Steps use `- [ ]`. Spec: `docs/superpowers/specs/2026-06-04-perf-approval-three-modules-v2-detail-and-ux-design.md`.

**Goal:** 补 `PERF_INFO` 详情接口；优化新增页样式；我的申请顶部 add-o + 列表撤回；待审批内联审批；已审批折叠展示分配对比。


## 已探明的后端事实（实现依据）
- `AllocAdjustService.getById(id)` → `ApplyWithItems{apply: PerfAllocAdjustApply, items: List<PerfAllocAdjustItem>}`。
- `PerfAllocAdjustItem` 字段：itemKind/empId/username/empChnName/ratio/acctNo。`itemKind`：`ORIGIN`=原分配、`NEW`=调整后（旧数据空→NEW）。
- 撤回守卫：status∈{IN_APPROVAL,DRAFT} 可撤回，撤回置 WITHDRAWN。
- 待办判定复用 `AllocAdjustTodoService.listMyTodosByEmp(empId,null×5,1,N)`，命中该 id 即 isCanAppr。
- USER_ID→工号反查（如需）`UserApi.getUserByEmpIds`；但 items 已带 username/empChnName，详情无需反查。

---

## Task A: perf `getAllocAdjustDetail` 对外 Api（TDD）
**Files:** `api/PerfApprovalQueryApi.java`(+方法)、`api/dto/AllocAdjustDetailDTO.java`(新)、`facade/PerfApprovalQueryFacade.java`(+实现，注入 `AllocAdjustService`)、test 新增。

- [ ] 写失败测试：mock `AllocAdjustService.getById` 返回 apply(status=IN_APPROVAL,createdBy=U001) + items(ORIGIN 1 行 / NEW 1 行)；mock `allocAdjustTodoService.listMyTodosByEmp` 命中该 id；断言 detail.allocaters 两类齐全、isCanAppr=1（U001 是审批人传入）、isCanDelete 按 createdBy 比较。
- [ ] 接口方法：`AllocAdjustDetailDTO getAllocAdjustDetail(String perfAdjustNo, String empId)`。
- [ ] DTO `AllocAdjustDetailDTO`：perfAdjustNo/applyNo/custId/custName/custType/allocDim/bizKind/accountNo/status/reason/createdBy/applyFullname/applyTime + `List<AllocItem> allocaters`(empId/username/fullname/ratio/itemKind/isOriginal) + isCanAppr/isCanDelete(boolean) + `List<ApprovalLogDTO> records`(或简化)。
- [ ] facade 实现：`getById`→映射；allocaters 由 items 映射（itemKind=ORIGIN→isOriginal=1，否则 2，fullname=empChnName，ratio=ratio.toPlainString）；isCanDelete=`empId.equals(apply.createdBy) && status∈{IN_APPROVAL,DRAFT}`；isCanAppr=`status==IN_APPROVAL && todos 命中 id`；applyFullname 走 `resolveEmpName`；records 取 approval-history（若 service 暴露则取，否则留空 list，标注）。
- [ ] 测试通过；commit Task A 文件 + `mvn -pl performance-engine-center install -DskipTests`。

## Task B: 网关 `PERF_INFO` 分发（TDD）
**Files:** `service/CallPuDispatchService.java`、`service/CallPuDispatchServiceTest.java`。
- [ ] 写失败测试：PERF_INFO + perfAdjustNo → resolveUserId → getAllocAdjustDetail；断言 RspMsg 含 applyType(custType CORP→"1")、applyRule(allocDim ACCOUNT→"1")、apprStatus 翻译、allocaters 合并、isCanAppr/isCanDelete(0/1)。
- [ ] dispatch 加 `PERF_INFO` 分支 → `handlePerfInfo`：resolveUserId、getAllocAdjustDetail，组装前端 `dataForm` 形态 JSON（用 Map 或新 DTO `PerfDetailData`）：字段翻译 custType→applyType、allocDim→applyRule、accountNo→iouNo、remark→adjustExplain、status→apprStatus(复用 toApprStatus，含 WITHDRAWN→3)、allocaters[{username,fullname,ratio,isOriginal}]、isCanAppr/isCanDelete(布尔→0/1)、records。
- [ ] 测试通过；commit；`mvn -pl soap-gateway-center test` 全绿。

## Task C: 前端 `applyAdd.vue` 样式优化（写+人工验证）
**Files:** `performanceAdjustment/applyAdd.vue`。
- [ ] 三段卡片化（基本信息/业务类型/分配调整）；分配调整由 `van-dialog` 改内联表格卡（工号/姓名/比例/操作，原分配行浅灰+「原」只读、手动行可编辑可删，底部「+ 添加分配对象」）；业务类型药丸选中蓝描边；提交固定底部主蓝。保留 `fetchOrigAlloc`、`submitCust` 逻辑不变。

## Task D: 前端 `performanceMyList.vue`（写+人工验证）
**Files:** `performanceAdjustment/performanceMyList.vue`。
- [ ] 顶部 `position:fixed` 蓝条 + 右末尾 `<van-icon name="add-o" @click="applyAdd"/>`（引入 Icon）；列表 padding-top 下移；**删除底部新增按钮**。
- [ ] 列表项右下「撤回」按钮：`v-if="item.apprStatus==='0'"`，`@click.stop="recall(item)"` → `van-dialog.confirm` → `PERF_RECALL`(perfAdjustNo+EmployeeNo) → 成功 toast + `getList()`。

## Task E: 前端 `applyInfo.vue` 按 listType 分支 + 退役 radioList/approvel（写+人工验证）
**Files:** `performanceAdjustment/applyInfo.vue`、删 `performanceAdjustment/radioList.vue`、`performanceAdjustment/approvel.vue`。
- [ ] `created` 读 `listType`；`getInfo` 已用 PERF_INFO（现可用）。
- [ ] 分配信息用 `van-collapse`（引入 Collapse/CollapseItem）两项：原分配(isOriginal==1)/调整后(isOriginal==2)，每行 分配规则(allocDim 文案)/分配对象(username+fullname)/比例(ratio%)。替换原「查看」跳 radioList。
- [ ] `pending`：底部审批区——`审批意见` textarea(必填) + 「审批通过」(绿)/「审批不通过」(红)；空意见 toast 拦截；PERF_APPR apprStatus=1/2 + apprOpinion → 返回。无撤回。
- [ ] `approved`：折叠只读，无操作。`mine`：只读。
- [ ] 删除 `radioList.vue`、`approvel.vue`。

## 收尾
- [ ] `mvn -pl performance-engine-center,soap-gateway-center test`（确认我的用例绿，余皆既有失败）。
- [ ] 交接：路由工程下线 `/apprHistory`(radioList)、`/performanceApprovel`(approvel)。
- [ ] 人工联调三页 + 新增样式 + 审批/撤回/折叠。
