# 09 — sys_job_conf / Quartz 集群调度运维 Runbook

**版本**: V1.9（整合 V1.6→V1.8 调度知识，2026-05-01）｜**归属模块**: system-governance-center
**权威源**: 本文件是 sys_job_conf / Quartz 集群调度运维的**唯一入口**，各模块 CLAUDE.md 保留摘要+指针。

> ⚠️ **Flyway 已彻底废弃**（详见根 [CLAUDE.md](../../../CLAUDE.md) "Flyway 禁令"红线）。
> 本文件下方 § 2 历史"启用前置检查"段中提到的 `mvn flyway:migrate` / `flyway_schema_history` /
> `V1_7_0FlywayIT` 等内容仅作**历史档案**保留，对应迁移脚本与 IT 测试基类已从源码中删除。
> 当前 schema 变更操作流程：DBA/开发者直接将 SQL 在目标库执行（`mysql -u... < ddl.sql`
> 或 source 命令），不再使用任何"按版本号自动 migrate"框架。

---

## § 1. 表结构概览

### 1.1 三类表的关系

平台调度体系由三层表组成：

```
业务配置层                  运行日志层           Quartz 持久层
────────────               ────────────         ─────────────────────
SYS_JOB_CONF               SYS_JOB_RUN_LOG      QRTZ_JOB_DETAILS
  job_key (唯一)              job_id → SYS_JOB_CONF   QRTZ_TRIGGERS
  cron_expr                   status               QRTZ_CRON_TRIGGERS
  quartz_job_class            trigger_type         QRTZ_FIRED_TRIGGERS
  status                      start/end_time       QRTZ_LOCKS
  misfire_policy              error_msg            QRTZ_SCHEDULER_STATE
```

- **SYS_JOB_CONF**：业务语义层，持有 job_key / cron_expr / quartz_job_class / status。
  JobService.syncJobsOnStartup 以此为权威源同步到 QRTZ_* 表。
- **SYS_JOB_RUN_LOG**：执行记录层，由 JobExecutionLogger（全局 Quartz JobListener）
  在每次调度开始/结束时自动写入，业务模块无需调用任何写日志 API。
- **QRTZ_\* 表**（11 张）：Quartz JDBC JobStore 持久层，由 ddl-quartz.sql 手动初始化，
  spring.quartz.jdbc.initialize-schema=never。

### 1.2 SYS_JOB_CONF 主键与关键字段语义

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | varchar(32) | 主键，业务生成 |
| `job_key` | varchar(100) | 唯一业务标识，如 `LEAD_CALLBACK_COMPENSATE`；UNIQUE KEY `uk_job_key` |
| `job_name` | varchar(200) | 显示名称 |
| `cron_expr` | varchar(100) | Quartz cron 表达式，6 段（含秒） |
| `quartz_job_class` | varchar(255) | Quartz 包装 Job 类全限定名（V1.6 新增）；反射加载失败会在 syncJobsOnStartup 中 catch + log.error 跳过 |
| `misfire_policy` | varchar(32) | 默认 `FIRE_ONCE_NOW`；LEAD_CALLBACK_COMPENSATE 使用 `DO_NOTHING` |
| `status` | varchar(20) | `ACTIVE`（启用）/ `PAUSED`（暂停）/ 逻辑删除时行可直接 DELETE（V1_7_1 删 DAILY_KPI_CALC） |
| `allow_manual_trigger` | tinyint(1) | 是否允许通过 JobController REST 端点手动触发，默认 1 |
| `last_run_time` | datetime | 上次执行时间（由生产代码写入；V1.7 观察项 #1：perf_metric_def.last_run_time 尚未写入） |

### 1.3 SYS_JOB_RUN_LOG status 字段语义

| 值 | 含义 |
|---|---|
| `RUNNING` | JobExecutionLogger.jobToBeExecuted 写入；执行中 |
| `SUCCESS` | jobWasExecuted 无异常时更新 |
| `FAILED` | jobWasExecuted 捕获 JobExecutionException 时更新，error_msg 记录异常栈 |

trigger_type 取值：`SCHEDULED`（cron 触发）/ `MANUAL`（JobController 手动触发）。

