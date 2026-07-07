# 指标级 Quartz 调度改造设计 (V1.7)

- **模块**: performance-engine-center
- **版本**: V1.7
- **日期**: 2026-04-30
- **作者**: Brainstorming Session
- **依赖**: V1.6 quartz 整合（已交付）

## 1. 背景与目标

### 1.1 当前痛点

V1.6 quartz 整合后，performance 模块仅有 3 个**全局**调度任务：

| Job | 频率 | 范围 |
|---|---|---|
| `DailyKpiCalcJob` | 每日凌晨 2 点 | 跑全部 ACTIVE KPI 方案 |
| `SysControlCleanupJob` | 每周日凌晨 3 点 | 清理失效 sys_control 行 |
| `PerfRunTaskCleanupJob` | 每日凌晨 4 点 | 清理过期 perf_run_task |

`PerfMetricDef` 表的 `calc_freq` 字段（`DAY/MONTH/QUARTER/YEAR`）仅是文档性标签，**没有真实驱动调度**。`MetricCalcService.calcMetric` 仅在以下场景被触发：
- 用户手动调用 REST 端点
- `HistoryRecalcService` 历史回算
- `DailyKpiCalcJob` 间接触发（通过 KpiCalcService）

### 1.2 业务诉求

1. **指标定义里的"按月、按周、日"频率应转化为 cron 表达式**
2. **每个 ACTIVE+AUTO 指标独立调度**，由 Quartz 集群按 cron 触发
3. **一次 Job 跑全量明细**：EMP 维度指标 A 关联 100 个员工，触发一次 Job 必须把 100 个员工的指标 A 全部计算并入库
4. **取代 `DailyKpiCalcJob`**：KPI 方案不再走"每日凌晨批量"路径，改为"指标计算完成后事件驱动"

## 2. 关键决策

| 决策点 | 选择 | 备选 | 理由 |
|---|---|---|---|
| 调度颗粒度 | **C：每指标 1:1 注册 Quartz Job + 保留 `calc_freq` 做默认值 + 新增 `cron_expr` 字段** | A 强制 cron / B 按频率聚合中央 Job | 兼容存量、最大灵活度、可单点暂停-恢复 |
| EXPR/GROOVY 多主体集合 | **甲：指标定义新增 `subject_sql` 字段** | 乙：base_dim 全量枚举 / 丙：限制 EXPR 只支持 AGG | 与"声明式指标"风格一致；每指标自带主体范围 |
| 失败颗粒度 | **部分成功 PARTIAL_FAILED**：单 subject 异常 catch 不抛，按 success/failed 计数收尾 | 整批失败 / 整批跳过 | 避免 1 个员工脏数据让全员重跑 |
| KPI 方案触发 | **(a) 事件驱动**：监听 `MetricCalcCompletedEvent`，依赖该指标的 KPI 方案触发重算 | (b) 挂在指标 Job 末尾 / (c) 保留独立 KPI Job | 与 V1.2 已有的 4 类领域事件机制一致；解耦指标与 KPI 时序 |
| Quartz 注册路径 | **i：走 sys_job_conf 中转**；governance 暴露 `JobApi.registerJob/unregisterJob` | ii：performance 直接操作 Scheduler bean | 与 V1.6 现有架构对齐；指标级 Job 也能从 JobController 治理界面看到 |
| MANUAL/DISABLED/PROC/SUMMARY 处理 | **不注册 Quartz Job**，仅 WARN 日志 | 报错阻止 | 兼容存量、不阻塞应用启动 |

## 3. 整体架构与数据流

### 3.1 模块边界

```
performance-engine-center                     system-governance-center
─────────────────────────                     ─────────────────────────
PerfMetricDef                                 sys_job_conf  (统一治理表)
  + cron_expr                                 sys_job_run_log
  + subject_sql                               JobApi
  + last_run_time                               + registerJob   (新)
                                                + unregisterJob (新)
MetricSchedulerService     ──registerJob──▶   JobService
  CRUD Hook + 启动同步                          + 反射加载 quartz_job_class
                                                + cronSchedule + misfirePolicy
                                                + scheduler.scheduleJob

MetricExecuteQuartzJob     ◀──Quartz 触发────  Quartz Scheduler (集群)
  从 JobDataMap 取 metricCode                   QRTZ_LOCKS 行锁防重
  调 MetricCalcService.calcMetric

MetricCalcService          ──发布事件────▶    Spring ApplicationEvent
  EXPR/GROOVY 多主体改造                        MetricCalcCompletedEvent

KpiCascadeListener         ◀──@EventListener
  检查依赖指标都齐了 → 调
  KpiCalcService.calcScheme
```

### 3.2 一次自动调度的完整数据流（"日指标 A，EMP 维度，100 人"为例）

1. **Quartz 触发**：cron `0 0 2 * * ?` 到点，Scheduler 通过 `QRTZ_LOCKS` 抢到行锁，invoke `MetricExecuteQuartzJob.execute()`
2. **JobListener 写日志**：`JobExecutionLogger.jobToBeExecuted` 写 `sys_job_run_log` (status=RUNNING)
3. **业务执行**：
   - `MetricCalcService.calcMetric(metricCode="A", dataDate=T-1, version=...)`
   - 插入 `perf_run_task` (PENDING → RUNNING)
   - 路由到 `executeGroovyAndPersist`（A 是 EXPR 类型）
   - **取主体集合**：用 `subject_sql` 查出 100 个 emp_id
   - **foreach 主体**：100 次执行 GroovyExecutor，结果写 `emp_index_result.val_${slot}`
   - **失败颗粒度**：单个 emp 异常 catch 不抛，累加 successCount/failedCount
   - 写 `perf_run_task` 终态：`SUCCESS` / `PARTIAL_FAILED` / `FAILED`
   - **发布事件**：`MetricCalcCompletedEvent(metricCode, dataDate, version, baseDim, status, subjectStats)`
