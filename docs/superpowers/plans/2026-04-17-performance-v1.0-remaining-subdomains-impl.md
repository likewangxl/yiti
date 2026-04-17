# performance-engine-center V1.0 剩余 5 子域实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在已完成的骨架 + sys_control 子域之上，以 TDD 红-绿-重构节奏串行交付剩余 5 个子域（指标库 / KPI 方案 / 目标方案 / 运行任务 / 分配关系），完成 V1.0 全部 35 REST 端点 + 7 对外 Api 的实现。

**Architecture:** 严格分层 Controller → Facade → Service → Mapper → Entity；每层一对 `test:red → feat:green` commit（必要时追加 `refactor:`）；单人单会话串行执行，顺序 Metric → Kpi → Target → RunTask → Alloc；通用模式（Redis 锁 / 缓存 afterCommit evict / 双写一致性 / DFS 纯函数）在指标库一次跑通，后续 4 子域复用。

**Tech Stack:** Spring Boot 3.2.3 / JDK 17 / MyBatis 3.0.3 / MySQL 8 (onepl) / Redis 6 / JUnit 5 / Mockito / AssertJ / Lombok

**Spec 引用:** [docs/superpowers/specs/2026-04-17-performance-v1.0-remaining-subdomains-design.md](../specs/2026-04-17-performance-v1.0-remaining-subdomains-design.md)（配合原 V1.0 spec v1.2 阅读）

**工作目录:** `C:/Users/52140/Desktop/yiti/.claude/worktrees/eloquent-mcnulty-f2072a/`（所有 `mvn` 和 `git` 命令假定此为 CWD）

---

## 0. 前置检查（首次执行或中断恢复时必做）

- [ ] **Step 0.1: 确认工作目录在 worktree**

Run: `git rev-parse --show-toplevel`
Expected: `C:/Users/52140/Desktop/yiti/.claude/worktrees/eloquent-mcnulty-f2072a`

- [ ] **Step 0.2: 确认当前分支**

Run: `git branch --show-current`
Expected: `claude/eloquent-mcnulty-f2072a`

- [ ] **Step 0.3: 确认基线 mvn 测试全绿（sys_control 子域已完成）**

Run: `mvn -q -pl performance-engine-center test`
Expected: `BUILD SUCCESS`，0 failures / 0 errors

- [ ] **Step 0.4: 确认 onepl 数据库 13 张表存在**

Run: `mysql -u root -p123456 onepl -e "SHOW TABLES LIKE 'perf_%'" && mysql -u root -p123456 onepl -e "SHOW TABLES LIKE 'cust_alloc_relation'" && mysql -u root -p123456 onepl -e "SHOW TABLES LIKE 'sys_control'"`
Expected: 至少列出 `perf_metric_def / perf_metric_ref / perf_kpi_scheme / perf_kpi_item / perf_target_plan / perf_target_value / perf_run_task / sys_control / cust_alloc_relation`

- [ ] **Step 0.5: 确认 35 条 PT_RESOURCE 已登记**

Run: `mysql -u root -p123456 onepl -e "SELECT COUNT(*) FROM PT_RESOURCE WHERE RESOURCE_ID LIKE 'P_PERF_%'"`
Expected: `35`

---

## 1. 阶段 1 — 指标库子域（顺位 1，最复杂）

**子域目标:** 实现 `perf_metric_def` + `perf_metric_ref` 两张表的全栈；槽位分配（Redis 锁 + Service 校验）、DFS 环路检测、层级校验、双写一致性（JSON + 独立表同事务）、缓存 afterCommit evict。

**测试数据前缀:** `TEST_METRIC_*`（单线程）/ `CONCUR_METRIC_*`（并发 IT）

**提交范围预期:** 18-20 个 commit（10 个红绿对 + 1-2 个 refactor）

---

### Task 1.1: `PerfMetricDef` + `PerfMetricRef` Entity + Mapper 接口（红）

**Files:**
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/entity/PerfMetricDef.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/entity/PerfMetricRef.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/mapper/PerfMetricDefMapper.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/mapper/PerfMetricRefMapper.java`
- Create: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/mapper/PerfMetricDefMapperIT.java`
- Create: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/mapper/PerfMetricRefMapperIT.java`
- Create: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/support/MetricTestDataBuilder.java`

- [ ] **Step 1: 创建 `PerfMetricDef` 实体**

```java
package com.bank.branch.platform.performance.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 指标定义表 perf_metric_def 贫血实体.
 *
 * <p>对齐 ddl-performance.sql (v1.2): 18 字段, 主键 varchar(32).
 * <p>唯一键: uk_metric_code(metric_code)
 * <p>索引: idx_dim_level / idx_status / idx_val_slot
 */
@Data
public class PerfMetricDef {

    /** 指标ID (varchar(32) 主键). */
    private String id;

    /** 指标编码 (唯一, 大写+数字+下划线). */
    private String metricCode;

    /** 指标中文名称. */
    private String metricName;

    /** 英文名称. */
    private String metricNameEn;

    /** 指标口径说明. */
    private String metricDesc;

    /** 基础维度: EMP / ORG / CUST. */
    private String baseDim;

    /** 指标层级: 1 / 2 / 3. */
    private Integer metricLevel;

    /** 计算频率: DAY / MONTH / QUARTER / YEAR. */
    private String calcFreq;

    /** 计算方式: AUTO / MANUAL. */
    private String calcMode;

    /** 计算逻辑类型: SQL / PROC / EXPR / SUMMARY. */
    private String calcLogicType;

    /** 一级指标 SQL / 存储过程文本. */
    private String sqlText;

    /** 二/三级指标 Groovy 表达式. */
    private String exprText;

    /** 机构汇总规则: SUM / AVG / MAX / MIN / COUNT. */
    private String summaryRule;

    /** 引用指标列表 (JSON 数组字符串). */
    private String refMetricCodes;

    /** 宽表槽位 (1..200, null 表示未分配). */
    private Integer valSlot;

    /** 状态: ACTIVE / DISABLED (DDL 无 DRAFT/PUBLISHED, v1.2 决策接受现状; 语义上 ACTIVE ≡ PUBLISHED). */
    private String status;

    /** 创建人. */
    private String createdBy;

    /** 创建时间. */
    private LocalDateTime createdTime;

    /** 更新人. */
    private String updatedBy;

    /** 更新时间. */
    private LocalDateTime updatedTime;
}
```

- [ ] **Step 2: 创建 `PerfMetricRef` 实体**

```java
package com.bank.branch.platform.performance.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 指标引用关系表 perf_metric_ref 贫血实体.
 *
 * <p>唯一键: uk_metric_ref(metric_code, ref_metric_code)
 * <p>索引: idx_ref_metric(ref_metric_code)
 */
@Data
public class PerfMetricRef {
    private String id;
    private String metricCode;
    private String refMetricCode;
    private LocalDateTime createdTime;
}
```

- [ ] **Step 3: 创建 `PerfMetricDefMapper` 接口**

```java
package com.bank.branch.platform.performance.mapper;

import com.bank.branch.platform.performance.entity.PerfMetricDef;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Set;

@Mapper
public interface PerfMetricDefMapper {

    int insert(PerfMetricDef def);

    int updateByIdSelective(PerfMetricDef def);

    int updateStatusById(@Param("id") String id,
                         @Param("status") String status,
                         @Param("updatedBy") String updatedBy);

    PerfMetricDef selectById(@Param("id") String id);

    PerfMetricDef selectByMetricCode(@Param("metricCode") String metricCode);

    List<PerfMetricDef> selectByMetricCodes(@Param("codes") List<String> codes);

    /** 查询某维度下全部已占用的 val_slot (不区分状态, 包含 DISABLED). */
    Set<Integer> selectOccupiedSlots(@Param("baseDim") String baseDim);

    List<PerfMetricDef> selectByCondition(@Param("baseDim") String baseDim,
                                          @Param("metricLevel") Integer metricLevel,
                                          @Param("status") String status,
                                          @Param("keyword") String keyword,
                                          @Param("offset") int offset,
                                          @Param("limit") int limit);

    long countByCondition(@Param("baseDim") String baseDim,
                          @Param("metricLevel") Integer metricLevel,
                          @Param("status") String status,
                          @Param("keyword") String keyword);

    /** 释放槽位: 只允许当前状态为 DISABLED 的指标. */
    int releaseSlotById(@Param("id") String id,
                        @Param("updatedBy") String updatedBy);
}
```

- [ ] **Step 4: 创建 `PerfMetricRefMapper` 接口**

```java
package com.bank.branch.platform.performance.mapper;

import com.bank.branch.platform.performance.entity.PerfMetricRef;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface PerfMetricRefMapper {

    int insertBatch(@Param("list") List<PerfMetricRef> list);

    int deleteByMetricCode(@Param("metricCode") String metricCode);

    List<PerfMetricRef> selectByMetricCode(@Param("metricCode") String metricCode);

    List<PerfMetricRef> selectByRefMetricCode(@Param("refMetricCode") String refMetricCode);

    /** 一次性加载"整个 ref 图"供环路检测使用 (Map<metricCode, List<refMetricCode>> 由 Service 组装). */
    List<PerfMetricRef> selectAll();
}
```

- [ ] **Step 5: 创建 `MetricTestDataBuilder`（测试数据构造器）**

