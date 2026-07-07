# 统计展示表「日增量归档 + 分批清理 + 主表瘦身」实施计划（v2）

> **For agentic workers:** REQUIRED SUB-SKILL: Use subagent-driven-development (recommended) or executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 用一条每天循环执行的 Quartz 任务，把两张统计展示主表按天增量归档到 6 张历史表，并在旬边界日按天分批清理旧数据、月初按天分批瘦身主表——全程幂等、无大事务。

**Architecture:** 一条任务 `STAT_SHOW_ARCHIVE`（cron `0 30 6-18 * * ?`，`@DisallowConcurrentExecution`）。每次运行对 cust/emp 两张主表：①把「昨天所属旬的旬首~昨天」逐日增量搬进对应历史表（count 比对幂等，源未就绪则跳过）；②当天是 1/11/21 号时按天分批删上一代旧旬；③每月 1 号按天分批把上月非月末数据从主表删掉（保留月末）。所有 DB 操作按 `STATIS_DT` 切成「每天 ≤200 万行」的小事务，命中索引 `idx_statis_dt`。

**Tech Stack:** Spring Boot 3.2.3 + JDK 17、MyBatis-Plus（本任务用原生 XML，外部表无主键非实体）、Quartz（JDBC JobStore，集群）、MySQL(dev)/GoldenDB(prod)、JUnit5 + Mockito + AssertJ。

---

## 背景与关键事实（实现者必读）

- **两张主表**：`XAN_M98_CUST_STAT_SHOW3`、`XAN_M98_EMP_STAT_SHOW3`。宽表、全 `varchar(300)`、**无主键**，仅二级索引 `idx_statis_dt(STATIS_DT)` 等。
- **日期字段** `STATIS_DT`：格式 **`yyyy-MM-dd` 定宽字符串**。定宽 → `STATIS_DT = '2026-07-05'` 等值、`BETWEEN` 区间均按字典序即按日期，正确。
- **数据量**：单表约 **200 万行/天、6000 万行/月**。因此**严禁**一次性大插入/大删除，一律按 `STATIS_DT` 逐日分批（每日 ≤200 万，各自独立事务）。
- **6 张历史表**：主表名 + `_H1/_H2/_H3`。归属：`_H2`=每月 1~10 日、`_H3`=11~20 日、`_H1`=21~月末。用 `CREATE TABLE ... LIKE` 建（继承列/索引/charset）。
- **旬边界清理口径**（用户确认，日增量模型下自洽）：11 号清**上月** 1~10；21 号清**上月** 11~20；1 号清**上上月** 21~月末（因 21~月末旬跨月，次月 1 号才收口，上一代要前推两月）。
- **主表瘦身**：每月只保留每月最后一天（取该月实际 `MAX(STATIS_DT)`），其余删除；仅 1 号处理刚封口的上月。
- **幂等/并发**：`@DisallowConcurrentExecution` + Quartz 集群 QRTZ 行锁；插入用 count 比对跳过；删除删空即 no-op。**不要**给 `run()` 加 `@Transactional`（要每日一提交，避免巨型事务）。
- **调度注册范式**：向 `SYS_JOB_CONF` 插一行（`job_key/cron_expr/quartz_job_class/misfire_policy=FIRE_ONCE_NOW/status=ACTIVE/allow_manual_trigger=1`），`JobService` 启动时同步进 Quartz。SQL 走 `docs/superpowers/sql/`，**双库**（`yiti`+`onepl`）执行。
- **Quartz 瘦包装范式**：`implements org.quartz.Job`，**不加 `@Component`**（Quartz 反射建实例，`AutowiringSpringBeanJobFactory` 注入），`execute()` 只委托业务 Service。参照 `performance/job/quartz/SysControlCleanupQuartzJob`。

### 与 v1 的关系（本会话已写的 v1 代码要改造）
v1（每月一次、整旬区间插/删）已写但执行模型作废。本计划**重写/删除**以下 v1 文件：
- 删除：`job/ArchiveBranch.java`、`job/StatShowArchivePlan.java`、`job/StatShowArchivePlanner.java`、`test/.../job/StatShowArchivePlannerTest.java`
- 重写：`mapper/StatShowArchiveMapper.java` + `.xml`、`job/StatShowArchiveJob.java`、`test/.../job/StatShowArchiveJobTest.java`、`test/.../mapper/StatShowArchiveMapperIT.java`、`job/quartz/StatShowArchiveQuartzJob.java`、`docs/superpowers/sql/2026-07-03-stat-show-archive.sql`

---

## 文件结构（本计划产出/改动）

| 文件 | 责任 |
|---|---|
| `job/StatShowArchiveDates.java`（新） | **纯日期逻辑**：旬路由、旬首、边界清理区间、瘦身月区间、区间日期遍历。零依赖、可纯单测。 |
| `job/StatShowArchiveJob.java`（重写） | 业务编排：两表 × 日增量/补全 + 边界清理 + 瘦身。无 `@Transactional`（逐日提交）。 |
| `mapper/StatShowArchiveMapper.java`（重写）+ `resources/mapper/performance/StatShowArchiveMapper.xml` | 5 个按日 SQL：countByTableDate / insertHistByDate / deleteHistByDate / selectMaxStatisDt / deleteMainByDate。 |
| `job/quartz/StatShowArchiveQuartzJob.java`（重写） | Quartz 瘦包装 + `@DisallowConcurrentExecution`。 |
| `docs/superpowers/sql/2026-07-03-stat-show-archive-hist-ddl.sql`（新） | 6 张历史表**显式** `CREATE TABLE IF NOT EXISTS` DDL（从主表逐字复制，同事惯例，双库幂等）。 |
| `docs/superpowers/sql/2026-07-03-stat-show-archive.sql`（重写） | `SYS_JOB_CONF` 任务注册（cron `0 30 6-18 * * ?`，双库幂等）；建表前置引用上面的 DDL 文件。 |
| 测试：`StatShowArchiveDatesTest`、`StatShowArchiveJobTest`、`StatShowArchiveMapperIT` | 纯逻辑单测 / 编排 Mockito 单测 / 真库 mapper IT。 |

**包根**：`com.bank.branch.platform.performance`，路径前缀 `performance-engine-center/src/{main,test}/java/com/bank/branch/platform/performance/`。

---

## Task 0: 清理 v1 遗留文件

**Files:**
- Delete: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/job/ArchiveBranch.java`
- Delete: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/job/StatShowArchivePlan.java`
- Delete: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/job/StatShowArchivePlanner.java`
- Delete: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/job/StatShowArchivePlannerTest.java`

