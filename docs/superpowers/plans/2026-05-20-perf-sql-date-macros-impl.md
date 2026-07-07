# 指标 SQL 日期宏 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use subagent-driven-development (recommended) or executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 `perf_metric_def.sql_text` 提供 8 个由后端按 `dataDate` 自动注入的强类型 `LocalDate` 命名参数（`:dateToday` / `:dateYesterday` / `:dateMonthEnd` / `:datePrevMonthEnd` / `:dateQuarterEnd` / `:datePrevQuarterEnd` / `:dateYearEnd` / `:datePrevYearEnd`），统一日期口径，免去业务方手写 MySQL 日期函数。

**Architecture:** 新增无状态 `DateMacroResolver`（纯函数）；在 `MetricCalcService.executeSqlAndPersist` 与 `MetricTrialService.runSql` 调 `SqlExecutor.execute` 之前合并到 params。Trial 路径系统宏覆盖用户 params 防伪造。`SqlValidator` / `SqlExecutor` 零改动。

**Tech Stack:** Java 17 + Spring `NamedParameterJdbcTemplate`；JUnit 5 + AssertJ + Mockito；Vue 3 (Element Plus) 前端。

**Spec:** `docs/superpowers/specs/2026-05-20-perf-sql-date-macros-design.md`

---

## File Structure

| 文件 | 类型 | 责任 |
|---|---|---|
| `performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/engine/DateMacroResolver.java` | 新增 | 纯函数 utility：`LocalDate base → Map<String,LocalDate>`（8 个宏） |
| `performance-engine-center/src/test/java/com/bank/branch/platform/performance/service/engine/DateMacroResolverTest.java` | 新增 | 6 个 surefire 用例（闰年/季初/季末/年初/普通日/null） |
| `performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/MetricCalcService.java` | 改动 | `executeSqlAndPersist` 注入 8 宏到 params |
| `performance-engine-center/src/test/java/com/bank/branch/platform/performance/service/MetricCalcServiceTest.java` | 改动 | 新增 1 case：宏被传入 sqlExecutor.execute |
| `performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/MetricTrialService.java` | 改动 | `runSql` 注入 8 宏（系统宏 > 用户 params 防伪造） |
| `performance-engine-center/src/test/java/com/bank/branch/platform/performance/service/MetricTrialServiceTest.java` | 改动 | 新增 2 case：宏覆盖用户、null dataDate 不抛 |
| `xanzc_frontend/src/views/perf/Metrics.vue` | 改动 | 编辑对话框 SQL 编辑器下方增加可用变量提示卡 |
| （副本目录）`../wangyq/xanzc_frontend/src/views/perf/Metrics.vue` | 同步 | vite 实际跑这份 |
| `docs/modules/performance-engine-center/03-接口设计与报文.md` | 改动 | 追加附录 §M「SQL 日期宏变量」 |
| `performance-engine-center/CLAUDE.md` | 改动 | V1.13 微调段加一行登记 |

---

### Task 1：DateMacroResolver + 6 surefire 用例（TDD）

**Files:**
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/engine/DateMacroResolver.java`
- Test: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/service/engine/DateMacroResolverTest.java`

- [ ] **Step 1: 写失败的单元测试（6 case）**

