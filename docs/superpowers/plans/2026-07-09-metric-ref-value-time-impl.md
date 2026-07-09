# 引用指标「取值时间」实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 EXPR/GROOVY 指标公式在引用其他指标结果时，可为每次引用指定取值时间（今日/昨日/上月末/上季末/上年末），并保证除数为 0 时不报错。

**Architecture:** 取值时间以 token 后缀（`M_A__PME` 等）承载在 `exprText`，无表结构变更。计算引擎按最后一个 `__` 拆 `(baseCode, timePoint)`，用 `DateMacroResolver` 复用已有 5 个日期锚点定位宽表历史行，以完整 token 为 Groovy 变量名绑定。除法走注入的 `div()` 安全闭包 + 执行器 `ArithmeticException` 兜底。

**Tech Stack:** Spring Boot 3.2.3 / JDK 17 / MyBatis / Groovy(SecureAST) / JUnit5 + Mockito / AssertJ。

**Spec:** `docs/superpowers/specs/2026-07-09-metric-ref-value-time-design.md`

**根 CLAUDE.md 红线：** 严格 TDD（红-绿-重构，每步独立 commit）；中文注释 + UTF-8；子代理 model ≥ sonnet。

---

## 文件结构

**新建**
- `performance-engine-center/src/main/java/com/bank/branch/platform/performance/enums/MetricValueTimeEnum.java` — 取值时间枚举（token 后缀 ↔ DateMacroResolver 宏 key ↔ 日期解析）
- `performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/engine/MetricRefTokenParser.java` — 把 exprText 里的 `M_xxx` token 解析成 `RefToken(token, baseCode, timePoint)`
- `performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/engine/SafeDivClosure.java` — 除法安全闭包（除 0 → 0，固定 scale 10）
- 测试：`.../enums/MetricValueTimeEnumTest.java`、`.../service/engine/MetricRefTokenParserTest.java`

**修改**
- `service/MetricCalcService.java` — token 解析接入取值/slot 预查/绑定（`extractMetricCodesFromExpr` / `resolveSlotMap` / `loadRefValuesBySlotMap` / `executeGroovyAndPersist` / `loadGroovyVarsForSubject`）
- `service/engine/GroovyExecutorImpl.java` — 注入 `div` 闭包 + `ArithmeticException` → 0 兜底
- `service/MetricDefService.java` — 保留后缀校验（create / update / import 入口）
- `enums/PerfErrorCode.java` — 新增 `PERF-42212 METRIC_CODE_RESERVED_SUFFIX`
- 前端 `xanzc_frontend/src/views/perf/Metrics.vue`（+ 可能 `src/api/metrics.js`）— 取值时间下拉 + 除法生成 `div()` + token 反解析展示

---

## Task 1: 取值时间枚举 MetricValueTimeEnum

**Files:**
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/enums/MetricValueTimeEnum.java`
- Test: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/enums/MetricValueTimeEnumTest.java`

- [ ] **Step 1: 写失败测试**

```java
package com.bank.branch.platform.performance.enums;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;

class MetricValueTimeEnumTest {

    private final LocalDate base = LocalDate.of(2026, 7, 9); // 2026年3季度中

    @Test
    void bySuffix_matchesEnumOrNull() {
        assertThat(MetricValueTimeEnum.bySuffix("PME")).isEqualTo(MetricValueTimeEnum.PME);
        assertThat(MetricValueTimeEnum.bySuffix("D1")).isEqualTo(MetricValueTimeEnum.D1);
        assertThat(MetricValueTimeEnum.bySuffix("PQE")).isEqualTo(MetricValueTimeEnum.PQE);
        assertThat(MetricValueTimeEnum.bySuffix("PYE")).isEqualTo(MetricValueTimeEnum.PYE);
        assertThat(MetricValueTimeEnum.bySuffix("XXX")).isNull();
        assertThat(MetricValueTimeEnum.bySuffix(null)).isNull();
    }

    @Test
    void resolve_returnsExpectedAnchorDates() {
        assertThat(MetricValueTimeEnum.TODAY.resolve(base)).isEqualTo(LocalDate.of(2026, 7, 9));
        assertThat(MetricValueTimeEnum.D1.resolve(base)).isEqualTo(LocalDate.of(2026, 7, 8));
        assertThat(MetricValueTimeEnum.PME.resolve(base)).isEqualTo(LocalDate.of(2026, 6, 30));
        assertThat(MetricValueTimeEnum.PQE.resolve(base)).isEqualTo(LocalDate.of(2026, 6, 30));
        assertThat(MetricValueTimeEnum.PYE.resolve(base)).isEqualTo(LocalDate.of(2025, 12, 31));
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `mvn -q -pl performance-engine-center test -Dtest=MetricValueTimeEnumTest`
Expected: 编译失败（`MetricValueTimeEnum` 不存在）

- [ ] **Step 3: 写最简实现**

```java
package com.bank.branch.platform.performance.enums;