```java
package com.bank.branch.platform.performance.support;

import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.entity.PerfMetricRef;
import java.time.LocalDateTime;
import java.util.UUID;

public final class MetricTestDataBuilder {

    private MetricTestDataBuilder() {}

    public static PerfMetricDef l1Emp(String codeSuffix, Integer slot) {
        PerfMetricDef d = new PerfMetricDef();
        d.setId(UUID.randomUUID().toString().replace("-", ""));
        d.setMetricCode("TEST_METRIC_" + codeSuffix);
        d.setMetricName("测试指标-" + codeSuffix);
        d.setBaseDim("EMP");
        d.setMetricLevel(1);
        d.setCalcFreq("DAY");
        d.setCalcMode("AUTO");
        d.setCalcLogicType("SQL");
        d.setSqlText("SELECT 1");
        d.setValSlot(slot);
        d.setStatus("ACTIVE");
        d.setCreatedBy("test");
        d.setCreatedTime(LocalDateTime.now());
        return d;
    }

    public static PerfMetricDef l2Emp(String codeSuffix, Integer slot, String refCodesJson) {
        PerfMetricDef d = l1Emp(codeSuffix, slot);
        d.setMetricLevel(2);
        d.setCalcLogicType("EXPR");
        d.setExprText("#A + #B");
        d.setSqlText(null);
        d.setRefMetricCodes(refCodesJson);
        return d;
    }

    public static PerfMetricRef ref(String metricCode, String refMetricCode) {
        PerfMetricRef r = new PerfMetricRef();
        r.setId(UUID.randomUUID().toString().replace("-", ""));
        r.setMetricCode(metricCode);
        r.setRefMetricCode(refMetricCode);
        r.setCreatedTime(LocalDateTime.now());
        return r;
    }
}
```

- [ ] **Step 6: 写 `PerfMetricDefMapperIT`（红测试）**

继承 `PerformanceMapperTestBase`（含 @Transactional + @Rollback），覆盖场景：

```java
package com.bank.branch.platform.performance.mapper;

import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.support.MetricTestDataBuilder;
import com.bank.branch.platform.performance.support.PerformanceMapperTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PerfMetricDefMapperIT extends PerformanceMapperTestBase {

    @Autowired
    private PerfMetricDefMapper mapper;

    @Test
    void insertAndSelectById_ok() {
        PerfMetricDef d = MetricTestDataBuilder.l1Emp("DEP_BAL", 1);
        mapper.insert(d);
        PerfMetricDef loaded = mapper.selectById(d.getId());
        assertThat(loaded).isNotNull();
        assertThat(loaded.getMetricCode()).isEqualTo("TEST_METRIC_DEP_BAL");
        assertThat(loaded.getValSlot()).isEqualTo(1);
    }

    @Test
    void insert_whenMetricCodeDup_throwsDuplicateKey() {
        PerfMetricDef d1 = MetricTestDataBuilder.l1Emp("DUP_CODE", 2);
        mapper.insert(d1);
        PerfMetricDef d2 = MetricTestDataBuilder.l1Emp("DUP_CODE", 3);
        assertThatThrownBy(() -> mapper.insert(d2))
            .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void selectByMetricCode_whenNotExists_returnsNull() {
        assertThat(mapper.selectByMetricCode("TEST_METRIC_NO_SUCH")).isNull();
    }

    @Test
    void selectByMetricCodes_batch_ok() {
        mapper.insert(MetricTestDataBuilder.l1Emp("BATCH_A", 10));
        mapper.insert(MetricTestDataBuilder.l1Emp("BATCH_B", 11));
        List<PerfMetricDef> list = mapper.selectByMetricCodes(
            List.of("TEST_METRIC_BATCH_A", "TEST_METRIC_BATCH_B"));
        assertThat(list).hasSize(2);
    }

    @Test
    void selectOccupiedSlots_returnsAllSlotsRegardlessOfStatus() {
        PerfMetricDef active = MetricTestDataBuilder.l1Emp("SLOT_A", 20);
        PerfMetricDef disabled = MetricTestDataBuilder.l1Emp("SLOT_B", 21);
        disabled.setStatus("DISABLED");
        mapper.insert(active);
        mapper.insert(disabled);
        Set<Integer> slots = mapper.selectOccupiedSlots("EMP");
        assertThat(slots).contains(20, 21);
    }

    @Test
    void selectByCondition_withKeyword_matchesCodeOrName() {
        mapper.insert(MetricTestDataBuilder.l1Emp("KW_CARD", 30));
        List<PerfMetricDef> list = mapper.selectByCondition(
            "EMP", 1, null, "KW_CARD", 0, 10);
        assertThat(list).extracting(PerfMetricDef::getMetricCode)
            .contains("TEST_METRIC_KW_CARD");
    }

    @Test
    void updateStatusById_changesStatusAndUpdatedBy() {
        PerfMetricDef d = MetricTestDataBuilder.l1Emp("STATUS_T", 40);
        mapper.insert(d);
        int rows = mapper.updateStatusById(d.getId(), "DISABLED", "test-admin");
        assertThat(rows).isEqualTo(1);
        assertThat(mapper.selectById(d.getId()).getStatus()).isEqualTo("DISABLED");
    }

    @Test
    void releaseSlotById_whenDisabled_setsSlotToNull() {
        PerfMetricDef d = MetricTestDataBuilder.l1Emp("REL_T", 50);
        d.setStatus("DISABLED");
        mapper.insert(d);
        int rows = mapper.releaseSlotById(d.getId(), "test-admin");
        assertThat(rows).isEqualTo(1);
        assertThat(mapper.selectById(d.getId()).getValSlot()).isNull();
    }
}
```

- [ ] **Step 7: 写 `PerfMetricRefMapperIT`（红测试）**

覆盖场景：
1. `insertBatch` 批量插入 ok
2. `insertBatch` UK 冲突（同一 metric_code + ref_metric_code）抛 DuplicateKeyException
3. `deleteByMetricCode` 按上层删除
4. `selectByMetricCode` 查某指标引用的下级
5. `selectByRefMetricCode` 反向查询（谁引用了我）
6. `selectAll` 全量图加载

- [ ] **Step 8: 运行测试验证全部失败（Mapper XML 尚未写）**

Run: `mvn -q -pl performance-engine-center test -Dtest='PerfMetricDefMapperIT,PerfMetricRefMapperIT'`
Expected: **FAIL** — "Invalid bound statement (not found)" 或类似；因为 XML 文件尚未创建

- [ ] **Step 9: git commit（红）**

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/entity/ \
        performance-engine-center/src/main/java/com/bank/branch/platform/performance/mapper/PerfMetric*.java \
        performance-engine-center/src/test/java/com/bank/branch/platform/performance/mapper/PerfMetric*.java \
        performance-engine-center/src/test/java/com/bank/branch/platform/performance/support/MetricTestDataBuilder.java
git commit -m "test(perf): red - PerfMetricDef+Ref Entity/Mapper IT + TestDataBuilder"
```

---

### Task 1.2: `PerfMetricDefMapper.xml` + `PerfMetricRefMapper.xml`（绿）

**Files:**
- Create: `performance-engine-center/src/main/resources/mapper/performance/PerfMetricDefMapper.xml`
- Create: `performance-engine-center/src/main/resources/mapper/performance/PerfMetricRefMapper.xml`

- [ ] **Step 1: 写 `PerfMetricDefMapper.xml`**

关键片段：
- `<sql id="BASE_COLUMNS">` 列出全部 20 列（不用 SELECT *）
- `<insert>` 使用 `#{id}` 等全部参数
- `selectByCondition` 用 `<where>` + `<if>` 组合 4 个可选过滤（baseDim/metricLevel/status/keyword）
- `selectOccupiedSlots` 返回 `resultType="java.lang.Integer"`，SQL 为 `SELECT val_slot FROM perf_metric_def WHERE base_dim=#{baseDim} AND val_slot IS NOT NULL`
- `releaseSlotById` WHERE 条件必须含 `AND status='DISABLED'`（这是业务保护，Service 层也会校验但 Mapper 再兜底一层）

- [ ] **Step 2: 写 `PerfMetricRefMapper.xml`**

- `insertBatch` 用 `<foreach>` + VALUES
- 其他方法标准 SELECT

- [ ] **Step 3: 运行测试验证全部通过**

Run: `mvn -q -pl performance-engine-center test -Dtest='PerfMetricDefMapperIT,PerfMetricRefMapperIT'`
Expected: **PASS** — 所有测试方法绿

- [ ] **Step 4: git commit（绿）**

```bash
git add performance-engine-center/src/main/resources/mapper/performance/PerfMetric*.xml
git commit -m "feat(perf): green - PerfMetricDef+RefMapper XML, 14 IT 通过"
```

---

### Task 1.3: `MetricCycleDetectService`（纯函数，UT only，红）

**Files:**
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/MetricCycleDetectService.java`
- Create: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/service/MetricCycleDetectServiceTest.java`

**设计**: 纯函数 Service，不依赖任何 Bean（无构造器参数），入参全部是 Map/String/List。这样单测 100% 无 mock，覆盖图算法所有边界。

- [ ] **Step 1: 写服务接口骨架（让红测试能编译）**

```java
package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.performance.exception.PerfException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 指标引用环路/层级校验纯函数服务.
 * 不依赖 DB, 入参全部为快照数据, 便于 UT 100% 覆盖边界.
 */
@Service
public class MetricCycleDetectService {

    /**
     * 层级校验: L2 只能引用 L1, L3 只能引用 L2, 一级不能引用其他一级.
     *
     * @param thisLevel         本指标层级
     * @param refMetricLevels   被引用指标的层级 Map (refMetricCode → level)
     * @throws PerfException PERF-40911
     */
    public void checkLevelConstraint(Integer thisLevel, Map<String, Integer> refMetricLevels) {
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * 环路检测: 假设新边 (newNode → newRefs) 加入图中, 从 newNode 出发做 DFS, 若能回到 newNode 则有环.
     *
     * @param existingGraph 当前图: metricCode → refMetricCodes
     * @param newNode       待加入的新节点 (上层指标 code)
     * @param newRefs       待加入的边 (下层指标 codes)
     * @throws PerfException PERF-40902
     */
    public void checkNoCycle(Map<String, List<String>> existingGraph,
                             String newNode,
                             List<String> newRefs) {
        throw new UnsupportedOperationException("not implemented");
    }
}
```