4. **JobListener 收尾**：`jobWasExecuted` 写 `sys_job_run_log` (终态)
5. **KPI 联动**：
   - `KpiCascadeListener.@TransactionalEventListener(AFTER_COMMIT)` + `@Async`
   - 反查 `perf_kpi_item` 找出引用指标 A 的 ACTIVE KPI 方案
   - 对每个方案：按 cycle_type 推导 cycleDate
   - Redis SETNX 防重（30 秒窗口）
   - 调 `KpiCalcService.calcScheme(...)`

### 3.3 关键不变式

- **每条 ACTIVE+AUTO 指标 ⇔ sys_job_conf 1 行 ⇔ QRTZ_TRIGGERS 1 行**（1:1:1）
- **运行日志双写**：`sys_job_run_log`（治理可见）+ `perf_run_task`（业务参数 + 明细计数），通过 `perf_run_task.params_json.jobKey` 关联
- **`DailyKpiCalcJob` 删除**，KPI 方案级调度不复存在
- **MANUAL / DISABLED / PROC / SUMMARY 不注册**，但仍可手动 `MetricCalcService.calcMetric` 或回算

## 4. DDL 与数据契约

### 4.1 `PERF_METRIC_DEF` 表结构变更（Flyway `V1_7_0`）

```sql
-- V1_7_0__perf_metric_def_schedule_cols.sql
ALTER TABLE PERF_METRIC_DEF
  ADD COLUMN cron_expr     VARCHAR(120) NULL COMMENT '自定义 cron 表达式；留空按 calc_freq 推导默认',
  ADD COLUMN subject_sql   LONGTEXT     NULL COMMENT 'EXPR/GROOVY 类型的主体集合 SQL（SELECT base_key FROM ...）；SQL/PROC/SUMMARY 类型不需要',
  ADD COLUMN last_run_time DATETIME     NULL COMMENT '最近一次自动调度执行时间（冗余，便于运维快速看到调度活性）';

CREATE INDEX idx_metric_def_schedulable
  ON PERF_METRIC_DEF (status, calc_mode, deleted);
```

配套 `U1_7_0__perf_metric_def_schedule_cols.sql` 反向 DROP。

### 4.2 `calc_freq` 枚举扩展

| 枚举 | 取值 | 用途 |
|---|---|---|
| `CalcFreqEnum`（**新增**） | `DAY / WEEK / MONTH / QUARTER / YEAR` | `PERF_METRIC_DEF.calc_freq` 取值；默认 cron 推导依据 |
| `CycleTypeEnum`（已有） | `MONTHLY / QUARTERLY / YEARLY` + `WEEKLY` (V1.5) | `PERF_KPI_SCHEME.cycle_type`；KPI 考核周期，与计算频率解耦 |

> 命名分离原因：指标"计算频率"（多久跑）与 KPI"考核周期"（多久评分）是不同维度。指标日跑日入库，KPI 月度评分（聚合 30 天数据）。两枚举不能合并。

### 4.3 默认 cron 推导规则（统一凌晨 2 点）

`MetricCronResolver.resolve(PerfMetricDef)` 实现：

```java
public String resolve(PerfMetricDef def) {
    if (StringUtils.hasText(def.getCronExpr())) return def.getCronExpr();
    return switch (def.getCalcFreq()) {
        case "DAY"     -> "0 0 2 * * ?";
        case "WEEK"    -> "0 0 2 ? * MON";
        case "MONTH"   -> "0 0 2 1 * ?";
        case "QUARTER" -> "0 0 2 1 1,4,7,10 ?";
        case "YEAR"    -> "0 0 2 1 1 ?";
        default -> throw new PerfException(METRIC_CALC_FREQ_INVALID, def.getCalcFreq());
    };
}
```

### 4.4 `jobKey` 与 `jobGroup` 命名约定

```
sys_job_conf.job_key  = "PERF_METRIC_" + metricCode
QRTZ_*.JOB_GROUP      = "PERF_METRIC"
QRTZ_*.TRIGGER_GROUP  = "PERF_METRIC"
```

- `metricCode` 由 `uk_metric_code` 保证全局唯一，加前缀仍 < 192 字符
- 若现有 `sys_job_conf.job_key` 列宽 < 192，需在 V1_7_0 同时拓宽

### 4.5 `MetricCalcCompletedEvent` 字段契约

```java
public record MetricCalcCompletedEvent(
    String metricCode,           // 指标编码
    String baseDim,              // EMP / ORG / CUST
    LocalDate dataDate,          // 数据日期（计算口径，T-1）
    String version,              // 数据版本
    String runStatus,            // SUCCESS / PARTIAL_FAILED / FAILED
    int subjectTotal,            // 主体总数
    int subjectSuccess,          // 成功明细数
    int subjectFailed,           // 失败明细数
    String runTaskId,            // perf_run_task.id
    String triggerType,          // SCHEDULED / MANUAL / RECALC
    LocalDateTime occurredAt     // 事件发生时间
) {}
```

### 4.6 `perf_run_task.params_json` 扩展约定

不改 DDL，靠 `params_json` longtext 承载新字段：