import com.bank.branch.platform.performance.service.engine.DateMacroResolver;

import java.time.LocalDate;

/**
 * 引用指标取值时间枚举.
 *
 * <p>token 后缀 ↔ {@link DateMacroResolver} 宏 key ↔ 相对 dataDate 的锚点日期。
 * <p>TODAY 无后缀（默认）；其余为系统保留后缀，禁止真实指标编码以其结尾。
 */
public enum MetricValueTimeEnum {

    /** 今日（默认，无后缀）. */
    TODAY(null, "dateToday"),
    /** 昨日. */
    D1("D1", "dateYesterday"),
    /** 上月末. */
    PME("PME", "datePrevMonthEnd"),
    /** 上季末. */
    PQE("PQE", "datePrevQuarterEnd"),
    /** 上年末. */
    PYE("PYE", "datePrevYearEnd");

    private final String suffix;
    private final String macroKey;

    MetricValueTimeEnum(String suffix, String macroKey) {
        this.suffix = suffix;
        this.macroKey = macroKey;
    }

    /** token 后缀（TODAY 为 null）. */
    public String getSuffix() {
        return suffix;
    }

    /** 按后缀返回枚举；无后缀/不匹配返回 null（TODAY 有意不通过 suffix 匹配）. */
    public static MetricValueTimeEnum bySuffix(String suffix) {
        if (suffix == null) {
            return null;
        }
        for (MetricValueTimeEnum e : values()) {
            if (suffix.equals(e.suffix)) {
                return e;
            }
        }
        return null;
    }

    /** 以 dataDate 为锚点解析目标日期（复用 DateMacroResolver 已算好的锚点，零新增日期数学）. */
    public LocalDate resolve(LocalDate dataDate) {
        return DateMacroResolver.resolve(dataDate).get(macroKey);
    }
}
```

- [ ] **Step 4: 运行确认通过**

Run: `mvn -q -pl performance-engine-center test -Dtest=MetricValueTimeEnumTest`
Expected: PASS（2 个方法全绿）

- [ ] **Step 5: 提交**

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/enums/MetricValueTimeEnum.java \
        performance-engine-center/src/test/java/com/bank/branch/platform/performance/enums/MetricValueTimeEnumTest.java
git commit -m "feat(perf): 新增引用指标取值时间枚举 MetricValueTimeEnum"
```

---

## Task 2: token 解析器 MetricRefTokenParser

**Files:**
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/engine/MetricRefTokenParser.java`
- Test: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/service/engine/MetricRefTokenParserTest.java`

- [ ] **Step 1: 写失败测试**

```java
package com.bank.branch.platform.performance.service.engine;

import com.bank.branch.platform.performance.enums.MetricValueTimeEnum;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MetricRefTokenParserTest {

    @Test
    void parse_plainCode_isToday() {
        List<MetricRefTokenParser.RefToken> t = MetricRefTokenParser.parse("M_A + 1");
        assertThat(t).hasSize(1);
        assertThat(t.get(0).token()).isEqualTo("M_A");
        assertThat(t.get(0).baseCode()).isEqualTo("M_A");
        assertThat(t.get(0).timePoint()).isEqualTo(MetricValueTimeEnum.TODAY);
    }

    @Test
    void parse_suffixedCode_splitsBaseAndTimePoint() {
        List<MetricRefTokenParser.RefToken> t = MetricRefTokenParser.parse("M_A - M_A__D1 + M_B__PME");
        assertThat(t).extracting("token").containsExactly("M_A", "M_A__D1", "M_B__PME");
        assertThat(t).extracting("baseCode").containsExactly("M_A", "M_A", "M_B");
        assertThat(t).extracting("timePoint").containsExactly(
                MetricValueTimeEnum.TODAY, MetricValueTimeEnum.D1, MetricValueTimeEnum.PME);
    }

    @Test
    void parse_underscoreCode_notMisSplit() {
        // 真实编码含下划线，尾段非枚举后缀 → 整体为 baseCode（今日）
        List<MetricRefTokenParser.RefToken> t = MetricRefTokenParser.parse("M_AUM_TOTAL + M_AUM_TOTAL__PYE");
        assertThat(t.get(0).baseCode()).isEqualTo("M_AUM_TOTAL");
        assertThat(t.get(0).timePoint()).isEqualTo(MetricValueTimeEnum.TODAY);
        assertThat(t.get(1).baseCode()).isEqualTo("M_AUM_TOTAL");
        assertThat(t.get(1).timePoint()).isEqualTo(MetricValueTimeEnum.PYE);
    }

    @Test
    void parse_deDupsKeepingOrder() {
        List<MetricRefTokenParser.RefToken> t = MetricRefTokenParser.parse("M_A + M_A - M_A__D1");
        assertThat(t).extracting("token").containsExactly("M_A", "M_A__D1");
    }

    @Test
    void parse_blankOrNull_empty() {
        assertThat(MetricRefTokenParser.parse(null)).isEmpty();
        assertThat(MetricRefTokenParser.parse("  ")).isEmpty();
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `mvn -q -pl performance-engine-center test -Dtest=MetricRefTokenParserTest`
Expected: 编译失败（`MetricRefTokenParser` 不存在）

- [ ] **Step 3: 写最简实现**

```java
package com.bank.branch.platform.performance.service.engine;

