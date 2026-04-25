# Quartz 整合 sys_job_conf — 设计规约（Spec）

**版本**: v1.0
**创建日期**: 2026-04-25
**作者**: Claude (Opus 4.7) + 用户协同 brainstorming
**子项目编号**: B（3 子项目 refactor 中的第 2 个）
**Worktree**: `D:\Project\oneplate\.claude\worktrees\refactor-quartz-job`
**Branch**: `refactor/quartz-job-integration`（基于 master `af66ccd` 分叉）
**前置子项目**: A（Excel→EasyExcel）已完成（merged: `subproject/A-excel-DONE` 已 push）
**后续子项目**: C（MyBatis-Plus 引入），3 子项目全部完成后统一合并 master

---

## 0. 背景与目标

### 0.1 现状

`performance-engine-center` 模块下存在 3 个基于 Spring `@Scheduled` 的定时任务：

| Job | 文件 | Cron 默认 | 业务逻辑 |
|---|---|---|---|
| `DailyKpiCalcJob` | `performance/job/DailyKpiCalcJob.java` | `0 30 1 * * ?` | 遍历 ACTIVE 方案 → 调 KpiCalcService.calcScheme（T-1） |
| `SysControlCleanupJob` | `performance/job/SysControlCleanupJob.java` | `0 0 3 * * ?` | 按 scope_dim 分组保留最新 12 条历史 |
| `PerfRunTaskCleanupJob` | `performance/job/PerfRunTaskCleanupJob.java` | `0 30 3 * * ?` | 删 SUCCESS 状态 + end_time < now-90d 的任务 |

**关键事实（已通过深度探索确认）**：
- 三个 Job 均通过 `${prefix.cron:default}` EL 表达式读 cron，可在 application.yml 覆盖
- 三个 Job 均带 `@ConditionalOnProperty(matchIfMissing=false)` 双保险（生产配置下默认不启用）
- 全库无 `@EnableScheduling` 注解 → **3 个 Job 现状从未真正运行过**
- 三个 Job 的 `run()` 方法**从未调用 JobApi**
- 三个 Job 与 `sys_job_conf.job_key` **无关联**（job_key 表数据可能为空）
- ShedLock 5.13.0 + Redis 仅在 3 个 Job 上声明 `@SchedulerLock`，**无其他用途**

`system-governance-center` 模块下已存在：
- 表 `sys_job_conf`（DDL 见 `docs/schema/ddl-governance.sql:71-107`）：作业元数据 + 任务目录
- 表 `sys_job_run_log`：任务执行记录
- `JobApi` 接口（4 方法：getJobConf / startJobRun / completeJobRun / failJobRun）— 探索确认 3 个写日志方法**全库无调用方**
- `JobController` 5 端点（list/logs/trigger/pause/resume）— **trigger/pause/resume 当前是装饰品**（无后端逻辑联动调度器）

### 0.2 问题

1. **多节点部署下 @Scheduled 无防重保证**：当前依赖 ShedLock + Redis 单点协调，与"无外部调度强一致"原则不符
2. **业务表与调度器割裂**：`sys_job_conf` 是配置展示用，但 cron 实际从 `application.yml` 读取，业务表只读不写
3. **手动触发无实现**：`POST /api/admin/sys/jobs/{jobId}/trigger` 端点存在但内部为空
4. **缺乏成熟调度框架特性**：无 misfire 处理、无集群心跳、无运行时调度状态可观测性

### 0.3 目标

引入 **嵌入式 Quartz 2.3.2 集群模式**作为统一调度引擎，实现：
- ✅ 多节点防重（QRTZ_LOCKS 表行锁，无需外部协调服务）
- ✅ `sys_job_conf` 成为单一配置源（cron 从表读，application.yml 不再覆盖）
- ✅ 5 个 JobController 端点真正可用（list/logs/trigger/pause/resume）
- ✅ misfire 分级处理（KPI 计算补跑 / 清理任务跳过）
- ✅ 删除 ShedLock 全部依赖（防重能力不退化）
- ✅ 所有改造代码 100% TDD 红-绿-重构

### 0.4 非目标

- ❌ Job 的 Web 创建 UI（创建 Job = 部署新 Quartz Job 类，需开发参与）
- ❌ Job 业务逻辑变更（3 个 Job 的 `run()` 方法体不动）
- ❌ Quartz 独立部署 / 独立服务（保持嵌入式与 bootstrap 同 JVM）
- ❌ 业务侧手写重试/退避逻辑
- ❌ 业务事件总线 / 跨 Job 触发链
- ❌ Job 历史性能监控告警（沿用现有 `audit_log` + 慢查询机制）

---

## 1. 决策日志（7 项 brainstorming 决策）

| # | 问题 | 决策 | 决策理由摘要 |
|---|---|---|---|
| 1 | sys_job_conf 与 QRTZ_* 数据关系 | **B（双写）** | sys_job_conf 保留作业务元数据 + 任务目录；JobConfService 单一写入口；sys_job_run_log 由 JobListener 自动写入 |
| 2 | ShedLock 处置 | **A（删除）** | 移除依赖 + ShedLockConfig + 3 个 @SchedulerLock；由 Quartz 集群（isClustered=true + JDBC JobStore）单一防重 |
| 3 | QRTZ_* 表归属 schema | **A（同库 onepl）** | 单实例单库架构，引入双库违背"简单优于复杂"；QRTZ_ 前缀严格隔离；事务可跨表 |
| 4 | Job 本体改造方式 | **B（解耦式包装）** | 3 业务 Job 类保留为纯业务方法持有者 + 新建 3 个 Quartz Job 包装类 implements `org.quartz.Job`；业务方法独立可测；triggerNow 可绕过 Quartz |
| 5 | JobApi 表面积 | **B（精简到 1 方法）** | 仅保留 `getJobConf(jobKey)`；删除 startJobRun/completeJobRun/failJobRun（事实无人调，由 JobListener 内部调用 SysJobRunLogService） |
| 6 | 失败重试策略 | **A（业务异常下次调度）+ misfire 分级** | 业务异常不重试等下次 cron；misfire：DailyKpiCalcJob=`FIRE_ONCE_NOW`，2 个清理 Job=`DO_NOTHING`；不引入业务侧手写重试 |
| 7 | 测试策略 | **A（全 TDD 重写）** | 3 个 Job 现状从未运行过，无"旧行为"概念；与子项目 A 节奏一致；CLAUDE.md 红线要求 TDD |

---

## 2. 架构总览（设计 §1）

### 2.1 部署形态

**单进程嵌入式 Quartz**（与 bootstrap 同 JVM），无独立调度服务

### 2.2 集群模式（即使当前单实例也启用，未来横向扩展零改动）

```properties
org.quartz.jobStore.isClustered=true
org.quartz.jobStore.class=org.quartz.impl.jdbcjobstore.JobStoreTX
org.quartz.jobStore.driverDelegateClass=org.quartz.impl.jdbcjobstore.StdJDBCDelegate
org.quartz.jobStore.tablePrefix=QRTZ_
org.quartz.jobStore.clusterCheckinInterval=20000
org.quartz.jobStore.misfireThreshold=60000
```

**多节点防重**通过 QRTZ_LOCKS 表的悲观锁（行锁 SELECT ... FOR UPDATE），不依赖外部协调服务。

### 2.3 模块归属

| 组件 | 归属模块 | 理由 |
|------|---------|------|
| Quartz 调度引擎本身（QuartzConfig、SpringBeanJobFactory、SchedulerFactoryBean、JobListener） | **system-governance-center** | 与 sys_job_conf/JobApi 同模块，单一治理边界 |
| 3 业务 Job 类（DailyKpiCalcJob 等持有 run() 方法） | 维持原位 **performance-engine-center** | 业务归属不变 |
| 3 Quartz 包装 Job 类（implements `org.quartz.Job`） | 也放 **performance-engine-center** | 与业务 Job 紧耦合，不暴露给其他模块 |