```json
{
  "jobKey": "PERF_METRIC_M_DEPOSIT_DAILY",
  "triggerType": "SCHEDULED",
  "subjectTotal": 100,
  "subjectSuccess": 95,
  "subjectFailed": 5,
  "failedSamples": ["E001", "E007", "E033"]
}
```

`perf_run_task.status` 状态机扩展：
- 原：`PENDING → RUNNING → SUCCESS / FAILED`
- **新增**：`PARTIAL_FAILED`（终态，failedCount > 0 且 successCount > 0）
- `RunTaskStatusEnum` 加此值，需审计现有 `*Mapper.xml` 中 `WHERE status IN ('SUCCESS')` 视图代码 → 大概率改为 `IN ('SUCCESS', 'PARTIAL_FAILED')`

### 4.7 V1.6 现有 3 个 perf 任务的处理

- **删除**：`DailyKpiCalcJob` + `DailyKpiCalcQuartzJob` + sys_job_conf 中 `jobKey=PERF_DAILY_KPI_CALC` 行
- **保留**：`SysControlCleanupJob` / `PerfRunTaskCleanupJob`（与指标级调度无关）

`V1_7_1__remove_daily_kpi_calc_job.sql`:
```sql
DELETE FROM sys_job_conf WHERE job_key = 'PERF_DAILY_KPI_CALC';
```

## 5. governance `JobApi` 扩展

### 5.1 新增 2 个方法

```java
public interface JobApi {
    Optional<JobConfDTO> getJobConf(String jobKey);                  // V1.6 已有

    /**
     * V1.7 新增：注册（或覆盖）一个调度任务.
     * 原子写入 sys_job_conf 一行 + Quartz Scheduler 注入 JobDetail/CronTrigger.
     * 若 jobKey 已存在则覆盖；若 Scheduler 不可用（测试上下文）则仅写 sys_job_conf 不抛异常.
     *
     * @return 写入后的 sys_job_conf 主键 id
     * @throws BizException GOV-50001 cron 表达式非法
     * @throws BizException GOV-50002 quartz_job_class 反射失败
     * @throws BizException GOV-50003 Scheduler 注册失败
     */
    String registerJob(RegisterJobCmd cmd);

    /**
     * V1.7 新增：注销一个调度任务（幂等）.
     * Quartz Scheduler 反向移除 + 删 sys_job_conf；jobKey 不存在时静默返回.
     */
    void unregisterJob(String jobKey);
}
```

### 5.2 `RegisterJobCmd` 字段

```java
public class RegisterJobCmd {
    private String jobKey;                  // 必填：唯一标识
    private String jobName;                 // 必填：展示名
    private String jobGroup;                // 选填：默认 "DEFAULT"
    private String cronExpr;                // 必填：合法 cron
    private String quartzJobClass;          // 必填：QuartzJobBean 全限定名
    private Map<String,String> jobData;     // 选填：透传到 JobDataMap
    private String misfirePolicy;           // 选填：默认 "FIRE_ONCE_NOW"
    private boolean allowManualTrigger;     // 选填：默认 true
    private String remark;                  // 选填
}
```

### 5.3 governance 端 `JobService.registerJob` 实现要点

```java
@Transactional
public String registerJob(RegisterJobCmd cmd) {
    // 1. 校验 cron 合法
    if (!CronExpression.isValidExpression(cmd.getCronExpr()))
        throw new BizException(GovErrorCode.JOB_CRON_INVALID);
    // 2. 校验 quartz_job_class 可加载
    Class<?> jobClass;
    try {
        jobClass = Class.forName(cmd.getQuartzJobClass());
        if (!Job.class.isAssignableFrom(jobClass))
            throw new BizException(GovErrorCode.JOB_CLASS_NOT_FOUND);
    } catch (ClassNotFoundException e) {
        throw new BizException(GovErrorCode.JOB_CLASS_NOT_FOUND);
    }
    // 3. upsert sys_job_conf（按 job_key 唯一键）
    SysJobConf conf = jobConfMapper.selectByJobKey(cmd.getJobKey());
    if (conf == null) { /* insert with new UUID */ }
    BeanUtils.copyProperties(cmd, conf);
    conf.setStatus(JobStatus.ACTIVE.getCode());
    conf.setUpdatedTime(LocalDateTime.now());
    jobConfMapper.updateById(conf);

    // 4. Scheduler 注入（test 上下文 scheduler==null 时跳过）
    if (scheduler != null) {
        // 改造 scheduleQuartzJob 接受 JobDataMap 参数
        JobDataMap dataMap = cmd.getJobData() != null
            ? new JobDataMap(cmd.getJobData()) : new JobDataMap();
        scheduleQuartzJob(conf, dataMap);   // 在 V1.6 scheduleQuartzJob 基础上加 JobDataMap 入参
    }
    return conf.getId();
}
```

`unregisterJob` 镜像：先 `scheduler.deleteJob(JobKey)`，再 `jobConfMapper.deleteByJobKey`，幂等返回。

### 5.4 新增错误码

| 错误码 | 含义 |
|---|---|
| GOV-50001 | JOB_CRON_INVALID |
| GOV-50002 / GOV-50003 | JOB_CLASS_NOT_FOUND / JOB_REGISTER_FAILED |

## 6. performance 端 `MetricSchedulerService`

### 6.1 类设计

