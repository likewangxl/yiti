---
title: 工作台办理跳转「待我审批」+ 按条件查询设计
date: 2026-05-21
status: draft
owner: yiti / 银行营销绩效平台
related:
  - docs/modules/workflow-center/CLAUDE.md
  - docs/modules/performance-engine-center/CLAUDE.md
---

# 工作台办理跳转「待我审批」+ 按条件查询设计

## 1. 背景

当前工作台（`/workspace`）右侧「待办 / 已办」面板的「办理」按钮点击后，全部跳转到 `/perf/adjust?taskId=xx&action=open`，但 `Adjust.vue` 默认落在「我的申请」tab，**用户需要手动切到「待我审批」tab 才能办理**，体验差。

同时，「待我审批」tab 目前只有 1 条 SQL（`bizType=ALLOC_ADJUST`），**没有按业务字段过滤的能力**：用户在多条待审批中找特定客户 / 维度 / 时间段的申请很麻烦，需要肉眼翻 50 条。

## 2. 需求

| # | 验收点 |
|---|---|
| R1 | 工作台办理按钮点击后，**直接落在「待我审批」tab**，且自动弹出该 task 的审批对话框 |
| R2 | 「待我审批」tab 顶部新增 4 个筛选字段：**关键字 / 维度 / 业务类型 / 申请时间范围** |
| R3 | 「待我审批」改为**后端分页**（pageNo + pageSize），默认 20/页，可选 10/20/50 |
| R4 | 跳转打开时找不到对应 taskId（已被别人办理 / 不在第一页），**提示「任务已处理或不在当前页」** |
| R5 | 「已审批」tab **不在本次范围**（保持现状 50 条 list，无表单） |
| R6 | 「我的申请」tab **不在本次范围**（已有筛选） |

## 3. 总体方案（方案 E：perf 新接口）

### 3.1 模块边界

按 CLAUDE.md「workflow-center 是唯一调用 Flowable API 的模块，且不依赖业务模块」，**不能在 workflow 内 join 业绩业务表 ALLOC_ADJUST**。所以选 E：

```
前端 Adjust.vue todo tab
     ↓ GET /api/perf/adjusts/my-todos?keyword=&allocDim=&...
performance-engine-center
     ├─ 调用 TodoQueryApi.listMyTodoBusinessKeys(empId, "ALLOC_ADJUST")
     │       ↓
     │   workflow-center → Flowable Task service → 返回 List<String> businessKeys
     ↓
     └─ 用 businessKeys IN 查询 ALLOC_ADJUST 表 + 按 keyword/allocDim/bizKind/dateRange 过滤 + 分页
     ↓ 再 join workflow 拿 taskId/nodeKey/taskName/claimable
     ↓
     返回 PageResult<AdjustTodoRespDTO>
```

不选方案 D（workflow join 业务表）：违反 CLAUDE.md 模块依赖图（perf → workflow 单向），且让 workflow 模块认识业务字段。

### 3.2 各模块改动一览

| 模块 | 改动 | 新文件 |
|---|---|---|
| **workflow-center** | 新增 `TodoQueryApi`（2 个方法：listMyTodoBusinessKeys + findTaskMetaByBusinessKeys）暴露给 perf | `api/TodoQueryApi.java` + `api/dto/TaskMetaDTO.java` + `facade/TodoQueryFacade.java` |
| **performance-engine-center** | 新增 controller + service + mapper xml + DTO + 测试 | 6 个文件（详 §6）|
| **xanzc_frontend** | workspace `goHandle` 加 `tab=todo` 参数 | Index.vue 1 行 |
| **xanzc_frontend** | Adjust.vue todo tab 加表单 + 分页 + 接口切换 + 自动弹审批 | Adjust.vue ~80 行 |

**严禁动表结构**（用户红线）：ALLOC_ADJUST 表已有 `apply_no, cust_no, alloc_dim, biz_kind, owner_org_id, created_time, created_by` 等字段，零 ALTER TABLE / 零新表。

## 4. 前端契约

### 4.1 跳转 URL 契约

**工作台 → Adjust.vue**：
```
URL:  /perf/adjust?tab=todo&taskId={taskId}&action=open
```