### 1.4 关键 QRTZ_* 表用途

| 表 | 用途 |
|---|---|
| `QRTZ_JOB_DETAILS` | 存储 JobDetail 元信息，JOB_NAME 对应 sys_job_conf.job_key |
| `QRTZ_TRIGGERS` / `QRTZ_CRON_TRIGGERS` | Trigger 主表 + cron 扩展，TRIGGER_STATE 可查执行状态 |
| `QRTZ_FIRED_TRIGGERS` | 正在执行的 Trigger 记录，INSTANCE_NAME 可确认执行节点 |
| `QRTZ_LOCKS` | 集群行锁防重，含 `TRIGGER_ACCESS` / `STATE_ACCESS` 两把锁 |
| `QRTZ_SCHEDULER_STATE` | 各节点心跳，LAST_CHECKIN_TIME 可判断节点存活 |

来源：system-governance-center/CLAUDE.md「数据库表」段 + docs/schema/ddl-governance.sql + docs/schema/ddl-quartz.sql 头部注释

---

## § 2. 启用前置检查

以下按**时间倒序**列出各版本 DDL 迁移的预检步骤（最新版本在前）。

### 2.1 V1.8（2026-05-01）— customer 模块 LeadCallback 注册

**迁移脚本**：`customer-marketing-center/src/main/resources/sql/V1_8_0__register_lead_callback_compensate_job.sql`

**迁移目的**：将 customer 模块 LeadCallbackCompensation @Scheduled 迁移到 Quartz，在 sys_job_conf
注册 LEAD_CALLBACK_COMPENSATE 行。

**预检 SQL**（确认行尚未存在，防止重复插入报 UK 冲突）：

```sql
-- 预检：sys_job_conf 是否已含 LEAD_CALLBACK_COMPENSATE 行
SELECT job_key, status, cron_expr
FROM SYS_JOB_CONF
WHERE job_key = 'LEAD_CALLBACK_COMPENSATE';
-- 无结果 → 安全执行 V1_8_0 脚本
-- 有结果 → 脚本有 INSERT IGNORE 或 ON DUPLICATE KEY 逻辑，直接跑也可；但需确认 cron_expr 正确
```

**期望结果**：无结果（首次）或 cron_expr = `0 */5 * * * ?`（每 5 分钟扫描一次未回调线索）。

**失败处理**：若存在行但 cron_expr 不正确，手工 UPDATE 后重跑。

来源：customer-marketing-center/CLAUDE.md「V1.8 改动进度」段

---

### 2.2 V1.7（2026-04-30）— perf 模块指标级调度

**迁移脚本**：
- `V1_7_0__perf_metric_def_schedule_cols.sql`：perf_metric_def 加 cron_expr / subject_sql / last_run_time + idx_metric_def_schedulable 索引
- `V1_7_1__remove_daily_kpi_calc_job.sql`：删除 sys_job_conf 中 DAILY_KPI_CALC 行（KPI 改为事件驱动）

**迁移目的**：支持每条 ACTIVE+AUTO 指标 1:1 注册一个 Quartz Job，并废除旧的每日 KPI 批量计算定时任务。

**预检 SQL**：

```sql
-- 预检 1：EXPR/GROOVY 类型指标 subject_sql 是否填齐
-- 缺失则 syncJobsOnStartup 会跳过该指标的 registerJob，指标不会被调度
SELECT COUNT(*) AS missing_subject_sql
FROM perf_metric_def
WHERE status = 'ACTIVE'
  AND calc_mode = 'AUTO'
  AND calc_logic_type IN ('EXPR', 'GROOVY')
  AND (subject_sql IS NULL OR subject_sql = '');
-- 期望: 0；非 0 则补填 subject_sql 后再迁移

-- 预检 2：sys_job_conf 中 DAILY_KPI_CALC 行是否存在（V1_7_1 会删除它）
SELECT job_key, status, cron_expr
FROM SYS_JOB_CONF
WHERE job_key = 'DAILY_KPI_CALC';
-- 期望: 1 行；若已不存在说明 V1_7_1 已执行过，可跳过
```

**启动后验证**：

