# 业绩分配调整审批超时提醒（定时通知）设计（spec）

- 日期：2026-07-16
- 作者：刘杨
- 范围：`performance-engine-center`
- 关联页面：业绩分配查询（分配调整审批）`views/report/AmasApprovals.vue` / 后端 `PerfApprovalQueryFacade`

## 1. 需求

业绩分配查询页中状态为**审批中（IN_APPROVAL）**的记录，若**从申请时间起满 14 天**仍未办结，给**申请人**在通知中心发一条提醒（**只提醒一次**），提醒内容含**当前审批环节**和**已用时（天数）**。只做通知、不跳转、只管本页（分配调整审批）。

## 2. 现状（已核实）

- 审批数据：`PERF_ALLOC_ADJUST_APPLY`（实体 `PerfAllocAdjustApply`，Mapper `PerfAllocAdjustApplyMapper extends BaseMapper`）：
  - `status` = DRAFT/**IN_APPROVAL**/APPROVED/REJECTED；`created_by`=申请人工号；`created_time`=申请时间；`apply_no`=申请编号；`cust_name`=客户名；`process_instance_id`=Flowable 流程实例。
- 当前审批环节解析：`PerfApprovalQueryFacade.resolveNodes` 已实现——`workflowQueryApi.getProcessNodes(pid)` → 取 `status='ACTIVE' && nodeType='userTask'` 的节点，`getNodeName()` 即当前环节中文名；无 pid/无活动节点/异常 → 回退 `"审批中"`。
- 通知中心：`governance.NotifyApi.sendNotification(NotificationCmd.builder()...)`。现有 `AllocAdjustCompletedListener.notifyApplicant` 范式：`notifyType="WORKFLOW"`、`bizType="ALLOC_ADJUST"`、`bizId=apply.getId()`、try/catch 包裹（失败不阻断）。`NotificationCmd` 为 `@Builder`：`targetEmpId/title/content/notifyType/bizType/bizId/linkUrl`。
- 定时任务：perf 无 `PerfQuartzConfig`，走 `sys_job_conf` + `*QuartzJob` 包装 + `syncJobsOnStartup`（与已交付的 `StatShowArchiveQuartzJob` 同套）。
- perf 已依赖 governance（NotifyApi）与 workflow（WorkflowQueryApi，facade 已注入）。

## 3. 设计

### 3.1 触发：Quartz 定时任务（每天 9 点）

沿用 `StatShowArchiveQuartzJob` 套路：
- `PerfAllocOverdueNotifyJob`（`@Component`，裸 `run()`，承载业务）。
- `PerfAllocOverdueNotifyQuartzJob`（`implements org.quartz.Job` + `@DisallowConcurrentExecution`，**非 @Component**，`@Autowired PerfAllocOverdueNotifyJob`，`execute()` 委托 `run()`）。
- 注册：`SYS_JOB_CONF` INSERT（`job_key=PERF_ALLOC_OVERDUE_NOTIFY`，`cron='0 0 9 * * ?'`，`quartz_job_class=com.bank.branch.platform.performance.job.quartz.PerfAllocOverdueNotifyQuartzJob`），`syncJobsOnStartup` 启动注册。

### 3.2 业务逻辑 `PerfAllocOverdueNotifyJob.run(now)`（注入 now 便于测试）

依赖：`PerfAllocAdjustApplyMapper applyMapper`、`NotifyApi notifyApi`、`WorkflowQueryApi workflowQueryApi`。

```
threshold = now.minusDays(14)
overdue = applyMapper.selectList(LambdaQueryWrapper
    .eq(status, "IN_APPROVAL")
    .le(createdTime, threshold)
    .isNull(overdueNotifiedTime))          // 未提醒过
for apply in overdue:
  try:
    node = resolveCurrentNode(apply)        // 活动 userTask 名，回退 "审批中"
    days = ChronoUnit.DAYS.between(apply.createdTime.toLocalDate(), now.toLocalDate())
    notifyApi.sendNotification(cmd(apply, node, days))
    applyMapper.update(null, LambdaUpdateWrapper.set(overdueNotifiedTime, now).eq(id, apply.id))  // 只提醒一次
  catch Exception:
    log.warn(...)                            // 单条失败不置标记 → 次日重试；不影响其他条
```
- `resolveCurrentNode(apply)`：复用 facade 同款最小逻辑（`getProcessNodes(pid)` 取 ACTIVE userTask `getNodeName()`；无 pid/无活动/异常 → `"审批中"`）。为不改动 facade（working code），在 Job 内内联同款 ~8 行逻辑。
- 逐条独立、`run()` 不加 `@Transactional`（发一条置一条标记，避免大事务；且发送允许异步）。
- 用 MyBatis-Plus Wrapper（符合红线：动态/简单条件 + 简单条件更新，不写 XML、不加 Mapper 方法）。