### 2.4 数据源策略

Quartz 使用 **同一个 Druid DataSource**（`@QuartzDataSource` 注解共享主数据源）；
决策 #3=A 的直接体现：单数据源支持"业务表 + QRTZ_* 表"事务联动。

### 2.5 模块拓扑变化

```
performance-engine-center
  ├── job/
  │   ├── DailyKpiCalcJob.java          (业务方法持有者，删 @Scheduled/@SchedulerLock)
  │   ├── SysControlCleanupJob.java
  │   ├── PerfRunTaskCleanupJob.java
  │   └── quartz/                        (新增子包)
  │       ├── DailyKpiCalcQuartzJob.java (implements Job, execute() 调 run())
  │       ├── SysControlCleanupQuartzJob.java
  │       └── PerfRunTaskCleanupQuartzJob.java

system-governance-center
  ├── config/
  │   └── QuartzConfig.java              (新增：SchedulerFactoryBean + SpringBeanJobFactory + JobListener 注册)
  ├── listener/
  │   └── JobExecutionLogger.java        (新增：implements JobListener，写 sys_job_run_log)
  ├── service/
  │   └── JobConfService.java            (重构：CRUD + scheduleJob/rescheduleJob/unscheduleJob)
  └── api/
      └── JobApi.java                    (精简：删 startJobRun/completeJobRun/failJobRun)
```

---

## 3. 数据库变更（设计 §2）

### 3.1 新增 11 张 QRTZ_* 表（Quartz 2.3.2 官方 MySQL DDL）

| 表名 | 用途 |
|------|------|
| `QRTZ_JOB_DETAILS` | Job 元信息（job class、is_durable、job_data） |
| `QRTZ_TRIGGERS` | Trigger 主表（next_fire_time、prev_fire_time、trigger_state） |
| `QRTZ_CRON_TRIGGERS` | Cron 触发器扩展（cron_expression、time_zone_id） |
| `QRTZ_SIMPLE_TRIGGERS` | 简单触发器（repeat_count、repeat_interval）— 本项目暂不用 |
| `QRTZ_SIMPROP_TRIGGERS` | 自定义属性触发器 — 本项目暂不用 |
| `QRTZ_BLOB_TRIGGERS` | Blob 触发器 — 本项目暂不用 |
| `QRTZ_CALENDARS` | 日历（业务日历调度） |
| `QRTZ_FIRED_TRIGGERS` | 当前正在执行的触发器 |
| `QRTZ_PAUSED_TRIGGER_GRPS` | 暂停的触发器组 |
| `QRTZ_SCHEDULER_STATE` | 调度器实例心跳（集群感知） |
| `QRTZ_LOCKS` | 集群锁（TRIGGER_ACCESS / STATE_ACCESS） |

**DDL 来源**：Quartz 2.3.2 官方包 `org/quartz/impl/jdbcjobstore/tables_mysql_innodb.sql`（验证后归档）
**归档位置**：`docs/schema/ddl-quartz.sql`（独立文件，不混入业务 ddl）

### 3.2 sys_job_conf 表字段扩展

| 新字段 | 类型 | 用途 |
|--------|------|------|
| `quartz_job_class` | varchar(255) | Quartz 包装 Job 类全限定名（如 `com.bank.branch.platform.performance.job.quartz.DailyKpiCalcQuartzJob`） |
| `misfire_policy` | varchar(32) | `FIRE_ONCE_NOW` / `DO_NOTHING` / `IGNORE_MISFIRE_POLICY`（默认 `FIRE_ONCE_NOW`） |

**已有字段说明**：
- `job_key` 字段已存在（UNIQUE 约束）→ 直接作为 Quartz `JobKey.name`，`group` 用固定值 `DEFAULT`
- `cron_expr` 字段已存在 → 直接作为 CronTrigger 的 cron 表达式
- `status`(ACTIVE/PAUSED) 已存在 → 与 Quartz 的 `pauseTrigger`/`resumeTrigger` 联动
- `last_run_time` / `next_run_time` 已存在 → 由 JobListener 自动更新

### 3.3 sys_job_run_log 表字段扩展

| 新字段 | 类型 | 用途 |
|--------|------|------|
| `scheduled_fire_time` | datetime(3) | Quartz 计划触发时间（区分实际执行时间 start_time 与计划时间，便于 misfire 排查） |

### 3.4 初始化 3 条业务记录

```sql
INSERT INTO sys_job_conf (job_key, job_name, cron_expr, quartz_job_class, misfire_policy, status, ...) VALUES
('DAILY_KPI_CALC',         '日常 KPI 计算',     '0 30 1 * * ?', 'com.bank.branch.platform.performance.job.quartz.DailyKpiCalcQuartzJob',     'FIRE_ONCE_NOW', 'ACTIVE', ...),
('SYS_CONTROL_CLEANUP',    '系统控制历史清理', '0 0 3 * * ?',  'com.bank.branch.platform.performance.job.quartz.SysControlCleanupQuartzJob',  'DO_NOTHING',    'ACTIVE', ...),
('PERF_RUN_TASK_CLEANUP',  '绩效执行任务清理', '0 30 3 * * ?', 'com.bank.branch.platform.performance.job.quartz.PerfRunTaskCleanupQuartzJob', 'DO_NOTHING',    'ACTIVE', ...);
```

### 3.5 文档同步

`docs/schema/ddl-governance.sql` 在原 sys_job_conf/sys_job_run_log DDL 同位置添加 ALTER 注释（标注 V1.6 quartz 引入）。

---

## 4. 核心组件（设计 §3）

### 4.1 QuartzConfig（system-governance-center/config/）

```java
@Configuration
@ConditionalOnProperty(prefix="quartz", name="enabled", havingValue="true", matchIfMissing=true)
public class QuartzConfig {

    @Bean
    public SpringBeanJobFactory springBeanJobFactory(ApplicationContext ctx) {
        // 让 Quartz Job 内部可 @Autowired Spring Bean
        AutowiringSpringBeanJobFactory factory = new AutowiringSpringBeanJobFactory();
        factory.setApplicationContext(ctx);
        return factory;
    }

    @Bean
    public SchedulerFactoryBean schedulerFactoryBean(
            DataSource dataSource,                    // 共享主数据源
            SpringBeanJobFactory jobFactory,
            JobExecutionLogger jobListener,
            QuartzProperties quartzProperties         // 来自 application.yml
    ) {
        SchedulerFactoryBean bean = new SchedulerFactoryBean();
        bean.setDataSource(dataSource);
        bean.setJobFactory(jobFactory);
        bean.setOverwriteExistingJobs(true);
        bean.setAutoStartup(true);
        bean.setStartupDelay(10);                     // 应用启动 10s 后再启动调度
        bean.setWaitForJobsToCompleteOnShutdown(true);
        bean.setQuartzProperties(quartzProperties.toProperties());
        bean.setGlobalJobListeners(jobListener);
        return bean;
    }
}
```

**关键点**：
- `AutowiringSpringBeanJobFactory` 是自定义类（继承 SpringBeanJobFactory + 实现 ApplicationContextAware），让 Quartz Job 可注入 Spring Bean
- `setOverwriteExistingJobs(true)`：启动时若 sys_job_conf 修改了 cron，自动覆盖 QRTZ_TRIGGERS

### 4.2 quartz.properties / application.yml