```sql
-- 启动后确认 PERF_METRIC_* 系列 Job 已注册
SELECT job_key, cron_expr, status
FROM SYS_JOB_CONF
WHERE job_key LIKE 'PERF_METRIC_%';
-- 应有 N 条，N = ACTIVE+AUTO 且 subject_sql 已填的指标数
```

**失败处理**：若 V1.7 schema 变更 SQL 执行失败，按 MySQL 报错定位后修复（如冲突列、索引、数据），
再 source 一次。严禁跳过当前版本直接执行后续 SQL，否则 schema 状态不一致。

来源：performance-engine-center/CLAUDE.md「V1.7 启用前置检查」段

---

### 2.3 V1.4（2026-04-24）— perf Target 字段扩展

**迁移脚本**：`V1_4_0__perf_target_owner_cols.sql`：perf_target_plan / perf_target_value 各加
`owner_emp_id` / `owner_org_code` 字段 + 索引，历史数据以 `owner_emp_id = created_by` 兜底回填。

**迁移目的**：修复 Target 数据范围注入降级到 created_by 的问题，引入标准 owner 字段模型。

**预检 SQL**：

```sql
-- 预检 1：确认两表字段尚未存在（幂等兜底）
SELECT TABLE_NAME, COLUMN_NAME
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('perf_target_plan', 'perf_target_value')
  AND COLUMN_NAME IN ('owner_emp_id', 'owner_org_code');
-- 无结果 → 安全执行
-- 有结果 → 脚本本身是 ADD COLUMN IF NOT EXISTS，直接跑也可

-- 预检 2：历史行 created_by 分布（估计 owner_emp_id 回填后的准确率）
SELECT COUNT(*), COUNT(DISTINCT created_by)
FROM perf_target_plan
WHERE created_by IS NOT NULL;

SELECT COUNT(*), COUNT(DISTINCT created_by)
FROM perf_target_value
WHERE created_by IS NOT NULL;
```

**期望结果**：预检 1 无结果为佳；预检 2 仅供评估，无阻断条件。

**失败处理**：undo 脚本 `U1_4_0__perf_target_owner_cols.sql` 可 DROP 两列；
undo 前确认 Service 已退回到 V1.3 的 created_by 配置，否则查询会报"未知列"。

来源：performance-engine-center/CLAUDE.md「V1.4 启用前置检查」段

---

### 2.4 V1.3（2026-04-24）— perf 基础债务清偿 DDL

**迁移脚本**：
- `V1_2_5__perf_cleanup_null_deleted.sql`：清理 perf_metric_def 历史 NULL deleted 行
- `V1_3_0__perf_run_task_uk.sql`：为 perf_run_task 补加 uk_task_key 唯一键

**迁移目的**：补偿 V1.0 MetricDefService.create 漏填 deleted 字段的历史 bug；补齐
uk_task_key 唯一约束作为 Redis SETNX 幂等的 DB 兜底。

**预检 SQL**：

```sql
-- 预检 1（V1_2_5）：perf_metric_def 有多少历史行 deleted 为 NULL
-- 注意：仅处理 perf_metric_def 一张表（perf_target_plan/value 无 deleted 字段）
SELECT COUNT(*) AS null_deleted_rows
FROM perf_metric_def
WHERE deleted IS NULL;
-- V1_2_5 将这些 NULL 置为 0，等价于"兜底"而非"误删"

-- 预检 2（V1_3_0）：是否有重复 task_key 导致 ALTER 失败
SELECT task_key, COUNT(*) AS cnt
FROM perf_run_task
WHERE task_key IS NOT NULL
GROUP BY task_key
HAVING COUNT(*) > 1;
-- 有结果 → 手工清理重复行后再 source ALTER 脚本
-- 无结果 → 直接 source ALTER 脚本
```

**期望结果**：预检 2 无结果，可直接执行；预检 1 行数多少均安全。

**失败处理**：若 V1.3 ALTER 因存量重复失败：
1. 按业务规则手工清理重复行
2. 重新 source 当期 ALTER 脚本
3. **严禁**直接跳到下个版本的 SQL，否则 schema 状态不一致

