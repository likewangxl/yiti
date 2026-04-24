# Performance-Engine-Center V1.5 Iteration Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 清理 V1.4 交付后登记的 6 项遗留清单，performance-engine-center 技术债清零。

**Architecture:** 纯 performance 模块内改动（除 P4 batch 方法涉及 Mapper 层 / XML 新增）。6 项改动相互独立（P3 与 P4/P5 对同一方法有耦合，但互不破坏），可分 Phase 推进。保留 5 字段 ScopeColumns、现有 MetricApi 单参签名（`getUserMetricCards(empId)`）不变——不做 V1.4 reviewer 设想中的"向 Controller 传 cycleType"式契约扩容。

**Tech Stack:** Spring Boot 3.2.3 + MyBatis 3.0.3 + JDK 17 + Flyway + TDD Red-Green-Refactor

**依赖文档（权威需求来源）:**
- `performance-engine-center/CLAUDE.md` §V1.5+ 遗留项（6 项清单）
- V1.4 S4.2 / S3 code-reviewer 观察（"非阻塞观察"条目落地）
- V1.3 R3.2 @Deprecated 兼容承诺（"1 版本后删除"）

**前置条件（必须已满足）:**
- ✅ V1.0-V1.4 全部交付推送（主分支 master）
- ✅ Facade UOE 清零 + 6 架构守护绿
- ✅ V1.4 交付后全量测试全绿（surefire + failsafe 双绿）
- ✅ V1_4_0 DDL 已落地（owner_emp_id / owner_org_code 列存在）
- ✅ MetricCardDTO 已含 previousValue / mom / yoy 字段

---

## 阶段概览

| 阶段 | 任务数 | 目标 | 预估 Commit 数（Red+Green 对） |
|---|---|---|---|
| P1 | 1 | 删除 `@Deprecated getSamples()` 兼容方法 | 2（Red + Refactor） |
| P2 | 1 | `cycleType=""` 空串 behavior 测试补齐 | 1（Red+Green 合并：纯测试补强） |
| P3 | 1 | `codeToCycleType` 多 scheme 首命中歧义修复 | 2（Red + Green） |
| P4 | 1 | `mom/yoy` 每 metric 3 次宽表查询 batch 优化 | 3（Red Mapper + Green Mapper + Refactor Facade） |
| P5 | 1 | `yoy` 按 cycleType 分支（WEEKLY 走 -52 周） | 2（Red + Green） |
| P6 | 1 | `PerfTargetPlanMapper.xml updateByIdSelective` owner `<if>` 分支补齐 | 2（Red + Green） |
| P7 | 3 | 收尾（全量回归 + 文档同步 + 技术债清算） | 3（单 commit each） |
| 合计 | 9 | | **~15 commit** |

---

## 阶段 P1：删除 @Deprecated getSamples()

### Task P1.1：彻底删除 `MetricTrialRespDTO.getSamples()` 及 @JsonAlias

**背景（前置调研发现）:**
- `MetricTrialRespDTO.getSamples()` 在 V1.3 R3.2 标 `@Deprecated + @JsonIgnore`，保留 1 版本兼容过渡期。
- **生产代码 zero consumer**：`Grep pattern="getSamples\(\)"` 仅命中测试文件（`MetricTrialRespDTOTest` 1 处 + `MetricTrialServiceTest` 4 处），生产代码无任何调用方；`setSamples(` 仅 `MetricTrialService` 内部 2 处（此处是 `MetricTrialResult.setSamples()` 而非 DTO），DTO 本身无 `samples` 字段（V1.3 改名为 `sampleRows`），无需删字段。
- 可安全删除 `getSamples()` 方法 + `@JsonAlias({"samples"})` 反序列化别名。
- `MetricTrialService.setSamples(...)` 调用的是 `MetricTrialResult` 的 setter，**不**受影响（service 层 DTO 与 Controller 层 DTO 是两个类，service DTO 仍叫 `samples` 不改名）。

**Files:**
- Modify: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/controller/dto/MetricTrialRespDTO.java`
- Modify: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/controller/MetricTrialRespDTOTest.java`（删除 2 个过时断言）

- [ ] **Step 1：写失败测试（Red）——断言方法和 alias 已被移除**

将 `MetricTrialRespDTOTest.java` 中关于 `getSamples()` 兼容行为的测试改写为"守护删除后状态"的断言：

```java
// MetricTrialRespDTOTest.java（删 2 个 case + 新增 1 个反向守护 case）

@Test
@DisplayName("V1.5 P1.1：MetricTrialRespDTO 不再暴露 getSamples() 方法")
void respDTO_legacyGetSamplesMethod_hasBeenRemoved() throws Exception {
    // 反射守护：getSamples() 不应再存在（V1.3→V1.4→V1.5 节奏兑现的兼容期结束承诺）
    boolean hasLegacyGetter = java.util.Arrays.stream(MetricTrialRespDTO.class.getMethods())
            .anyMatch(m -> m.getName().equals("getSamples"));
    org.assertj.core.api.Assertions.assertThat(hasLegacyGetter)
            .as("V1.5 删除 @Deprecated getSamples() 兼容方法")
            .isFalse();
}

@Test
@DisplayName("V1.5 P1.1：legacy samples JSON 字段不再反序列化（@JsonAlias 已移除）")
void respDTO_legacySamplesAlias_notDeserializedAnymore() throws Exception {
    // V1.3 R3.2 @JsonAlias({"samples"}) 已在 V1.5 一并删除；用 fail-on-unknown 模式守护
    ObjectMapper strict = new ObjectMapper()
            .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
    String legacyJson = "{\"taskId\":\"T\",\"samples\":[{\"base_key\":\"E1\"}]}";
    org.assertj.core.api.Assertions
            .assertThatThrownBy(() -> strict.readValue(legacyJson, MetricTrialRespDTO.class))
            .isInstanceOf(com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException.class);
}
```

同时删除原本 2 个兼容测试：
- `respDTO_deserializeLegacySamples_viaJsonAlias`（依赖 @JsonAlias）
- `respDTO_deprecatedGetSamples_stillReturnsSampleRows`（依赖 @Deprecated 方法）

- [ ] **Step 2：运行测试确认失败（Red 成立）**

```bash
cd performance-engine-center
mvn -Dtest=MetricTrialRespDTOTest test
```

预期：新增 2 case 失败（getSamples 仍存在 / @JsonAlias 仍在别名反序列化）。

- [ ] **Step 3：移除 `@Deprecated getSamples()` + `@JsonAlias({"samples"})`（Green/Refactor）**

```java
// MetricTrialRespDTO.java（修改后）
package com.bank.branch.platform.performance.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 指标试运行响应 DTO（03 §A.5）.
 *
 * <p>V1.5 P1.1：兼容过渡期结束，删除 {@code @Deprecated getSamples()} 与
 * {@code @JsonAlias({"samples"})}。客户端必须使用 {@code sampleRows} 字段。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricTrialRespDTO {

    /** 本次试运行任务 ID. */
    private String taskId;

    /** 回显的指标编码. */
    private String metricCode;

    /** 实际返回样本条数. */
    private Integer sampleSize;

    /** 总行数（EXPR 为 1）. */
    private Integer totalRows;

    /** 执行状态：RUNNING/SUCCESS/FAILED（Service 同步执行固定 SUCCESS）. */
    private String status;

    /** 执行开始时间. */
    private LocalDateTime startedAt;

    /** 执行结束时间. */
    private LocalDateTime endedAt;

    /** 错误信息（失败时填入）. */
    private String errorMsg;

    /** EXPR 单值结果（SQL 场景为 null）. */
    private BigDecimal exprResult;

    /** 执行耗时（毫秒）. */
    private Long executionMillis;

    /** 样本行（SQL 场景填充，EXPR 为空列表）. */
    private List<Map<String, Object>> sampleRows;
}
```

（删除 `@JsonIgnore`、`@JsonAlias`、`@Deprecated getSamples()` 方法及其 import）

- [ ] **Step 4：运行测试确认通过（Green）**

```bash
mvn -Dtest=MetricTrialRespDTOTest test
```

预期：新增 2 case 通过。全量 surefire 也要通过（其他受影响测试已在 Step 1 删除）。

- [ ] **Step 5：Commit（Red + Refactor 两 commit）**

Red commit：

```bash
git add performance-engine-center/src/test/java/com/bank/branch/platform/performance/controller/MetricTrialRespDTOTest.java
git commit -m "$(cat <<'EOF'
test(perf-v1.5): MetricTrialRespDTO getSamples/JsonAlias 移除守护测试（Red，Task P1.1）

V1.3 R3.2 @Deprecated 承诺"1 版本后删除"。V1.5 P1.1 兑现：
- 反射守护 getSamples() 方法不再存在
- FAIL_ON_UNKNOWN_PROPERTIES 守护 @JsonAlias({"samples"}) 已移除
- 删除 2 个依赖兼容行为的过时 case（respDTO_deserializeLegacySamples_viaJsonAlias /
  respDTO_deprecatedGetSamples_stillReturnsSampleRows）

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

Refactor commit：

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/controller/dto/MetricTrialRespDTO.java
git commit -m "$(cat <<'EOF'
refactor(perf-v1.5): 删除 MetricTrialRespDTO.getSamples() 与 @JsonAlias 兼容别名（Green，Task P1.1）

V1.3 R3.2 → V1.5 兼容承诺期满，彻底清理：
- 移除 @Deprecated @JsonIgnore getSamples() 方法
- 移除 sampleRows 字段上的 @JsonAlias({"samples"})
- 同步清理 JsonAlias/JsonIgnore import
生产代码 zero consumer（Grep 确认：仅 3 处测试引用，Red 阶段已删除/改写）。

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## 阶段 P2：cycleType="" 空串 behavior 测试补齐

### Task P2.1：`HistoryRecalcService` 空串 cycleType 跳过字段的测试

> **TDD 合规监督**：P2.1 虽合并 Red+Green 为单 commit（因 V1.4 S4.2 实现已支持空串，仅补守护性测试），提交前**必须**本地跑 `mvn -pl performance-engine-center test -Dtest=HistoryRecalcServiceTest`，确认 2 个新 case 直接绿 且 回归测试不受影响。commit message 明确声明"守护性测试补齐"。

**背景（V1.4 S4.2 非阻塞观察）:**
- V1.4 S4.2 已把 `cycleType=null` 场景修为"跳过字段"，实现上用 `if (cycleType != null && !cycleType.isBlank())` 同时覆盖 null 与 blank（包括 `""` 和 `"   "`）。
- 但 `HistoryRecalcServiceTest` 仅显式断言了 null case + non-null case 两条路径，**未显式覆盖空串与纯空格**——V1.4 reviewer 将此留为观察项。
- V1.5 P2.1：补 2 case 守护行为，避免未来误把 `isBlank()` 改回 `== null` 时回归。

**Files:**
- Modify: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/service/HistoryRecalcServiceTest.java`（新增 2 个 case，实现代码无改动）

