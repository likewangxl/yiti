# V1.8 — Customer Lead 回调补偿 Quartz 迁移实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 `LeadCallbackCompensationService` 从 Spring `@Scheduled` 迁移到 Quartz 集群调度，使整个仓库的业务定时任务由 `sys_job_conf` + Quartz 统一管理。

**Architecture:** 沿用 V1.6/V1.7 已建立的 pattern——新增 `LeadCallbackCompensateQuartzJob`（实现 `org.quartz.Job`）+ `sys_job_conf` 数据行（`V1_8_0` SQL）+ governance `JobService.syncOnStartup` 启动期自动 schedule 到 Quartz Scheduler。同时把 `@EnableScheduling` 配置从 customer 归还给 performance 模块。

**Tech Stack:** Spring Boot 3.2.3 + JDK 17 + MyBatis 3.0.3 + Quartz 2.3.2 (JDBC JobStore, cluster mode) + AssertJ + Mockito + ArchUnit + JUnit 5

**对应 Spec:** `docs/superpowers/specs/2026-05-01-customer-lead-callback-quartz-migration-design.md`

**Git baseline:** `5438b1d`（master，spec commit 之后）

**Branch policy:** 直接 master，每个 Phase 末尾 commit + push（与 V1.7 节奏一致；改动小不需要 feature 分支）

---

## File Structure

| 文件 | 操作 | Phase |
|---|---|---|
| `customer-marketing-center/src/main/resources/sql/V1_8_0__register_lead_callback_compensate_job.sql` | 新建 | P1 |
| `customer-marketing-center/src/main/resources/sql/U1_8_0__remove_lead_callback_compensate_job.sql` | 新建 | P1 |
| `bootstrap/src/test/resources/data.sql` | 修改 | P1 |
| `customer-marketing-center/src/main/java/com/bank/branch/platform/customer/job/quartz/LeadCallbackCompensateQuartzJob.java` | 新建 | P2 |
| `customer-marketing-center/src/test/java/com/bank/branch/platform/customer/job/quartz/LeadCallbackCompensateQuartzJobTest.java` | 新建 | P2 |
| `customer-marketing-center/src/test/java/com/bank/branch/platform/customer/arch/NoCustomerScheduledArchTest.java` | 新建 | P3 |
| `customer-marketing-center/src/main/java/com/bank/branch/platform/customer/service/LeadCallbackCompensationService.java` | 修改（删 `@Scheduled` 入口 + 更新 javadoc） | P3 |
| `performance-engine-center/src/main/java/com/bank/branch/platform/performance/config/PerformanceSchedulingConfig.java` | 新建 | P4 |
| `customer-marketing-center/src/main/java/com/bank/branch/platform/customer/config/CustomerSchedulingConfig.java` | 删除 | P4 |
| `bootstrap/src/test/java/com/bank/branch/platform/it/LeadCallbackJobRegisteredIT.java` | 新建 | P5 |
| `CLAUDE.md`（root） | 修改（V1.8 状态） | P7 |
| `.claude/CLAUDE.md` | 修改（V1.8 状态） | P7 |
| `customer-marketing-center/CLAUDE.md` | 修改 | P7 |
| `performance-engine-center/CLAUDE.md` | 修改 | P7 |

---

## Phase 0 · 准备 baseline 验证

**目的：** 确认 master 干净 + 现有测试全绿，作为对比基线。

### Task 0.1: 验证 git baseline

- [ ] **Step 1: 检查 git 状态**

```bash
git status
git log --oneline -3
```

Expected:
- working tree clean
- HEAD == `5438b1d`（spec commit）

- [ ] **Step 2: 跑全量 surefire 验证 baseline 全绿**

```bash
mvn clean install -DskipTests
```

Expected: BUILD SUCCESS

```bash
mvn test
```

Expected: BUILD SUCCESS, 0 failures, 0 errors. 记录测试总数（用作 P6 对比）。

> 若 baseline 已红，**必须先停下来定位**（不应继续在红色基线上做改动）。

---

## Phase 1 · DDL / SQL 脚本

**目的：** 建立 Quartz job 的 sys_job_conf 数据基础。

### Task 1.1: V1_8_0 迁移脚本

**Files:**
- Create: `customer-marketing-center/src/main/resources/sql/V1_8_0__register_lead_callback_compensate_job.sql`

- [ ] **Step 1: 写 V1_8_0 SQL**