来源：performance-engine-center/CLAUDE.md「V1.3 启用前置检查」段

---

### 2.5 V1.6（2026-04-25）— Quartz 基础设施初始化

**迁移脚本**：`docs/schema/ddl-quartz.sql`（手动执行）

**迁移目的**：初始化 11 张 QRTZ_* 表，为 Quartz JDBC JobStore 集群模式提供持久层。

**预检 SQL**（确认 QRTZ_* 表尚未存在）：

```sql
-- 预检：11 张 QRTZ_* 表是否已存在
SELECT TABLE_NAME
FROM INFORMATION_SCHEMA.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME LIKE 'QRTZ_%';
-- 无结果 → 执行 ddl-quartz.sql
-- 有结果 → 已初始化，跳过（或检查表结构是否完整）
```

**期望结果**：11 张表全部存在，或全部不存在（初次部署）。

**配置验证**：确认 application.yml 或 bootstrap.yml 含：
```yaml
spring:
  quartz:
    jdbc:
      initialize-schema: never   # 必须为 never，DDL 手动初始化
    properties:
      org.quartz.jobStore.isClustered: true
```

来源：system-governance-center/CLAUDE.md「Quartz 集群调度」段 + ddl-quartz.sql 头部注释

---

## § 3. 紧急停止

紧急停止分三个层级，影响范围由小到大：

### 3.1 业务级停止（推荐：停单个 Job，无需重启）

修改 sys_job_conf.status = 'PAUSED'，再通过 JobController 触发重新同步（或重启应用）：

```sql
-- 停止单个 Job（以 LEAD_CALLBACK_COMPENSATE 为例）
UPDATE SYS_JOB_CONF
SET status = 'PAUSED', updated_time = NOW()
WHERE job_key = 'LEAD_CALLBACK_COMPENSATE';
```

然后调用 REST 端点（`@BizAuth(SYS_CONFIG)` 鉴权）触发同步：
```
POST /api/admin/sys/jobs/{jobId}/pause
```

或重启应用，syncJobsOnStartup 会读取 PAUSED 状态并 pause Quartz Trigger。

**影响范围**：仅停止指定 Job 的 cron 调度，其他 Job 不受影响。
**是否需要重启**：不需要（通过 REST 端点同步）或需要（直接 SQL 改后等下次启动）。
**回滚成本**：低——改回 ACTIVE 后同步即可恢复。

---

### 3.2 Quartz 级停止（停 Trigger，绕过业务配置层）

直接操作 QRTZ_TRIGGERS 表或通过 JobController REST 端点：

```sql
-- 查看当前 Trigger 状态
SELECT TRIGGER_NAME, TRIGGER_GROUP, TRIGGER_STATE, NEXT_FIRE_TIME
FROM QRTZ_TRIGGERS
WHERE JOB_NAME = 'LEAD_CALLBACK_COMPENSATE';

-- 直接暂停 Trigger（TRIGGER_STATE = 'PAUSED'）
-- 注意：此操作绕过 sys_job_conf，重启后 syncJobsOnStartup 会按 sys_job_conf 状态重置
-- 生产建议优先走 § 3.1 业务级停止，保持两层一致
UPDATE QRTZ_TRIGGERS
SET TRIGGER_STATE = 'PAUSED'
WHERE TRIGGER_NAME = 'LEAD_CALLBACK_COMPENSATE'
  AND SCHED_NAME = 'BranchPlatformScheduler';
```

也可调用 JobController REST 端点（推荐，有鉴权和审计）：
```
POST /api/admin/sys/jobs/{jobId}/pause
```

**影响范围**：仅停止指定 Job 的 Quartz Trigger，sys_job_conf 状态不变（两层可能短暂不一致）。
**是否需要重启**：不需要。
**回滚成本**：中——需手动或通过 REST 端点恢复，且重启后 syncJobsOnStartup 会以 sys_job_conf 为准覆盖。

---

### 3.3 配置级停止（停整个 Quartz 子系统或 HealthCheck）

**停整个 Quartz 子系统**（影响所有调度 Job）：

在 application.yml 中设置：
```yaml
spring:
  quartz:
    enabled: false
```