```java
@Service
@RequiredArgsConstructor
public class MetricSchedulerService {

    private final JobApi jobApi;
    private final MetricDefService metricDefService;
    private final MetricCronResolver cronResolver;

    /** 启动期同步：扫描所有 ACTIVE+AUTO 指标，注册到 Quartz */
    @EventListener(ApplicationReadyEvent.class)
    public void syncOnStartup() { /* 见 6.2 */ }

    /** 注册或覆盖（CRUD afterCommit 调用） */
    public void register(PerfMetricDef def) { /* 见 6.3 */ }

    /** 注销（CRUD afterCommit 调用） */
    public void unregister(String metricCode) {
        jobApi.unregisterJob("PERF_METRIC_" + metricCode);
    }

    /** 是否需要调度（统一判定） */
    public boolean isSchedulable(PerfMetricDef def) {
        return "ACTIVE".equals(def.getStatus())
            && "AUTO".equals(def.getCalcMode())
            && (def.getDeleted() == null || def.getDeleted() == 0)
            && !"PROC".equals(def.getCalcLogicType())
            && !"SUMMARY".equals(def.getCalcLogicType());
    }
}
```

### 6.2 启动同步

```java
@EventListener(ApplicationReadyEvent.class)
public void syncOnStartup() {
    List<PerfMetricDef> metrics = metricDefService.listSchedulable();
    int success = 0, failed = 0;
    for (PerfMetricDef m : metrics) {
        try { register(m); success++; }
        catch (Exception e) {
            log.error("[MetricScheduler] sync 失败 metricCode={}", m.getMetricCode(), e);
            failed++;
        }
    }
    log.info("[MetricScheduler] 启动同步完成 success={} failed={}", success, failed);
}
```

### 6.3 register 实现

```java
public void register(PerfMetricDef def) {
    // EXPR/GROOVY + subject_sql 为空 → WARN 不抛
    if (("EXPR".equals(def.getCalcLogicType()) || "GROOVY".equals(def.getCalcLogicType()))
            && !StringUtils.hasText(def.getSubjectSql())) {
        log.warn("[MetricScheduler] metric={} EXPR/GROOVY 类型 subject_sql 为空，跳过注册",
            def.getMetricCode());
        return;
    }
    RegisterJobCmd cmd = new RegisterJobCmd();
    cmd.setJobKey("PERF_METRIC_" + def.getMetricCode());
    cmd.setJobName("指标 " + def.getMetricCode() + " 自动调度");
    cmd.setJobGroup("PERF_METRIC");
    cmd.setCronExpr(cronResolver.resolve(def));
    cmd.setQuartzJobClass(MetricExecuteQuartzJob.class.getName());
    cmd.setJobData(Map.of("metricCode", def.getMetricCode()));
    cmd.setMisfirePolicy("FIRE_ONCE_NOW");
    cmd.setAllowManualTrigger(true);
    jobApi.registerJob(cmd);
}
```

### 6.4 `MetricDefService` CRUD Hook 接入点

| 操作 | 状态变更 | 联动调用 |
|---|---|---|
| `create(metric)` | 新建 | `if (isSchedulable) afterCommit → register` |
| `update(metric)` | cron / freq / status / mode / subjectSql 任一变更 | 按变更后状态：`isSchedulable ? register : unregister` |
| `delete(metric)`（软删） | deleted=1 | `afterCommit → unregister` |
| `updateStatus(ACTIVE/DISABLED)` | 切换 status | 同 update |

**事务隔离原则**：`register/unregister` 必须在 commit 之后才能调用：

```java
TransactionSynchronizationManager.registerSynchronization(
    new TransactionSynchronization() {
        @Override public void afterCommit() {
            metricSchedulerService.register(def);
        }
    });
```

测试上下文（无活动事务）通过 `TransactionSynchronizationManager.isSynchronizationActive()` 判定，直接调用兜底。

### 6.5 失败补偿：`MetricSchedulerHealthCheck`

`@Scheduled(cron="0 */10 * * * ?")` 每 10 分钟扫"应注册但 sys_job_conf 缺失"的指标，重试 register。同时 ERROR 日志接监控告警。

> 注：本 HealthCheck 是 Spring `@Scheduled`，**不**走 Quartz；它的目的是补偿 Quartz 注册失败，自身不能依赖 Quartz。

## 7. `MetricExecuteQuartzJob` + EXPR/GROOVY 多主体执行链路

### 7.1 通用 `MetricExecuteQuartzJob` 包装类

```java
package com.bank.branch.platform.performance.job.quartz;

@Slf4j
public class MetricExecuteQuartzJob implements Job {

    @Autowired private MetricCalcService metricCalcService;
    @Autowired private SysControlService sysControlService;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        String metricCode = context.getMergedJobDataMap().getString("metricCode");
        if (!StringUtils.hasText(metricCode))
            throw new JobExecutionException("metricCode 未传入 JobDataMap", false);
        LocalDate dataDate = LocalDate.now().minusDays(1);
        String version = sysControlService.getActiveVersionOrFallback(dataDate);
        try {
            metricCalcService.calcMetric(metricCode, dataDate, version);
        } catch (Exception e) {
            log.error("[MetricExecute] metricCode={} 异常", metricCode, e);
            throw new JobExecutionException(e, false);
        }
    }
}
```

### 7.2 `SubjectFetcher`

```java
@Service
@RequiredArgsConstructor
public class SubjectFetcher {
    private final JdbcTemplate jdbcTemplate;
    private final SqlValidator sqlValidator;

    public List<String> fetch(String subjectSql, Map<String,Object> params) {
        if (!StringUtils.hasText(subjectSql))
            throw new PerfException(PerfErrorCode.METRIC_SUBJECT_SQL_REQUIRED);
        sqlValidator.validate(subjectSql);   // 复用现有黑名单
        try {
            List<String> keys = jdbcTemplate.queryForList(subjectSql, params, String.class);
            return keys.stream().distinct().toList();
        } catch (DataAccessException e) {
            throw new PerfException(PerfErrorCode.METRIC_SUBJECT_SQL_FAILED, e);
        }
    }
}
```