```sql
-- V1.8 注册 Lead 回调补偿 Quartz Job
-- 引入版本：V1.8（2026-05-01）
-- 由 Spring @Scheduled(fixedDelay 5min) 迁移而来
-- 此脚本需手动执行；非 Flyway 自动迁移（项目无 Flyway 自动加载配置）

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

- [ ] **Step 2: 校验文件**

```bash
ls -la customer-marketing-center/src/main/resources/sql/V1_8_0__register_lead_callback_compensate_job.sql
```

Expected: 文件存在，且与已有 V1_7_0 同目录。

### Task 1.2: U1_8_0 撤销脚本

**Files:**
- Create: `customer-marketing-center/src/main/resources/sql/U1_8_0__remove_lead_callback_compensate_job.sql`

- [ ] **Step 1: 写 U1_8_0 SQL**

```sql
-- V1.8 撤销脚本：删除 Lead 回调补偿 Quartz Job
-- 引入版本：V1.8（2026-05-01）
-- 注：仅 DELETE sys_job_conf 行；Quartz Scheduler 中残留 trigger 由运维或重启清理
DELETE FROM SYS_JOB_CONF WHERE job_key = 'LEAD_CALLBACK_COMPENSATE';
```

- [ ] **Step 2: 校验文件**

```bash
ls -la customer-marketing-center/src/main/resources/sql/U1_8_0__remove_lead_callback_compensate_job.sql
```

Expected: 文件存在。

### Task 1.3: bootstrap 测试 seed 数据同步

**Files:**
- Modify: `bootstrap/src/test/resources/data.sql`

- [ ] **Step 1: 先读现有 data.sql，定位 sys_job_conf INSERT 块**

```bash
grep -n "sys_job_conf" bootstrap/src/test/resources/data.sql
```

Expected: 命中行号 ≈ 90。读取该 INSERT IGNORE 块的当前内容，理解列顺序与 VALUES 行格式。

- [ ] **Step 2: 在该 INSERT IGNORE 块的最后一个 VALUES 行追加 LEAD_CALLBACK_COMPENSATE 行**

注意点：
1. 必须保持与现有列顺序完全一致；
2. 用 INSERT IGNORE 而非 INSERT（沿用 V1.6 既有惯例，避免 PK 冲突）；
3. 行末用 `;` 结束（如果之前最后一行是 `,` 则改为 `,` + 新行 + `;`）。

要追加的行（具体格式按现有列顺序对齐）：

```sql
('JOB_LEAD_CALLBACK_COMPENSATE', 'LEAD_CALLBACK_COMPENSATE', 'Lead 回调补偿巡检',
 '0 */5 * * * ?', 'ACTIVE', 1,
 'com.bank.branch.platform.customer.job.quartz.LeadCallbackCompensateQuartzJob', 'DO_NOTHING')
```

> 如果现有 data.sql 的 INSERT 块**只有 6 列**（id/job_key/job_name/cron_expr/status/allow_manual_trigger）而**没有 quartz_job_class/misfire_policy 列**，需要先把现有 INSERT 块升级为 8 列形式（增加最后两列），并把已有 row 的这两列也填上对应值（参考 docs/superpowers/sql/backup/2026-04-27-pre-fu1415-import-backup.sql）。
>
> 升级时注意：sys_job_conf 表的 DDL 中 quartz_job_class/misfire_policy 列在 V1.6 已加。

- [ ] **Step 3: 跑 bootstrap surefire 校验 data.sql 语法正确**

```bash
mvn test -pl bootstrap -DskipTests=false
```

Expected: BUILD SUCCESS（如果有任何 IT 触发 data.sql 加载失败，会立刻在此 phase 暴露）。

### Task 1.4: Phase 1 commit

- [ ] **Step 1: 提交**

```bash
git add customer-marketing-center/src/main/resources/sql/V1_8_0__register_lead_callback_compensate_job.sql \
        customer-marketing-center/src/main/resources/sql/U1_8_0__remove_lead_callback_compensate_job.sql \
        bootstrap/src/test/resources/data.sql

git commit -m "$(cat <<'EOF'
feat(customer): V1.8 P1 sys_job_conf 注册 LEAD_CALLBACK_COMPENSATE

- V1_8_0__register_lead_callback_compensate_job.sql：新增 sys_job_conf
  行 JOB_LEAD_CALLBACK_COMPENSATE，cron='0 */5 * * * ?'，misfire=DO_NOTHING
- U1_8_0__remove_lead_callback_compensate_job.sql：撤销脚本
- bootstrap/src/test/resources/data.sql：测试 seed 同步新行
EOF
)"
git push origin master
```

---

## Phase 2 · Quartz Job 类（TDD 红绿重构）

**目的：** 新增 `LeadCallbackCompensateQuartzJob` Quartz Job 类。

### Task 2.1: 写失败测试（RED）

**Files:**
- Create: `customer-marketing-center/src/test/java/com/bank/branch/platform/customer/job/quartz/LeadCallbackCompensateQuartzJobTest.java`

- [ ] **Step 1: 写测试代码**

```java
package com.bank.branch.platform.customer.job.quartz;

import com.bank.branch.platform.customer.service.LeadCallbackCompensationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobKey;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * V1.8 — {@link LeadCallbackCompensateQuartzJob} 单元测试。
 * 验证 Quartz 入口顶层 try-catch 兜底语义保留（与原 scheduledScan() 一致）。
 */
class LeadCallbackCompensateQuartzJobTest {

    private LeadCallbackCompensationService compensationService;
    private LeadCallbackCompensateQuartzJob job;
    private JobExecutionContext context;

    @BeforeEach
    void setUp() {
        compensationService = mock(LeadCallbackCompensationService.class);
        job = new LeadCallbackCompensateQuartzJob(compensationService);

        context = mock(JobExecutionContext.class);
        JobDetail jobDetail = mock(JobDetail.class);
        when(context.getJobDetail()).thenReturn(jobDetail);
        when(jobDetail.getKey()).thenReturn(JobKey.jobKey("LEAD_CALLBACK_COMPENSATE", "DEFAULT"));
    }

    @Test
    @DisplayName("正常路径：execute 调用 service.scanAndCompensate 一次")
    void execute_callsScanAndCompensate_normalPath() throws Exception {
        job.execute(context);

        verify(compensationService, times(1)).scanAndCompensate();
    }