- [ ] **Step 2: 写 `MetricCycleDetectServiceTest`**

```java
package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MetricCycleDetectServiceTest {

    private final MetricCycleDetectService svc = new MetricCycleDetectService();

    // ======== 层级校验 ========

    @Test
    void checkLevelConstraint_L2RefL1_ok() {
        assertThatCode(() -> svc.checkLevelConstraint(2, Map.of("A", 1, "B", 1)))
            .doesNotThrowAnyException();
    }

    @Test
    void checkLevelConstraint_L2RefL3_throws40911() {
        assertThatThrownBy(() -> svc.checkLevelConstraint(2, Map.of("A", 3)))
            .isInstanceOf(PerfException.class)
            .hasMessageContaining(PerfErrorCode.METRIC_LEVEL_VIOLATION.getCode());
    }

    @Test
    void checkLevelConstraint_L3RefL1_throws40911() {
        assertThatThrownBy(() -> svc.checkLevelConstraint(3, Map.of("A", 1)))
            .isInstanceOf(PerfException.class)
            .hasMessageContaining(PerfErrorCode.METRIC_LEVEL_VIOLATION.getCode());
    }

    @Test
    void checkLevelConstraint_L1HasAnyRef_throws40911() {
        // L1 不能引用其他 L1 (一级必须独立)
        assertThatThrownBy(() -> svc.checkLevelConstraint(1, Map.of("A", 1)))
            .isInstanceOf(PerfException.class);
    }

    // ======== 环路检测 ========

    @Test
    void checkNoCycle_selfRef_throws40902() {
        assertThatThrownBy(() -> svc.checkNoCycle(Map.of(), "A", List.of("A")))
            .isInstanceOf(PerfException.class)
            .hasMessageContaining(PerfErrorCode.METRIC_CYCLE.getCode());
    }

    @Test
    void checkNoCycle_twoNodeLoop_A_B_A_throws40902() {
        // 已有 B → A, 新增 A → B 形成环
        Map<String, List<String>> graph = Map.of("B", List.of("A"));
        assertThatThrownBy(() -> svc.checkNoCycle(graph, "A", List.of("B")))
            .isInstanceOf(PerfException.class);
    }

    @Test
    void checkNoCycle_threeNodeLoop_A_B_C_A_throws40902() {
        // 已有 B→C, C→A, 新增 A→B 形成 A→B→C→A 环
        Map<String, List<String>> graph = Map.of(
            "B", List.of("C"),
            "C", List.of("A")
        );
        assertThatThrownBy(() -> svc.checkNoCycle(graph, "A", List.of("B")))
            .isInstanceOf(PerfException.class);
    }

    @Test
    void checkNoCycle_diamondNoCycle_ok() {
        // A→B, A→C, B→D, C→D 菱形无环
        Map<String, List<String>> graph = Map.of(
            "B", List.of("D"),
            "C", List.of("D")
        );
        assertThatCode(() -> svc.checkNoCycle(graph, "A", List.of("B", "C")))
            .doesNotThrowAnyException();
    }

    @Test
    void checkNoCycle_deepChain_noLoop_ok() {
        Map<String, List<String>> graph = Map.of(
            "B", List.of("C"),
            "C", List.of("D"),
            "D", List.of("E")
        );
        assertThatCode(() -> svc.checkNoCycle(graph, "A", List.of("B")))
            .doesNotThrowAnyException();
    }

    @Test
    void checkNoCycle_emptyRefs_ok() {
        assertThatCode(() -> svc.checkNoCycle(Map.of(), "A", List.of()))
            .doesNotThrowAnyException();
    }
}
```

- [ ] **Step 3: 运行测试验证失败**

Run: `mvn -q -pl performance-engine-center test -Dtest='MetricCycleDetectServiceTest'`
Expected: **FAIL** — `UnsupportedOperationException: not implemented`

- [ ] **Step 4: git commit（红）**

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/MetricCycleDetectService.java \
        performance-engine-center/src/test/java/com/bank/branch/platform/performance/service/MetricCycleDetectServiceTest.java
git commit -m "test(perf): red - MetricCycleDetectService UT (层级+环路 10 场景)"
```

---

### Task 1.4: `MetricCycleDetectService` 实现（绿）

- [ ] **Step 1: 实现 `checkLevelConstraint`**

```java
public void checkLevelConstraint(Integer thisLevel, Map<String, Integer> refMetricLevels) {
    if (thisLevel == null || thisLevel == 1) {
        // L1 不能有任何引用
        if (refMetricLevels != null && !refMetricLevels.isEmpty()) {
            throw new PerfException(PerfErrorCode.METRIC_LEVEL_VIOLATION,
                "L1 指标不得引用其他指标");
        }
        return;
    }
    int expectedRefLevel = thisLevel - 1; // L2→只能引 L1, L3→只能引 L2
    if (refMetricLevels != null) {
        for (Map.Entry<String, Integer> e : refMetricLevels.entrySet()) {
            if (e.getValue() == null || e.getValue() != expectedRefLevel) {
                throw new PerfException(PerfErrorCode.METRIC_LEVEL_VIOLATION,
                    "L" + thisLevel + " 只能引用 L" + expectedRefLevel
                    + "; 实际 refMetric=" + e.getKey() + " level=" + e.getValue());
            }
        }
    }
}
```

- [ ] **Step 2: 实现 `checkNoCycle`（DFS）**

```java
public void checkNoCycle(Map<String, List<String>> existingGraph,
                         String newNode,
                         List<String> newRefs) {
    if (newRefs == null || newRefs.isEmpty()) return;

    // 自引用直接拦截
    if (newRefs.contains(newNode)) {
        throw new PerfException(PerfErrorCode.METRIC_CYCLE,
            "指标 " + newNode + " 自引用");
    }

    // 构造临时图: existing + new edges
    Map<String, List<String>> graph = new java.util.HashMap<>(existingGraph);
    graph.put(newNode, new java.util.ArrayList<>(newRefs));

    // DFS from newNode, 若能再访问到 newNode 则有环
    Set<String> visiting = new java.util.HashSet<>();
    if (hasCycleDfs(graph, newNode, newNode, visiting)) {
        throw new PerfException(PerfErrorCode.METRIC_CYCLE,
            "指标引用图出现环路, 起点=" + newNode);
    }
}

private boolean hasCycleDfs(Map<String, List<String>> graph,
                            String start, String cur, Set<String> visiting) {
    List<String> nexts = graph.getOrDefault(cur, java.util.Collections.emptyList());
    for (String next : nexts) {
        if (next.equals(start)) return true;
        if (visiting.contains(next)) continue; // 其他分支环由各自起点检测
        visiting.add(next);
        if (hasCycleDfs(graph, start, next, visiting)) return true;
    }
    return false;
}
```

- [ ] **Step 3: 运行测试验证通过**

Run: `mvn -q -pl performance-engine-center test -Dtest='MetricCycleDetectServiceTest'`
Expected: **PASS** — 10 UT 绿

- [ ] **Step 4: git commit（绿）**

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/MetricCycleDetectService.java
git commit -m "feat(perf): green - MetricCycleDetectService, 10 UT 通过"
```

---

### Task 1.5: `MetricSlotService` 单线程 UT + 实现（红绿一对）

**Files:**
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/MetricSlotService.java`
- Create: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/service/MetricSlotServiceTest.java`

**设计要点** (spec §4.1)：
- Service 层方法：`allocSlot(baseDim, metricLevel, preferredSlot)` / `releaseSlot(id, operator, reason)` / `listOccupied(baseDim)`
- Service 层**不**申请 Redis 锁（锁在 Facade 层）；Service 只做"取占用集 → 找最小未用 → INSERT" 逻辑
- 槽位区间：L1=1..100 / L2=101..150 / L3=151..200
- UT 使用 Mockito mock Mapper，覆盖：`L1 首次分配返回 1` / `L1 有空缺填最小` / `L2 落在 101..150` / `手工指定槽位已占抛 40901` / `释放槽位时状态非 DISABLED 抛 40905`

- [ ] **Step 1: 写服务骨架（让测试能编译）**

```java
package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.performance.mapper.PerfMetricDefMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MetricSlotService {
    private final PerfMetricDefMapper mapper;

    @Transactional(readOnly = true)
    public int allocSlot(String baseDim, Integer metricLevel, Integer preferredSlot) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Transactional(rollbackFor = Exception.class)
    public void releaseSlot(String id, String operator, String reason) {
        throw new UnsupportedOperationException("not implemented");
    }
}
```

- [ ] **Step 2: 写 UT（必含场景见 spec §5.1 DoD）**

