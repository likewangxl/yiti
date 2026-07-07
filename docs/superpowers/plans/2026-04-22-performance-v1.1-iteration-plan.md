# performance-engine-center V1.1 迭代计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use subagent-driven-development (recommended) or executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付 V1.1 核心业务能力——指标计算执行引擎（SQL + Groovy + 级联刷新）、KPI 定时计算、数据导入统一入口、外部数据上报闭环、历史回算。

**Architecture:**
V1.1 在 V1.0 配置骨架上补"运行时"。新增 `engine/` 执行引擎子包（SqlExecutor、GroovyExecutor、CascadeRefresher）、`importer/` 数据导入子包（PerfImportExecutor + TargetImportExecutor）、`job/` 定时任务子包。`engine` 以**策略模式**解耦 SQL/Groovy 两类计算逻辑，级联刷新采用**拓扑排序 + BFS** 处理指标依赖。所有写入走 `perf_run_task` 做幂等 + 状态机（PENDING / RUNNING / SUCCESS / FAILED），外部系统上报通过同一张表完成回调闭环。

**Tech Stack:**
- 新增依赖：`org.codehaus.groovy:groovy:4.0.21`（Groovy 表达式沙盒）
- 新增依赖：`com.alibaba:easyexcel:3.3.4`（Excel 批量导入）
- 新增模块依赖：无（V1.1 不依赖 workflow-center、customer-marketing-center）
- 保持：Spring Boot 3.2.3、MyBatis、MySQL、Redis、Testcontainers

**依赖文档（权威需求来源）:**
- `docs/modules/performance-engine-center/01-功能规格.md` §3.6（级联刷新）、§3.7（试运行）、§6（数据导入）、§8（KPI 计算）、§10.3（外部上报）
- `docs/modules/performance-engine-center/03-接口设计与报文.md` §A.5-A.8、§D、§F.1-F.2、§G
- `docs/modules/performance-engine-center/04-对外API契约.md` V1.1 标注项（含 `MetricApi.getEmpMetricValues` 等 13 个方法）
- `docs/modules/performance-engine-center/06-并发与事务策略.md`
- `docs/modules/performance-engine-center/07-审计要求.md` §1.5（METRIC_EXECUTE）、§1.6（KPI_CALC）

**前置条件（必须完成）:**
- ✅ `2026-04-22-performance-v1.0-rectification-plan.md` 阶段 A-E 已交付（错误码重排 + DDL 对齐）
- ✅ V1_0_3 / V1_0_4 Flyway 脚本已在本地与测试环境验证
- ✅ 宽表 `emp_index_result / org_index_result / cust_index_result / kpi_result` 的 DDL 存在（V1_0_0 已建表）
- ✅ 表 `perf_import_batch` DDL 存在（V1_0_0 已建表）

---

## 阶段概览

| 阶段 | 任务数 | 目标 | 预估 |
|---|---|---|---|
| P0 | 2 | 引入 pom 依赖 + V1.1 Flyway 基线 | 0.5 天 |
| P1 | 4 | 宽表 Entity/Mapper 补齐 | 1 天 |
| P2 | 6 | 指标计算引擎（SQL + Groovy + 级联） | 4 天 |
| P3 | 3 | 指标试运行 + 执行接口 | 1 天 |
| P4 | 4 | KPI 计算 + 定时任务 | 2 天 |
| P5 | 5 | 数据导入统一入口 + Excel 目标导入 | 2 天 |
| P6 | 3 | 外部数据上报闭环 | 1 天 |
| P7 | 2 | 历史回算 | 1 天 |
| P8 | 3 | UOE 占位替换 + 资源/字典补齐 | 1 天 |
| 合计 | 32 | | ~13.5 天 |

---

## 阶段 P0：基础设施

### Task P0.1：pom 依赖补齐

**Files:**
- Modify: `performance-engine-center/pom.xml`
- Test: `performance-engine-center/src/test/java/.../config/DependencyPresenceTest.java`

- [ ] **Step 1：写失败测试（断言类可加载）**

```java
@Test
void groovyShell_available() {
    Class<?> c = Class.forName("groovy.lang.GroovyShell");
    assertThat(c).isNotNull();
}

@Test
void easyExcel_available() {
    Class<?> c = Class.forName("com.alibaba.excel.EasyExcel");
    assertThat(c).isNotNull();
}
```

- [ ] **Step 2：测试失败（ClassNotFoundException）**

- [ ] **Step 3：在 pom.xml 追加依赖**

```xml
<dependency>
    <groupId>org.apache.groovy</groupId>
    <artifactId>groovy</artifactId>
    <version>4.0.21</version>
</dependency>
<dependency>
    <groupId>com.alibaba</groupId>
    <artifactId>easyexcel</artifactId>
    <version>3.3.4</version>
</dependency>
```

- [ ] **Step 4：测试通过**

- [ ] **Step 5：Commit**

```bash
git commit -m "chore(perf-v1.1): 引入 groovy 和 easyexcel 依赖"
```

---

### Task P0.2：V1.1 Flyway 基线 + 执行引擎配置开关

**Files:**
- Create: `performance-engine-center/src/main/resources/sql/V1_1_0__perf_v11_baseline.sql`
- Create: `performance-engine-center/src/main/java/.../config/PerfEngineProperties.java`
- Test: `performance-engine-center/src/test/java/.../config/PerfEnginePropertiesTest.java`