需要重启应用才能生效。**影响范围**：所有 Quartz 调度任务全部停止，包括
SYSCONTROL_CLEANUP / PERF_RUN_TASK_CLEANUP / LEAD_CALLBACK_COMPENSATE /
所有 PERF_METRIC_* 指标调度。**回滚成本**：高——需改配置文件 + 重启。

**仅停 MetricSchedulerHealthCheck**（保留 Quartz，仅停止指标补偿 @Scheduled）：

```yaml
perf:
  scheduler:
    health-check:
      enabled: false
```

需要重启。**影响范围**：仅停止每 10 分钟扫描的指标调度补偿检查，不影响 Quartz 正式调度。
**回滚成本**：中——改配置 + 重启。

来源：performance-engine-center/CLAUDE.md「紧急停止」段 + system-governance-center/CLAUDE.md「集群与防重」段

---

## § 4. 业务 Job 清单

以下为系统当前所有 ACTIVE Job（V1.8 交付后）：

| jobKey | group | cron | cron 说明 | misfire 策略 | quartz_job_class | 业务用途 |
|---|---|---|---|---|---|---|
| `SYSCONTROL_CLEANUP` | DEFAULT | `0 0 3 ? * SUN` | 每周日凌晨 3 点 | FIRE_ONCE_NOW | SysControlCleanupQuartzJob | 清理过期 sys_control 历史版本 |
| `PERF_RUN_TASK_CLEANUP` | DEFAULT | `0 0 4 * * ?` | 每日凌晨 4 点 | FIRE_ONCE_NOW | PerfRunTaskCleanupQuartzJob | 清理 perf_run_task 90 天前数据 |
| `LEAD_CALLBACK_COMPENSATE` | DEFAULT | `0 */5 * * * ?` | 每 5 分钟执行一次 | DO_NOTHING | LeadCallbackCompensateQuartzJob | 扫描 customer 模块未回调线索并补偿 |
| `PERF_METRIC_${metricCode}` | PERF_METRIC | 按各指标 cron_expr | 随指标配置 | 随指标 misfire 配置 | MetricExecuteQuartzJob | V1.7 指标级调度；每个 ACTIVE+AUTO 指标自动注册一个 Job |

**已删除 Job**（历史归档）：

| jobKey | 删除版本 | 原因 |
|---|---|---|
| `DAILY_KPI_CALC` | V1.7（2026-04-30，V1_7_1 脚本删除） | KPI 计算改为事件驱动（MetricCalcCompletedEvent → KpiCascadeListener） |

**残留 Spring @Scheduled**（非 Quartz，设计意图保留）：

| 类 | 调度 | 说明 |
|---|---|---|
| `MetricSchedulerHealthCheck` | 每 10 分钟 | V1.7 spec § 7 明确论证：补偿器不依赖 Quartz，故意不 Quartz 化（V1.9 永久关闭该迁移计划） |

来源：performance-engine-center/CLAUDE.md「3 个定时任务调度（V1.6 quartz 整合后）」段 + customer-marketing-center/CLAUDE.md「V1.8 改动进度」段

---

## § 5. 故障排查

### 5.1 cron 误植

**现象**：Job 触发时间不符预期（过频 / 不触发）。

**排查**：

```sql
-- 查看当前配置
SELECT job_key, cron_expr, status, last_run_time
FROM SYS_JOB_CONF
WHERE job_key = 'LEAD_CALLBACK_COMPENSATE';

-- 确认 Quartz 侧 Trigger 的 cron 是否已同步
SELECT ct.CRON_EXPRESSION, t.NEXT_FIRE_TIME, t.TRIGGER_STATE
FROM QRTZ_CRON_TRIGGERS ct
JOIN QRTZ_TRIGGERS t ON t.TRIGGER_NAME = ct.TRIGGER_NAME
  AND t.TRIGGER_GROUP = ct.TRIGGER_GROUP
  AND t.SCHED_NAME = ct.SCHED_NAME
WHERE ct.TRIGGER_NAME = 'LEAD_CALLBACK_COMPENSATE';
```