- [ ] **Step 1：写失败测试（Red）——其实当前实现已支持，但断言缺失**

在 `HistoryRecalcServiceTest.java` 的 S4.2 测试区块（`recalc_cycleTypeNull_skipsFieldInParamsJson` 之后）追加：

```java
/**
 * V1.5 P2.1 Red 守护：cycleType="" 空串时 params_json 跳过该字段.
 *
 * <p>V1.4 S4.2 的 Green 实现用 StringUtils.isNotBlank (isBlank 同义逆) 同时
 * 覆盖 null / "" / "   "；V1.4 reviewer 指出测试仅显式覆盖 null，留观察项。
 * V1.5 P2.1 补齐：Facade 层可能透传空串 → 期望与 null 行为一致（跳过字段）。
 */
@Test
@DisplayName("V1.5 P2.1：cycleType=\"\" 空串时 params_json 跳过 cycleType 字段")
void recalc_cycleTypeEmptyString_skipsFieldInParamsJson() {
    LocalDate date = LocalDate.of(2026, 3, 1);
    List<String> metricCodes = List.of("M_A");
    when(metricCalcService.calcMetric(eq("M_A"), eq(date), anyString()))
            .thenReturn("CHILD_EMPTY");

    // 显式传 ""，避开 6 参兜底到 null 的路径
    historyRecalcService.recalc(date, date, metricCodes, "v1", "原因 P2 empty", "op", "");

    ArgumentCaptor<PerfRunTask> cap = ArgumentCaptor.forClass(PerfRunTask.class);
    verify(perfRunTaskMapper).insert(cap.capture());
    String paramsJson = cap.getValue().getParamsJson();
    assertThat(paramsJson)
            .as("空串 cycleType 应与 null 行为一致，不出现在 JSON 中")
            .doesNotContain("cycleType");
    assertThat(paramsJson)
            .contains("\"startDate\"")
            .contains("\"endDate\"")
            .contains("\"reason\"");
}

/**
 * V1.5 P2.1 Red 守护：cycleType="   " 纯空格时也跳过字段.
 *
 * <p>blank 语义的边界守护——防 UI/Facade 层透传未 trim 的空白字符串。
 */
@Test
@DisplayName("V1.5 P2.1：cycleType=\"   \" 纯空格时 params_json 跳过 cycleType 字段")
void recalc_cycleTypeWhitespace_skipsFieldInParamsJson() {
    LocalDate date = LocalDate.of(2026, 3, 1);
    List<String> metricCodes = List.of("M_A");
    when(metricCalcService.calcMetric(eq("M_A"), eq(date), anyString()))
            .thenReturn("CHILD_WS");

    historyRecalcService.recalc(date, date, metricCodes, "v1", "原因 P2 whitespace", "op", "   ");

    ArgumentCaptor<PerfRunTask> cap = ArgumentCaptor.forClass(PerfRunTask.class);
    verify(perfRunTaskMapper).insert(cap.capture());
    String paramsJson = cap.getValue().getParamsJson();
    assertThat(paramsJson)
            .as("纯空格 cycleType 应按 blank 处理跳过字段")
            .doesNotContain("cycleType");
}
```

- [ ] **Step 2：运行测试确认已通过（Red 不必然失败——实现已支持）**

```bash
mvn -Dtest=HistoryRecalcServiceTest test
```

预期：2 新 case 直接通过（V1.4 Green 实现 `cycleType.isBlank()` 已覆盖）。

**说明**：本 Task 是"守护测试补齐"而非"新功能"，TDD 红线允许当 Red 阶段即 Green 时合并为单 commit，但需在 commit message 注明"补充 behavior 守护测试"而非 feat。

- [ ] **Step 3：Commit**

```bash
git add performance-engine-center/src/test/java/com/bank/branch/platform/performance/service/HistoryRecalcServiceTest.java
git commit -m "$(cat <<'EOF'
test(perf-v1.5): HistoryRecalcService cycleType 空串/纯空格 behavior 守护（Task P2.1）

V1.4 S4.2 实现用 isBlank() 统一处理 null / "" / "   "，但测试仅显式覆盖
null。V1.5 P2.1 补 2 case 守护：
- cycleType="" → params_json 跳过 cycleType 字段
- cycleType="   " → 同上（纯空格走 blank 语义）

V1.4 Green 实现已支持此行为，测试加上后即通过；本 commit 属于"行为守护测试补齐"
而非新功能，不单独拆 Red/Green 两 commit。

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## 阶段 P3：codeToCycleType 多 scheme 首命中歧义修复

### Task P3.1：按 (metricCode, cycleType) 分组组装卡片，取代首命中策略

**背景（V1.4 S3 reviewer 观察 M01）:**
- 当前 `MetricApiImpl.getUserMetricCards` 用 `codeToCycleType = new LinkedHashMap<>()` 记录 metricCode → scheme.cycleType，遍历方案时 `if (!orderedCodes.contains(code))` 保证每个 metricCode 只登记一次（取首命中方案的 cycleType）。
- **歧义场景**：方案 A（QUARTERLY）+ 方案 B（YEARLY）都引用 metricCode `M_X`。当前逻辑按 A 首命中，`buildCycleKey` 走 QUARTERLY → `2026Q2`，但 B 的 target 实际写入的是 `2026` → YEARLY scheme 的目标永远命中不到。
- **V1.5 P3.1 策略**：**按 (metricCode, cycleType) 组合生成卡片**。同一 metricCode 在多方案 cycleType 不同时，生成多张卡片（每个 cycleType 一张），前端按 scheme 分组展示。
- **影响**：
  - 返回的 `List<MetricCardDTO>` 可能同一 metricCode 出现多次（每 cycleType 一条），前端应按 `metricCode + cycleType` 作为唯一键。
  - `MetricCardDTO` 暂不新增 `cycleType` 字段（保持既有契约），通过卡片数量变化让前端感知；V1.5+ 可评估是否增字段。
  - 不改 `MetricApi.getUserMetricCards` 签名，仍是 `(empId) → List<MetricCardDTO>`。

**Files:**
- Modify: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/facade/MetricApiImpl.java`
- Modify: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/facade/MetricApiImplCardsTest.java`

- [ ] **Step 1：写失败测试（Red）**

在 `MetricApiImplCardsTest.java` 新增 case：

```java
/**
 * V1.5 P3.1 Red：同一 metricCode 被多个 scheme 引用且 cycleType 不同时，
 * 生成按 (metricCode, cycleType) 分组的多张卡片.
 *
 * <p>V1.4 reviewer M01 观察项：当前首命中策略会让 YEARLY scheme 的目标值永远查不到.
 * V1.5 修复：按 cycleType 分组 × metricCode，每个组合一张卡片.
 */
@Test
@DisplayName("[V1.5 P3.1] 多 scheme 共享 metric 不同 cycleType：按 (metric, cycleType) 分组生成多卡片")
void getUserMetricCards_multiSchemesDifferentCycleType_generatesCardPerCombo() {
    String empId = "E001";
    LocalDate latest = LocalDate.of(2026, 7, 15);
    SysControl sc = new SysControl();
    sc.setCurrentVersion("v1");
    sc.setLatestDataDate(latest);
    when(sysControlService.getCurrentVersion("EMP")).thenReturn(sc);

    // scheme A (QUARTERLY) + scheme B (YEARLY) 都引用 M_X
    PerfKpiScheme sA = new PerfKpiScheme();
    sA.setId("SA");
    sA.setCycleType("QUARTERLY");
    sA.setStatus("ACTIVE");
    PerfKpiScheme sB = new PerfKpiScheme();
    sB.setId("SB");
    sB.setCycleType("YEARLY");
    sB.setStatus("ACTIVE");
    when(kpiSchemeService.listActiveSchemes()).thenReturn(List.of(sA, sB));
    when(kpiItemService.listBySchemeId("SA")).thenReturn(List.of(kpiItem("SA", "M_X")));
    when(kpiItemService.listBySchemeId("SB")).thenReturn(List.of(kpiItem("SB", "M_X")));
    when(metricDefService.getByCodes(List.of("M_X")))
            .thenReturn(List.of(def("M_X", "EMP", 5, "X", "万元")));

    lenient().when(empIndexResultMapper.selectSlotValue(eq(empId), any(), eq("v1"), eq(5)))
            .thenReturn(new BigDecimal("80"));

    // QUARTERLY 方向下 cycleKey=2026Q3；YEARLY 方向下 cycleKey=2026
    when(perfTargetValueMapper.selectByUniqueKey(any(), eq("EMP"), eq(empId), eq("2026Q3"), eq("M_X")))
            .thenReturn(targetValue(new BigDecimal("100")));
    when(perfTargetValueMapper.selectByUniqueKey(any(), eq("EMP"), eq(empId), eq("2026"), eq("M_X")))
            .thenReturn(targetValue(new BigDecimal("400")));

    List<MetricCardDTO> cards = api.getUserMetricCards(empId);

    // V1.5：同一 metricCode 分 2 张卡片，分别命中不同 cycleType 的 target
    assertThat(cards).hasSize(2);
    assertThat(cards).extracting(MetricCardDTO::getTargetValue)
            .extracting(bd -> bd == null ? null : bd.stripTrailingZeros().toPlainString())
            .containsExactlyInAnyOrder("1E+2", "4E+2"); // 100 和 400

    // 验证两张卡片分别对两个 cycleType 的 target 做了精确查询
    verify(perfTargetValueMapper).selectByUniqueKey(any(), eq("EMP"), eq(empId), eq("2026Q3"), eq("M_X"));
    verify(perfTargetValueMapper).selectByUniqueKey(any(), eq("EMP"), eq(empId), eq("2026"), eq("M_X"));
}

/**
 * V1.5 P3.1 Red：同一 metricCode 多个 scheme cycleType 相同时，仍只出一张卡片（去重）.
 *
 * <p>防止 P3 过度修复变成"每 scheme 一卡"，保持"按 cycleType 去重"语义.
 */