- [ ] **Step 1：写失败测试**

```java
@SpringBootTest
@TestPropertySource(properties = {
    "perf.engine.groovy-enabled=true",
    "perf.engine.sql-timeout-seconds=30",
    "perf.engine.cascade-max-depth=5"
})
class PerfEnginePropertiesTest {
    @Autowired PerfEngineProperties props;
    @Test
    void properties_loaded() {
        assertThat(props.isGroovyEnabled()).isTrue();
        assertThat(props.getSqlTimeoutSeconds()).isEqualTo(30);
        assertThat(props.getCascadeMaxDepth()).isEqualTo(5);
    }
}
```

- [ ] **Step 2：失败**

- [ ] **Step 3：建 Properties + V1_1_0 脚本**

```java
@ConfigurationProperties(prefix = "perf.engine")
@Data
public class PerfEngineProperties {
    private boolean groovyEnabled = true;
    private int sqlTimeoutSeconds = 30;
    private int cascadeMaxDepth = 5;
    private int importBatchSize = 500;
}
```

```sql
-- V1_1_0__perf_v11_baseline.sql
-- 预留给 V1.1 所需的其他 DDL 变更（本 Baseline 仅占版本号，内容可留为 SELECT 1;）
SELECT 1;
```

（主要目的：让 Flyway 提前占用 V1.1 版本号；后续 P1/P5 任务以 `V1_1_1` 等叠加）

- [ ] **Step 4：测试通过**

- [ ] **Step 5：Commit**

```bash
git commit -m "feat(perf-v1.1): PerfEngineProperties 配置 + V1_1_0 Flyway 基线"
```

---

## 阶段 P1：宽表 Entity/Mapper 补齐

### Task P1.1：EmpIndexResult Entity + Mapper

**Files:**
- Create: `performance-engine-center/src/main/java/.../entity/EmpIndexResult.java`
- Create: `performance-engine-center/src/main/java/.../mapper/EmpIndexResultMapper.java`
- Create: `performance-engine-center/src/main/resources/mapper/performance/EmpIndexResultMapper.xml`
- Test: `performance-engine-center/src/test/java/.../mapper/EmpIndexResultMapperIT.java`

> DDL 已在 `V1_0_0__performance_ddl.sql` 预建，字段参考 05 §3.1。

- [ ] **Step 1：写失败测试**

```java
@Test
void insert_and_query_byKeyAndDataDate() {
    EmpIndexResult r = new EmpIndexResult();
    r.setId(IdUtil.nano());
    r.setEmpId("E001"); r.setDataDate(LocalDate.of(2026,4,1));
    r.setBaseDim("EMP"); r.setMetricCode("M_EMP_DEP_AVG_BAL");
    r.setVal1(new BigDecimal("123456.78")); // 对应 val_slot=1
    r.setVersion("v20260401");
    mapper.insert(r);

    List<EmpIndexResult> list = mapper.selectByEmpAndDate("E001", LocalDate.of(2026,4,1), "v20260401");
    assertThat(list).hasSize(1);
    assertThat(list.get(0).getVal1()).isEqualByComparingTo("123456.78");
}
```

- [ ] **Step 2：失败**

- [ ] **Step 3：建 Entity（200 个 val_slot 字段用 `BigDecimal val1 ... val200`）+ Mapper**

为避免手写 200 字段，采用以下策略：
- Entity 中用 `Map<Integer, BigDecimal> slots` 承载
- Mapper XML 使用动态 SQL 根据传入的 slot 列表生成 `val1, val5, val10 = #{...}`
- resultMap 使用 `<association>` + 手写 TypeHandler 将结果集转回 Map

（此处为 V1.1 核心难点，**TDD 第 1 步测试只覆盖最简写法（全量 200 列）**，重构在 Task P1.4）

- [ ] **Step 4：通过**

- [ ] **Step 5：Commit**

```bash
git commit -m "feat(perf-v1.1): EmpIndexResult Entity + Mapper（宽表 200 slot）"
```

---

### Task P1.2：OrgIndexResult + CustIndexResult + KpiResult（并行三子任务）

**Files（每个子任务一组）:**

- `OrgIndexResult*` / `CustIndexResult*` / `KpiResult*`

- [ ] **Step 1-5：重复 P1.1 TDD 模式，每张表独立 commit**

三个子任务形态完全一致，除了字段主键不同（`org_id` / `cust_id` / `emp_id + scheme_id`）。

- [ ] **Step 6：整体回归**

```bash
mvn -Dtest="*IndexResult*IT" test
```

- [ ] **Step 7：Commit（若子任务独立 commit 则此步为空提交收尾）**

---

### Task P1.3：PerfImportBatch Entity + Mapper

**Files:**
- Create: `.../entity/PerfImportBatch.java`
- Create: `.../mapper/PerfImportBatchMapper.java`
- Create: `mapper/performance/PerfImportBatchMapper.xml`
- Test: `.../mapper/PerfImportBatchMapperIT.java`

> 字段参考 05 §2.6：`id / import_type / file_name / file_size / status / total_rows / success_rows / fail_rows / operator_id / start_at / end_at / error_summary`

- [ ] **Step 1-5：标准 CRUD + 状态机 TDD**