    @Test
    @DisplayName("异常兜底：service 抛出 RuntimeException 时 execute 不冒泡")
    void execute_swallowsServiceException_doesNotThrow() {
        doThrow(new RuntimeException("simulated lead compensation failure"))
                .when(compensationService).scanAndCompensate();

        // 顶层 try-catch 必须吞掉异常，避免 Quartz scheduler 把 trigger 标记为 ERROR
        assertThatCode(() -> job.execute(context)).doesNotThrowAnyException();

        verify(compensationService, times(1)).scanAndCompensate();
    }
}
```

- [ ] **Step 2: 跑测试验证 FAIL**

```bash
mvn test -pl customer-marketing-center -Dtest=LeadCallbackCompensateQuartzJobTest
```

Expected: 编译失败，错误信息为 `cannot find symbol: class LeadCallbackCompensateQuartzJob`。这是预期的 RED。

### Task 2.2: 实现 Quartz Job 类（GREEN）

**Files:**
- Create: `customer-marketing-center/src/main/java/com/bank/branch/platform/customer/job/quartz/LeadCallbackCompensateQuartzJob.java`

- [ ] **Step 1: 写实现代码**

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
 * <strong>异常处理</strong>：原 scheduledScan() 顶层 try-catch 移到本类——
 * 补偿任务异常不应冒泡到 Quartz 调度器导致 trigger 被禁用。
 * </p>
 *
 * @see LeadCallbackCompensationService#scanAndCompensate()
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
            // JobExecutionLogger（governance V1.6 全局监听器）会记录 vetoed/error 状态。
            log.error("[LeadCallbackCompensateQuartzJob] {} 执行异常", jobKey, e);
        } finally {
            log.info("[LeadCallbackCompensateQuartzJob] {} 完成，耗时 {}ms",
                    jobKey, System.currentTimeMillis() - start);
        }
    }
}
```

- [ ] **Step 2: 跑测试验证 PASS**

```bash
mvn test -pl customer-marketing-center -Dtest=LeadCallbackCompensateQuartzJobTest
```

Expected: BUILD SUCCESS, Tests: 2, Failures: 0, Errors: 0.

### Task 2.3: 跑 customer-marketing-center 全量 surefire（无回归）

- [ ] **Step 1: 跑全量**

```bash
mvn test -pl customer-marketing-center
```

Expected: BUILD SUCCESS, 0 failures。新增的 2 个 case 进入总数。

### Task 2.4: Phase 2 commit

- [ ] **Step 1: 提交**

```bash
git add customer-marketing-center/src/main/java/com/bank/branch/platform/customer/job/quartz/LeadCallbackCompensateQuartzJob.java \
        customer-marketing-center/src/test/java/com/bank/branch/platform/customer/job/quartz/LeadCallbackCompensateQuartzJobTest.java

git commit -m "$(cat <<'EOF'
feat(customer): V1.8 P2 LeadCallbackCompensateQuartzJob 新增

- 实现 org.quartz.Job：execute() 调 service.scanAndCompensate()
- 顶层 try-catch 兜底（移植自原 @Scheduled scheduledScan() 语义）
- @Component + @RequiredArgsConstructor，由 AutowiringSpringBeanJobFactory
  注入 LeadCallbackCompensationService
- 单测 2 case：normal path + 异常兜底
EOF
)"
git push origin master
```

---

## Phase 3 · 删除旧 @Scheduled + ArchUnit 守护（TDD 红绿）

**目的：** 删除 Spring `@Scheduled` 入口，由 ArchUnit 守护防回退。

### Task 3.1: 写 ArchUnit 守护测试（RED — 因为 @Scheduled 还在）

**Files:**
- Create: `customer-marketing-center/src/test/java/com/bank/branch/platform/customer/arch/NoCustomerScheduledArchTest.java`

- [ ] **Step 1: 检查 customer-marketing-center 是否已引入 archunit 依赖**

```bash
grep -A2 "archunit" customer-marketing-center/pom.xml
```

Expected:
- 若已存在 archunit-junit5 依赖，跳过到 Step 2；
- 若不存在，**在 customer-marketing-center/pom.xml 的 dependencies 块加入**（test scope）：

```xml
<dependency>
    <groupId>com.tngtech.archunit</groupId>
    <artifactId>archunit-junit5</artifactId>
    <scope>test</scope>
</dependency>
```

> 版本号在 root pom 已声明（V1.7 NoOldDailyKpiCalcArchTest 已用），子模块只 import 即可。验证 root pom：

```bash
grep -B1 -A2 "archunit" pom.xml
```

确认 dependencyManagement 中已有版本约束。

- [ ] **Step 2: 写 ArchUnit 测试代码**

```java
package com.bank.branch.platform.customer.arch;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

/**
 * V1.8 — customer-marketing-center 模块 ArchUnit 守护：禁止 @Scheduled。
 * <p>
 * V1.8 起业务定时调度全部走 Quartz（job_key=LEAD_CALLBACK_COMPENSATE
 * 由 sys_job_conf 注册），customer 模块不应再出现 Spring {@code @Scheduled}。
 * </p>
 * <p>
 * <strong>注</strong>：若未来 customer 模块出现合理的 {@code @Scheduled}
 * 需求，应当先评估是否真的合理（例如纯本地兜底巡检），并在评估通过后
 * 调整本规则；不允许通过本测试静默回退。
 * </p>
 */
@AnalyzeClasses(
        packages = "com.bank.branch.platform.customer",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class NoCustomerScheduledArchTest {

    @ArchTest
    static final ArchRule no_spring_scheduled =
            noMethods()
                    .should()
                    .beAnnotatedWith("org.springframework.scheduling.annotation.Scheduled")
                    .as("V1.8 起 customer 模块禁止 @Scheduled —— 业务调度走 Quartz")
                    .allowEmptyShould(true);
}
```