```java
package com.bank.branch.platform.performance.service.engine;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DateMacroResolver 单元测试（6 case：闰年/季初/季末/年初/普通日/null）.
 *
 * <p>覆盖 spec {@code docs/superpowers/specs/2026-05-20-perf-sql-date-macros-design.md} §2 边界用例.
 */
class DateMacroResolverTest {

    @Test
    void resolve_normalDay_returnsAllEightMacros() {
        Map<String, LocalDate> m = DateMacroResolver.resolve(LocalDate.of(2026, 5, 20));
        assertThat(m).containsOnlyKeys(
                "dateToday", "dateYesterday",
                "dateMonthEnd", "datePrevMonthEnd",
                "dateQuarterEnd", "datePrevQuarterEnd",
                "dateYearEnd", "datePrevYearEnd");
        assertThat(m).containsEntry("dateToday", LocalDate.of(2026, 5, 20));
        assertThat(m).containsEntry("dateYesterday", LocalDate.of(2026, 5, 19));
        assertThat(m).containsEntry("dateMonthEnd", LocalDate.of(2026, 5, 31));
        assertThat(m).containsEntry("datePrevMonthEnd", LocalDate.of(2026, 4, 30));
        assertThat(m).containsEntry("dateQuarterEnd", LocalDate.of(2026, 6, 30));
        assertThat(m).containsEntry("datePrevQuarterEnd", LocalDate.of(2026, 3, 31));
        assertThat(m).containsEntry("dateYearEnd", LocalDate.of(2026, 12, 31));
        assertThat(m).containsEntry("datePrevYearEnd", LocalDate.of(2025, 12, 31));
    }

    @Test
    void resolve_leapYearFeb29_handlesMonthBoundary() {
        Map<String, LocalDate> m = DateMacroResolver.resolve(LocalDate.of(2024, 2, 29));
        assertThat(m).containsEntry("dateMonthEnd", LocalDate.of(2024, 2, 29));
        assertThat(m).containsEntry("datePrevMonthEnd", LocalDate.of(2024, 1, 31));
        assertThat(m).containsEntry("dateQuarterEnd", LocalDate.of(2024, 3, 31));
        assertThat(m).containsEntry("dateYearEnd", LocalDate.of(2024, 12, 31));
    }

    @Test
    void resolve_q1MidJan_prevQuarterCrossesYear() {
        Map<String, LocalDate> m = DateMacroResolver.resolve(LocalDate.of(2026, 1, 15));
        assertThat(m).containsEntry("dateQuarterEnd", LocalDate.of(2026, 3, 31));
        assertThat(m).containsEntry("datePrevQuarterEnd", LocalDate.of(2025, 12, 31));
        assertThat(m).containsEntry("datePrevYearEnd", LocalDate.of(2025, 12, 31));
    }

    @Test
    void resolve_quarterLastDay_dateQuarterEndEqualsToday() {
        Map<String, LocalDate> m = DateMacroResolver.resolve(LocalDate.of(2026, 3, 31));
        assertThat(m).containsEntry("dateQuarterEnd", LocalDate.of(2026, 3, 31));
        assertThat(m).containsEntry("datePrevQuarterEnd", LocalDate.of(2025, 12, 31));
    }

    @Test
    void resolve_yearFirstDay_yesterdayCrossesYear() {
        Map<String, LocalDate> m = DateMacroResolver.resolve(LocalDate.of(2026, 1, 1));
        assertThat(m).containsEntry("dateYesterday", LocalDate.of(2025, 12, 31));
        assertThat(m).containsEntry("datePrevYearEnd", LocalDate.of(2025, 12, 31));
        assertThat(m).containsEntry("datePrevMonthEnd", LocalDate.of(2025, 12, 31));
    }

    @Test
    void resolve_null_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> DateMacroResolver.resolve(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dataDate");
    }
}
```

- [ ] **Step 2: 运行测试确认全部 Red**

```bash
mvn -pl performance-engine-center -Dtest=DateMacroResolverTest test
```
Expected: 6 case 全 FAIL，原因 `cannot find symbol: class DateMacroResolver`（编译失败）。

- [ ] **Step 3: 写最简实现让 6 case 通过**

```java
package com.bank.branch.platform.performance.service.engine;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 指标 SQL 日期宏解析器.
 *
 * <p>以 {@code dataDate} 为锚点，产出 8 个强类型 {@link LocalDate} 命名参数，
 * 供 SQL 类指标 sql_text 通过 {@code :dateXxx} 引用。
 *
 * <p>详见 spec {@code docs/superpowers/specs/2026-05-20-perf-sql-date-macros-design.md}.
 */
public final class DateMacroResolver {

    private DateMacroResolver() {
        // 工具类，禁止实例化
    }

    /**
     * 按 base 日期解析 8 个日期宏（顺序与 spec §2 一致）.
     *
     * @param base 锚点日期（必填）
     * @return 有序 Map，key 即 SQL 中的 {@code :name}
     * @throws IllegalArgumentException base 为 null
     */
    public static Map<String, LocalDate> resolve(LocalDate base) {
        if (base == null) {
            throw new IllegalArgumentException("dataDate 不能为空");
        }
        Map<String, LocalDate> macros = new LinkedHashMap<>();
        macros.put("dateToday", base);
        macros.put("dateYesterday", base.minusDays(1));
        macros.put("dateMonthEnd", base.with(TemporalAdjusters.lastDayOfMonth()));
        macros.put("datePrevMonthEnd", base.withDayOfMonth(1).minusDays(1));
        int quarter = (base.getMonthValue() - 1) / 3 + 1;
        LocalDate firstDayOfQuarter = LocalDate.of(base.getYear(), (quarter - 1) * 3 + 1, 1);
        macros.put("dateQuarterEnd",
                firstDayOfQuarter.plusMonths(2).with(TemporalAdjusters.lastDayOfMonth()));
        macros.put("datePrevQuarterEnd", firstDayOfQuarter.minusDays(1));
        macros.put("dateYearEnd", LocalDate.of(base.getYear(), 12, 31));
        macros.put("datePrevYearEnd", LocalDate.of(base.getYear() - 1, 12, 31));
        return macros;
    }
}
```