```java
@Test
void insert_and_updateStatus_transition() {
    PerfImportBatch b = ...;
    mapper.insert(b);
    mapper.updateStatus(b.getId(), "RUNNING", null);
    mapper.updateStatus(b.getId(), "SUCCESS", "{}");
    assertThat(mapper.selectById(b.getId()).getStatus()).isEqualTo("SUCCESS");
}
```

- [ ] **Step 6：Commit**

```bash
git commit -m "feat(perf-v1.1): PerfImportBatch Entity + Mapper"
```

---

### Task P1.4：宽表 slot TypeHandler 统一抽象

**Files:**
- Create: `.../mapper/typehandler/SlotMapTypeHandler.java`
- Modify: 三张宽表 Mapper XML 使用统一 handler
- Test: `.../typehandler/SlotMapTypeHandlerTest.java`

- [ ] **Step 1：写失败测试**

```java
@Test
void handler_reads200Columns_intoMap() {
    ResultSet rs = mockResultSetWithVal1To200();
    Map<Integer, BigDecimal> slots = handler.getNullableResult(rs, "slots");
    assertThat(slots.get(1)).isEqualByComparingTo("1.00");
    assertThat(slots.get(200)).isEqualByComparingTo("200.00");
}
```

- [ ] **Step 2-5：实现 + 测试通过 + commit**

```bash
git commit -m "feat(perf-v1.1): 宽表 200 slot 统一 TypeHandler"
```

---

## 阶段 P2：指标计算引擎（V1.1 核心）

### Task P2.1：SqlExecutor 基础

**Files:**
- Create: `.../service/engine/SqlExecutor.java`
- Create: `.../service/engine/SqlExecutorImpl.java`
- Test: `.../service/engine/SqlExecutorImplTest.java`

**接口设计：**
```java
public interface SqlExecutor {
    /**
     * 执行指标 SQL，返回 (baseKey -> 数值) 的映射。
     * baseKey 含义：EMP -> empId；ORG -> orgId；CUST -> custId。
     */
    Map<String, BigDecimal> execute(String sql, Map<String, Object> params, Duration timeout);
}
```

- [ ] **Step 1：写失败测试**

```java
@Test
void execute_returnsBaseKeyToValueMap() {
    // 假设数据源有临时表 t_emp_bal(emp_id, bal)
    Map<String, BigDecimal> result = executor.execute(
        "SELECT emp_id AS base_key, SUM(bal) AS metric_value FROM t_emp_bal WHERE data_date=#{d} GROUP BY emp_id",
        Map.of("d", LocalDate.of(2026,4,1)),
        Duration.ofSeconds(30));
    assertThat(result).containsEntry("E001", new BigDecimal("100.00"));
}
```

- [ ] **Step 2：失败**

- [ ] **Step 3：实现**

```java
@Service
public class SqlExecutorImpl implements SqlExecutor {
    private final NamedParameterJdbcTemplate jdbc;
    public Map<String, BigDecimal> execute(String sql, Map<String, Object> params, Duration timeout) {
        // 强制要求结果列名为 base_key + metric_value
        jdbc.getJdbcTemplate().setQueryTimeout((int) timeout.getSeconds());
        try {
            return jdbc.query(sql, params, rs -> {
                Map<String, BigDecimal> m = new LinkedHashMap<>();
                while (rs.next()) {
                    m.put(rs.getString("base_key"), rs.getBigDecimal("metric_value"));
                }
                return m;
            });
        } catch (DataAccessException e) {
            throw new PerfException(PerfErrorCode.METRIC_EXEC_FAILED, e.getMessage());
        }
    }
}
```

- [ ] **Step 4：通过**

- [ ] **Step 5：Commit**

```bash
git commit -m "feat(perf-v1.1): SqlExecutor 实现指标 SQL 执行"
```

---

### Task P2.2：SqlExecutor 防注入与关键字黑名单

**Files:**
- Modify: `.../service/engine/SqlExecutorImpl.java`
- Create: `.../service/engine/SqlValidator.java`
- Test: `.../service/engine/SqlValidatorTest.java`

- [ ] **Step 1：写失败测试**

```java
@ParameterizedTest
@ValueSource(strings = {
    "DELETE FROM t", "UPDATE t SET a=1", "DROP TABLE t", "TRUNCATE t",
    "INSERT INTO t VALUES (1)", "CREATE TABLE x(a int)", "ALTER TABLE t ADD b int"
})
void validate_rejectsMutationSql(String sql) {
    assertThatThrownBy(() -> validator.validate(sql))
        .isInstanceOf(PerfException.class)
        .hasMessageContaining("PERF-40014");
}

@Test
void validate_allowsSelectWithCte() {
    validator.validate("WITH cte AS (SELECT 1) SELECT * FROM cte");
}
```

- [ ] **Step 2：失败**

- [ ] **Step 3：实现**

```java
@Component
public class SqlValidator {
    private static final Pattern MUTATION = Pattern.compile(
        "(?i)^\\s*(INSERT|UPDATE|DELETE|DROP|TRUNCATE|ALTER|CREATE|RENAME|GRANT|REVOKE|CALL)\\b");
    public void validate(String sql) {
        if (MUTATION.matcher(sql).find()) {
            throw new PerfException(PerfErrorCode.METRIC_SQL_FORBIDDEN);
        }
    }
}
```

- [ ] **Step 4：通过**

- [ ] **Step 5：Commit**

```bash
git commit -m "feat(perf-v1.1): SqlExecutor 增加 DML/DDL 黑名单校验"
```

---

### Task P2.3：GroovyExecutor