- [ ] **Step 1: 删除 v1 每月模型的 4 个文件**

```bash
cd /home/djdev/liuyang/yiti
rm performance-engine-center/src/main/java/com/bank/branch/platform/performance/job/ArchiveBranch.java \
   performance-engine-center/src/main/java/com/bank/branch/platform/performance/job/StatShowArchivePlan.java \
   performance-engine-center/src/main/java/com/bank/branch/platform/performance/job/StatShowArchivePlanner.java \
   performance-engine-center/src/test/java/com/bank/branch/platform/performance/job/StatShowArchivePlannerTest.java
```

> 注：此刻 `StatShowArchiveJob.java`/`StatShowArchiveMapper*`/`StatShowArchiveJobTest.java` 仍引用旧类会编译失败，Task 1~4 会全部重写。可在 Task 4 结束后统一编译。

---

## Task 1: 纯日期逻辑 `StatShowArchiveDates`

**Files:**
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/job/StatShowArchiveDates.java`
- Test: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/job/StatShowArchiveDatesTest.java`

- [ ] **Step 1: 写失败测试**

```java
package com.bank.branch.platform.performance.job;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

/** StatShowArchiveDates 纯日期逻辑单测：旬路由 / 旬首 / 边界清理区间 / 瘦身月 / 区间遍历。 */
class StatShowArchiveDatesTest {

    @Test
    void histSuffix_routesByThird() {
        assertThat(StatShowArchiveDates.histSuffix(LocalDate.of(2026, 8, 1))).isEqualTo("_H2");
        assertThat(StatShowArchiveDates.histSuffix(LocalDate.of(2026, 8, 10))).isEqualTo("_H2");
        assertThat(StatShowArchiveDates.histSuffix(LocalDate.of(2026, 8, 11))).isEqualTo("_H3");
        assertThat(StatShowArchiveDates.histSuffix(LocalDate.of(2026, 8, 20))).isEqualTo("_H3");
        assertThat(StatShowArchiveDates.histSuffix(LocalDate.of(2026, 8, 21))).isEqualTo("_H1");
        assertThat(StatShowArchiveDates.histSuffix(LocalDate.of(2026, 8, 31))).isEqualTo("_H1");
    }

    @Test
    void sliceStart_returnsThirdStart() {
        assertThat(StatShowArchiveDates.sliceStart(LocalDate.of(2026, 8, 5))).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(StatShowArchiveDates.sliceStart(LocalDate.of(2026, 8, 15))).isEqualTo(LocalDate.of(2026, 8, 11));
        assertThat(StatShowArchiveDates.sliceStart(LocalDate.of(2026, 8, 25))).isEqualTo(LocalDate.of(2026, 8, 21));
    }

    @Test
    void boundaryCleanup_on11_cleansPrevMonth1To10_H2() {
        var opt = StatShowArchiveDates.boundaryCleanup(LocalDate.of(2026, 8, 11));
        assertThat(opt).isPresent();
        var cr = opt.get();
        assertThat(cr.histSuffix()).isEqualTo("_H2");
        assertThat(cr.start()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(cr.end()).isEqualTo(LocalDate.of(2026, 7, 10));
    }

    @Test
    void boundaryCleanup_on21_cleansPrevMonth11To20_H3() {
        var cr = StatShowArchiveDates.boundaryCleanup(LocalDate.of(2026, 8, 21)).orElseThrow();
        assertThat(cr.histSuffix()).isEqualTo("_H3");
        assertThat(cr.start()).isEqualTo(LocalDate.of(2026, 7, 11));
        assertThat(cr.end()).isEqualTo(LocalDate.of(2026, 7, 20));
    }

    @Test
    void boundaryCleanup_on1_cleansTwoMonthsAgo21ToEnd_H1() {
        var cr = StatShowArchiveDates.boundaryCleanup(LocalDate.of(2026, 8, 1)).orElseThrow();
        assertThat(cr.histSuffix()).isEqualTo("_H1");
        assertThat(cr.start()).isEqualTo(LocalDate.of(2026, 6, 21));
        assertThat(cr.end()).isEqualTo(LocalDate.of(2026, 6, 30));
    }

    @Test
    void boundaryCleanup_on1_march_handlesFebEnd() {
        var cr = StatShowArchiveDates.boundaryCleanup(LocalDate.of(2026, 3, 1)).orElseThrow();
        assertThat(cr.start()).isEqualTo(LocalDate.of(2026, 1, 21));
        assertThat(cr.end()).isEqualTo(LocalDate.of(2026, 1, 31));
    }

    @Test
    void boundaryCleanup_onNonBoundary_empty() {
        assertThat(StatShowArchiveDates.boundaryCleanup(LocalDate.of(2026, 8, 15))).isEmpty();
        assertThat(StatShowArchiveDates.boundaryCleanup(LocalDate.of(2026, 8, 2))).isEmpty();
    }

    @Test
    void pruneMonth_on1_returnsPrevMonthFullRange() {
        var r = StatShowArchiveDates.pruneMonth(LocalDate.of(2026, 8, 1)).orElseThrow();
        assertThat(r.start()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(r.end()).isEqualTo(LocalDate.of(2026, 7, 31));
    }

    @Test
    void pruneMonth_on1_jan_handlesYearRollover() {
        var r = StatShowArchiveDates.pruneMonth(LocalDate.of(2026, 1, 1)).orElseThrow();
        assertThat(r.start()).isEqualTo(LocalDate.of(2025, 12, 1));
        assertThat(r.end()).isEqualTo(LocalDate.of(2025, 12, 31));
    }

    @Test
    void pruneMonth_onNon1_empty() {
        assertThat(StatShowArchiveDates.pruneMonth(LocalDate.of(2026, 8, 11))).isEmpty();
    }

    @Test
    void datesInclusive_returnsClosedIntervalAscending() {
        List<LocalDate> ds = StatShowArchiveDates.datesInclusive(
                LocalDate.of(2026, 7, 28), LocalDate.of(2026, 8, 1));
        assertThat(ds).containsExactly(
                LocalDate.of(2026, 7, 28), LocalDate.of(2026, 7, 29),
                LocalDate.of(2026, 7, 30), LocalDate.of(2026, 7, 31),
                LocalDate.of(2026, 8, 1));
    }

    @Test
    void fmt_isIsoLocalDate() {
        assertThat(StatShowArchiveDates.fmt(LocalDate.of(2026, 7, 5))).isEqualTo("2026-07-05");
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `mvn test -pl performance-engine-center -Dtest=StatShowArchiveDatesTest -DfailIfNoTests=false`
Expected: 编译失败 / 全部 FAIL（`StatShowArchiveDates` 不存在）。

- [ ] **Step 3: 写实现**

```java
package com.bank.branch.platform.performance.job;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 统计展示表旬度归档「日期逻辑」纯计算器（无副作用、无依赖，便于单测）.
 *
 * <p>旬归属：1~10→_H2，11~20→_H3，21~月末→_H1。日期一律 yyyy-MM-dd 定宽字符串。
 */