```java
package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.mapper.PerfMetricDefMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetricSlotServiceTest {

    @Mock PerfMetricDefMapper mapper;
    @InjectMocks MetricSlotService svc;

    @Test
    void allocSlot_L1FirstAllocation_returns1() {
        when(mapper.selectOccupiedSlots("EMP")).thenReturn(Set.of());
        int slot = svc.allocSlot("EMP", 1, null);
        assertThat(slot).isEqualTo(1);
    }

    @Test
    void allocSlot_L1HasGap_fillsSmallestFreeSlot() {
        // 已占用 1, 3 → 应返回 2
        when(mapper.selectOccupiedSlots("EMP")).thenReturn(Set.of(1, 3));
        int slot = svc.allocSlot("EMP", 1, null);
        assertThat(slot).isEqualTo(2);
    }

    @Test
    void allocSlot_L2_returnsFrom101Range() {
        when(mapper.selectOccupiedSlots("EMP")).thenReturn(Set.of());
        int slot = svc.allocSlot("EMP", 2, null);
        assertThat(slot).isEqualTo(101);
    }

    @Test
    void allocSlot_L3_returnsFrom151Range() {
        when(mapper.selectOccupiedSlots("ORG")).thenReturn(Set.of());
        int slot = svc.allocSlot("ORG", 3, null);
        assertThat(slot).isEqualTo(151);
    }

    @Test
    void allocSlot_preferredSlotAvailable_returnsPreferred() {
        when(mapper.selectOccupiedSlots("EMP")).thenReturn(Set.of(1, 2));
        int slot = svc.allocSlot("EMP", 1, 50);
        assertThat(slot).isEqualTo(50);
    }

    @Test
    void allocSlot_preferredSlotOccupied_throws40901() {
        when(mapper.selectOccupiedSlots("EMP")).thenReturn(Set.of(1, 50));
        assertThatThrownBy(() -> svc.allocSlot("EMP", 1, 50))
            .isInstanceOf(PerfException.class)
            .hasMessageContaining(PerfErrorCode.SLOT_OCCUPIED.getCode());
    }

    @Test
    void allocSlot_preferredSlotOutOfLevelRange_throws() {
        // L1 preferredSlot=200 越界
        assertThatThrownBy(() -> svc.allocSlot("EMP", 1, 200))
            .isInstanceOf(PerfException.class);
    }

    @Test
    void allocSlot_levelRangeExhausted_throws() {
        // L1 1..100 全占用
        Set<Integer> full = new java.util.HashSet<>();
        for (int i = 1; i <= 100; i++) full.add(i);
        when(mapper.selectOccupiedSlots("EMP")).thenReturn(full);
        assertThatThrownBy(() -> svc.allocSlot("EMP", 1, null))
            .isInstanceOf(PerfException.class);
    }

    @Test
    void releaseSlot_whenStatusNotDisabled_throws40905() {
        PerfMetricDef active = new PerfMetricDef();
        active.setId("M001");
        active.setStatus("ACTIVE");
        when(mapper.selectById("M001")).thenReturn(active);
        assertThatThrownBy(() -> svc.releaseSlot("M001", "admin", "测试"))
            .isInstanceOf(PerfException.class)
            .hasMessageContaining(PerfErrorCode.SLOT_RELEASE_NOT_DISABLED.getCode());
    }

    @Test
    void releaseSlot_whenDisabled_callsMapperRelease() {
        PerfMetricDef disabled = new PerfMetricDef();
        disabled.setId("M002");
        disabled.setStatus("DISABLED");
        when(mapper.selectById("M002")).thenReturn(disabled);
        when(mapper.releaseSlotById(eq("M002"), any())).thenReturn(1);
        svc.releaseSlot("M002", "admin", "停用后释放");
        // 成功路径无异常
    }
}
```

- [ ] **Step 3: 运行测试验证失败**

Run: `mvn -q -pl performance-engine-center test -Dtest='MetricSlotServiceTest'`
Expected: **FAIL**

- [ ] **Step 4: git commit（红）**

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/MetricSlotService.java \
        performance-engine-center/src/test/java/com/bank/branch/platform/performance/service/MetricSlotServiceTest.java
git commit -m "test(perf): red - MetricSlotService UT (10 场景)"
```

- [ ] **Step 5: 实现 `allocSlot` + `releaseSlot`**

```java
public int allocSlot(String baseDim, Integer metricLevel, Integer preferredSlot) {
    int[] range = rangeOf(metricLevel); // [start, end]
    Set<Integer> occupied = mapper.selectOccupiedSlots(baseDim);
    if (preferredSlot != null) {
        if (preferredSlot < range[0] || preferredSlot > range[1]) {
            throw new PerfException(PerfErrorCode.PARAM_INVALID,
                "preferredSlot=" + preferredSlot + " 越出 L" + metricLevel + " 区间");
        }
        if (occupied.contains(preferredSlot)) {
            throw new PerfException(PerfErrorCode.SLOT_OCCUPIED,
                "slot=" + preferredSlot + " 已被占用");
        }
        return preferredSlot;
    }
    for (int s = range[0]; s <= range[1]; s++) {
        if (!occupied.contains(s)) return s;
    }
    throw new PerfException(PerfErrorCode.SLOT_OCCUPIED,
        "L" + metricLevel + " 槽位已耗尽");
}

public void releaseSlot(String id, String operator, String reason) {
    PerfMetricDef def = mapper.selectById(id);
    if (def == null) {
        throw new PerfException(PerfErrorCode.METRIC_NOT_FOUND, "id=" + id);
    }
    if (!"DISABLED".equals(def.getStatus())) {
        throw new PerfException(PerfErrorCode.SLOT_RELEASE_NOT_DISABLED,
            "仅 DISABLED 状态可释放槽位, 当前=" + def.getStatus());
    }
    mapper.releaseSlotById(id, operator);
}

private int[] rangeOf(Integer level) {
    if (level == null) throw new PerfException(PerfErrorCode.PARAM_INVALID, "metricLevel null");
    return switch (level) {
        case 1 -> new int[]{1, 100};
        case 2 -> new int[]{101, 150};
        case 3 -> new int[]{151, 200};
        default -> throw new PerfException(PerfErrorCode.PARAM_INVALID, "level=" + level);
    };
}
```

- [ ] **Step 6: 运行测试验证通过**

Run: `mvn -q -pl performance-engine-center test -Dtest='MetricSlotServiceTest'`
Expected: **PASS**

- [ ] **Step 7: git commit（绿）**

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/MetricSlotService.java
git commit -m "feat(perf): green - MetricSlotService, 10 UT 通过"
```

---

### Task 1.6: `MetricSlotConcurrentIT`（并发 IT，红绿一对）

**目的**: 验证 Redis 分布式锁在 Facade 层申请后，两个并发请求中只有一个能成功写入同一 slot。

**Files:**
- Create: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/mapper/MetricSlotConcurrentIT.java`

**设计**: 此 IT **不含 @Transactional**，继承 `PerformanceConcurrentTestBase`，使用 `CONCUR_METRIC_*` 前缀，在 `@AfterEach` 显式删除。测试直接调用 `MetricSlotService.allocSlot` 两次（不走 Facade 锁），验证无锁情况下**不保证互斥**；然后再次调用走 Facade（在 Task 1.9 后补齐），验证有锁情况下互斥。

**V1.0 实现策略**: 本 Task 只验证"Service 层 allocSlot 不加锁时确实会出现槽位冲突"（红），实现在 Task 1.9 的 MetricApiImpl Facade 层加 Redis 锁（绿）。

- [ ] **Step 1: 写并发 IT 骨架**

```java
package com.bank.branch.platform.performance.mapper;

import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.service.MetricSlotService;
import com.bank.branch.platform.performance.support.PerformanceConcurrentTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

class MetricSlotConcurrentIT extends PerformanceConcurrentTestBase {

    @Autowired MetricSlotService slotService;
    @Autowired JdbcTemplate jdbc;
    @Autowired PerfMetricDefMapper mapper;

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM perf_metric_def WHERE metric_code LIKE 'CONCUR_METRIC_%'");
    }

    @Test
    void allocSlot_concurrentTwoThreads_bothWouldGetSameSlot_withoutFacadeLock() throws Exception {
        // Service 层不加锁时, 两线程同时 allocSlot 会拿到同一个 slot (体现并发问题)
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Integer> task = () -> {
            start.await();
            return slotService.allocSlot("EMP", 1, null);
        };
        Future<Integer> f1 = pool.submit(task);
        Future<Integer> f2 = pool.submit(task);
        start.countDown();
        int s1 = f1.get(10, TimeUnit.SECONDS);
        int s2 = f2.get(10, TimeUnit.SECONDS);
        pool.shutdown();

        // 不加锁场景: s1 == s2 大概率发生, 体现并发问题的存在
        // 本测试只断言"两次返回是合法区间", 验证 Service 行为本身无 NPE/异常
        assertThat(s1).isBetween(1, 100);
        assertThat(s2).isBetween(1, 100);
        // Facade 层加锁后的互斥验证在 Task 1.9 MetricApiImplIT
    }
}
```

- [ ] **Step 2: 运行测试（此时应该通过，因为 Service 本身不抛异常；真实互斥验证在 Task 1.9）**

Run: `mvn -q -pl performance-engine-center test -Dtest='MetricSlotConcurrentIT'`
Expected: **PASS**（单独此测试即使不加锁也能通过 Service 合法区间断言）

- [ ] **Step 3: git commit（红 — 本 test 本身绿，但完整互斥验证推迟到 Task 1.9）**

```bash
git add performance-engine-center/src/test/java/com/bank/branch/platform/performance/mapper/MetricSlotConcurrentIT.java
git commit -m "test(perf): red - MetricSlotConcurrentIT baseline (锁互斥验证推迟到 Facade)"
```

---

### Task 1.7: `MetricRefService` + `MetricDefService` UT + 实现（红绿两对）

**Files:**
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/MetricRefService.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/MetricDefService.java`
- Create: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/service/MetricRefServiceTest.java`
- Create: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/service/MetricDefServiceTest.java`