### 7.3 `MetricCalcService.executeGroovyAndPersist` 改写为多主体

```java
private SubjectStats executeGroovyAndPersist(PerfMetricDef def, LocalDate dataDate, String version) {
    if (!StringUtils.hasText(def.getExprText()))
        throw new PerfException(METRIC_CALC_LOGIC_INVALID, "EXPR 类型 exprText 为空: " + def.getMetricCode());

    // 1. 取主体集合
    List<String> subjects = subjectFetcher.fetch(def.getSubjectSql(),
        Map.of("dataDate", dataDate, "version", version));
    if (subjects.isEmpty()) {
        log.warn("[MetricCalc] metric={} 主体集合为空，跳过", def.getMetricCode());
        return SubjectStats.empty();
    }
    // 2. 解析引用指标 codes
    List<String> refCodes = parseRefMetricCodes(def.getRefMetricCodes());
    Duration timeout = resolveTimeout();
    int success = 0, failed = 0;
    List<String> failedSamples = new ArrayList<>();
    Map<String, BigDecimal> outputs = new HashMap<>();

    // 3. foreach 主体
    for (String subject : subjects) {
        try {
            Map<String,Object> vars = loadRefValues(subject, refCodes,
                def.getBaseDim(), dataDate, version);
            BigDecimal value = groovyExecutor.execute(def.getExprText(), vars, timeout);
            outputs.put(subject, value);
            success++;
        } catch (Exception perSubjectEx) {
            failed++;
            if (failedSamples.size() < 10) failedSamples.add(subject);
            log.warn("[MetricCalc] metric={} subject={} 失败: {}",
                def.getMetricCode(), subject, perSubjectEx.getMessage());
        }
    }
    // 4. 批量写宽表（仅成功部分）
    persistValues(def, outputs, dataDate, version);
    return new SubjectStats(subjects.size(), success, failed, failedSamples);
}
```

### 7.4 引用指标值喂入 `loadRefValues`

```java
private Map<String,Object> loadRefValues(String subject, List<String> refCodes,
                                          String baseDim, LocalDate dataDate, String version) {
    if (refCodes.isEmpty()) return Map.of();
    Map<String,BigDecimal> values = switch (baseDim) {
        case "EMP"  -> empIndexResultMapper.selectSlotValuesByCodes(subject, refCodes, dataDate, version);
        case "ORG"  -> orgIndexResultMapper.selectSlotValuesByCodes(subject, refCodes, dataDate, version);
        case "CUST" -> custIndexResultMapper.selectSlotValuesByCodes(subject, refCodes, dataDate, version);
        default -> throw new PerfException(PerfErrorCode.METRIC_CALC_LOGIC_INVALID,
            "未知 baseDim=" + baseDim);
    };
    // EXPR 中变量名 = 引用指标 code
    return new HashMap<>(values);
}
```

### 7.5 顶层 `calcMetric` 状态机

```java
public String calcMetric(String metricCode, LocalDate dataDate, String version) {
    // 加载定义、插入 PENDING、切 RUNNING（保留原逻辑）
    try {
        validateSlot(def);
        SubjectStats stats;
        if ("SQL".equals(logicType)) {
            stats = executeSqlAndPersist(def, dataDate, version);
            // SQL 类型整批要么成功要么失败抛异常
        } else if ("EXPR".equals(logicType) || "GROOVY".equals(logicType)) {
            stats = executeGroovyAndPersist(def, dataDate, version);
        } else if ("PROC".equals(logicType) || "SUMMARY".equals(logicType)) {
            throw new PerfException(CALC_JOB_FAILED, "PROC/SUMMARY 暂不支持自动调度");
        } else {
            throw new PerfException(METRIC_CALC_LOGIC_INVALID, "未知 calcLogicType=" + logicType);
        }
        String finalStatus =
              stats.failed() == 0 ? "SUCCESS"
            : stats.success() == 0 ? "FAILED"
            : "PARTIAL_FAILED";
        perfRunTaskMapper.updateStatusWithParams(taskId, finalStatus, null, stats.toJson());
        eventPublisher.publishEvent(new MetricCalcCompletedEvent(
            metricCode, def.getBaseDim(), dataDate, version,
            finalStatus, stats.total(), stats.success(), stats.failed(),
            taskId, "SCHEDULED", LocalDateTime.now()));
        return taskId;
    } catch (Exception ex) {
        markFailed(taskId, ex);
        // 失败事件：subjectStats 全 0，runStatus=FAILED，消费者侧据此跳过 KPI 联动
        eventPublisher.publishEvent(new MetricCalcCompletedEvent(
            metricCode, def == null ? null : def.getBaseDim(), dataDate, version,
            "FAILED", 0, 0, 0, taskId, "SCHEDULED", LocalDateTime.now()));
        throw new PerfException(CALC_JOB_FAILED, ex, ex.getMessage());
    }
}
```

### 7.6 引用指标值缺失退化策略

`loadRefValues` 读宽表读到 null：
- 视为 `BigDecimal.ZERO` 喂给 GroovyExecutor（与 V1.1 默认行为一致）
- 除法表达式 `a/b` 在 b=0 时抛 ArithmeticException → 该 subject 进入 failed 计数
- 这是可接受的——属于"配置时序问题"，运维通过 `failedSamples` 看到并修复

### 7.7 PROC / SUMMARY 类型与 AUTO 模式