- [ ] **Step 3: 跑测试验证 FAIL**

```bash
mvn test -pl customer-marketing-center -Dtest=NoCustomerScheduledArchTest
```

Expected: FAIL, error like `Architecture Violation: Method 'LeadCallbackCompensationService.scheduledScan()' is annotated with @Scheduled`. 这是预期的 RED。

### Task 3.2: 删除 @Scheduled 入口（GREEN）

**Files:**
- Modify: `customer-marketing-center/src/main/java/com/bank/branch/platform/customer/service/LeadCallbackCompensationService.java`

- [ ] **Step 1: 删除 import 和 scheduledScan() 方法**

删除：
- import 行 `import org.springframework.scheduling.annotation.Scheduled;`（line 10）
- 整个 `scheduledScan()` 方法（含 javadoc，line 197-212）

```java
// 删除 import:
import org.springframework.scheduling.annotation.Scheduled;

// 删除整段方法（带前置 javadoc）:
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

- [ ] **Step 2: 更新类级 javadoc**

定位 line 27 附近 "本 Service 职责" 段落，把：

```
 * <strong>本 Service 职责</strong>：以 5 分钟为粒度（{@code fixedDelay} 可配）
 * 巡检 stuck IN_APPROVAL leads（超过 stuckThresholdMinutes 未推进），
```

改为：

```
 * <strong>本 Service 职责</strong>：由 Quartz job
 * {@code LeadCallbackCompensateQuartzJob}（V1.8 起，
 * job_key={@code LEAD_CALLBACK_COMPENSATE}，cron 默认 5 分钟）
 * 巡检 stuck IN_APPROVAL leads（超过 stuckThresholdMinutes 未推进），
```

- [ ] **Step 3: 验证 ArchUnit 测试 PASS**

```bash
mvn test -pl customer-marketing-center -Dtest=NoCustomerScheduledArchTest
```

Expected: BUILD SUCCESS, Tests: 1, Failures: 0.

- [ ] **Step 4: 验证既有 LeadCallbackCompensationIT 业务逻辑仍 pass**

需要先 install 上游模块到本地 m2（CLAUDE.md 测试章节明确要求）：

```bash
mvn clean install -DskipTests
```

Expected: BUILD SUCCESS。

```bash
mvn verify -pl bootstrap -Dit.test=LeadCallbackCompensationIT
```

Expected: BUILD SUCCESS, IT 全绿。证明删除调度入口后，业务逻辑（直接调 `scanAndCompensate()` 那条路径）零影响。

### Task 3.3: 跑 customer-marketing-center 全量 surefire

- [ ] **Step 1: 跑全量**

```bash
mvn test -pl customer-marketing-center
```

Expected: BUILD SUCCESS, 0 failures。新增 ArchUnit case 进入总数。

### Task 3.4: Phase 3 commit

- [ ] **Step 1: 提交**

```bash
git add customer-marketing-center/src/main/java/com/bank/branch/platform/customer/service/LeadCallbackCompensationService.java \
        customer-marketing-center/src/test/java/com/bank/branch/platform/customer/arch/NoCustomerScheduledArchTest.java \
        customer-marketing-center/pom.xml

git commit -m "$(cat <<'EOF'
refactor(customer): V1.8 P3 删除 LeadCallbackCompensationService.@Scheduled

- 删除 scheduledScan() 方法（含顶层 try-catch，已迁到 LeadCallbackCompensateQuartzJob）
- 删除 @Scheduled / @Value(interval-ms) 引用，导入清理
- 更新类级 javadoc：明确由 Quartz job 调度（job_key=LEAD_CALLBACK_COMPENSATE）
- ArchUnit NoCustomerScheduledArchTest 守护：customer 模块禁 @Scheduled
- 既有 LeadCallbackCompensationIT 零改动（直接调 scanAndCompensate）
EOF
)"
git push origin master
```

> 若 pom.xml 未改动（archunit 已存在），从 git add 列表删除 pom.xml。

---

## Phase 4 · 模块归属调整

**目的：** 删除 customer 的 `CustomerSchedulingConfig`，新建 performance 的 `PerformanceSchedulingConfig` 接管 `@EnableScheduling`。

### Task 4.1: 新建 PerformanceSchedulingConfig

**Files:**
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/config/PerformanceSchedulingConfig.java`

- [ ] **Step 1: 写代码**

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
 * <strong>当前服务于</strong>：
 * {@link com.bank.branch.platform.performance.service.MetricSchedulerHealthCheck}
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

- [ ] **Step 2: 跑 performance 全量 surefire**

```bash
mvn test -pl performance-engine-center
```

Expected: BUILD SUCCESS, 0 failures。

### Task 4.2: 删除 CustomerSchedulingConfig

**Files:**
- Delete: `customer-marketing-center/src/main/java/com/bank/branch/platform/customer/config/CustomerSchedulingConfig.java`

- [ ] **Step 1: 删除文件**

```bash
git rm customer-marketing-center/src/main/java/com/bank/branch/platform/customer/config/CustomerSchedulingConfig.java
```

Expected: 文件被 git 标记为删除。

- [ ] **Step 2: 验证仓库无任何遗留 import 该类**

```bash
grep -rn "CustomerSchedulingConfig" --include="*.java" --exclude-dir=target . | grep -v "^Binary"
```

Expected: 0 命中（如有命中，说明遗留 import，必须修复）。

- [ ] **Step 3: 跑 customer-marketing-center 全量 surefire**