- [ ] **Step 4: 运行测试确认 6 case 全部 PASS**

```bash
mvn -pl performance-engine-center -Dtest=DateMacroResolverTest test
```
Expected: `Tests run: 6, Failures: 0, Errors: 0, Skipped: 0`。

- [ ] **Step 5: Commit**

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/engine/DateMacroResolver.java \
        performance-engine-center/src/test/java/com/bank/branch/platform/performance/service/engine/DateMacroResolverTest.java
git commit -m "$(cat <<'EOF'
feat(perf): 新增 DateMacroResolver 解析 8 个 SQL 日期宏

无状态 utility，以 dataDate 为锚点产出 LinkedHashMap<String, LocalDate>（顺序固定）：
dateToday/dateYesterday/dateMonthEnd/datePrevMonthEnd/
dateQuarterEnd/datePrevQuarterEnd/dateYearEnd/datePrevYearEnd

6 个 surefire 用例覆盖闰年 / 季初跨年 / 季末当日 / 年初跨年 / 普通日 / null 抛错。
本 commit 仅引入纯函数，未接入任何调用方。

EOF
)"
```

---

### Task 2：MetricCalcService 注入宏到生产计算路径

**Files:**
- Modify: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/MetricCalcService.java`（`executeSqlAndPersist` 方法体）
- Modify: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/service/MetricCalcServiceTest.java`（新增 1 case）

- [ ] **Step 1: 在 MetricCalcServiceTest 加失败测试**

定位现有 MetricCalcServiceTest，**新增**以下测试方法到类末尾（class body 内）：

```java
    @Test
    void executeSqlAndPersist_injectsDateMacrosIntoParams() {
        // 准备 SQL 类指标 def
        PerfMetricDef def = new PerfMetricDef();
        def.setMetricCode("M_TEST_MACRO");
        def.setBaseDim("EMP");
        def.setCalcLogicType("SQL");
        def.setSqlText("SELECT emp_id AS base_key, 1 AS metric_value FROM t WHERE dt = :dateMonthEnd");
        def.setValSlot(1);
        when(metricDefService.getByCodeOrNull("M_TEST_MACRO")).thenReturn(def);

        // sqlExecutor.execute 返回空结果，专注断言 params 内容
        when(sqlExecutor.execute(anyString(), anyMap(), any(Duration.class)))
                .thenReturn(java.util.Map.of());

        LocalDate dataDate = LocalDate.of(2026, 5, 20);
        metricCalcService.calcMetric("M_TEST_MACRO", dataDate, "V1", "MANUAL");

        // 验证宏全部注入
        ArgumentCaptor<java.util.Map<String, Object>> paramsCap =
                ArgumentCaptor.forClass(java.util.Map.class);
        verify(sqlExecutor).execute(eq(def.getSqlText()), paramsCap.capture(), any(Duration.class));
        java.util.Map<String, Object> captured = paramsCap.getValue();
        assertThat(captured)
                .containsEntry("dataDate", dataDate)
                .containsEntry("version", "V1")
                .containsEntry("dateToday", dataDate)
                .containsEntry("dateYesterday", dataDate.minusDays(1))
                .containsEntry("dateMonthEnd", LocalDate.of(2026, 5, 31))
                .containsEntry("datePrevMonthEnd", LocalDate.of(2026, 4, 30))
                .containsEntry("dateQuarterEnd", LocalDate.of(2026, 6, 30))
                .containsEntry("datePrevQuarterEnd", LocalDate.of(2026, 3, 31))
                .containsEntry("dateYearEnd", LocalDate.of(2026, 12, 31))
                .containsEntry("datePrevYearEnd", LocalDate.of(2025, 12, 31));
    }
```

确保文件 import 区域有：
```java
import org.mockito.ArgumentCaptor;
import java.time.Duration;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
```
（按文件已有 import 风格补齐，避免重复 import）

- [ ] **Step 2: 运行测试确认 Red**

```bash
mvn -pl performance-engine-center -Dtest=MetricCalcServiceTest#executeSqlAndPersist_injectsDateMacrosIntoParams test
```
Expected: FAIL，captured 没有 dateXxx 键。

- [ ] **Step 3: 修改 MetricCalcService.executeSqlAndPersist**

在 `MetricCalcService.java`（搜索 `private SubjectStats executeSqlAndPersist`）的方法体内，把：

```java
        Map<String, Object> params = new HashMap<>();
        params.put("dataDate", dataDate);
        params.put("version", version);