**Adjust.vue 行为**：
- mounted 时读 `route.query.tab`，存在则 `activeTab.value = route.query.tab`
- 当 `tab=todo + action=open + taskId` 三者都有 → 先调 `reloadTodo()` 拉第一页，加载完成后从 `todos.value` 数组中按 `t.taskId === route.query.taskId` 查找
  - 找到 → 调 `openApprove(row)` 弹审批对话框
  - 找不到 → `ElMessage.warning('任务已处理或不在当前页')`，不弹窗

### 4.2 「待我审批」UI 结构

```
┌──────────────────────────────────────────────────────────────┐
│ [关键字______] [维度▼] [业务类型▼] [日期范围____] [查询][重置]│
├──────────────────────────────────────────────────────────────┤
│ 标题 │ 当前节点 │ 客户 │ 维度 │ 业务类型 │ 申请时间 │ 操作  │
│ ...                                                          │
├──────────────────────────────────────────────────────────────┤
│ 共 N 条  [10/20/50]  ← 1 2 3 →                              │
└──────────────────────────────────────────────────────────────┘
```

**字段定义**：

| 字段 | 类型 | 后端参数 | 说明 |
|---|---|---|---|
| 关键字 | text | `keyword` | 模糊匹配 `apply_no` 或 `cust_no` |
| 维度 | select | `allocDim` | 枚举：CUST/ORG/EMP（沿用 Adjust.vue 现有字典）|
| 业务类型 | select | `bizKind` | 枚举：LOAN/DEPOSIT/SUPPORT 等（沿用现有字典）|
| 申请时间 | daterange | `dateFrom` + `dateTo` | YYYY-MM-DD ~ YYYY-MM-DD，闭区间 |

**分页**：`pageNo`（默认 1）+ `pageSize`（默认 20，可选 [10,20,50]），表单变更 / 翻页 / 改 pageSize 都触发 reloadTodo。

## 5. 后端契约

### 5.1 workflow-center 新增 TodoQueryApi

```java
// api/TodoQueryApi.java
package com.bank.branch.platform.workflow.api;

public interface TodoQueryApi {
    /**
     * 查询某员工某业务类型的所有待办 task 的 businessKey。
     * <p>给 perf / 其他业务模块按业务字段二次过滤用，避免业务模块直连 Flowable 表。</p>
     *
     * @param empId   员工 ID（非 null）
     * @param bizType 业务类型（非 null，如 "ALLOC_ADJUST"）
     * @return businessKey 列表（去重，可能为空）
     */
    List<String> listMyTodoBusinessKeys(String empId, String bizType);

    /**
     * 按 businessKey 批量查询 task 元信息（taskId/nodeKey/taskName/claimable）。
     * <p>给 perf 返回 todo list 时反向 join 用。
     * empId 用于鉴权：只返回该员工有权处理的 task（候选 OR 受理），防止越权拿到他人 task 元信息。</p>
     *
     * @param empId        员工 ID
     * @param businessKeys businessKey 列表
     * @return 以 businessKey 为 key 的 Map（找不到的 key 缺失，调用方自行处理）
     */
    Map<String, TaskMetaDTO> findTaskMetaByBusinessKeys(String empId, List<String> businessKeys);
}
```

**TaskMetaDTO** 含 `taskId, nodeKey, taskName, claimable, title`（不含业务字段）。

**实现**：复用 `TodoQueryService.queryTodoList()` 内部用的 Flowable TaskQuery，但只返回 businessKey 集合 / 按 businessKey 反查。

### 5.2 perf-engine-center 新增端点

```
GET /api/perf/adjusts/my-todos
    ?keyword=        (optional)
    &allocDim=       (optional, 枚举 CUST/ORG/EMP)
    &bizKind=        (optional, 枚举 LOAN/DEPOSIT/...)
    &dateFrom=       (optional, YYYY-MM-DD)
    &dateTo=         (optional, YYYY-MM-DD)
    &pageNo=1        (default 1)
    &pageSize=20     (default 20, max 100)
```

**鉴权**：`@BizAuth(bizType="ALLOC_ADJUST", bizAction="READ")`，PT_RESOURCE 登记（字段名与 CLAUDE.md 共享开发规范 `bizAction` 对齐）。