**Files:**
- Create: `.../service/engine/GroovyExecutor.java`
- Create: `.../service/engine/GroovyExecutorImpl.java`
- Test: `.../service/engine/GroovyExecutorImplTest.java`

**沙盒策略：** 使用 `org.codehaus.groovy.control.customizers.SecureASTCustomizer`，禁用：
- `java.lang.System` 全家
- `java.io.*`
- `Thread.start`
- `Runtime.exec`

- [ ] **Step 1：写失败测试**

```java
@Test
void execute_simpleArithmetic() {
    BigDecimal r = executor.execute("a + b * 2",
        Map.of("a", new BigDecimal("1"), "b", new BigDecimal("3")));
    assertThat(r).isEqualByComparingTo("7");
}

@Test
void execute_rejectsSystemCall() {
    assertThatThrownBy(() -> executor.execute("System.exit(0)", Map.of()))
        .isInstanceOf(PerfException.class)
        .hasMessageContaining("PERF-40015");
}

@Test
void execute_timeoutExceeded_throws() {
    // Thread.sleep 应被禁用，也可通过 while(true) 制造超时
    assertThatThrownBy(() -> executor.execute("def s=0; for (i in 1..Long.MAX_VALUE) s++; s", Map.of()))
        .isInstanceOf(PerfException.class);
}
```

- [ ] **Step 2-4：实现 + 通过**

```java
@Service
public class GroovyExecutorImpl implements GroovyExecutor {
    private final CompilerConfiguration config;
    public GroovyExecutorImpl() {
        this.config = new CompilerConfiguration();
        SecureASTCustomizer sec = new SecureASTCustomizer();
        sec.setReceiversBlackList(List.of("java.lang.System","java.lang.Thread","java.lang.Runtime"));
        sec.setPackageAllowed(false);
        sec.setImportsBlacklist(List.of("java.io.*","java.net.*","java.nio.*"));
        this.config.addCompilationCustomizers(sec);
    }

    public BigDecimal execute(String expr, Map<String, Object> vars) {
        GroovyShell sh = new GroovyShell(new Binding(vars), config);
        Object r = sh.evaluate(expr);
        return new BigDecimal(r.toString());
    }
}
```

超时：通过 `Future + ExecutorService` 在 `PerfEngineProperties.sqlTimeoutSeconds` 后强制 cancel。

- [ ] **Step 5：Commit**

```bash
git commit -m "feat(perf-v1.1): GroovyExecutor 含 SecureAST 沙盒 + 超时"
```

---

### Task P2.4：MetricCalcService 单指标执行

**Files:**
- Create: `.../service/MetricCalcService.java`
- Test: `.../service/MetricCalcServiceIT.java`

**职责：**
1. 读 `perf_metric_def` 拿到 SQL / Groovy 表达式 + val_slot
2. 根据 `calc_logic_type` 路由到 SqlExecutor / GroovyExecutor
3. 计算结果写入对应宽表的 `val{slot}` 列
4. 通过 `perf_run_task` 记录状态

- [ ] **Step 1：写失败测试（SQL 路径）**

```java
@Test
void calcEmpMetric_viaSql_writesToValSlotColumn() {
    // 准备：指标 M_EMP_B1 的 calc_logic_type=SQL, val_slot=5
    // 执行
    String taskId = service.calcMetric("M_EMP_B1", LocalDate.of(2026,4,1), "v20260401");
    // 验证
    PerfRunTask task = runTaskMapper.selectById(taskId);
    assertThat(task.getStatus()).isEqualTo("SUCCESS");
    List<EmpIndexResult> rs = empMapper.selectByEmpAndDate("E001", LocalDate.of(2026,4,1), "v20260401");
    assertThat(rs.get(0).getVal5()).isEqualByComparingTo("100.00");
}
```

- [ ] **Step 2：失败**

- [ ] **Step 3-4：实现（核心逻辑 ~150 行）+ 通过**

- [ ] **Step 5：Commit**

```bash
git commit -m "feat(perf-v1.1): MetricCalcService 单指标计算 + run_task 状态机"
```

---

### Task P2.5：CascadeRefresher 级联刷新

**Files:**
- Create: `.../service/CascadeRefresher.java`
- Create: `.../service/DependencyGraphBuilder.java`
- Test: `.../service/CascadeRefresherTest.java` + `.../service/DependencyGraphBuilderTest.java`

**算法：**
1. 从 `perf_metric_ref` 表构建有向图（A → B 表示 B 依赖 A）
2. 对指定起点做 BFS，按拓扑序推进
3. 遇到循环依赖抛 `PerfException(PERF-40003)`

- [ ] **Step 1：写 DependencyGraphBuilder 单元测试**

```java
@Test
void graph_detectsCycle() {
    List<PerfMetricRef> refs = List.of(
        ref("A","B"), ref("B","C"), ref("C","A"));
    assertThatThrownBy(() -> builder.build(refs))
        .isInstanceOf(PerfException.class)
        .hasMessageContaining("PERF-40003");
}

@Test
void graph_topoSort_correct() {
    List<PerfMetricRef> refs = List.of(ref("A","B"), ref("B","C"));
    List<String> order = builder.build(refs).topoFrom("A");
    assertThat(order).containsExactly("A","B","C");
}
```

- [ ] **Step 2-5：实现 + 通过 + commit**

```bash
git commit -m "feat(perf-v1.1): DependencyGraphBuilder 拓扑排序 + 环检测"
```

