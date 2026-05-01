# V1.8 — Customer Lead Callback 补偿任务迁移到 Quartz 设计

**版本**：V1.8
**日期**：2026-05-01
**作者**：Claude Code
**状态**：DRAFT，待 review

---

## 1. 背景与目标

### 1.1 起因

V1.6 把 performance 模块的 3 个 `@Scheduled` 任务（`DailyKpiCalcJob` / `PerfRunTaskCleanupJob` / `SysControlCleanupJob`）迁移到 Quartz 集群调度（`QRTZ_LOCKS` 行锁防重）。V1.7 又把 performance 指标级调度也改造为 Quartz。

V1.8 排查发现，整个仓库**仍有一个业务定时任务**遗留在 Spring `@Scheduled` 上：

- `customer-marketing-center` 的 `LeadCallbackCompensationService.scheduledScan()`：每 5 分钟扫 stuck IN_APPROVAL 线索做补偿。

该任务：

- **是业务任务**——会写表 + 反查 Flowable 流程 + 触发 reconcile（更新 `cust_lead.lead_status`、发布 `LeadApprovedEvent` / `LeadDeletedEvent`）；
- **多实例并发不安全**——Spring `@Scheduled` 没有集群协调；如果该模块未来部署多实例，每 5min N 个实例同时扫表，幂等性虽由 reconcile 内部 `conditionalUpdateStatus` (CAS) 兜底，但**重复触发外部回调和 Spring 事件链是浪费**；
- **不符合 V1.6 既定标准**——V1.6 已确立"业务调度全部走 Quartz、由 `QRTZ_LOCKS` 行锁防重"原则，这是历史遗留漏迁。

### 1.2 V1.8 目标

把 `LeadCallbackCompensationService` 的调度入口从 Spring `@Scheduled` 迁移到 Quartz，使整个仓库的**业务定时调度全部由 `sys_job_conf` + Quartz 集群调度统一管理**。

迁移后：

- ✅ 仓库内零业务 `@Scheduled`（仅 performance `MetricSchedulerHealthCheck` 兜底巡检保留 `@Scheduled`，已在 V1.7 spec 论证）；
- ✅ customer-marketing-center 模块零 `@Scheduled` 注解（ArchUnit 守护防回退）；
- ✅ Lead 补偿任务在多实例部署下由 `QRTZ_LOCKS` 行锁防重；
- ✅ 调度参数（cron）落库 `sys_job_conf`，运维可在线调整；
- ✅ 配置项 `customer.lead-compensation.interval-ms` 删除，配置来源单一化。

### 1.3 非目标

- ❌ **不动** `MetricSchedulerHealthCheck`：V1.7 spec § 7 已论证保留 `@Scheduled`（兜底巡检需按节点本地触发，不能交给 Quartz 自调度，否则形成"自己兜底自己"循环）；
- ❌ **不动** `LeadCallbackReconcileService`：业务实现层，与调度无关；
- ❌ **不动** `WorkflowCallbackListener`：事件驱动主路径，与补偿任务正交；
- ❌ **不重构** `scanAndCompensate()` 业务逻辑：本期纯入口迁移，业务行为零变化；
- ❌ **不引入** Lead 补偿的并发并行化、batch 调优、性能优化（YAGNI，超出 V1.8 范围）；
- ❌ **不修改** `customer.lead-compensation.stuck-threshold-minutes` / `batch-size` 业务参数（保留）。

---

## 2. 范围

### 2.1 涉及模块

| 模块 | 改动类型 | 说明 |
|---|---|---|
| `customer-marketing-center` | 修改 + 新增 + 删除 | 主战场：删除 `@Scheduled` 入口、新增 Quartz Job 类、删除 `CustomerSchedulingConfig` |
| `performance-engine-center` | 新增 1 个 Configuration | 新建 `PerformanceSchedulingConfig`（@EnableScheduling）服务于 `MetricSchedulerHealthCheck` |
| `system-governance-center` | 0 改动 | 既有 `JobApi.registerJob` 与启动同步路径已就绪（V1.7 已交付） |
| `bootstrap` | 修改 1 处测试 seed | `bootstrap/src/test/resources/data.sql` 加 sys_job_conf 测试 row（与生产 V1_8_0 SQL 一致） |
| `docs/schema` | 修改 | DDL/迁移脚本同步 |

### 2.2 涉及文件清单

新建：

