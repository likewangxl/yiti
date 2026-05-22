---
title: 「我的申请」+「已审批」tab 加查询条件 + 分页（V2 扩展）
date: 2026-05-21
status: draft
v1_reference: 2026-05-21-workspace-todo-jump-and-filter-design.md
owner: yiti / 银行营销绩效平台
---

# 「我的申请」+「已审批」tab 加查询条件 + 分页（V2）

## 1. 背景

V1（同日同主题）已上线「待我审批」tab 加 4 字段查询 + 后端分页 + 自动弹审批，端点 `GET /perf/alloc-adjust/my-todos` 走 perf 调 workflow `TodoQueryApi` 反查模式。

V2 把同一套（表单 + 分页 + 后端过滤）扩到剩下两个 tab：「我的申请」「已审批」。

## 2. 需求

| # | 验收 |
|---|---|
| R1 | 「我的申请」tab 加 5 字段查询：关键字 / 维度 / 业务类型 / 申请时间 / **状态** |
| R2 | 「已审批」tab 加 4 字段查询（与 todo 一致）：关键字 / 维度 / 业务类型 / 申请时间 |
| R3 | 两个 tab 都改后端分页（pageNo + pageSize，可选 10/20/50）|
| R4 | 「我的申请」**硬约束 createdBy=当前用户**，前端无法传 createdBy 越权查别人 |
| R5 | 「已审批」实例数据源是当前用户已办（同 workflow `/tasks/done` 鉴权口径），按 perf 业务字段二次过滤 |
| R6 | V1 已上线的「待我审批」+ 工作台办理跳转 + 自动弹审批 行为不受影响 |

## 3. 端点设计

| Tab | 端点 | 备注 |
|---|---|---|
| 待我审批 | `GET /perf/alloc-adjust/my-todos` ✅ V1 已上线 | empId 内部取 |
| 我的申请 | **新 `GET /perf/alloc-adjust/my-applies`** | empId 内部取；现有 `/perf/alloc-adjust/list` 保留给 admin 查任意用户 |
| 已审批 | **新 `GET /perf/alloc-adjust/my-done`** | 同 todo 模式 |

**为什么 mine 也新建端点不扩 `/list`**：现 `/list` 接收 `createdBy` 参数供 admin 用，扩它会破坏边界；新 `/my-applies` 内部硬编码 `createdBy=currentEmpId` 防越权。

## 4. 字段

### 4.1 「我的申请」5 字段

| 字段 | 类型 | 后端参数 | 数据源 |
|---|---|---|---|
| 关键字 | text | `keyword` | `apply_no` / `cust_id` 模糊匹配 |
| 维度 | select | `allocDim` | 枚举 CUST/ORG/EMP |
| 业务类型 | select | `bizKind` | 枚举 LOAN/DEPOSIT/SUPPORT |
| 申请时间 | daterange | `dateFrom` + `dateTo` | 闭区间（service 内部转 +1day exclusive）|
| **状态** | select | `status` | 枚举 DRAFT/IN_APPROVAL/APPROVED/REJECTED/WITHDRAWN |

### 4.2 「已审批」4 字段

去 status，其他同 todo / mine：keyword + allocDim + bizKind + dateRange。

## 5. 后端契约

### 5.1 新增 workflow TodoQueryApi 方法

```java
public interface TodoQueryApi {
    // V1 已有
    List<String> listMyTodoBusinessKeys(String empId, String bizType);
    Map<String, TaskRespDTO> findTaskRespByBusinessKeys(String empId, List<String> businessKeys);

    // V2 新增（已办）
    /** 查询某员工某 bizType 下所有已办 task 的 businessKey（去重）。 */
    List<String> listMyDoneBusinessKeys(String empId, String bizType);

    /** 按 businessKey 反查已办 TaskRespDTO（走 HistoryService）。 */
    Map<String, TaskRespDTO> findDoneTaskRespByBusinessKeys(String empId, List<String> businessKeys);
}
```

### 5.2 perf 新 endpoints

```
GET /api/perf/alloc-adjust/my-applies
    ?keyword=&allocDim=&bizKind=&dateFrom=&dateTo=&status=&pageNo=1&pageSize=20
鉴权: @BizAuth(PERF_CONFIG, LIST)
empId: 从 CurrentUserApi 取，硬约束 createdBy=empId

GET /api/perf/alloc-adjust/my-done
    ?keyword=&allocDim=&bizKind=&dateFrom=&dateTo=&pageNo=1&pageSize=20
鉴权: @BizAuth(PERF_CONFIG, LIST)
empId: 从 CurrentUserApi 取，走 workflow done 反查 + perf 业务过滤
```

响应同 V1 用 `AdjustTodoRespDTO`（mine 不需要 task 字段，但复用 DTO 留 null 减少新类型）。或新建 `AdjustMineRespDTO` 不含 task 字段——本次**复用 DTO** 简化（YAGNI）。

### 5.3 Service / Mapper 实现

**my-applies** 不需要 workflow，直接查 perf 表：

```
1. controller 拿 empId
2. mapper.countAndSelect(createdBy=empId, keyword, allocDim, bizKind, dateRange, status, page)
3. PageResult<AdjustTodoRespDTO>（task 字段全 null）
```

