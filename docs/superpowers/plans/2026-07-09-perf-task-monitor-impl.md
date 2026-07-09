# 指标任务执行监控页 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增一个运维监控页，展示"指标重算"任务的执行状态/进度与错误原因（任务类型、子名称、状态、起止时间、发起人），复用现有 `PERF_RUN_TASK` 表与 `GET /api/perf/run-tasks` 端点。

**Architecture:** 给 `PERF_RUN_TASK` 加 `trigger_type` 列并在子任务落库时写入 `RECALC`；扩展 run-task 查询过滤（引入 `RunTaskQuery` 查询对象避免参数爆炸）；`PerfRunTaskDTO` 增强 `taskKeyName`(指标中文名)+`startedByName`(发起人姓名)，在 `pageDto` 批量解析零 N+1；前端新建 `TaskMonitor.vue`（表格+过滤+手动刷新+错误详情抽屉）。

**Tech Stack:** Spring Boot 3.2.3 / JDK 17 / MyBatis(-Plus) / JUnit5+Mockito / AssertJ；Vue3 + Element Plus + Vite。

**Spec:** `docs/superpowers/specs/2026-07-09-perf-task-monitor-design.md`

**红线（根 CLAUDE.md）：** 严格 TDD（红-绿-重构，每步独立 commit）；中文注释 + UTF-8；子代理 model 用较强档位；废弃 Flyway（SQL 直接执行）；新增 DB 访问用 MyBatis-Plus。

**环境：** JDK17（本机 temurin-17）；测试库 `onepl_test_bootstrap`；开发库 `onepl`。运行单测：`mvn -pl performance-engine-center test -Dtest=Xxx -Dsurefire.failIfNoSpecifiedTests=false`。

---

## 文件结构

**新建**
- `docs/superpowers/sql/2026-07-09-perf-run-task-add-trigger-type.sql` — DDL 迁移（幂等）
- `performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/dto/RunTaskQuery.java` — run-task 查询过滤对象
- `xanzc_frontend/src/views/perf/TaskMonitor.vue` — 监控页

**修改（后端）**
- `entity/PerfRunTask.java` — 加 `triggerType`
- `mapper/PerfRunTaskMapper.java` + `resources/mapper/**/PerfRunTaskMapper.xml` — BASE_COLUMNS 加列 + selectByCondition/countByCondition 改用 RunTaskQuery + 新过滤
- `service/MetricCalcService.java` — `insertPendingTask` 透传 triggerType
- `service/HistoryRecalcService.java` — 子任务传 `RECALC` + 父任务 setTriggerType
- `service/PerfRunTaskService.java` — page/pageDto 用 RunTaskQuery + 增强字段
- `service/RunTaskAssembler.java` — 无需改（保持纯基础转换；增强在 pageDto）
- `api/dto/PerfRunTaskDTO.java` — 加 `taskKeyName` / `startedByName`
- `controller/PerfRunTaskController.java` — list 加过滤参数
- `docs/schema/ddl-performance.sql` — 基线补 trigger_type

**修改（前端）**
- `xanzc_frontend/src/api/perf.js` — 加 `listRunTasks(params)`
- 路由/菜单（`src/router` 或对应菜单配置）— 挂 TaskMonitor 入口

---

## Task 1: DDL — PERF_RUN_TASK 加 trigger_type 列

**Files:**
- Create: `docs/superpowers/sql/2026-07-09-perf-run-task-add-trigger-type.sql`
- Modify: `docs/schema/ddl-performance.sql`（基线补列）

- [ ] **Step 1: 写幂等迁移脚本**

```sql
-- 2026-07-09 PERF_RUN_TASK 加 trigger_type（触发来源）列 + 复合索引，供任务监控页按重算过滤
-- 幂等：INFORMATION_SCHEMA 预检
SET @col := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'PERF_RUN_TASK' AND COLUMN_NAME = 'trigger_type');
SET @sql := IF(@col = 0,
  'ALTER TABLE `PERF_RUN_TASK` ADD COLUMN `trigger_type` varchar(20) DEFAULT NULL COMMENT ''触发来源：RECALC/SCHEDULED/MANUAL'' AFTER `task_type`',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @idx := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'PERF_RUN_TASK' AND INDEX_NAME = 'idx_type_trigger');
SET @sql2 := IF(@idx = 0,
  'ALTER TABLE `PERF_RUN_TASK` ADD KEY `idx_type_trigger` (`task_type`, `trigger_type`)',
  'SELECT 1');
PREPARE s2 FROM @sql2; EXECUTE s2; DEALLOCATE PREPARE s2;
```