import com.bank.branch.platform.performance.enums.MetricValueTimeEnum;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 指标引用 token 解析器.
 *
 * <p>把 exprText 里的 {@code M_xxx} 字面量解析成 {@link RefToken}：按<strong>最后一个 {@code __}</strong>
 * 拆分，尾段命中 {@link MetricValueTimeEnum} 保留后缀 → (baseCode, timePoint)；否则整体为 baseCode（今日）。
 * <p>去重保序：同一 token 只出现一次。
 */
public final class MetricRefTokenParser {

    private MetricRefTokenParser() {
    }

    /** 指标编码字面量：M_ 开头，后接字母数字下划线. */
    private static final Pattern METRIC_CODE_PATTERN = Pattern.compile("\\bM_[A-Za-z0-9_]+\\b");

    /** 单个引用 token 的解析结果. */
    public record RefToken(String token, String baseCode, MetricValueTimeEnum timePoint) {
    }

    /** 解析 exprText 中所有引用 token，去重保序. */
    public static List<RefToken> parse(String exprText) {
        if (exprText == null || exprText.isBlank()) {
            return List.of();
        }
        LinkedHashMap<String, RefToken> out = new LinkedHashMap<>();
        Matcher m = METRIC_CODE_PATTERN.matcher(exprText);
        while (m.find()) {
            String token = m.group();
            out.putIfAbsent(token, split(token));
        }
        return new ArrayList<>(out.values());
    }

    /** 单 token 拆分：尾段命中保留后缀→历史档；否则整体 baseCode（今日）. */
    static RefToken split(String token) {
        int idx = token.lastIndexOf("__");
        if (idx > 0) {
            String suffix = token.substring(idx + 2);
            MetricValueTimeEnum tp = MetricValueTimeEnum.bySuffix(suffix);
            if (tp != null) {
                return new RefToken(token, token.substring(0, idx), tp);
            }
        }
        return new RefToken(token, token, MetricValueTimeEnum.TODAY);
    }
}
```

- [ ] **Step 4: 运行确认通过**

Run: `mvn -q -pl performance-engine-center test -Dtest=MetricRefTokenParserTest`
Expected: PASS（5 个方法全绿）

- [ ] **Step 5: 提交**

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/engine/MetricRefTokenParser.java \
        performance-engine-center/src/test/java/com/bank/branch/platform/performance/service/engine/MetricRefTokenParserTest.java
git commit -m "feat(perf): 新增指标引用 token 解析器 MetricRefTokenParser"
```

---

## Task 3: 计算引擎接入 token 取值（MetricCalcService）

**Files:**
- Modify: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/MetricCalcService.java`
- Test: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/service/MetricCalcServiceMultiSubjectTest.java`

**说明**：现状 `loadRefValuesBySlotMap:390` 对所有引用固定用 `dataDate` 读宽表、以 metricCode 为变量名。改造为：按 RefToken 逐个解析目标日期、以完整 token 为变量名绑定；slot 预查改用 baseCode。

- [ ] **Step 1: 写失败测试（历史档取值 + 变量绑定）**

在 `MetricCalcServiceMultiSubjectTest` 增加。先确认该测试类里 `groovyExecutor` / `empIndexResultMapper` 均为 `@Mock`、`metricCalcService` 为 `@InjectMocks`（与 `MetricCalcServiceTest` 同款）。新增：