```

替换为：

```java
        Map<String, Object> params = new HashMap<>();
        params.put("dataDate", dataDate);
        params.put("version", version);
        // V1.13：注入 8 个日期宏（dateToday/dateYesterday/...），见 spec
        // docs/superpowers/specs/2026-05-20-perf-sql-date-macros-design.md
        params.putAll(com.bank.branch.platform.performance.service.engine.DateMacroResolver.resolve(dataDate));
```

并在文件顶部 import 区添加（按字母序）：
```java
import com.bank.branch.platform.performance.service.engine.DateMacroResolver;
```
然后把上面 inline 写的 FQCN 替换为简写 `DateMacroResolver.resolve(dataDate)`。

- [ ] **Step 4: 运行测试确认 PASS + 回归 MetricCalcServiceTest**

```bash
mvn -pl performance-engine-center -Dtest=MetricCalcServiceTest test
```
Expected: 原有用例 + 新增 1 case 全部 PASS。

- [ ] **Step 5: Commit**

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/MetricCalcService.java \
        performance-engine-center/src/test/java/com/bank/branch/platform/performance/service/MetricCalcServiceTest.java
git commit -m "$(cat <<'EOF'
feat(perf): MetricCalcService.executeSqlAndPersist 注入 8 个日期宏

调 sqlExecutor.execute 前调用 DateMacroResolver.resolve(dataDate)，
8 个 :dateXxx 命名参数随同 :dataDate/:version 一起灌进 NamedParameterJdbcTemplate
的 params Map。调度态/手动 execute/历史 recalc 三条入口共享此路径。

新增 1 case 用 ArgumentCaptor 校验 8 个宏 + 2 个固定参数全部进入 params。

EOF
)"
```

---

### Task 3：MetricTrialService 注入宏 + 系统宏覆盖用户 params

**Files:**
- Modify: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/MetricTrialService.java`（`runSql` 方法）
- Modify: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/service/MetricTrialServiceTest.java`（新增 2 case）

- [ ] **Step 1: 在 MetricTrialServiceTest 加 2 个失败测试**

类末尾追加：

```java
    @Test
    void runSql_userParamsCannotOverrideSystemMacros() {
        PerfMetricDef def = new PerfMetricDef();
        def.setMetricCode("M_TRIAL_MACRO");
        def.setBaseDim("EMP");
        def.setStatus("ACTIVE");
        def.setCalcLogicType("SQL");
        def.setSqlText("SELECT 1 AS base_key, 2 AS metric_value WHERE :dateToday IS NOT NULL");
        when(metricDefService.getByCodeOrNull("M_TRIAL_MACRO")).thenReturn(def);
        when(sqlExecutor.execute(anyString(), anyMap(), any(Duration.class)))
                .thenReturn(java.util.Map.of());

        // 用户尝试用 params 伪造 dateToday
        LocalDate dataDate = LocalDate.of(2026, 5, 20);
        java.util.Map<String, Object> userParams = new java.util.HashMap<>();
        userParams.put("dateToday", LocalDate.of(2099, 1, 1));
        userParams.put("dateMonthEnd", LocalDate.of(2099, 12, 31));
        userParams.put("customParam", "kept");

        metricTrialService.trial("M_TRIAL_MACRO", dataDate, 10, userParams);

        ArgumentCaptor<java.util.Map<String, Object>> cap =
                ArgumentCaptor.forClass(java.util.Map.class);
        verify(sqlExecutor).execute(anyString(), cap.capture(), any(Duration.class));
        java.util.Map<String, Object> sent = cap.getValue();
        // 系统宏必须覆盖用户伪造值
        assertThat(sent).containsEntry("dateToday", dataDate);
        assertThat(sent).containsEntry("dateMonthEnd", LocalDate.of(2026, 5, 31));
        // 用户的非宏参数保留
        assertThat(sent).containsEntry("customParam", "kept");
    }

    @Test
    void runSql_nullDataDate_doesNotInjectMacros() {
        PerfMetricDef def = new PerfMetricDef();
        def.setMetricCode("M_TRIAL_NULL_DATE");
        def.setBaseDim("EMP");
        def.setStatus("ACTIVE");
        def.setCalcLogicType("SQL");
        def.setSqlText("SELECT 1 AS base_key, 2 AS metric_value");
        when(metricDefService.getByCodeOrNull("M_TRIAL_NULL_DATE")).thenReturn(def);
        when(sqlExecutor.execute(anyString(), anyMap(), any(Duration.class)))
                .thenReturn(java.util.Map.of());

        // dataDate=null（兜底场景），不应因 resolver 抛 IAE 中断 trial
        metricTrialService.trial("M_TRIAL_NULL_DATE", null, 10, null);

        ArgumentCaptor<java.util.Map<String, Object>> cap =
                ArgumentCaptor.forClass(java.util.Map.class);
        verify(sqlExecutor).execute(anyString(), cap.capture(), any(Duration.class));
        java.util.Map<String, Object> sent = cap.getValue();
        // 不注入任何 dateXxx 宏
        assertThat(sent).doesNotContainKeys("dateToday", "dateMonthEnd", "datePrevYearEnd");
    }
```