- [ ] **Step 2: 在开发库 + 测试库执行**

Run:
```bash
mysql -uroot -pdjdev onepl < docs/superpowers/sql/2026-07-09-perf-run-task-add-trigger-type.sql
mysql -uroot -p123456 onepl_test_bootstrap < docs/superpowers/sql/2026-07-09-perf-run-task-add-trigger-type.sql
```
Expected: 无报错；`SHOW COLUMNS FROM PERF_RUN_TASK LIKE 'trigger_type';` 返回 1 行。
> 若本机 mysql 凭据不同，按 `performance-engine-center/CLAUDE.md`「环境依赖」调整。测试库缺列会导致 Task 4/6 的 IT 失败。

- [ ] **Step 3: 基线 DDL 补列**

在 `docs/schema/ddl-performance.sql` 的 `CREATE TABLE ... PERF_RUN_TASK` 中，`task_type` 行之后加：
```sql
  `trigger_type` varchar(20) DEFAULT NULL COMMENT '触发来源：RECALC/SCHEDULED/MANUAL',
```
并在 KEY 区加：`KEY `idx_type_trigger` (`task_type`, `trigger_type`),`

- [ ] **Step 4: 提交**

```bash
git add docs/superpowers/sql/2026-07-09-perf-run-task-add-trigger-type.sql docs/schema/ddl-performance.sql
git commit -m "feat(perf): PERF_RUN_TASK 加 trigger_type 列(迁移脚本+基线)"
```

---

## Task 2: 实体 + Mapper XML 承载 trigger_type

**Files:**
- Modify: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/entity/PerfRunTask.java`
- Modify: `performance-engine-center/src/main/resources/mapper/**/PerfRunTaskMapper.xml`

> 无独立测试；由 Task 3 的落库测试 + Task 4 的查询 IT 共同覆盖。BaseMapper `insert` 依赖实体字段 + `map-underscore-to-camel-case` 自动映射 `triggerType↔trigger_type`。

- [ ] **Step 1: 实体加字段**

在 `PerfRunTask.java` 的 `private String taskType;` 之后加：
```java
    /** 触发来源：RECALC / SCHEDULED / MANUAL（V1.13+ 任务监控用）. */
    private String triggerType;
```

- [ ] **Step 2: XML BASE_COLUMNS 加列**

`PerfRunTaskMapper.xml` 的 `<sql id="BASE_COLUMNS">` 里，`task_type,` 之后加 `trigger_type,`：
```xml
    <sql id="BASE_COLUMNS">
        id, task_type, trigger_type, task_key, data_date, data_version, params_json,
        status, started_by, start_time, end_time, error_msg, result_preview_json, created_time
    </sql>
```

- [ ] **Step 3: 提交**

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/entity/PerfRunTask.java \
        performance-engine-center/src/main/resources/mapper/*/PerfRunTaskMapper.xml
git commit -m "feat(perf): PerfRunTask 实体+XML 承载 trigger_type"
```

---

## Task 3: 子任务落库写入 triggerType（MetricCalc + HistoryRecalc）

**Files:**
- Modify: `service/MetricCalcService.java`（`insertPendingTask` 透传 triggerType）
- Modify: `service/HistoryRecalcService.java`（子任务传 RECALC + 父任务 setTriggerType）
- Test: `test/.../service/MetricCalcServiceTest.java`

- [ ] **Step 1: 写失败测试（MetricCalcServiceTest）**

```java
@Test
@DisplayName("落库：insertPendingTask 把 triggerType 写入 run_task")
void calcMetric_persistsTriggerType() {
    PerfMetricDef def = buildEmpSqlMetric();
    when(metricDefService.getByCodeOrNull("TEST_CALC_EMP_01")).thenReturn(def);
    when(sqlExecutor.execute(anyString(), anyMap(), any(java.time.Duration.class)))
            .thenReturn(java.util.Map.of("E001", new java.math.BigDecimal("1")));

    metricCalcService.calcMetric("TEST_CALC_EMP_01", LocalDate.of(2026, 7, 9), "V1", "RECALC");

    org.mockito.ArgumentCaptor<PerfRunTask> cap = org.mockito.ArgumentCaptor.forClass(PerfRunTask.class);
    verify(perfRunTaskMapper).insert(cap.capture());
    assertThat(cap.getValue().getTriggerType()).isEqualTo("RECALC");
}
```
（import 补 `java.time.LocalDate`；`buildEmpSqlMetric` 为该测试类既有 helper。）

