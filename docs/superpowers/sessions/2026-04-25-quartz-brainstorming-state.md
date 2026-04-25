# Quartz 整合（子项目 B）— Brainstorming 中途状态

**创建时间**: 2026-04-25
**目的**: compact 后无缝续接 brainstorming 流程
**当前位置**: 子项目 B brainstorming，澄清问题 #2 已答完，待问 #3

---

## 整体进度（3 子项目 refactor）

| 子项目 | 状态 | 分支 | Milestone |
|---|---|---|---|
| **A. Excel 收编** | ✅ 完成 | `refactor/excel-easyexcel` | `subproject/A-excel-DONE` 已 push |
| **B. Quartz 整合 sys_job_conf** | 🔄 进行中（brainstorming）| `refactor/quartz-job-integration` | — |
| **C. MyBatis-Plus 引入** | ⏳ 待启动 | （未创建）| — |

**合并策略**（按 user memory `feedback_phase_commit_push.md`）：3 子项目全部完成后再合并 master。当前 A 已 push 但未合并。

---

## 子项目 B 的 brainstorming 已确定决策

### #1 sys_job_conf 与 QRTZ_* 数据关系：B（双写）

- sys_job_conf 保留作业务元数据 + 任务目录
- 新增 11 张 QRTZ_* 标准表作 Quartz 调度引擎内部状态
- JobConfService 是单一写入口：业务写 sys_job_conf → 内部触发 quartzScheduler.scheduleJob/rescheduleJob
- sys_job_run_log 由 Quartz JobListener 自动写入

### #2 ShedLock 处置：A（删除）

- 移除 `shedlock-spring` + `shedlock-provider-redis-spring` 依赖
- 删 `performance-engine-center/.../config/ShedLockConfig.java`
- 移除 3 个 Job 上的 `@SchedulerLock` 注解
- 由 Quartz 集群（`isClustered=true` + JDBC JobStore）单一防重

---

## 子项目 B brainstorming 待问的剩余问题（按重要性）

| # | 问题 | 说明 |
|---|---|---|
| 3 | **QRTZ_* 表归属 schema** | 选 A 放业务库 `onepl`（11 张 QRTZ_* 与 sys_job_* 同库）vs B 独立 quartz schema（隔离） |
| 4 | **Job 本体改造方式** | 选 A 删除 @Scheduled 改 implements `Job.execute()` vs B 保留旧方法当业务方法 + 新建 Quartz Job 包装类 |
| 5 | **JobApi 是否扩展** | 选 A 不动（Quartz JobListener 自动调 startJobRun/completeJobRun）vs B 加 syncToScheduler/triggerNow 等 |
| 6 | **失败重试策略** | 选 A Quartz 默认无重试 + 下次调度时重跑（与现状一致） vs B 启用 Quartz 内置 misfire/refireImmediately vs C 业务侧手写指数退避 |
| 7 | **测试策略** | 选 A 当作新功能 TDD 重写（与子项目 A 一致）vs B 特征化测试（保留旧 @Scheduled 行为）—— 推 A，因为 3 个 Job 现状压根没运行过 |

---

## B 关键现状（已通过深度探索确认）

### 3 个 @Scheduled Job

文件：`performance-engine-center/src/main/java/com/bank/branch/platform/performance/job/`

| Job | Cron 默认 | 业务逻辑 |
|---|---|---|
| `DailyKpiCalcJob` | `0 30 1 * * ?` | 遍历 ACTIVE 方案 → 调 KpiCalcService.calcScheme（T-1） |
| `SysControlCleanupJob` | `0 0 3 * * ?` | 按 scope_dim 分组保留最新 12 条历史 |
| `PerfRunTaskCleanupJob` | `0 30 3 * * ?` | 删 SUCCESS 状态 + end_time < now-90d 的任务 |

**关键事实**：
- 三个 Job 均通过 `${prefix.cron:default}` EL 表达式读 cron，可在 application.yml 覆盖
- 三个 Job 均带 `@ConditionalOnProperty(matchIfMissing=false)` 双保险
- 全库无任何 `@EnableScheduling` 注解
- 三个 Job 的 `run()` 方法**从未调用 JobApi**
- 三个 Job 与 `sys_job_conf.job_key` 无关联（job_key 表数据可能为空）

### sys_job_conf + sys_job_run_log

DDL 见 `docs/schema/ddl-governance.sql:71-107`。字段：
- sys_job_conf: id / job_key (UNIQUE) / job_name / cron_expr / status (ACTIVE/PAUSED) / allow_manual_trigger / last_run_time / next_run_time / remark / 审计字段
- sys_job_run_log: id / job_id / trigger_type (SCHEDULED/MANUAL) / reason / start_time / end_time / status (RUNNING/SUCCESS/FAILED) / error_msg / created_by

### JobApi (system-governance-center) — 4 方法（已存在）

```java
Optional<JobConfDTO> getJobConf(String jobKey);
String startJobRun(String jobId, String triggerType, String operatorEmpId);
void completeJobRun(String runLogId);
void failJobRun(String runLogId, String errorMsg);
```

### JobController 5 端点（已存在但 trigger/pause/resume 是装饰品）

| 方法 | 路径 | 鉴权 |
|---|---|---|
| GET | `/api/admin/sys/jobs` | SYS_CONFIG, READ |
| GET | `/api/admin/sys/jobs/{jobId}/logs` | SYS_CONFIG, READ |
| POST | `/api/admin/sys/jobs/{jobId}/trigger` | SYS_CONFIG, JOB_TRIGGER |
| PUT | `/api/admin/sys/jobs/{jobId}/pause` | SYS_CONFIG, CONFIG |
| PUT | `/api/admin/sys/jobs/{jobId}/resume` | SYS_CONFIG, CONFIG |

---

## 工作环境

- **Worktree**: `D:\Project\oneplate\.claude\worktrees\refactor-quartz-job`
- **Branch**: `refactor/quartz-job-integration`（从 master `af66ccd` 分叉）
- **子代理模型**: sonnet（按 user memory）
- **执行节奏**: implementer + 1 次合并 spec+quality reviewer（与子项目 A 后期一致）

---

## Compact 后续接指令

读完本文档后：

1. 读 `docs/superpowers/specs/2026-04-25-excel-easyexcel-migration-design.md`（A 子项目最终 spec，可作 B spec 格式参考）
2. 读 `docs/superpowers/plans/2026-04-25-excel-easyexcel-migration-plan.md`（A 子项目最终 plan，可作 B plan 格式参考）
3. 继续 brainstorming：从问题 #3（QRTZ_* schema 归属）开始问

完成 5 个剩余问题后：
4. 提出 2-3 方案
5. 分节呈现设计
6. 写 spec → 派发 spec-document-reviewer
7. 用户最终审
8. 进入 writing-plans → execute（subagent-driven）