`MetricSchedulerService.isSchedulable` 已显式排除：

```java
if ("PROC".equals(def.getCalcLogicType()) || "SUMMARY".equals(def.getCalcLogicType())) {
    log.warn("[MetricScheduler] metric={} calcLogicType={} 暂不支持自动调度，跳过注册",
        def.getMetricCode(), def.getCalcLogicType());
    return false;
}
```

## 8. KPI 事件驱动重算

### 8.1 触发模型

```
MetricCalcService.calcMetric          ApplicationEventPublisher
  ├─ 写 perf_run_task 终态                    │
  └─ publishEvent(MetricCalcCompletedEvent)  ─┤
                                              ▼
                          KpiCascadeListener.onMetricCompleted
                            (@TransactionalEventListener AFTER_COMMIT, @Async)
                                              │
                            1. 反查 perf_kpi_item 找出引用 metricCode 的 ACTIVE 方案
                            2. 对每个方案：按 cycle_type 推导 cycleDate
                            3. Redis SETNX 防重（30 秒窗口）
                            4. 调 KpiCalcService.calcScheme(...)
                            5. 发布 KpiCalcCompletedEvent（已有，V1.2 引入）
```

### 8.2 `KpiCascadeListener` 实现

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class KpiCascadeListener {

    private final PerfKpiItemMapper kpiItemMapper;
    private final KpiSchemeService kpiSchemeService;
    private final KpiCalcService kpiCalcService;
    private final RedisTemplate<String,String> redisTemplate;

    @Async("kpiCascadeExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMetricCompleted(MetricCalcCompletedEvent event) {
        if ("FAILED".equals(event.runStatus())) {
            log.info("[KpiCascade] metric={} 状态 FAILED，不触发 KPI", event.metricCode());
            return;
        }
        List<String> schemeIds = kpiItemMapper.selectActiveSchemeIdsByMetric(event.metricCode());
        for (String schemeId : schemeIds) {
            try { triggerScheme(schemeId, event); }
            catch (Exception e) {
                log.warn("[KpiCascade] schemeId={} metric={} 触发失败",
                    schemeId, event.metricCode(), e);
            }
        }
    }

    private void triggerScheme(String schemeId, MetricCalcCompletedEvent event) {
        PerfKpiScheme scheme = kpiSchemeService.getById(schemeId);
        if (scheme == null || !"ACTIVE".equals(scheme.getStatus())) return;
        LocalDate cycleDate = resolveCycleDate(scheme.getCycleType(), event.dataDate());
        String lockKey = String.format("kpi:cascade:%s:%s:%s",
            scheme.getSchemeCode(), cycleDate, event.version());
        Boolean acquired = redisTemplate.opsForValue()
            .setIfAbsent(lockKey, "1", Duration.ofSeconds(30));
        if (!Boolean.TRUE.equals(acquired)) {
            log.debug("[KpiCascade] 30s 内已触发过 lockKey={}, 跳过", lockKey);
            return;
        }
        int success = kpiCalcService.calcScheme(
            scheme.getSchemeCode(), scheme.getCycleType(),
            cycleDate, event.dataDate(), event.version());
        log.info("[KpiCascade] schemeCode={} cycleType={} cycleDate={} success={}",
            scheme.getSchemeCode(), scheme.getCycleType(), cycleDate, success);
    }

    private LocalDate resolveCycleDate(String cycleType, LocalDate dataDate) {
        return switch (cycleType) {
            case "MONTHLY"   -> dataDate.withDayOfMonth(1);
            case "QUARTERLY" -> dataDate.with(IsoFields.DAY_OF_QUARTER, 1L);
            case "YEARLY"    -> dataDate.withDayOfYear(1);
            case "WEEKLY"    -> dataDate.with(DayOfWeek.MONDAY);
            default -> throw new PerfException(KPI_CYCLE_TYPE_INVALID, cycleType);
        };
    }
}
```

### 8.3 反查 mapper

```xml
<select id="selectActiveSchemeIdsByMetric" parameterType="string" resultType="string">
    SELECT DISTINCT i.scheme_id
    FROM perf_kpi_item i
    INNER JOIN perf_kpi_scheme s ON i.scheme_id = s.id
    WHERE i.metric_code = #{metricCode}
      AND s.status = 'ACTIVE'
      AND (s.deleted IS NULL OR s.deleted = 0)