**修复步骤**：
1. UPDATE SYS_JOB_CONF SET cron_expr = '修正后表达式' WHERE job_key = '...'
2. 调用 `POST /api/admin/sys/jobs/{jobId}/reschedule` 或重启，syncJobsOnStartup 会覆盖 QRTZ_CRON_TRIGGERS
3. 验证 QRTZ_TRIGGERS.NEXT_FIRE_TIME 已更新

**下次 reload 时机**：应用启动（syncJobsOnStartup）或通过 JobController REST 端点触发，
不会自动定时重读 sys_job_conf（需显式触发或重启）。

---

### 5.2 多节点行为确认

**集群模式**：`isClustered=true`，QRTZ_LOCKS 行锁（`TRIGGER_ACCESS` / `STATE_ACCESS`）
保证同一 Trigger 在多节点环境下只有一个节点执行。

**确认哪个节点在跑**：

```sql
-- 查看当前正在执行的 Trigger 及其所在节点
SELECT TRIGGER_NAME, TRIGGER_GROUP, INSTANCE_NAME, FIRED_TIME, STATE
FROM QRTZ_FIRED_TRIGGERS
WHERE JOB_NAME = 'LEAD_CALLBACK_COMPENSATE'
  AND SCHED_NAME = 'BranchPlatformScheduler'
ORDER BY FIRED_TIME DESC
LIMIT 10;

-- 查看各节点心跳（判断节点存活）
SELECT INSTANCE_NAME, LAST_CHECKIN_TIME, CHECKIN_INTERVAL
FROM QRTZ_SCHEDULER_STATE
WHERE SCHED_NAME = 'BranchPlatformScheduler'
ORDER BY LAST_CHECKIN_TIME DESC;
```

---

### 5.3 反射加载失败（类不存在）

**现象**：syncJobsOnStartup 启动时跳过某个 Job，sys_job_conf 有配置但 Quartz 侧无对应
JobDetail；sys_job_run_log 可能出现 status=FAILED 且 error_msg 含 ClassNotFoundException。

**原因**：sys_job_conf.quartz_job_class 指向的类不存在（如 V1.6→V1.7 迁移删除了
DailyKpiCalcQuartzJob，但 sys_job_conf 旧行未删除）。

**排查**：

```sql
-- 查看失败日志
SELECT job_id, status, error_msg, created_time
FROM SYS_JOB_RUN_LOG
WHERE status = 'FAILED'
ORDER BY created_time DESC
LIMIT 20;

-- 对照 sys_job_conf 确认 quartz_job_class 是否仍存在
SELECT job_key, quartz_job_class, status
FROM SYS_JOB_CONF
WHERE status = 'ACTIVE';
```

**修复**：若类已删除（如 DailyKpiCalcQuartzJob），手工 DELETE 对应 sys_job_conf 行；
或更新 quartz_job_class 为新类名。

---

### 5.4 Quartz 锁竞争（QRTZ_LOCKS 死锁）

**现象**：多节点下 Job 触发延迟，MySQL slow query log 出现 QRTZ_LOCKS 相关长时间等待。

**排查**：

```sql
-- 查看 InnoDB 当前锁与死锁信息
SHOW ENGINE INNODB STATUS;
-- 在输出中搜索 QRTZ_LOCKS 相关的 TRANSACTION 段

-- 查看当前持锁线程
SELECT * FROM INFORMATION_SCHEMA.INNODB_TRX
WHERE trx_tables_locked > 0;
```

**处理**：通常由 Quartz 内部自动超时释放；若持续死锁，按 MySQL 标准流程 KILL 问题线程，
Quartz 节点会在下一个 checkin 周期重新竞争锁。

---

### 5.5 sys_job_conf 与 Quartz 不一致（孤儿 Job）

**背景**：V1.9 评估认为此情况实际产生频率 < 1 次/年（V1.9 spec § 1.1 # 3 永久关闭
自动反向清理实现）。Quartz 侧孤儿 Job 对运行无害（不会触发，不会写日志），
运维侧可手工排查。

**孤儿 Job 排查**（Quartz 有但 sys_job_conf 无记录）：