```
customer-marketing-center/src/main/java/com/bank/branch/platform/customer/job/
└── quartz/
    └── LeadCallbackCompensateQuartzJob.java                       (新建)

customer-marketing-center/src/main/java/com/bank/branch/platform/customer/arch/
└── NoCustomerScheduledArchTest.java                               (新建 ArchUnit 守护，放 src/test 下)
                                                                    实际路径：
                                                                    customer-marketing-center/src/test/java/com/bank/branch/platform/customer/arch/

customer-marketing-center/src/test/java/com/bank/branch/platform/customer/job/quartz/
└── LeadCallbackCompensateQuartzJobTest.java                       (新建单测)

customer-marketing-center/src/main/resources/sql/
├── V1_8_0__register_lead_callback_compensate_job.sql              (新建迁移脚本)
└── U1_8_0__remove_lead_callback_compensate_job.sql                (新建撤销脚本)

performance-engine-center/src/main/java/com/bank/branch/platform/performance/config/
└── PerformanceSchedulingConfig.java                               (新建 @EnableScheduling)

bootstrap/src/test/java/com/bank/branch/platform/it/
└── LeadCallbackJobRegisteredIT.java                               (新建启动注册 IT)
```

修改：

```
customer-marketing-center/src/main/java/com/bank/branch/platform/customer/service/
└── LeadCallbackCompensationService.java
    - 删除 @Scheduled scheduledScan() 方法（含顶层 try-catch）
    - 删除 import org.springframework.scheduling.annotation.Scheduled
    - scanAndCompensate() 签名/逻辑不变；javadoc 更新（移除"Spring 调度入口"措辞）

customer-marketing-center/src/main/java/com/bank/branch/platform/customer/config/
└── CustomerSchedulingConfig.java                                  (删除)

bootstrap/src/test/resources/
└── data.sql                                                       (加 sys_job_conf 测试 row)

docs/schema/
└── ddl-system-governance.sql                                      (无变更，列结构 V1.6/V1.7 已就绪)

docs/superpowers/specs/
└── 2026-05-01-customer-lead-callback-quartz-migration-design.md   (本文件)

docs/superpowers/plans/
└── 2026-05-01-customer-lead-callback-quartz-migration-impl.md     (后续生成)

CLAUDE.md (root + customer-marketing-center + performance-engine-center)
└── 更新 V1.8 已交付状态
```

删除：

```
customer-marketing-center/src/main/java/com/bank/branch/platform/customer/config/
└── CustomerSchedulingConfig.java                                  (整类删除)
```

> **配置项删除说明**
> 任何 `application.yml` / `application-test.yml` 含 `customer.lead-compensation.interval-ms` 的位置都必须删除（原则上 production 与 test 配置都未显式声明，仅在 service `@Value` 默认值中存在；删除 `@Value` 行即可。仍需 grep 确认 0 处显式声明）。

---

## 3. 架构概览

### 3.1 现状（V1.7）

```
┌────────────────────────────────────────────────────────────────┐
│                    Spring ApplicationContext                    │
│                                                                 │
│  CustomerSchedulingConfig (@EnableScheduling)                   │
│         │                                                       │
│         │ 启用                                                  │
│         ▼                                                       │
│  LeadCallbackCompensationService                                │
│  ├── scheduledScan()  @Scheduled(fixedDelay 5min) ◀─┐           │
│  └── scanAndCompensate()  ← IT 直接调用            │           │
│         │                                          │ Spring     │
│         ▼                                          │ TaskSched  │
│  reconcileService.reconcileApproved/Rejected      ─┘           │
│                                                                 │
│  MetricSchedulerHealthCheck (performance)                       │
│  └── @Scheduled(fixedDelay 10min) ◀─── 同一 TaskSched          │
│                                                                 │
└────────────────────────────────────────────────────────────────┘
```

问题：

- 单进程调度，多实例下两个实例都会触发；
- `@EnableScheduling` 配置归在 customer 模块，但同时服务 performance 的 HealthCheck，归属混乱。

### 3.2 目标态（V1.8）

```
┌────────────────────────────────────────────────────────────────┐
│                    Spring ApplicationContext                    │
│                                                                 │
│  ┌── governance JobService (V1.6 + V1.7)                        │
│  │     │ ApplicationReadyEvent 启动同步                         │
│  │     ▼                                                        │
│  │   sys_job_conf  ──────► Quartz Scheduler (JDBC JobStore)     │
│  │   ├ JOB_PERF_RUN_TASK_CLEANUP                                │
│  │   ├ JOB_SYS_CONTROL_CLEANUP                                  │
│  │   ├ JOB_LEAD_CALLBACK_COMPENSATE  ◀── V1.8 新增              │
│  │   └ JOB_METRIC_EXEC_*  (V1.7)                                │
│  │                                                              │
│  └── PerformanceSchedulingConfig (@EnableScheduling) ──┐        │
│                                                        │        │
│                                                        ▼        │
│        MetricSchedulerHealthCheck  @Scheduled(10min)            │
│                                                                 │
│                                                                 │
│  Quartz 触发链：                                                │
│  Quartz Trigger → AutowiringSpringBeanJobFactory                │
│                 → LeadCallbackCompensateQuartzJob               │
│                 → leadCallbackCompensationService               │
│                     .scanAndCompensate()                        │
│                                                                 │
│  集群防重：QRTZ_LOCKS 行锁（V1.6 已就绪）                       │
│                                                                 │
└────────────────────────────────────────────────────────────────┘
```

