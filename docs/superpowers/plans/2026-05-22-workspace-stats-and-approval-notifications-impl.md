# V3 workspace stats + 审批通知 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use executing-plans。

**Goal:** workspace stats 联动真实 todo count + 未读通知；审批/撤回自动给申请人或下一节点 assignee 发通知。

**Architecture:** workflow 加 ProcessWithdrawnEvent + perf withdrawAdjust 内 publish；portal 监听 3 个 event（TaskApproved/TaskRejected/ProcessWithdrawn）调 NotificationService 发；前端 workspace.js + Index.vue mounted 调 2 个 count API 覆盖 stats[0]/[1]。

**Tech Stack:** Spring Boot 3.2.3 / Flowable 7 / Vue 3

---

## §0 拓扑

```
C1 (already done) spec V3 commit
C2 workflow 新 ProcessWithdrawnEvent (1 file)
C3 perf withdrawAdjust 内 publish ProcessWithdrawnEvent + spec inline fix
C4 portal 新 WorkflowApprovalNotificationListener + test (3 case)
C5 wangyq 前端 workspace stats reactive 联动
```

---

### Task C2: workflow 新建 ProcessWithdrawnEvent

**Files:**
- Create: `workflow-center/src/main/java/com/bank/branch/platform/workflow/api/event/ProcessWithdrawnEvent.java`

- [ ] **Step 1: 建 event record**

```java
package com.bank.branch.platform.workflow.api.event;

/**
 * 流程被申请人撤回事件。
 * <p>由业务模块（如 perf）在调 Flowable deleteProcessInstance 之前 publish；
 * currentAssigneeEmpId 由发布方查 active task 拿到，避免 listener 收到时 active task 已不存在。</p>
 *
 * @param processInstanceId  Flowable 流程实例 ID
 * @param businessKey        业务键，如 "ALLOC_ADJUST:A1"
 * @param withdrawnByEmpId   撤回者（一般是申请人）
 * @param currentAssigneeEmpId 撤回时当前 active task 的受理人（可能为 null：未签收的候选组任务）
 * @param opinion            撤回原因
 */
public record ProcessWithdrawnEvent(String processInstanceId,
                                    String businessKey,
                                    String withdrawnByEmpId,
                                    String currentAssigneeEmpId,
                                    String opinion) {
}
```

- [ ] **Step 2: install + commit**

```bash
cd /home/djdev/lf/yiti && mvn install -pl workflow-center -DskipTests -am 2>&1 | tail -3
git -C /home/djdev/lf/yiti add workflow-center/src/main/java/com/bank/branch/platform/workflow/api/event/ProcessWithdrawnEvent.java
git -C /home/djdev/lf/yiti commit -m "feat(workflow): 新 ProcessWithdrawnEvent 通用撤回事件"
```

---

### Task C3: perf withdrawAdjust 内 publish event

**Files:**
- Modify: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/adjust/AllocAdjustService.java`（withdrawAdjust 内）
- Modify: `docs/superpowers/specs/2026-05-22-workspace-stats-and-approval-notifications-design.md`（已 inline 修）

- [ ] **Step 1: 看 withdrawAdjust 现状决定改在哪行**

```bash
grep -nA20 "withdrawAdjust\|deleteProcessInstance" /home/djdev/lf/yiti/performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/adjust/AllocAdjustService.java | head -40
```

- [ ] **Step 2: 在 deleteProcessInstance 之前插入 publish**

伪代码（按实际 indent 调整）：
```java
// 查 active task 拿 assignee
String activeAssignee = taskService.createTaskQuery()
        .processInstanceId(pid)
        .active()
        .list().stream()
        .map(Task::getAssignee)
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);

// publish 撤回事件（在 delete 之前）
eventPublisher.publishEvent(new ProcessWithdrawnEvent(
    pid, businessKey, currentEmpId, activeAssignee, reason));

// 然后再 delete
runtimeService.deleteProcessInstance(pid, "withdrawn-by-applicant: " + reason);
```

**注意 import**：`org.flowable.task.api.Task`, `com.bank.branch.platform.workflow.api.event.ProcessWithdrawnEvent`。如 service 已有 eventPublisher 字段就 reuse；否则注入 ApplicationEventPublisher。

- [ ] **Step 3: 跑 mvn compile + commit**

```bash
cd /home/djdev/lf/yiti && mvn compile -pl performance-engine-center 2>&1 | tail -3
git -C /home/djdev/lf/yiti add performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/adjust/AllocAdjustService.java
git -C /home/djdev/lf/yiti commit -m "feat(perf): withdrawAdjust 撤回时 publish ProcessWithdrawnEvent 给 listener 发通知"
```

(spec inline fix commit 跟 wangyq 一起在 C5)

---

### Task C4: portal listener + test

**Files:**
- Create: `portal-content-center/src/main/java/com/bank/branch/platform/portal/listener/WorkflowApprovalNotificationListener.java`
- Create: `portal-content-center/src/test/java/com/bank/branch/platform/portal/listener/WorkflowApprovalNotificationListenerTest.java`

- [ ] **Step 1: 先看 NotificationService 真实 send 签名 + BizProcessMap 查申请人方法**

```bash
grep -nE "public.*send\|sendNotification\|insert\|public.*Notification" /home/djdev/lf/yiti/system-governance-center/src/main/java/com/bank/branch/platform/governance/service/NotificationService.java 2>/dev/null | head -10
```

按实际签名写 listener。如果 NotificationService 只有 portal/governance 内部访问 + 没暴露 *Api，portal 直接 autowire 它（portal 已依赖 governance）。

- [ ] **Step 2: 写 listener（按实际 send 签名 + 用 @TransactionalEventListener AFTER_COMMIT）**

骨架：
```java
package com.bank.branch.platform.portal.listener;