确保 import：
```java
import org.mockito.ArgumentCaptor;
import java.time.Duration;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
```

- [ ] **Step 2: 运行测试确认 Red**

```bash
mvn -pl performance-engine-center -Dtest=MetricTrialServiceTest#runSql_userParamsCannotOverrideSystemMacros+MetricTrialServiceTest#runSql_nullDataDate_doesNotInjectMacros test
```
Expected: 两个 case FAIL（dateToday=2099/01/01 没被覆盖；null 路径抛 IAE 或 case 1 dateMonthEnd 不在 params）。

- [ ] **Step 3: 修改 MetricTrialService.runSql**

在 `MetricTrialService.java` 顶部 import 区按字母序追加：
```java
import com.bank.branch.platform.performance.service.engine.DateMacroResolver;
```

定位 `runSql` 方法体（约 122-154 行），把：

```java
        Map<String, Object> mergedParams = new HashMap<>();
        if (params != null) {
            mergedParams.putAll(params);
        }
        mergedParams.putIfAbsent("dataDate", dataDate);
```

替换为：

```java
        Map<String, Object> mergedParams = new HashMap<>();
        if (params != null) {
            mergedParams.putAll(params);
        }
        mergedParams.putIfAbsent("dataDate", dataDate);
        // V1.13：系统日期宏在 putAll(userParams) 之后注入，强制覆盖用户伪造的 :dateXxx
        // （dataDate=null 时跳过，宏可选；用户 SQL 若引用宏会被 SqlExecutor 反馈缺失值）
        if (dataDate != null) {
            mergedParams.putAll(DateMacroResolver.resolve(dataDate));
        }
```

- [ ] **Step 4: 运行测试确认全 PASS（含原有用例回归）**

```bash
mvn -pl performance-engine-center -Dtest=MetricTrialServiceTest test
```
Expected: 原有用例 + 新增 2 case 全部 PASS。

- [ ] **Step 5: Commit**

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/MetricTrialService.java \
        performance-engine-center/src/test/java/com/bank/branch/platform/performance/service/MetricTrialServiceTest.java
git commit -m "$(cat <<'EOF'
feat(perf): MetricTrialService.runSql 注入日期宏，强制覆盖用户伪造

mergedParams 顺序改为：userParams → :dataDate (putIfAbsent) → 8 个日期宏 (putAll)，
保证系统宏优先级最高，用户在 trial 请求 params 字段塞 dateToday=2099-01-01 会被覆盖。
dataDate=null 兜底分支跳过宏注入，避免破坏 trial 默认入口。

新增 2 case：
- runSql_userParamsCannotOverrideSystemMacros
- runSql_nullDataDate_doesNotInjectMacros

EOF
)"
```

---

### Task 4：前端 Metrics.vue 增加日期变量提示卡 + 同步 wangyq

**Files:**
- Modify: `xanzc_frontend/src/views/perf/Metrics.vue`（编辑对话框 SQL 编辑器下方）
- Sync: `../wangyq/xanzc_frontend/src/views/perf/Metrics.vue`（vite dev server 实际读取此目录）

- [ ] **Step 1: 在 Metrics.vue 的 SQL/EXPR 编辑器 `<el-form-item>` 之后追加提示卡**

定位 lines 191-202（当前 `<el-form-item :label="dlg.form.calcLogicType === 'EXPR' ? 'Groovy 表达式' : 'SQL 表达式 (支持 #{slot} 占位符)'">` 块），**紧跟其 `</el-form-item>` 之后**插入：

```vue
        <!-- V1.13：SQL 类指标可用的日期宏变量（后端按 dataDate 自动注入到 NamedParameter） -->
        <el-form-item v-if="dlg.form.calcLogicType === 'SQL'" label="">
          <div class="sql-date-macros">
            <div class="hint-title">可用日期变量（后端按 dataDate 自动计算注入）</div>
            <table class="hint-table">
              <tr><th style="width:180px">SQL 占位符</th><th>含义</th></tr>
              <tr><td><code>:dataDate</code></td><td>数据日期（=dateToday，由调度/试运行传入）</td></tr>
              <tr><td><code>:version</code></td><td>sys_control 当前版本</td></tr>
              <tr><td><code>:dateToday</code></td><td>当前日期 T</td></tr>
              <tr><td><code>:dateYesterday</code></td><td>T-1 上一日期</td></tr>
              <tr><td><code>:dateMonthEnd</code></td><td>本月最后一天</td></tr>
              <tr><td><code>:datePrevMonthEnd</code></td><td>上月最后一天</td></tr>
              <tr><td><code>:dateQuarterEnd</code></td><td>本季度最后一天</td></tr>
              <tr><td><code>:datePrevQuarterEnd</code></td><td>上季度最后一天</td></tr>
              <tr><td><code>:dateYearEnd</code></td><td>本年最后一天</td></tr>
              <tr><td><code>:datePrevYearEnd</code></td><td>上年最后一天（去年 12-31）</td></tr>
            </table>
            <div class="hint-foot">用法：<code>WHERE stat_date = :datePrevMonthEnd</code>。结果列必须含 <code>base_key</code> + <code>metric_value</code>。</div>
          </div>
        </el-form-item>