---

## 4. 详细设计

### 4.1 Quartz Job 类

`customer-marketing-center/src/main/java/com/bank/branch/platform/customer/job/quartz/LeadCallbackCompensateQuartzJob.java`

```java
package com.bank.branch.platform.customer.job.quartz;

import com.bank.branch.platform.customer.service.LeadCallbackCompensationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.stereotype.Component;

/**
 * Lead 回调补偿 Quartz Job。
 * <p>
 * V1.8（2026-05-01）由 Spring {@code @Scheduled} 迁移而来：
 * <ul>
 *   <li>cron 由 sys_job_conf.cron_expr 控制（默认 "0 */5 * * * ?"，每 5 分钟）；</li>
 *   <li>misfire 策略 DO_NOTHING（错过即跳过，下个 5min 继续）；</li>
 *   <li>多实例集群防重由 Quartz QRTZ_LOCKS 行锁兜底（V1.6 已就绪）。</li>
 * </ul>
 * </p>
 * <p>
 * <strong>异常处理</strong>：原 {@code scheduledScan()} 顶层 try-catch
 * 移到本类——补偿任务异常不应冒泡到 Quartz 调度器导致 trigger 被禁用。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LeadCallbackCompensateQuartzJob implements Job {

    private final LeadCallbackCompensationService compensationService;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        String jobKey = context.getJobDetail().getKey().toString();
        long start = System.currentTimeMillis();
        try {
            compensationService.scanAndCompensate();
        } catch (Exception e) {
            // 顶层兜底：业务异常不冒泡到 Quartz scheduler；
            // JobExecutionLogger（governance V1.6 全局监听器）会另行记录 vetoed/error 状态。
            log.error("[LeadCallbackCompensateQuartzJob] {} 执行异常", jobKey, e);
        } finally {
            log.info("[LeadCallbackCompensateQuartzJob] {} 完成，耗时 {}ms",
                    jobKey, System.currentTimeMillis() - start);
        }
    }
}
```

设计要点：

1. `implements Job`：使用 `org.quartz.Job` 接口，与 V1.6 三个 cleanup job + V1.7 `MetricExecuteQuartzJob` 一致；
2. `@Component`：由 Spring 容器管理；Quartz 通过 `AutowiringSpringBeanJobFactory`（V1.6 已就绪）注入依赖；
3. `@RequiredArgsConstructor` + `final`：构造器注入 service；
4. **不抛 `JobExecutionException`**：顶层兜底吞异常，与原 `scheduledScan()` 一致；
5. **不传 JobDataMap**：本 Job 无参数（vs V1.7 `MetricExecuteQuartzJob` 需要 metricCode）。

### 4.2 sys_job_conf 数据

V1_8_0 迁移脚本（生产手工执行，**非** Flyway 自动）：

```sql
-- V1.8 注册 Lead 回调补偿 Quartz Job
-- 引入版本：V1.8（2026-05-01）
-- 由 Spring @Scheduled(fixedDelay 5min) 迁移而来
-- 此脚本需手动执行；非 Flyway 自动迁移

INSERT INTO SYS_JOB_CONF (
    id,
    job_key,
    job_name,
    cron_expr,
    quartz_job_class,
    misfire_policy,
    status,
    allow_manual_trigger,
    remark,
    created_by,
    created_time,
    updated_by,
    updated_time
) VALUES (
    'JOB_LEAD_CALLBACK_COMPENSATE',
    'LEAD_CALLBACK_COMPENSATE',
    'Lead 回调补偿巡检',
    '0 */5 * * * ?',
    'com.bank.branch.platform.customer.job.quartz.LeadCallbackCompensateQuartzJob',
    'DO_NOTHING',
    'ACTIVE',
    1,
    'V1.8 由 Spring @Scheduled 迁移；扫 stuck IN_APPROVAL 线索补偿，错过即跳过',
    'SYSTEM',
    NOW(),
    'SYSTEM',
    NOW()
);
```

字段说明：