@Test
@DisplayName("[V1.5 P3.1] 多 scheme 同 cycleType 引用同 metric：去重后仅一张卡片")
void getUserMetricCards_multiSchemesSameCycleType_generatesSingleCard() {
    String empId = "E001";
    LocalDate latest = LocalDate.of(2026, 7, 15);
    SysControl sc = new SysControl();
    sc.setCurrentVersion("v1");
    sc.setLatestDataDate(latest);
    when(sysControlService.getCurrentVersion("EMP")).thenReturn(sc);

    PerfKpiScheme sA = new PerfKpiScheme();
    sA.setId("SA");
    sA.setCycleType("MONTHLY");
    sA.setStatus("ACTIVE");
    PerfKpiScheme sB = new PerfKpiScheme();
    sB.setId("SB");
    sB.setCycleType("MONTHLY");
    sB.setStatus("ACTIVE");
    when(kpiSchemeService.listActiveSchemes()).thenReturn(List.of(sA, sB));
    when(kpiItemService.listBySchemeId("SA")).thenReturn(List.of(kpiItem("SA", "M_Y")));
    when(kpiItemService.listBySchemeId("SB")).thenReturn(List.of(kpiItem("SB", "M_Y")));
    when(metricDefService.getByCodes(List.of("M_Y")))
            .thenReturn(List.of(def("M_Y", "EMP", 6, "Y", "户")));
    lenient().when(empIndexResultMapper.selectSlotValue(eq(empId), any(), eq("v1"), eq(6)))
            .thenReturn(new BigDecimal("5"));
    when(perfTargetValueMapper.selectByUniqueKey(any(), eq("EMP"), eq(empId), eq("202607"), eq("M_Y")))
            .thenReturn(targetValue(new BigDecimal("10")));

    List<MetricCardDTO> cards = api.getUserMetricCards(empId);

    assertThat(cards).hasSize(1);
    assertThat(cards.get(0).getMetricCode()).isEqualTo("M_Y");
    assertThat(cards.get(0).getTargetValue()).isEqualByComparingTo("10");
}
```

- [ ] **Step 2：运行测试确认失败（Red 成立）**

```bash
mvn -Dtest=MetricApiImplCardsTest test
```

预期：新 2 case 失败——当前首命中实现只会生成 1 张卡片（以首命中 scheme 的 cycleType）。

- [ ] **Step 3：修改实现（Green）——按 (metricCode, cycleType) 去重**

修改 `MetricApiImpl.getUserMetricCards`：

```java
@Override
public List<MetricCardDTO> getUserMetricCards(String empId) {
    List<PerfKpiScheme> schemes = kpiSchemeService.listActiveSchemes();
    if (schemes == null || schemes.isEmpty()) {
        return List.of();
    }

    // V1.5 P3.1: 按 (metricCode, cycleType) 作为组合键去重，避免 V1.4 首命中歧义.
    // LinkedHashSet 保持首次出现顺序，前端展示时顺序稳定.
    LinkedHashSet<MetricKey> metricKeys = new LinkedHashSet<>();
    for (PerfKpiScheme scheme : schemes) {
        List<PerfKpiItem> items = kpiItemService.listBySchemeId(scheme.getId());
        if (items == null) {
            continue;
        }
        for (PerfKpiItem item : items) {
            String code = item.getMetricCode();
            if (code == null) {
                continue;
            }
            metricKeys.add(new MetricKey(code, scheme.getCycleType()));
        }
    }
    if (metricKeys.isEmpty()) {
        return List.of();
    }

    // 批量读取指标定义 (distinct metricCode 避免重复查询)
    List<String> distinctCodes = metricKeys.stream()
            .map(MetricKey::metricCode)
            .distinct()
            .toList();
    List<PerfMetricDef> defs = metricDefService.getByCodes(distinctCodes);
    Map<String, PerfMetricDef> codeToDef = new LinkedHashMap<>();
    for (PerfMetricDef def : defs) {
        if (!"EMP".equalsIgnoreCase(def.getBaseDim())) {
            log.debug("[getUserMetricCards] 指标 {} baseDim={} 非 EMP, 跳过",
                    def.getMetricCode(), def.getBaseDim());
            continue;
        }
        if (def.getValSlot() == null) {
            log.debug("[getUserMetricCards] 指标 {} 未分配 slot, 跳过", def.getMetricCode());
            continue;
        }
        codeToDef.put(def.getMetricCode(), def);
    }
    if (codeToDef.isEmpty()) {
        return List.of();
    }

    SysControl sc = sysControlService.getCurrentVersion("EMP");
    if (sc == null) {
        log.warn("[getUserMetricCards] empId={} 无 sys_control(EMP) 基线版本, 返回空卡片", empId);
        return List.of();
    }
    String version = sc.getCurrentVersion();
    LocalDate latestDate = sc.getLatestDataDate();

    List<MetricCardDTO> cards = new ArrayList<>(metricKeys.size());
    for (MetricKey key : metricKeys) {
        PerfMetricDef def = codeToDef.get(key.metricCode());
        if (def == null) {
            continue; // 已在 codeToDef 过滤掉（非 EMP / 无 slot）
        }
        cards.add(buildCard(empId, def, key.cycleType(), version, latestDate));
    }
    return cards;
}

/** V1.5 P3.1: 单卡组装抽取. */
private MetricCardDTO buildCard(String empId, PerfMetricDef def, String cycleType,
                                String version, LocalDate latestDate) {
    BigDecimal actual = empIndexResultMapper.selectSlotValue(
            empId, latestDate, version, def.getValSlot());
    String cycleKey = buildCycleKey(cycleType, latestDate);
    PerfTargetValue tv = perfTargetValueMapper.selectByUniqueKey(
            null, "EMP", empId, cycleKey, def.getMetricCode());
    BigDecimal target = tv == null ? null : tv.getTargetValue();
    BigDecimal rate = null;
    if (target != null && target.compareTo(BigDecimal.ZERO) != 0 && actual != null) {
        rate = actual.multiply(new BigDecimal("100"))
                .divide(target, 4, RoundingMode.HALF_UP);
    }
    LocalDate previousDate = calculatePreviousDate(cycleType, latestDate);
    BigDecimal previousValue = previousDate == null ? null
            : empIndexResultMapper.selectSlotValue(empId, previousDate, version, def.getValSlot());
    BigDecimal mom = calculateMom(actual, previousValue);
    LocalDate yearAgoDate = latestDate == null ? null : latestDate.minusYears(1);
    BigDecimal yearAgoValue = yearAgoDate == null ? null
            : empIndexResultMapper.selectSlotValue(empId, yearAgoDate, version, def.getValSlot());
    BigDecimal yoy = calculateYoy(actual, yearAgoValue);
    return MetricCardDTO.builder()
            .metricCode(def.getMetricCode())
            .metricName(def.getMetricName())
            .currentValue(actual)
            .previousValue(previousValue)
            .targetValue(target)
            .achievementRate(rate)
            .mom(mom)
            .yoy(yoy)
            .unit(def.getUnit())
            .dataDate(latestDate)
            .build();
}

/** V1.5 P3.1: (metricCode, cycleType) 组合键（cycleType 可 null → 兜底 YEARLY 语义）. */
private record MetricKey(String metricCode, String cycleType) {
    public MetricKey {
        // null cycleType 归一为 "" 以保证 equals/hashCode 稳定；buildCycleKey 内部仍按 null 走兜底
    }
}
```

**注意**：`MetricKey` record 需正确处理 null cycleType。Java record 的 canonical constructor 允许 null 字段；`LinkedHashSet` 基于 equals/hashCode 去重，record 默认生成两者，null 字段组合键 `(code, null)` 与 `(code, "")` 不同——保持 null 原值，`buildCycleKey(null, date)` 内部已按年兜底。

- [ ] **Step 4：运行测试确认通过（Green）**

```bash
mvn -Dtest=MetricApiImplCardsTest test
```

预期：新 2 case 通过，既有 S3 case 全绿（单 scheme 场景的卡片数量不变）。

- [ ] **Step 5：Commit（Red + Green 两 commit）**

Red commit：

```bash
git add performance-engine-center/src/test/java/com/bank/branch/platform/performance/facade/MetricApiImplCardsTest.java
git commit -m "$(cat <<'EOF'
test(perf-v1.5): 多 scheme 共享 metric 不同 cycleType 分组失败测试（Red，Task P3.1）

V1.4 reviewer M01 观察项：codeToCycleType 首命中策略在方案 A(QUARTERLY) +
方案 B(YEARLY) 都引用 M_X 时，YEARLY 目标永远查不到。V1.5 P3.1 改按
(metricCode, cycleType) 组合分组：
- 新 case 1：多 scheme 不同 cycleType → 生成 2 张卡片，分别查到各自 target
- 新 case 2：多 scheme 同 cycleType → 去重仅生成 1 张卡片

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

Green commit：

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/facade/MetricApiImpl.java
git commit -m "$(cat <<'EOF'
feat(perf-v1.5): MetricApi.getUserMetricCards 按 (metric, cycleType) 分组修复首命中歧义（Green，Task P3.1）

V1.4 reviewer M01：替换 codeToCycleType LinkedHashMap 为 LinkedHashSet<MetricKey>
组合键去重。同一 metricCode 被多 scheme 不同 cycleType 引用时生成多张卡片；
同 cycleType 时仍去重。MetricCardDTO 签名不变（未加 cycleType 字段，保持既有
前端契约），前端按 metricCode+cycleType 作为唯一键展示。
顺带把单卡组装抽到 buildCard(...) 提高可读性。

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## 阶段 P4：mom/yoy 3 次宽表查询 batch 优化

### Task P4.1：新增 `selectSlotValuesByDates` 批量方法并重构 buildCard

**背景（V1.4 S3 reviewer 观察 M02）:**
- 当前 `buildCard` 对每个 metric 串行发起 3 次 `empIndexResultMapper.selectSlotValue(empId, date, version, slot)`（current / previous / yearAgo）。
- 20 metric × 3 日期 = **60 次**宽表点查，串行总延迟 ~400-600ms（p95 下限）。
- V1.5 P4.1：新增 batch 方法 `selectSlotValuesByDates(empId, dates, version, slot) → Map<LocalDate, BigDecimal>`，一次 IN 查询拿回 3 个日期的值。
- 优化后：20 metric × 1 次 IN (3 dates) = **20 次**，下降 66%。

**Files:**
- Modify: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/mapper/EmpIndexResultMapper.java`（新增方法）
- Modify: `performance-engine-center/src/main/resources/mapper/performance/EmpIndexResultMapper.xml`（新增 select）
- Modify: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/facade/MetricApiImpl.java`（buildCard 走 batch）
- Create: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/mapper/EmpIndexResultMapperBatchDatesIT.java`（Mapper IT）
- Modify: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/facade/MetricApiImplCardsTest.java`（改 stub 为新 batch 方法 + 新增调用次数断言）

- [ ] **Step 1：写失败测试（Red Mapper + Red Facade）**

**1a. Mapper IT（Testcontainers-MySQL 风格，见 V1.1 P1 同款 Base）**：

