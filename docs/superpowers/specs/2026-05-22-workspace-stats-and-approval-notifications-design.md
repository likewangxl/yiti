---
title: 工作台 stats 真实联动 + 审批通过/驳回/撤回 自动发通知 (V3)
date: 2026-05-22
status: draft
owner: yiti / 银行营销绩效平台
---

# 工作台 stats 真实联动 + 审批通知 (V3)

## 1. 背景

V1+V2 已完成 todo/mine/done tab 改造。但工作台 `/workspace` 顶部 4 个 stat 卡 + 右侧通知列表仍然是 mock 假数据：
- 「待办」「未读通知」数字不动也不真实
- 通知卡片显示 mock 张三示例

后端已具备：`TaskApprovedEvent / TaskRejectedEvent` 已发出（workflow TaskOperationService line 100/151）、`NotificationController` 全套 CRUD 已就绪、`UserApi.getUserName(empId)` 可拿 username。**只缺一个 listener 把 event 转成通知**。

## 2. 需求

| # | 验收 |
|---|---|
| R1 | 工作台「待办」stat 卡数字 = 当前用户 ALLOC_ADJUST 待办真实 count |
| R2 | 工作台「未读通知」stat 卡数字 = 调 `/api/notifications/unread-count` 真实值 |
| R3 | 其他 2 个 stat 卡（我的客户/本月业绩之类）保持 mock 不动 |
| R4 | 审批通过时 → 自动给申请人发通知「您的申请已通过审批 by [审批人 username]」 |
| R5 | 审批驳回时 → 自动给申请人发通知「您的申请被驳回 by [审批人 username] 原因：[opinion]」 |
| R6 | 申请人撤回时 → 自动给当前 active task 的 assignee 发通知「申请人 [username] 已撤回申请」 |
| R7 | 通知 R4-R6 走现有 NotificationService（不动表结构）|
| R8 | 通知文案**不带申请单号**（用户确认）；审批人/申请人显示 username（非 displayName）|

## 3. 总体方案

### 3.1 后端：3 个改动

**A. perf 新建 `AllocAdjustWithdrawnEvent`**（撤回事件）
- 字段：`processInstanceId, withdrawnByEmpId, opinion`
- 在 `AllocAdjustService.withdrawAdjust` 内 publishEvent
- 现有 `withdrawAdjust` 已经在调 Flowable deleteProcessInstance 终止流程，event 在 delete 前 publish

**B. portal 新建 `WorkflowApprovalNotificationListener`**（监听 3 个 event 发通知）
- 监听：
  - `workflow.api.event.TaskApprovedEvent` → 查 BizProcessMap 拿 startUser → NotificationService.send 给 startUser
  - `workflow.api.event.TaskRejectedEvent` → 同上
  - `perf.api.event.AllocAdjustWithdrawnEvent` → TaskService 查当前 active task 的 assignee → NotificationService.send 给 assignee
- 用 `@TransactionalEventListener(phase=AFTER_COMMIT)` 防部分提交场景下重复发通知

**C. 通知文案**：
- approved: `您的申请已通过审批 by ${approverUsername}`
- rejected: `您的申请被驳回 by ${approverUsername} 原因：${opinion}`
- withdrawn: `申请人 ${withdrawerUsername} 已撤回申请`

### 3.2 前端：workspace stats 联动

**workspace.js**：
- 加 wrapper `getMyTodoCount()` 调 `/workflow/tasks?bizType=ALLOC_ADJUST&pageSize=1` 取 `.total`
- 加 wrapper `getUnreadNotificationCount()` 调 `/api/notifications/unread-count`
- adapter 不动 stats（保持 mock 4 个），让 Index.vue 自己 reactive override 前 2 个

**Index.vue**：
- onMounted 后并行调上述 2 个 wrapper
- mutate `data.stats[0].value = todoCount`（待办）
- mutate `data.stats[1].value = unreadCount`（未读通知）
- 其他 stats 不动

注：依赖 mock 数组前 2 个语义是「待办」「未读通知」。若顺序变化先调整顺序而非改逻辑。

### 3.3 NotificationService.send 接口签名

依赖现有 NotificationApi/Service 的 send 方法。如签名是 `send(toEmpId, bizType, title, content)`，listener 按此调用。**执行时确认实际签名 + 适配**（不在 spec 锁死）。

## 4. 文件清单

### 新建（lf）

```
performance-engine-center/src/main/java/com/bank/branch/platform/performance/api/event/
└── AllocAdjustWithdrawnEvent.java     (record，3 字段)

portal-content-center/src/main/java/com/bank/branch/platform/portal/listener/
└── WorkflowApprovalNotificationListener.java   (@Component, 3 @EventListener)

portal-content-center/src/test/java/com/bank/branch/platform/portal/listener/
└── WorkflowApprovalNotificationListenerTest.java   (3 cases approve/reject/withdraw)
```

### 修改（lf）

```
performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/adjust/
└── AllocAdjustService.java   (withdrawAdjust 内 publishEvent)
```

### 修改（wangyq）

```
xanzc_frontend/src/api/workspace.js   (加 2 wrapper)
xanzc_frontend/src/views/workspace/Index.vue   (mounted 调 + reactive override stats[0]/[1])
```

### 不动

- 数据库 schema
- 通知 NotificationController / Service / Mapper
- 现有 workspace 后端 WorkspaceController
- V1/V2 已上线代码

## 5. 测试

### 5.1 后端 listener 单测 3 cases

```
N1 onTaskApproved → 调 NotificationService.send(申请人, "您的申请已通过审批 by X")
N2 onTaskRejected → 调 NotificationService.send(申请人, "您的申请被驳回 by X 原因：Y")
N3 onAllocAdjustWithdrawn → 查 active task assignee → 调 NotificationService.send(assignee, "申请人 X 已撤回申请")
```

### 5.2 前端手工验收

- [ ] workspace「待办」数字 = mine tab pager.total
- [ ] workspace「未读通知」数字 ≥ 0 且与 /api/notifications/unread-count 一致
- [ ] 其他 2 stat 卡不变
- [ ] 在 todo tab 点「通过」→ workspace 通知列表新增 1 条「已通过审批 by xxx」（申请人账号登录查看）
- [ ] 在 mine tab 点「撤回」→ 当前 active 审批人收到「已撤回申请」通知

## 6. 不做的事 (YAGNI)

- 不为通知加表结构（用现有）
- 不做通知推送 / 实时 SSE（用户主动刷新）
- 不动其他 2 个 stat 卡
- 不带申请单号到通知文案
- 不为撤回区分多个候选审批人（只发给 assignee；未签收时 candidate group 不发——属于边界，本次不覆盖）

## 7. 安全 & 兼容

- listener 用 @TransactionalEventListener(AFTER_COMMIT)，事务回滚不发通知
- 撤回 event 在 perf 内 publish 时机：在 deleteProcessInstance **之前** publish，否则 listener 内查 active task 已被删除拿不到 assignee
- 不动现有 V1/V2 endpoint
- 回滚：4 commit 单独 revert 不互相影响