```bash
mvn test -pl customer-marketing-center
```

Expected: BUILD SUCCESS, 0 failures。

### Task 4.3: 验证 HealthCheck 仍生效（端到端）

- [ ] **Step 1: 重新 install 全部模块（stale jar 防护）**

```bash
mvn clean install -DskipTests
```

Expected: BUILD SUCCESS。

- [ ] **Step 2: 跑既有 MetricSchedulerHealthCheck 相关测试**

```bash
grep -rn "MetricSchedulerHealthCheck" --include="*.java" --exclude-dir=target . | grep -i "test\|IT"
```

Expected: 列出现有 HealthCheck 相关测试。然后跑它们：

```bash
# 假设找到 MetricSchedulerHealthCheckTest 或 IT
mvn test -pl performance-engine-center -Dtest='MetricSchedulerHealthCheckTest*'
```

Expected: BUILD SUCCESS（如果仅有 IT，下个 phase 会跑到）。

> 若没有现成 HealthCheck 单测，本 step 可作 noop——下一 phase mvn verify 全量回归会覆盖。

### Task 4.4: Phase 4 commit

- [ ] **Step 1: 提交**

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/config/PerformanceSchedulingConfig.java
git add -u customer-marketing-center/src/main/java/com/bank/branch/platform/customer/config/CustomerSchedulingConfig.java

git commit -m "$(cat <<'EOF'
refactor: V1.8 P4 @EnableScheduling 归属调整 customer→performance

- performance: 新建 PerformanceSchedulingConfig（仅 @EnableScheduling）
  接管 MetricSchedulerHealthCheck 的 Spring TaskScheduler 启用职责
- customer: 删除 CustomerSchedulingConfig（V1.8 后零 @Scheduled，名实不副）

V1.8 后整个仓库的 @EnableScheduling 仅服务于 performance HealthCheck
（V1.7 spec § 7 论证：纯本地兜底，不交给 Quartz 自调度）。
EOF
)"
git push origin master
```

---

## Phase 5 · 启动注册 IT

**目的：** 验证应用启动后 sys_job_conf 数据 + Quartz Scheduler 双重注册。

### Task 5.1: 探查 governance JobService.resolveGroup 取值

由于 `LeadCallbackJobRegisteredIT` 需要构造正确的 `JobKey(name, group)` 来调用 `Scheduler.checkExists`，必须先确认 group 字符串。

- [ ] **Step 1: 读 governance JobService 找 resolveGroup**

```bash
grep -n "resolveGroup\|JobKey.jobKey" system-governance-center/src/main/java/com/bank/branch/platform/governance/service/JobService.java
```

Expected: 命中 resolveGroup 方法或 jobKey 构造点。读上下文 10-20 行确认 group 字符串如何决定（多半是 hardcoded "DEFAULT" 或基于 job_key 前缀）。

> 若 group = "DEFAULT"，IT 直接用 `JobKey.jobKey("LEAD_CALLBACK_COMPENSATE", "DEFAULT")`。
>
> 若 group 是其他规则（例如根据 job_key 前缀分组），按规则计算 LEAD_CALLBACK_COMPENSATE 的 group 值。

### Task 5.2: 写 LeadCallbackJobRegisteredIT

**Files:**
- Create: `bootstrap/src/test/java/com/bank/branch/platform/it/LeadCallbackJobRegisteredIT.java`

- [ ] **Step 1: 探查 bootstrap 既有 IT 的基础设置（@SpringBootTest / @ActiveProfiles / @Sql 用法）**

```bash
ls bootstrap/src/test/java/com/bank/branch/platform/it/ | head -10
```

挑一个跟 sys_job_conf 相关的 IT（例如 V1_7_0FlywayIT 如果存在）作为模板：

```bash
find bootstrap -name "*FlywayIT.java" -o -name "*JobRegister*IT.java" 2>/dev/null
```

读其代码理解 H2 + Spring Boot test 的最小启动配置。

- [ ] **Step 2: 探查 governance SysJobConf entity / mapper 命名**

```bash
grep -rln "selectByJobKey\|SysJobConfMapper\|SysJobConf " system-governance-center/src/main/java/ | head -5
```

确认 mapper bean 名称、entity 字段名（cronExpr/quartzJobClass/misfirePolicy 是否驼峰）。

- [ ] **Step 3: 写 IT 代码（按 Task 5.1 / 5.2 探查到的真实 API 调整字段名）**

骨架（最终代码须按真实 API 调整）：

```java
package com.bank.branch.platform.it;

import com.bank.branch.platform.governance.entity.SysJobConf;
import com.bank.branch.platform.governance.mapper.SysJobConfMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V1.8 — 验证应用启动后 LEAD_CALLBACK_COMPENSATE 已落 sys_job_conf
 * 且被 governance JobService.syncOnStartup 注册到 Quartz Scheduler。
 */
@SpringBootTest
@ActiveProfiles("test")
class LeadCallbackJobRegisteredIT {

    @Autowired
    private SysJobConfMapper sysJobConfMapper;

    @Autowired
    private Scheduler scheduler;