```java
// EmpIndexResultMapperBatchDatesIT.java
package com.bank.branch.platform.performance.mapper;

import com.bank.branch.platform.performance.support.PerformanceMapperTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V1.5 P4.1：EmpIndexResultMapper.selectSlotValuesByDates batch 查询 IT.
 */
class EmpIndexResultMapperBatchDatesIT extends PerformanceMapperTestBase {

    @Autowired
    private EmpIndexResultMapper mapper;

    @Test
    @DisplayName("selectSlotValuesByDates：单次 IN 查询返回多日期 slot 值")
    void selectSlotValuesByDates_returnsPerDateMap() {
        String emp = "TEST_P4_E001";
        String version = "TEST_P4_v1";
        LocalDate d1 = LocalDate.of(2026, 4, 1);
        LocalDate d2 = LocalDate.of(2026, 1, 1);
        LocalDate d3 = LocalDate.of(2025, 4, 1);
        mapper.insertSlotValue(emp, d1, version, 5, new BigDecimal("120"));
        mapper.insertSlotValue(emp, d2, version, 5, new BigDecimal("100"));
        mapper.insertSlotValue(emp, d3, version, 5, new BigDecimal("80"));
        // d4 故意不插入，验证返回 Map 不含缺失日期
        LocalDate d4 = LocalDate.of(2023, 1, 1);

        Map<LocalDate, BigDecimal> values = mapper.selectSlotValuesByDates(
                emp, List.of(d1, d2, d3, d4), version, 5);

        assertThat(values).hasSize(3);
        assertThat(values.get(d1)).isEqualByComparingTo("120");
        assertThat(values.get(d2)).isEqualByComparingTo("100");
        assertThat(values.get(d3)).isEqualByComparingTo("80");
        assertThat(values).doesNotContainKey(d4);
    }

    @Test
    @DisplayName("selectSlotValuesByDates：dates 为空或 null 时返回空 Map（不下发 SQL）")
    void selectSlotValuesByDates_emptyDates_returnsEmpty() {
        Map<LocalDate, BigDecimal> values = mapper.selectSlotValuesByDates(
                "TEST_P4_E001", List.of(), "v1", 5);
        assertThat(values).isEmpty();
    }
}
```

**1b. Facade 测试补调用次数断言**：

在 `MetricApiImplCardsTest.java` 新增：

```java
/**
 * V1.5 P4.1 Red：buildCard 必须改为调用 selectSlotValuesByDates batch API，
 * 而不是 3 次单点 selectSlotValue.
 */
@Test
@DisplayName("[V1.5 P4.1] buildCard 改用 batch 查询：每卡片只调一次 selectSlotValuesByDates")
void getUserMetricCards_batchesWideTableQueries() {
    String empId = "E001";
    LocalDate latest = LocalDate.of(2026, 4, 1);
    LocalDate previous = LocalDate.of(2026, 1, 1);
    LocalDate yearAgo = LocalDate.of(2025, 4, 1);
    SysControl sc = new SysControl();
    sc.setCurrentVersion("v1");
    sc.setLatestDataDate(latest);
    when(sysControlService.getCurrentVersion("EMP")).thenReturn(sc);

    PerfKpiScheme s1 = new PerfKpiScheme();
    s1.setId("S1");
    s1.setCycleType("QUARTERLY");
    s1.setStatus("ACTIVE");
    when(kpiSchemeService.listActiveSchemes()).thenReturn(List.of(s1));
    when(kpiItemService.listBySchemeId("S1")).thenReturn(List.of(kpiItem("S1", "M_X")));
    when(metricDefService.getByCodes(List.of("M_X")))
            .thenReturn(List.of(def("M_X", "EMP", 5, "X", "万元")));

    // 期望 Facade 调 batch API 一次拿 3 个日期
    Map<LocalDate, BigDecimal> batchMap = new HashMap<>();
    batchMap.put(latest, new BigDecimal("120"));
    batchMap.put(previous, new BigDecimal("100"));
    batchMap.put(yearAgo, new BigDecimal("80"));
    when(empIndexResultMapper.selectSlotValuesByDates(
            eq(empId), argThat(list -> list != null && list.containsAll(List.of(latest, previous, yearAgo))),
            eq("v1"), eq(5)))
            .thenReturn(batchMap);

    List<MetricCardDTO> cards = api.getUserMetricCards(empId);

    assertThat(cards).hasSize(1);
    assertThat(cards.get(0).getCurrentValue()).isEqualByComparingTo("120");
    assertThat(cards.get(0).getPreviousValue()).isEqualByComparingTo("100");
    // yoy = (120-80)/80 * 100 = 50.00
    assertThat(cards.get(0).getYoy()).isEqualByComparingTo("50.00");

    // 关键断言：batch 方法调用次数 = 卡片数 = 1（而非 3 次单点）
    verify(empIndexResultMapper, times(1))
            .selectSlotValuesByDates(anyString(), anyList(), anyString(), anyInt());
    // 旧单点方法不应再被调用
    verify(empIndexResultMapper, never())
            .selectSlotValue(anyString(), any(LocalDate.class), anyString(), anyInt());
}
```

**注意**：既有 S3 其他 case 的 stub 都是 `selectSlotValue`，重构后会 break。V1.5 P4.1 Green 阶段需同步更新所有 S3/现存 case 的 Mock 改用 `selectSlotValuesByDates`——但这一步属于"测试同步升级"，在 Green commit 中一并改，不拆独立 commit。

- [ ] **Step 2：运行测试确认失败（Red 成立）**

```bash
mvn -Dtest=EmpIndexResultMapperBatchDatesIT verify                           # Mapper IT: fail (方法不存在)
mvn -Dtest=MetricApiImplCardsTest#getUserMetricCards_batchesWideTableQueries test  # Facade: fail
```

预期：Mapper IT 编译失败（方法不存在），Facade 新 case 失败。

- [ ] **Step 3：实现（Green 分 2 commit）**

**3a. Mapper 方法 + XML（Green Mapper commit）**：

```java
// EmpIndexResultMapper.java 追加方法
/**
 * V1.5 P4.1 新增：按 dataDate 列表批量查询同一 slot 的值.
 *
 * <p>用途：MetricApi.getUserMetricCards 的 mom/yoy 场景，一次 IN 查询
 * 拿回 current/previous/yearAgo 三个日期的 slot 值，取代原 3 次单点查询。
 *
 * <p>返回 Map 中未命中的日期不出现（调用方需 null 判断）。
 *
 * @param empId    员工工号
 * @param dates    数据日期列表（可空；为 null 或空时返回空 Map，不下发 SQL）
 * @param version  数据版本
 * @param slot     值槽（1..200，<strong>调用方必须校验</strong>）
 * @return (dataDate → metricValue) 映射；未命中日期不入 Map
 */
Map<LocalDate, BigDecimal> selectSlotValuesByDates(@Param("empId") String empId,
                                                    @Param("dates") List<LocalDate> dates,
                                                    @Param("version") String version,
                                                    @Param("slot") Integer slot);
```

```xml
<!-- EmpIndexResultMapper.xml 追加 select -->
<!--
  V1.5 P4.1：dates 批量查询。返回多行 (data_date, metricValue) 投影。
  MyBatis 默认对 Map<K,V> 结果类型通过 @MapKey 注解映射；此处改用
  resultMap 加 @MapKey 等价写法更清晰，直接 resultType="java.util.Map"
  结合 selectList-then-collect 也可。为保持 Mapper 签名声明 Map 类型，
  这里用 <select resultType> 返回 List<DataSlotRow>，在 Java 层用 default
  方法聚合为 Map。但既有代码风格是 XML 内返回 List，Java 层不做转换。
  为简化，本选项 A：新增 XML resultType=java.util.HashMap 并使用
  key-property 等模式——MyBatis 不直接支持此种；改用选项 B：
  在 Mapper 接口签名仍返回 List<DataSlotRow>，在 Facade 层 collect 到 Map.

  最终选型：选项 C（推荐）——新增 EmpDateValueRow 投影类（类似
  EmpMetricValueRow 的对称），Mapper 返回 List<EmpDateValueRow>，
  Java default 方法 selectSlotValuesByDates(...) 聚合为 Map 返回.
-->
<select id="selectSlotValuesByDatesRaw"
        resultType="com.bank.branch.platform.performance.mapper.EmpDateValueRow">
    SELECT data_date AS dataDate,
           val_${slot} AS metricValue
    FROM emp_index_result
    WHERE emp_id = #{empId}
      AND version = #{version}
      AND data_date IN
        <foreach collection="dates" item="d" open="(" separator="," close=")">
            #{d}
        </foreach>
</select>
```

```java
// EmpDateValueRow.java（新建，位于 mapper 包）
package com.bank.branch.platform.performance.mapper;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** V1.5 P4.1：selectSlotValuesByDatesRaw 返回行投影. */
@Data
public class EmpDateValueRow {
    private LocalDate dataDate;
    private BigDecimal metricValue;
}
```

```java
// EmpIndexResultMapper.java 改为 default 方法聚合
Map<LocalDate, BigDecimal> selectSlotValuesByDates(...) 的实现改为：

default Map<LocalDate, BigDecimal> selectSlotValuesByDates(String empId, List<LocalDate> dates,
                                                            String version, Integer slot) {
    if (dates == null || dates.isEmpty()) {
        return java.util.Collections.emptyMap();
    }
    List<EmpDateValueRow> rows = selectSlotValuesByDatesRaw(empId, dates, version, slot);
    Map<LocalDate, BigDecimal> result = new java.util.LinkedHashMap<>();
    for (EmpDateValueRow row : rows) {
        if (row.getMetricValue() != null) {
            result.put(row.getDataDate(), row.getMetricValue());
        }
    }
    return result;
}

// 隐藏的 "raw" Mapper 方法
List<EmpDateValueRow> selectSlotValuesByDatesRaw(@Param("empId") String empId,
                                                  @Param("dates") List<LocalDate> dates,
                                                  @Param("version") String version,
                                                  @Param("slot") Integer slot);
```

**注意**：`default` 方法在 MyBatis Mapper 接口中是合法的（自 MyBatis 3.5+ 支持），调用 raw 方法做内存层聚合——把 Map 聚合责任留在 Mapper 接口内，Facade 调用方零感知聚合细节。

---

**备选方案 B（若生产 MyBatis 插件或 Spring AOP 报 `BindingException` / 代理冲突）**：

**回退方式**：把 Mapper 只暴露 raw 方法，把聚合逻辑提到 Facade 层。