```

- [ ] **Step 2: 在 `<style scoped>` 中追加样式**

定位 Metrics.vue 中的 `<style scoped>` 块（搜索 `<style scoped>`），在块内末尾追加：

```css
.sql-date-macros {
  background: #f7f9fc;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  padding: 10px 12px;
  font-size: 12px;
  line-height: 1.5;
}
.sql-date-macros .hint-title {
  font-weight: 600;
  color: #303133;
  margin-bottom: 6px;
}
.sql-date-macros .hint-table {
  border-collapse: collapse;
  width: 100%;
}
.sql-date-macros .hint-table th,
.sql-date-macros .hint-table td {
  border: 1px solid #ebeef5;
  padding: 4px 8px;
  text-align: left;
  vertical-align: top;
}
.sql-date-macros .hint-table th {
  background: #fafafa;
  color: #606266;
  font-weight: 500;
}
.sql-date-macros code {
  background: #fff5e6;
  color: #b87600;
  padding: 0 4px;
  border-radius: 2px;
}
.sql-date-macros .hint-foot {
  margin-top: 8px;
  color: #909399;
}
```

- [ ] **Step 3: 验证前端语法（基本编译）**

```bash
cd /home/djdev/lf/yiti/xanzc_frontend && (npm ls vue 2>&1 | head -3) && echo "--- 修改文件存在性 ---" && wc -l src/views/perf/Metrics.vue
```
Expected: vue 版本输出 + Metrics.vue 行数大于改前。

- [ ] **Step 4: 同步到 wangyq 副本（vite 实际跑这份）**

```bash
cp /home/djdev/lf/yiti/xanzc_frontend/src/views/perf/Metrics.vue \
   /home/djdev/lf/wangyq/xanzc_frontend/src/views/perf/Metrics.vue && \
diff /home/djdev/lf/yiti/xanzc_frontend/src/views/perf/Metrics.vue \
     /home/djdev/lf/wangyq/xanzc_frontend/src/views/perf/Metrics.vue && \
echo "OK: 两份完全一致"
```
Expected: `OK: 两份完全一致`（diff 0 输出）。

- [ ] **Step 5: Commit**

```bash
git add xanzc_frontend/src/views/perf/Metrics.vue
git commit -m "$(cat <<'EOF'
feat(perf-fe): 指标编辑对话框增加日期变量提示卡

在 SQL 编辑器下方加只读 hint 表格，列出 8 个由后端自动注入的 :dateXxx 命名参数 +
固定的 :dataDate / :version。仅 calcLogicType=SQL 时展示，Groovy 路径无关。

注：wangyq 副本同步已 cp，不进入本仓库提交范围（wangyq 是另一独立目录）。

EOF
)"
```

---

### Task 5：文档同步（03-接口设计与报文.md 附录 §M + CLAUDE.md 登记）

**Files:**
- Modify: `docs/modules/performance-engine-center/03-接口设计与报文.md`
- Modify: `performance-engine-center/CLAUDE.md`

- [ ] **Step 1: 在 03-接口设计与报文.md 末尾追加附录 §M**

定位文件末尾（在已有附录之后，新加一节）：

```markdown

---

## 附录 §M：SQL 指标可用的日期宏变量（V1.13 新增）

SQL 类指标的 `sql_text` 可直接引用以下命名参数，由后端在执行前自动按 `dataDate` 注入到 `NamedParameterJdbcTemplate`，类型固定 `java.time.LocalDate`：