- [ ] **Step 6：CascadeRefresher IT**

```java
@Test
void refresh_propagates_changesDownstream() {
    // A 刚计算完成，触发 refresh
    String rootTaskId = refresher.refresh("M_EMP_A", date, version);
    // 验证 B、C 按顺序被触发
    List<PerfRunTask> tasks = runTaskMapper.selectByParent(rootTaskId);
    assertThat(tasks).extracting(PerfRunTask::getMetricCode)
        .containsExactly("M_EMP_B","M_EMP_C");
}
```

- [ ] **Step 7：Commit**

```bash
git commit -m "feat(perf-v1.1): CascadeRefresher 级联刷新"
```

---

### Task P2.6：MetricApi V1.1 查询方法实现

**Files:**
- Modify: `.../facade/MetricApiImpl.java`（替换 4 个 UOE）
- Test: `.../facade/MetricApiImplV11Test.java`

> 替换方法：`getEmpMetricValues` / `getOrgMetricValues` / `getCustMetricValues` / `getMetricTrend`

- [ ] **Step 1：针对每个方法写失败测试（4 个）**

```java
@Test
void getEmpMetricValues_returnsSlotValuesByMetricCode() {
    // 预置：E001 在 2026-04-01 的 M_EMP_A (val_slot=5) = 100.00
    List<EmpMetricValueDTO> list = api.getEmpMetricValues(
        List.of("E001"), List.of("M_EMP_A"), LocalDate.of(2026,4,1), "v20260401");
    assertThat(list).hasSize(1);
    assertThat(list.get(0).getValue()).isEqualByComparingTo("100.00");
}
```

- [ ] **Step 2-4：实现 + 通过（每方法独立 commit）**

- [ ] **Step 5：Commit**

```bash
git commit -m "feat(perf-v1.1): MetricApi 4 个查询方法实现（替换 UOE）"
```

---

## 阶段 P3：指标试运行与执行接口

### Task P3.1：MetricTrialService + /api/perf/metric-def/trial-run

**Files:**
- Create: `.../service/MetricTrialService.java`
- Create: `.../controller/MetricTrialController.java`
- Create: `.../controller/dto/MetricTrialReqDTO.java`
- Create: `.../controller/dto/MetricTrialRespDTO.java`
- Test: `.../controller/MetricTrialControllerIT.java`

> 03 §A.5：`POST /api/perf/metric-def/trial-run`，仅执行不落库，返回样本结果。

- [ ] **Step 1：写 IT 失败测试**

```java
@Test
void trialRun_returnsSampleResults_noSideEffect() {
    MetricTrialReqDTO req = new MetricTrialReqDTO();
    req.setCalcLogicType("SQL");
    req.setSql("SELECT emp_id AS base_key, 1 AS metric_value FROM dual");
    req.setSampleSize(10);
    String body = post("/api/perf/metric-def/trial-run", req);
    assertThat(body).contains("\"sampleSize\":1");
    // 未写入任何宽表
    assertThat(empMapper.count()).isEqualTo(0);
}
```

- [ ] **Step 2-5：实现 + 通过 + commit**

```bash
git commit -m "feat(perf-v1.1): 指标试运行接口 /trial-run"
```

---

### Task P3.2：MetricApi.execute + /api/perf/metric-def/{code}/execute

**Files:**
- Modify: `.../controller/MetricDefController.java`（新增端点）
- Modify: `.../facade/MetricApiImpl.java`

- [ ] **Step 1-5：TDD**

接口入参：`metricCode, dataDate, version, cascade(boolean)`；
实现：调用 MetricCalcService；若 cascade=true 调用 CascadeRefresher。

- [ ] **Step 5：Commit**

```bash
git commit -m "feat(perf-v1.1): 指标执行接口 /execute（含 cascade 参数）"
```

---

### Task P3.3：PerfCalcApi.triggerMetricCalc 实现

**Files:**
- Modify: `.../facade/PerfCalcApiImpl.java`（替换 UOE）
- Test: `.../facade/PerfCalcApiImplTriggerTest.java`

- [ ] **Step 1-5：TDD 替换 UOE**

```bash
git commit -m "feat(perf-v1.1): PerfCalcApi.triggerMetricCalc 实现"
```

---

## 阶段 P4：KPI 计算

### Task P4.1：KpiFormulaService 公式解析

**Files:**
- Create: `.../service/KpiFormulaService.java`
- Test: `.../service/KpiFormulaServiceTest.java`

**职责：** 将 KPI 方案的公式字符串（含 `${metric_code}` 变量）编译为可执行 Groovy，替换变量后计算。

- [ ] **Step 1：失败测试**

```java
@Test
void eval_withMetricCodeVariables() {
    String formula = "${M_EMP_A} * 0.3 + ${M_EMP_B} * 0.7";
    BigDecimal r = service.eval(formula, Map.of(
        "M_EMP_A", new BigDecimal("100"),
        "M_EMP_B", new BigDecimal("200")));
    assertThat(r).isEqualByComparingTo("170.00");
}
```

- [ ] **Step 2-5：实现 + 通过 + commit**

```bash
git commit -m "feat(perf-v1.1): KpiFormulaService 公式解析与计算"
```

---

### Task P4.2：KpiCalcService 单员工 KPI 计算

**Files:**
- Create: `.../service/KpiCalcService.java`
- Test: `.../service/KpiCalcServiceIT.java`