Mapper 改为：
```java
// EmpIndexResultMapper.java
List<EmpDateValueRow> selectSlotValuesByDatesRaw(
    @Param("empId") String empId,
    @Param("dates") List<LocalDate> dates,
    @Param("version") String version,
    @Param("valSlot") int valSlot);
```

Facade 层手工聚合：
```java
// MetricApiImpl.java
private Map<LocalDate, BigDecimal> fetchSlotValuesByDates(
    String empId, List<LocalDate> dates, String version, int valSlot) {
    if (dates == null || dates.isEmpty()) return Map.of();
    List<EmpDateValueRow> rows = empIndexResultMapper
        .selectSlotValuesByDatesRaw(empId, dates, version, valSlot);
    return rows.stream().collect(Collectors.toMap(
        EmpDateValueRow::getSlotDate,
        EmpDateValueRow::getValue,
        (a, b) -> a  // 同日期取首条
    ));
}
```

切换准则：若 P4.1 Step 4 跑测试报 `BindingException` / default 方法无法代理，立即回退到方案 B（同 commit 内调整，不新增 commit）。**测试断言结果不变**（验证调用次数 = 1 次 batch 查询，而非 3 次独立点查）。

**3b. Facade 重构（Refactor / Green Facade commit）**：

修改 `MetricApiImpl.buildCard`：

```java
private MetricCardDTO buildCard(String empId, PerfMetricDef def, String cycleType,
                                String version, LocalDate latestDate) {
    // V1.5 P4.1: 单次 IN 查询把 current / previous / yearAgo 三日期一次性拿回
    LocalDate previousDate = calculatePreviousDate(cycleType, latestDate);
    LocalDate yearAgoDate = latestDate == null ? null : latestDate.minusYears(1);

    List<LocalDate> dates = new ArrayList<>(3);
    if (latestDate != null) dates.add(latestDate);
    if (previousDate != null) dates.add(previousDate);
    if (yearAgoDate != null) dates.add(yearAgoDate);

    Map<LocalDate, BigDecimal> batchValues = empIndexResultMapper.selectSlotValuesByDates(
            empId, dates, version, def.getValSlot());

    BigDecimal actual = batchValues.get(latestDate);
    BigDecimal previousValue = previousDate == null ? null : batchValues.get(previousDate);
    BigDecimal yearAgoValue = yearAgoDate == null ? null : batchValues.get(yearAgoDate);

    String cycleKey = buildCycleKey(cycleType, latestDate);
    PerfTargetValue tv = perfTargetValueMapper.selectByUniqueKey(
            null, "EMP", empId, cycleKey, def.getMetricCode());
    BigDecimal target = tv == null ? null : tv.getTargetValue();
    BigDecimal rate = null;
    if (target != null && target.compareTo(BigDecimal.ZERO) != 0 && actual != null) {
        rate = actual.multiply(new BigDecimal("100"))
                .divide(target, 4, RoundingMode.HALF_UP);
    }
    BigDecimal mom = calculateMom(actual, previousValue);
    BigDecimal yoy = calculateYoy(actual, yearAgoValue);
    return MetricCardDTO.builder()
            .metricCode(def.getMetricCode())
            .metricName(def.getMetricName())
            .currentValue(actual)
            .previousValue(previousValue)
            .targetValue(target)
            .achievementRate(rate)
            .mom(mom)
            .yoy(yoy)
            .unit(def.getUnit())
            .dataDate(latestDate)
            .build();
}
```

**测试同步升级**：将 `MetricApiImplCardsTest` 既有所有 case 的 `when(empIndexResultMapper.selectSlotValue(...)).thenReturn(...)` stub 改为 `when(empIndexResultMapper.selectSlotValuesByDates(...)).thenReturn(mapOf(...))`。此步骤在 Green Facade commit 里与实现一起提交。

- [ ] **Step 4：运行测试确认通过（Green）**

```bash
mvn -Dtest=EmpIndexResultMapperBatchDatesIT verify     # Mapper IT 绿
mvn -Dtest=MetricApiImplCardsTest test                  # Facade 全绿（含既有 + P4.1 新 case）
mvn -Dtest=MetricApiImplTest test                        # 已有 metric values 测试不受影响
```

- [ ] **Step 5：Commit（3 commit：Red 测试 → Green Mapper → Refactor Facade）**

Red commit：

```bash
git add performance-engine-center/src/test/java/com/bank/branch/platform/performance/mapper/EmpIndexResultMapperBatchDatesIT.java \
        performance-engine-center/src/test/java/com/bank/branch/platform/performance/facade/MetricApiImplCardsTest.java
git commit -m "$(cat <<'EOF'
test(perf-v1.5): selectSlotValuesByDates batch API + buildCard 次数断言失败测试（Red，Task P4.1）

V1.4 reviewer M02 观察项：buildCard 对每卡片 3 次串行宽表查询（20 metric = 60 次）。
V1.5 P4.1 改为单次 IN 查询：
- Mapper IT：selectSlotValuesByDates 返回 dates→value Map，不命中日期不入 Map
- Facade 测试：getUserMetricCards_batchesWideTableQueries 断言 batch API
  调用 1 次，旧 selectSlotValue 不再被调用

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

Green Mapper commit：

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/mapper/EmpIndexResultMapper.java \
        performance-engine-center/src/main/java/com/bank/branch/platform/performance/mapper/EmpDateValueRow.java \
        performance-engine-center/src/main/resources/mapper/performance/EmpIndexResultMapper.xml
git commit -m "$(cat <<'EOF'
feat(perf-v1.5): EmpIndexResultMapper 新增 selectSlotValuesByDates batch 查询（Green，Task P4.1）

V1.4 reviewer M02 消化：新增单次 IN 查询 API，一次拉回多日期的同 slot 值。
- 新 Row 投影类 EmpDateValueRow（dataDate + metricValue）
- 新 XML select selectSlotValuesByDatesRaw（val_${slot} 动态列 + dates IN）
- default 方法 selectSlotValuesByDates 把 List→Map 聚合责任留在 Mapper 接口
- dates=null/空时 short-circuit 返回空 Map 不下发 SQL

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

Refactor Facade commit：

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/facade/MetricApiImpl.java \
        performance-engine-center/src/test/java/com/bank/branch/platform/performance/facade/MetricApiImplCardsTest.java
git commit -m "$(cat <<'EOF'
refactor(perf-v1.5): MetricApi.buildCard 改用 batch 查询降低宽表调用 66%（Green，Task P4.1）

V1.4 reviewer M02：每卡片 3 次串行查询 → 1 次 IN 查询。20 metric 场景从
60 次 → 20 次。既有 MetricApiImplCardsTest 的 stub 同步升级到 batch API；
MetricApiImplTest 宽表点查场景不受影响（queryValues 路径仍用 selectSlotValue）。

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## 阶段 P5：yoy 按 cycleType 分支

### Task P5.1：WEEKLY yoy 走 -52 周（ISO 周对齐），其他 cycleType 保持 -1 年

**背景（V1.4 S3 reviewer 观察 M03）:**
- 当前 `MetricApiImpl.getUserMetricCards`（现 `buildCard`）内 yearAgoDate 统一 `latestDate.minusYears(1)`，所有 cycleType 一致。
- **WEEKLY 边界**：`LocalDate.minusYears(1)` 保持日期的年月日不变（闰年时调整 2/29 → 2/28），但 **ISO 周周次可能偏移**——2026-01-08 是 ISO 2026W02，`.minusYears(1)` 得 2025-01-08 即 2025W02，看似匹配；但 2026-12-30 是 2026W53（53 周年），`.minusYears(1)` 到 2025-12-30 是 2025W01（下一 ISO 年度）——跨 ISO 年边界时语义倒置。
- **V1.5 P5.1 策略**：`WEEKLY` 走 `date.minus(52, ChronoUnit.WEEKS)`，保持周次对齐；YEARLY/QUARTERLY/MONTHLY/其他仍 `minusYears(1)`。
- 抽取新方法 `calculateYearAgoDate(cycleType, date)` 与 `calculatePreviousDate` 对称。

**Files:**
- Modify: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/facade/MetricApiImpl.java`
- Modify: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/facade/MetricApiImplCardsTest.java`

- [ ] **Step 1：写失败测试（Red）**

在 `MetricApiImplCardsTest.java` 新增：

```java
/**
 * V1.5 P5.1 Red：WEEKLY cycleType 的 yoy 必须走 -52 周，而非 -1 年.
 *
 * <p>V1.4 reviewer M03 观察：统一 minusYears(1) 在 WEEKLY 下跨 ISO 年边界时
 * 周次偏移。V1.5 分支处理：WEEKLY → minusWeeks(52)，其他不变.
 */
@Test
@DisplayName("[V1.5 P5.1] WEEKLY yoy：yearAgoDate 走 minusWeeks(52)，不同于 minusYears(1)")
void getUserMetricCards_weeklyYoy_usesMinusWeeks52() {
    String empId = "E001";
    LocalDate current = LocalDate.of(2026, 12, 28); // ISO 2026W53
    LocalDate previousWeek = current.minusWeeks(1); // WEEKLY 前一周 = 2026-12-21
    LocalDate yearAgoWeek52 = current.minus(52, java.time.temporal.ChronoUnit.WEEKS); // 2026-01-05 = ISO 2026W01
    LocalDate minusYearsFallback = current.minusYears(1); // 2025-12-28 = ISO 2025W52 (若走旧逻辑)

    // Red 设计：仅 stub minus-52-week 路径，旧 minusYears 路径未 stub，走旧逻辑会拿 null
    SysControl sc = new SysControl();
    sc.setCurrentVersion("v1");
    sc.setLatestDataDate(current);
    when(sysControlService.getCurrentVersion("EMP")).thenReturn(sc);
    PerfKpiScheme s1 = new PerfKpiScheme();
    s1.setId("S1");
    s1.setCycleType("WEEKLY");
    s1.setStatus("ACTIVE");
    when(kpiSchemeService.listActiveSchemes()).thenReturn(List.of(s1));
    when(kpiItemService.listBySchemeId("S1")).thenReturn(List.of(kpiItem("S1", "M_W")));
    when(metricDefService.getByCodes(List.of("M_W")))
            .thenReturn(List.of(def("M_W", "EMP", 7, "W", "户")));

    // V1.5 P4.1 后用 batch Map；Mock 按"-52 周"路径返回值
    Map<LocalDate, BigDecimal> batch = new HashMap<>();
    batch.put(current, new BigDecimal("200"));
    batch.put(previousWeek, new BigDecimal("180"));
    batch.put(yearAgoWeek52, new BigDecimal("150")); // 关键：只在 -52 周命中
    // 故意不 put minusYearsFallback，走旧逻辑会 yearAgoValue=null → yoy=null
    when(empIndexResultMapper.selectSlotValuesByDates(
            eq(empId), anyList(), eq("v1"), eq(7)))
            .thenReturn(batch);

    List<MetricCardDTO> cards = api.getUserMetricCards(empId);

    assertThat(cards).hasSize(1);
    // 走 -52 周路径：yoy = (200-150)/150*100 = 33.33
    assertThat(cards.get(0).getYoy())
            .as("WEEKLY cycleType 应走 minusWeeks(52)，命中 150 的 yearAgo 值")
            .isEqualByComparingTo("33.33");
}