### 3.3 通知内容

```
targetEmpId = apply.createdBy
title       = "业绩分配调整审批超时提醒"
content     = "您提交的业绩分配调整申请【" + applyNo + " · 客户 " + custName + "】已提交 " + days
              + " 天仍未办结，当前审批环节：" + node + "。请关注审批进度或联系审批人跟进。"
notifyType  = "WORKFLOW"
bizType     = "ALLOC_ADJUST"
bizId       = apply.id
（不设 linkUrl —— 只发通知，不跳转）
```
- `custName` 为空时用 `custId` 兜底或省略该段（取非空）。

### 3.4 去重列（DDL）

`PERF_ALLOC_ADJUST_APPLY` 加 `overdue_notified_time DATETIME NULL COMMENT '超时提醒已发送时间(仅提醒一次)'`。实体 `PerfAllocAdjustApply` 加 `LocalDateTime overdueNotifiedTime`（`map-underscore-to-camel-case` 自动映射，无需 `@TableField`）。发过即填时间、扫描 `IS NULL` 过滤 → 保证只提醒一次。

## 4. 改动文件

| 文件 | 改动 |
|---|---|
| 新增 `job/PerfAllocOverdueNotifyJob.java` | 业务逻辑（扫描+发通知+置标记） |
| 新增 `job/quartz/PerfAllocOverdueNotifyQuartzJob.java` | Quartz 包装 |
| 改 `entity/PerfAllocAdjustApply.java` | 加 `overdueNotifiedTime` 字段 |
| 新增 `docs/superpowers/sql/2026-07-16-alloc-adjust-overdue-notify.sql` | 加列 DDL（幂等）+ SYS_JOB_CONF 注册（INSERT IGNORE） |
| 新增测试 `PerfAllocOverdueNotifyJobTest.java` | Mockito 单测 |

## 5. 测试（TDD，Mockito）

- 超期未提醒（createdTime=15 天前）→ `notifyApi.sendNotification` 被调，`NotificationCmd`：title=「业绩分配调整审批超时提醒」、content 含 `15 天` + 当前环节名、targetEmpId=申请人；随后 `applyMapper.update` 置 `overdueNotifiedTime`。
- 当前环节：mock `workflowQueryApi.getProcessNodes(pid)` 返回 ACTIVE userTask「机构负责人审批」→ content 含之；返回 null/无活动 → content 含「审批中」。
- 发送抛异常 → **不**调 `applyMapper.update`（不置标记，次日重试），且不抛出（不影响其他条）。
- mapper 查询返回空 → 不发任何通知。
- 天数计算：createdTime 恰 14 天前 → `14 天`。

## 6. 部署（上线步骤）

1. 部署新代码（重建 jar，含 `PerfAllocOverdueNotifyQuartzJob` 类——集群所有连库节点都要有该类，否则触发器会 ERROR，见踩坑清单 #11）。
2. yiti 库执行 `2026-07-16-alloc-adjust-overdue-notify.sql`（加列 + 注册任务）。
3. 重启应用 → `syncJobsOnStartup` 注册进 Quartz → 每天 9 点扫描。

## 7. 不做（YAGNI）

- 不管目标调整审批（仅分配调整）；不做跳转 linkUrl；不做"重复提醒/升级提醒"；不改 facade（当前节点逻辑内联复用，不动 working code）；不改前端页面。

## 8. 风险

- 「当前环节」逻辑与 facade 重复（内联 ~8 行）——为避免改动 working 的 facade，接受此轻微 DRY；如需收敛可后续抽公共 resolver。
- 定时任务是 Quartz 集群任务：所有连同一 yiti 库的后端节点都必须含 `PerfAllocOverdueNotifyQuartzJob` 类，否则该节点抢到触发会把触发器打成 ERROR（同 STAT_SHOW_ARCHIVE 经验）。