- `id` = `JOB_LEAD_CALLBACK_COMPENSATE`：沿用 V1.6/V1.7 `JOB_<KEY>` 命名规范；
- `job_key` = `LEAD_CALLBACK_COMPENSATE`：唯一业务键；
- `cron_expr` = `0 */5 * * * ?`：每 5 分钟（与原 `fixedDelay 300_000ms` 等价）；
- `quartz_job_class` = 全限定类名，governance JobService 启动同步时反射加载（`Class.forName`）；
- `misfire_policy` = `DO_NOTHING`：错过即跳过（与 PerfRunTaskCleanup / SysControlCleanup 一致）；
- `status` = `ACTIVE`：默认启用；
- `allow_manual_trigger` = `1`：保留运维手动触发能力；
- `remark`：审计与运维可读的迁移来源说明。

撤销脚本 `U1_8_0__remove_lead_callback_compensate_job.sql`：

```sql
-- V1.8 撤销脚本：删除 Lead 回调补偿 Quartz Job
-- 引入版本：V1.8（2026-05-01）
DELETE FROM SYS_JOB_CONF WHERE job_key = 'LEAD_CALLBACK_COMPENSATE';
```

> **注意**：governance `JobService.syncOnStartup` 在启动时只 upsert sys_job_conf 中的 job 到 Quartz Scheduler；不会反向清理 Quartz 中存在但 sys_job_conf 中已删除的 job。撤销时若需要彻底卸载已 schedule 的 job，需配合 `JobApi.unregisterJob('LEAD_CALLBACK_COMPENSATE')` 或重启实例。本 spec 假设回滚场景由运维操作，不在 V1.8 实现中加自动反向清理逻辑（YAGNI）。

### 4.3 旧入口删除

`LeadCallbackCompensationService.java` 改动：

**删除**：

```java
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Spring 调度入口 —— 默认每 5 分钟（{@code 300_000ms}）触发一次
 * {@link #scanAndCompensate()}。
 * <p>
 * 整体 try-catch 防止补偿异常冒泡到调度器导致后续触发被禁用 —— Spring
 * {@code @Scheduled} 默认会因连续异常停止调度该 bean 的方法。
 * </p>
 */
@Scheduled(fixedDelayString = "${customer.lead-compensation.interval-ms:300000}")
public void scheduledScan() {
    try {
        scanAndCompensate();
    } catch (Exception e) {
        log.error("[LeadCallbackCompensationService.scheduledScan] 补偿任务执行异常", e);
    }
}
```

**保留**：

- `scanAndCompensate()` 签名与逻辑零变化；
- `stuckThresholdMinutes` / `batchSize` `@Value` 注入零变化；
- `COMPENSATION_REJECT_REASON` / `PROCESS_STATUS_*` 常量零变化；
- 类级 javadoc 更新（移除"以 5 分钟为粒度（fixedDelay 可配）"措辞，改为"由 Quartz 调度（V1.8 起，job_key=LEAD_CALLBACK_COMPENSATE）"）。

更新后 javadoc 类描述（替换原第 27-29 行）：

```
 * <strong>本 Service 职责</strong>：由 Quartz job
 * {@code LeadCallbackCompensateQuartzJob}（V1.8 起，
 * job_key={@code LEAD_CALLBACK_COMPENSATE}，cron 默认 5 分钟）
 * 巡检 stuck IN_APPROVAL leads（超过 stuckThresholdMinutes 未推进），
```

### 4.4 配置项变更

| 配置项 | V1.7 | V1.8 | 说明 |
|---|---|---|---|
| `customer.lead-compensation.interval-ms` | `@Value` 默认 300_000 | **删除** | 频率改由 sys_job_conf.cron_expr 控制 |
| `customer.lead-compensation.stuck-threshold-minutes` | `@Value` 默认 10 | **保留**（业务参数） | 不变 |
| `customer.lead-compensation.batch-size` | `@Value` 默认 100 | **保留**（业务参数） | 不变 |

grep 验证：

```bash
grep -rn "customer\.lead-compensation\.interval-ms" \
     /home/djdev/leid/yiti --exclude-dir=target
# Expected: 0 matches after V1.8
```

### 4.5 模块归属调整

#### 4.5.1 `CustomerSchedulingConfig` 删除

V1.7 现状：customer 模块持有 `@EnableScheduling` 配置，但实际服务于：

- ✅ customer 自身的 `LeadCallbackCompensationService.scheduledScan()`（V1.8 删除）
- ✅ performance 的 `MetricSchedulerHealthCheck.healthCheck()`（V1.7 仍需要）

V1.8 删除 customer 的 `@Scheduled` 入口后，`CustomerSchedulingConfig` 名实不副——**customer 模块不再需要 Spring 调度**，但仍要让 performance HealthCheck 工作。