```sql
-- 查找 Quartz 中有但 sys_job_conf 中无对应行的 Job（孤儿）
SELECT j.JOB_NAME, j.JOB_GROUP, j.JOB_CLASS_NAME
FROM QRTZ_JOB_DETAILS j
LEFT JOIN SYS_JOB_CONF s ON s.job_key = j.JOB_NAME
WHERE s.job_key IS NULL
  AND j.SCHED_NAME = 'BranchPlatformScheduler';
```

**清理方式**（手工）：若确认为孤儿 Job，调用 JobController 的 unregisterJob 端点，
或直接通过 Quartz API 删除 JobDetail + Trigger（级联删除）。

来源：system-governance-center/CLAUDE.md「集群与防重」段 + V1.9 spec § 1.1（# 3 关闭决策）

---

## § 6. 升级路径（V1.0 → V1.6 → V1.7 → V1.8 调度演进史）

### 6.1 V1.0–V1.5：Spring @Scheduled + ShedLock + Redis 防重

**时间范围**：2026-04-15（V1.0）→ 2026-04-24（V1.5）

**调度方式**：各业务模块（performance-engine-center）独立使用 Spring `@Scheduled` 注解 +
`@SchedulerLock`（ShedLock）在 Redis 层面防止多实例重复执行。

**JobApi 当时形态**（4 个方法）：`getJobConf` / `startJobRun` / `completeJobRun` / `failJobRun`，
业务模块须在 Job 执行前后显式调用写日志方法，耦合性高。

**缺陷**：ShedLock 依赖 Redis 单点可用性；Redis 宕机窗口内无防重保障。

---

### 6.2 V1.6（2026-04-25）：Quartz 集成，ShedLock 全部废弃

**交付内容**：
- governance 新增 Quartz 基础设施：`QuartzConfig`（注册 JobExecutionLogger 为全局 JobListener） +
  `JobExecutionLogger`（统一写 sys_job_run_log）+ `JobService.syncJobsOnStartup`（启动同步）
- ddl-quartz.sql 手动初始化 11 张 QRTZ_* 表
- performance 模块 3 个 `@Scheduled` 任务迁移为 Quartz（删除 `@SchedulerLock` 注解）：
  - DailyKpiCalcJob → DailyKpiCalcQuartzJob（每日凌晨 2 点，`0 0 2 * * ?`）
  - SysControlCleanupJob → SysControlCleanupQuartzJob（每周日凌晨 3 点，`0 0 3 ? * SUN`）
  - PerfRunTaskCleanupJob → PerfRunTaskCleanupQuartzJob（每日凌晨 4 点，`0 0 4 * * ?`）
- 删除 ShedLock 全部痕迹（pom.xml + ShedLockConfig + 相关测试）
- **JobApi 精简到 1 方法**：仅保留 `getJobConf`；`startJobRun/completeJobRun/failJobRun`
  由 JobExecutionLogger 统一接管，业务模块无需调用

**防重机制切换**：ShedLock + Redis → Quartz QRTZ_LOCKS 行锁（InnoDB 事务保证，不依赖 Redis）

---

### 6.3 V1.7（2026-04-30）：指标级 Quartz 调度，JobApi 扩展

**交付内容**：
- perf_metric_def 新增 `cron_expr` / `subject_sql` / `last_run_time` 字段（V1_7_0 DDL）
- **JobApi 扩展为 3 方法**：新增 `registerJob(RegisterJobCmd)` + `unregisterJob(jobKey)`，
  支持业务模块声明式动态注册/注销 Quartz Job
- 指标 CRUD afterCommit Hook 自动调用 `registerJob` / `unregisterJob`
- MetricSchedulerService.syncSchedulableMetrics 启动时批量同步所有 ACTIVE+AUTO 指标
- 通用 MetricExecuteQuartzJob（按 JobDataMap.metricCode 派发，不再需要每指标一个 Job 类）
- MetricSchedulerHealthCheck：每 10 分钟补偿扫描（Spring @Scheduled，`@ConditionalOnProperty` 启停）
- **DailyKpiCalcJob 删除**：KPI 计算改为事件驱动——
  MetricCalcCompletedEvent → KpiCascadeListener（@TransactionalEventListener AFTER_COMMIT + @Async +
  Redis SETNX 30s 防重）