public final class StatShowArchiveDates {

    private static final DateTimeFormatter DT = DateTimeFormatter.ISO_LOCAL_DATE;

    private StatShowArchiveDates() {
    }

    /** 边界清理区间：历史表后缀 + 闭区间 [start, end]. */
    public record CleanupRange(String histSuffix, LocalDate start, LocalDate end) {
    }

    /** 主表瘦身月区间：闭区间 [start, end]. */
    public record MonthRange(LocalDate start, LocalDate end) {
    }

    /** 某日所属旬的历史表后缀（_H2 / _H3 / _H1）. */
    public static String histSuffix(LocalDate d) {
        int day = d.getDayOfMonth();
        if (day <= 10) {
            return "_H2";
        }
        if (day <= 20) {
            return "_H3";
        }
        return "_H1";
    }

    /** 某日所属旬的旬首日（1 / 11 / 21 号）. */
    public static LocalDate sliceStart(LocalDate d) {
        int day = d.getDayOfMonth();
        if (day <= 10) {
            return d.withDayOfMonth(1);
        }
        if (day <= 20) {
            return d.withDayOfMonth(11);
        }
        return d.withDayOfMonth(21);
    }

    /**
     * 旬边界清理区间：仅当 today 为 1/11/21 号返回「上一代」旧旬；否则 empty.
     * 11→上月1~10(_H2)；21→上月11~20(_H3)；1→上上月21~末(_H1，因该旬跨月上一代前推两月).
     */
    public static Optional<CleanupRange> boundaryCleanup(LocalDate today) {
        return switch (today.getDayOfMonth()) {
            case 11 -> {
                LocalDate pm = today.minusMonths(1);
                yield Optional.of(new CleanupRange("_H2", pm.withDayOfMonth(1), pm.withDayOfMonth(10)));
            }
            case 21 -> {
                LocalDate pm = today.minusMonths(1);
                yield Optional.of(new CleanupRange("_H3", pm.withDayOfMonth(11), pm.withDayOfMonth(20)));
            }
            case 1 -> {
                LocalDate p2 = today.minusMonths(2);
                yield Optional.of(new CleanupRange("_H1", p2.withDayOfMonth(21), monthEnd(p2)));
            }
            default -> Optional.empty();
        };
    }

    /** 主表瘦身月：仅 today 为 1 号返回上月整月区间；否则 empty. */
    public static Optional<MonthRange> pruneMonth(LocalDate today) {
        if (today.getDayOfMonth() != 1) {
            return Optional.empty();
        }
        LocalDate pm = today.minusMonths(1);
        return Optional.of(new MonthRange(pm.withDayOfMonth(1), monthEnd(pm)));
    }

    /** 闭区间 [start, end] 的所有日期，升序. */
    public static List<LocalDate> datesInclusive(LocalDate start, LocalDate end) {
        List<LocalDate> list = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            list.add(d);
        }
        return list;
    }

    /** yyyy-MM-dd. */
    public static String fmt(LocalDate d) {
        return d.format(DT);
    }

    private static LocalDate monthEnd(LocalDate m) {
        return m.withDayOfMonth(m.lengthOfMonth());
    }
}
```

- [ ] **Step 4: 运行确认通过**

Run: `mvn test -pl performance-engine-center -Dtest=StatShowArchiveDatesTest -DfailIfNoTests=false`
Expected: `Tests run: 12, Failures: 0, Errors: 0`。（`StatShowArchiveJob`/`Mapper` 尚未重写，若整模块 test 编译失败属正常，本步只跑该类；如受阻可在 Task 3 后再跑。）

- [ ] **Step 5: 提交**

```bash
cd /home/djdev/liuyang/yiti
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/job/StatShowArchiveDates.java \
        performance-engine-center/src/test/java/com/bank/branch/platform/performance/job/StatShowArchiveDatesTest.java
git rm performance-engine-center/src/main/java/com/bank/branch/platform/performance/job/ArchiveBranch.java \
       performance-engine-center/src/main/java/com/bank/branch/platform/performance/job/StatShowArchivePlan.java \
       performance-engine-center/src/main/java/com/bank/branch/platform/performance/job/StatShowArchivePlanner.java \
       performance-engine-center/src/test/java/com/bank/branch/platform/performance/job/StatShowArchivePlannerTest.java
git commit -m "feat(perf): stat-show 归档 v2 日期逻辑 StatShowArchiveDates，移除 v1 每月模型"
```

---

## Task 2: Mapper（5 个按日 SQL）+ 真库 IT

**Files:**
- Rewrite: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/mapper/StatShowArchiveMapper.java`
- Rewrite: `performance-engine-center/src/main/resources/mapper/performance/StatShowArchiveMapper.xml`
- Rewrite test: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/mapper/StatShowArchiveMapperIT.java`

- [ ] **Step 1: 写失败测试（真库 IT，最小同构 fixture 表）**

```java
package com.bank.branch.platform.performance.mapper;

import com.bank.branch.platform.auth.api.RoleApi;
import com.bank.branch.platform.governance.api.AuditApi;
import com.bank.branch.platform.governance.api.DictApi;
import com.bank.branch.platform.governance.api.FileApi;
import com.bank.branch.platform.governance.api.NotifyApi;
import com.bank.branch.platform.performance.support.PerformanceMapperTestBase;
import com.bank.branch.platform.workflow.api.TodoQueryApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * StatShowArchiveMapper 集成测试（真库 onepl_test_bootstrap）.
 * 用最小同构 fixture 表（无主键，STATIS_DT 定宽日期），验证 5 个按日 SQL 与 ${表名} 拼接。
 */
class StatShowArchiveMapperIT extends PerformanceMapperTestBase {

    private static final String MAIN = "ARCH_IT_MAIN";
    private static final String HIST = "ARCH_IT_HIST";

    @Autowired
    private StatShowArchiveMapper mapper;
    @Autowired
    private JdbcTemplate jdbc;