决策：**整类删除 `CustomerSchedulingConfig`**，归属迁回 performance 模块。

#### 4.5.2 `PerformanceSchedulingConfig` 新建

`performance-engine-center/src/main/java/com/bank/branch/platform/performance/config/PerformanceSchedulingConfig.java`

```java
package com.bank.branch.platform.performance.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * performance-engine-center 调度开关。
 * <p>
 * 通过 {@link EnableScheduling} 在 Spring 上下文中开启 Spring TaskScheduler。
 * V1.8（2026-05-01）由 customer 模块的 {@code CustomerSchedulingConfig}
 * 迁移而来——V1.8 后 customer 模块零 {@code @Scheduled}，故归属正确化。
 * </p>
 * <p>
 * <strong>当前服务于</strong>：{@link com.bank.branch.platform.performance.service.MetricSchedulerHealthCheck}
 * 每 10 分钟兜底巡检（V1.7 spec § 7 论证：纯本地兜底，不交给 Quartz 自调度）。
 * </p>
 * <p>
 * 仅一个空类，不需要 {@code @Bean} 方法 —— 单纯通过 {@code @EnableScheduling}
 * 注解触发 Spring 注册 {@code ScheduledAnnotationBeanPostProcessor}。
 * </p>
 */
@Configuration
@EnableScheduling
public class PerformanceSchedulingConfig {
}
```

> **风险确认**：Spring `@EnableScheduling` 是 application-context 级开关，加在 performance 任意 `@Configuration` 上均会全局生效——也就是说仍会扫描 `MetricSchedulerHealthCheck`（performance 自有）以及任何**未来**新增的 `@Scheduled` bean。这与 V1.7 在 customer 模块持有该注解时的行为完全一致，不引入新风险。

---

## 5. 数据库迁移

### 5.1 V1_8_0 脚本

文件：`customer-marketing-center/src/main/resources/sql/V1_8_0__register_lead_callback_compensate_job.sql`

内容见 § 4.2。

### 5.2 U1_8_0 撤销脚本

文件：`customer-marketing-center/src/main/resources/sql/U1_8_0__remove_lead_callback_compensate_job.sql`

内容见 § 4.2 末尾。

### 5.3 测试 seed 同步

`bootstrap/src/test/resources/data.sql` 的 `sys_job_conf` INSERT 块加一行（保持与生产种子一致）：

```sql
INSERT IGNORE INTO sys_job_conf (id, job_key, job_name, cron_expr, status, allow_manual_trigger,
                                 quartz_job_class, misfire_policy)
VALUES
    -- ...既有行...
    ('JOB_LEAD_CALLBACK_COMPENSATE', 'LEAD_CALLBACK_COMPENSATE', 'Lead 回调补偿巡检',
     '0 */5 * * * ?', 'ACTIVE', 1,
     'com.bank.branch.platform.customer.job.quartz.LeadCallbackCompensateQuartzJob', 'DO_NOTHING');
```

> **注**：使用 `INSERT IGNORE`（已是 V1.6 既有惯例），避免 IT 启动时与 governance `JobService` 启动同步路径产生 PK 冲突。

### 5.4 执行顺序

生产环境部署：

1. **代码先发布**：包含新 Quartz Job 类和删除的 `@Scheduled`；
2. **数据库后同步**：执行 V1_8_0 SQL 一次（运维手工）；
3. **应用重启**：governance JobService `syncOnStartup` 自动把新 job 注册到 Quartz Scheduler；
4. **验证**：观察 `QRTZ_TRIGGERS` 含 `LEAD_CALLBACK_COMPENSATE` 行；下个 5min 整点观察 `JobExecutionLogger` 日志。

回滚顺序：

1. 执行 U1_8_0（DELETE sys_job_conf row）；
2. （可选）调用 `JobApi.unregisterJob('LEAD_CALLBACK_COMPENSATE')` 清理 Quartz Scheduler；
3. `git revert` 应用代码（恢复 `@Scheduled` + `CustomerSchedulingConfig`）；
4. 重新部署。

---

## 6. 测试策略

遵循 TDD 红绿重构。

### 6.1 单元测试

#### 6.1.1 `LeadCallbackCompensateQuartzJobTest`

文件：`customer-marketing-center/src/test/java/com/bank/branch/platform/customer/job/quartz/LeadCallbackCompensateQuartzJobTest.java`

测试用例：