/**
 * V1.5 P5.1 Red：非 WEEKLY cycleType 的 yoy 行为保持不变（仍 minusYears(1)）.
 */
@Test
@DisplayName("[V1.5 P5.1] YEARLY/QUARTERLY/MONTHLY yoy 保持 minusYears(1) 不变")
void getUserMetricCards_nonWeeklyYoy_unchanged_usesMinusYears1() {
    String empId = "E001";
    LocalDate current = LocalDate.of(2026, 4, 1);
    LocalDate previous = LocalDate.of(2026, 1, 1);       // QUARTERLY 上一季
    LocalDate yearAgo = LocalDate.of(2025, 4, 1);         // minusYears(1)
    LocalDate wrongMinus52Weeks = current.minus(52, java.time.temporal.ChronoUnit.WEEKS); // 2025-04-02（与 yearAgo 差 1 天）

    SysControl sc = new SysControl();
    sc.setCurrentVersion("v");
    sc.setLatestDataDate(current);
    when(sysControlService.getCurrentVersion("EMP")).thenReturn(sc);
    PerfKpiScheme s1 = new PerfKpiScheme();
    s1.setId("S1");
    s1.setCycleType("QUARTERLY");
    s1.setStatus("ACTIVE");
    when(kpiSchemeService.listActiveSchemes()).thenReturn(List.of(s1));
    when(kpiItemService.listBySchemeId("S1")).thenReturn(List.of(kpiItem("S1", "M_Q")));
    when(metricDefService.getByCodes(List.of("M_Q")))
            .thenReturn(List.of(def("M_Q", "EMP", 8, "Q", "万元")));

    // 只 stub minusYears(1) 路径；-52 周路径故意不填值
    Map<LocalDate, BigDecimal> batch = new HashMap<>();
    batch.put(current, new BigDecimal("150"));
    batch.put(previous, new BigDecimal("130"));
    batch.put(yearAgo, new BigDecimal("100"));
    // 故意不 put wrongMinus52Weeks
    when(empIndexResultMapper.selectSlotValuesByDates(
            eq(empId), anyList(), eq("v"), eq(8)))
            .thenReturn(batch);

    List<MetricCardDTO> cards = api.getUserMetricCards(empId);

    assertThat(cards).hasSize(1);
    // QUARTERLY 仍走 minusYears(1)：yoy = (150-100)/100*100 = 50.00
    assertThat(cards.get(0).getYoy()).isEqualByComparingTo("50.00");
}
```

- [ ] **Step 2：运行测试确认失败（Red 成立）**

```bash
mvn -Dtest=MetricApiImplCardsTest#getUserMetricCards_weeklyYoy_usesMinusWeeks52 test
```

预期：WEEKLY case 失败（当前 `minusYears(1)` 路径从 batch Map 取不到 2025-12-28 对应值，yoy=null）。

- [ ] **Step 3：实现（Green）**

```java
// MetricApiImpl.java 追加方法
/**
 * V1.5 P5.1：按 cycleType 分支的"去年同期"日期.
 *
 * <ul>
 *   <li>YEARLY / QUARTERLY / MONTHLY / 兜底 → {@code minusYears(1)}</li>
 *   <li>WEEKLY → {@code minus(52, ChronoUnit.WEEKS)}（对齐 ISO 周次，避免
 *       跨 ISO 年 53 周边界的语义倒置）</li>
 * </ul>
 *
 * @param cycleType 周期类型（大小写不敏感；null 走年兜底）
 * @param date      当期日期（null 时返回 null）
 * @return 去年同期日期
 */
private LocalDate calculateYearAgoDate(String cycleType, LocalDate date) {
    if (date == null) {
        return null;
    }
    if (cycleType == null) {
        return date.minusYears(1);
    }
    return switch (cycleType.toUpperCase()) {
        case "WEEKLY" -> date.minus(52, java.time.temporal.ChronoUnit.WEEKS);
        case "YEARLY", "QUARTERLY", "MONTHLY" -> date.minusYears(1);
        default -> date.minusYears(1);
    };
}
```

在 `buildCard` 内替换：

```java
// 旧：
// LocalDate yearAgoDate = latestDate == null ? null : latestDate.minusYears(1);

// V1.5 P5.1：
LocalDate yearAgoDate = calculateYearAgoDate(cycleType, latestDate);
```

- [ ] **Step 4：运行测试确认通过（Green）**

```bash
mvn -Dtest=MetricApiImplCardsTest test
```

预期：WEEKLY + QUARTERLY 两新 case 通过，既有 yoy 相关 case（QUARTERLY / 其他）行为保持。

- [ ] **Step 5：Commit（Red + Green 两 commit）**

Red commit：

```bash
git add performance-engine-center/src/test/java/com/bank/branch/platform/performance/facade/MetricApiImplCardsTest.java
git commit -m "$(cat <<'EOF'
test(perf-v1.5): WEEKLY yoy 走 minusWeeks(52) 分支失败测试（Red，Task P5.1）

V1.4 reviewer M03 观察项：yoy 统一 minusYears(1)，WEEKLY 跨 ISO 年 53 周
边界时周次偏移。V1.5 改按 cycleType 分支：WEEKLY → minusWeeks(52) 对齐
ISO 周；其他 cycleType 保持 minusYears(1)。
- 新 case 1: WEEKLY 期望命中 yearAgoWeek52 的 mock 值
- 新 case 2: QUARTERLY 保持 minusYears(1)，仍走 2025-04-01 路径

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

Green commit：

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/facade/MetricApiImpl.java
git commit -m "$(cat <<'EOF'
feat(perf-v1.5): getUserMetricCards yoy 按 cycleType 分支，WEEKLY 走 -52 周（Green，Task P5.1）

V1.4 reviewer M03 消化：新增 calculateYearAgoDate(cycleType, date)：
- WEEKLY → date.minus(52, ChronoUnit.WEEKS) 对齐 ISO 周
- YEARLY / QUARTERLY / MONTHLY / 兜底 → date.minusYears(1)
buildCard 内替换 yearAgoDate 计算。与 V1.4 S3.3 的 calculatePreviousDate
结构对称，维护性更好。

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## 阶段 P6：PerfTargetPlanMapper.xml updateByIdSelective owner `<if>` 分支

### Task P6.1：为 `updateByIdSelective` 补齐 ownerEmpId / ownerOrgCode 动态分支

**背景（V1.4 S2 reviewer 观察）:**
- V1.4 S2.2 给 `perf_target_plan` / `perf_target_value` 加了 `owner_emp_id` / `owner_org_code` 字段。
- `upsertBatch`（TargetValue）已在 UK 冲突时通过 `VALUES(owner_emp_id)` / `VALUES(owner_org_code)` 覆盖。
- 但 `PerfTargetPlanMapper.xml` 的 `updateByIdSelective` 缺 owner 相关的 `<if>` 分支（现有只列 planCode/planName/kpiSchemeId/targetDim/targetCycle/effectiveDate/status/updatedBy）。
- 若未来 Service 层通过 `updateByIdSelective` 单独修改 owner 字段（例如"目标方案转交给新 owner"业务），字段不会被写入。
- **前置调研确认**：
  - `PerfTargetPlanMapper.xml.updateByIdSelective` 缺 owner 分支（详见调研引用）。
  - `PerfTargetValueMapper.xml` **没有** `updateByIdSelective`（TargetValue 的更新路径只有 `upsertBatch`），无需补。
- V1.5 P6.1 仅处理 `PerfTargetPlanMapper.xml`。

**Files:**
- Modify: `performance-engine-center/src/main/resources/mapper/performance/PerfTargetPlanMapper.xml`
- Create: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/mapper/PerfTargetPlanMapperOwnerUpdateIT.java`（Mapper IT）

- [ ] **Step 1：写失败测试（Red）**

```java
// PerfTargetPlanMapperOwnerUpdateIT.java
package com.bank.branch.platform.performance.mapper;

import com.bank.branch.platform.performance.entity.PerfTargetPlan;
import com.bank.branch.platform.performance.support.PerformanceMapperTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V1.5 P6.1：PerfTargetPlanMapper.updateByIdSelective 补齐 owner 字段 <if> 分支 IT.
 */
class PerfTargetPlanMapperOwnerUpdateIT extends PerformanceMapperTestBase {

    @Autowired
    private PerfTargetPlanMapper mapper;

    @Test
    @DisplayName("updateByIdSelective：ownerEmpId 非 null 时被更新")
    void updateByIdSelective_updatesOwnerEmpIdWhenProvided() {
        PerfTargetPlan seed = buildSeed("TEST_P6_PLAN_001");
        mapper.insert(seed);

        PerfTargetPlan patch = new PerfTargetPlan();
        patch.setId("TEST_P6_PLAN_001");
        patch.setOwnerEmpId("NEW_OWNER_EMP");
        patch.setUpdatedBy("op");
        int affected = mapper.updateByIdSelective(patch);

        assertThat(affected).isGreaterThanOrEqualTo(1);
        PerfTargetPlan after = mapper.selectById("TEST_P6_PLAN_001");
        assertThat(after.getOwnerEmpId()).isEqualTo("NEW_OWNER_EMP");
        // 原字段保持不变
        assertThat(after.getPlanCode()).isEqualTo("TEST_P6_CODE_001");
        assertThat(after.getOwnerOrgCode()).isEqualTo("ORG_ORIGINAL");
    }

    @Test
    @DisplayName("updateByIdSelective：ownerOrgCode 非 null 时被更新")
    void updateByIdSelective_updatesOwnerOrgCodeWhenProvided() {
        PerfTargetPlan seed = buildSeed("TEST_P6_PLAN_002");
        mapper.insert(seed);

        PerfTargetPlan patch = new PerfTargetPlan();
        patch.setId("TEST_P6_PLAN_002");
        patch.setOwnerOrgCode("ORG_NEW");
        patch.setUpdatedBy("op");
        mapper.updateByIdSelective(patch);

        PerfTargetPlan after = mapper.selectById("TEST_P6_PLAN_002");
        assertThat(after.getOwnerOrgCode()).isEqualTo("ORG_NEW");
        assertThat(after.getOwnerEmpId()).isEqualTo("EMP_ORIGINAL");
    }