import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.workflow.api.event.TaskApprovedEvent;
import com.bank.branch.platform.workflow.api.event.TaskRejectedEvent;
import com.bank.branch.platform.workflow.api.event.ProcessWithdrawnEvent;
import com.bank.branch.platform.workflow.entity.BizProcessMap;
import com.bank.branch.platform.workflow.mapper.BizProcessMapMapper;
// import com.bank.branch.platform.governance.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowApprovalNotificationListener {

    private final BizProcessMapMapper bizProcessMapMapper;
    private final UserApi userApi;
    // private final NotificationService notificationService;  // 按实际类名调

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTaskApproved(TaskApprovedEvent event) {
        BizProcessMap map = bizProcessMapMapper.selectByProcessInstanceId(event.processInstanceId());
        if (map == null || map.getStartUser() == null) return;
        String approverName = userApi.getUserName(event.empId());
        String content = "您的申请已通过审批 by " + approverName;
        // notificationService.send(map.getStartUser(), bizType, "审批通过", content);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTaskRejected(TaskRejectedEvent event) {
        BizProcessMap map = bizProcessMapMapper.selectByProcessInstanceId(event.processInstanceId());
        if (map == null || map.getStartUser() == null) return;
        String approverName = userApi.getUserName(event.empId());
        // 注：TaskRejectedEvent 没 opinion 字段（看 line 236 record 定义），文案改：
        String content = "您的申请被驳回 by " + approverName;
        // notificationService.send(map.getStartUser(), bizType, "审批驳回", content);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProcessWithdrawn(ProcessWithdrawnEvent event) {
        if (event.currentAssigneeEmpId() == null) return;  // 未签收的候选组任务跳过
        String withdrawerName = userApi.getUserName(event.withdrawnByEmpId());
        String content = "申请人 " + withdrawerName + " 已撤回申请";
        // notificationService.send(event.currentAssigneeEmpId(), bizType, "申请撤回", content);
    }
}
```

实际 NotificationService.send 签名 + bizType 常量按 Step 1 grep 结果填。

- [ ] **Step 3: 写 3 个 test case（mock NotificationService）**

```java
@Test
void n1_onTaskApproved_sendsToApplicant() { ... }
@Test
void n2_onTaskRejected_sendsToApplicant() { ... }
@Test
void n3_onProcessWithdrawn_sendsToAssignee_skipsIfNullAssignee() { ... }
```

- [ ] **Step 4: 跑 test + commit**

```bash
cd /home/djdev/lf/yiti && mvn test -pl portal-content-center -Dtest=WorkflowApprovalNotificationListenerTest
git -C /home/djdev/lf/yiti add portal-content-center/
git -C /home/djdev/lf/yiti commit -m "feat(portal): 审批通过/驳回/撤回 自动给申请人/assignee 发通知"
```

---

### Task C5: wangyq workspace 前端联动

**Files:**
- Modify: `xanzc_frontend/src/api/workspace.js`（加 2 wrapper）
- Modify: `xanzc_frontend/src/views/workspace/Index.vue`（mounted 覆盖 stats[0]/[1]）

- [ ] **Step 1: workspace.js 加 2 wrapper**

```js
export function getMyTodoCount() {
  return call('get', '/workflow/tasks', { params: { bizType: 'ALLOC_ADJUST', pageSize: 1 } },
              { total: 0, records: [] }).then(r => r?.total || 0);
}

export function getUnreadNotificationCount() {
  return call('get', '/notifications/unread-count', {}, 0).then(r => Number(r) || 0);
}
```

- [ ] **Step 2: Index.vue import + mounted 后调用 mutate stats[0]/[1]**

import 加 `getMyTodoCount, getUnreadNotificationCount` from `@/api/workspace`。

onMounted 内 getWorkspace 之后并行：
```js
Promise.all([getMyTodoCount(), getUnreadNotificationCount()])
  .then(([todo, unread]) => {
    if (data.value?.stats?.length >= 2) {
      data.value.stats[0].value = todo;
      data.value.stats[1].value = unread;
    }
  })
  .catch(() => { /* noop, 保持 mock */ });
```

注：前 2 stat 卡的语义假设是「待办」「未读通知」。如 mock 顺序不一致，按 label 找 index。

- [ ] **Step 3: vite build + commit（含 spec inline 修订）**

```bash
cd /home/djdev/wangyq/yiti/xanzc_frontend && timeout 30 npx vite build --logLevel error 2>&1 | tail -3
git -C /home/djdev/wangyq/yiti add xanzc_frontend/src/api/workspace.js xanzc_frontend/src/views/workspace/Index.vue docs/superpowers/specs/2026-05-22-workspace-stats-and-approval-notifications-design.md
git -C /home/djdev/wangyq/yiti commit -m "feat(workspace): stats 待办+未读通知 联动真实数据 + spec V3 架构修订"
```

---

## §3 验收 checklist

- [ ] workspace 待办 stat = mine todo 真实 count
- [ ] workspace 未读 stat = /api/notifications/unread-count 真实值
- [ ] 审批通过 → 申请人收到「您的申请已通过审批 by xxx」
- [ ] 审批驳回 → 申请人收到「您的申请被驳回 by xxx」
- [ ] 撤回 → active assignee 收到「申请人 xxx 已撤回申请」
- [ ] 其他 2 个 stat 卡保持 mock 不变
- [ ] V1/V2 行为无回归