```java
@Test
@DisplayName("EXPR 引用含取值时间后缀：按 DateMacroResolver 目标日期读宽表并以完整 token 绑定")
void calcMetric_exprWithValueTimeSuffix_bindsHistoricalValues() {
    // def: baseDim=EMP, EXPR, slot=5, exprText 引用 M_A(今日) 与 M_A__D1(昨日)
    PerfMetricDef def = new PerfMetricDef();
    def.setMetricCode("TEST_METRIC_DELTA");
    def.setBaseDim("EMP");
    def.setCalcLogicType("EXPR");
    def.setValSlot(5);
    def.setExprText("M_A - M_A__D1");
    def.setDeleted(0);
    when(metricDefService.getByCodeOrNull("TEST_METRIC_DELTA")).thenReturn(def);

    LocalDate dataDate = LocalDate.of(2026, 7, 9);
    String version = "V1";
    // 该日宽表主体只有 E001
    when(empIndexResultMapper.selectDistinctEmpIds(dataDate, version)).thenReturn(List.of("E001"));
    // 基础指标 M_A 的 slot = 3
    when(empIndexResultMapper.selectValSlotsByCodes(anyList()))
            .thenReturn(Map.of("M_A", 3));
    // 今日 M_A(slot3)=100，昨日 M_A(slot3)=30
    when(empIndexResultMapper.selectValBySlot("E001", 3, dataDate, version))
            .thenReturn(new BigDecimal("100"));
    when(empIndexResultMapper.selectValBySlot("E001", 3, LocalDate.of(2026, 7, 8), version))
            .thenReturn(new BigDecimal("30"));
    // 捕获传给 Groovy 的变量表
    ArgumentCaptor<Map<String, Object>> varsCaptor = ArgumentCaptor.forClass(Map.class);
    when(groovyExecutor.execute(eq("M_A - M_A__D1"), varsCaptor.capture(), any(Duration.class)))
            .thenReturn(new BigDecimal("70"));

    metricCalcService.calcMetric("TEST_METRIC_DELTA", dataDate, version);

    Map<String, Object> vars = varsCaptor.getValue();
    assertThat(vars).containsEntry("M_A", new BigDecimal("100"));
    assertThat(vars).containsEntry("M_A__D1", new BigDecimal("30"));
    verify(empIndexResultMapper).insertSlotValue("E001", dataDate, version, 5, new BigDecimal("70"));
}
```

（import 补：`org.mockito.ArgumentCaptor`、`java.util.Map`、`static ...anyList`。）

- [ ] **Step 2: 运行确认失败**

Run: `mvn -q -pl performance-engine-center test -Dtest=MetricCalcServiceMultiSubjectTest#calcMetric_exprWithValueTimeSuffix_bindsHistoricalValues`
Expected: FAIL —— 现状取值用 `M_A__D1` 当 baseCode 查 slot（查无→ZERO）且用 dataDate 读，`vars` 不含 `M_A__D1` 正确昨日值，绑定断言失败。

- [ ] **Step 3: 改实现**

改 `MetricCalcService`。3.1 `executeGroovyAndPersist` 里把 refCodes 构造换成 RefToken 列表：

```java
// 原（约 321-332 行）：mergeRefCodes(parseRefMetricCodes(...), extractMetricCodesFromExpr(...)) → refCodes/slotMap
// 改为：
List<MetricRefTokenParser.RefToken> refTokens = MetricRefTokenParser.parse(def.getExprText());
// 声明式 refMetricCodes 里的裸编码按今日补齐（去重）
for (String declared : parseRefMetricCodes(def.getRefMetricCodes())) {
    boolean exists = refTokens.stream().anyMatch(rt -> rt.token().equals(declared));
    if (!exists) {
        refTokens.add(new MetricRefTokenParser.RefToken(declared, declared, MetricValueTimeEnum.TODAY));
    }
}
Duration timeout = Duration.ofSeconds(perfEngineProperties == null
        ? 30 : Math.max(1, perfEngineProperties.getSqlTimeoutSeconds()));
// slot 预查用 baseCode 去重
List<String> baseCodes = refTokens.stream().map(MetricRefTokenParser.RefToken::baseCode).distinct().toList();
Map<String, Integer> slotMap = baseCodes.isEmpty() ? Map.of()
        : resolveSlotMap(def.getBaseDim(), baseCodes);
```

3.2 for-subject 循环里改调新签名：

```java
Map<String, Object> vars = loadRefValuesByTokens(subject, refTokens, slotMap,
        def.getBaseDim(), dataDate, version);
```

3.3 用新方法 `loadRefValuesByTokens` 替换 `loadRefValuesBySlotMap` 的调用（保留旧方法给试运行过渡或一并改，见 Task 5）。新增：

```java
/**
 * 按 RefToken 逐个解析目标日期，从宽表取值，以完整 token 为变量名绑定.
 *
 * <p>目标日期 = timePoint.resolve(dataDate)（复用 DateMacroResolver）；缺 slot / 查无值 → ZERO。
 */
private Map<String, Object> loadRefValuesByTokens(String subject,
                                                  List<MetricRefTokenParser.RefToken> refTokens,
                                                  Map<String, Integer> slotMap,
                                                  String baseDim, LocalDate dataDate, String version) {
    if (refTokens == null || refTokens.isEmpty()) {
        return Map.of();
    }
    Map<String, Object> result = new HashMap<>();
    for (MetricRefTokenParser.RefToken rt : refTokens) {
        Integer slot = slotMap.get(rt.baseCode());
        BigDecimal value = null;
        if (slot != null) {
            LocalDate targetDate = rt.timePoint().resolve(dataDate);
            value = switch (baseDim == null ? "" : baseDim.toUpperCase()) {
                case "EMP"  -> empIndexResultMapper.selectValBySlot(subject, slot, targetDate, version);
                case "ORG"  -> orgIndexResultMapper.selectValBySlot(subject, slot, targetDate, version);
                case "CUST" -> custIndexResultMapper.selectValBySlot(subject, slot, targetDate, version);
                default -> throw new PerfException(PerfErrorCode.METRIC_CALC_LOGIC_INVALID,
                        "未知 baseDim=" + baseDim);
            };
        }
        result.put(rt.token(), value != null ? value : BigDecimal.ZERO);
    }
    return result;
}
```