| SQL 占位符 | 含义 | 计算规则（以 dataDate 为锚点） |
|---|---|---|
| `:dataDate` | 数据日期 | 由调度/试运行入参传入 |
| `:version` | sys_control 当前版本 | 由调度上下文解析 |
| `:dateToday` | 当前日期 T | `dataDate` 本身 |
| `:dateYesterday` | T-1 | `dataDate - 1 day` |
| `:dateMonthEnd` | 本月最后一天 | `lastDayOfMonth(dataDate)` |
| `:datePrevMonthEnd` | 上月最后一天 | `dataDate.withDayOfMonth(1) - 1 day` |
| `:dateQuarterEnd` | 本季度最后一天 | 本季度末月 last day |
| `:datePrevQuarterEnd` | 上季度最后一天 | 本季度首日 - 1 day（跨年自动到去年 12-31） |
| `:dateYearEnd` | 本年最后一天 | `(year, 12, 31)` |
| `:datePrevYearEnd` | 上年最后一天 | `(year-1, 12, 31)` |

**使用约束**：
- 必须用 `:name` 形式，不支持 `${name}` 或 `?`（`NamedParameterJdbcTemplate` 限制）。
- 不存在的宏名（如拼写错误 `:dateMontEnd`）→ `PERF-42201 METRIC_CALC_LOGIC_INVALID`，错误消息含 `No value supplied for the SQL parameter`。
- Trial 路径用户在 `params` 中传入同名键会被系统宏覆盖（防伪造）。
- 生产路径无用户参数渠道，宏值由系统全权计算。

**示例**：
```sql
-- 上月底存款快照
SELECT cust_id AS base_key, dep_bal AS metric_value
FROM cust_deposit_snapshot
WHERE snap_date = :datePrevMonthEnd;

-- 本月累计中收（月初到 T）
SELECT emp_id AS base_key, SUM(fee_amt) AS metric_value
FROM fee_income_daily
WHERE stat_date BETWEEN DATE_FORMAT(:dateToday, '%Y-%m-01') AND :dateToday
GROUP BY emp_id;
```

设计文档：`docs/superpowers/specs/2026-05-20-perf-sql-date-macros-design.md`。
```

- [ ] **Step 2: 在 performance-engine-center/CLAUDE.md 顶部「模块概述」上方或 V1.13 区段追加一行**

定位 V1.13 / V1.12 节附近，找一个语义合适的位置（可在「## V1.8 微调」类似的"微调"段落区追加新段）：

在文件最后一个"V1.x 微调"段（V1.8 微调段）**之后**追加：

```markdown

## V1.13 微调（2026-05-20）

- 新增 `service/engine/DateMacroResolver`：纯函数 utility，按 `dataDate` 锚点产出 8 个
  `LocalDate` 命名参数（`:dateToday`/`:dateYesterday`/`:dateMonthEnd`/`:datePrevMonthEnd`/
  `:dateQuarterEnd`/`:datePrevQuarterEnd`/`:dateYearEnd`/`:datePrevYearEnd`），供 SQL 类
  指标 `sql_text` 引用，免去业务方手写 MySQL 日期函数。
- `MetricCalcService.executeSqlAndPersist` 与 `MetricTrialService.runSql` 调
  `sqlExecutor.execute` 前 `params.putAll(DateMacroResolver.resolve(dataDate))`；
  trial 路径系统宏优先级高于用户 params 防伪造；`dataDate=null` 兜底跳过宏注入。
- `SqlValidator` / `SqlExecutorImpl` 零改动；现网 sql_text 已 grep 验证无 `:dateXxx` 冲突。
- 前端 `xanzc_frontend/src/views/perf/Metrics.vue` 在 SQL 编辑器下方加只读提示卡。
- Spec：`docs/superpowers/specs/2026-05-20-perf-sql-date-macros-design.md`
- Plan：`docs/superpowers/plans/2026-05-20-perf-sql-date-macros-impl.md`
- 测试：DateMacroResolverTest 6 case + MetricCalcServiceTest +1 + MetricTrialServiceTest +2
  = 净增 9 case，全部 surefire 层。
```

- [ ] **Step 3: Commit**

```bash
git add docs/modules/performance-engine-center/03-接口设计与报文.md \
        performance-engine-center/CLAUDE.md
git commit -m "$(cat <<'EOF'
docs(perf): 同步 SQL 日期宏到 03 接口文档附录 + CLAUDE.md V1.13 微调

- 03-接口设计与报文.md 追加附录 §M：8 个 :dateXxx 占位符表 + 使用约束 + 2 例
- performance-engine-center/CLAUDE.md 加 V1.13 微调段，登记 DateMacroResolver
  与两条调用方改造范围，链接 spec/plan/测试净增数