- [ ] **Step 2: 运行确认失败**

Run: `mvn -pl performance-engine-center test -Dtest=MetricCalcServiceTest#calcMetric_persistsTriggerType -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL —— `getTriggerType()` 为 null（insertPendingTask 没写）。

- [ ] **Step 3: 改 MetricCalcService**

3.1 `insertPendingTask` 加参数并落库。原方法：
```java
    private void insertPendingTask(String taskId, String metricCode,
                                   LocalDate dataDate, String version) {
        PerfRunTask task = new PerfRunTask();
        task.setId(taskId);
        task.setTaskType("METRIC_RUN");
        task.setTaskKey(metricCode);
```
改为：
```java
    private void insertPendingTask(String taskId, String metricCode,
                                   LocalDate dataDate, String version, String triggerType) {
        PerfRunTask task = new PerfRunTask();
        task.setId(taskId);
        task.setTaskType("METRIC_RUN");
        task.setTriggerType(triggerType);
        task.setTaskKey(metricCode);
```

3.2 调用点（`calcMetricWithStats` 里 `insertPendingTask(taskId, metricCode, dataDate, version);`）改为传入本方法已有的 `triggerType` 形参：
```java
        insertPendingTask(taskId, metricCode, dataDate, version, triggerType);
```

- [ ] **Step 4: 改 HistoryRecalcService（子任务传 RECALC + 父任务标记）**

4.1 子任务调用（约 123 行）：`String childTaskId = metricCalcService.calcMetric(metricCode, date, version);`
改为四参重载显式传 RECALC：
```java
                    String childTaskId = metricCalcService.calcMetric(metricCode, date, version, "RECALC");
```

4.2 父任务 `insertParentTask` 里 `parent.setTaskType("RECALC");` 之后加：
```java
        parent.setTriggerType("RECALC");
```

- [ ] **Step 5: 运行确认通过 + 回归本类**

Run: `mvn -pl performance-engine-center test -Dtest=MetricCalcServiceTest,MetricCalcServiceMultiSubjectTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: 新 case 绿；既有 case 不回归（关注 Tests run/Failures/Errors）。

- [ ] **Step 6: 提交**

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/MetricCalcService.java \
        performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/HistoryRecalcService.java \
        performance-engine-center/src/test/java/com/bank/branch/platform/performance/service/MetricCalcServiceTest.java
git commit -m "feat(perf): 指标计算/重算子任务落库 trigger_type(RECALC)"
```

---

## Task 4: 查询过滤扩展（RunTaskQuery + Mapper）

**Files:**
- Create: `service/dto/RunTaskQuery.java`
- Modify: `mapper/PerfRunTaskMapper.java`（selectByCondition/countByCondition 改签名）
- Modify: `resources/mapper/**/PerfRunTaskMapper.xml`
- Modify: `service/PerfRunTaskService.java`（page 组装 RunTaskQuery）
- Test: `test/.../mapper/PerfRunTaskMapperIT.java`（failsafe，需 DB）

> 引入查询对象避免正在增长的位置参数（现 7 个 + 4 新过滤 = 参数爆炸）。这是"改到即优化"的合理重构，调用方仅 PerfRunTaskService。

- [ ] **Step 1: 写 RunTaskQuery record**

```java
package com.bank.branch.platform.performance.service.dto;

import java.time.LocalDate;

/**
 * run_task 列表查询过滤条件（全部 nullable，null/空表示不过滤）.
 *
 * @param taskType     任务类型（如 METRIC_RUN）
 * @param triggerType  触发来源（如 RECALC）
 * @param taskKey      关键键（指标编码，精确匹配）
 * @param status       状态
 * @param startedBy    发起人工号
 * @param dataDate     数据日期（单值，兼容旧调用）
 * @param dataDateFrom 数据日期范围下界（含）
 * @param dataDateTo   数据日期范围上界（含）
 */
public record RunTaskQuery(String taskType, String triggerType, String taskKey, String status,
                           String startedBy, LocalDate dataDate,
                           LocalDate dataDateFrom, LocalDate dataDateTo) {
}
```

- [ ] **Step 2: 写失败 IT（PerfRunTaskMapperIT）**

在 `PerfRunTaskMapperIT`（继承 `PerformanceMapperTestBase`）新增。先插 2 条 METRIC_RUN：一条 trigger_type=RECALC、一条 SCHEDULED，再按 RECALC 过滤只得 1 条。

```java
@Test
void selectByCondition_filtersByTriggerType() {
    insertRunTask("IT_RT_RECALC", "METRIC_RUN", "RECALC", "M_0046", "SUCCESS", LocalDate.of(2026,7,9), "E01");
    insertRunTask("IT_RT_SCHED",  "METRIC_RUN", "SCHEDULED", "M_0046", "SUCCESS", LocalDate.of(2026,7,9), "E01");
    RunTaskQuery q = new RunTaskQuery("METRIC_RUN", "RECALC", null, null, null, null, null, null);

    long total = mapper.countByCondition(q, null);
    java.util.List<PerfRunTask> rows = mapper.selectByCondition(q, null, 0, 20);

    assertThat(total).isEqualTo(1);
    assertThat(rows).extracting(PerfRunTask::getId).containsExactly("IT_RT_RECALC");
    assertThat(rows.get(0).getTriggerType()).isEqualTo("RECALC");
}
```
（`insertRunTask` 为测试辅助：new PerfRunTask 设字段后 `mapper.insert`；若类中无则内联构造。数据前缀用 `IT_RT_` 便于清理。）

- [ ] **Step 3: 运行确认失败**

Run: `mvn -pl performance-engine-center test -Dtest=PerfRunTaskMapperIT#selectByCondition_filtersByTriggerType -Dsurefire.failIfNoSpecifiedTests=false`
Expected: 编译失败（新签名不存在）或断言失败。
> 若因本机测试库连接/上下文问题无法跑 IT，记录 `log()` 并以 Task 5/6 单测为主验证；但仍须提交本 IT。

- [ ] **Step 4: 改 Mapper 接口签名**

`PerfRunTaskMapper.java` 把 selectByCondition/countByCondition 改为：
```java
    List<PerfRunTask> selectByCondition(@Param("q") RunTaskQuery q,
                                        @Param("dataScopeFilter") String dataScopeFilter,
                                        @Param("offset") int offset,
                                        @Param("limit") int limit);

    long countByCondition(@Param("q") RunTaskQuery q,
                          @Param("dataScopeFilter") String dataScopeFilter);
```
（import `com.bank.branch.platform.performance.service.dto.RunTaskQuery`。）

- [ ] **Step 5: 改 XML**

selectByCondition/countByCondition 的 `<where>` 改为引用 `q.*` 并加新过滤（保留 dataScopeFilter `${}` 注入片段与原排序/分页）：
```xml
    <select id="selectByCondition" resultType="com.bank.branch.platform.performance.entity.PerfRunTask">
        SELECT <include refid="BASE_COLUMNS"/>
        FROM PERF_RUN_TASK
        <where>
            <if test="q.taskType != null and q.taskType != ''">AND task_type = #{q.taskType}</if>
            <if test="q.triggerType != null and q.triggerType != ''">AND trigger_type = #{q.triggerType}</if>
            <if test="q.taskKey != null and q.taskKey != ''">AND task_key = #{q.taskKey}</if>
            <if test="q.status != null and q.status != ''">AND status = #{q.status}</if>
            <if test="q.startedBy != null and q.startedBy != ''">AND started_by = #{q.startedBy}</if>
            <if test="q.dataDate != null">AND data_date = #{q.dataDate}</if>
            <if test="q.dataDateFrom != null">AND data_date &gt;= #{q.dataDateFrom}</if>
            <if test="q.dataDateTo != null">AND data_date &lt;= #{q.dataDateTo}</if>
            <if test="dataScopeFilter != null and dataScopeFilter != ''">${dataScopeFilter}</if>
        </where>
        ORDER BY created_time DESC
        LIMIT #{offset}, #{limit}
    </select>

    <select id="countByCondition" resultType="long">
        SELECT COUNT(*) FROM PERF_RUN_TASK
        <where>
            <if test="q.taskType != null and q.taskType != ''">AND task_type = #{q.taskType}</if>
            <if test="q.triggerType != null and q.triggerType != ''">AND trigger_type = #{q.triggerType}</if>
            <if test="q.taskKey != null and q.taskKey != ''">AND task_key = #{q.taskKey}</if>
            <if test="q.status != null and q.status != ''">AND status = #{q.status}</if>
            <if test="q.startedBy != null and q.startedBy != ''">AND started_by = #{q.startedBy}</if>
            <if test="q.dataDate != null">AND data_date = #{q.dataDate}</if>
            <if test="q.dataDateFrom != null">AND data_date &gt;= #{q.dataDateFrom}</if>
            <if test="q.dataDateTo != null">AND data_date &lt;= #{q.dataDateTo}</if>
            <if test="dataScopeFilter != null and dataScopeFilter != ''">${dataScopeFilter}</if>
        </where>
    </select>
```
> 保持原 selectByCondition 的排序/分页写法：若原 XML 排序或分页片段与此不同，以原写法为准，仅替换 `<where>` 内条件与参数引用为 `q.*`。

- [ ] **Step 6: 改 PerfRunTaskService.page 组装 RunTaskQuery**

`page(...)` 内把两处 mapper 调用改为传 `RunTaskQuery`。将 `page` 签名扩展为接收 RunTaskQuery（见 Task 5 一并定稿），本步最小改动：构造 `RunTaskQuery` 传入 count/select。

- [ ] **Step 7: 运行 IT 确认通过**

Run: `mvn -pl performance-engine-center test -Dtest=PerfRunTaskMapperIT -Dsurefire.failIfNoSpecifiedTests=false`
Expected: 新 case 绿。

- [ ] **Step 8: 提交**

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/dto/RunTaskQuery.java \
        performance-engine-center/src/main/java/com/bank/branch/platform/performance/mapper/PerfRunTaskMapper.java \
        performance-engine-center/src/main/resources/mapper/*/PerfRunTaskMapper.xml \
        performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/PerfRunTaskService.java \
        performance-engine-center/src/test/java/com/bank/branch/platform/performance/mapper/PerfRunTaskMapperIT.java
git commit -m "feat(perf): run-task 查询支持 triggerType/startedBy/日期范围(RunTaskQuery)"
```

---

## Task 5: DTO 增强（taskKeyName + startedByName）

**Files:**
- Modify: `api/dto/PerfRunTaskDTO.java`
- Modify: `service/PerfRunTaskService.java`（page/pageDto 用 RunTaskQuery + enrich）
- Test: `test/.../service/PerfRunTaskServiceTest.java`（Mockito 单测）

- [ ] **Step 1: DTO 加字段**

`PerfRunTaskDTO` 在 `taskKey` 后、`startedBy` 后各加：
```java
    /** 子名称（指标中文名，来源 PERF_METRIC_DEF.metric_name；非指标类任务为 null）. */
    private String taskKeyName;
```
```java
    /** 发起人姓名（来源通讯录；解析不到为 null）. */
    private String startedByName;
```

- [ ] **Step 2: 写失败单测（PerfRunTaskServiceTest）**

构造 service（mock `runTaskMapper` / `bizScopeApi` / `metricDefMapper` / `addressBookApi`）。mock count=1、select 返回 1 条 METRIC_RUN(task_key=M_0046, started_by=12094108)；mock `metricDefMapper.selectByMetricCodes` 返回 metricName=零售…、`addressBookApi.getEmployees` 返回 empName=雷栋。断言 DTO 的 taskKeyName/startedByName 被填。

```java
@Test
void pageDto_enrichesMetricNameAndStartedByName() {
    PerfRunTask t = new PerfRunTask();
    t.setId("R1"); t.setTaskType("METRIC_RUN"); t.setTaskKey("M_0046"); t.setStartedBy("12094108");
    when(runTaskMapper.countByCondition(any(), any())).thenReturn(1L);
    when(runTaskMapper.selectByCondition(any(), any(), anyInt(), anyInt())).thenReturn(List.of(t));
    PerfMetricDef def = new PerfMetricDef(); def.setMetricCode("M_0046"); def.setMetricName("零售一般性存款余额-员工");
    when(metricDefMapper.selectByMetricCodes(List.of("M_0046"))).thenReturn(List.of(def));
    EmployeeDTO emp = new EmployeeDTO(); emp.setEmpId("12094108"); emp.setEmpName("雷栋");
    when(addressBookApi.getEmployees(List.of("12094108"))).thenReturn(List.of(emp));

    PageResult<PerfRunTaskDTO> page = service.pageDto(
        new RunTaskQuery("METRIC_RUN","RECALC",null,null,null,null,null,null), 1, 20);

    PerfRunTaskDTO dto = page.getRecords().get(0);
    assertThat(dto.getTaskKeyName()).isEqualTo("零售一般性存款余额-员工");
    assertThat(dto.getStartedByName()).isEqualTo("雷栋");
}
```
（EmployeeDTO 若为 record/@Builder，按其真实构造方式；字段 empId/empName 已确认。若 PerfRunTaskServiceTest 尚不存在，新建该测试类并按上述 mock 装配 service。）

- [ ] **Step 3: 运行确认失败**

Run: `mvn -pl performance-engine-center test -Dtest=PerfRunTaskServiceTest#pageDto_enrichesMetricNameAndStartedByName -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL —— 增强未实现，name 字段为 null。

- [ ] **Step 4: 改 PerfRunTaskService**

4.1 注入依赖：构造器加 `PerfMetricDefMapper metricDefMapper` 与 `AddressBookApi addressBookApi`（`@Autowired`/构造器注入，跨模块用 Api）。

4.2 `page` 与 `pageDto` 改为接收 `RunTaskQuery`：
```java
public PageResult<PerfRunTask> page(RunTaskQuery q, int pageNo, int pageSize) {
    String dataScopeFilter = resolveScopeFilter();
    long total = runTaskMapper.countByCondition(q, dataScopeFilter);
    int offset = (pageNo - 1) * pageSize;
    List<PerfRunTask> records = runTaskMapper.selectByCondition(q, dataScopeFilter, offset, pageSize);
    return PageResult.of(pageNo, pageSize, total, records);
}

public PageResult<PerfRunTaskDTO> pageDto(RunTaskQuery q, int pageNo, int pageSize) {
    PageResult<PerfRunTask> raw = page(q, pageNo, pageSize);
    List<PerfRunTaskDTO> dtos = new ArrayList<>(raw.getRecords().size());
    for (PerfRunTask t : raw.getRecords()) dtos.add(RunTaskAssembler.toDto(t));
    enrich(dtos, raw.getRecords());
    return PageResult.of(raw.getPageNo(), raw.getPageSize(), raw.getTotal(), dtos);
}
```

4.3 新增私有 `enrich`（批量解析，零 N+1）：
```java
/** 批量填充子名称(指标中文名) + 发起人姓名；查不到留 null（前端回退编码/工号）. */
private void enrich(List<PerfRunTaskDTO> dtos, List<PerfRunTask> raw) {
    if (dtos.isEmpty()) return;
    // 指标名：仅 METRIC_RUN 的 task_key 是 metric_code
    List<String> codes = raw.stream()
            .filter(t -> "METRIC_RUN".equals(t.getTaskType()))
            .map(PerfRunTask::getTaskKey).filter(java.util.Objects::nonNull).distinct().toList();
    Map<String, String> codeToName = codes.isEmpty() ? Map.of()
            : metricDefMapper.selectByMetricCodes(codes).stream()
                .collect(java.util.stream.Collectors.toMap(PerfMetricDef::getMetricCode,
                        PerfMetricDef::getMetricName, (a, b) -> a));
    // 发起人姓名
    List<String> empIds = raw.stream().map(PerfRunTask::getStartedBy)
            .filter(java.util.Objects::nonNull).distinct().toList();
    Map<String, String> empToName = empIds.isEmpty() ? Map.of()
            : addressBookApi.getEmployees(empIds).stream()
                .collect(java.util.stream.Collectors.toMap(EmployeeDTO::getEmpId,
                        EmployeeDTO::getEmpName, (a, b) -> a));
    for (PerfRunTaskDTO d : dtos) {
        d.setTaskKeyName(codeToName.get(d.getTaskKey()));
        d.setStartedByName(empToName.get(d.getStartedBy()));
    }
}
```
（import `PerfMetricDef`、`PerfMetricDefMapper`、`AddressBookApi`、`EmployeeDTO`、`Map`。`getByIdDto` 保持不变或亦可 enrich 单条——本期 list 优先。）

- [ ] **Step 5: 运行确认通过**

Run: `mvn -pl performance-engine-center test -Dtest=PerfRunTaskServiceTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS。

- [ ] **Step 6: 提交**

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/api/dto/PerfRunTaskDTO.java \
        performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/PerfRunTaskService.java \
        performance-engine-center/src/test/java/com/bank/branch/platform/performance/service/PerfRunTaskServiceTest.java
git commit -m "feat(perf): run-task DTO 增强 指标中文名+发起人姓名(批量解析)"
```

---

## Task 6: Controller 过滤参数

**Files:**
- Modify: `controller/PerfRunTaskController.java`
- Test: `test/.../controller/PerfRunTaskControllerIT.java`（或既有 controller 测试）

- [ ] **Step 1: 写失败测试**

在既有 `PerfRunTaskControllerIT`（或新建）加：请求 `GET /api/perf/run-tasks?taskType=METRIC_RUN&triggerType=RECALC&dataDateFrom=2026-07-01&dataDateTo=2026-07-31` 返回 200，且响应含增强字段（断言 JSON 路径 `page`/`data` 结构 + 至少能反序列化 taskKeyName/startedByName 字段存在）。

```java
@Test
void list_withTriggerTypeAndDateRange_ok() throws Exception {
    mockMvc.perform(get("/api/perf/run-tasks")
            .param("taskType", "METRIC_RUN").param("triggerType", "RECALC")
            .param("dataDateFrom", "2026-07-01").param("dataDateTo", "2026-07-31")
            .header(/* 认证头，按既有 IT 约定 */))
        .andExpect(status().isOk());
}
```
（认证/请求头按该模块既有 controller IT 基类约定；若无 IT 基础设施，改为 `PerfRunTaskControllerTest` 用 MockMvc standalone + mock service 验证参数透传与调用。）

- [ ] **Step 2: 运行确认失败**

Run: `mvn -pl performance-engine-center test -Dtest=PerfRunTaskControllerIT#list_withTriggerTypeAndDateRange_ok -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL（新参数未支持 / 编译不过）。

- [ ] **Step 3: 改 Controller.list**

签名加参数并组装 `RunTaskQuery`：
```java
    @GetMapping
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.LIST)
    public ResponseWrapper<PerfRunTaskDTO> list(
            @RequestParam(value = "taskType", required = false) String taskType,
            @RequestParam(value = "triggerType", required = false) String triggerType,
            @RequestParam(value = "taskKey", required = false) String taskKey,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "startedBy", required = false) String startedBy,
            @RequestParam(value = "dataDate", required = false)
                @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate dataDate,
            @RequestParam(value = "dataDateFrom", required = false)
                @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate dataDateFrom,
            @RequestParam(value = "dataDateTo", required = false)
                @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate dataDateTo,
            @RequestParam(value = "pageNo", defaultValue = "1") @Min(1) int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        RunTaskQuery q = new RunTaskQuery(taskType, triggerType, taskKey, status, startedBy,
                dataDate, dataDateFrom, dataDateTo);
        PageResult<PerfRunTaskDTO> dtoPage = perfRunTaskService.pageDto(q, pageNo, pageSize);
        return ResponseWrapper.page(dtoPage);
    }