3.4 imports 补 `com.bank.branch.platform.performance.enums.MetricValueTimeEnum` 和 `com.bank.branch.platform.performance.service.engine.MetricRefTokenParser`。保留 `extractMetricCodesFromExpr` / `mergeRefCodes` 供其他调用点，或若无引用则删除（编译报未用可删）。

- [ ] **Step 4: 运行确认新测试通过 + 回归本类**

Run: `mvn -q -pl performance-engine-center test -Dtest=MetricCalcServiceMultiSubjectTest,MetricCalcServiceTest`
Expected: PASS（新 case 绿 + 既有 case 不回归）

- [ ] **Step 5: 提交**

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/MetricCalcService.java \
        performance-engine-center/src/test/java/com/bank/branch/platform/performance/service/MetricCalcServiceMultiSubjectTest.java
git commit -m "feat(perf): 指标计算按 token 取值时间读宽表历史行"
```

---

## Task 4: 除法安全（SafeDivClosure + GroovyExecutorImpl 兜底）

**Files:**
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/engine/SafeDivClosure.java`
- Modify: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/engine/GroovyExecutorImpl.java`
- Test: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/service/engine/GroovyExecutorImplDivSafeTest.java`

- [ ] **Step 1: 写失败测试**

```java
package com.bank.branch.platform.performance.service.engine;

import com.bank.branch.platform.performance.config.PerfEngineProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GroovyExecutorImplDivSafeTest {

    private GroovyExecutorImpl executor;

    @BeforeEach
    void setup() {
        PerfEngineProperties props = new PerfEngineProperties();
        props.setGroovyEnabled(true);
        props.setSqlTimeoutSeconds(30);
        executor = new GroovyExecutorImpl(props);
    }

    @Test
    void div_byZero_returnsZero() {
        BigDecimal r = executor.execute("div(a, b)",
                Map.of("a", new BigDecimal("100"), "b", BigDecimal.ZERO), Duration.ofSeconds(5));
        assertThat(r).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void div_nonTerminating_noException() {
        BigDecimal r = executor.execute("div(a, b)",
                Map.of("a", new BigDecimal("1"), "b", new BigDecimal("3")), Duration.ofSeconds(5));
        assertThat(r).isEqualByComparingTo(new BigDecimal("0.3333333333"));
    }

    @Test
    void rawSlashByZero_caughtAsZero() {
        BigDecimal r = executor.execute("a / b",
                Map.of("a", new BigDecimal("100"), "b", BigDecimal.ZERO), Duration.ofSeconds(5));
        assertThat(r).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
```

> 注：若 `PerfEngineProperties` 的 setter 名与此不符，按实际类字段调整（`isGroovyEnabled()` 已确认存在）。

- [ ] **Step 2: 运行确认失败**

Run: `mvn -q -pl performance-engine-center test -Dtest=GroovyExecutorImplDivSafeTest`
Expected: FAIL —— `div` 未绑定（MissingMethodException）；裸 `/0` 抛 ArithmeticException 被包成 PerfException。

- [ ] **Step 3: 写 SafeDivClosure**

```java
package com.bank.branch.platform.performance.service.engine;

import groovy.lang.Closure;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Groovy 安全除法闭包：{@code div(a, b)}.
 *
 * <p>除数为 0 或 null → 返回 {@link BigDecimal#ZERO}；否则以固定 scale=10、HALF_UP 相除
 * （固定 scale 同时规避无限小数如 1/3 不指定 scale 抛 ArithmeticException）。
 */
public final class SafeDivClosure extends Closure<BigDecimal> {

    /** 内部除法精度. */
    private static final int SCALE = 10;

    public SafeDivClosure(Object owner) {
        super(owner);
    }

    /** div(a, b)：b 为 0/null → 0；否则 a/b。 */
    public BigDecimal doCall(Object a, Object b) {
        BigDecimal divisor = toBigDecimal(b);
        if (divisor == null || divisor.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal dividend = toBigDecimal(a);
        if (dividend == null) {
            return BigDecimal.ZERO;
        }
        return dividend.divide(divisor, SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal toBigDecimal(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof BigDecimal bd) {
            return bd;
        }
        if (v instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        return new BigDecimal(v.toString());
    }
}
```

- [ ] **Step 4: 改 GroovyExecutorImpl —— 注入 div + 捕获 ArithmeticException**