**流程：**
1. 读 `perf_kpi_scheme + perf_kpi_item` 拿到 item 清单（metric_code + weight + formula）
2. 对每个员工，从 EmpIndexResult 拿指标值
3. 调 KpiFormulaService 计算得分
4. 写入 `kpi_result` 表（emp_id + scheme_id + data_date + version + total_score + item_details JSON）

- [ ] **Step 1-5：TDD**

```bash
git commit -m "feat(perf-v1.1): KpiCalcService 单员工 KPI 计算"
```

---

### Task P4.3：KpiApi V1.1 方法实现

**Files:**
- Modify: `.../facade/KpiApiImpl.java`（替换 3 个 UOE：`getCurrentKpiTotal` / `getKpiHistory` / `batchGetKpiTotals` 注：若 04 契约已删除 `batchGetKpiTotals`，按决策处理）
- Test: `.../facade/KpiApiImplV11Test.java`

- [ ] **Step 1-5：TDD**

```bash
git commit -m "feat(perf-v1.1): KpiApi 查询方法实现"
```

---

### Task P4.4：DailyKpiCalcJob 定时任务

**Files:**
- Create: `.../job/DailyKpiCalcJob.java`
- Modify: `bootstrap/src/main/resources/application.yml`（增加 job.cron 配置）
- Test: `.../job/DailyKpiCalcJobTest.java`

**调度：** 使用 Spring `@Scheduled` + `@ConditionalOnProperty`，cron 默认 `0 30 1 * * ?`（每天 01:30）。

- [ ] **Step 1：写失败测试（注入 mock clock + mock KpiCalcService）**

```java
@Test
void run_callsKpiCalcForEachActiveScheme() {
    job.run();
    verify(kpiCalcService, times(3)).calcScheme(any(), any(), any()); // 3 个活跃方案
}
```

- [ ] **Step 2-5：实现 + 通过 + commit**

```bash
git commit -m "feat(perf-v1.1): DailyKpiCalcJob 每日 KPI 计算定时任务"
```

---

## 阶段 P5：数据导入统一入口

### Task P5.1：PerfImportService + 3 种 importType 策略骨架

**Files:**
- Create: `.../service/importer/PerfImportService.java`
- Create: `.../service/importer/ImportStrategy.java`（接口）
- Create: `.../service/importer/impl/BaseDataImportStrategy.java`
- Create: `.../service/importer/impl/TargetImportStrategy.java`
- Create: `.../service/importer/impl/AllocRelationImportStrategy.java`
- Test: `.../service/importer/PerfImportServiceTest.java`

**策略分发：**
```java
public interface ImportStrategy {
    String importType();  // BASE_DATA / TARGET / ALLOC
    ImportResult execute(PerfImportBatch batch, MultipartFile file);
}
```

- [ ] **Step 1：写失败测试（策略分发正确）**

```java
@Test
void importService_routesToStrategy_byImportType() {
    when(targetStrategy.importType()).thenReturn("TARGET");
    ImportResult r = service.importData("TARGET", mockFile());
    verify(targetStrategy).execute(any(), eq(mockFile()));
}
```

- [ ] **Step 2-5：实现 + 通过 + commit**

```bash
git commit -m "feat(perf-v1.1): PerfImportService 统一入口 + 3 种策略骨架"
```

---

### Task P5.2：TargetImportStrategy Excel 解析（easyexcel）

**Files:**
- Modify: `.../service/importer/impl/TargetImportStrategy.java`
- Create: `.../service/importer/model/TargetImportRow.java`
- Create: `src/test/resources/import-samples/targets_ok.xlsx`
- Create: `src/test/resources/import-samples/targets_invalid.xlsx`
- Test: `.../service/importer/impl/TargetImportStrategyIT.java`

- [ ] **Step 1：写失败测试**

```java
@Test
void execute_parsesExcel_andUpsertTargetValues() {
    MultipartFile file = loadSample("targets_ok.xlsx"); // 10 行有效
    ImportResult r = strategy.execute(batch, file);
    assertThat(r.getTotalRows()).isEqualTo(10);
    assertThat(r.getSuccessRows()).isEqualTo(10);
    assertThat(targetValueMapper.count()).isEqualTo(10);
}

@Test
void execute_invalidRow_recordedInErrorSummary() {
    MultipartFile file = loadSample("targets_invalid.xlsx"); // 5 行有效 + 3 行无效
    ImportResult r = strategy.execute(batch, file);
    assertThat(r.getSuccessRows()).isEqualTo(5);
    assertThat(r.getFailRows()).isEqualTo(3);
    assertThat(r.getErrorSummary()).contains("第 3 行", "第 7 行");
}
```

- [ ] **Step 2-5：实现**

```java
public class TargetImportStrategy implements ImportStrategy {
    public ImportResult execute(PerfImportBatch batch, MultipartFile file) {
        List<TargetImportRow> rows = EasyExcel.read(file.getInputStream()).head(TargetImportRow.class).sheet().doReadSync();
        List<String> errors = new ArrayList<>();
        int success = 0;
        for (int i = 0; i < rows.size(); i++) {
            try {
                validate(rows.get(i));
                targetValueService.upsert(toCmd(rows.get(i), batch));
                success++;
            } catch (Exception e) {
                errors.add("第 " + (i+2) + " 行: " + e.getMessage());
            }
        }
        return new ImportResult(rows.size(), success, rows.size() - success, String.join("; ", errors));
    }
}
```