    @Test
    @DisplayName("V1.8 启动后 sys_job_conf 含 LEAD_CALLBACK_COMPENSATE 行")
    void sysJobConfHasLeadCallbackRow() {
        SysJobConf row = sysJobConfMapper.selectByJobKey("LEAD_CALLBACK_COMPENSATE");
        assertThat(row)
                .as("data.sql 应在 H2 中预置 LEAD_CALLBACK_COMPENSATE 行")
                .isNotNull();
        assertThat(row.getQuartzJobClass())
                .isEqualTo("com.bank.branch.platform.customer.job.quartz.LeadCallbackCompensateQuartzJob");
        assertThat(row.getCronExpr()).isEqualTo("0 */5 * * * ?");
        assertThat(row.getMisfirePolicy()).isEqualTo("DO_NOTHING");
        assertThat(row.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("V1.8 启动后 Quartz Scheduler 含 LEAD_CALLBACK_COMPENSATE Job")
    void quartzSchedulerHasJob() throws SchedulerException {
        // group 取值需与 JobService.resolveGroup 保持一致（详见 Task 5.1 探查结果）
        JobKey jobKey = JobKey.jobKey("LEAD_CALLBACK_COMPENSATE", /* group */ "DEFAULT");
        assertThat(scheduler.checkExists(jobKey))
                .as("LEAD_CALLBACK_COMPENSATE 应已被 governance JobService.syncOnStartup 注册到 Quartz")
                .isTrue();
    }
}
```

> **注**：若 SysJobConfMapper 没有 selectByJobKey 方法，改用 `selectOne(Wrappers.lambdaQuery(SysJobConf.class).eq(SysJobConf::getJobKey, "LEAD_CALLBACK_COMPENSATE"))`（项目已用 MyBatis-Plus）；具体 API 按实际 mapper 定义。

- [ ] **Step 4: install 全部模块 + 跑 IT**

```bash
mvn clean install -DskipTests
mvn verify -pl bootstrap -Dit.test=LeadCallbackJobRegisteredIT
```

Expected: BUILD SUCCESS, 2 IT 全绿。

如果失败：
- `sysJobConfHasLeadCallbackRow` 失败 → data.sql 没生效（回查 P1.3）
- `quartzSchedulerHasJob` 失败 → governance syncOnStartup 没被触发；或 group 字符串不对
  - 用 `scheduler.getJobKeys(GroupMatcher.anyJobGroup())` 列出所有 job 调试

### Task 5.3: Phase 5 commit

- [ ] **Step 1: 提交**

```bash
git add bootstrap/src/test/java/com/bank/branch/platform/it/LeadCallbackJobRegisteredIT.java

git commit -m "$(cat <<'EOF'
test(bootstrap): V1.8 P5 LeadCallbackJobRegisteredIT 启动注册 IT

- 验证 H2 启动后 sys_job_conf 含 LEAD_CALLBACK_COMPENSATE 行
- 验证 governance JobService.syncOnStartup 注册到 Quartz Scheduler
- 关键字段：cron='0 */5 * * * ?', misfire=DO_NOTHING,
  class=com.bank.branch.platform.customer.job.quartz.LeadCallbackCompensateQuartzJob
EOF
)"
git push origin master
```

---

## Phase 6 · 全量回归

**目的：** 跑完整 surefire + failsafe，确保 V1.8 改动零回归。

### Task 6.1: 重 install 全部模块

- [ ] **Step 1: 清缓存 + 重 install（CLAUDE.md 强制要求）**

```bash
mvn clean install -DskipTests
```

Expected: BUILD SUCCESS。

### Task 6.2: 跑 surefire

- [ ] **Step 1: 跑全量 surefire**

```bash
mvn test
```

Expected: BUILD SUCCESS, 0 failures, 0 errors。

记录测试总数。与 Phase 0 baseline 对比：

| 模块 | baseline | V1.8 后 | 增量来源 |
|---|---|---|---|
| customer-marketing-center | N | N+3 | +1 LeadCallbackCompensateQuartzJobTest 单测.normal_path、+1 异常兜底、+1 NoCustomerScheduledArchTest |

### Task 6.3: 跑 failsafe

- [ ] **Step 1: 跑全量 failsafe（含 IT）**

```bash
mvn verify
```

Expected: BUILD SUCCESS, 0 failures, 0 errors。

记录 failsafe 测试总数。增量预期：
- bootstrap +2 IT（`LeadCallbackJobRegisteredIT` 2 cases）

### Task 6.4: 若有红色，定位修复

- [ ] **Step 1: 任何 failure / error 必须先定位修复**

常见雷区：
- bootstrap data.sql 列顺序错位（P1.3 升级 8 列时弄错）
- Quartz JobKey group 字符串猜错（Phase 5 Task 5.1 探查不足）
- 删除 CustomerSchedulingConfig 后某个测试或 IT 仍 import 它（Task 4.2.Step 2 grep 漏掉）
- archunit 版本不匹配（pom.xml 加错 scope）

修复后回到 Task 6.1 重新走一遍。

### Task 6.5: Phase 6 commit（仅当有修复）

- [ ] **Step 1: 若 P6 有修复，commit**

```bash
git add <修复涉及的文件>
git commit -m "fix(V1.8 P6): <具体修复描述>"
git push origin master
```

否则跳过本 task。

---

## Phase 7 · 文档更新

**目的：** 更新 root + 模块 CLAUDE.md，标注 V1.8 已交付。

### Task 7.1: 更新 root CLAUDE.md

**Files:**
- Modify: `CLAUDE.md`
- Modify: `.claude/CLAUDE.md`（与 root 内容大体一致，需同步）

- [ ] **Step 1: 在「当前已实现的模块」表中更新 customer-marketing-center 状态**

定位现有：

```
| `customer-marketing-center` | com.bank.branch.platform.customer | 已完成 | 客户营销中心 (113 Java + 46 测试，0 UOE) |
```

更新为（数字按 Phase 6 实测数据对齐）：

```
| `customer-marketing-center` | com.bank.branch.platform.customer | V1.8 已交付（2026-05-01）| 客户营销中心 (114 Java + 49 测试，0 UOE)；V1.8 LeadCallbackCompensation @Scheduled→Quartz 迁移 |
```

- [ ] **Step 2: 在「模块依赖图」附近加 V1.8 说明（可选）**

如果当前模块依赖图描述里有"performance V1.6 / V1.7 已交付"等版本节点，追加一行：

```
customer-marketing-center V1.8 已交付：LeadCallbackCompensation @Scheduled → Quartz 集群调度（job_key=LEAD_CALLBACK_COMPENSATE）；CustomerSchedulingConfig 删除，@EnableScheduling 归属 PerformanceSchedulingConfig
```

- [ ] **Step 3: 同步 .claude/CLAUDE.md**

```bash
diff CLAUDE.md .claude/CLAUDE.md
```

把 Step 1 / Step 2 改动同步到 `.claude/CLAUDE.md`（项目惯例两份并存，需保持同步）。

### Task 7.2: 更新 customer-marketing-center/CLAUDE.md

**Files:**
- Modify: `customer-marketing-center/CLAUDE.md`

- [ ] **Step 1: 在合适位置加 V1.8 章节**

定位「P1 三批改动进度（2026-04-29 已交付）」后面，新加：

```markdown
## V1.8 改动进度（2026-05-01 已交付）

LeadCallbackCompensationService 由 Spring `@Scheduled` 迁移到 Quartz：

- 新增 `customer/job/quartz/LeadCallbackCompensateQuartzJob`（实现 `org.quartz.Job`）
- 删除 `service/LeadCallbackCompensationService.scheduledScan()` + `@Scheduled` 入口
- 删除 `config/CustomerSchedulingConfig`（@EnableScheduling 归还 performance 模块）
- 新增 ArchUnit `arch/NoCustomerScheduledArchTest`（防回退）
- DDL: `sql/V1_8_0__register_lead_callback_compensate_job.sql` + `U1_8_0`
- IT: `bootstrap/.../LeadCallbackJobRegisteredIT`（启动注册验证）
- 既有 `LeadCallbackCompensationIT` 零改动（直接调 scanAndCompensate 业务路径不变）

cron='0 */5 * * * ?', misfire=DO_NOTHING；多实例由 QRTZ_LOCKS 行锁防重。
```

- [ ] **Step 2: 在「包结构」目录树中标注 job/quartz 子包**

定位现有 `src/main/java/com/bank/branch/platform/customer/` 树，加上：

```
├── job/              # V1.8 Quartz Job (1 个)
│   └── quartz/
│       └── LeadCallbackCompensateQuartzJob.java
```

- [ ] **Step 3: 在「V1.0 已知技术债」表附近，更新或加新表「V1.8 后续 / 后置事项」**

参考 spec § 12，可选地加：

```markdown
## V1.9 候选事项（来自 V1.8 spec § 12）

| # | 事项 | 优先级 |
|---|---|---|
| 1 | HealthCheck 也 Quartz 化（去掉最后一个 @Scheduled）| 低 |
| 2 | sys_job_conf 运维 Runbook | 中 |
| 3 | Quartz JobStore 反向清理（gcDanglingTriggers）| 低 |
```

### Task 7.3: 更新 performance-engine-center/CLAUDE.md

**Files:**
- Modify: `performance-engine-center/CLAUDE.md`

- [ ] **Step 1: 在 V1.7 已交付描述附近加 V1.8 微调说明**

```markdown
## V1.8 微调（2026-05-01）

- 新增 `config/PerformanceSchedulingConfig`（@EnableScheduling）：
  从 customer 模块迁移而来，接管 `MetricSchedulerHealthCheck` 的 Spring TaskScheduler 启用职责
- V1.8 后整个仓库的 @EnableScheduling 仅服务于本模块的 HealthCheck
  （V1.7 spec § 7 论证：纯本地兜底，不交给 Quartz 自调度）
```

### Task 7.4: Phase 7 commit

- [ ] **Step 1: 提交**

```bash
git add CLAUDE.md .claude/CLAUDE.md \
        customer-marketing-center/CLAUDE.md \
        performance-engine-center/CLAUDE.md

git commit -m "$(cat <<'EOF'
docs: V1.8 P7 CLAUDE.md 同步交付状态

- root + .claude/CLAUDE.md：customer-marketing-center 标注 V1.8 已交付
- customer-marketing-center/CLAUDE.md：新增 V1.8 改动进度章节、
  job/quartz 子包记录、V1.9 候选事项
- performance-engine-center/CLAUDE.md：V1.8 微调
  （@EnableScheduling 接管自 customer）
EOF
)"
git push origin master
```

---

## Phase 8 · 最终验证 + 收尾

**目的：** 全流程 end-to-end 验证，签收。

### Task 8.1: 验收硬指标 checklist

逐项 grep / 跑测试核对，每条都能给出 ✅：

- [ ] **Step 1: customer 模块零 `@Scheduled`**

```bash
grep -rn --include="*.java" "@Scheduled\(" customer-marketing-center/src/main/ 2>/dev/null
```

Expected: 0 命中。

- [ ] **Step 2: LeadCallbackCompensateQuartzJob 类存在**

```bash
ls customer-marketing-center/src/main/java/com/bank/branch/platform/customer/job/quartz/LeadCallbackCompensateQuartzJob.java
```

Expected: 文件存在。

- [ ] **Step 3: V1_8_0 + U1_8_0 SQL 存在**

```bash
ls customer-marketing-center/src/main/resources/sql/V1_8_0__*.sql \
   customer-marketing-center/src/main/resources/sql/U1_8_0__*.sql