</select>
```

### 8.4 滚动重算策略（不等"全齐"）

不做"全部依赖指标都齐了再触发"判定：
- 等齐意味着季度末 M3 跑完前 KPI 评分始终为旧值
- 业务上更希望**滚动重算**：每天 M1 算完就用最新 M1 + 旧 M2/M3 重算 → 逐步逼近完整数据
- `KpiCalcService.calcScheme` 内部对缺失指标值已有兜底（V1.5 mom/yoy null 处理）

**Redis SETNX 30 秒窗口防重**：
- 同一 dataDate+version 短时间内多个事件涌入 → 只跑首次
- 30 秒后过期 → 允许"次日同 schemeCode 再次正常调度"
- 锁失败不抛异常（debug log）

### 8.5 异步线程池

```java
@Configuration
public class KpiCascadeAsyncConfig {
    @Bean("kpiCascadeExecutor")
    public TaskExecutor kpiCascadeExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(2);
        exec.setMaxPoolSize(4);
        exec.setQueueCapacity(200);
        exec.setThreadNamePrefix("kpi-cascade-");
        exec.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        exec.initialize();
        return exec;
    }
}
```

需在 `PerformanceAutoConfiguration` 加 `@EnableAsync`（如尚未启用）。

### 8.6 删除 `DailyKpiCalcJob` 的迁移影响

| 资产 | 处理 |
|---|---|
| `DailyKpiCalcJob.java` | 删除 |
| `DailyKpiCalcQuartzJob.java` | 删除 |
| `DailyKpiCalcJobTest.java` / `DailyKpiCalcQuartzJobTest.java` | 删除 |
| sys_job_conf 中 `jobKey=PERF_DAILY_KPI_CALC` 行 | Flyway `V1_7_1` DELETE |
| 模块 CLAUDE.md "3 个定时任务调度"章节 | 改为"2 个固定 + N 个指标级动态" |
| `NoOldDailyKpiCalcArchTest`（架构守护） | 新增：代码库不再出现该字面量 |

### 8.7 失败 / 退化场景

| 场景 | 行为 |
|---|---|
| Redis 不可用 | `setIfAbsent` 抛异常 → catch → 退化为不防重；KpiCalcService 内部已 `@Transactional`+幂等覆盖 |
| `KpiCalcService.calcScheme` 抛异常 | 单方案 catch + warn 日志，不影响其他方案 |
| 事件队列满 | `CallerRunsPolicy` 由发布线程同步执行 → 短时延迟 MetricExecuteQuartzJob 完成时间，但不丢事件 |

## 9. 测试矩阵

| 层级 | 测试类 | 关键 case | 类型 |
|---|---|---|---|
| **DDL** | `V1_7_0FlywayIT` | 3 列存在性 + 索引存在性 + undo 反向 DROP 后还能正向重跑 | failsafe IT |
| **枚举** | `CalcFreqEnumTest` | 5 取值 + isValid + 不区分大小写 | surefire UT |
| **Cron 推导** | `MetricCronResolverTest` | 5 freq 默认值 + cron_expr 优先 + 非法 freq 抛异常 | surefire UT |
| **JobApi** | `JobApiRegisterTest` + `JobApiUnregisterTest` | upsert + 幂等注销 + 非法 cron / class 失败 + scheduler null 分支 | surefire UT + failsafe IT |
| **MetricSchedulerService** | `MetricSchedulerServiceTest` | isSchedulable 6 分支 + register/unregister 委托 + afterCommit 回调 + 启动同步成功/失败统计 + PROC/SUMMARY 跳过 | surefire UT |
| **SubjectFetcher** | `SubjectFetcherTest` | 正常 100 行 + 空集合 + SQL 黑名单拦截 + DataAccessException 包装 | surefire UT |
| **MetricCalcService EXPR 多主体** | `MetricCalcServiceMultiSubjectTest` | 100 全成功(SUCCESS) + 5 失败(PARTIAL_FAILED) + 100 全失败(FAILED) + 主体集合空(SUCCESS+跳过) + failedSamples 截断到 10 个 | surefire UT |
| **MetricExecuteQuartzJob** | `MetricExecuteQuartzJobTest` | metricCode 取自 JobDataMap + 缺失抛异常 + JobExecutionException 包装 | surefire UT |
| **KpiCascadeListener** | `KpiCascadeListenerTest` | metricCode→schemes 反查 + cycleDate 4 类型推导 + Redis SETNX 防重 + FAILED 事件不触发 + 单方案异常隔离 | surefire UT |
| **KpiCascadeAsync** | `KpiCascadeAsyncIT` | 真实 @Async + 真实 Redis（Testcontainers）+ AFTER_COMMIT 时序验证 | failsafe IT |
| **CRUD 联动** | `MetricDefServiceScheduleHookIT` | create ACTIVE+AUTO → register / update 改状态 → unregister or register / 软删 → unregister / 事务回滚不触发注册 | failsafe IT |
| **Health Check 补偿** | `MetricSchedulerHealthCheckTest` | sys_job_conf 缺失的指标被检出并重注册 | surefire UT |
| **架构守护** | `NoOldDailyKpiCalcArchTest` | 代码库不再出现 `DailyKpiCalcJob` / `DailyKpiCalcQuartzJob` 字面量 | surefire UT |
| **端到端** | `MetricScheduledE2EIT` | 注册指标 → JobDataMap 驱动 Quartz 触发 → 写宽表 → 发事件 → KPI 重算 → sys_job_run_log + perf_run_task 双写 | failsafe IT |

## 10. 迁移步骤（TDD 节奏）

| Phase | 内容 | 提交粒度 |
|---|---|---|
| **P0 准备** | DDL Flyway V1_7_0 + undo + IT | TDD 1 cycle |
| **P1 governance JobApi 扩展** | RegisterJobCmd + JobApi.registerJob/unregisterJob + JobService 实现 + 错误码 | TDD 4 commit |
| **P2 cron 推导 + 枚举** | CalcFreqEnum + MetricCronResolver | TDD 2 commit |
| **P3 SubjectFetcher** | 主体取数（含 SQL 黑名单复用） | TDD 1 commit |
| **P4 MetricCalcService EXPR 多主体** | executeGroovyAndPersist 改写 + SubjectStats 返回 + status PARTIAL_FAILED | TDD 3 commit |
| **P5 MetricExecuteQuartzJob** | 通用包装类 + JobDataMap 协议 | TDD 1 commit |
| **P6 MetricSchedulerService** | isSchedulable + register/unregister + 启动同步 + Health Check + CRUD Hook | TDD 5 commit |
| **P7 MetricCalcCompletedEvent + KpiCascadeListener** | 事件 + 监听器 + 异步线程池 + Redis 防重 | TDD 3 commit |
| **P8 删除 DailyKpiCalcJob 残留** | 删除 4 类 + Flyway V1_7_1 + ArchTest 守护 | TDD 2 commit |
| **P9 文档同步** | 模块 CLAUDE.md / docs/modules/performance-engine-center/02-后端架构 / 05-DDL / Runbook | doc commit |
| **P10 全量回归** | `mvn clean install -DskipTests` + `mvn verify` 期望全绿 | smoke commit |

预计总 commit 数：~22-25。

## 11. 风险点与应对

| 风险 | 影响 | 应对 |
|---|---|---|
| **存量 ACTIVE+AUTO 指标未配 cron_expr 也未配 subject_sql** | 启动同步时 EXPR 类型因 subject_sql 为空而 register 失败 | 启动同步对 EXPR + subject_sql 空仅 WARN 不抛；运维通过 sys_job_conf 缺失列表手工补 |
| **指标级 Quartz Job 数量膨胀** | 200 指标 → QRTZ_TRIGGERS / QRTZ_FIRED_TRIGGERS 行数显著增加 | (a) JobController 列表加"PERF_METRIC 组数量"显示；(b) DBA 评估 QRTZ_* 表索引（已有），200 行级别影响极小 |
| **指标 cron 集中在凌晨 2 点导致 DB 瞬时压力** | 100 指标同时跑 SQL，连接池打满 | (a) MetricExecuteQuartzJob 加随机抖动 0-60s（可选）；(b) 鼓励用户在 cron_expr 错峰；(c) Quartz 线程池 `org.quartz.threadPool.threadCount=10`（默认）限制并发 |
| **EXPR 引用指标依赖时序问题** | A 引用 B，A 的 cron 比 B 早 → A 失败 | MVP 不引入拓扑排序；register 时校验"被引用指标 cron ≤ 当前指标 cron"，否则 register 成功但 ERROR 日志提示 |
| **删除 DailyKpiCalcJob 后存量 KPI 方案永不被触发** | 没有指标变更的 KPI 方案永远不重算 | (a) `KpiSchemeService.publish` 时主动触发一次 calcScheme；(b) Runbook 提示：导入存量数据后手工调 `/api/perf/kpi/trigger` |
| **Quartz JDBC JobStore 与业务 DB 同库** | 高频触发竞争连接 | 与 V1.6 一致，未变更；Druid 池大小已配 |
| **测试上下文 scheduler==null** | MetricSchedulerService 启动同步与 CRUD 联动跳过 register | 已有兜底；测试断言 sys_job_conf 写入即可 |

## 12. Runbook（生产启用）

```bash
# 1. 部署前预检（DBA 执行）
SELECT COUNT(*) FROM perf_metric_def WHERE status='ACTIVE' AND calc_mode='AUTO';
SELECT COUNT(*) FROM perf_metric_def WHERE status='ACTIVE' AND calc_mode='AUTO'
  AND calc_logic_type IN ('EXPR','GROOVY') AND (subject_sql IS NULL OR subject_sql='');