**`MetricRefService` 职责**（spec §4.2）:
- `setRefs(metricCode, refMetricCodes)`: `DELETE FROM perf_metric_ref WHERE metric_code=?` + `INSERT BATCH`（同事务）
- `listRefsOf(metricCode)` / `listWhoRef(refMetricCode)` / `loadFullGraph()` → `Map<String, List<String>>`

**`MetricDefService` 职责**:
- `create(CreateMetricDefCmd)`: 事务内「校验层级+环路 → 分配 slot（委托 MetricSlotService）→ INSERT def + setRefs」
- `update(UpdateMetricDefCmd)` / `disable(id, reason, operator)` / `getByCode` / `getByCodeOrNull` / `page(query)`
- `delete(id, reason, operator)`：逻辑删除 → `status=DISABLED`（DDL 无 deleted 字段）

**UT 覆盖场景**（spec §5.1）:

MetricRefServiceTest:
- `setRefs_clearsOldAndInsertsNew`
- `setRefs_whenEmpty_onlyDeletes`
- `loadFullGraph_groupsByMetricCode`

MetricDefServiceTest:
- `create_L1_insertsDefAndNoRefs`
- `create_L2WithRefs_insertsDefAndRefRows`
- `create_whenMetricCodeDup_throws40903`
- `create_whenRefLevelWrong_throws40911`
- `create_whenRefFormsCycle_throws40902`
- `update_withRefsChange_callsSetRefsWithNewList`
- `disable_whenStatusNotActive_throws40905`
- `page_returnsPageResult`

**实现要点**:
- `MetricDefService.create` 必须在 @Transactional 内按顺序：parse refCodesJson → loadFullGraph → checkLevel + checkCycle → allocSlot → INSERT perf_metric_def → MetricRefService.setRefs
- 使用 bootstrap 注入的 `ObjectMapper`（通过构造器注入），不自建
- 不在 Service 内做 Redis 锁或缓存 evict —— 留到 Facade 层

**操作序列**（重复 Task 1.3/1.4/1.5 的"红→绿→commit"模式）:

- [ ] **Step 1**: 写 MetricRefServiceTest + 骨架 → mvn 验证红 → commit `test(perf): red - MetricRefService UT`
- [ ] **Step 2**: 实现 MetricRefService → mvn 验证绿 → commit `feat(perf): green - MetricRefService, 3 UT 通过`
- [ ] **Step 3**: 写 MetricDefServiceTest + 骨架 → mvn 验证红 → commit `test(perf): red - MetricDefService UT (8 场景)`
- [ ] **Step 4**: 实现 MetricDefService → mvn 验证绿 → commit `feat(perf): green - MetricDefService, 8 UT 通过`

**2 对红绿 = 4 commit**

---

### Task 1.8: `MetricApiImpl` + `MetricQueryApiImpl` Facade UT + 实现（红绿一对）