```yaml
spring:
  quartz:
    enabled: true
    job-store-type: jdbc
    jdbc:
      initialize-schema: never              # DDL 由 docs/schema/ddl-quartz.sql 手动初始化
    properties:
      org.quartz.scheduler.instanceName: BranchPlatformScheduler
      org.quartz.scheduler.instanceId: AUTO            # 自动生成节点 ID
      org.quartz.threadPool.threadCount: 5
      org.quartz.threadPool.threadPriority: 5
      org.quartz.jobStore.class: org.quartz.impl.jdbcjobstore.JobStoreTX
      org.quartz.jobStore.driverDelegateClass: org.quartz.impl.jdbcjobstore.StdJDBCDelegate
      org.quartz.jobStore.tablePrefix: QRTZ_
      org.quartz.jobStore.isClustered: true
      org.quartz.jobStore.clusterCheckinInterval: 20000
      org.quartz.jobStore.misfireThreshold: 60000     # 60s 阈值
```

### 4.3 业务 Job（保留原位，最小改动）

**3 个业务 Job 类的真实结构（重构后保留以下，仅删除 `@Scheduled`/`@SchedulerLock`/`scheduled()` 包装方法 + 其上的 `@ConditionalOnProperty`）**：

`performance-engine-center/job/DailyKpiCalcJob.java`：
```java
@Component
@RequiredArgsConstructor
@Slf4j
// 删除：@ConditionalOnProperty(prefix = "perf.job.daily-kpi", ...)
public class DailyKpiCalcJob {
    private static final DateTimeFormatter VERSION_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private final KpiSchemeService kpiSchemeService;     // 真实注入
    private final KpiCalcService kpiCalcService;

    // 删除：@Scheduled(cron="${perf.job.daily-kpi.cron:0 30 1 * * ?}")
    // 删除：@SchedulerLock(name="DailyKpiCalcJob", lockAtMostFor="PT30M", lockAtLeastFor="PT5M")
    // 删除：public void scheduled() { run(); }

    public void run() {                                  // 保留：返回 void，业务逻辑不变
        List<PerfKpiScheme> schemes = kpiSchemeService.listActiveSchemes();
        // ... T-1 cycleDate / asOfDate 推导 + for 循环调 calcScheme（异常单方案 catch warn）
    }
}
```

`performance-engine-center/job/SysControlCleanupJob.java`：
```java
@Component
@RequiredArgsConstructor
@Slf4j
// 删除：@ConditionalOnProperty(prefix = "perf.job.sys-control-cleanup", ...)
public class SysControlCleanupJob {
    private final SysControlMapper sysControlMapper;
    @Value("${perf.job.sys-control-cleanup.keep-count:12}")
    private int keepCount;                                // 保留：可继续从 application.yml 配置

    // 删除：@Scheduled / @SchedulerLock / scheduled()

    public int run() {                                    // 保留：返回 int（删除行数总和），业务逻辑不变
        // ... selectScopeDims → 每个 scope 查 oldVersionIds → deleteByIds → 累加 totalDeleted
        return totalDeleted;
    }
}
```

`performance-engine-center/job/PerfRunTaskCleanupJob.java`：
```java
@Component
@RequiredArgsConstructor
@Slf4j
// 删除：@ConditionalOnProperty(prefix = "perf.job.run-task-cleanup", ...)
public class PerfRunTaskCleanupJob {
    private final PerfRunTaskMapper perfRunTaskMapper;
    @Value("${perf.job.run-task-cleanup.retention-days:90}")
    private int retentionDays;                            // 保留：可继续从 application.yml 配置

    // 删除：@Scheduled / @SchedulerLock / scheduled()

    public int run() {                                    // 保留：返回 int（删除行数），业务逻辑不变
        // ... cutoff = now - retentionDays → deleteSuccessTasksBefore(cutoff) → return deleted
        return deleted;
    }
}
```

**关键差异说明**：
- `DailyKpiCalcJob.run()` 返回 **void**
- `SysControlCleanupJob.run()` / `PerfRunTaskCleanupJob.run()` 返回 **int**（实际删除行数）
- Quartz 包装类调用 `run()` 时**忽略返回值**（int 返回值仅用于日志/单元测试断言，调度链路不需要）
- 删除 `@ConditionalOnProperty` 注解：现在通过 `sys_job_conf.status='ACTIVE'` 控制启停，application.yml 不再控制 Job Bean 是否注册

**保留项**：
- `@Component` 不删（业务 Bean 仍需 Spring 管理）
- `keepCount` / `retentionDays` 的 `@Value("${...}")` 不删（业务参数与调度无关）
- `run()` 内的异常隔离 + log warn 模式不删

### 4.4 Quartz 包装 Job（新增）

`performance-engine-center/job/quartz/DailyKpiCalcQuartzJob.java`：
```java
@Slf4j
public class DailyKpiCalcQuartzJob implements Job {  // 注意：不加 @Component
    // 注：以下 @Autowired 字段注入依赖 §4.1 的 AutowiringSpringBeanJobFactory
    //     （Quartz 通过反射 newInstance() 创建本对象 → JobFactory 调用
    //      applicationContext.getAutowireCapableBeanFactory().autowireBean(job) 完成注入）
    @Autowired private DailyKpiCalcJob dailyKpiCalcJob;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            dailyKpiCalcJob.run();   // void，无返回值
        } catch (Exception e) {
            log.error("DailyKpiCalcJob 执行异常", e);
            throw new JobExecutionException(e, false);   // false = 不立即重试
        }
    }
}
```

`SysControlCleanupQuartzJob.java` / `PerfRunTaskCleanupQuartzJob.java` 结构相同，但调用业务 `run()` 返回值会被**显式忽略**（无赋值即可），例如：
```java
@Override
public void execute(JobExecutionContext context) throws JobExecutionException {
    try {
        sysControlCleanupJob.run();   // 返回 int，但调度链路不消费 → 直接丢弃
    } catch (Exception e) {
        log.error("SysControlCleanupJob 执行异常", e);
        throw new JobExecutionException(e, false);
    }
}
```

**关键点**：不加 `@Component`！Quartz 通过反射 + SpringBeanJobFactory 实例化（每次执行 new 一个），@Autowired 由 Factory 注入（详见 §4.1）。

### 4.5 JobExecutionLogger（system-governance-center/listener/）

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class JobExecutionLogger implements JobListener {
    private final SysJobConfMapper jobConfMapper;
    private final SysJobRunLogMapper runLogMapper;

    @Override public String getName() { return "JobExecutionLogger"; }

    @Override
    public void jobToBeExecuted(JobExecutionContext context) {
        // 写 sys_job_run_log: status=RUNNING, start_time, scheduled_fire_time
        // jobKey → sys_job_conf.id → sys_job_run_log.job_id
        // triggerType: MANUAL (来自 JobDataMap "triggerType") 或 SCHEDULED
        // runLogId 写入 context.put("runLogId", id)
    }

    @Override
    public void jobWasExecuted(JobExecutionContext context, JobExecutionException jobException) {
        String runLogId = (String) context.get("runLogId");
        if (jobException == null) {
            runLogMapper.updateSuccess(runLogId, LocalDateTime.now());      // status=SUCCESS, end_time
        } else {
            String errorMsg = ExceptionUtils.getStackTrace(jobException);
            if (errorMsg.length() > 4000) errorMsg = errorMsg.substring(0, 4000);
            runLogMapper.updateFailed(runLogId, LocalDateTime.now(), errorMsg);  // status=FAILED, end_time, error_msg
        }
        // 同时更新 sys_job_conf.last_run_time
    }

    @Override public void jobExecutionVetoed(JobExecutionContext context) { /* no-op */ }
}
```

### 4.6 JobConfService（重构）

```java
@Service
@RequiredArgsConstructor
public class JobConfService {
    private final SysJobConfMapper jobConfMapper;
    private final Scheduler scheduler;       // 由 SchedulerFactoryBean 注入