**my-done** 同 my-todos 模式：

```
1. workflow.listMyDoneBusinessKeys(empId, "ALLOC_ADJUST")
2. mapper.countMyDones + selectMyDones（同 my-todos 的 mapper 复用 todoWhere SQL）
3. workflow.findDoneTaskRespByBusinessKeys
4. merge
```

**Mapper 复用决策**：扩 `PerfAllocAdjustTodoMapper` 加 4 个方法（countMyApplies + selectMyApplies + countMyDones + selectMyDones），SQL 复用 `todoWhere` 片段；或新建 2 个 mapper 类。**本次复用同一 mapper 类**（命名改成更通用 `PerfAllocAdjustQueryMapper`？为避免 V1 commit 改名引入大 diff，**保持 `PerfAllocAdjustTodoMapper`** 类名 + 内部加 4 方法）。

## 6. 文件清单

### lf 后端

**修改**：
- `workflow/api/TodoQueryApi.java` + 2 个方法签名
- `workflow/facade/TodoQueryFacade.java` + 2 个方法实现
- `workflow/service/TodoQueryService.java` + 2 个 service 方法（用 historyService）
- `workflow/test/TodoQueryFacadeTest.java` + 测试 done 方法 Map 构造（2 cases）
- `perf/mapper/PerfAllocAdjustTodoMapper.java` + 4 个方法
- `perf/mapper/PerfAllocAdjustTodoMapper.xml` + 4 个 SQL（复用 `todoWhere` + 加 status/createdBy 条件）

**新建**：
- `perf/controller/AllocAdjustMineController.java` (GET /my-applies)
- `perf/service/adjust/AllocAdjustMineService.java` + 单测（5-6 case）
- `perf/controller/AllocAdjustDoneController.java` (GET /my-done)
- `perf/service/adjust/AllocAdjustDoneService.java` + 单测（5-6 case）

或合并：复用 `AllocAdjustTodoController` 改名 `AllocAdjustMyController` 含 3 个 @GetMapping —— **不合并**（每个 endpoint 一个 controller 更清晰，方便 PT_RESOURCE 单独鉴权）。

### wangyq SQL

- `docs/superpowers/sql/2026-05-21-pt-resource-mine-done.sql`（登记 2 个新 endpoint）

### wangyq 前端

**修改**：
- `xanzc_frontend/src/api/perf.js` + 2 个 wrapper (listMyApplies / listMyDoneTodos)
- `xanzc_frontend/src/views/perf/Adjust.vue`：
  - mine tab 表单 + 分页 + reactive state + reload 改造
  - done tab 表单 + 分页 + reactive state + reload 改造 + 切新接口（不用 listDoneTasks）

## 7. 测试策略（TDD）

### 7.1 workflow facade test 加 2 case
- W4 listMyDoneBusinessKeys 委托 service 返 distinct
- W5 findDoneTaskRespByBusinessKeys 构造 Map (partial match)

### 7.2 perf AllocAdjustMineServiceTest（5-6 case）
- M1 createdBy 硬约束（empId 传入即用）
- M2 5 字段全过滤命中
- M3 status 单独过滤
- M4 分页边界
- M5 dateRange 闭区间
- M6 empty result

### 7.3 perf AllocAdjustDoneServiceTest（5-6 case）
- D1 同 todo T1 (empty workflow keys)
- D2 全字段过滤
- D3 workflow 5 keys but DB only 3
- D4 task meta 缺失
- D5 分页边界
- D6 dateRange

### 7.4 前端手工验收
- mine tab：5 字段筛选 + 分页 + 重置
- done tab：4 字段筛选 + 分页 + 重置
- mine tab 不能传 createdBy 查别人（curl 直接打 endpoint 验证 empId 强制覆盖）
- todo tab 行为无回归

## 8. PT_RESOURCE 登记

```sql
INSERT INTO PT_RESOURCE ... ('R_PERF_ADJ_MINE_LIST', '/api/perf/alloc-adjust/my-applies', 'GET', '业绩调整-我的申请', 'PERF_CONFIG', 'LIST', 0, 0, 'PLATFORM', NOW(), NOW());
INSERT INTO PT_RESOURCE ... ('R_PERF_ADJ_DONE_LIST', '/api/perf/alloc-adjust/my-done', 'GET', '业绩调整-已审批', 'PERF_CONFIG', 'LIST', 0, 0, 'PLATFORM', NOW(), NOW());
```

绑给所有审批角色 + 申请人角色（任何登录用户都该能看自己的申请）。

## 9. 不做的事（YAGNI）

- 不重命名 V1 的 `PerfAllocAdjustTodoMapper`（复用类名不影响功能）
- 不拆 DTO 类（复用 `AdjustTodoRespDTO`）
- 不为 mine tab 加 task 字段渲染（mine 不显示 workflow 信息）
- 不动 DB schema（与 V1 一致）

## 10. 上线 & 兼容

- 不影响 V1 已上线 `/my-todos` + 工作台跳转
- 部署前需手工跑 2 条 PT_RESOURCE INSERT
- 回滚：3 个 commit 单独 revert 不互相影响（workflow API / perf 2 新 controller / 前端 2 tab）