在 `GroovyExecutorImpl.execute` 的 `Callable` 里，构造 binding 时注入 `div`；并在 catch 链新增 ArithmeticException → ZERO。

```java
// 原（约 142-146 行）：
Callable<Object> task = () -> {
    Binding binding = new Binding(vars == null ? Map.of() : vars);
    GroovyShell shell = new GroovyShell(binding, compilerConfig);
    return shell.evaluate(expr);
};
// 改为：
Callable<Object> task = () -> {
    Binding binding = new Binding(vars == null ? new java.util.HashMap<>() : new java.util.HashMap<>(vars));
    // 注入安全除法闭包 div(a,b)：除 0 → 0
    binding.setVariable("div", new SafeDivClosure(this));
    GroovyShell shell = new GroovyShell(binding, compilerConfig);
    return shell.evaluate(expr);
};
```

并在 `future.get` 的 catch 中，`catch (Exception ex)` 分支前增加：

```java
} catch (java.util.concurrent.ExecutionException ee) {
    Throwable cause = ee.getCause();
    if (cause instanceof ArithmeticException) {
        // 除数为 0 / 无限小数：兜底返回 0，不使该主体失败（spec §9）
        log.warn("[GroovyExecutor] 算术异常兜底返回 0: {}", cause.getMessage());
        return BigDecimal.ZERO;
    }
    if (cause instanceof PerfException pe) {
        throw pe;
    }
    log.warn("[GroovyExecutor] 表达式执行失败: {}", cause == null ? ee.getMessage() : cause.getMessage());
    throw new PerfException(PerfErrorCode.METRIC_CALC_LOGIC_INVALID, cause == null ? ee : cause,
            "Groovy 执行失败: " + (cause == null ? ee.getMessage() : cause.getMessage()));
}
```

> 放在现有 `catch (PerfException pe)` 之后、通用 `catch (Exception ex)` 之前。`Future.get` 的业务异常包在 `ExecutionException.getCause()`。保留原通用 catch 兜底其他异常。

- [ ] **Step 5: 运行确认通过 + 回归 Groovy 执行器既有测试**

Run: `mvn -q -pl performance-engine-center test -Dtest=GroovyExecutorImplDivSafeTest,GroovyExecutorImpl*Test`
Expected: PASS（新 3 case 绿 + 既有 GroovyExecutor 测试不回归）

- [ ] **Step 6: 提交**

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/engine/SafeDivClosure.java \
        performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/engine/GroovyExecutorImpl.java \
        performance-engine-center/src/test/java/com/bank/branch/platform/performance/service/engine/GroovyExecutorImplDivSafeTest.java
git commit -m "feat(perf): Groovy 注入 div() 安全除法 + 算术异常兜底返回 0"
```

---

## Task 5: 试运行路径一致（loadGroovyVarsForSubject）

**Files:**
- Modify: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/MetricCalcService.java`（`loadGroovyVarsForSubject:471`）
- Test: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/service/MetricCalcServiceTest.java`

**说明**：试运行取值当前用 `extractMetricCodesFromExpr` + `loadRefValuesBySlotMap`（固定 dataDate、metricCode 变量名），需与 Task 3 真实计算统一到 token 口径。

- [ ] **Step 1: 写失败测试**

```java
@Test
@DisplayName("试运行 loadGroovyVarsForSubject：含取值时间后缀按目标日期取值并以完整 token 绑定")
void loadGroovyVarsForSubject_withSuffix_bindsHistoricalValue() {
    LocalDate dataDate = LocalDate.of(2026, 7, 9);
    when(empIndexResultMapper.selectValSlotsByCodes(anyList())).thenReturn(Map.of("M_A", 3));
    when(empIndexResultMapper.selectValBySlot("E001", 3, dataDate, "V1")).thenReturn(new BigDecimal("100"));
    when(empIndexResultMapper.selectValBySlot("E001", 3, LocalDate.of(2026, 6, 30), "V1"))
            .thenReturn(new BigDecimal("80"));

    Map<String, Object> vars = metricCalcService.loadGroovyVarsForSubject(
            "EMP", "M_A - M_A__PME", dataDate, "E001", "V1");

    assertThat(vars).containsEntry("M_A", new BigDecimal("100"));
    assertThat(vars).containsEntry("M_A__PME", new BigDecimal("80"));
}
```

- [ ] **Step 2: 运行确认失败**

Run: `mvn -q -pl performance-engine-center test -Dtest=MetricCalcServiceTest#loadGroovyVarsForSubject_withSuffix_bindsHistoricalValue`
Expected: FAIL —— 现状把 `M_A__PME` 当 baseCode 查 slot（查无→ZERO），无 `M_A__PME` 昨月末值。

- [ ] **Step 3: 改实现**