    // PerfTestConfig 未提供的跨模块 governance/auth/workflow API，补 mock 使 perf 测试上下文可加载（与本用例逻辑无关）
    @MockBean private NotifyApi notifyApi;
    @MockBean private FileApi fileApi;
    @MockBean private DictApi dictApi;
    @MockBean private AuditApi auditApi;
    @MockBean private RoleApi roleApi;
    @MockBean private TodoQueryApi todoQueryApi;

    @BeforeEach
    void setUp() {
        for (String t : new String[]{MAIN, HIST}) {
            jdbc.execute("DROP TABLE IF EXISTS " + t);
            jdbc.execute("CREATE TABLE " + t + " (STATIS_DT VARCHAR(10), CUST_ID VARCHAR(32), VAL VARCHAR(32))");
        }
    }

    @AfterEach
    void tearDown() {
        jdbc.execute("DROP TABLE IF EXISTS " + MAIN);
        jdbc.execute("DROP TABLE IF EXISTS " + HIST);
    }

    private void ins(String t, String dt, String id, String val) {
        jdbc.update("INSERT INTO " + t + "(STATIS_DT,CUST_ID,VAL) VALUES (?,?,?)", dt, id, val);
    }

    private long cnt(String t) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + t, Long.class);
    }

    @Test
    void countByTableDate_countsOnlyThatDate() {
        ins(MAIN, "2026-07-05", "A", "1");
        ins(MAIN, "2026-07-05", "B", "1");
        ins(MAIN, "2026-07-06", "C", "1");
        assertThat(mapper.countByTableDate(MAIN, "2026-07-05")).isEqualTo(2);
        assertThat(mapper.countByTableDate(MAIN, "2026-07-09")).isZero();
    }

    @Test
    void insertHistByDate_copiesOnlyThatDate_allColumns() {
        ins(MAIN, "2026-07-05", "Y", "v-y");
        ins(MAIN, "2026-07-06", "Z", "v-z");
        int n = mapper.insertHistByDate(HIST, MAIN, "2026-07-05");
        assertThat(n).isEqualTo(1);
        assertThat(cnt(HIST)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT VAL FROM " + HIST + " WHERE CUST_ID='Y'", String.class)).isEqualTo("v-y");
    }

    @Test
    void deleteHistByDate_deletesOnlyThatDate() {
        ins(HIST, "2026-07-05", "A", "1");
        ins(HIST, "2026-07-05", "B", "1");
        ins(HIST, "2026-07-06", "C", "1");
        int n = mapper.deleteHistByDate(HIST, "2026-07-05");
        assertThat(n).isEqualTo(2);
        assertThat(cnt(HIST)).isEqualTo(1);
    }

    @Test
    void selectMaxStatisDt_returnsMaxInRange_orNull() {
        ins(MAIN, "2026-07-01", "A", "1");
        ins(MAIN, "2026-07-28", "B", "1");
        ins(MAIN, "2026-08-05", "C", "1");
        assertThat(mapper.selectMaxStatisDt(MAIN, "2026-07-01", "2026-07-31")).isEqualTo("2026-07-28");
        assertThat(mapper.selectMaxStatisDt(MAIN, "2026-09-01", "2026-09-30")).isNull();
    }

    @Test
    void deleteMainByDate_deletesOnlyThatDate() {
        ins(MAIN, "2026-07-15", "A", "1");
        ins(MAIN, "2026-07-16", "B", "1");
        int n = mapper.deleteMainByDate(MAIN, "2026-07-15");
        assertThat(n).isEqualTo(1);
        assertThat(cnt(MAIN)).isEqualTo(1);
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `mvn verify -pl performance-engine-center -Dit.test=StatShowArchiveMapperIT -Dtest=NoSuchUnitTest -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false`
Expected: FAIL/ERROR（`StatShowArchiveMapper` 方法不存在 / `Invalid bound statement`）。
> ⚠️ **已知环境问题**：本地 `onepl_test_bootstrap` 测试库 schema 漂移（`EVAL_ASSIGN_BATCH.total_rows` 缺列）会导致 perf 测试**上下文加载失败**，该 IT 在本地跑不起来（非本代码问题）。若上下文报 `Unknown column 'total_rows'`，转 Step 4b 用 mysql 直连验证 SQL；IT 代码保留，正确 provision 的库/内网可跑。

- [ ] **Step 3: 写 Mapper 接口**

```java
package com.bank.branch.platform.performance.mapper;

import org.apache.ibatis.annotations.Param;

/**
 * 统计展示表旬度归档 Mapper（XAN_M98_CUST/EMP_STAT_SHOW3 及其历史表 _H1/_H2/_H3）.
 *
 * <p>表名为固定白名单字面量（来自 StatShowArchiveJob.MAIN_TABLES + 后缀，非用户输入），用 ${} 拼接；
 * STATIS_DT 一律 #{} 占位。外部表无主键、非 MyBatis-Plus 实体，故用原生 XML。所有方法按单日操作，
 * 每次 ≤ 一天数据量（约 200 万），逐日提交避免巨型事务。
 */
public interface StatShowArchiveMapper {

    /** 统计某表在某 STATIS_DT 的行数（main/hist 通用，用于幂等 count 比对）. */
    long countByTableDate(@Param("table") String table, @Param("dt") String dt);

    /** 将主表某 STATIS_DT 的数据整行插入历史表（历史表 CREATE LIKE 主表，列同构，SELECT * 安全）. */
    int insertHistByDate(@Param("histTable") String histTable,
                         @Param("mainTable") String mainTable,
                         @Param("dt") String dt);

    /** 删除历史表某 STATIS_DT 的数据. */
    int deleteHistByDate(@Param("histTable") String histTable, @Param("dt") String dt);

    /** 取主表指定闭区间内的 MAX(STATIS_DT)（作为「该月最后一天」），无数据返回 null. */
    String selectMaxStatisDt(@Param("mainTable") String mainTable,
                             @Param("start") String start,
                             @Param("end") String end);

    /** 删除主表某 STATIS_DT 的数据（瘦身按天分批用）. */
    int deleteMainByDate(@Param("mainTable") String mainTable, @Param("dt") String dt);
}
```

- [ ] **Step 4: 写 Mapper XML**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<!--
  统计展示表旬度归档按日 SQL。表名为固定白名单字面量（StatShowArchiveJob.MAIN_TABLES + _H1/_H2/_H3），
  用 ${} 拼接；STATIS_DT 一律 #{} 占位，无注入风险。STATIS_DT 为 yyyy-MM-dd 定宽字符串，等值/BETWEEN 即按日期。
  所有语句按单日操作，命中索引 idx_statis_dt，每次 ≤ 一天数据量。
-->
<mapper namespace="com.bank.branch.platform.performance.mapper.StatShowArchiveMapper">

    <select id="countByTableDate" resultType="long">
        SELECT COUNT(*) FROM ${table} WHERE STATIS_DT = #{dt}
    </select>

    <insert id="insertHistByDate">
        INSERT INTO ${histTable}
        SELECT * FROM ${mainTable}
        WHERE STATIS_DT = #{dt}
    </insert>

    <delete id="deleteHistByDate">
        DELETE FROM ${histTable} WHERE STATIS_DT = #{dt}
    </delete>

    <select id="selectMaxStatisDt" resultType="java.lang.String">
        SELECT MAX(STATIS_DT) FROM ${mainTable}
        WHERE STATIS_DT BETWEEN #{start} AND #{end}
    </select>

    <delete id="deleteMainByDate">
        DELETE FROM ${mainTable} WHERE STATIS_DT = #{dt}
    </delete>

</mapper>
```

- [ ] **Step 4b: 若 IT 因环境无法运行，用 mysql 直连验证 5 条 SQL 语义**

Run:
```bash
mysql -h127.0.0.1 -uroot -pdjdev onepl_test_bootstrap <<'SQL'
DROP TABLE IF EXISTS ARCH_IT_MAIN; DROP TABLE IF EXISTS ARCH_IT_HIST;
CREATE TABLE ARCH_IT_MAIN (STATIS_DT VARCHAR(10), CUST_ID VARCHAR(32), VAL VARCHAR(32));
CREATE TABLE ARCH_IT_HIST (STATIS_DT VARCHAR(10), CUST_ID VARCHAR(32), VAL VARCHAR(32));
INSERT INTO ARCH_IT_MAIN VALUES ('2026-07-05','Y','v-y'),('2026-07-05','B','1'),('2026-07-06','Z','v-z');
SELECT 'count(期望2)' c, COUNT(*) FROM ARCH_IT_MAIN WHERE STATIS_DT='2026-07-05';
INSERT INTO ARCH_IT_HIST SELECT * FROM ARCH_IT_MAIN WHERE STATIS_DT='2026-07-05';
SELECT 'insert(期望2,Y=v-y)' c, COUNT(*), MAX(CASE WHEN CUST_ID='Y' THEN VAL END) FROM ARCH_IT_HIST;
DELETE FROM ARCH_IT_HIST WHERE STATIS_DT='2026-07-05';
SELECT 'deleteHist(期望0)' c, COUNT(*) FROM ARCH_IT_HIST;
SELECT 'max(期望2026-07-06)' c, MAX(STATIS_DT) FROM ARCH_IT_MAIN WHERE STATIS_DT BETWEEN '2026-07-01' AND '2026-07-31';
DELETE FROM ARCH_IT_MAIN WHERE STATIS_DT='2026-07-05';
SELECT 'deleteMain(期望1:07-06)' c, COUNT(*), GROUP_CONCAT(STATIS_DT) FROM ARCH_IT_MAIN;
DROP TABLE IF EXISTS ARCH_IT_MAIN; DROP TABLE IF EXISTS ARCH_IT_HIST;
SQL
```
Expected: count=2；insert=2 且 Y=v-y；deleteHist=0；max=2026-07-06；deleteMain 剩 1 行 2026-07-06。

- [ ] **Step 5: 提交**

```bash
cd /home/djdev/liuyang/yiti
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/mapper/StatShowArchiveMapper.java \
        performance-engine-center/src/main/resources/mapper/performance/StatShowArchiveMapper.xml \
        performance-engine-center/src/test/java/com/bank/branch/platform/performance/mapper/StatShowArchiveMapperIT.java
git commit -m "feat(perf): stat-show 归档 v2 按日 Mapper（count/insert/delete/max/deleteMain）+ 真库 IT"
```

---

## Task 3: 编排 `StatShowArchiveJob`（Mockito TDD）

**Files:**
- Rewrite: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/job/StatShowArchiveJob.java`
- Rewrite test: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/job/StatShowArchiveJobTest.java`

- [ ] **Step 1: 写失败测试**

```java
package com.bank.branch.platform.performance.job;

import com.bank.branch.platform.performance.mapper.StatShowArchiveMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * StatShowArchiveJob 编排单测（Mockito）：日增量幂等、旬边界按天清理、1 号按天瘦身、两表处理。
 * 约定：mock countByTableDate 默认 main 有数(=100)、hist 无数(=0) → 触发插入；具体用例覆盖。
 */
@ExtendWith(MockitoExtension.class)
class StatShowArchiveJobTest {

    private static final String CUST = "XAN_M98_CUST_STAT_SHOW3";
    private static final String EMP = "XAN_M98_EMP_STAT_SHOW3";

    @Mock
    private StatShowArchiveMapper mapper;
    @InjectMocks
    private StatShowArchiveJob job;

    // 昨天=2026-08-14（属 11~20 旬→_H3），旬首=08-11；应逐日 sync 08-11..08-14
    @Test
    void daily_gapFillsSliceStartToYesterday_intoRoutedHist() {
        when(mapper.countByTableDate(anyString(), anyString())).thenReturn(100L); // main
        when(mapper.countByTableDate(eq(CUST + "_H3"), anyString())).thenReturn(0L);
        when(mapper.countByTableDate(eq(EMP + "_H3"), anyString())).thenReturn(0L);

        job.run(LocalDate.of(2026, 8, 15)); // 昨天 08-14

        for (String main : new String[]{CUST, EMP}) {
            String h3 = main + "_H3";
            for (String d : new String[]{"2026-08-11", "2026-08-12", "2026-08-13", "2026-08-14"}) {
                verify(mapper).deleteHistByDate(h3, d);
                verify(mapper).insertHistByDate(h3, main, d);
            }
        }
    }

    // 源未就绪：main count=0 → 跳过，不删不插
    @Test
    void daily_whenMainEmpty_skips() {
        when(mapper.countByTableDate(anyString(), anyString())).thenReturn(0L);

        job.run(LocalDate.of(2026, 8, 15));

        verify(mapper, never()).insertHistByDate(anyString(), anyString(), anyString());
        verify(mapper, never()).deleteHistByDate(anyString(), anyString());
    }

    // 已同步：hist count == main count → 跳过插入
    @Test
    void daily_whenAlreadySynced_skipsInsert() {
        when(mapper.countByTableDate(anyString(), anyString())).thenReturn(100L); // main 与 hist 都 100

        job.run(LocalDate.of(2026, 8, 15));

        verify(mapper, never()).insertHistByDate(anyString(), anyString(), anyString());
    }

    // 边界 11 号：按天分批清 _H2 的上月 1~10（10 天各一次 deleteHistByDate）
    @Test
    void boundary_on11_deletesPrevMonth1To10_dayByDay() {
        when(mapper.countByTableDate(anyString(), anyString())).thenReturn(0L); // 让日增量跳过，聚焦清理
        job.run(LocalDate.of(2026, 8, 11));

        for (String main : new String[]{CUST, EMP}) {
            String h2 = main + "_H2";
            verify(mapper).deleteHistByDate(h2, "2026-07-01");
            verify(mapper).deleteHistByDate(h2, "2026-07-05");
            verify(mapper).deleteHistByDate(h2, "2026-07-10");
        }
    }

    // 边界 1 号：清 _H1 上上月 21~末（按天） + 主表瘦身
    @Test
    void boundary_on1_cleansH1TwoMonthsAgo_andPrunesMain() {
        when(mapper.countByTableDate(anyString(), anyString())).thenReturn(0L);
        when(mapper.selectMaxStatisDt(anyString(), eq("2026-07-01"), eq("2026-07-31"))).thenReturn("2026-07-31");

        job.run(LocalDate.of(2026, 8, 1));

        for (String main : new String[]{CUST, EMP}) {
            // 清 _H1 上上月(6月)21~30 按天
            verify(mapper).deleteHistByDate(main + "_H1", "2026-06-21");
            verify(mapper).deleteHistByDate(main + "_H1", "2026-06-30");
            // 瘦身：上月(7月)非月末按天删，保留 07-31
            verify(mapper).selectMaxStatisDt(main, "2026-07-01", "2026-07-31");
            verify(mapper).deleteMainByDate(main, "2026-07-01");
            verify(mapper).deleteMainByDate(main, "2026-07-30");
            verify(mapper, never()).deleteMainByDate(main, "2026-07-31"); // 保留月末
        }
    }

    // 瘦身保护：上月无数据(MAX null) → 不删主表
    @Test
    void prune_whenNoMax_skips() {
        when(mapper.countByTableDate(anyString(), anyString())).thenReturn(0L);
        lenient().when(mapper.selectMaxStatisDt(anyString(), anyString(), anyString())).thenReturn(null);

        job.run(LocalDate.of(2026, 8, 1));

        verify(mapper, never()).deleteMainByDate(anyString(), anyString());
    }

    // 非边界日：不清理、不瘦身
    @Test
    void nonBoundaryDay_noCleanupNoPrune() {
        when(mapper.countByTableDate(anyString(), anyString())).thenReturn(0L);

        job.run(LocalDate.of(2026, 8, 15));

        verify(mapper, never()).deleteMainByDate(anyString(), anyString());
        verify(mapper, never()).selectMaxStatisDt(anyString(), anyString(), anyString());
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `mvn test -pl performance-engine-center -Dtest=StatShowArchiveJobTest -DfailIfNoTests=false`
Expected: FAIL（`StatShowArchiveJob` 尚为 v1 或行为不符）。

- [ ] **Step 3: 写实现**

```java
package com.bank.branch.platform.performance.job;

import com.bank.branch.platform.performance.job.StatShowArchiveDates.CleanupRange;
import com.bank.branch.platform.performance.job.StatShowArchiveDates.MonthRange;
import com.bank.branch.platform.performance.mapper.StatShowArchiveMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 统计展示表「日增量归档 + 分批清理 + 主表瘦身」业务任务（由 Quartz 每天循环调用）.
 *
 * <p><strong>不加 @Transactional</strong>：每个按日 mapper 调用需独立提交（每次 ≤ 一天约 200 万行），
 * 避免整月/整旬巨型事务锁表。幂等：插入用 count 比对，删除删空即 no-op。
 *
 * <p>每次运行对 {@link #MAIN_TABLES} 两张主表依次：①日增量补全昨天所属旬 →
 * ②旬边界(1/11/21)按天清上一代旧旬 → ③每月 1 号按天瘦身上月（保留月末）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StatShowArchiveJob {

    /** 待归档的两张主表（固定白名单，历史表 = 主表名 + _H1/_H2/_H3）. */
    static final String[] MAIN_TABLES = {
            "XAN_M98_CUST_STAT_SHOW3",
            "XAN_M98_EMP_STAT_SHOW3"
    };

    private final StatShowArchiveMapper mapper;

    /** Quartz 调度入口：以当前日期执行. */
    public int run() {
        return run(LocalDate.now());
    }

    /**
     * 按指定运行日执行（供单测注入确定日期）.
     *
     * @return 本次插入历史表的总行数（跨两表累加）
     */
    public int run(LocalDate today) {
        LocalDate yesterday = today.minusDays(1);
        int inserted = 0;
        for (String main : MAIN_TABLES) {
            inserted += dailyIncremental(main, yesterday);
            boundaryCleanup(main, today);
            pruneMain(main, today);
        }
        log.info("[StatShowArchiveJob] {} 执行完成，昨天={}，共插入 {} 行", today, yesterday, inserted);
        return inserted;
    }

    /** ①日增量补全：把「昨天所属旬的旬首~昨天」逐日搬进对应历史表（幂等）. */
    private int dailyIncremental(String main, LocalDate yesterday) {
        String histTable = main + StatShowArchiveDates.histSuffix(yesterday);
        LocalDate start = StatShowArchiveDates.sliceStart(yesterday);
        int total = 0;
        for (LocalDate d : StatShowArchiveDates.datesInclusive(start, yesterday)) {
            total += syncDate(main, histTable, StatShowArchiveDates.fmt(d));
        }
        return total;
    }

    /** 单日幂等同步：main 无数→跳过；hist 已等量→跳过；否则删该日再插该日. */
    private int syncDate(String main, String histTable, String dt) {
        long mainCnt = mapper.countByTableDate(main, dt);
        if (mainCnt == 0) {
            return 0; // 源表当日数据未就绪，等下一轮
        }
        long histCnt = mapper.countByTableDate(histTable, dt);
        if (histCnt == mainCnt) {
            return 0; // 已同步
        }
        mapper.deleteHistByDate(histTable, dt); // 幂等重置（无主键，防重复）
        int n = mapper.insertHistByDate(histTable, main, dt);
        log.info("[StatShowArchiveJob] {} <- {} 同步 {} 行 @ {}", histTable, main, n, dt);
        return n;
    }

    /** ②旬边界清理：today 为 1/11/21 时，按天分批删「上一代」旧旬. */
    private void boundaryCleanup(String main, LocalDate today) {
        Optional<CleanupRange> opt = StatShowArchiveDates.boundaryCleanup(today);
        if (opt.isEmpty()) {
            return;
        }
        CleanupRange cr = opt.get();
        String histTable = main + cr.histSuffix();
        int deleted = 0;
        for (LocalDate d : StatShowArchiveDates.datesInclusive(cr.start(), cr.end())) {
            deleted += mapper.deleteHistByDate(histTable, StatShowArchiveDates.fmt(d));
        }
        log.info("[StatShowArchiveJob] 边界清理 {} {}~{} 删 {} 行", histTable, cr.start(), cr.end(), deleted);
    }

    /** ③主表瘦身：仅 1 号，保留上月 MAX(STATIS_DT)，按天分批删其余. */
    private void pruneMain(String main, LocalDate today) {
        Optional<MonthRange> opt = StatShowArchiveDates.pruneMonth(today);
        if (opt.isEmpty()) {
            return;
        }
        MonthRange r = opt.get();
        String keepDt = mapper.selectMaxStatisDt(main, StatShowArchiveDates.fmt(r.start()), StatShowArchiveDates.fmt(r.end()));
        if (keepDt == null || keepDt.isBlank()) {
            log.warn("[StatShowArchiveJob] 主表 {} 瘦身跳过：{}~{} 无数据", main, r.start(), r.end());
            return;
        }
        int deleted = 0;
        for (LocalDate d : StatShowArchiveDates.datesInclusive(r.start(), r.end())) {
            String ds = StatShowArchiveDates.fmt(d);
            if (!ds.equals(keepDt)) {
                deleted += mapper.deleteMainByDate(main, ds);
            }
        }
        log.info("[StatShowArchiveJob] 主表 {} 瘦身 {}~{} 保留 {}，删 {} 行", main, r.start(), r.end(), keepDt, deleted);
    }
}
```

- [ ] **Step 4: 运行确认通过**

Run: `mvn test -pl performance-engine-center -Dtest="StatShowArchiveJobTest,StatShowArchiveDatesTest" -DfailIfNoTests=false`
Expected: 两类全绿（Job 7 + Dates 12）。

- [ ] **Step 5: 提交**

```bash
cd /home/djdev/liuyang/yiti
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/job/StatShowArchiveJob.java \
        performance-engine-center/src/test/java/com/bank/branch/platform/performance/job/StatShowArchiveJobTest.java
git commit -m "feat(perf): stat-show 归档 v2 编排 StatShowArchiveJob（日增量+补全+分批清理+瘦身，逐日提交幂等）"
```

---

## Task 4: Quartz 瘦包装 + `@DisallowConcurrentExecution`

**Files:**
- Rewrite: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/job/quartz/StatShowArchiveQuartzJob.java`

- [ ] **Step 1: 写实现（无独立单测；Quartz 包装靠 mapper IT/编排单测覆盖业务，包装本身仅委托）**

```java
package com.bank.branch.platform.performance.job.quartz;

import com.bank.branch.platform.performance.job.StatShowArchiveJob;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 统计展示表旬度归档 Quartz 包装类（job_key=STAT_SHOW_ARCHIVE，cron 每天 6:30~18:30 循环）.
 *
 * <p>{@link DisallowConcurrentExecution}：同一 JobDetail 不并发执行——防同一天 13 次触发/多节点重叠
 * （单次可能耗时较长，且业务已幂等，重叠无意义）。集群下配合 QRTZ 行锁，同一时刻仅一个节点执行。
 *
 * <p>不加 @Component！Quartz 反射建实例 + AutowiringSpringBeanJobFactory 注入。execute() 只委托。
 */
@Slf4j
@DisallowConcurrentExecution
public class StatShowArchiveQuartzJob implements Job {

    @Autowired
    private StatShowArchiveJob statShowArchiveJob;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            statShowArchiveJob.run();
        } catch (Exception e) {
            log.error("[StatShowArchiveQuartzJob] 执行异常", e);
            throw new JobExecutionException(e, false);
        }
    }
}
```

- [ ] **Step 2: 全模块编译 + 相关单测通过（确认 v1 残留已清、整体编译无碍）**

Run: `mvn test -pl performance-engine-center -Dtest="StatShowArchive*" -DfailIfNoTests=false`
Expected: `BUILD SUCCESS`；Dates(12) + Job(7) 全绿。

- [ ] **Step 3: 提交**

```bash
cd /home/djdev/liuyang/yiti
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/job/quartz/StatShowArchiveQuartzJob.java
git commit -m "feat(perf): stat-show 归档 v2 Quartz 包装 + @DisallowConcurrentExecution"
```

---

## Task 5: SQL 迁移脚本（6 表 + 任务注册，cron 每天循环）

> **同事惯例**：新增表的建表 DDL 单独放 `docs/superpowers/sql/<日期>-xxx-ddl.sql`，写**完整显式 `CREATE TABLE IF NOT EXISTS`**（不用 `LIKE`），双库执行、幂等；汇总 `docs/schema/ddl-yiti-prod-golive.sql` 由 `gen-yiti-prod-golive.sh` 从真库重生成，不手改。故本任务拆成两个文件：DDL 文件 + 注册脚本。

**Files:**
- Create/已生成: `docs/superpowers/sql/2026-07-03-stat-show-archive-hist-ddl.sql`（6 张历史表显式 DDL）
- Rewrite: `docs/superpowers/sql/2026-07-03-stat-show-archive.sql`（仅任务注册）

- [ ] **Step 1a: 生成 6 张历史表显式 DDL 文件**

从权威源 `docs/schema/ddl-yiti-prod-golive.sql` **精确抽取**两张主表的完整 `CREATE TABLE` 块（勿手敲，避免列/charset 出错），各重命名生成 `_H1/_H2/_H3` 三张，`CREATE TABLE` 改为 `CREATE TABLE IF NOT EXISTS`，写入 `2026-07-03-stat-show-archive-hist-ddl.sql`（顶部加注释块：用途/与主表一致/幂等/双库执行）。索引名（`idx_statis_dt`/`idx_cust`）per-table 不冲突，逐字保留即可；cust 系列 `CHARSET=gbk`、emp 系列 `utf8mb3`，逐字保留。
> 本仓库已生成该文件（463 行，6 表：cust 88 字段行/gbk、emp 54 字段行/utf8mb3），可直接复用。

- [ ] **Step 1b: 写任务注册脚本（注册 only，建表前置引用 DDL 文件）**

```sql
-- ============================================================================
-- 统计展示表「日增量归档 + 分批清理 + 主表瘦身」(v2)：注册 Quartz 任务 STAT_SHOW_ARCHIVE
-- ★ 6 张历史表建表见同目录 2026-07-03-stat-show-archive-hist-ddl.sql（先在两库各执行该 DDL）。
-- cron '0 30 6-18 * * ?' = 每天 6:30~18:30（13 次）循环；yiti+onepl 双库；SYS_JOB_CONF INSERT IGNORE 幂等。
-- 生效：重启应用或 reschedule；JobService.syncJobsOnStartup 覆盖 QRTZ 触发器。
-- ============================================================================

-- ---------- 前置：先在 yiti 与 onepl 两库各执行 2026-07-03-stat-show-archive-hist-ddl.sql 建 6 张历史表 ----------

INSERT IGNORE INTO yiti.SYS_JOB_CONF
  (id, job_key, job_name, cron_expr, quartz_job_class, misfire_policy, status, allow_manual_trigger, remark, created_time)
VALUES
  (REPLACE(UUID(),'-',''), 'STAT_SHOW_ARCHIVE', '统计展示表日增量归档', '0 30 6-18 * * ?',
   'com.bank.branch.platform.performance.job.quartz.StatShowArchiveQuartzJob', 'FIRE_ONCE_NOW', 'ACTIVE', 1,
   '每天6:30~18:30循环：昨天旬块逐日增量搬入_H1/H2/H3(幂等)；1/11/21按天分批清上一代旧旬；1号按天分批瘦身主表留月末', NOW());

INSERT IGNORE INTO onepl.SYS_JOB_CONF
  (id, job_key, job_name, cron_expr, quartz_job_class, misfire_policy, status, allow_manual_trigger, remark, created_time)
VALUES
  (REPLACE(UUID(),'-',''), 'STAT_SHOW_ARCHIVE', '统计展示表日增量归档', '0 30 6-18 * * ?',
   'com.bank.branch.platform.performance.job.quartz.StatShowArchiveQuartzJob', 'FIRE_ONCE_NOW', 'ACTIVE', 1,
   '每天6:30~18:30循环：昨天旬块逐日增量搬入_H1/H2/H3(幂等)；1/11/21按天分批清上一代旧旬；1号按天分批瘦身主表留月末', NOW());
```

- [ ] **Step 1c: 语法验证（临时库，不碰真库）**

Run:
```bash
mysql -h127.0.0.1 -uroot -pdjdev <<'SQL'
DROP DATABASE IF EXISTS _stat_ddl_check; CREATE DATABASE _stat_ddl_check;
USE _stat_ddl_check; source docs/superpowers/sql/2026-07-03-stat-show-archive-hist-ddl.sql;
SELECT COUNT(*) 建表数_期望6 FROM information_schema.tables WHERE table_schema='_stat_ddl_check';
DROP DATABASE _stat_ddl_check;
SQL
```
Expected: 建表数=6，退出码 0。

- [ ] **Step 2: 提交**

```bash
cd /home/djdev/liuyang/yiti
git add docs/superpowers/sql/2026-07-03-stat-show-archive-hist-ddl.sql \
        docs/superpowers/sql/2026-07-03-stat-show-archive.sql
git commit -m "feat(perf): stat-show 归档 v2 SQL（6 表显式 DDL + 任务注册 cron 每天 6:30~18:30）"
```

---

## Task 6: 收尾验证

- [ ] **Step 1: 全量相关单测 + 模块编译**

Run: `mvn test -pl performance-engine-center -Dtest="StatShowArchive*" -DfailIfNoTests=false`
Expected: `BUILD SUCCESS`；`StatShowArchiveDatesTest`(12) + `StatShowArchiveJobTest`(7) 全绿。

- [ ] **Step 2: mapper SQL 真库验证（Task 2 Step 4b 的 mysql 脚本）已通过留档**

- [ ] **Step 3: 部署清单（人工，交付说明写入 PR/交接）**
  1. 在 **yiti 与 onepl 两库**各执行 `docs/superpowers/sql/2026-07-03-stat-show-archive.sql`。
  2. 重启应用（`JobService.syncJobsOnStartup` 覆盖生效）。
  3. 「任务调度」页确认 `STAT_SHOW_ARCHIVE`（cron `0 30 6-18 * * ?`、ACTIVE），手动触发一次；核对 `SYS_JOB_RUN_LOG` 与 6 张历史表行数。

---

## 风险 / 注意点

- **1 号最重**：H1 清理(≈2000万) + 主表瘦身(≈5800万) 同日；均已按天分批(每次≤200万) + 幂等，首个满足的循环轮完成、后续轮 no-op。必要时评估把 1 号重活排到独立早窗口。
- **首次上线存量**：历史多月存量的初始化/回填另开一次性分批脚本，不混入日常任务（本计划不含）。
- **补全依赖 count**：`countByTableDate` 走 `idx_statis_dt`；每轮对当前旬(≤10天)各 count 一次，成本可接受。若需更省可引入「已归档水位」表（后续优化）。
- **本地 IT 阻塞**：`onepl_test_bootstrap` 缺 `EVAL_ASSIGN_BATCH.total_rows` 致 perf 测试上下文起不来 → `StatShowArchiveMapperIT` 本地跑不了，用 Task 2 Step 4b 的 mysql 直连验证；IT 在正确 provision 的库/内网可跑。
- **双库**：yiti+onepl 都要建表+注册，漏一个则该环境任务不生效。
- **无 @Transactional**：`run()` 绝不能包一个大事务，否则又变巨型事务；保持逐日 mapper 调用各自提交。

---

## 自检（写作者已核对）

- **Spec 覆盖**：日增量插入✔(Task3 dailyIncremental)、补全✔(旬首~昨天逐日 sync)、旬边界按天清理✔(Task3 boundaryCleanup + Task1 boundaryCleanup)、清理口径 1号上上月/11·21号上月✔(Task1 测试)、主表瘦身保留月末✔(Task3 pruneMain)、6 表✔(Task5)、一条每天循环任务✔(Task4+5 cron)、幂等/并发✔(count 比对 + @DisallowConcurrentExecution)、按天分批避免大事务✔(全部按日 mapper + 无 @Transactional)。
- **占位符**：无。
- **类型/方法一致性**：`histSuffix/sliceStart/boundaryCleanup/pruneMonth/datesInclusive/fmt`、`CleanupRange(histSuffix,start,end)`、`MonthRange(start,end)`、mapper 5 方法签名 在 Task1/2/3 间一致。