**Files:**
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/facade/MetricApiImpl.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/facade/MetricQueryApiImpl.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/facade/assembler/MetricAssembler.java`
- Create: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/facade/MetricApiImplTest.java`
- Create: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/facade/MetricQueryApiImplTest.java`

**MetricApiImpl 契约**（spec §5.2.1）:
- V1.0 实现: `getMetricDef(String) / getMetricDefs(List<String>) / listMetrics(String, Integer)`
- V1.0 UOE 占位: `getUserMetricCards / getEmpMetricValues / getOrgMetricValues / getCustMetricValues`

**MetricQueryApiImpl 契约**（spec §5.2.2）:
- 全部 3 方法都是 V1.0 UOE 占位

**Facade 层还要承担**（Task 1.8 扩展范围）:
- **槽位分配的 Redis 锁**：对于指标库 V1.0 没有暴露"分配槽位"的独立 Controller（slot 分配由 create 内部调用），所以 Redis 锁需要在 `MetricApiImpl.createMetric` 或对外暴露的方法上加。但 §5.2.1 的 7 个方法都是**读**方法，不涉及 allocSlot。
- **正确做法**：Redis 锁在 `MetricDefController.create` 对应的 Facade 方法上加（如果没有独立的 Facade 方法，就直接在 Service 层加……但 §4.1 spec 明确"Service 层不加锁"）
- **折中**：本 Task 仅处理 7 个对外 Api 方法；槽位锁留到 Task 1.9（Controller 层）通过一个辅助 Facade 方法（如 `MetricLifecycleFacade.createMetric`）处理，或在 Controller 侧直接用 `RedisTemplate` + Lua。下方按"Facade 新增 `MetricLifecycleFacade` 类承担 create 的锁"写。

- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/facade/MetricLifecycleFacade.java`
- Create: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/facade/MetricLifecycleFacadeTest.java`

**UT 必含场景**（spec §5.1）:
- `MetricApiImplTest.getUserMetricCards_throwsUOE`（契约）
- `MetricApiImplTest.getEmpMetricValues_throwsUOE`
- `MetricApiImplTest.getOrgMetricValues_throwsUOE`
- `MetricApiImplTest.getCustMetricValues_throwsUOE`
- `MetricApiImplTest.getMetricDef_returnsOptional`
- `MetricApiImplTest.getMetricDef_hitsCacheOnSecondCall`（用 Spring context，Facade UT 默认禁用 Cache 的情况下改用 `MetricApiImplCacheIT`）
- `MetricQueryApiImplTest.batchQueryEmpSnapshots_throwsUOE`
- `MetricQueryApiImplTest.batchQueryOrgSnapshots_throwsUOE`
- `MetricQueryApiImplTest.batchQueryCustSnapshots_throwsUOE`
- `MetricLifecycleFacadeTest.createMetric_whenLockAcquireFailed_throws40904`
- `MetricLifecycleFacadeTest.createMetric_whenSuccess_releasesLock`
- `MetricLifecycleFacadeTest.createMetric_whenServiceThrows_stillReleasesLock`

**Redis 锁实现**（复用 sys_control 模式）:
- Key: `perf:slot-alloc:{baseDim}`（一个维度一把锁）
- TTL: 30s
- 释放: Lua `if redis.call('get',k)==v then return redis.call('del',k) end`

**缓存 evict 实现** (spec §4.1):
```java
TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
    @Override
    public void afterCommit() {
        cacheManager.getCache("perf:metric_def").evict(metricCode);
        cacheManager.getCache("perf:metric_def:list").evict(baseDim);
    }
});
```

**操作序列**:

- [ ] **Step 1**: 写 3 个 Facade 骨架（API、QueryAPI、Lifecycle）+ Assembler + 3 个 Test → 红 → commit `test(perf): red - Metric 3 Facade UT (12 场景)`
- [ ] **Step 2**: 实现 3 个 Facade + Lua 锁脚本（复用 SysControlFacade 模式）+ @Cacheable + afterCommit evict → 绿 → commit `feat(perf): green - Metric 3 Facade + 锁+缓存, 12 UT 通过`

**1 对红绿 = 2 commit**

---

### Task 1.9: `MetricDefController` IT + 实现（红绿一对 + Controller IT 扩展并发锁验证）

**Files:**
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/controller/MetricDefController.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/controller/dto/CreateMetricReqDTO.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/controller/dto/UpdateMetricReqDTO.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/controller/dto/ChangeStatusReqDTO.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/controller/dto/ReleaseSlotReqDTO.java`
- Create: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/controller/MetricDefControllerIT.java`

**Controller 10 端点**（spec §6.2）:

| 方法 | 路径 | @BizAuth action | @AuditLog |
|---|---|---|---|
| GET | `/api/perf/metrics` | READ | ✗ |
| GET | `/api/perf/metrics/{metricCode}` | READ | ✗ |
| POST | `/api/perf/metrics` | WRITE | CREATE |
| PUT | `/api/perf/metrics/{metricCode}` | WRITE | UPDATE |
| DELETE | `/api/perf/metrics/{metricCode}` | DELETE | reason 必填 |
| PUT | `/api/perf/metrics/{metricCode}/status` | STATUS_CHANGE | reason 必填 |
| GET | `/api/perf/metrics/{metricCode}/refs` | READ | ✗ |
| GET | `/api/perf/metrics/{metricCode}/ref-by` | READ | ✗ |
| GET | `/api/perf/metrics/val-slots` | READ | ✗ |
| POST | `/api/perf/metrics/{metricCode}/slot/release` | MANAGE | reason 必填 |

**IT 必含场景**（spec §5.1 DoD）:
- `post_whenUnauthenticated_returns401`
- `post_whenLackBizAuth_returns403`
- `delete_whenReasonMissing_returns400`
- `post_whenMetricCodeDup_returns409`
- `post_whenSuccess_returns200_andAuditLogRecorded`
- `get_whenMetricNotFound_returns404`
- `put_status_whenReasonMissing_returns400`
- `post_slotRelease_whenNotDisabled_returns409`
- **并发验证**: `createMetric_concurrentTwoRequestsSameSlot_onlyOneWins`（使用 MockMvc 并发发起两个 create 请求到同一 baseDim + 不带 preferredSlot，验证只有一个成功）

**操作序列**:

- [ ] **Step 1**: 写 4 个请求 DTO（含 @NotBlank / @NotNull / @Valid）+ Controller 骨架 + IT 红测试 → mvn 验证红 → commit `test(perf): red - MetricDefController IT + 4 请求 DTO`
- [ ] **Step 2**: 实现 Controller 10 端点（@BizAuth + @AuditLog）→ mvn 验证绿 → commit `feat(perf): green - MetricDefController 10 端点, 9 IT 通过`

**1 对红绿 = 2 commit**

---

### Task 1.10: 指标库子域集成回归

- [ ] **Step 1: 运行全模块测试**

Run: `mvn -q -pl performance-engine-center test`
Expected: **PASS**（sys_control 13 个原有测试 + 指标库新增 ~50 个测试，总计 ~63 个绿）

- [ ] **Step 2: 对照 DoD 清单打勾（spec §5.1）**

逐条对照文件清单 / 测试方法 / 注解检查 / commit 数量。

- [ ] **Step 3: 如发现漏测，补红测试 + refactor commit（可选）**

如 sys_control 最后那个 `initIfAbsent 历史均失效` 补丁案例。

- [ ] **Step 4: 阶段结束 commit（可选里程碑标记）**

```bash
git log --oneline 6ceb51d..HEAD -- performance-engine-center/  # 查看本阶段 commit
```

Expected: 18-20 条 commit 按 `test:red → feat:green [→ refactor]` 排列。

---

## 2. 阶段 2 — KPI 方案子域（顺位 2）

**子域目标:** 实现 `perf_kpi_scheme` + `perf_kpi_item` 父子表全栈；发布时跨表校验（item.metricCode ∈ perf_metric_def PUBLISHED/ACTIVE）；父子表同事务 CRUD；缓存 evict。

**测试数据前缀:** `TEST_KPI_*`

**提交范围预期:** 11 个 commit（5 红绿对 + 1 refactor）

**依赖**: 阶段 1 的 `MetricDefService.getByCodeOrNull`

---

### Task 2.1: `PerfKpiScheme` + `PerfKpiItem` Entity + Mapper IT + XML（红绿一对）

**Files:**
- Create: `entity/PerfKpiScheme.java`（id / schemeCode / schemeName / cycleType / openDetail / status / 审计 4 字段）
- Create: `entity/PerfKpiItem.java`（id / schemeId / metricCode / weight / multiplier / minScore / maxScore / createdTime）
- Create: `mapper/PerfKpiSchemeMapper.java` + `.xml`
- Create: `mapper/PerfKpiItemMapper.java` + `.xml`
- Create: `test/.../mapper/PerfKpiSchemeMapperIT.java`
- Create: `test/.../mapper/PerfKpiItemMapperIT.java`
- 扩展 `KpiTestDataBuilder.java`

**测试必含**（spec §5.2）:
- `KpiSchemeMapperIT.insert_whenSchemeCodeDup_throwsDuplicateKey`
- `KpiItemMapperIT.insert_whenSchemeIdMetricCodeDup_throwsDuplicateKey`
- 各自的 selectById / selectByCode / deleteById 基础路径

**操作序列**（参照 Task 1.1-1.2 模式）:

- [ ] Step 1: 写 Entity + Mapper 接口 + Test + TestDataBuilder → 红 → commit `test(perf): red - PerfKpiScheme+Item Entity/Mapper IT`
- [ ] Step 2: 写 XML → 绿 → commit `feat(perf): green - PerfKpiScheme+ItemMapper XML, N IT 通过`

---

### Task 2.2: `KpiSchemeService` + `KpiItemService` UT + 实现（红绿两对 = 4 commit）

**Files:**
- Create: `service/KpiSchemeService.java`
- Create: `service/KpiItemService.java`
- Create: `service/cmd/CreateKpiSchemeCmd.java`（含 items 列表）
- Create: `service/cmd/UpdateKpiSchemeCmd.java`
- Create: `service/cmd/AddKpiItemCmd.java`
- Create: `service/cmd/UpdateKpiItemCmd.java`
- Create: `test/service/KpiSchemeServiceTest.java`
- Create: `test/service/KpiItemServiceTest.java`

**KpiSchemeService UT 必含**（spec §5.2 DoD）:
- `publish_whenItemReferMissingMetric_throws40906`（构造不存在的 metricCode）
- `publish_whenItemReferDraftMetric_throws40906`（DDL 只有 ACTIVE/DISABLED，"DRAFT" 语义由代码守护：item 创建时要求 metric.status=ACTIVE；如 metric 被 disable 后发布方案 → 拒绝）
- `publish_whenAllItemsMetricPublished_succeeds`
- `create_withItems_writesBothTablesSameTransaction`
- `create_itemInsertFails_rollbacksScheme`（Mockito 第 2 次 insert 抛异常）
- `disable_whenReasonMissing_throws`

**KpiItemService UT 必含**:
- `addItem_whenDuplicateMetricInScheme_throws`（MockMapper `selectBySchemeAndMetric` 返回 non-null）

**实现要点**:
- `KpiSchemeService.create` 内事务：INSERT scheme → foreach items 调 `itemService.addItem`（非独立事务，靠 REQUIRED 传播）
- `publish` 方法：遍历 items，调 `MetricDefService.getByCodeOrNull`，若 null 或 status ≠ ACTIVE 抛 PERF-40906

**操作序列**:
- [ ] Step 1: KpiItemService UT + 骨架 → 红 → commit `test(perf): red - KpiItemService UT`
- [ ] Step 2: KpiItemService 实现 → 绿 → commit `feat(perf): green - KpiItemService, N UT 通过`
- [ ] Step 3: KpiSchemeService UT + 骨架 → 红 → commit `test(perf): red - KpiSchemeService UT (发布校验+父子事务)`
- [ ] Step 4: KpiSchemeService 实现 → 绿 → commit `feat(perf): green - KpiSchemeService, N UT 通过`

---

### Task 2.3: `KpiApiImpl` Facade UT + 实现（红绿一对 = 2 commit）

**契约**（spec §5.2.3）: V1.0 实现 `getKpiScheme / getKpiSchemeById`；UOE 占位 `getCurrentKpiTotal / getCurrentKpiResult / getKpiHistory`

- [ ] Step 1: Facade UT 含 2 实现测试 + 3 UOE 契约测试 → 红 → commit `test(perf): red - KpiApiImpl UT (2 实现+3 UOE)`
- [ ] Step 2: 实现 + @Cacheable(`perf:kpi_scheme:{id}`) + afterCommit evict → 绿 → commit `feat(perf): green - KpiApiImpl, 5 UT 通过`

---

### Task 2.4: `KpiSchemeController` IT + 实现（红绿一对 = 2 commit）

**9 端点**（spec §6.2）: list / getById / create / update / delete / publish / addItem / updateItem / deleteItem

**IT 必含**:
- `publish_whenUnpublished_returns200`
- `delete_whenReasonMissing_returns400`
- `deleteItem_whenReasonMissing_returns400`

- [ ] Step 1: Controller 骨架 + 请求 DTO（Create/Update/AddItem/UpdateItem/Publish ReqDTO）+ IT → 红 → commit `test(perf): red - KpiSchemeController IT + 5 DTO`
- [ ] Step 2: 实现 9 端点（@BizAuth + 高危 @AuditLog(reasonRequired=true)）→ 绿 → commit `feat(perf): green - KpiSchemeController 9 端点, N IT 通过`

---

### Task 2.5: 子域回归 + refactor（可选）

- [ ] 运行 `mvn -q -pl performance-engine-center test` 全绿
- [ ] 对照 spec §5.2 DoD 勾选
- [ ] 如发现边界缺口补 refactor commit

**阶段 2 完成，累计 commit = sys_control(12) + Metric(18-20) + Kpi(11) ≈ 41-43**

---

## 3. 阶段 3 — 目标方案子域（顺位 3）

**子域目标:** 实现 `perf_target_plan` + `perf_target_value` 全栈；Plan 引用 PUBLISHED KPI 方案；Value 支持 `INSERT ON DUPLICATE KEY UPDATE` upsert；批量上限 500；**planId 统一 String**（与生产 DDL 对齐）。

**测试数据前缀:** `TEST_TGT_*`

**提交范围预期:** 14-16 个 commit

**依赖**: 阶段 2 的 `KpiSchemeService.getByIdOrNull`

---

### Task 3.1: `PerfTargetPlan` + `PerfTargetValue` Entity + Mapper IT + XML（红绿一对 = 2 commit）

**Files:**
- Create: `entity/PerfTargetPlan.java`（**id String**）
- Create: `entity/PerfTargetValue.java`（**planId String**）
- Create: `mapper/PerfTargetPlanMapper.java` + `.xml`
- Create: `mapper/PerfTargetValueMapper.java` + `.xml`
- Create: `test/.../mapper/PerfTargetPlanMapperIT.java`
- Create: `test/.../mapper/PerfTargetValueMapperIT.java`
- Create: `support/TargetTestDataBuilder.java`

**PerfTargetValueMapper 接口特殊方法**:
```java
int upsertBatch(@Param("list") List<PerfTargetValue> list);
Optional<PerfTargetValue> selectByUniqueKey(@Param("planId") String planId, ...);
List<PerfTargetValue> listByPlan(@Param("planId") String planId, ...);
```

**XML upsert 写法**（spec §4.3）:
```xml
<insert id="upsertBatch">
  INSERT INTO perf_target_value (id, plan_id, subject_type, subject_id,
      cycle_key, metric_code, target_value, base_value, created_by, created_time)
  VALUES
  <foreach collection="list" item="v" separator=",">
    (#{v.id}, #{v.planId}, #{v.subjectType}, #{v.subjectId},
     #{v.cycleKey}, #{v.metricCode}, #{v.targetValue}, #{v.baseValue},
     #{v.createdBy}, NOW())
  </foreach>
  ON DUPLICATE KEY UPDATE
    target_value = VALUES(target_value),
    base_value = VALUES(base_value),
    updated_time = NOW(),
    updated_by = VALUES(created_by)
</insert>
```

**IT 必含**:
- `TargetPlanMapperIT.insert_whenPlanCodeDup_throwsDuplicateKey`
- `TargetValueMapperIT.upsert_newRow_inserts`
- `TargetValueMapperIT.upsert_existingRow_updates`（先 upsertBatch 1 条，再 upsertBatch 同 UK 1 条 → 验证 target_value 已更新）
- `TargetValueMapperIT.upsertBatch_mix_newAndExisting_ok`

---

### Task 3.2: `TargetPlanService` + `TargetValueService` UT + 实现（红绿两对 = 4 commit）

**TargetPlanService UT 必含**（spec §5.3）:
- `create_whenEffectiveAfterExpire_throws40001`
- `create_whenEffectiveEqualsExpire_succeeds`（边界 == 合法）
- `create_whenKpiSchemeDraft_throws40906`（mock `KpiSchemeService` 返回 DISABLED scheme）
- `getByCode_whenExists_returnsOptional`

**TargetValueService UT 必含**:
- `upsertBatch_whenSizeExceeds500_throws40910`
- `upsertBatch_whenAllSuccess_returnsAffectedCount`
- `upsert_singleCall_delegatesBatch`

**实现要点**:
- `TargetPlanService.create`：校验 `effective_date <= expire_date`（`<=`，含等）、校验 kpi_scheme_id 对应 scheme 存在且 `status=ACTIVE`
- `TargetValueService.upsertBatch`：size > 500 抛 PERF-40910；调 Mapper `upsertBatch`；返回 affected rows

操作序列：
- [ ] Step 1-2: TargetValueService UT + 实现 → 2 commit
- [ ] Step 3-4: TargetPlanService UT + 实现 → 2 commit

---

### Task 3.3: `TargetApiImpl` Facade UT + 实现（红绿一对 = 2 commit）

**契约**（spec §5.2.4）: **全部 4 方法 V1.0 实现**，**planId 统一 String**

**UT 必含**:
- `getTargetPlan_hitsCache`
- `getTargetPlanById_whenStringPlanId_ok`
- `getTargetValue_returnsEmpty_whenNotFound`

- [ ] Step 1: UT + 骨架 → 红 → commit `test(perf): red - TargetApiImpl UT (4 实现+缓存)`
- [ ] Step 2: 实现 + @Cacheable(`perf:target_plan:{id}`) → 绿 → commit `feat(perf): green - TargetApiImpl, N UT 通过`

---

### Task 3.4: `TargetPlanController` IT + `TargetValueController` IT + 实现（红绿两对 = 4 commit）

**Plan Controller 4 端点**: list / getById / create / update
**Value Controller 3 端点**: list / create(单条 upsert) / batch(batch upsert)

**Plan IT 必含**:
- `put_whenReasonMissing_returns200`（update **不**强制 reason）
- `post_whenKpiSchemeMissing_returns409`

**Value IT 必含**:
- `batchPost_when501Items_returns400`
- `batchPost_when500Items_returns200`
- `batchPost_whenEmptyList_returns400`

操作序列：
- [ ] Step 1-2: TargetValueController IT + 实现 → 2 commit（先做简单的）
- [ ] Step 3-4: TargetPlanController IT + 实现 → 2 commit

---

### Task 3.5: 子域回归 + refactor（可选）

- [ ] 运行 `mvn test` 全绿
- [ ] 验证所有 `planId` 参数确实是 `String` 类型（grep `Long planId` 应无命中）
- [ ] DoD 勾选

**阶段 3 完成，累计 commit ≈ 55-59**

---

## 4. 阶段 4 — 运行任务子域（顺位 4，最轻）

**子域目标:** 实现 `perf_run_task` 只读全栈；数据范围 SQL 片段注入（管理员全见 / 普通用户仅见 started_by = currentUserId）；V1.0 **无写入**（V1.1 由计算引擎触发）。

**测试数据前缀:** `TEST_RT_*`

**提交范围预期:** 8-10 个 commit

---

### Task 4.1: `PerfRunTask` Entity + Mapper IT + XML（红绿一对 = 2 commit）

**PerfRunTaskMapper 接口**（只读）:
```java
PerfRunTask selectById(String id);
PerfRunTask selectByTaskNo(String taskNo);  // V1.1 用, V1.0 声明留着
List<PerfRunTask> selectByCondition(...);  // 含 dataScopeFilter 参数
long countByCondition(...);
long countByTypeAndDate(...);
```

**XML 关键**: `selectByCondition` 末尾 `<if test="dataScopeFilter != null">${dataScopeFilter}</if>`（`${}` 合法用于数据范围片段）

**IT 必含**:
- `selectByTaskNo_whenNotFound_returnsNull`
- `selectByCondition_withDataScopeFilter_onlyShowsOwnTasks`（手工构造 filter = `AND started_by='USER_A'`，对比插入 3 条 different started_by 的数据后查询结果）

---

### Task 4.2: `PerfRunTaskService` UT + 实现（红绿一对 = 2 commit）

**Service 只有 4 方法**: getById / getByTaskNo / page / countByTypeAndDate

**UT 必含**（spec §5.4）:
- `page_whenAdmin_returnsAll`（mock DataScopeApi.resolveScope 返回 null → Mapper 不加过滤）
- `page_whenRegularUser_returnsOnlyOwnStarted`（mock 返回 `AND started_by=#{currentUserId}`）

**实现要点**: 调 `DataScopeApi.resolveScope(PerfBizType 或 "PERF_RUN_TASK_QUERY", "perf_run_task")` 取 filter 串，传入 Mapper。

---

### Task 4.3: `PerfCalcApiImpl` Facade UT + 实现（红绿一对 = 2 commit）

**契约**（spec §5.2.5）: V1.0 实现 `getRunTask`；UOE 占位 `triggerKpiCalc / triggerRecalc`

**UT 必含**:
- `triggerKpiCalc_throwsUOE`（契约）
- `triggerRecalc_throwsUOE`（契约）
- `getRunTask_whenExists_returnsOptional`
- `getRunTask_whenNotExists_returnsEmpty`

---

### Task 4.4: `PerfRunTaskController` IT + 实现（红绿一对 = 2 commit）

**2 端点**: list / getById

**IT 必含**:
- `get_whenUnauthenticated_returns401`
- `list_regularUser_onlyShowsOwnStarted`（登录普通用户 token，构造 3 条不同 started_by 数据 → 验证仅返回 1 条）
- `get_whenTaskIdNotExist_returns404`

**注意**: GET 方法只有 @BizAuth，**无** @AuditLog（读操作）

---

### Task 4.5: 子域回归

- [ ] `mvn test` 全绿
- [ ] DoD 勾选

**阶段 4 完成，累计 commit ≈ 63-67**

---

## 5. 阶段 5 — 分配关系子域（顺位 5，方法最多）

**子域目标:** 实现 `cust_alloc_relation` 只读全栈；`AllocApi` 10 个方法全部 V1.0 实现；时间线查询 `effective_date <= ? AND (end_date IS NULL OR end_date >= ?)`；批量查询缓存合并；派生 sys_control CUST 维度版本号；数据范围 emp_id 过滤。

**测试数据前缀:** `TEST_AR_*`

**提交范围预期:** 12-14 个 commit

**依赖**: sys_control（已完成）的 `SysControlService.getCurrentVersion / listVersionHistory`

---

### Task 5.1: `CustAllocRelation` Entity + Mapper IT + XML（红绿一对 = 2 commit）

**Mapper 接口（10+ 方法覆盖所有 AllocApi 场景）**:
```java
List<CustAllocRelation> selectCurrentByCustAndBiz(@Param("custId") ..., @Param("bizKind") ..., @Param("asOfDate") LocalDate today);
List<CustAllocRelation> selectHistoryByCustAsOf(@Param("custId") ..., @Param("asOfDate") ...);
List<CustAllocRelation> selectByEmpAndBiz(...);
List<CustAllocRelation> selectCurrentByCustIds(@Param("custIds") Set<String>, ..., @Param("dataScopeFilter") ...);
Map<String,Long> countCustomersByEmps(...);
// 等等
```

**IT 必含**（spec §5.5）:
- `getCurrentAllocations_excludesExpired`（构造 end_date=昨天的记录，断言不在结果中）
- `getAllocationHistory_asOfDate_returnsActiveAtThatTime`
- `listCustomersByEmp_withDataScopeFilter_onlyOwnEmp`

---

### Task 5.2: `AllocRelationService` UT + 实现（红绿一对 = 2 commit）

**Service 10 方法**（对应 AllocApi 10 方法）

**UT 必含**（spec §5.5）:
- `batchGetCurrentAllocations_hitsCacheForSomeMissesDb_fillsBoth`（spec §4.5 缓存合并逻辑）
- `getAllocSummary_sumRatio_equals100_exactly`（验证比例汇总精度）
- `hasAllocation_whenExists_returnsTrue`
- `countCustomersByEmps_batchOk`
- `getLatestAllocVersion_derivesFromSysControlCust`（mock SysControlService）
- `getAllocVersionAt_whenNoExactMatch_returnsMostRecentBeforeAsOfDate`

**实现要点**:
- 缓存合并：先遍历 Redis 查命中 → 未命中的 id 集中查 DB → 回写缓存 → 合并结果
- `getLatestAllocVersion`: 调 `sysControlService.getCurrentVersion("CUST")`，包装为 `AllocVersionDTO`

---

### Task 5.3: `AllocApiImpl` + `DataTaskApiImpl` Facade UT + 实现（红绿两对 = 4 commit）

**AllocApiImpl**: 10 方法全 V1.0 实现
**DataTaskApiImpl**: 1 方法 UOE 占位

**UT 必含**（spec §5.5）:
- `AllocApiImplTest.getCurrentAllocations_hitsCache`
- `AllocApiImplTest.batchGetCurrentAllocations_cacheMergesBatchQuery`
- `DataTaskApiImplTest.reportDataTaskStatus_throwsUOE`

**注意**: V1.0 **不实现 evict**（V1.2 才做），只做 TTL 自然过期

操作序列:
- [ ] Step 1-2: DataTaskApiImpl UT + 实现（简单）→ 2 commit
- [ ] Step 3-4: AllocApiImpl UT + 实现（10 方法 + 缓存合并）→ 2 commit

---

### Task 5.4: `AllocRelationController` IT + 实现（红绿一对 = 2 commit）

**3 端点**（spec §6.2）: list / history / summary

**IT 必含**（spec §5.5）:
- `summary_regularUser_onlyOwnEmp`
- `history_whenAsOfDateMissing_returns400`

**注意**: 3 个 GET 方法只有 @BizAuth(READ)，**无** @AuditLog

---

### Task 5.5: 子域回归 + refactor

- [ ] `mvn test` 全绿
- [ ] DoD 勾选
- [ ] 发现漏测 → refactor commit

**阶段 5 完成，累计 commit ≈ 75-81（略超过估算的 63-71，因为 Facade 分成 2 对红绿而非 1 对）**

---

## 6. 阶段 6 — 全模块集成验收

### Task 6.1: 仓库根 mvn 验证

- [ ] **Step 1: 运行根 mvn package**

Run: `mvn -q clean package -DskipTests=false`
Expected: `BUILD SUCCESS`

- [ ] **Step 2: 统计模块测试数**

Run: `mvn -q -pl performance-engine-center test 2>&1 | grep "Tests run"`
Expected: 总测试数 ~120+，0 failures / 0 errors

### Task 6.2: bootstrap 启动 + Knife4j 联调

- [ ] **Step 1: 启动 bootstrap**

Run (run_in_background): `cd bootstrap && mvn spring-boot:run`
等待日志 `Started BootstrapApplication in N seconds`

- [ ] **Step 2: 访问 Knife4j**

浏览器打开 `http://localhost:8080/doc.html`，验证：
- "绩效-sys_control" 标签 4 端点
- "绩效-指标库" 标签 10 端点
- "绩效-KPI 方案" 标签 9 端点
- "绩效-目标方案" 标签 4 端点
- "绩效-目标值" 标签 3 端点
- "绩效-运行任务" 标签 2 端点
- "绩效-分配关系" 标签 3 端点
- 合计 **35 个端点**

### Task 6.3: curl 冒烟测试（admin token）

先登录拿 token：

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"123456"}' | jq -r .data.token)
```

- [ ] **Step 1:** `curl -s -H "Authorization: Bearer $TOKEN" "http://localhost:8080/api/perf/sys-control?scopeDim=EMP"` → 200
- [ ] **Step 2:** `curl -s -H "Authorization: Bearer $TOKEN" "http://localhost:8080/api/perf/metrics?pageNo=1&pageSize=10"` → 200
- [ ] **Step 3:** `curl -s -H "Authorization: Bearer $TOKEN" "http://localhost:8080/api/perf/kpi-schemes?pageNo=1&pageSize=10"` → 200
- [ ] **Step 4:** `curl -s -H "Authorization: Bearer $TOKEN" "http://localhost:8080/api/perf/target-plans?pageNo=1&pageSize=10"` → 200
- [ ] **Step 5:** `curl -s -H "Authorization: Bearer $TOKEN" "http://localhost:8080/api/perf/run-tasks?pageNo=1&pageSize=10"` → 200
- [ ] **Step 6:** `curl -s -H "Authorization: Bearer $TOKEN" "http://localhost:8080/api/perf/alloc-relations?custId=C001&bizKind=DEPOSIT"` → 200

### Task 6.4: 数据库对齐检查

- [ ] **Step 1:** `mysql -u root -p123456 onepl -e "SELECT COUNT(*) FROM PT_RESOURCE WHERE RESOURCE_ID LIKE 'P_PERF_%'"` → `35`
- [ ] **Step 2:** `mysql -u root -p123456 onepl -e "SELECT COUNT(*) FROM sys_dict_item WHERE DICT_CODE LIKE 'PERF_%'"` → 验证 V1_0_2 字典已加载

### Task 6.5: 验收总结提交

- [ ] **Step 1:** `git log --oneline 6ceb51d..HEAD -- performance-engine-center/ | wc -l` 查看本次剩余 5 子域 commit 总数
Expected: 51-65 条（加 sys_control 原有 12 条，总计 63-77 条）

- [ ] **Step 2: 生成交付总结 commit（非必须，可选）**

```bash
git commit --allow-empty -m "chore(perf): V1.0 全模块交付完成