- [ ] **Step 5：Commit**

```bash
git commit -m "feat(perf-v1.1): TargetImportStrategy Excel 批量导入"
```

---

### Task P5.3：BaseDataImportStrategy

**Files:**
- Modify: `.../service/importer/impl/BaseDataImportStrategy.java`
- Test: `.../service/importer/impl/BaseDataImportStrategyIT.java`

- [ ] **Step 1-5：TDD（解析到原始表 `t_*_base` 或宽表的 val_slot）**

```bash
git commit -m "feat(perf-v1.1): BaseDataImportStrategy 基础数据导入"
```

---

### Task P5.4：AllocRelationImportStrategy

**Files:**
- Modify: `.../service/importer/impl/AllocRelationImportStrategy.java`
- Test: `.../service/importer/impl/AllocRelationImportStrategyIT.java`

- [ ] **Step 1-5：TDD**

```bash
git commit -m "feat(perf-v1.1): AllocRelationImportStrategy 分配关系导入"
```

---

### Task P5.5：导入接口暴露 /api/perf/import（5 个端点）

**Files:**
- Create: `.../controller/PerfImportController.java`
- Test: `.../controller/PerfImportControllerIT.java`

> 03 §D：`POST /upload` / `GET /batch/{id}` / `GET /batch/{id}/errors` / `POST /batch/{id}/retry` / `DELETE /batch/{id}`

- [ ] **Step 1：写 5 个端点 IT**

- [ ] **Step 2-5：实现 + 通过 + commit**

```bash
git commit -m "feat(perf-v1.1): 数据导入 5 个 REST 端点"
```

---

## 阶段 P6：外部数据上报闭环

### Task P6.1：DataTaskController + /api/data-task/status

**Files:**
- Create: `.../controller/DataTaskController.java`
- Create: `.../controller/dto/DataTaskStatusReqDTO.java`
- Test: `.../controller/DataTaskControllerIT.java`

> 03 §G.1：外部系统将 T 日数据导入完成后，调此接口上报。

- [ ] **Step 1：失败 IT**

```java
@Test
void report_success_triggersCalcPipeline() {
    DataTaskStatusReqDTO req = new DataTaskStatusReqDTO();
    req.setDataDate(LocalDate.of(2026,4,1));
    req.setStatus("SUCCESS");
    req.setSource("CORE_BANK");
    post("/api/data-task/status", req).andExpect(status().isOk());
    // 验证：对应日期的 kpi 计算 task 已创建（PENDING）
    List<PerfRunTask> tasks = runTaskMapper.selectByDate(LocalDate.of(2026,4,1));
    assertThat(tasks).extracting(PerfRunTask::getTaskType).contains("KPI_CALC");
}
```

- [ ] **Step 2-5：实现 + 通过 + commit**

```bash
git commit -m "feat(perf-v1.1): 外部数据上报接口 /api/data-task/status"
```

---

### Task P6.2：DataTaskApi 实现（替换 UOE）

**Files:**
- Modify: `.../facade/DataTaskApiImpl.java`
- Test: `.../facade/DataTaskApiImplTest.java`

- [ ] **Step 1-5：TDD**

```bash
git commit -m "feat(perf-v1.1): DataTaskApi.reportDataTaskStatus 实现"
```

---

### Task P6.3：外部上报幂等（以 source + dataDate 为幂等键）

**Files:**
- Modify: `.../service/DataTaskService.java`
- Test: `.../service/DataTaskServiceIdempotentIT.java`

- [ ] **Step 1：失败测试（同键重复上报只触发一次计算）**

```java
@Test
void reportSameKey_twice_onlyOneCalcTriggered() {
    service.report(req);
    service.report(req);
    assertThat(runTaskMapper.countByDate(req.getDataDate(), "KPI_CALC")).isEqualTo(1);
}
```

- [ ] **Step 2-5：实现（Redis SETNX 或 DB unique key）+ 通过 + commit**

```bash
git commit -m "feat(perf-v1.1): 外部上报接口幂等"
```

---

## 阶段 P7：历史回算

### Task P7.1：HistoryRecalcService

**Files:**
- Create: `.../service/HistoryRecalcService.java`
- Test: `.../service/HistoryRecalcServiceIT.java`

**输入：** `dataDateRange (start, end)` + `metricCodes[]` + `version`
**输出：** `taskId`（parent task）
**流程：** 对日期范围内每天，对每个指标码，依次调 MetricCalcService；全部完成后触发 KpiCalcService。

- [ ] **Step 1：失败 IT**

```java
@Test
void recalc_dateRange_runsForEachDate() {
    String parentTaskId = service.recalc(
        LocalDate.of(2026,3,1), LocalDate.of(2026,3,3),
        List.of("M_EMP_A"), "v20260301");
    List<PerfRunTask> children = runTaskMapper.selectByParent(parentTaskId);
    assertThat(children).hasSize(3); // 3 个日期
}
```

- [ ] **Step 2-5：实现 + 通过 + commit**

```bash
git commit -m "feat(perf-v1.1): HistoryRecalcService 按日期范围回算"
```

---

### Task P7.2：/api/perf/recalc 端点 + PerfCalcApi.triggerRecalc