    @Test
    @DisplayName("updateByIdSelective：owner 字段 null 时保持原值不变")
    void updateByIdSelective_keepsOwnerWhenNullInPatch() {
        PerfTargetPlan seed = buildSeed("TEST_P6_PLAN_003");
        mapper.insert(seed);

        PerfTargetPlan patch = new PerfTargetPlan();
        patch.setId("TEST_P6_PLAN_003");
        patch.setPlanName("NEW_NAME"); // 只改 planName
        patch.setUpdatedBy("op");
        mapper.updateByIdSelective(patch);

        PerfTargetPlan after = mapper.selectById("TEST_P6_PLAN_003");
        assertThat(after.getPlanName()).isEqualTo("NEW_NAME");
        assertThat(after.getOwnerEmpId()).isEqualTo("EMP_ORIGINAL");
        assertThat(after.getOwnerOrgCode()).isEqualTo("ORG_ORIGINAL");
    }

    private PerfTargetPlan buildSeed(String id) {
        PerfTargetPlan p = new PerfTargetPlan();
        p.setId(id);
        p.setPlanCode("TEST_P6_CODE_" + id.substring(id.length() - 3));
        p.setPlanName("ORIGINAL_NAME");
        p.setKpiSchemeId("S_TEST");
        p.setTargetDim("EMP");
        p.setTargetCycle("MONTHLY");
        p.setEffectiveDate(LocalDate.of(2026, 1, 1));
        p.setStatus("DRAFT");
        p.setOwnerEmpId("EMP_ORIGINAL");
        p.setOwnerOrgCode("ORG_ORIGINAL");
        p.setCreatedBy("init");
        p.setCreatedTime(LocalDateTime.now());
        p.setUpdatedBy("init");
        p.setUpdatedTime(LocalDateTime.now());
        return p;
    }
}
```

- [ ] **Step 2：运行测试确认失败（Red 成立）**

```bash
mvn -Dtest=PerfTargetPlanMapperOwnerUpdateIT verify
```

预期：前两个 case 失败（owner 字段未写入），第三个 case 通过（XML 无 `<if>` 分支，owner 自然不被改）。

- [ ] **Step 3：补齐 XML 分支（Green）**

修改 `PerfTargetPlanMapper.xml` 的 `updateByIdSelective`：

```xml
<update id="updateByIdSelective" parameterType="com.bank.branch.platform.performance.entity.PerfTargetPlan">
    UPDATE perf_target_plan
    <set>
        <if test="planCode != null">plan_code = #{planCode},</if>
        <if test="planName != null">plan_name = #{planName},</if>
        <if test="kpiSchemeId != null">kpi_scheme_id = #{kpiSchemeId},</if>
        <if test="targetDim != null">target_dim = #{targetDim},</if>
        <if test="targetCycle != null">target_cycle = #{targetCycle},</if>
        <if test="effectiveDate != null">effective_date = #{effectiveDate},</if>
        <if test="status != null">status = #{status},</if>
        <!-- V1.5 P6.1 新增：owner 字段动态分支，支持"目标方案转交"业务 -->
        <if test="ownerEmpId != null">owner_emp_id = #{ownerEmpId},</if>
        <if test="ownerOrgCode != null">owner_org_code = #{ownerOrgCode},</if>
        <if test="updatedBy != null">updated_by = #{updatedBy},</if>
        <!-- 创建字段 (created_by/created_time) 不可变, 刻意不提供 <if> 分支. -->
        updated_time = NOW()
    </set>
    WHERE id = #{id}
</update>
```

- [ ] **Step 4：运行测试确认通过（Green）**

```bash
mvn -Dtest=PerfTargetPlanMapperOwnerUpdateIT verify
```

预期：3 case 全绿。

- [ ] **Step 5：Commit（Red + Green 两 commit）**

Red commit：

```bash
git add performance-engine-center/src/test/java/com/bank/branch/platform/performance/mapper/PerfTargetPlanMapperOwnerUpdateIT.java
git commit -m "$(cat <<'EOF'
test(perf-v1.5): PerfTargetPlanMapper.updateByIdSelective owner 字段更新失败测试（Red，Task P6.1）

V1.4 S2 reviewer 观察：V1_4_0 DDL 加了 owner_emp_id / owner_org_code，
但 updateByIdSelective XML 未补 <if> 分支。3 新 case 守护：
- ownerEmpId 非 null → 被更新
- ownerOrgCode 非 null → 被更新
- 两者 null 时保持原值不变（向后兼容）

PerfTargetValueMapper.xml 无 updateByIdSelective 方法（更新路径只有
upsertBatch），V1.5 P6.1 仅处理 PerfTargetPlanMapper.xml。

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

Green commit：

```bash
git add performance-engine-center/src/main/resources/mapper/performance/PerfTargetPlanMapper.xml
git commit -m "$(cat <<'EOF'
fix(perf-v1.5): PerfTargetPlanMapper.xml updateByIdSelective 补齐 owner <if> 分支（Green，Task P6.1）

V1.4 reviewer 观察消化：在 plan_code/plan_name 后追加 2 个动态分支：
- <if test="ownerEmpId != null">owner_emp_id = #{ownerEmpId},</if>
- <if test="ownerOrgCode != null">owner_org_code = #{ownerOrgCode},</if>
null 时跳过、非 null 时更新，与既有字段一致的 MyBatis selective 语义。

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## 阶段 P7：V1.5 收尾

### Task P7.1：全量回归 + 6 架构守护绿

**Files:** 无（仅执行）

- [ ] **Step 1：跑全量**

```bash
cd performance-engine-center
mvn clean verify
```

预期：
- surefire 计数 = V1.4 基线 + V1.5 Red/Green 新增（估计 +10~14，含 P1.1 -2 +2 / P2.1 +2 / P3.1 +2 / P4.1 +1 + Mapper IT +2 / P5.1 +2 / P6.1 +3 IT）
- failsafe 计数 = V1.4 基线 + V1.5 IT 新增（P4.1 EmpIndexResultMapperBatchDatesIT +2 / P6.1 PerfTargetPlanMapperOwnerUpdateIT +3 ≈ +5）
- 总测试量 ~950-965 全绿

- [ ] **Step 2：6 架构守护绿**

```bash
mvn -Dtest=BizAuthConsistencyArchTest,NoEntityInControllerArchTest,NoEntityInControllerLocalsArchTest,NoV11UOEArchTest,NoUoeInFacadeTestsArchTest,PerfErrorCodeTest test
```

预期：6 守护全绿；特别注意：
- `NoEntityInControllerLocalsArchTest`：P3.1 抽 `buildCard` 私有方法不涉及 Controller
- `NoUoeInFacadeTestsArchTest`：V1.5 所有 Red 测试不使用 `assertThrows(UnsupportedOperationException.class, ...)`
- `NoV11UOEArchTest`：无 `"V1.1 delivered"` 字面量

- [ ] **Step 3：跨模块全量（快速守护 workflow-center / bootstrap 不受影响）**

```bash
cd D:/Project/oneplate
mvn -pl workflow-center,bootstrap verify -am
```

- [ ] **Step 4：空 commit 记录回归结果**

```bash
git commit --allow-empty -m "$(cat <<'EOF'
chore(perf-v1.5): P7.1 全量回归通过（Task P7.1）

- surefire + failsafe 双绿（计数 +10~14 / +5）
- 6 架构守护绿
- workflow-center + bootstrap 不受 performance V1.5 变更影响

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task P7.2：V1.5 文档同步

**Files:**
- Modify: `CLAUDE.md`（根）：performance 状态 "V1.3 已交付（技术债清偿）" → "V1.5 已交付（V1.4 遗留 6 项清零）"；分期策略追加 V1.5 行
- Modify: `performance-engine-center/CLAUDE.md`：顶部版本 V1.4 → V1.5；分期策略表加 V1.5 行；更新"V1.4 遗留项"章节 6 项状态改"已消化"
- Modify: `docs/modules/performance-engine-center/04-对外API契约.md`：MetricApi.getUserMetricCards 说明多 cycleType 场景的卡片分组语义；新增 batch Mapper 不对外公开不需更新
- Modify: `docs/modules/performance-engine-center/03-接口设计与报文.md`（§A.5 MetricTrialRespDTO samples 历史备注改为 V1.5 已删除）

- [ ] **Step 1：更新根 CLAUDE.md**

```markdown
# 在模块状态表把 performance-engine-center 行改为：
| `performance-engine-center` | com.bank.branch.platform.performance | V1.5 已交付（V1.4 遗留清零） | 绩效计算中心 (V1.0-V1.4 累积能力 + V1.5 6 项技术债清零) |
```

- [ ] **Step 2：更新模块 CLAUDE.md**

顶部版本描述：

```markdown
**当前版本**: V1.5（V1.4 遗留 6 项清零）—— 在 V1.4 交付之上清理最后 6 项观察/兼容项：
- P1 MetricTrialRespDTO @Deprecated getSamples() 删除 + @JsonAlias 移除
- P2 HistoryRecalcService cycleType=""/"   " 空串 behavior 测试补齐
- P3 MetricApi.getUserMetricCards 多 scheme 首命中歧义修复（按 metricCode+cycleType 分组）
- P4 mom/yoy 宽表查询 batch 化（20 metric 从 60 次降至 20 次，-66%）
- P5 yoy 按 cycleType 分支（WEEKLY 走 -52 周 ISO 对齐）
- P6 PerfTargetPlanMapper.xml updateByIdSelective owner <if> 分支补齐
```

分期策略表追加：

```markdown
| **V1.5** | 6 项 V1.4 遗留清零：@Deprecated getSamples 删除 / cycleType 空串测试 / codeToCycleType 分组修复 / batch 宽表 / yoy WEEKLY 分支 / updateByIdSelective owner <if> | **本期交付（2026-04-24）** |
```

"V1.4 遗留项"章节每项状态改"已消化（2026-04-24，V1.5 P*）"；"V1.5+ 遗留项"表清空或仅保留"无"。

- [ ] **Step 3：更新 04 API 契约**

在 `docs/modules/performance-engine-center/04-对外API契约.md` 的 `MetricApi.getUserMetricCards` 描述中追加说明：

```markdown
#### 返回卡片分组语义（V1.5 P3.1 更新）

- 同一 metricCode 被多个 KPI 方案共享且 cycleType 不同时，返回多张卡片（每个 cycleType 一张）
- 前端须按 (metricCode, cycleType) 作为唯一显示键；MetricCardDTO 未新增 cycleType 字段，保持既有契约
- 未来若需显式区分 cycleType，以新字段 + 向后兼容方式引入
```