```java
public Map<String, Object> loadGroovyVarsForSubject(String baseDim, String exprText,
                                                    LocalDate dataDate, String subjectId, String version) {
    List<MetricRefTokenParser.RefToken> refTokens = MetricRefTokenParser.parse(exprText);
    if (refTokens.isEmpty()) {
        return new HashMap<>();
    }
    String dim = baseDim == null ? "" : baseDim.toUpperCase();
    if (!dim.equals("EMP") && !dim.equals("ORG") && !dim.equals("CUST")) {
        // 维度无关/未知维度：无法定位宽表，引用指标兜底 ZERO
        Map<String, Object> vars = new HashMap<>();
        for (MetricRefTokenParser.RefToken rt : refTokens) {
            vars.put(rt.token(), BigDecimal.ZERO);
        }
        return vars;
    }
    List<String> baseCodes = refTokens.stream()
            .map(MetricRefTokenParser.RefToken::baseCode).distinct().toList();
    Map<String, Integer> slotMap = resolveSlotMap(dim, baseCodes);
    return loadRefValuesByTokens(subjectId, refTokens, slotMap, dim, dataDate, version);
}
```

imports 已在 Task 3 补齐。若 `extractMetricCodesFromExpr` / `loadRefValuesBySlotMap` 此后无任何调用者，删除以免死代码（编译警告为准）。

- [ ] **Step 4: 运行确认通过 + 回归**

Run: `mvn -q -pl performance-engine-center test -Dtest=MetricCalcServiceTest,MetricCalcServiceMultiSubjectTest`
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/MetricCalcService.java \
        performance-engine-center/src/test/java/com/bank/branch/platform/performance/service/MetricCalcServiceTest.java
git commit -m "feat(perf): 试运行取值统一到 token 口径，与真实计算一致"
```

---

## Task 6: 保留后缀校验（MetricDefService + 错误码）

**Files:**
- Modify: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/enums/PerfErrorCode.java`
- Modify: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/MetricDefService.java`
- Test: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/service/MetricDefServiceTest.java`

- [ ] **Step 1: 加错误码（无独立测试，随 Step 3 校验测试一起绿）**

在 `PerfErrorCode.java` 的 `IMPORT_BATCH_ALL_OR_NONE_FAILED("PERF-42211", ...)` 之后加一行：

```java
    /** 指标编码使用了系统保留的取值时间后缀（__D1/__PME/__PQE/__PYE）. */
    METRIC_CODE_RESERVED_SUFFIX("PERF-42212", "指标编码不能以取值时间保留后缀结尾"),
```

- [ ] **Step 2: 写失败测试**

在 `MetricDefServiceTest` 增加（构造 `CreateMetricDefCmd` 的方式参照该测试类既有 create 用例；仅 metricCode 用保留后缀）：

```java
@Test
@DisplayName("create：metricCode 以保留后缀 __PME 结尾 → 抛 METRIC_CODE_RESERVED_SUFFIX")
void create_reservedSuffixCode_throws() {
    CreateMetricDefCmd cmd = buildValidCreateCmd();   // 复用本类既有 helper
    cmd.setMetricCode("M_BALANCE__PME");
    assertThatThrownBy(() -> metricDefService.create(cmd))
            .isInstanceOf(PerfException.class)
            .hasMessageContaining("PERF-42212");
}
```

> 若本类无 `buildValidCreateCmd` helper，则内联构造一个合法 cmd（参照类内其它 create 测试的字段填充），仅覆盖 `metricCode`。断言用错误码字面量 `PERF-42212`（`PerfException` message 含错误码）。

- [ ] **Step 3: 运行确认失败**

Run: `mvn -q -pl performance-engine-center test -Dtest=MetricDefServiceTest#create_reservedSuffixCode_throws`
Expected: FAIL —— 当前无保留后缀校验，create 不抛该错误码。

- [ ] **Step 4: 写最简实现**

在 `MetricDefService` 加私有校验并在 `create`、`update`、导入 upsert 入口调用。先加方法：

```java
/** 系统保留的取值时间后缀（引用 token 用），指标编码禁止以其结尾. */
private static final List<String> RESERVED_SUFFIXES = List.of("__D1", "__PME", "__PQE", "__PYE");

/** 校验 metricCode 未使用保留后缀，否则抛 METRIC_CODE_RESERVED_SUFFIX. */
private void validateMetricCodeSuffix(String metricCode) {
    if (metricCode == null) {
        return;
    }
    for (String suffix : RESERVED_SUFFIXES) {
        if (metricCode.endsWith(suffix)) {
            throw new PerfException(PerfErrorCode.METRIC_CODE_RESERVED_SUFFIX,
                    "指标编码 " + metricCode + " 不能以保留后缀 " + suffix + " 结尾");
        }
    }
}
```

调用点：`create(CreateMetricDefCmd cmd)` 方法体开头（约 277 行，`validateExprIfNeeded` 之前）加：

```java
validateMetricCodeSuffix(cmd.getMetricCode());
```