```
（import `RunTaskQuery`；保留原有 `empId`/日志行；`DateTimeFormat` 若原 dataDate 已有注解沿用其写法。）

- [ ] **Step 4: 运行确认通过 + 回归 controller 层**

Run: `mvn -pl performance-engine-center test -Dtest=PerfRunTaskController* -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/controller/PerfRunTaskController.java \
        performance-engine-center/src/test/java/com/bank/branch/platform/performance/controller/PerfRunTaskControllerIT.java
git commit -m "feat(perf): run-tasks 端点加 triggerType/startedBy/日期范围过滤"
```

---

## Task 7: 前端监控页 TaskMonitor.vue

**Files:**
- Modify: `xanzc_frontend/src/api/perf.js`
- Create: `xanzc_frontend/src/views/perf/TaskMonitor.vue`
- Modify: 路由/菜单配置（`xanzc_frontend/src/router/*` 或菜单数据）

> 前端无 TDD 基础设施，按现有 Vue 模式手改 + `npm run build` 语法校验 + 人工验证。

- [ ] **Step 1: perf.js 加 API**

在 `xanzc_frontend/src/api/perf.js` 加：
```js
// 运行任务监控列表
export function listRunTasks(params = {}) {
  return http.get('/api/perf/run-tasks', { params });
}
```
（`http` 引用方式与文件内既有函数一致。）

- [ ] **Step 2: 建 TaskMonitor.vue**

参照 `src/views/system/Jobs.vue` 结构，实现：
- 过滤区：状态下拉（PENDING/RUNNING/SUCCESS/PARTIAL_FAILED/FAILED）、数据日期范围 `el-date-picker type=daterange`、指标关键字输入(→`taskKey`)、发起人输入(→`startedBy`)、查询/重置/**刷新**按钮。
- 表格列：任务类型(固定"指标重算")、子名称(`row.taskKey + '·' + (row.taskKeyName||'')`)、执行状态(中文 badge)、开始时间、结束时间(空→`-`)、发起人(`row.startedBy + '·' + (row.startedByName||'')`)、操作(详情)。
- 固定请求参数 `taskType:'METRIC_RUN', triggerType:'RECALC'`，合并过滤 + 分页。
- 状态字典 + badge 配色：
```js
const STATUS_MAP = {
  PENDING: { label: '待执行', type: 'info' },
  RUNNING: { label: '执行中', type: 'primary' },
  SUCCESS: { label: '完成', type: 'success' },
  PARTIAL_FAILED: { label: '部分失败', type: 'warning' },
  FAILED: { label: '失败', type: 'danger' }
};
```
- 详情：`el-drawer`，展示全部字段 + `errorMsg` 全文（`<pre>` 可滚动）+ `resultPreviewJson`/`paramsJson`。

- [ ] **Step 3: 挂路由/菜单**

按 `xanzc_frontend/src/router` 既有 perf 路由风格加一条 `path: '/perf/task-monitor', component: () => import('@/views/perf/TaskMonitor.vue')`，菜单标题"任务监控"，归入绩效/运维分组（按现有菜单数据结构）。

- [ ] **Step 4: 构建校验**

Run: `cd xanzc_frontend && npm run build`
Expected: `✓ built`，无 error。

- [ ] **Step 5: 人工验证**

启动前端，进入"任务监控"：触发一次指标重算后，列表应出现该重算的每指标子任务行（任务类型=指标重算、子名称=编码·中文名、发起人=工号·姓名）；点详情看 error_msg；过滤状态/日期范围/指标/发起人生效；刷新按钮重拉。

- [ ] **Step 6: 提交**

```bash
git add xanzc_frontend/src/api/perf.js xanzc_frontend/src/views/perf/TaskMonitor.vue xanzc_frontend/src/router/*
git commit -m "feat(perf-fe): 新增指标重算任务监控页 TaskMonitor.vue"
```

---

## Task 8: 全模块回归

- [ ] **Step 1: 后端 surefire 全量**

Run: `mvn -pl performance-engine-center test -Dsurefire.failIfNoSpecifiedTests=false`
Expected: 新增 case 全绿；失败/错误数不超过既有基线（本环境既有 stale/环境类失败见模块 CLAUDE.md；对比 master 基线确认零新增回归）。

- [ ] **Step 2: 前端构建**

Run: `cd xanzc_frontend && npm run build`
Expected: `✓ built`。

- [ ] **Step 3: 提交（若回归中有修补）**

```bash
git add -A && git commit -m "test(perf): 任务监控回归修补"
```

---

## Self-Review（作者自查，已执行）

**Spec 覆盖**
- §4 数据模型（trigger_type 列+落库）→ Task 1/2/3
- §5 查询过滤 + DTO 增强 → Task 4/5
- §6 权限（复用 P_PERF_RT_LIST + 数据范围）→ 复用现有 `resolveScopeFilter`，无新 Task（Controller 沿用 `@BizAuth(LIST)`）
- §7 前端页 → Task 7
- §8 测试 → 各 Task 内 TDD + Task 8 回归

**占位符扫描**：无 TBD/TODO。少量"按既有 IT 基类约定/按现有菜单结构"是对齐现有代码的指引，非占位。EmployeeDTO 构造在测试步注明按其真实形态。

**类型/命名一致**：`RunTaskQuery(taskType,triggerType,taskKey,status,startedBy,dataDate,dataDateFrom,dataDateTo)`、`PerfRunTaskDTO.taskKeyName/startedByName`、`insertPendingTask(...,triggerType)`、`calcMetric(code,date,version,"RECALC")`、`selectByMetricCodes`、`getEmployees→EmployeeDTO{empId,empName}` 全计划一致。

**风险**：Mapper IT（Task 4）/Controller IT（Task 6）依赖测试库与 Spring 上下文；本环境既有大量 IT 失败基线，若无法跑通，以 Task 5 的 Service 单测（Mockito）为主验证增强逻辑，IT 仍提交但结果按基线对比判定，不据此阻断。