    @Transactional
    public String createJob(JobConfCreateCmd cmd) {
        // 1. 写 sys_job_conf
        SysJobConf entity = ...;
        jobConfMapper.insert(entity);

        // 2. 注册到 Quartz
        JobDetail jobDetail = JobBuilder.newJob((Class<? extends Job>) Class.forName(cmd.getQuartzJobClass()))
            .withIdentity(cmd.getJobKey(), "DEFAULT")
            .storeDurably()
            .build();
        CronTrigger trigger = TriggerBuilder.newTrigger()
            .withIdentity(cmd.getJobKey() + "_TRIGGER", "DEFAULT")
            .withSchedule(applyMisfirePolicy(cronSchedule(cmd.getCronExpr()), cmd.getMisfirePolicy()))
            .forJob(jobDetail)
            .build();
        scheduler.scheduleJob(jobDetail, trigger);
        return entity.getId();
    }

    @Transactional
    public void updateJob(String id, JobConfUpdateCmd cmd) {
        // sys_job_conf 更新 + Scheduler.rescheduleJob
    }

    @Transactional
    public void pauseJob(String id) {
        // sys_job_conf.status=PAUSED + Scheduler.pauseJob
    }

    @Transactional
    public void resumeJob(String id) {
        // sys_job_conf.status=ACTIVE + Scheduler.resumeJob
    }

    public void triggerJobNow(String id, String empId) {
        // 不写 sys_job_conf，但通过 JobDataMap 传递 triggerType=MANUAL + operatorEmpId
        JobDataMap data = new JobDataMap();
        data.put("triggerType", "MANUAL");
        data.put("operatorEmpId", empId);
        scheduler.triggerJob(JobKey.jobKey(jobKey, "DEFAULT"), data);
    }

    @PostConstruct
    public void syncJobsOnStartup() {
        // 启动时遍历 sys_job_conf ACTIVE 记录，确保 Quartz 中存在对应 Trigger
        // (overwriteExistingJobs=true 自动覆盖 cron 变更)
    }
}
```

### 4.7 JobController（5 端点真正实现）

| 方法 | 路径 | 实现 |
|------|------|------|
| GET | `/api/admin/sys/jobs` | jobConfMapper.selectPage |
| GET | `/api/admin/sys/jobs/{jobId}/logs` | runLogMapper.selectPageByJobId |
| POST | `/api/admin/sys/jobs/{jobId}/trigger` | jobConfService.triggerJobNow(id, currentEmpId) |
| PUT | `/api/admin/sys/jobs/{jobId}/pause` | jobConfService.pauseJob(id) |
| PUT | `/api/admin/sys/jobs/{jobId}/resume` | jobConfService.resumeJob(id) |

### 4.8 JobApi（精简到 1 方法）

```java
public interface JobApi {
    Optional<JobConfDTO> getJobConf(String jobKey);
    // 删除 startJobRun / completeJobRun / failJobRun
}
```

---

## 5. 数据流（设计 §4）

### 5.1 应用启动流程

```
Spring Boot 启动
  ↓
DataSource 初始化（Druid 连接 onepl）
  ↓
QuartzConfig 加载
  ↓
SchedulerFactoryBean 启动（startupDelay=10s）
  ├─ 读取 quartz.properties
  ├─ 连接 onepl 库的 QRTZ_* 表
  ├─ 集群心跳注册（QRTZ_SCHEDULER_STATE 写入 instanceId）
  ├─ 触发器恢复（读 QRTZ_TRIGGERS 已有数据）
  └─ Scheduler.start()
  ↓
JobConfService.@PostConstruct.syncJobsOnStartup()
  ├─ 查 sys_job_conf WHERE status='ACTIVE'
  ├─ 对每条记录：
  │   ├─ JobBuilder + TriggerBuilder 构造
  │   └─ scheduler.scheduleJob(detail, trigger)（overwriteExistingJobs=true 覆盖 cron 变更）
  └─ 完成
```

**Spring 生命周期实际顺序说明**：
- `@PostConstruct` 在 Bean 初始化阶段执行（Spring `BeanPostProcessor` 完成依赖注入后立即调用），**早于** `SchedulerFactoryBean.start()`（后者在 LifecycleProcessor 阶段、所有 Bean 初始化完成后才执行）
- Quartz 的 `Scheduler.scheduleJob()` 在 Scheduler 未 start 时**也是合法的**（仅写入 QRTZ_* 表，trigger 不会触发；待 Scheduler.start() 后由触发器扫描激活）
- 因此 `syncJobsOnStartup()` 在 `Scheduler.start()` 之前执行**不影响功能正确性**，只是前者负责"配置同步到 QRTZ_* 表"，后者负责"开始触发"
- 上面流程图的"先 SchedulerFactoryBean 启动 → 后 syncJobsOnStartup"是按**逻辑成立时序**呈现，实际 Spring 容器执行顺序是相反的

### 5.2 调度执行流程（自动触发）

```
QRTZ_TRIGGERS.next_fire_time 到达
  ↓
Quartz 工作线程从线程池取出
  ↓
QRTZ_LOCKS 行锁竞争（多节点防重）
  ↓
赢得锁的节点：
  ├─ JobListener.jobToBeExecuted(context)
  │   ├─ 解析 JobKey → 查 sys_job_conf 取 id
  │   ├─ INSERT sys_job_run_log (job_id, trigger_type='SCHEDULED', status='RUNNING', start_time, scheduled_fire_time)
  │   ├─ 写 runLogId 到 context
  │   └─ 完成
  ├─ SpringBeanJobFactory.newJob() 实例化包装类
  │   └─ @Autowired 注入 DailyKpiCalcJob
  ├─ DailyKpiCalcQuartzJob.execute()
  │   └─ dailyKpiCalcJob.run()  → 业务逻辑
  ├─ JobListener.jobWasExecuted(context, exception)
  │   ├─ 从 context 取 runLogId
  │   ├─ 成功：UPDATE sys_job_run_log SET status='SUCCESS', end_time=NOW
  │   ├─ 失败：UPDATE status='FAILED', end_time=NOW, error_msg=stackTrace[0:4000]
  │   └─ UPDATE sys_job_conf SET last_run_time=NOW, next_run_time=trigger.next_fire_time
  └─ 释放 QRTZ_LOCKS
```

### 5.3 手动触发流程（POST /api/admin/sys/jobs/{jobId}/trigger）

```
JobController.trigger(jobId, request)
  ↓ @BizAuth(SYS_CONFIG, JOB_TRIGGER) 鉴权
  ↓
jobConfService.triggerJobNow(jobId, currentEmpId)
  ├─ 查 sys_job_conf 取 jobKey
  ├─ 构造 JobDataMap{ triggerType='MANUAL', operatorEmpId=empId }
  └─ scheduler.triggerJob(JobKey.jobKey(jobKey,"DEFAULT"), dataMap)
  ↓
Quartz 立即派发到工作线程（与 5.2 后续相同，但 JobListener 读 dataMap 中 triggerType='MANUAL'）
  ↓
sys_job_run_log 记录 trigger_type='MANUAL', created_by=operatorEmpId
```

### 5.4 暂停 / 恢复流程

```
JobController.pause(jobId)
  ↓
jobConfService.pauseJob(jobId)
  ├─ UPDATE sys_job_conf SET status='PAUSED' WHERE id=?
  └─ scheduler.pauseJob(JobKey.jobKey(jobKey,"DEFAULT"))
       └─ Quartz 内部更新 QRTZ_TRIGGERS.trigger_state='PAUSED'
            （正在执行的不会中断，但下次 next_fire_time 到达时不再触发）

resume 流程对称
```

### 5.5 集群多节点协调（说明性，单实例下也成立）

```
节点 A 与节点 B 同时启动
  ├─ A 写入 QRTZ_SCHEDULER_STATE (instanceId='nodeA-12345', last_checkin=...)
  ├─ B 写入 QRTZ_SCHEDULER_STATE (instanceId='nodeB-67890', last_checkin=...)
  └─ 心跳每 20s 更新一次 last_checkin