**Files:**
- Modify: `.../controller/PerfCalcController.java`（新增端点）
- Modify: `.../facade/PerfCalcApiImpl.java`（替换 UOE）
- Test: `.../controller/PerfCalcControllerRecalcIT.java`

- [ ] **Step 1-5：TDD**

```bash
git commit -m "feat(perf-v1.1): 历史回算接口 /api/perf/recalc + PerfCalcApi"
```

---

## 阶段 P8：资源/字典/文档收尾

### Task P8.1：V1_1_x 资源启用

**Files:**
- Create: `performance-engine-center/src/main/resources/sql/V1_1_1__perf_v11_resources.sql`
- Test: `.../migration/V1_1_1FlywayIT.java`

- [ ] **Step 1：失败测试（资源状态变为 ACTIVE）**

```java
@Test
void v11Resources_enabled() {
    assertThat(jdbc.queryForObject(
        "SELECT status FROM pt_resource WHERE id='P_PERF_METRIC_EXECUTE'",
        String.class)).isEqualTo("ACTIVE");
}
```

- [ ] **Step 2-5：脚本 + 通过 + commit**

```sql
-- V1_1_1__perf_v11_resources.sql
UPDATE pt_resource SET status='ACTIVE' WHERE id IN (
  'P_PERF_METRIC_EXECUTE','P_PERF_METRIC_TRIAL_RUN',
  'P_PERF_IMPORT_UPLOAD','P_PERF_KPI_TRIGGER','P_PERF_KPI_RECALC',
  'P_PERF_DATA_TASK_STATUS'
);
```

```bash
git commit -m "feat(perf-v1.1): V1.1 PT_RESOURCE 启用"
```

---

### Task P8.2：UOE 占位全量清单验证

**Files:**
- Test: `.../facade/NoUnsupportedOperationArchTest.java`

- [ ] **Step 1：写架构测试（V1.1 结束后，除 V1.2 占位外，任何 API 方法不得抛 UOE）**

```java
@Test
void noV11ApiMethod_throwsUnsupportedOperation() {
    // 反射检查 7 个 *Api 的实现类方法体（通过 AOP 代理 + Invoke + fail fast 检查）
    List<Method> v11 = getV11AnnotatedMethods();
    for (Method m : v11) {
        try {
            invoke(m);
        } catch (UnsupportedOperationException e) {
            fail(m.getName() + " 仍抛 UOE");
        } catch (Exception ignored) { /* 其他异常（如参数校验）OK */ }
    }
}
```

- [ ] **Step 2-3：若失败，定位残留的 UOE（应该只剩 V1.2 的 4 个）**

- [ ] **Step 4：通过**

- [ ] **Step 5：Commit**

```bash
git commit -m "test(perf-v1.1): 架构守护 V1.1 范围内不得有 UOE 残留"
```

---

### Task P8.3：文档更新

**Files:**
- Modify: `performance-engine-center/CLAUDE.md`（V1.1 → 已交付；V1.2 → 规划中）
- Modify: `CLAUDE.md`（根）：performance-engine-center 状态从"V1.0 骨架"改为"V1.1 已交付"
- Modify: `docs/modules/performance-engine-center/04-对外API契约.md`：去除 V1.1 UOE 标注

- [ ] **Step 1-2：修改**

- [ ] **Step 3：Commit**

```bash
git commit -m "docs(perf-v1.1): V1.1 交付后的文档同步"
```

---

## 验收清单

V1.1 交付后必须全部满足：

- [ ] `mvn clean test` 在 `performance-engine-center` 全绿
- [ ] `mvn clean test` 在 `bootstrap`（集成测试）全绿
- [ ] Flyway 从空库迁移到 V1_1_1 无错
- [ ] 以 `POST /api/data-task/status` 模拟外部触发，能在 5 分钟内完成 1000 员工的 KPI 计算（性能预算）
- [ ] `POST /api/perf/metric-def/trial-run` 执行任意用户构造的 SQL 不能产生写入
- [ ] `GroovyExecutor` 执行 `System.exit(0)` 被拒绝
- [ ] 所有 V1.1 端点已在 `pt_resource` 登记且 `status='ACTIVE'`

## 风险与回滚

| 风险 | 严重度 | 应对 |
|---|---|---|
| Groovy 沙盒绕过 | 高 | Step P2.3 必须覆盖 10+ 攻击样例（System/Runtime/Thread/File/Net）；代码走 `@AuditLog` 留痕 |
| 宽表 200 slot SQL 性能不足 | 中 | 仅更新必要列（Dynamic SQL by slot 列表），加索引 `(base_dim, data_date, version)` |
| 级联刷新深度过大导致事务过长 | 中 | 每个子任务独立事务，通过 `run_task.parent_id` 关联；`cascade-max-depth` 限流 |
| 定时任务重复执行（多节点） | 中 | 使用 `ShedLock` 或 Redis 分布式锁（已在 workflow-center 有参考） |
| 外部上报幂等漏 | 中 | Task P6.3 双保险：DB unique key (`source`,`data_date`) + Redis SETNX |

**回滚策略：**
- Flyway V1_1_x 脚本全部可回退（`V1_1_1__perf_v11_resources.sql` 写反向 SQL 到 `undo` 目录）
- `application.yml` 中 `perf.engine.groovy-enabled=false` 可紧急禁用 Groovy 路径，SQL 指标不受影响
- 定时任务 `@ConditionalOnProperty("perf.job.daily-kpi.enabled=true")`，紧急关闭一键切断