**响应**：
```json
{
  "code": "0",
  "data": {
    "pageNo": 1,
    "pageSize": 20,
    "total": 35,
    "records": [
      {
        "applyId": "ADJ_xxx",
        "applyNo": "ADJ20260521001",
        "custNo": "C123",
        "custName": "张三",
        "allocDim": "CUST",
        "bizKind": "LOAN",
        "ownerOrgId": "ORG_001",
        "ownerOrgName": "南山支行",
        "createdBy": "E001",
        "createdByName": "李四",
        "createdTime": "2026-05-21T10:00:00",

        "taskId": "TSK_xxx",
        "nodeKey": "biz_dept_review",
        "taskName": "业务部门经办审批",
        "claimable": true,
        "title": "ADJ20260521001 / 张三",
        "businessKey": "ALLOC_ADJUST:ADJ_xxx"
      }
    ]
  }
}
```

### 5.3 实现流程（perf service 内部）

```
1. allTodoKeys = workflowApi.listMyTodoBusinessKeys(currentEmpId, "ALLOC_ADJUST")
   → ["ALLOC_ADJUST:ADJ_001", "ALLOC_ADJUST:ADJ_002", ...]
2. applyIds = allTodoKeys.map(k -> k.split(":")[1])
3. 如果 applyIds 为空，return PageResult.empty(pageNo, pageSize)
4. select count(1) from ALLOC_ADJUST a
   where a.apply_id in (#{applyIds})
     and (#{keyword} is null or a.apply_no like ... or a.cust_no like ...)
     and (#{allocDim} is null or a.alloc_dim = #{allocDim})
     and (#{bizKind} is null or a.biz_kind = #{bizKind})
     and (#{dateFrom} is null or a.created_time >= #{dateFrom})
     and (#{dateToExclusive} is null or a.created_time < #{dateToExclusive})
     -- service 层把 dateTo (YYYY-MM-DD) +1 天传入 dateToExclusive，闭区间含 dateTo 当天
   → total
5. select * from ALLOC_ADJUST a (同上 where) order by a.created_time desc
   limit #{offset}, #{pageSize}
   → adjusts page
6. metaMap = workflowApi.findTaskMetaByBusinessKeys(empId, adjusts.map(a -> "ALLOC_ADJUST:"+a.applyId))
7. records = adjusts.map(a -> AdjustTodoRespDTO.of(a, metaMap.get("ALLOC_ADJUST:"+a.applyId)))
8. return PageResult(pageNo, pageSize, total, records)
```

**注意**：步 4-5 的 IN 列表来自 workflow 内存集合，不会爆 SQL（一个用户的待办最多通常 < 200，可控）。如果未来某用户待办超 1000，需考虑两阶段分页（先按 workflow 分页拿 100 个 businessKey，再 join 业务），本次 YAGNI。

## 6. 文件清单

### 新建（lf/yiti 后端）

```
workflow-center/src/main/java/com/bank/branch/platform/workflow/
├── api/
│   ├── TodoQueryApi.java                     (新)
│   └── dto/TaskMetaDTO.java                  (新)
└── facade/
    └── TodoQueryFacade.java                  (新，实现 TodoQueryApi)

performance-engine-center/src/main/java/com/bank/branch/platform/performance/
├── api/dto/AdjustTodoRespDTO.java            (新)
├── controller/AllocAdjustTodoController.java (新，含 GET /api/perf/adjusts/my-todos)
├── service/AllocAdjustTodoService.java       (新)
└── mapper/AllocAdjustTodoMapper.java + xml   (新，1 个 count + 1 个 select)

performance-engine-center/src/test/java/.../
└── AllocAdjustTodoServiceTest.java           (新，覆盖：空待办/全字段过滤/分页边界/meta 缺失)
```

### 修改（wangyq/yiti 前端）

```
xanzc_frontend/src/
├── views/workspace/Index.vue   (goHandle 第 180/182 行加 tab: 'todo')
├── views/perf/Adjust.vue       (todo tab 加表单 + 分页 + 自动弹审批 hook)
└── api/perf.js                 (新增 listMyAdjustTodos 包装)
```

### 不动

- 数据库表（零 ALTER TABLE / 零新表）
- 已审批 tab、我的申请 tab
- workflow `/api/workflow/tasks` 现有端点（保留向后兼容）

## 7. 错误处理