Job 触发时刻到达：
  ├─ A 和 B 各自的工作线程都尝试取 trigger
  ├─ QRTZ_LOCKS 表 SELECT ... FOR UPDATE → 只有 1 个节点拿到锁
  ├─ 拿到锁的节点：UPDATE QRTZ_TRIGGERS.trigger_state = 'ACQUIRED' → 释放锁 → 执行
  └─ 另一节点：拿到锁后看到 trigger_state='ACQUIRED' → 跳过该 trigger

节点 A 宕机：
  ├─ B 心跳检查发现 A 的 last_checkin 超时（默认 15s）
  ├─ B 接管 A 未完成的 trigger（QRTZ_FIRED_TRIGGERS 中 instanceId='nodeA-...' 的记录）
  └─ 触发 misfire 处理（按 sys_job_conf.misfire_policy 配置）
```

---

## 6. 错误处理与失败策略（设计 §5）

### 6.1 业务异常处理路径

```
DailyKpiCalcQuartzJob.execute()
  ├─ try { dailyKpiCalcJob.run(); }
  ├─ catch (Exception e) {
  │   ├─ log.error(...)                                   // 记录到 Spring 日志
  │   └─ throw new JobExecutionException(e, false);       // false = 不立即重试
  │ }
  ↓
Quartz 捕获 JobExecutionException
  ↓
JobListener.jobWasExecuted(context, exception)
  ├─ exception != null
  ├─ stackTrace = ExceptionUtils.getStackTrace(e)
  ├─ if (stackTrace.length > 4000) stackTrace = stackTrace.substring(0, 4000)
  └─ runLogMapper.updateFailed(runLogId, NOW, stackTrace)
  ↓
sys_job_run_log: status=FAILED, end_time=NOW, error_msg=<截断的堆栈>
  ↓
等 cron 下次触发自然重跑（决策 #6 = A）
```

**关键点**：
- `JobExecutionException(cause, refireImmediately=false)` 显式禁止 Quartz 立即重试
- error_msg 字段截断到 4000 字符（数据库列限制）
- 业务异常**不影响其他 Job**（每个 Job 独立工作线程）

### 6.2 misfire 处理路径

| Job | misfire_policy | 触发条件 | 行为 |
|-----|----------------|---------|------|
| `DailyKpiCalcJob` | `FIRE_ONCE_NOW` | 触发时刻 + misfireThreshold(60s) 已过仍未执行 | 恢复后立即补跑一次（仅 1 次，不补历史） |
| `SysControlCleanupJob` | `DO_NOTHING` | 同上 | 跳过本次，等下次正常 cron |
| `PerfRunTaskCleanupJob` | `DO_NOTHING` | 同上 | 跳过本次，等下次正常 cron |

**Quartz CronScheduleBuilder 配置映射**：
```java
CronScheduleBuilder builder = cronSchedule(cronExpr);
switch (misfirePolicy) {
    case "FIRE_ONCE_NOW":
        builder.withMisfireHandlingInstructionFireAndProceed();
        break;
    case "DO_NOTHING":
        builder.withMisfireHandlingInstructionDoNothing();
        break;
    case "IGNORE_MISFIRE_POLICY":
        builder.withMisfireHandlingInstructionIgnoreMisfires();
        break;
    default:
        throw new IllegalArgumentException("未知 misfire_policy: " + misfirePolicy);
}
```

### 6.3 JobListener 异常隔离

JobListener 内部 try-catch 防止治理日志失败影响业务：
```java
@Override
public void jobToBeExecuted(JobExecutionContext context) {
    try {
        // INSERT sys_job_run_log
    } catch (Exception e) {
        log.error("JobListener 写入 RUNNING 日志失败，jobKey={}, 业务执行不受影响",
            context.getJobDetail().getKey(), e);
        // 不抛出，Quartz 继续执行业务
    }
}

@Override
public void jobWasExecuted(JobExecutionContext context, JobExecutionException jobException) {
    try {
        // UPDATE sys_job_run_log
    } catch (Exception e) {
        log.error("JobListener 更新结果日志失败，runLogId={}, 业务执行结果未持久化",
            context.get("runLogId"), e);
        // 不抛出，避免触发 Quartz 错误处理链
    }
}
```

**对齐项目规范**：与 GovAuditLogHandler 的"审计写入失败不阻塞主业务"模式一致。

### 6.4 调度器启动失败处理

启动时 SchedulerFactoryBean 失败的可能原因：
- QRTZ_* 表不存在（DDL 未初始化）→ Spring Boot 启动失败，应用不上线（fail-fast）
- 数据库连接失败 → DataSource 健康检查阶段就拦截（与现状一致）
- Quartz 配置解析失败 → @Configuration 加载失败 → 应用不上线

**syncJobsOnStartup() 容错**：
```java
@PostConstruct
public void syncJobsOnStartup() {
    List<SysJobConf> activeJobs = jobConfMapper.selectByStatus("ACTIVE");
    int success = 0, failed = 0;
    for (SysJobConf job : activeJobs) {
        try {
            scheduleQuartzJob(job);
            success++;
        } catch (Exception e) {
            log.error("同步 Job 到 Quartz 失败，跳过该 Job 继续后续: jobKey={}", job.getJobKey(), e);
            failed++;
        }
    }
    log.info("Quartz Job 启动同步完成: 成功={}, 失败={}", success, failed);
}
```
**理由**：1 个 Job 配置错误（如 Job class 不存在 / cron 表达式非法）不应阻止其他 2 个 Job 调度。

### 6.5 ShedLock 移除后的多节点防重保证

| 防重维度 | 旧（ShedLock） | 新（Quartz Cluster） |
|---------|----------------|--------------------- |
| 同时刻多节点抢同一 Job | Redis SETNX 锁 | QRTZ_LOCKS 表行锁（FOR UPDATE） |
| 锁失败行为 | 跳过 | 跳过（同节点拿不到 trigger） |
| 锁超时机制 | TTL 1 小时（lockAtMostFor） | 心跳超时（15s 后接管） |
| 业务异常 | 锁不会自动释放（需等 TTL） | trigger 立即释放，下次 cron 重跑 |

**结论**：ShedLock 删除后**防重能力不退化**（Quartz Cluster 是更标准的方案）。

---

## 7. 测试拓扑（设计 §6）

### 7.1 测试覆盖目标（决策 #7 = A 全 TDD 重写）

| 组件 | 测试类型 | 测试数量目标 |
|------|---------|-------------|
| 3 业务 Job 类（`run()` 方法） | 单元测试 (Mockito) | 每类 ≥3（正常 / 边界 / 异常） |
| 3 Quartz 包装 Job 类（`execute()`） | 单元测试 (Mockito) | 每类 2（委托验证 + 异常透传 JobExecutionException） |
| `JobExecutionLogger`（JobListener） | 单元测试 (Mockito) | 5（jobToBeExecuted SCHEDULED / jobToBeExecuted MANUAL / jobWasExecuted 成功 / jobWasExecuted 失败 + errorMsg 截断 / 内部异常隔离） |
| `JobConfService` | 单元测试 + 集成测试 | 单元 6（CRUD + pause/resume/triggerNow）+ 集成 3（与真实 Scheduler 联动 schedule/reschedule/unschedule） |
| `JobController` | MockMvc | 5 端点 × 2（成功 + 鉴权失败）= 10 |
| `AutowiringSpringBeanJobFactory` | 单元测试 | 2（普通 Bean 注入 + Bean 不存在异常） |
| `QuartzConfig` | 集成测试 | 1（Spring 上下文加载 + Scheduler bean 可用） |

**总计目标**：约 ≥45 测试（surefire 单元 ~40 + failsafe 集成 ~5）

### 7.2 测试基础设施

#### A. Quartz 测试上下文（`@SpringBootTest` 子集）

```java
@SpringBootTest(classes = {QuartzConfig.class, JobConfService.class, ...})
@TestPropertySource(properties = {
    "spring.quartz.job-store-type=memory",        // 测试用内存存储，不依赖 QRTZ_* 表
    "spring.quartz.properties.org.quartz.jobStore.isClustered=false"
})
class JobConfServiceIntegrationTest {
    @Autowired Scheduler scheduler;
    @Autowired JobConfService jobConfService;