```
@Test execute_callsScanAndCompensate_normalPath()
    setup: mock LeadCallbackCompensationService
    when:  job.execute(mock context)
    then:  verify service.scanAndCompensate() called exactly once

@Test execute_swallowsServiceException_doesNotThrow()
    setup: mock service.scanAndCompensate() throws RuntimeException
    when:  job.execute(mock context)
    then:  no exception propagates
           service.scanAndCompensate() invoked once
           (验证顶层 try-catch 兜底语义保留)

@Test execute_logsJobKeyAndElapsed()
    (可选：用 LogCaptor / Mockito argumentCaptor 验证 log 含 jobKey 和耗时；
     若过度 brittle 可降级为 sanity check)
```

#### 6.1.2 `NoCustomerScheduledArchTest`

文件：`customer-marketing-center/src/test/java/com/bank/branch/platform/customer/arch/NoCustomerScheduledArchTest.java`

```java
@AnalyzeClasses(packages = "com.bank.branch.platform.customer",
        importOptions = ImportOption.DoNotIncludeTests.class)
class NoCustomerScheduledArchTest {

    @ArchTest
    static final ArchRule no_spring_scheduled =
            noMethods()
                .should()
                .beAnnotatedWith("org.springframework.scheduling.annotation.Scheduled")
                .as("customer 模块禁止 @Scheduled —— V1.8 起业务调度全部走 Quartz")
                .allowEmptyShould(true);
}
```

> 与 V1.7 `NoOldDailyKpiCalcArchTest` 同 pattern：`allowEmptyShould(true)` 处理无匹配方法时的 vacuously true 语义。

### 6.2 集成测试

#### 6.2.1 既有 `LeadCallbackCompensationIT` 不动

该 IT 注入 `LeadCallbackCompensationService` 直接调 `scanAndCompensate()`，与调度方式脱耦——V1.8 后**测试代码零改动**，行为零变化。

#### 6.2.2 新增 `LeadCallbackJobRegisteredIT`

文件：`bootstrap/src/test/java/com/bank/branch/platform/it/LeadCallbackJobRegisteredIT.java`

验证启动后 sys_job_conf + Quartz Scheduler 都已注册本 Job。

```java
@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/data.sql")
class LeadCallbackJobRegisteredIT {

    @Autowired private SysJobConfMapper sysJobConfMapper;  // governance 内部 mapper
    @Autowired private Scheduler scheduler;                // Quartz Scheduler bean

    @Test
    @DisplayName("V1.8 启动后 sys_job_conf 含 LEAD_CALLBACK_COMPENSATE 行")
    void sysJobConfHasLeadCallbackRow() {
        SysJobConf row = sysJobConfMapper.selectByJobKey("LEAD_CALLBACK_COMPENSATE");
        assertThat(row).isNotNull();
        assertThat(row.getQuartzJobClass())
            .isEqualTo("com.bank.branch.platform.customer.job.quartz.LeadCallbackCompensateQuartzJob");
        assertThat(row.getCronExpr()).isEqualTo("0 */5 * * * ?");
        assertThat(row.getMisfirePolicy()).isEqualTo("DO_NOTHING");
        assertThat(row.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("V1.8 启动后 Quartz Scheduler 含 LEAD_CALLBACK_COMPENSATE Job")
    void quartzSchedulerHasJob() throws SchedulerException {
        // governance JobService.syncOnStartup 在 ApplicationReadyEvent 时已 schedule
        JobKey jobKey = JobKey.jobKey("LEAD_CALLBACK_COMPENSATE",
                                      /* group */ "DEFAULT");  // 与 JobService.resolveGroup 保持一致
        assertThat(scheduler.checkExists(jobKey))
            .as("LEAD_CALLBACK_COMPENSATE 应已被 schedule 到 Quartz")
            .isTrue();
    }
}
```

> **JobKey group 验证**：跟 V1.7 V1_7_0FlywayIT pattern 一致，从 `JobService.resolveGroup(jobKey)` 拿真实 group 字符串。如果 `resolveGroup` 是 private，从 `JobApi.getJobConf(...)` 获取 group 也行；具体在 plan 阶段细化。

### 6.3 手动验证

部署 V1.8 后第一周执行：

- [ ] 数据库 `SELECT * FROM SYS_JOB_CONF WHERE job_key = 'LEAD_CALLBACK_COMPENSATE'` 返回 1 行；
- [ ] 数据库 `SELECT * FROM QRTZ_JOB_DETAILS WHERE job_name = 'LEAD_CALLBACK_COMPENSATE'` 返回 1 行；
- [ ] 数据库 `SELECT * FROM QRTZ_TRIGGERS WHERE trigger_name = 'LEAD_CALLBACK_COMPENSATE'` 返回 1 行，cron='0 */5 * * * ?'；
- [ ] 应用日志在整 5min（00/05/10/...）观察 `[LeadCallbackCompensateQuartzJob] LEAD_CALLBACK_COMPENSATE 完成，耗时 Xms`；
- [ ] `JobExecutionLogger`（governance V1.6 全局监听器）写日志记录每次 fire。