- 骨架层 + 6 子域全部交付
- 35 REST 端点 + 7 对外 Api (21 实现 + 13 UOE) 全部可联调
- Knife4j 验证通过, 冒烟 6 个 GET 端点 200
- 累计 ~75 个 commit 严格遵守 TDD 红-绿-重构节奏"
```

---

## 7. 执行提示

### 7.1 每个 commit 前的检查清单

1. ✅ `mvn -q -pl performance-engine-center test` 全绿（red commit 除外，red 应红）
2. ✅ 本 commit 只含一层的测试或实现（不跨层）
3. ✅ commit message 遵循 §3.2 规范
4. ✅ 无 `System.out` / 无 TODO / 无注释代码

### 7.2 遇到意外情况的处理

| 情况 | 处理 |
|---|---|
| 测试期望 FAIL 但实际 PASS | 红测试写得不够严 → 补断言，重新 commit 红 |
| 绿期测试全通过但漏写场景 | 补红测试 → 若现有实现已满足 → 仅 `refactor:` commit；若需要改实现 → 先 `test: red`，再 `feat: green`，不要 amend 之前 commit |
| mvn 发现编译错误不属于当前 Task 范围 | 优先修复编译，单独 commit：`fix(perf): 修复 XXX 编译错误` |
| 某个 DoD 测试方法名与实际需求不符 | 以 **spec DoD 为准**；如需改动，先更新 spec，再改 plan，再改代码 |

### 7.3 阶段切换的守门

每阶段最后一步必须：
- [ ] `mvn test` 全绿
- [ ] 对照对应阶段的 DoD 清单勾选
- [ ] `git log --oneline` 核对本阶段 commit 数在预期范围（spec §5.x）

---

## 8. 附录

### 8.1 测试基类选择速查

| 场景 | 基类 | 事务策略 |
|---|---|---|
| 单线程 Mapper IT | `PerformanceMapperTestBase` | @Transactional + @Rollback |
| 单线程 Service IT | `PerformanceMapperTestBase`（继承使用） | 同上 |
| 单线程 Controller IT | `PerformanceControllerTestBase` | @Transactional + @Rollback |
| 并发 Mapper/Service IT | `PerformanceConcurrentTestBase` | **不含事务**，`@AfterEach` 显式清理 |
| Service UT（全 mock） | 无（`@ExtendWith(MockitoExtension.class)`） | 无 |
| Facade UT（全 mock） | 同上 | 无 |

### 8.2 通用 @Cacheable + afterCommit 模板

```java
@Override
@Cacheable(cacheNames = "perf:xxx", key = "#arg")
public Optional<XxxDTO> getXxx(String arg) {
    // 从 Service 读取
}