    // 用真实 Scheduler 验证 schedule/reschedule/unschedule 行为
}
```

**关键决策**：
- **集成测试用内存 JobStore**（`RAMJobStore`）→ 不需要测试容器启动 MySQL + 11 张 QRTZ_* 表
- **单元测试纯 Mockito**（Mock Scheduler）→ 不启动 Spring 上下文

#### B. JobListener 单元测试模式

```java
@ExtendWith(MockitoExtension.class)
class JobExecutionLoggerTest {
    @Mock SysJobConfMapper jobConfMapper;
    @Mock SysJobRunLogMapper runLogMapper;
    @Mock JobExecutionContext context;
    @Mock JobDetail jobDetail;

    @InjectMocks JobExecutionLogger listener;

    @Test
    void jobToBeExecuted_scheduledTrigger_writesRunningLog() {
        // given
        when(context.getJobDetail()).thenReturn(jobDetail);
        when(jobDetail.getKey()).thenReturn(JobKey.jobKey("DAILY_KPI_CALC", "DEFAULT"));
        when(jobConfMapper.selectByJobKey("DAILY_KPI_CALC")).thenReturn(testJobConf());
        // when
        listener.jobToBeExecuted(context);
        // then
        ArgumentCaptor<SysJobRunLog> captor = ArgumentCaptor.forClass(SysJobRunLog.class);
        verify(runLogMapper).insert(captor.capture());
        assertThat(captor.getValue().getTriggerType()).isEqualTo("SCHEDULED");
        verify(context).put(eq("runLogId"), anyString());
    }
}
```

#### C. 业务 Job 单元测试模式

`DailyKpiCalcJob`（注入 `KpiSchemeService` + `KpiCalcService`，run() 返回 void）：
```java
@ExtendWith(MockitoExtension.class)
class DailyKpiCalcJobTest {
    @Mock KpiSchemeService kpiSchemeService;     // 真实注入字段名
    @Mock KpiCalcService kpiCalcService;
    @InjectMocks DailyKpiCalcJob job;

    @Test
    void run_withActiveSchemes_callsCalcSchemeForEach() {
        when(kpiSchemeService.listActiveSchemes()).thenReturn(List.of(scheme1, scheme2));
        job.run();   // void
        verify(kpiCalcService).calcScheme(
            eq(scheme1.getSchemeCode()), eq(scheme1.getCycleType()),
            any(LocalDate.class), any(LocalDate.class), anyString());
        verify(kpiCalcService).calcScheme(
            eq(scheme2.getSchemeCode()), eq(scheme2.getCycleType()),
            any(LocalDate.class), any(LocalDate.class), anyString());
    }

    @Test
    void run_withNoActiveSchemes_doesNothing() {
        when(kpiSchemeService.listActiveSchemes()).thenReturn(List.of());
        job.run();
        verifyNoInteractions(kpiCalcService);
    }

    @Test
    void run_singleSchemeFails_continuesOtherSchemes() {
        // 验证现状：单方案 catch 并 log warn，不中断循环
        when(kpiSchemeService.listActiveSchemes()).thenReturn(List.of(scheme1, scheme2));
        when(kpiCalcService.calcScheme(eq(scheme1.getSchemeCode()), any(), any(), any(), any()))
            .thenThrow(new RuntimeException("scheme1 异常"));
        job.run();   // 不抛
        verify(kpiCalcService).calcScheme(eq(scheme2.getSchemeCode()), any(), any(), any(), any());
    }
}
```

`SysControlCleanupJob` / `PerfRunTaskCleanupJob`（run() 返回 int）测试模式类似但需断言返回值：
```java
@Test
void run_returnsTotalDeleted() {
    when(sysControlMapper.selectScopeDims()).thenReturn(List.of("scope1", "scope2"));
    when(sysControlMapper.selectOldVersionIdsForCleanup("scope1", 12)).thenReturn(List.of("id1", "id2"));
    when(sysControlMapper.deleteByIds(List.of("id1", "id2"))).thenReturn(2);
    when(sysControlMapper.selectOldVersionIdsForCleanup("scope2", 12)).thenReturn(List.of());

    int total = job.run();   // 返回 int
    assertThat(total).isEqualTo(2);
}
```

#### D. Quartz 包装类单元测试模式

```java
@ExtendWith(MockitoExtension.class)
class DailyKpiCalcQuartzJobTest {
    @Mock DailyKpiCalcJob dailyKpiCalcJob;
    @Mock JobExecutionContext context;
    @InjectMocks DailyKpiCalcQuartzJob quartzJob;

    @Test
    void execute_delegatesToBusinessJob() throws JobExecutionException {
        quartzJob.execute(context);
        verify(dailyKpiCalcJob).run();
    }

    @Test
    void execute_businessException_throwsJobExecutionException() {
        doThrow(new RuntimeException("业务异常")).when(dailyKpiCalcJob).run();
        JobExecutionException ex = assertThrows(JobExecutionException.class,
            () -> quartzJob.execute(context));
        assertThat(ex.getCause()).isInstanceOf(RuntimeException.class);
        assertThat(ex.refireImmediately()).isFalse();
    }
}
```

#### E. JobController MockMvc 测试

```java
@WebMvcTest(JobController.class)
class JobControllerTest {
    @MockBean JobConfService jobConfService;
    @MockBean SysJobRunLogService runLogService;

    @Test
    @WithMockUser(authorities = "SYS_CONFIG:JOB_TRIGGER")
    void trigger_success_returnsOk() throws Exception { ... }

    @Test
    @WithMockUser(authorities = "SYS_CONFIG:READ")  // 缺少 JOB_TRIGGER
    void trigger_missingPermission_returns403() throws Exception { ... }
}
```

### 7.3 测试不覆盖的边界（明确说明）

- **多节点 QRTZ_LOCKS 真实并发**：依赖 MySQL 集群部署，测试容器代价高 → **不覆盖**（信任 Quartz 官方）
- **misfire 真实触发**：需停掉调度器 + 时钟跳跃 → **不覆盖**（信任 Quartz 配置正确性）
- **集群心跳 / 节点接管**：同上 → **不覆盖**

### 7.4 与子项目 A 测试节奏对齐

- 单元测试用 surefire（mvn test 阶段执行）
- 集成测试用 failsafe（mvn verify 阶段执行）
- TDD Red-Green-Refactor 闭环（CLAUDE.md 红线）

---

## 8. 清理与迁移（设计 §7）

### 8.1 ShedLock 完全删除（决策 #2 = A）

**Maven 依赖移除**：
```xml
<!-- performance-engine-center/pom.xml 删除以下 2 项 -->
<dependency>
    <groupId>net.javacrumbs.shedlock</groupId>
    <artifactId>shedlock-spring</artifactId>
</dependency>
<dependency>
    <groupId>net.javacrumbs.shedlock</groupId>
    <artifactId>shedlock-provider-redis-spring</artifactId>