```

Expected: 2 个文件。

- [ ] **Step 4: CustomerSchedulingConfig 已删除**

```bash
ls customer-marketing-center/src/main/java/com/bank/branch/platform/customer/config/CustomerSchedulingConfig.java 2>&1
```

Expected: `No such file or directory`。

- [ ] **Step 5: PerformanceSchedulingConfig 存在**

```bash
ls performance-engine-center/src/main/java/com/bank/branch/platform/performance/config/PerformanceSchedulingConfig.java
```

Expected: 文件存在。

- [ ] **Step 6: bootstrap data.sql 含 LEAD_CALLBACK_COMPENSATE**

```bash
grep "LEAD_CALLBACK_COMPENSATE" bootstrap/src/test/resources/data.sql
```

Expected: 1 命中。

- [ ] **Step 7: 仓库内 customer.lead-compensation.interval-ms 引用 = 0**

```bash
grep -rn "customer\.lead-compensation\.interval-ms" --exclude-dir=target . 2>/dev/null
```

Expected: 0 命中（除了 spec / plan / commit 历史日志）。如果命中是 docs/spec/plan 中说明性提及，可豁免。

- [ ] **Step 8: ArchUnit 守护通过**

```bash
mvn test -pl customer-marketing-center -Dtest=NoCustomerScheduledArchTest
```

Expected: BUILD SUCCESS。

- [ ] **Step 9: 全量 surefire + failsafe 全绿**

```bash
mvn clean install -DskipTests
mvn verify
```

Expected: BUILD SUCCESS, 0 failures, 0 errors。

### Task 8.2: 最终 push 状态确认

- [ ] **Step 1: 查 git 状态**

```bash
git status
git log --oneline 5438b1d..HEAD
```

Expected:
- working tree clean
- 7 个 commit（P1 / P2 / P3 / P4 / P5 / [P6 if any] / P7）
- 全部已 push 到 origin/master

```bash
git log origin/master..HEAD --oneline
```

Expected: 0 行（即本地无未推送 commit）。

### Task 8.3: 终态总结输出

- [ ] **Step 1: 输出 V1.8 交付总结**

```
✅ V1.8 交付完成