# 第 2 个查询结果必须是 0，否则启动后这些指标会 WARN 不注册

# 2. Flyway 迁移
mvn -pl performance-engine-center flyway:migrate
# V1_7_0__perf_metric_def_schedule_cols.sql
# V1_7_1__remove_daily_kpi_calc_job.sql

# 3. 部署应用，观察启动日志
grep "MetricScheduler\] 启动同步完成" application.log
# 期望: success=N failed=0

# 4. 验证 sys_job_conf 写入
SELECT job_key, cron_expr, status FROM sys_job_conf
WHERE job_key LIKE 'PERF_METRIC_%' ORDER BY job_key;

# 5. 验证 Quartz 已接管
SELECT TRIGGER_NAME, TRIGGER_GROUP, NEXT_FIRE_TIME
FROM QRTZ_TRIGGERS WHERE TRIGGER_GROUP='PERF_METRIC';

# 6. 等首次触发后验证日志双写
SELECT * FROM sys_job_run_log WHERE job_id IN
  (SELECT id FROM sys_job_conf WHERE job_key LIKE 'PERF_METRIC_%')
  ORDER BY start_time DESC LIMIT 10;
SELECT * FROM perf_run_task WHERE task_type='METRIC_RUN'
  ORDER BY created_time DESC LIMIT 10;

# 7. 紧急停止某指标
UPDATE sys_job_conf SET status='PAUSED' WHERE job_key='PERF_METRIC_M_DEPOSIT_DAILY';
# 或 REST: POST /api/admin/sys/jobs/{jobId}/pause

# 8. 回滚
mvn -pl performance-engine-center flyway:undo  # 反向 V1_7_1 + V1_7_0
# 应用回滚到 V1.6 jar 重启
```

## 13. 后续可选增强（不在 V1.7 范围）

1. **依赖拓扑排序**：根据指标的 `ref_metric_codes` 计算 DAG，调度时严格按拓扑顺序触发，避免 cron 错峰人工配置
2. **PROC / SUMMARY 类型支持**：当前 V1.7 跳过这两种类型，未来可扩展 `MetricCalcService` 增加对应分支
3. **指标级 Job 优先级**：基于 KPI 方案的业务重要性给 cron 分配 Quartz priority
4. **失败重试**：MetricExecuteQuartzJob 抛 JobExecutionException 时通过 Quartz `setRefireImmediately(true)` 立即重试 1 次
5. **JobController UI 强化**：按 metricCode 反查 KPI 方案 + 一键手动触发指标级 Job

---

**末尾**：本 spec 完成 brainstorming 阶段。下一步进入 `writing-plans` 生成详细实现计划。