</dependency>
```

**根 pom.xml dependencyManagement 移除 ShedLock 版本声明**

**配置类删除**：
- 删除 `performance-engine-center/src/main/java/com/bank/branch/platform/performance/config/ShedLockConfig.java`

**注解清理**（3 个 Job 类）：
| Job | 删除项 |
|-----|--------|
| `DailyKpiCalcJob` | `@Scheduled(cron="${...}")` + `@SchedulerLock(name=..., lockAtMostFor=..., lockAtLeastFor=...)` |
| `SysControlCleanupJob` | 同上 |
| `PerfRunTaskCleanupJob` | 同上 |

**保留**：3 个 Job 上的 `@Component` 不删（业务 Bean 仍需 Spring 管理）

**验证**：grep 全库
```bash
grep -r "shedlock\|@SchedulerLock\|ShedLockConfig" --include="*.java" --include="*.xml" --include="*.yml"
```
预期返回 0 行

**configurationProperties 清理**（application.yml）：
```yaml
# 删除：
shedlock:
  defaults:
    lock-at-most-for: PT1H
```

### 8.2 JobApi 精简的影响面（决策 #5 = B）

**删除 3 方法**：
- `JobApi.startJobRun(jobId, triggerType, empId)`
- `JobApi.completeJobRun(runLogId)`
- `JobApi.failJobRun(runLogId, errorMsg)`

**对应实现删除**：
- `JobFacade.startJobRun/completeJobRun/failJobRun`（删除 3 方法）
- `SysJobRunLogService` 中 startJobRun/completeJobRun/failJobRun 方法**保留**（被 `JobExecutionLogger` 内部调用），但变为 `package-private` 或仅 system-governance 内部 `@Service`

**调用方扫描**：
```bash
grep -r "JobApi\." --include="*.java" | grep -E "startJobRun|completeJobRun|failJobRun"
```
预期返回 0 行（前期探索已确认）

**DTO 清理**：检查 `JobConfDTO` 之外是否有专用 DTO（如 `JobRunCmd`）→ 若仅这 3 方法用 → 删除

**JobApi 单元测试**：
- 保留 `getJobConf` 的测试
- 删除 `startJobRun/completeJobRun/failJobRun` 的所有测试（如有）

### 8.3 配置项变更（application.yml）

**新增**：见 §4.2 quartz 配置块全文

**删除**（旧 Job cron 与 enabled 配置项 — 不再生效，迁移到 sys_job_conf 表）：
```yaml
# 删除（真实 EL 表达式前缀为 perf.job.*，不是 performance.job.*）：
perf:
  job:
    daily-kpi:
      enabled: true                  # 不再生效（启停由 sys_job_conf.status 控制）
      cron: "0 30 1 * * ?"           # 不再生效（cron 由 sys_job_conf.cron_expr 控制）
    sys-control-cleanup:
      enabled: true
      cron: "0 0 3 * * ?"
      # keep-count: 12               # 保留！业务参数，业务 Job 仍读取
    run-task-cleanup:
      enabled: true
      cron: "0 30 3 * * ?"
      # retention-days: 90           # 保留！业务参数，业务 Job 仍读取
```

**保留项说明**：
- `perf.job.sys-control-cleanup.keep-count`（默认 12）→ `SysControlCleanupJob.@Value` 仍读取
- `perf.job.run-task-cleanup.retention-days`（默认 90）→ `PerfRunTaskCleanupJob.@Value` 仍读取
- 这两个**业务参数**与调度无关，不迁移到 sys_job_conf

**清理时的 grep 验证**（防漏删/误删）：
```bash
# 应被删除（验证返回 0 行）
grep -rn "perf\.job\.daily-kpi\.\(enabled\|cron\)" --include="*.yml" --include="*.properties"
grep -rn "perf\.job\.sys-control-cleanup\.\(enabled\|cron\)" --include="*.yml" --include="*.properties"
grep -rn "perf\.job\.run-task-cleanup\.\(enabled\|cron\)" --include="*.yml" --include="*.properties"

# 应保留（验证仍存在）
grep -rn "perf\.job\.sys-control-cleanup\.keep-count" --include="*.yml" --include="*.properties"
grep -rn "perf\.job\.run-task-cleanup\.retention-days" --include="*.yml" --include="*.properties"
```

**删除**（ShedLock 已移除）：
```yaml
# 删除：
shedlock:
  defaults:
    lock-at-most-for: PT1H
```

### 8.4 Maven 版本管理（根 pom.xml）

**Quartz 版本**：Spring Boot 3.2.3 默认管理，spring-boot-starter-quartz 自带 quartz 2.3.2，不需要额外声明。

**performance-engine-center/pom.xml 新增**：
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-quartz</artifactId>
</dependency>
```

**system-governance-center/pom.xml 新增**：
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-quartz</artifactId>
</dependency>
```

### 8.5 数据库迁移脚本顺序（部署时一次性执行）

```sql
-- Step 1: 初始化 11 张 QRTZ_* 表（来源：docs/schema/ddl-quartz.sql）
SOURCE docs/schema/ddl-quartz.sql;

-- Step 2: sys_job_conf 表字段扩展
ALTER TABLE sys_job_conf
    ADD COLUMN quartz_job_class VARCHAR(255) NOT NULL DEFAULT '' COMMENT 'Quartz 包装 Job 类全限定名',
    ADD COLUMN misfire_policy   VARCHAR(32)  NOT NULL DEFAULT 'FIRE_ONCE_NOW' COMMENT 'misfire 处理策略';

-- Step 3: sys_job_run_log 表字段扩展
ALTER TABLE sys_job_run_log
    ADD COLUMN scheduled_fire_time DATETIME(3) NULL COMMENT 'Quartz 计划触发时间';

-- Step 4: 写入 3 条业务 Job 记录
INSERT INTO sys_job_conf (id, job_key, job_name, cron_expr, status, allow_manual_trigger,
                          quartz_job_class, misfire_policy, ...) VALUES
('xxx-uuid-1', 'DAILY_KPI_CALC', '日常 KPI 计算', '0 30 1 * * ?', 'ACTIVE', 1,
 'com.bank.branch.platform.performance.job.quartz.DailyKpiCalcQuartzJob', 'FIRE_ONCE_NOW', ...),
('xxx-uuid-2', 'SYS_CONTROL_CLEANUP', '系统控制历史清理', '0 0 3 * * ?', 'ACTIVE', 1,
 'com.bank.branch.platform.performance.job.quartz.SysControlCleanupQuartzJob', 'DO_NOTHING', ...),
('xxx-uuid-3', 'PERF_RUN_TASK_CLEANUP', '绩效执行任务清理', '0 30 3 * * ?', 'ACTIVE', 1,
 'com.bank.branch.platform.performance.job.quartz.PerfRunTaskCleanupQuartzJob', 'DO_NOTHING', ...);
```

**归档位置**：`docs/schema/migrations/2026-04-25-quartz-integration.sql`

### 8.6 DDL 文件全套清单（变更后）

```
docs/schema/
├── ddl-governance.sql       (修改：sys_job_conf + sys_job_run_log 字段说明同步)
├── ddl-quartz.sql           (新增：11 张 QRTZ_* 表 DDL)
└── migrations/
    └── 2026-04-25-quartz-integration.sql  (新增：完整迁移脚本)