- V1_7_1 DDL 脚本删除 sys_job_conf 中 DAILY_KPI_CALC 行

**错误码扩展**：
- GOV-50010 (JOB_CRON_INVALID)
- GOV-50011 (JOB_CLASS_NOT_FOUND)
- GOV-50012 (JOB_REGISTER_FAILED)

---

### 6.4 V1.8（2026-05-01）：customer LeadCallback @Scheduled → Quartz 迁移

**交付内容**：
- LeadCallbackCompensationService.scheduledScan() + @Scheduled 入口删除
- 新增 `LeadCallbackCompensateQuartzJob`（实现 `org.quartz.Job`）
- V1_8_0 DDL：sys_job_conf 插入 LEAD_CALLBACK_COMPENSATE 行
  - cron = `0 */5 * * * ?`（每 5 分钟扫描一次）
  - misfire = DO_NOTHING（补偿任务跳过错过的执行，不追偿）
- customer 模块 `CustomerSchedulingConfig`（@EnableScheduling）删除，
  @EnableScheduling 职责归还 performance 模块的 `PerformanceSchedulingConfig`
- ArchUnit `NoCustomerScheduledArchTest` 守护 customer 模块零 @Scheduled
- 多实例防重：依旧由 QRTZ_LOCKS 行锁保证（与其他 Quartz Job 一致）

**V1.9 候选评估结果**（2026-05-01 二次收敛）：

| 候选事项 | 处置 |
|---|---|
| HealthCheck 也 Quartz 化 | 永久关闭：V1.7 spec § 7 论证"补偿器不依赖 Quartz"是设计意图 |
| sys_job_conf 运维 Runbook（本文档） | 已交付（V1.9） |
| Quartz JobStore 反向清理 | 永久关闭：实际产生 < 1 次/年，孤儿对运行无害，成本/收益失衡 |
| 大小写一致性治理 | 已交付（V1.9 bootstrap test data SQL 30 处） |
| 测试库环境完整治理 | 延期 V1.10 |

---

### 6.5 当前调度格局总结

| 维度 | 当前状态 |
|---|---|
| 调度引擎 | Quartz 2.3.x，JDBC JobStore，isClustered=true |
| 防重机制 | QRTZ_LOCKS InnoDB 行锁（已替代 ShedLock + Redis） |
| 日志写入 | JobExecutionLogger 全局统一（业务模块零感知） |
| JobApi 方法数 | 3（getJobConf + registerJob + unregisterJob） |
| @Scheduled 残留 | 仅 MetricSchedulerHealthCheck（设计意图保留） |
| ACTIVE Quartz Job 数 | 3 固定（SYSCONTROL_CLEANUP / PERF_RUN_TASK_CLEANUP / LEAD_CALLBACK_COMPENSATE）+ N 指标级动态 Job |

来源：performance-engine-center/CLAUDE.md「V1.6/V1.7/V1.8」段 +
customer-marketing-center/CLAUDE.md「V1.8 改动进度」段 +
V1.9 spec docs/superpowers/specs/2026-05-01-v1.9-runbook-and-case-consistency-design.md

---

## § 7. 测试库合一（V1.10 / 2026-05-01）

### 唯一测试库

V1.10 合一后平台测试 mysql 库**唯一为 `onepl_test_bootstrap`**：
- bootstrap @SpringBootTest 业务 IT 用
- perf 模块 IT 用
- report 模块 IT 用

> Flyway 已彻底废弃（详见根 CLAUDE.md "Flyway 禁令"红线），原 perf/report 的 `*FlywayTestBase` /
> `*FlywayIT` 测试基类已从源码中删除。

### v103 库废弃

`onepl_test_v103` 库已废弃。V1.10 P5 阶段**不**物理 DROP（保留为 V1.10 回滚 fallback 1-2 周）。V1.10 稳定 1-2 周后由运维手工执行：
```sql
DROP DATABASE onepl_test_v103;
```

### 来源

详细决策溯源 + 路径 Y 设计：`docs/superpowers/specs/2026-05-01-v1.10-test-db-unification-design.md`