---

## 7. 风险与缓解

| # | 风险 | 等级 | 缓解措施 |
|---|---|---|---|
| 1 | `Class.forName(quartz_job_class)` 失败：sys_job_conf 引用了不存在的 customer Job 类 | 中 | governance V1.7 已加 `Class.forName` 异常捕获（catch `ClassNotFoundException \| LinkageError`），失败时记 warn 日志、跳过该 job 而不阻断启动 |
| 2 | 部署不同步：DB 已插 V1_8_0 但应用尚未发布新 Job 类 | 中 | 见风险 1：governance 启动同步路径已容错；旧实例不会因新 row 启动失败 |
| 3 | 部署不同步：应用已发布 V1.8（移除 `@Scheduled`）但 DB 尚未执行 V1_8_0 | 中 | 表现：补偿任务**短暂停跑**（旧入口已删，新 job 未注册）；补偿任务非高频/非关键路径，最长 5min 间隔可容忍。运维 SOP 必须确保发布顺序：代码先发 → SQL 紧跟 |
| 4 | 多实例并发：两个实例都跑 LeadCallback | 低 | Quartz `QRTZ_LOCKS` 行锁防重（V1.6 已就绪），与 `PerfRunTaskCleanup` 等同语义 |
| 5 | misfire 风险：实例宕机导致 5min 触发被错过 | 低 | `DO_NOTHING` 策略——错过即跳过，下个 5min 继续。补偿任务自身设计就是周期巡检，错过一次最多多 5min stuck，业务可接受 |
| 6 | `CustomerSchedulingConfig` 删除影响其他模块？ | 低 | grep 已确认仓库内**仅 2 处** `@Scheduled` 生产用法（customer / performance），且 performance 改用新建的 `PerformanceSchedulingConfig`。其他模块零 `@Scheduled`，删除无影响 |
| 7 | ArchUnit 守护误伤未来合理需求 | 低 | 守护范围限定 `customer` 模块；future-proof 场景应当评估是否真的合理，再考虑是否在 spec 中改回（拒绝静默回退） |

---

## 8. 验收标准

V1.8 交付完成的硬指标：

- ✅ `customer-marketing-center/src/main/java` 下 `@Scheduled` 注解出现次数 = 0；
- ✅ `customer-marketing-center` 含 `LeadCallbackCompensateQuartzJob` 类，extends/implements Quartz Job；
- ✅ `customer-marketing-center` 含 V1_8_0 / U1_8_0 SQL 脚本各 1 个；
- ✅ `customer-marketing-center` `CustomerSchedulingConfig.java` 已删除；
- ✅ `performance-engine-center` 含 `PerformanceSchedulingConfig.java` 1 个；
- ✅ `bootstrap/src/test/resources/data.sql` `sys_job_conf` 含 `LEAD_CALLBACK_COMPENSATE` 行；
- ✅ surefire（`mvn test`）全绿；
- ✅ failsafe（`mvn verify`）全绿，含新增 `LeadCallbackJobRegisteredIT`；
- ✅ ArchUnit `NoCustomerScheduledArchTest` 通过；
- ✅ 仓库内 grep `customer.lead-compensation.interval-ms` = 0 处；
- ✅ root `CLAUDE.md` + customer/performance 模块 `CLAUDE.md` 标注 V1.8 已交付。

---

## 9. 时间线

按 plan 阶段拆分（具体见 plan 文档）：

| Phase | 内容 | 预估 |
|---|---|---|
| P0 | 写 plan + 起 worktree（如用） | 0.5h |
| P1 | DDL/SQL 脚本（V1_8_0 + U1_8_0 + bootstrap data.sql） | 0.5h |
| P2 | `LeadCallbackCompensateQuartzJob` 类 + 单测（TDD red-green） | 1h |
| P3 | 修改 `LeadCallbackCompensationService`（删 `@Scheduled` + 删 import + 更新 javadoc） | 0.3h |
| P4 | 删除 `CustomerSchedulingConfig` + 新建 `PerformanceSchedulingConfig` | 0.3h |
| P5 | `LeadCallbackJobRegisteredIT` + ArchUnit 守护 | 1h |
| P6 | 跑全量 surefire + failsafe，修复任何回归 | 1h |
| P7 | 更新 root + 模块 CLAUDE.md | 0.3h |
| P8 | 最终代码评审 + commit + push | 0.5h |

**预估总时长**：5h（一个工作日下午即可完成）。

---

## 10. 兼容性 / Breaking Change 声明