EOF
)"
```

---

### Task 6：模块级 mvn 全量回归 + 端到端 smoke

**Files:**（无修改，仅验证）
- 全模块 surefire + failsafe

- [ ] **Step 1: 跑 performance-engine-center surefire 全量**

```bash
mvn -pl performance-engine-center clean test
```
Expected: `BUILD SUCCESS`；Tests run 计数应 ≥ 上次 baseline + 9（DateMacroResolverTest 6 + MetricCalcService +1 + MetricTrialService +2）。失败立刻终止，回查上面 Task 1-3。

- [ ] **Step 2: 跑 performance-engine-center failsafe（IT 层兜底，不应被 9 个 surefire 改动影响）**

```bash
mvn -pl performance-engine-center verify -DskipITs=false
```
Expected: 已知 baseline 失败不变（V1.13 # 1b spike 2 + V1.7 已知 3 等，参考 CLAUDE.md V1.13 # 1 节），不应**新增**失败。如新增失败，回查 Task 2/3 的 MetricCalcService/MetricTrialService 是否破坏 IT 数据准备。

- [ ] **Step 3: 端到端 smoke（启 bootstrap + curl trial-run）**

如果本地 bootstrap 已起，否则跳过；起服务命令：
```bash
mvn -pl bootstrap spring-boot:run &
# 等启动日志 "Started BranchPlatformApplication"
```

取一条 ACTIVE+SQL 类指标 metricCode（可 `mysql -uroot -pdjdev yiti -e "SELECT metric_code FROM PERF_METRIC_DEF WHERE status='ACTIVE' AND calc_logic_type='SQL' AND deleted=0 LIMIT 1"` 拿）；记为 `$CODE`，临时把它的 sql_text 改为引用宏（或事先准备好一条测试指标）：

```sql
UPDATE yiti.PERF_METRIC_DEF
SET sql_text = 'SELECT 1 AS base_key, DAYOFMONTH(:dateMonthEnd) AS metric_value'
WHERE metric_code = '<CODE>' AND deleted = 0;
```

然后 curl 试运行（dataDate 必须传以触发宏注入）：
```bash
curl -s -X POST "http://localhost:8080/api/perf/metrics/<CODE>/trial-run" \
  -H "Content-Type: application/json" \
  -H "Cookie: <session cookie>" \
  -d '{"dataDate":"2026-05-20","sampleSize":5}' | jq .
```
Expected: 返回 `status=SUCCESS`，`sampleRows[0].metricValue` 应等于 31（2026-05 月最后一天 5-31 的 DAYOFMONTH）。

- [ ] **Step 4: 还原临时数据 + 记录验证结果**

```sql
-- 用改前的 sql_text 还原；如果是新建测试指标则直接软删
UPDATE yiti.PERF_METRIC_DEF SET deleted=1 WHERE metric_code='<CODE>' AND metric_name LIKE 'TEST_MACRO%';
```

- [ ] **Step 5: 不需要 commit（无文件变更）**

如有 spec / plan 修订，单独 commit；否则任务完成。

---

## Self-Review Notes（计划自检结论）

- **Spec coverage**：
  - §2 8 个宏 → Task 1 实现 + 测试覆盖 ✓
  - §3 架构 / 注入点 → Task 2 (calc) + Task 3 (trial) ✓
  - §4 SQL 示例 → Task 5 文档附录 §M ✓
  - §5 错误处理 → Task 3 step 3 注释说明 "宏可选" ✓（运行期由 SqlExecutor 现有 catch 翻译）
  - §6 前端提示卡 → Task 4 ✓
  - §7 文档同步 → Task 5 ✓
  - §8 测试矩阵 → Task 1+2+3 共 9 case ✓
  - §9 YAGNI → 计划未引入 monaco-editor / 自定义宏 / ${MACRO} / autocomplete ✓
  - §10 兼容性 / 回滚 → Task 1 是新文件，回滚把 Task 2/3 的 putAll 两行删掉即可 ✓
- **Placeholder scan**：所有步骤均有可执行命令 + 可粘贴代码块，无 TBD/TODO。
- **Type consistency**：`DateMacroResolver.resolve(LocalDate) → Map<String,LocalDate>` 在 Task 1/2/3 完全一致；calls 全用 `Map<String,Object>` 接收（向上兼容）。
- **关键风险点**：
  - Task 2/3 加 `putAll(macros)` 后，所有现存 SQL 多了 8 个未引用 NamedParameter。NamedParameterJdbcTemplate 对"params 多余于 SQL 引用"是宽容的（仅"SQL 引用了 params 没有"会报错），无副作用。
  - 前端 wangyq cp 必须做，否则浏览器看不到提示卡（项目记忆 `reference_frontend_dual_dirs.md`）。