```

### 8.7 文档更新清单

| 文件 | 变更点 |
|------|--------|
| 根 `CLAUDE.md` | 模块 status 表 performance-engine-center 加 V1.6 标记（quartz 整合）|
| `system-governance-center/CLAUDE.md` | JobApi 4 方法 → 1 方法，新增 QuartzConfig + JobExecutionLogger 说明 |
| `performance-engine-center/CLAUDE.md` | 3 业务 Job + 3 Quartz 包装 Job 说明，删 ShedLock 章节 |
| `docs/CLAUDE.md` | 新增 ddl-quartz.sql 说明 |

---

## 9. 文件影响清单（变更总览）

### 9.1 新建文件（11 个）

| 文件 | 模块 |
|------|------|
| `system-governance-center/src/main/java/.../config/QuartzConfig.java` | system-governance-center |
| `system-governance-center/src/main/java/.../config/AutowiringSpringBeanJobFactory.java` | system-governance-center |
| `system-governance-center/src/main/java/.../listener/JobExecutionLogger.java` | system-governance-center |
| `performance-engine-center/src/main/java/.../job/quartz/DailyKpiCalcQuartzJob.java` | performance-engine-center |
| `performance-engine-center/src/main/java/.../job/quartz/SysControlCleanupQuartzJob.java` | performance-engine-center |
| `performance-engine-center/src/main/java/.../job/quartz/PerfRunTaskCleanupQuartzJob.java` | performance-engine-center |
| `docs/schema/ddl-quartz.sql` | docs |
| `docs/schema/migrations/2026-04-25-quartz-integration.sql` | docs |
| 测试文件 ×N | 各模块 src/test |

### 9.2 修改文件（核心 ~12 个）

| 文件 | 改动类型 |
|------|---------|
| `pom.xml`（根） | 删除 ShedLock dependencyManagement |
| `performance-engine-center/pom.xml` | 删除 shedlock 2 项 + 新增 spring-boot-starter-quartz |
| `system-governance-center/pom.xml` | 新增 spring-boot-starter-quartz |
| `performance-engine-center/src/main/java/.../job/DailyKpiCalcJob.java` | 删 @Scheduled/@SchedulerLock，保留 @Component + run() |
| `performance-engine-center/src/main/java/.../job/SysControlCleanupJob.java` | 同上 |
| `performance-engine-center/src/main/java/.../job/PerfRunTaskCleanupJob.java` | 同上 |
| `system-governance-center/src/main/java/.../api/JobApi.java` | 删 startJobRun/completeJobRun/failJobRun |
| `system-governance-center/src/main/java/.../facade/JobFacade.java` | 删对应 3 方法 |
| `system-governance-center/src/main/java/.../service/JobConfService.java` | 重构：CRUD + Scheduler 联动 |
| `system-governance-center/src/main/java/.../controller/JobController.java` | 5 端点真正实现 |
| `bootstrap/src/main/resources/application.yml` | 删旧 cron + 删 shedlock + 增 quartz 配置 |
| `docs/schema/ddl-governance.sql` | sys_job_conf/sys_job_run_log 字段同步注释 |

### 9.3 删除文件（1 个）

| 文件 |
|------|
| `performance-engine-center/src/main/java/.../config/ShedLockConfig.java` |

---

## 10. 验收标准

### 10.1 功能验收

- [ ] 启动应用后，3 条 sys_job_conf 记录被自动同步到 QRTZ_* 表（QRTZ_TRIGGERS 有 3 条记录）
- [ ] 修改 sys_job_conf.cron_expr 字段后重启应用，QRTZ_TRIGGERS 对应 cron 自动更新
- [ ] `POST /api/admin/sys/jobs/{jobId}/trigger` 触发后，sys_job_run_log 出现 trigger_type='MANUAL' 记录
- [ ] `PUT /api/admin/sys/jobs/{jobId}/pause` 后，sys_job_conf.status='PAUSED' 且 QRTZ_TRIGGERS.trigger_state='PAUSED'
- [ ] Job 执行成功 → sys_job_run_log status='SUCCESS' + sys_job_conf.last_run_time 更新
- [ ] Job 执行失败 → sys_job_run_log status='FAILED' + error_msg 包含截断的堆栈

### 10.2 代码验收

- [ ] 全库 grep `shedlock|@SchedulerLock|ShedLockConfig` 返回 0 行
- [ ] 全库 grep `@Scheduled` 在 3 个 Job 类中返回 0 行
- [ ] 全库 grep `JobApi\.(startJobRun|completeJobRun|failJobRun)` 返回 0 行
- [ ] `JobApi.java` 仅包含 1 个方法 `getJobConf`
- [ ] mvn surefire（单元测试）≥45 个测试全部通过
- [ ] mvn failsafe（集成测试）≥5 个测试全部通过
- [ ] 全模块 mvn clean install 成功

### 10.3 文档验收

- [ ] `docs/schema/ddl-quartz.sql` 存在且包含 11 张 QRTZ_* 表完整 DDL
- [ ] `docs/schema/migrations/2026-04-25-quartz-integration.sql` 存在且可一次性执行
- [ ] `system-governance-center/CLAUDE.md` 中 JobApi 章节更新（4 方法 → 1 方法）
- [ ] `performance-engine-center/CLAUDE.md` 中删除 ShedLock 相关说明，新增 Quartz 包装 Job 说明
- [ ] 根 `CLAUDE.md` 中 performance-engine-center 模块状态加 V1.6 quartz 整合标记

---

## 11. 风险与应对

| 风险 | 概率 | 影响 | 应对 |
|------|------|------|------|
| QRTZ_* 表初始化遗漏导致启动失败 | 中 | 高 | 部署文档明确 ddl-quartz.sql 必须先执行；启动失败有清晰报错（fail-fast） |
| @Autowired 注入失败（Quartz Job 实例化） | 中 | 高 | AutowiringSpringBeanJobFactory 单元测试覆盖；启动后立即跑一次 syncJobsOnStartup 暴露问题 |
| misfire 配置错误导致雪崩补跑 | 低 | 中 | 仅 DailyKpiCalcJob 配 FIRE_ONCE_NOW，2 个清理 Job 配 DO_NOTHING；测试覆盖 misfire policy 解析 |
| sys_job_run_log 写入失败影响业务 | 低 | 中 | JobListener 内部 try-catch 异常隔离（与 GovAuditLogHandler 模式一致） |
| ShedLock 删除后 Redis 连接闲置 | 低 | 低 | Redis 仍被 Spring Session + 缓存使用，不会变成"裸跑" |
| 集群环境下 QRTZ_LOCKS 行锁性能 | 低 | 低 | 当前单实例部署，未来需扩容时再观察 |

---

## 12. 实施节奏（参考）

按子项目 A 的实施节奏（implementer + 1 次合并 spec+quality reviewer），预估 **3-4 个 Phase**：

| Phase | 范围 | 预估 task 数 |
|-------|------|------------|
| P1 基础设施 | DDL + pom 依赖 + QuartzConfig + AutowiringSpringBeanJobFactory + JobExecutionLogger | ~6 |
| P2 Job 改造 | 3 业务 Job + 3 Quartz 包装 Job + 单元测试 | ~9 |
| P3 服务层 | JobConfService 重构 + JobController 5 端点真正实现 + 集成测试 | ~6 |
| P4 清理收尾 | ShedLock 删除 + JobApi 精简 + application.yml 调整 + 文档更新 + 全量验证 | ~5 |

**总计预估**：约 26 task（与子项目 A 的 24 task 数量级一致）

---

## 13. 附录：Brainstorming 决策来源

详见 `docs/superpowers/sessions/2026-04-25-quartz-brainstorming-state.md`（Q1-Q2 决策状态）+ 后续追问 Q3-Q7（本设计文档 §1 决策日志）。

**完整 7 问决策路径**：
1. Q1（sys_job_conf 与 QRTZ_* 关系）→ B（双写）
2. Q2（ShedLock 处置）→ A（删除）
3. Q3（QRTZ_* schema 归属）→ A（同库 onepl）
4. Q4（Job 改造方式）→ B（解耦式包装）
5. Q5（JobApi 表面积）→ B（精简到 1 方法）
6. Q6（失败重试）→ A（业务异常下次调度）+ misfire 分级（KPI=FIRE_ONCE_NOW，清理=DO_NOTHING）
7. Q7（测试策略）→ A（全 TDD 重写）

---

**END OF SPEC**