代码改动：
  - 新增：LeadCallbackCompensateQuartzJob（46 行）+ 单测 60 行
  - 新增：NoCustomerScheduledArchTest（28 行）
  - 新增：PerformanceSchedulingConfig（12 行）
  - 新增：LeadCallbackJobRegisteredIT（约 60 行）
  - 修改：LeadCallbackCompensationService（删 16 行）
  - 删除：CustomerSchedulingConfig（30 行）
DDL：
  - V1_8_0__register_lead_callback_compensate_job.sql（INSERT 1 行）
  - U1_8_0__remove_lead_callback_compensate_job.sql（DELETE 1 行）
  - bootstrap data.sql：seed +1 行
文档：
  - root + .claude/CLAUDE.md：customer-marketing-center 标注 V1.8 已交付
  - customer-marketing-center/CLAUDE.md：V1.8 改动进度章节
  - performance-engine-center/CLAUDE.md：V1.8 微调说明
git：
  - 7 个 commit，全部已 push 到 origin/master
  - 起点：5438b1d，终点：<HEAD SHA>
测试：
  - surefire +3：LeadCallbackCompensateQuartzJobTest x2 + NoCustomerScheduledArchTest x1
  - failsafe +2：LeadCallbackJobRegisteredIT x2
  - 全量 surefire / failsafe 全绿
验收硬指标：8/8 ✅
```

---

## Self-Review

- [x] **Spec 覆盖**：spec § 4.1 / 4.2 / 4.3 / 4.4 / 4.5 → P2 / P1 / P3 / P3 / P4；spec § 5 → P1；spec § 6 → P2 / P3 / P5 / P6；spec § 8 验收标准 → P8.1
- [x] **Placeholder 扫描**：plan 中无 TBD/TODO；只有 P5 Task 5.1 / 5.2 含"按真实 API 调整"措辞——这是显式 explore step，不是占位；
- [x] **类型/方法名一致性**：全文 `LeadCallbackCompensateQuartzJob` / `scanAndCompensate()` / `LEAD_CALLBACK_COMPENSATE` / `JOB_LEAD_CALLBACK_COMPENSATE` 统一；
- [x] **TDD 红绿落地**：P2 RED→GREEN（先写测试编译失败，再实现）；P3 RED→GREEN（ArchUnit 先红，删 `@Scheduled` 后绿）；
- [x] **每 phase 都 commit + push**：与 V1.7 节奏一致；
- [x] **stale jar 防护**：每个跨模块 IT 步骤前都有 `mvn clean install -DskipTests`（CLAUDE.md 测试章节强制要求）；
- [x] **回滚路径**：U1_8_0 + git revert 文档化（spec § 5.4 + plan P0 baseline 对照）。

---

## Execution

按 brainstorming 阶段拍板：

- **Sub-skill：** `superpowers:subagent-driven-development`
- **Branch：** master 直接 push（每 phase 末尾）
- **Subagent model：** P1 / P3 / P4 / P7 用 cheap model（机械任务）；P2 / P5 / P6 用 standard model（含探查/调试判断）；最终 review 用 capable model
- **Two-stage review per task：** spec compliance → code quality（如 V1.7 节奏）