### 10.1 配置兼容

`customer.lead-compensation.interval-ms` 删除：

- 任何在 `application.yml` / `application-prod.yml` 显式设置该 key 的环境，删除即可（无副作用）；
- Spring `@Value("${customer.lead-compensation.interval-ms:300000}")` 行整体删除，不留 deprecated stub。

> 当前 grep 已确认：仓库内仅 2 处出现该 key（service `@Value` 默认值 + service javadoc 提及），无显式 yaml 配置。

### 10.2 行为兼容

| 维度 | V1.7 | V1.8 | 是否 breaking |
|---|---|---|---|
| 调度频率 | fixedDelay 5min（首次启动后 5min 起跑） | cron `0 */5 * * * ?`（整 5min 触发） | **轻微变化**：触发时刻从"启动时刻 + n*5min"变为"整 5min" |
| 业务行为 | scanAndCompensate() | scanAndCompensate() | 零变化 |
| 多实例语义 | 各实例独立跑 | QRTZ 行锁防重，单实例跑 | **改善**：从重复跑变为单跑 |
| stuck 阈值 | 10min（未变） | 10min（未变） | 零变化 |
| batchSize | 100（未变） | 100（未变） | 零变化 |

**结论**：业务无 breaking；仅多实例并发语义改善 + 触发时刻轻微对齐到整 5min。

### 10.3 测试兼容

`LeadCallbackCompensationIT` 注入 service 直接调 `scanAndCompensate()`，与调度无关，**零改动**。

---

## 11. 自查（Spec Review）

- [x] **Placeholder 扫描**：无 TBD/TODO/"待补充"；
- [x] **内部一致性**：`scanAndCompensate()` 签名贯通（spec § 4.1 / § 4.3 / § 6.2.1 一致）；job_key/id/class 全文统一为 `LEAD_CALLBACK_COMPENSATE` / `JOB_LEAD_CALLBACK_COMPENSATE` / `LeadCallbackCompensateQuartzJob`；
- [x] **范围检查**：纯入口迁移，零业务逻辑改动，单 plan 可覆盖；
- [x] **歧义检查**：所有"是否保留"决策均明确（保留 = scanAndCompensate / 业务参数 / IT；删除 = `@Scheduled` / `CustomerSchedulingConfig` / interval-ms 配置）；
- [x] **Spec 与 V1.7 节奏对齐**：DDL/U/V 脚本 + ArchUnit 守护 + IT 验启动注册——V1.7 已建立 pattern，V1.8 直接复用；
- [x] **风险表与缓解措施 1:1 对齐**：风险表每条都有对应缓解策略，无悬空风险。

---

## 12. 后续工作（V1.9+）

V1.8 落地后建议跟进的小事项（非 V1.8 本期范围）：

1. **HealthCheck 统一治理**：`MetricSchedulerHealthCheck` 是否仍需独立 `@EnableScheduling`？或可考虑改为 Quartz `SimpleTrigger`（fixedDelay 等价语义）+ 加上 `@DisallowConcurrentExecution` 注解+本地 Lock 来实现兜底巡检的"本节点独立跑"语义。如可实现，整个仓库可去掉最后一个 `@Scheduled`，`PerformanceSchedulingConfig` 也可删除——彻底 100% 调度统一。决策延后到 V1.9 评估；
2. **运维 Runbook**：在 governance/CLAUDE.md 写一段"sys_job_conf 维护手册"（如何禁用某 Job、如何调整 cron、Quartz Scheduler 中残留 trigger 的清理）；
3. **回滚自动化**：当前 U1_8_0 只 DELETE sys_job_conf 行，对 Quartz JDBC JobStore 中的 trigger 不做反向清理。可考虑在 governance `JobService` 加一个 `gcDanglingTriggers()` 启动钩子，删 sys_job_conf 中已不存在但 Quartz 仍在 schedule 的 job——非紧迫，待 V1.9。

---

## 13. 附录

### 13.1 引用

- V1.6 设计：`docs/superpowers/specs/2026-04-25-quartz-integration-design.md`
- V1.7 设计：`docs/superpowers/specs/2026-04-30-performance-metric-quartz-schedule-design.md`
- 既有补偿 IT：`bootstrap/src/test/java/com/bank/branch/platform/it/LeadCallbackCompensationIT.java`
- governance JobApi：`system-governance-center/src/main/java/com/bank/branch/platform/governance/api/JobApi.java`

### 13.2 git baseline

- 起点 commit：`7911a9e`（master，2026-05-01 09:47 PreCompact 检查点）
- 目标分支：`master`（直接 commit/push，无 feature 分支——改动小，按 brainstorming 阶段确认）