| 场景 | 行为 |
|---|---|
| 跳转 taskId 不在第一页 | 前端 ElMessage.warning("任务已处理或不在当前页")，不弹窗 |
| 跳转 taskId 找到但状态变化（已被别人 claim/办理）| 弹窗时 approveTask 接口会返 Flowable 错误，沿用现有错误处理 |
| workflow 返回空 businessKey 列表 | perf service 返回 `PageResult.empty(pageNo, pageSize, 0)` |
| 业务字段过滤后 total=0 | 前端表格 `empty-text="无符合条件的待审批"` |
| dateFrom > dateTo | 后端不强校验，SQL 自然返空，前端 daterange picker 已限制不会出现 |
| 用户未登录 | AuthenticationFilter 拦截，返 401，前端跳 login |

## 8. 测试策略（TDD 红绿重构）

### 8.1 后端 unit test（AllocAdjustTodoServiceTest）

| # | Case | 期望 |
|---|---|---|
| T1 | 用户无待办 | 返回 PageResult(0, [])，不调用 mapper |
| T2 | 全字段过滤命中 1 条 | total=1, records 含完整业务+task 字段 |
| T3 | keyword 部分匹配 cust_no | total>0 |
| T4 | allocDim=CUST 过滤 | 只返 CUST 维度 |
| T5 | dateRange 闭区间 | dateTo 当天包含 |
| T6 | pageNo=2 pageSize=10 但 total=15 | records.size=5 |
| T7 | workflow 返 5 个 key 但 ALLOC_ADJUST 只剩 3 条（DB 删了 2）| total=3 |
| T8 | workflow.findTaskMetaByBusinessKeys 返回缺失 | 缺失行 taskId 等字段为 null，不抛 NPE |

### 8.2 workflow 模块单测

| # | Case | 期望 |
|---|---|---|
| W1 | listMyTodoBusinessKeys(empId, "ALLOC_ADJUST") 返 5 个 | List<String> size=5 去重 |
| W2 | findTaskMetaByBusinessKeys 部分命中 | Map 只含命中的 key |

### 8.3 前端手工验收 checklist

- [ ] 工作台「办理」点击 → URL 含 `tab=todo` → 落在「待我审批」tab + 自动弹审批
- [ ] 跳转 taskId 不存在 → 提示「任务已处理或不在当前页」
- [ ] 表单 4 字段单独筛选都生效
- [ ] 表单组合筛选生效
- [ ] 重置按钮清空 4 个字段 + 回到第一页
- [ ] 分页器：翻页 / 改 pageSize 都重新拉数据
- [ ] empty 状态文案对（无数据 vs 过滤后无结果）

## 9. 不做的事（YAGNI）

- 不做「已审批」「我的申请」tab 的查询表单（用户明确不要）
- 不做导出待审批 list（用户没要求）
- 不做高级 saved filter / 默认筛选记忆（YAGNI）
- 不在 workflow 模块加业务字段过滤（违反模块依赖）
- 不为 perf 加 Redis 缓存（已去 redis，且 todo list 实时性优先）

## 10. 安全 & 权限

- `GET /api/perf/adjusts/my-todos` 注册 PT_RESOURCE，`@BizAuth(bizType="ALLOC_ADJUST", action="READ")`
- 后端从 `CurrentUserApi.getCurrentEmpId()` 拿 empId，**禁止接收前端传 empId 参数**（防止越权查别人待办）
- DataScope 由 BizScopeFacade 处理，本接口已天然限于「我的」待办（用 currentEmpId），无需额外数据范围
- 4 个 query 参数 LIKE 用 MyBatis `#{}` 参数化，无 SQL 注入

## 11. 性能

- 单用户待办量预期 < 200 条 → IN 列表可控
- 单 page select 命中 `(apply_id, created_time)` 索引（如存在），否则 created_time DESC 全表扫描在 1000 行内可接受
- workflow listMyTodoBusinessKeys 是 Flowable TaskQuery 走 ACT_RU_TASK 表，单次响应 < 50ms
- 接口总响应预期 < 200ms

## 12. 上线 & 兼容

- 不影响现有 `/api/workflow/tasks` 端点，保留供其他业务模块使用
- 不动数据库 schema
- 前端 Adjust.vue todo tab 由旧 `listTodoTasks` 切到新 `listMyAdjustTodos`，回滚只需 git revert 一个 commit