`update(...)`（约 360 行）里 metricCode 可变更时同样加一行 `validateMetricCodeSuffix(...)`（用变更后的编码；若 update 不允许改 metricCode 则跳过）。导入 `upsertByName` / `batchUpsertByName` 里，指标名生成 metricCode 或使用 Excel 提供的 metricCode 时，同样调用一次（在生成 `M_{indexNo:04d}` 之后、落库之前）。

- [ ] **Step 5: 运行确认通过 + 回归本类**

Run: `mvn -q -pl performance-engine-center test -Dtest=MetricDefServiceTest`
Expected: PASS

- [ ] **Step 6: 提交**

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/enums/PerfErrorCode.java \
        performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/MetricDefService.java \
        performance-engine-center/src/test/java/com/bank/branch/platform/performance/service/MetricDefServiceTest.java
git commit -m "feat(perf): 校验指标编码禁用取值时间保留后缀 (PERF-42212)"
```

---

## Task 7: 全模块回归

- [ ] **Step 1: 跑 performance surefire 全量**

Run: `mvn -q -pl performance-engine-center test`
Expected: 新增 case 全绿；既有 case 无回归（历史已知本地失败项见模块 CLAUDE.md「V1.7 累积测试」，与本改造无关）。

- [ ] **Step 2: 端到端 EXPR 差值验证（可选 IT）**

若造数 IT 基础设施可用，新增 `B = M_A - M_A__D1` 端到端：宽表插两日值 → 触发 `calcMetric` → 断言 B 的 slot 写入正确差额。命名 `*IT.java`（failsafe）。无 IT 基础设施则以 Task 3/5 mock 级覆盖为准，`log()` 记录未做端到端。

- [ ] **Step 3: 提交（若有 IT）**

```bash
git add performance-engine-center/src/test/java/.../MetricRefValueTimeE2EIT.java
git commit -m "test(perf): 取值时间端到端 IT（B = M_A - M_A__D1）"
```

---

## Task 8: 前端表达式构建器（xanzc_frontend）

**Files:**
- Modify: `xanzc_frontend/src/views/perf/Metrics.vue`
- 可能 Modify: `xanzc_frontend/src/api/metrics.js`

**说明**：前端无 TDD 基础设施，按现有 Vue 模式手改 + 人工验证。

- [ ] **Step 1: 取值时间下拉**

在指标公式构建/编辑区，为"选择指标"加并列的"取值时间"下拉，选项固定：
`{label:'今日',value:''} / {label:'昨日',value:'__D1'} / {label:'上月末',value:'__PME'} / {label:'上季末',value:'__PQE'} / {label:'上年末',value:'__PYE'}`，默认今日（空后缀）。插入公式时把 `M_A` 拼成 `M_A${后缀}`。

- [ ] **Step 2: 除法节点生成 `div(x,y)`**

公式构建器的"÷/除"操作生成 `div(左, 右)` 而非 `左 / 右`，与后端安全除法对齐。

- [ ] **Step 3: 公式回显反解析**

展示已存 `exprText` 时，把 `__PME` 等后缀反解析成 `@上月末` 之类中文标注（仅展示，保存仍写规范 token）。

- [ ] **Step 4: 人工验证**

启动前端，配一个 `B = M_A - M_A__D1` 与 `C = div(M_X, M_Y__PME)`，保存后确认 `exprText` 落库为规范 token；触发/试运行确认结果符合预期（含 Y 上月末为 0 时 C=0 不报错）。

---

## Self-Review（作者自查，已执行）

**Spec 覆盖**
- §3 取值时间枚举 → Task 1
- §4 token 语法/解析 → Task 2
- §6 计算引擎取值 → Task 3
- §9 除法安全 → Task 4
- §11 试运行一致 → Task 5
- §10 保留后缀校验 + 错误码 → Task 6
- §5 版本(§7 用今日 version)/§8 缺值 ZERO → Task 3 实现内（`loadRefValuesByTokens` 用同一 `version`、缺值 ZERO）
- §14 前端 → Task 8
- §2 自引用不做/环检测不改 → 无 Task（有意不改 `MetricCycleDetectService`）

**占位符扫描**：无 TBD；错误码编号 `PERF-42212` 已定（现有最大 42211）。

**类型/命名一致**：`MetricRefTokenParser.RefToken(token, baseCode, timePoint)`、`MetricValueTimeEnum.{TODAY,D1,PME,PQE,PYE}`、`loadRefValuesByTokens(...)`、`SafeDivClosure`、`METRIC_CODE_RESERVED_SUFFIX` 全计划一致。

**风险**：Groovy 绑定 Closure 变量名 `div` 与 SecureAST 兼容性 —— Task 4 测试直接验证 `div(a,b)` 可调用；若 SecureAST 拦截闭包调用，退路为改注入一个持 `div` 方法的绑定对象并以 `H.div(a,b)` 调用（Task 4 失败时切换）。