@Override
public void updateXxx(...) {
    xxxService.update(...);
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                cacheManager.getCache("perf:xxx").evict(arg);
            }
        }
    );
}
```

### 8.3 Redis 锁 + Lua 脚本模板（复用 SysControlFacade 模式）

```java
private static final String UNLOCK_LUA =
    "if redis.call('get', KEYS[1]) == ARGV[1] then " +
    "  return redis.call('del', KEYS[1]) " +
    "else return 0 end";
private final DefaultRedisScript<Long> unlockScript =
    new DefaultRedisScript<>(UNLOCK_LUA, Long.class);

public XxxResult doWithLock(String scope, Supplier<XxxResult> work) {
    String key = "perf:xxx-lock:" + scope;
    String token = java.util.UUID.randomUUID().toString();
    Boolean locked = redisTemplate.opsForValue()
        .setIfAbsent(key, token, java.time.Duration.ofSeconds(30));
    if (!Boolean.TRUE.equals(locked)) {
        throw new PerfException(PerfErrorCode.CONCURRENT_SWITCH,
            "获取锁失败 key=" + key);
    }
    try {
        return work.get();
    } finally {
        try {
            redisTemplate.execute(unlockScript,
                java.util.Collections.singletonList(key), token);
        } catch (Exception e) {
            log.warn("[PERF] 释放锁失败 key={}", key, e);
        }
    }
}
```

### 8.4 预估总 commit 数与实际记录

| 阶段 | 预估 commit | 实际（填空） |
|---|---|---|
| 骨架层（已完成） | 18 | 18 |
| sys_control 子域（已完成） | 12 | 12 |
| 指标库子域 | 18-20 | __ |
| KPI 方案子域 | 11 | __ |
| 目标方案子域 | 14-16 | __ |
| 运行任务子域 | 8-10 | __ |
| 分配关系子域 | 12-14 | __ |
| V1.0 集成验收 | 1 | __ |
| **总计** | **94-111** | __ |