- [ ] **Step 3c：更新 03-接口设计与报文.md**

找到 A.5 章节 MetricTrialRespDTO samples 相关内容，从"V1.3 已 @Deprecated 但保留向后兼容"改为"V1.5 P1 已彻底删除，生产已无调用者（2026-04-24）"。

- [ ] **Step 4：Commit**

```bash
git add CLAUDE.md \
        performance-engine-center/CLAUDE.md \
        docs/modules/performance-engine-center/04-对外API契约.md \
        docs/modules/performance-engine-center/03-接口设计与报文.md
git commit -m "$(cat <<'EOF'
docs(perf-v1.5): V1.5 交付后文档全量同步（Task P7.2）

- 根 CLAUDE.md: performance 状态 V1.3/V1.4 → V1.5
- 模块 CLAUDE.md: 顶部版本 V1.5 + 分期策略行 + V1.4 遗留项全标已消化
- 04 对外 API 契约: getUserMetricCards 追加多 cycleType 分组语义说明
- 03 接口设计与报文: §A.5 MetricTrialRespDTO samples 历史备注改为 V1.5 已删除

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task P7.3：V1.5 技术债清算

**Files:**
- Modify: `performance-engine-center/CLAUDE.md` 技术债章节

- [ ] **Step 1：清算 V1.4 遗留清单 → V1.5 消化清单**

把 "V1.5+ 遗留项" 表 6 行全标"✅ 已消化（V1.5 P*）"，并追加新章节：

```markdown
### V1.5 已消化项（2026-04-24）

V1.5 P1-P6 共 6 个 Task 消化以下 6 项 V1.4 遗留技术债：

- **P1.1 MetricTrialRespDTO @Deprecated getSamples() 彻底删除**
  - 删除 `@Deprecated @JsonIgnore getSamples()` 方法 + `@JsonAlias({"samples"})` 反序列化别名
  - 反射 + FAIL_ON_UNKNOWN_PROPERTIES 双守护
  - 生产代码 zero consumer，删除零风险
- **P2.1 HistoryRecalcService cycleType=""/"   " behavior 测试**
  - V1.4 S4.2 实现 `isBlank()` 已覆盖空串与纯空格，V1.5 补 2 case 守护
  - 不改代码，仅补测试
- **P3.1 MetricApi.getUserMetricCards 多 scheme 首命中歧义修复**
  - 将 `codeToCycleType` LinkedHashMap 替换为 `LinkedHashSet<MetricKey>` 组合键
  - 同 metricCode 跨方案 cycleType 不同 → 多卡片；相同 → 去重 1 卡
  - 抽 `buildCard(...)` 私有方法降低圈复杂度
- **P4.1 mom/yoy 3 次宽表查询 batch 优化**
  - 新增 `EmpIndexResultMapper.selectSlotValuesByDates` + `EmpDateValueRow` Row 投影类
  - 20 metric 场景从 60 次宽表查询降至 20 次（-66%）
  - 使用 MyBatis default 方法聚合，Facade 调用零感知
- **P5.1 yoy WEEKLY cycleType 走 -52 周**
  - 新增 `calculateYearAgoDate(cycleType, date)`：WEEKLY → `minus(52, WEEKS)`，其他 `minusYears(1)`
  - 消除跨 ISO 年 53 周边界时周次偏移的 yoy 歧义
- **P6.1 PerfTargetPlanMapper.xml updateByIdSelective owner <if> 补齐**
  - 追加 `ownerEmpId` / `ownerOrgCode` 两个 `<if>` 动态分支
  - 3 IT case 守护行为（非 null 更新 / null 保持 / 非 owner 字段单独更新不影响 owner）
  - PerfTargetValueMapper.xml 无 updateByIdSelective 方法，无需补

### V1.6+ 遗留项（登记）

V1.5 交付后无明确已登记的观察项。若未来 reviewer 或生产运维发现新技术债，在此登记。
```

- [ ] **Step 2：Commit**

```bash
git add performance-engine-center/CLAUDE.md
git commit -m "$(cat <<'EOF'
docs(perf-v1.5): V1.5 技术债清算 + V1.4 遗留 6 项全消化（Task P7.3）

- V1.4 遗留清单 6 项全标已消化（P1/P2/P3/P4/P5/P6）
- 新增"V1.5 已消化项"章节记录清偿明细
- "V1.6+ 遗留项"表清空，performance 模块技术债清零

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## 验收清单

V1.5 交付后必须全部满足：

- [ ] `mvn clean verify` 全绿（surefire ≥ V1.4 基线 +10，failsafe ≥ V1.4 基线 +5，合计 ~950-965 tests）
- [ ] 6 架构守护绿（BizAuth / NoEntityInController / NoEntityInControllerLocals / NoV11UOE / NoUoeInFacadeTests / PerfErrorCodeTest）
- [ ] `MetricTrialRespDTO.getSamples()` 方法不存在（反射断言绿 + 生产代码 zero consumer）
- [ ] `MetricTrialRespDTO` 的 `sampleRows` 字段无 `@JsonAlias`（FAIL_ON_UNKNOWN_PROPERTIES 守护 `samples` 键反序列化报错）
- [ ] `MetricApiImpl.getUserMetricCards` 在多 scheme 不同 cycleType 引用同 metric 时，返回多张卡片（每 cycleType 一张）
- [ ] `EmpIndexResultMapper.selectSlotValuesByDates` 存在且 `buildCard` 每卡片仅 1 次调用（而非 3 次 `selectSlotValue`）
- [ ] `calculateYearAgoDate(cycleType, date)` 存在：WEEKLY → `minus(52, ChronoUnit.WEEKS)`，其他 → `minusYears(1)`
- [ ] `HistoryRecalcService` 对 `cycleType=""` 与 `"   "` 与 `null` 三态行为一致（params_json 均跳过 cycleType 字段）
- [ ] `PerfTargetPlanMapper.xml updateByIdSelective` 包含 `ownerEmpId` / `ownerOrgCode` 两个 `<if>` 分支
- [ ] 文档三份同步（根 CLAUDE.md / 模块 CLAUDE.md / 04 契约）
- [ ] Flyway 链路完整：V1_0_0 → … → V1_4_0（V1.5 不新增 Flyway 脚本）
- [ ] PerfErrorCode 仍 30 条（V1.5 不新增）

---

## 风险与回滚

| 风险 | 严重度 | 应对 |
|---|---|---|
| P1.1 隐藏消费者：某前端或外部 SDK 依赖 `samples` JSON 字段反序列化 | 中 | V1.3 R3.2 已用 1 版本过渡期公告；V1.5 删除前 grep 全仓确认 zero consumer；若生产发现破坏，紧急回滚该 commit（只涉及 1 文件）并发 V1.5.1 补丁延长兼容期 |
| P3.1 卡片数量膨胀：前端未适配多卡片逻辑 | 高 | 既有单 cycleType 场景卡片数不变（向后兼容）；多 cycleType 分组仅发生在"多方案共享同 metric"的特定场景，可与前端同步排期。V1.5 默认生效；若上线后前端对"同 metric 多卡片"行为不兼容，单 Phase revert（P3.1 Red + Green 两个 commit 配对回滚） |
| P4.1 `selectSlotValuesByDates` 在宽表未命中任何日期时返回空 Map → actual=null → 整个卡片 null 化 | 中 | 在 `buildCard` 仍按 V1.4 行为（actual=null 时 rate/mom/yoy 均 null），前端已按 null 展示 "-"；Mapper IT 覆盖"dates 有命中 + 有未命中"混合场景 |
| P4.1 MyBatis default 方法在某 IDE/IDE-plugin 下不被识别为 Mapper 方法 | 低 | 已知兼容：MyBatis 3.5+ 原生支持 default；3.0.3 属于 MyBatis 官方重命名版（底层仍基于 3.5 系）；运行时 Spring Bean 代理正常。若发现报 `BindingException`，降级为在 Facade 层手工聚合（Mapper 接口只声明 raw，Facade 调用 raw + 自行 collect） |
| P5.1 WEEKLY yoy 跨 53 周年时 `minus(52, WEEKS)` 仍有 ±1 周误差 | 低 | V1.5 P5.1 将统一逻辑对齐主流"去年同 ISO 周"语义；极端场景（2020/2026 这类 ISO 53 周年） yoy 边界案例 V1.6+ 如果业务反馈再补 `adjustToIsoWeek` 辅助方法 |
| P6.1 PerfTargetPlanMapper.updateByIdSelective 生产调用方未传 owner 字段但传了其他字段，行为变化 | 极低 | `<if test="ownerEmpId != null">` 仅在非 null 时写入，null 时跳过——向后兼容，既有调用零破坏 |
| 测试计数偏差过大（±10 上限） | 低 | P7.1 计数目标 +10~14 Red/Green test + +5 failsafe IT；若实际偏差 > 20，P7.1 空 commit 阶段回查：可能是 P4.1 重构时 S3 既有 case 改 stub 导致新增几个 helper 断言；写在 commit message 说明 |

---

## 回滚策略

V1.5 6 项任务相互独立（P3 与 P4/P5 对同一方法耦合但互不破坏）：

1. **单 Phase 回滚**：`git revert <P*.1 Green commit>`（revert 对应 Red commit 可选，保留测试作为守护也行）
2. **整期回滚**：V1.5 不新增 Flyway 脚本，代码 revert 即可；不需要 undo SQL
3. **P1.1 紧急回滚**：若前端/SDK 发现依赖 `samples` JSON 字段，revert P1.1 Refactor commit，重新暴露 `@Deprecated getSamples()` 与 `@JsonAlias`，发 V1.5.1 补丁并重新公告延长兼容期至 V1.7

---

## 与前五份计划的关系

- **V1.0 整改**（2026-04-22-performance-v1.0-rectification-plan.md）：28 commit 已交付
- **V1.1 迭代**（2026-04-22-performance-v1.1-iteration-plan.md）：53 commit 已交付
- **V1.2 迭代**（2026-04-22-performance-v1.2-iteration-plan.md）：58 commit 已交付
- **V1.3 迭代**（2026-04-24-performance-v1.3-iteration-plan.md）：40 commit 已交付
- **V1.4 迭代**（2026-04-24-performance-v1.4-iteration-plan.md）：38-42 commit 已交付
- **V1.5 本计划**：6 项技术债清零 + 9 Task（预计 **15 commit**，Red/Green 各半 + 3 收尾 commit）

V1.5 交付后 performance-engine-center 技术债清零，除非 reviewer 或生产发现新观察项，后续无需再开"清偿型"迭代；V1.6+ 应转向业务能力扩展（例如 portal 对接 / 报表数据供给）。
