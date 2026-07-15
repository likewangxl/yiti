# 指标重算任务监控页（新增/执行/批量执行/历史）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把只读的"任务监控"页升级为按指标分组的可操作页，交付 新增 / 执行 / 批量执行 / 历史 四个动作，全部复用 `PERF_RUN_TASK`（零 schema 变更）。

**Architecture:** 后端新增两个端点——① `GET /api/perf/run-tasks/metric-summary`（按 `task_key` 分组汇总的只读列表）；② `POST /api/perf/metrics/batch-execute`（批量执行，服务端逐指标 best-effort 聚合，复用 `MetricLifecycleFacade.executeMetric`）。单指标"新增/执行"直接复用既有 `POST /api/perf/metrics/{code}/execute`，"历史"复用既有 `GET /api/perf/run-tasks?taskKey=`。前端重写 `TaskMonitor.vue`。字典与资源用 DML 种子。

**Tech Stack:** Spring Boot 3.2.3 + JDK 17 + MyBatis-Plus（BaseMapper + XML 自定义 SQL）+ MySQL 8；Vue 3 + Vite + Element Plus。

**Spec:** `docs/superpowers/specs/2026-07-15-perf-task-monitor-execute-history-design.md`

## Global Constraints

- 每个 Controller 方法必标 `@BizAuth(bizType = BizType.PERF_CONFIG, action = <BizAction>)`（守护 `BizAuthConsistencyArchTest`）。
- 写/高危端点标 `@AuditLog(action, resourceType, reasonRequired=true)`；请求体须含 `reason` 字段供 AOP 读取。
- 新增 DB 访问一律 MyBatis-Plus：Mapper `extends BaseMapper<T>`，自定义 SQL 才落 XML；Mapper XML 一律 `#{}`，**唯一例外**是既有数据范围片段 `${dataScopeFilter}`（仅可信来源）。
- **零 schema 变更**：不建表、不加列。只新增 SYS_DICT 字典行 + PT_RESOURCE/PT_ROLE_RESOURCE 资源行（DML）。
- 批量执行为 **best-effort、不开最外层 `@Transactional`**：单指标失败不中断整批（仿 `HistoryRecalcService`），每指标各自写 `PERF_RUN_TASK` 行。
- `PT_RESOURCE.RESOURCE_ID` 长度 ≤ 20。
- MySQL 8 默认 `ONLY_FULL_GROUP_BY`：GROUP BY 查询的非聚合列必须出现在 GROUP BY 中。
- 全中文注释、UTF-8；TDD 红-绿-重构，每步独立 commit。
- 单测 `*Test.java`（surefire，`mvn test`）；集成测试 `*IT.java`（failsafe，`mvn verify`）。跨模块改动先 `mvn clean install -DskipTests` 再测。
- 若用 subagent 执行：`model` 必须 ≥ `sonnet`（禁 haiku）。
- DDL/字典/资源种子落 **yiti** 开发库（`jdbc:mysql://localhost:3306/yiti`，root/djdev）；IT 测试库为 `onepl_test_bootstrap`。

---

## 现有锚点（实现时直接照抄的既有代码事实）

- `PerfRunTaskMapper extends BaseMapper<PerfRunTask>`，XML 命名空间 `com.bank.branch.platform.performance.mapper.PerfRunTaskMapper`，路径 `performance-engine-center/src/main/resources/mapper/performance/PerfRunTaskMapper.xml`。已有 `<sql id="CONDITION_WHERE">` 用 `${dataScopeFilter}`。
- `PerfRunTaskService`（`service/PerfRunTaskService.java`）构造注入：`runTaskMapper, currentUserApi, bizScopeApi, metricDefMapper, addressBookApi`。已有 **private** `String resolveScopeFilter()`：`ALL` 范围返回 `null`，否则返回 `"AND started_by = '<empId>'"`。已有 `pageDto(...)`（分页装配范例）。
- `MetricLifecycleFacade.executeMetric(String metricCode, LocalDate dataDate, Boolean cascadeInput, LocalDate allocDate)` → `RunTaskInfoDTO{taskId,status,metricCode,dataDate,version}`：内部解析版本 `sysControlService.getCurrentVersion(def.getBaseDim()).getCurrentVersion()`（异常兜底 `"v_default"`），`cascade=false` 分支即 `metricCalcService.calcMetric(code, dataDate, version, "MANUAL", allocDate)`；不存在的指标抛 `PERF-40001`。构造已注入 `metricDefService/metricCalcService/cascadeRefresher/sysControlService/perfRunTaskMapper`。
- `PerfRunTaskController`（`@RequestMapping("/api/perf/run-tasks")`）：已有 `list`（`@BizAuth LIST`）、`getById`（`@BizAuth READ`）。注入 `currentUserApi, perfRunTaskService`。
- `MetricDefController`（`@RequestMapping("/api/perf/metrics")`）：注入 `currentUserApi, metricLifecycleFacade, metricDefService, metricRefService, metricSlotService`。既有 `execute` 端点 `POST /{metricCode}/execute` = `@BizAuth EXECUTE` + `@AuditLog(reasonRequired=true)` → `metricLifecycleFacade.executeMetric(...)`。
- `BizAction` 含 `LIST/READ/WRITE/DELETE/EXECUTE/CONFIG/RECALC`。
- `RunTaskInfoDTO{taskId,status,metricCode,dataDate,version}`、`MetricExecuteReqDTO{dataDate,allocDate,cascade=true,async=true,reason}`。
- 前端 `src/api/http.js`：`call(method,url,config,fallback)`（GET 失败回退 mock，写操作抛错）、`unwrapPage(r)→{records,total}`；响应拦截器对分页返回 `body.page`。`src/api/perf.js`：`listMetrics(params)`（GET `/perf/metrics` → 数组）、`executeMetric(code,data)`（POST `/perf/metrics/{code}/execute`）、`listRunTasks(params)`（GET `/perf/run-tasks` → `unwrapPage`）。`src/api/system.js`：`listDictItems(dictType)`（GET `/sys/dicts/{dictType}/items` → `DictItemDTO[]{dictCode,dictLabel,dictValue}`）。
- 路由已注册：`src/router/index.js` `{ path:'perf/task-monitor', name:'PerfTaskMonitor', component: TaskMonitor.vue }`；菜单 `M_PERF_TASK_MONITOR` 已种子。
- 单执行资源 ID = `P_PERF_MTR_EXEC`（`/api/perf/metrics/*/execute`，POST，父菜单 `M_PERF_METRICS`，`SYS_CODE='PERF'`）。列表资源 = `P_PERF_RT_LIST`。
- `SYS_DICT` 表列：`id, dict_type, dict_code, dict_label, dict_value, sort_order, status, remark, created_by, created_time, updated_by, updated_time`；唯一键 `(dict_type, dict_code)`。字典项经 `DictFacade.toItemDTO(SysDict)` 从 **SYS_DICT** 读出。

---

## File Structure

**后端（performance-engine-center）**
- Create `controller/dto/MetricSummaryDTO.java` — 分组汇总响应行（metricCode/metricName/firstCreatedTime/runCount）。
- Create `controller/dto/BatchExecuteReqDTO.java` — 批量执行请求。
- Create `controller/dto/BatchExecuteRespDTO.java` — 批量执行聚合响应（含内嵌 `Item`）。
- Modify `mapper/PerfRunTaskMapper.java` — 新增 `selectMetricSummary` / `countMetricSummary`。
- Modify `mapper/performance/PerfRunTaskMapper.xml` — 两条 GROUP BY 查询。
- Modify `service/PerfRunTaskService.java` — 新增 `pageMetricSummary`。
- Modify `controller/PerfRunTaskController.java` — 新增 `GET /metric-summary`。
- Modify `facade/MetricLifecycleFacade.java` — 新增 `batchExecute`。
- Modify `controller/MetricDefController.java` — 新增 `POST /batch-execute`。
- Tests：`*MapperIT` / `*ServiceTest` / `*ControllerIT` / facade `*Test`。

**SQL**
- Create `docs/superpowers/sql/2026-07-15-perf-task-monitor-dict-and-resources.sql` — 字典 + 资源 + 角色绑定种子。

**前端（xanzc_frontend）**
- Modify `src/api/perf.js` — `listMetricSummary` / `batchExecuteMetrics`。
- Rewrite `src/views/perf/TaskMonitor.vue`。

---

## Task 1: 分组汇总 Mapper + XML + DTO

**Files:**
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/controller/dto/MetricSummaryDTO.java`
- Modify: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/mapper/PerfRunTaskMapper.java`
- Modify: `performance-engine-center/src/main/resources/mapper/performance/PerfRunTaskMapper.xml`
- Test: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/mapper/PerfRunTaskSummaryMapperIT.java`

**Interfaces:**
- Produces: `MetricSummaryDTO{String metricCode; String metricName; LocalDateTime firstCreatedTime; long runCount}`；
  `List<MetricSummaryDTO> PerfRunTaskMapper.selectMetricSummary(@Param("taskType") String, @Param("keyword") String, @Param("dataScopeFilter") String, @Param("offset") int, @Param("limit") int)`；
  `long PerfRunTaskMapper.countMetricSummary(@Param("taskType") String, @Param("keyword") String, @Param("dataScopeFilter") String)`。

- [ ] **Step 1: 写 DTO**

```java
package com.bank.branch.platform.performance.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 指标重算任务按指标分组汇总行（任务监控列表）. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricSummaryDTO {
    /** 指标编码（= perf_run_task.task_key）. */
    private String metricCode;
    /** 指标中文名（LEFT JOIN perf_metric_def；查不到为 null）. */
    private String metricName;
    /** 该指标首次进入回算时间（= MIN(created_time)）. */
    private LocalDateTime firstCreatedTime;
    /** 该指标累计执行次数（= COUNT(*)）. */
    private long runCount;
}
```

- [ ] **Step 2: Mapper 接口新增两个方法**

在 `PerfRunTaskMapper.java` 的 `countByTypeAndDate` 声明之后（import 处补 `import com.bank.branch.platform.performance.controller.dto.MetricSummaryDTO;`）加入：

```java
    /**
     * 按 task_key(指标) 分组汇总（任务监控列表）.
     *
     * <p>LEFT JOIN perf_metric_def 取指标名并支持"编码或名称"关键字模糊；
     * {@code dataScopeFilter} 为可信数据范围片段（形如 " AND started_by = 'xxx' "），
     * 仅由 Service 层 resolveScopeFilter 生成，禁止用户输入拼入。
     *
     * @param taskType        任务类型（如 METRIC_RUN）
     * @param keyword         关键字（nullable；匹配 task_key 或 metric_name）
     * @param dataScopeFilter 数据范围 SQL 片段（nullable）
     * @param offset          偏移
     * @param limit           每页
     */
    List<MetricSummaryDTO> selectMetricSummary(@Param("taskType") String taskType,
                                               @Param("keyword") String keyword,
                                               @Param("dataScopeFilter") String dataScopeFilter,
                                               @Param("offset") int offset,
                                               @Param("limit") int limit);

    /** 与 {@link #selectMetricSummary} 同过滤条件的分组计数（DISTINCT task_key 数）. */
    long countMetricSummary(@Param("taskType") String taskType,
                            @Param("keyword") String keyword,
                            @Param("dataScopeFilter") String dataScopeFilter);
```

- [ ] **Step 3: XML 新增两条查询**

在 `PerfRunTaskMapper.xml` 的 `</mapper>` 前插入：

```xml
    <!--
        任务监控：按 task_key(指标) 分组汇总。
        - LEFT JOIN PERF_METRIC_DEF 取指标名 + 支持名称/编码关键字模糊；
        - ${dataScopeFilter} 仅 run_task 有 started_by 列，拼在 t 上无歧义；
        - ONLY_FULL_GROUP_BY：GROUP BY 含 t.task_key + d.metric_name。
    -->
    <sql id="SUMMARY_WHERE">
        t.task_type = #{taskType}
        AND t.task_key IS NOT NULL AND t.task_key != ''
        <if test="keyword != null and keyword != ''">
            AND (t.task_key LIKE CONCAT('%', #{keyword}, '%')
                 OR d.metric_name LIKE CONCAT('%', #{keyword}, '%'))
        </if>
        <if test="dataScopeFilter != null and dataScopeFilter != ''">
            ${dataScopeFilter}
        </if>
    </sql>

    <select id="selectMetricSummary"
            resultType="com.bank.branch.platform.performance.controller.dto.MetricSummaryDTO">
        SELECT t.task_key            AS metricCode,
               d.metric_name         AS metricName,
               MIN(t.created_time)   AS firstCreatedTime,
               COUNT(*)              AS runCount
        FROM PERF_RUN_TASK t
        LEFT JOIN PERF_METRIC_DEF d ON d.metric_code = t.task_key
        <where>
            <include refid="SUMMARY_WHERE"/>
        </where>
        GROUP BY t.task_key, d.metric_name
        ORDER BY firstCreatedTime DESC
        LIMIT #{limit} OFFSET #{offset}
    </select>

    <select id="countMetricSummary" resultType="long">
        SELECT COUNT(DISTINCT t.task_key)
        FROM PERF_RUN_TASK t
        LEFT JOIN PERF_METRIC_DEF d ON d.metric_code = t.task_key
        <where>
            <include refid="SUMMARY_WHERE"/>
        </where>
    </select>
```

- [ ] **Step 4: 写失败的 Mapper IT**

```java
package com.bank.branch.platform.performance.mapper;

import com.bank.branch.platform.performance.controller.dto.MetricSummaryDTO;
import com.bank.branch.platform.performance.entity.PerfRunTask;
import com.bank.branch.platform.performance.support.PerformanceMapperTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 分组汇总 Mapper IT：验证 COUNT/MIN/GROUP BY 与关键字过滤（前缀 TEST_RT_）. */
class PerfRunTaskSummaryMapperIT extends PerformanceMapperTestBase {

    @Autowired
    private PerfRunTaskMapper mapper;

    private PerfRunTask row(String id, String metricCode, LocalDateTime created) {
        PerfRunTask t = new PerfRunTask();
        t.setId(id);
        t.setTaskType("METRIC_RUN");
        t.setTriggerType("MANUAL");
        t.setTaskKey(metricCode);
        t.setDataDate(LocalDate.of(2026, 7, 1));
        t.setStatus("SUCCESS");
        t.setStartedBy("TEST_RT_EMP");
        t.setStartTime(created);
        return t; // created_time 由 DB 默认值填充
    }

    @Test
    void selectMetricSummary_groupsByMetricAndCounts() {
        mapper.insert(row("TEST_RT_S1", "TEST_RT_MA", LocalDateTime.now().minusDays(2)));
        mapper.insert(row("TEST_RT_S2", "TEST_RT_MA", LocalDateTime.now().minusDays(1)));
        mapper.insert(row("TEST_RT_S3", "TEST_RT_MB", LocalDateTime.now()));

        List<MetricSummaryDTO> rows = mapper.selectMetricSummary("METRIC_RUN", "TEST_RT_M", null, 0, 20);

        assertThat(rows).extracting(MetricSummaryDTO::getMetricCode)
                .contains("TEST_RT_MA", "TEST_RT_MB");
        MetricSummaryDTO ma = rows.stream().filter(r -> "TEST_RT_MA".equals(r.getMetricCode())).findFirst().orElseThrow();
        assertThat(ma.getRunCount()).isEqualTo(2L);
        assertThat(ma.getFirstCreatedTime()).isNotNull();

        long total = mapper.countMetricSummary("METRIC_RUN", "TEST_RT_M", null);
        assertThat(total).isGreaterThanOrEqualTo(2L);
    }

    @Test
    void selectMetricSummary_dataScopeFilterNarrowsByStartedBy() {
        mapper.insert(row("TEST_RT_S4", "TEST_RT_MC", LocalDateTime.now()));
        List<MetricSummaryDTO> mine = mapper.selectMetricSummary(
                "METRIC_RUN", "TEST_RT_MC", "AND started_by = 'TEST_RT_EMP'", 0, 20);
        assertThat(mine).extracting(MetricSummaryDTO::getMetricCode).contains("TEST_RT_MC");

        List<MetricSummaryDTO> others = mapper.selectMetricSummary(
                "METRIC_RUN", "TEST_RT_MC", "AND started_by = 'NOBODY_X'", 0, 20);
        assertThat(others).extracting(MetricSummaryDTO::getMetricCode).doesNotContain("TEST_RT_MC");
    }
}
```

- [ ] **Step 5: 运行确认失败**

Run: `mvn -q -pl performance-engine-center test-compile` 先确保编译（此时若 XML 未加会编译过但 IT 失败）。
Run: `mvn -q -pl performance-engine-center -Dit.test=PerfRunTaskSummaryMapperIT verify`
Expected: 先失败（方法未实现/编译报错）→ 补齐 Step 1–3 后再跑。

- [ ] **Step 6: 补齐实现后运行确认通过**

Run: `mvn -q -pl performance-engine-center -Dit.test=PerfRunTaskSummaryMapperIT verify`
Expected: PASS（2 个 IT 绿）。

- [ ] **Step 7: Commit**

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/controller/dto/MetricSummaryDTO.java \
        performance-engine-center/src/main/java/com/bank/branch/platform/performance/mapper/PerfRunTaskMapper.java \
        performance-engine-center/src/main/resources/mapper/performance/PerfRunTaskMapper.xml \
        performance-engine-center/src/test/java/com/bank/branch/platform/performance/mapper/PerfRunTaskSummaryMapperIT.java
git commit -m "feat(perf): 任务监控-按指标分组汇总 Mapper/XML/DTO + IT"
```

---

## Task 2: Service.pageMetricSummary

**Files:**
- Modify: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/PerfRunTaskService.java`
- Test: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/service/PerfRunTaskSummaryServiceTest.java`

**Interfaces:**
- Consumes: `PerfRunTaskMapper.selectMetricSummary/countMetricSummary`（Task 1）；private `resolveScopeFilter()`（既有）。
- Produces: `PageResult<MetricSummaryDTO> PerfRunTaskService.pageMetricSummary(String taskType, String keyword, int pageNo, int pageSize)`。

- [ ] **Step 1: 写失败的 Service 单测**

```java
package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.auth.api.BizScopeApi;
import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.security.enums.DataScopeType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.performance.controller.dto.MetricSummaryDTO;
import com.bank.branch.platform.performance.mapper.PerfRunTaskMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PerfRunTaskSummaryServiceTest {

    @Mock PerfRunTaskMapper runTaskMapper;
    @Mock CurrentUserApi currentUserApi;
    @Mock BizScopeApi bizScopeApi;
    @Mock com.bank.branch.platform.performance.mapper.PerfMetricDefMapper metricDefMapper;
    @Mock com.bank.branch.platform.portal.api.AddressBookApi addressBookApi;

    @InjectMocks PerfRunTaskService service;

    @Test
    void pageMetricSummary_admin_noScopeFilter_returnsPage() {
        when(currentUserApi.getCurrentEmpId()).thenReturn("ADMIN");
        when(bizScopeApi.resolveScope(eq("ADMIN"), any())).thenReturn(DataScopeType.ALL);
        when(runTaskMapper.countMetricSummary(eq("METRIC_RUN"), isNull(), isNull())).thenReturn(1L);
        when(runTaskMapper.selectMetricSummary(eq("METRIC_RUN"), isNull(), isNull(), eq(0), eq(20)))
                .thenReturn(List.of(MetricSummaryDTO.builder()
                        .metricCode("M_1").metricName("指标一").runCount(3L).build()));

        PageResult<MetricSummaryDTO> page = service.pageMetricSummary("METRIC_RUN", null, 1, 20);

        assertThat(page.getTotal()).isEqualTo(1L);
        assertThat(page.getRecords()).hasSize(1);
        assertThat(page.getRecords().get(0).getRunCount()).isEqualTo(3L);
    }

    @Test
    void pageMetricSummary_emptyTotal_skipsSelect() {
        when(currentUserApi.getCurrentEmpId()).thenReturn("U");
        when(bizScopeApi.resolveScope(eq("U"), any())).thenReturn(DataScopeType.SELF);
        when(runTaskMapper.countMetricSummary(eq("METRIC_RUN"), isNull(), any())).thenReturn(0L);

        PageResult<MetricSummaryDTO> page = service.pageMetricSummary("METRIC_RUN", null, 1, 20);

        assertThat(page.getTotal()).isZero();
        assertThat(page.getRecords()).isEmpty();
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `mvn -q -pl performance-engine-center -Dtest=PerfRunTaskSummaryServiceTest test`
Expected: FAIL（`pageMetricSummary` 未定义，编译错误）。

- [ ] **Step 3: 实现 pageMetricSummary**

在 `PerfRunTaskService` 中（import 补 `import com.bank.branch.platform.performance.controller.dto.MetricSummaryDTO;`）新增：

```java
    /**
     * 任务监控：按指标分组分页汇总（含数据范围过滤）.
     *
     * <p>total=0 直接返回空分页（跳过 select）；数据范围复用 {@link #resolveScopeFilter()}.
     *
     * @param taskType 任务类型（如 METRIC_RUN）
     * @param keyword  指标编码/名称关键字（nullable）
     * @param pageNo   页码（从 1 起）
     * @param pageSize 页大小
     */
    @Transactional(readOnly = true)
    public PageResult<MetricSummaryDTO> pageMetricSummary(String taskType, String keyword,
                                                          int pageNo, int pageSize) {
        String dataScopeFilter = resolveScopeFilter();
        String kw = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;
        long total = runTaskMapper.countMetricSummary(taskType, kw, dataScopeFilter);
        if (total == 0L) {
            return PageResult.of(pageNo, pageSize, 0L, java.util.Collections.emptyList());
        }
        int offset = Math.max(pageNo - 1, 0) * pageSize;
        List<MetricSummaryDTO> records = runTaskMapper.selectMetricSummary(
                taskType, kw, dataScopeFilter, offset, pageSize);
        return PageResult.of(pageNo, pageSize, total, records);
    }
```

- [ ] **Step 4: 运行确认通过**

Run: `mvn -q -pl performance-engine-center -Dtest=PerfRunTaskSummaryServiceTest test`
Expected: PASS（2 绿）。

- [ ] **Step 5: Commit**

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/PerfRunTaskService.java \
        performance-engine-center/src/test/java/com/bank/branch/platform/performance/service/PerfRunTaskSummaryServiceTest.java
git commit -m "feat(perf): 任务监控-Service pageMetricSummary + 单测"
```

---

## Task 3: Controller GET /run-tasks/metric-summary

**Files:**
- Modify: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/controller/PerfRunTaskController.java`
- Test: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/controller/PerfRunTaskSummaryControllerIT.java`

**Interfaces:**
- Consumes: `PerfRunTaskService.pageMetricSummary`（Task 2）。
- Produces: `GET /api/perf/run-tasks/metric-summary?taskType&metricKeyword&pageNo&pageSize` → `ResponseWrapper<MetricSummaryDTO>`（page 分页）。资源 `P_PERF_RT_SUM`。

- [ ] **Step 1: 写端点**

在 `PerfRunTaskController` 中（import 补 `MetricSummaryDTO` 与 `jakarta.validation.constraints.Max/Min` 已有）加：

```java
    /**
     * 任务监控：按指标分组汇总分页（列表页数据源）.
     *
     * @param taskType      任务类型（默认 METRIC_RUN）
     * @param metricKeyword 指标编码/名称关键字（nullable）
     */
    @GetMapping("/metric-summary")
    @Operation(summary = "按指标分组汇总任务（任务监控列表）")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.LIST)
    public ResponseWrapper<MetricSummaryDTO> metricSummary(
            @RequestParam(value = "taskType", defaultValue = "METRIC_RUN") String taskType,
            @RequestParam(value = "metricKeyword", required = false) String metricKeyword,
            @RequestParam(value = "pageNo", defaultValue = "1") @Min(1) int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        String empId = currentUserApi.getCurrentEmpId();
        log.debug("[PerfRunTaskController.metricSummary] empId={}, taskType={}, keyword={}, pageNo={}, pageSize={}",
                empId, taskType, metricKeyword, pageNo, pageSize);
        PageResult<MetricSummaryDTO> page = perfRunTaskService.pageMetricSummary(
                taskType, metricKeyword, pageNo, pageSize);
        return ResponseWrapper.page(page);
    }
```

> `BizAction` 需 import：`import com.bank.branch.platform.common.security.enums.BizAction;`（若未 import）。`MetricSummaryDTO` import：`import com.bank.branch.platform.performance.controller.dto.MetricSummaryDTO;`。

- [ ] **Step 2: 写失败的 Controller IT**

按模块现有 `*ControllerIT`（`@SpringBootTest` + `MockMvc` + 测试登录）写法。若模块已有 `PerfRunTaskControllerIT`，复用其基类/登录辅助；否则参照其它 perf `*ControllerIT`：

```java
package com.bank.branch.platform.performance.controller;

// 复用模块既有 IT 基类与登录辅助（参照 PerfRunTaskControllerIT / MetricDefControllerIT）
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /api/perf/run-tasks/metric-summary 鉴权 + 分页契约 IT. */
class PerfRunTaskSummaryControllerIT extends /* 既有 perf Controller IT 基类 */ AbstractPerfControllerIT {

    @Test
    void metricSummary_authed_returns200WithPageEnvelope() throws Exception {
        loginAsAdmin(); // 复用基类登录辅助
        mockMvc.perform(get("/api/perf/run-tasks/metric-summary")
                        .param("taskType", "METRIC_RUN")
                        .param("pageNo", "1").param("pageSize", "20")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00000"));
    }

    @Test
    void metricSummary_anonymous_returns401() throws Exception {
        mockMvc.perform(get("/api/perf/run-tasks/metric-summary"))
                .andExpect(status().isUnauthorized());
    }
}
```

> 若模块 IT 基类名不同（如 `PerformanceControllerITBase`），替换 `extends` 与 `loginAsAdmin()/session` 为该基类等价物。断言口径以模块既有 IT 为准（`$.code` 成功码在本项目为 `"00000"`）。

- [ ] **Step 3: 运行确认失败 → 补齐 → 通过**

Run: `mvn -q -pl performance-engine-center -Dit.test=PerfRunTaskSummaryControllerIT verify`
Expected: 先失败（端点未加/基类占位）→ 端点补齐 + IT 基类对齐后 PASS。

- [ ] **Step 4: 补资源种子占位说明**

> 注：`P_PERF_RT_SUM` 资源与角色绑定在 **Task 7** 统一落库；本任务端点上线后，未绑定资源的角色会 403，属预期，Task 7 后放行。

- [ ] **Step 5: Commit**

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/controller/PerfRunTaskController.java \
        performance-engine-center/src/test/java/com/bank/branch/platform/performance/controller/PerfRunTaskSummaryControllerIT.java
git commit -m "feat(perf): 任务监控-GET /run-tasks/metric-summary 端点 + IT"
```

---

## Task 4: 批量执行请求/响应 DTO

**Files:**
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/controller/dto/BatchExecuteReqDTO.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/controller/dto/BatchExecuteRespDTO.java`

**Interfaces:**
- Produces: `BatchExecuteReqDTO{List<String> metricCodes; LocalDate dataDate; String reason}`；
  `BatchExecuteRespDTO{int total; int success; int failed; List<Item> results}`，`Item{String metricCode; String status; String runTaskId; String errorMsg}`。

- [ ] **Step 1: 写 ReqDTO**

```java
package com.bank.branch.platform.performance.controller.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/** 指标批量执行请求（POST /api/perf/metrics/batch-execute）. */
@Data
public class BatchExecuteReqDTO {

    /** 指标编码列表（必填、非空；同步执行软上限 50 防超时）. */
    @NotEmpty(message = "metricCodes 不能为空")
    @Size(max = 50, message = "单次批量执行指标数不能超过 50")
    private List<String> metricCodes;

    /** 数据日期（必填，= 要计算哪天的数据）. */
    @NotNull(message = "dataDate 不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dataDate;

    /** 执行原因（高危操作必填，@AuditLog reasonRequired=true）. */
    @NotBlank(message = "reason 不能为空")
    @Size(max = 500, message = "reason 不能超过 500 字")
    private String reason;
}
```

- [ ] **Step 2: 写 RespDTO**

```java
package com.bank.branch.platform.performance.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 指标批量执行聚合响应. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchExecuteRespDTO {
    /** 请求指标总数. */
    private int total;
    /** 成功数. */
    private int success;
    /** 失败数. */
    private int failed;
    /** 逐指标结果明细. */
    private List<Item> results;

    /** 单指标执行结果. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        /** 指标编码. */
        private String metricCode;
        /** 状态：SUCCESS/RUNNING/FAILED（失败为本地聚合状态）. */
        private String status;
        /** 成功时的 run_task 主键（失败为 null）. */
        private String runTaskId;
        /** 失败原因（成功为 null）. */
        private String errorMsg;
    }
}
```

- [ ] **Step 3: 编译**

Run: `mvn -q -pl performance-engine-center test-compile`
Expected: PASS。

- [ ] **Step 4: Commit**

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/controller/dto/BatchExecuteReqDTO.java \
        performance-engine-center/src/main/java/com/bank/branch/platform/performance/controller/dto/BatchExecuteRespDTO.java
git commit -m "feat(perf): 批量执行请求/响应 DTO"
```

---

## Task 5: Facade.batchExecute（best-effort 聚合）

**Files:**
- Modify: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/facade/MetricLifecycleFacade.java`
- Test: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/facade/MetricLifecycleFacadeBatchExecuteTest.java`

**Interfaces:**
- Consumes: 既有 `this.executeMetric(String, LocalDate, Boolean, LocalDate)`。
- Produces: `BatchExecuteRespDTO MetricLifecycleFacade.batchExecute(List<String> metricCodes, LocalDate dataDate)`。

- [ ] **Step 1: 写失败的 Facade 单测**

```java
package com.bank.branch.platform.performance.facade;

import com.bank.branch.platform.performance.controller.dto.BatchExecuteRespDTO;
import com.bank.branch.platform.performance.controller.dto.RunTaskInfoDTO;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Spy;
import org.mockito.InjectMocks;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class MetricLifecycleFacadeBatchExecuteTest {

    // batchExecute 只依赖自身 executeMetric，用 spy 打桩其行为，隔离下游
    @Spy @InjectMocks MetricLifecycleFacade facade;

    @Test
    void batchExecute_partialFailure_aggregates() {
        LocalDate d = LocalDate.of(2026, 7, 1);
        doReturn(RunTaskInfoDTO.builder().taskId("T1").status("SUCCESS").build())
                .when(facade).executeMetric(eq("M_OK"), eq(d), eq(false), any());
        doThrow(new PerfException(PerfErrorCode.VALIDATION_FAILED, "boom"))
                .when(facade).executeMetric(eq("M_BAD"), eq(d), eq(false), any());

        BatchExecuteRespDTO resp = facade.batchExecute(List.of("M_OK", "M_BAD"), d);

        assertThat(resp.getTotal()).isEqualTo(2);
        assertThat(resp.getSuccess()).isEqualTo(1);
        assertThat(resp.getFailed()).isEqualTo(1);
        assertThat(resp.getResults()).extracting(BatchExecuteRespDTO.Item::getMetricCode)
                .containsExactly("M_OK", "M_BAD");
        assertThat(resp.getResults().get(1).getErrorMsg()).contains("boom");
    }
}
```

> `@Spy @InjectMocks` 会用无参 Mockito 构造 facade（其 final 依赖为 null，但本测试只打桩 `executeMetric` 不触达它们）。若模块启用了 Lombok `@RequiredArgsConstructor` 使无参构造缺失导致 spy 失败，改为 `new MetricLifecycleFacade(mock, mock, ...)` 手动构造后 `spy(...)`；下游依赖全传 `Mockito.mock(...)`。

- [ ] **Step 2: 运行确认失败**

Run: `mvn -q -pl performance-engine-center -Dtest=MetricLifecycleFacadeBatchExecuteTest test`
Expected: FAIL（`batchExecute` 未定义）。

- [ ] **Step 3: 实现 batchExecute**

在 `MetricLifecycleFacade` 中新增（import 补 `BatchExecuteRespDTO`、`java.util.ArrayList`、`java.util.List`）：

```java
    /**
     * 批量执行：对给定指标逐个立即执行（非级联），best-effort 聚合结果.
     *
     * <p>单指标失败不中断整批（仿 HistoryRecalcService）；每指标各自写 PERF_RUN_TASK 行。
     * 本方法不开 @Transactional：各 calcMetric 由 {@link #executeMetric} 独立管理 run_task。
     * version 由 {@link #executeMetric} 内部解析当前生效版本。
     *
     * @param metricCodes 指标编码列表
     * @param dataDate    数据日期
     * @return 聚合结果（total/success/failed + 逐指标明细）
     */
    public BatchExecuteRespDTO batchExecute(java.util.List<String> metricCodes, LocalDate dataDate) {
        java.util.List<BatchExecuteRespDTO.Item> results = new java.util.ArrayList<>(metricCodes.size());
        int success = 0;
        int failed = 0;
        for (String code : metricCodes) {
            try {
                // cascade=false：批量场景走直算，避免大批量级联放大；version 内部解析
                RunTaskInfoDTO r = executeMetric(code, dataDate, Boolean.FALSE, null);
                results.add(BatchExecuteRespDTO.Item.builder()
                        .metricCode(code).status(r.getStatus()).runTaskId(r.getTaskId()).build());
                success++;
            } catch (Exception ex) {
                String msg = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                if (msg.length() > 500) {
                    msg = msg.substring(0, 500);
                }
                log.warn("[MetricLifecycleFacade.batchExecute] 指标 {} 执行失败: {}", code, msg);
                results.add(BatchExecuteRespDTO.Item.builder()
                        .metricCode(code).status("FAILED").errorMsg(msg).build());
                failed++;
            }
        }
        return BatchExecuteRespDTO.builder()
                .total(metricCodes.size()).success(success).failed(failed).results(results).build();
    }
```

- [ ] **Step 4: 运行确认通过**

Run: `mvn -q -pl performance-engine-center -Dtest=MetricLifecycleFacadeBatchExecuteTest test`
Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/facade/MetricLifecycleFacade.java \
        performance-engine-center/src/test/java/com/bank/branch/platform/performance/facade/MetricLifecycleFacadeBatchExecuteTest.java
git commit -m "feat(perf): 批量执行 Facade.batchExecute best-effort 聚合 + 单测"
```

---

## Task 6: Controller POST /metrics/batch-execute

**Files:**
- Modify: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/controller/MetricDefController.java`
- Test: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/controller/MetricBatchExecuteControllerIT.java`

**Interfaces:**
- Consumes: `MetricLifecycleFacade.batchExecute`（Task 5）；`CurrentUserApi`（既有注入）。
- Produces: `POST /api/perf/metrics/batch-execute`（body `BatchExecuteReqDTO`）→ `ResponseWrapper<BatchExecuteRespDTO>`。资源 `P_PERF_MTR_BEXEC`。

- [ ] **Step 1: 写端点**

在 `MetricDefController` 中新增（import 补 `AuditLog`（已有）、`BatchExecuteReqDTO`、`BatchExecuteRespDTO`）：

```java
    /**
     * 指标批量执行（高危）：对所选指标按同一数据日期逐个立即执行，best-effort 聚合返回.
     *
     * <p>整批一条审计（reason 必填）；实际逐指标写各自 PERF_RUN_TASK 行。
     */
    @PostMapping("/batch-execute")
    @Operation(summary = "指标批量执行（高危，需 reason）")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.EXECUTE)
    @AuditLog(action = "METRIC_BATCH_EXECUTE", resourceType = "PERF_RUN_TASK", reasonRequired = true)
    public ResponseWrapper<BatchExecuteRespDTO> batchExecute(@Valid @RequestBody BatchExecuteReqDTO req) {
        String operator = currentUserApi.getCurrentEmpId();
        log.info("[MetricDefController.batchExecute] operator={}, metricCodes={}, dataDate={}, reason={}",
                operator, req.getMetricCodes(), req.getDataDate(), req.getReason());
        return ResponseWrapper.success(
                metricLifecycleFacade.batchExecute(req.getMetricCodes(), req.getDataDate()));
    }
```

- [ ] **Step 2: 写失败的 Controller IT**

```java
package com.bank.branch.platform.performance.controller;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** POST /api/perf/metrics/batch-execute 鉴权 + 入参校验 IT（参照既有 perf ControllerIT）. */
class MetricBatchExecuteControllerIT extends AbstractPerfControllerIT {

    @Test
    void batchExecute_emptyMetricCodes_returns400() throws Exception {
        loginAsAdmin();
        mockMvc.perform(post("/api/perf/metrics/batch-execute")
                        .contentType("application/json")
                        .content("{\"metricCodes\":[],\"dataDate\":\"2026-07-01\",\"reason\":\"批量\"}")
                        .session(session))
                .andExpect(status().isBadRequest());
    }

    @Test
    void batchExecute_missingReason_returns400() throws Exception {
        loginAsAdmin();
        mockMvc.perform(post("/api/perf/metrics/batch-execute")
                        .contentType("application/json")
                        .content("{\"metricCodes\":[\"M_1\"],\"dataDate\":\"2026-07-01\"}")
                        .session(session))
                .andExpect(status().isBadRequest());
    }

    @Test
    void batchExecute_anonymous_returns401() throws Exception {
        mockMvc.perform(post("/api/perf/metrics/batch-execute")
                        .contentType("application/json")
                        .content("{\"metricCodes\":[\"M_1\"],\"dataDate\":\"2026-07-01\",\"reason\":\"r\"}"))
                .andExpect(status().isUnauthorized());
    }
}
```

> `extends`/`loginAsAdmin()`/`session` 同 Task 3：对齐模块既有 perf `*ControllerIT` 基类。400 由 Spring 校验（`@NotEmpty/@NotBlank`）触发。

- [ ] **Step 3: 运行确认失败 → 补齐 → 通过**

Run: `mvn -q -pl performance-engine-center -Dit.test=MetricBatchExecuteControllerIT verify`
Expected: 先失败 → 端点 + IT 基类对齐后 PASS。

- [ ] **Step 4: 全模块回归编译/单测**

Run: `mvn -q -pl performance-engine-center test`
Expected: 既有 surefire 全绿（新增单测通过，无回归）。

- [ ] **Step 5: Commit**

```bash
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/controller/MetricDefController.java \
        performance-engine-center/src/test/java/com/bank/branch/platform/performance/controller/MetricBatchExecuteControllerIT.java
git commit -m "feat(perf): POST /metrics/batch-execute 端点 + IT"
```

---

## Task 7: 字典 + 资源 + 角色绑定 种子（落 yiti 库）

**Files:**
- Create: `docs/superpowers/sql/2026-07-15-perf-task-monitor-dict-and-resources.sql`

**Interfaces:**
- Produces: `SYS_DICT` 一行（`PERF_TASK_TYPE / METRIC_RECALC / 指标重算`）；`PT_RESOURCE` 两行（`P_PERF_RT_SUM`、`P_PERF_MTR_BEXEC`）+ 对应 `PT_ROLE_RESOURCE` 绑定。

- [ ] **Step 1: 备份 PT_* 与 SYS_DICT（破坏性 DML 前必备份）**

```bash
mysqldump -uroot -pdjdev --default-character-set=utf8mb4 --no-tablespaces \
  yiti PT_RESOURCE PT_ROLE_RESOURCE SYS_DICT \
  > docs/superpowers/sql/backup/2026-07-15-yiti-before-task-monitor-dict-res.sql
```

- [ ] **Step 2: 写种子脚本**

```sql
-- =====================================================================
-- 指标重算任务监控：任务类型字典 + 汇总/批量执行资源 + 角色绑定
-- 幂等：先删同键再插；yiti(dev) 执行。
-- =====================================================================

-- 1) 任务类型字典（DictFacade 从 SYS_DICT 读；前端 /sys/dicts/PERF_TASK_TYPE/items）
DELETE FROM SYS_DICT WHERE dict_type = 'PERF_TASK_TYPE';
INSERT INTO SYS_DICT
  (id, dict_type, dict_code, dict_label, dict_value, sort_order, status, remark, created_by, created_time)
VALUES
  ('DICT_PERF_TASK_TYPE_RECALC', 'PERF_TASK_TYPE', 'METRIC_RECALC', '指标重算', 'METRIC_RECALC',
   1, 'ACTIVE', '任务监控-任务类型', 'seed', NOW());

-- 2) 资源：分组汇总（只读 LIST）+ 批量执行（高危 EXECUTE）
DELETE FROM PT_ROLE_RESOURCE WHERE RESOURCE_ID IN ('P_PERF_RT_SUM', 'P_PERF_MTR_BEXEC');
DELETE FROM PT_RESOURCE      WHERE RESOURCE_ID IN ('P_PERF_RT_SUM', 'P_PERF_MTR_BEXEC');

INSERT INTO PT_RESOURCE
  (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, MENU_NAME, MENU_ICON_URL, MENU_RANK_NO,
   ISMENU, MENU_ENDFLAG, PARENT_RESOURCE_ID, STATUS, SYS_CODE, CREATE_TIME, CREATE_USER, REMARK)
VALUES
  ('P_PERF_RT_SUM', '/api/perf/run-tasks/metric-summary', 'GET', '任务监控-指标汇总', NULL, 0,
   0, '0', 'M_PERF_METRICS', 0, 'PERF', NOW(), 'seed', '任务监控-按指标分组汇总'),
  ('P_PERF_MTR_BEXEC', '/api/perf/metrics/batch-execute', 'POST', '指标批量执行', NULL, 0,
   0, '0', 'M_PERF_METRICS', 0, 'PERF', NOW(), 'seed', '任务监控-批量执行(高危)');

-- 3) 角色绑定：
--    汇总资源 → 复用 P_PERF_RT_LIST（能看任务列表的角色都能看汇总）
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME)
  SELECT CONCAT('RRPRTS_', ROLE_ID), ROLE_ID, 'P_PERF_RT_SUM', 'PLATFORM', NOW()
    FROM PT_ROLE_RESOURCE WHERE RESOURCE_ID = 'P_PERF_RT_LIST';
--    批量执行资源 → 复用 P_PERF_MTR_EXEC（能单执行的角色才能批量执行）
INSERT INTO PT_ROLE_RESOURCE (ID, ROLE_ID, RESOURCE_ID, SYS_CODE, CREATE_TIME)
  SELECT CONCAT('RRPMBX_', ROLE_ID), ROLE_ID, 'P_PERF_MTR_BEXEC', 'PLATFORM', NOW()
    FROM PT_ROLE_RESOURCE WHERE RESOURCE_ID = 'P_PERF_MTR_EXEC';
```

- [ ] **Step 3: 执行到 yiti 库**

Run: `mysql -uroot -pdjdev --default-character-set=utf8mb4 yiti < docs/superpowers/sql/2026-07-15-perf-task-monitor-dict-and-resources.sql`

- [ ] **Step 4: 验证种子落库**

Run:
```bash
mysql -uroot -pdjdev -N -e "SELECT dict_type,dict_code,dict_label FROM yiti.SYS_DICT WHERE dict_type='PERF_TASK_TYPE';
SELECT RESOURCE_ID,RESOURCE_URL,RESOURCE_METHOD FROM yiti.PT_RESOURCE WHERE RESOURCE_ID IN ('P_PERF_RT_SUM','P_PERF_MTR_BEXEC');
SELECT RESOURCE_ID,COUNT(*) FROM yiti.PT_ROLE_RESOURCE WHERE RESOURCE_ID IN ('P_PERF_RT_SUM','P_PERF_MTR_BEXEC') GROUP BY RESOURCE_ID;"
```
Expected: 字典 1 行；资源 2 行；两个资源各有 ≥1 条角色绑定（数量与 P_PERF_RT_LIST / P_PERF_MTR_EXEC 的绑定角色数一致）。

- [ ] **Step 5: Commit**

```bash
git add docs/superpowers/sql/2026-07-15-perf-task-monitor-dict-and-resources.sql \
        docs/superpowers/sql/backup/2026-07-15-yiti-before-task-monitor-dict-res.sql
git commit -m "chore(perf): 任务监控 任务类型字典 + 汇总/批量执行资源与角色绑定种子"
```

---

## Task 8: 前端 api/perf.js 新增两个接口

**Files:**
- Modify: `xanzc_frontend/src/api/perf.js`

**Interfaces:**
- Produces: `listMetricSummary(params) → Promise<{records,total}>`；`batchExecuteMetrics(payload) → Promise<{total,success,failed,results}>`。

- [ ] **Step 1: 新增接口函数**

在 `perf.js` 里 `// PerfRunTask:` 注释所在的区块（`listRunTasks` 附近，约 421 行后）追加：

```javascript
// 任务监控：按指标分组汇总（列表数据源）
// params: taskType(默认 METRIC_RUN) / metricKeyword / pageNo / pageSize
export function listMetricSummary(params = {}) {
  return call('get', '/perf/run-tasks/metric-summary', { params }, { records: [], total: 0 }).then(unwrapPage);
}

// 任务监控：批量执行（POST /perf/metrics/batch-execute）
// payload: { metricCodes: string[], dataDate: 'YYYY-MM-DD', reason }
export function batchExecuteMetrics(payload) {
  return call('post', '/perf/metrics/batch-execute', { data: payload }, { total: 0, success: 0, failed: 0, results: [] });
}
```

- [ ] **Step 2: 构建校验（编译通过）**

Run: `cd xanzc_frontend && npm run build`
Expected: 构建成功（无语法/导入错误）。

- [ ] **Step 3: Commit**

```bash
git add xanzc_frontend/src/api/perf.js
git commit -m "feat(perf-fe): api 新增 listMetricSummary / batchExecuteMetrics"
```

---

## Task 9: 重写 TaskMonitor.vue（分组列表 + 新增/执行 + 批量执行 + 历史抽屉）

**Files:**
- Rewrite: `xanzc_frontend/src/views/perf/TaskMonitor.vue`

**Interfaces:**
- Consumes: `listMetricSummary`、`batchExecuteMetrics`（Task 8）、`executeMetric`、`listMetrics`（既有 `@/api/perf`）、`listDictItems`（`@/api/system`）。

- [ ] **Step 1: 用完整内容覆盖 `TaskMonitor.vue`**

```vue
<template>
  <div>
    <div class="page-h">
      <h1>任务监控 <span class="sub">指标重算任务 · 按指标汇总 / 执行 / 历史</span></h1>
      <div class="actions">
        <el-button @click="reload">刷新</el-button>
        <el-button type="primary" @click="openAdd">新增</el-button>
        <el-button type="warning" :disabled="selected.length === 0" @click="openBatch">
          批量执行{{ selected.length ? `（${selected.length}）` : '' }}
        </el-button>
      </div>
    </div>

    <!-- 过滤：任务类型（字典）+ 指标关键字 -->
    <div class="card-section">
      <el-form :inline="true" size="default">
        <el-form-item label="任务类型">
          <el-select v-model="query.taskType" style="width:160px">
            <el-option v-for="t in taskTypes" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="指标">
          <el-input v-model="query.keyword" clearable placeholder="指标编码/名称" style="width:200px"
            @keyup.enter="onSearch" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="onReset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="card-section table">
      <el-table :data="rows" size="default" v-loading="loading" empty-text="暂无任务记录"
        @selection-change="onSelectionChange" row-key="metricCode">
        <el-table-column type="selection" width="46" reserve-selection />
        <el-table-column label="指标" min-width="280" show-overflow-tooltip>
          <template #default="{row}">
            <code class="mono">{{ row.metricCode || '-' }}</code>
            <span v-if="row.metricName" class="sub-name"> · {{ row.metricName }}</span>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="180">
          <template #default="{row}">{{ fmtTime(row.firstCreatedTime) }}</template>
        </el-table-column>
        <el-table-column label="计算次数" width="110" align="right">
          <template #default="{row}">{{ row.runCount ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{row}">
            <el-button link type="primary" size="small" @click="openExecute(row)">执行</el-button>
            <el-button link type="primary" size="small" @click="openHistory(row)">历史</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination
          v-model:current-page="pageNo"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[20, 50, 100]"
          background
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="reload"
          @size-change="() => { pageNo = 1; reload(); }"
        />
      </div>
    </div>

    <!-- 新增 / 执行 共用对话框 -->
    <el-dialog v-model="execDlg.show" :title="execDlg.lockMetric ? '执行' : '新增'"
      width="520px" :close-on-click-modal="false">
      <el-form label-width="100px">
        <el-form-item label="任务类型">
          <el-select v-model="execDlg.taskType" :disabled="execDlg.lockMetric" style="width:100%">
            <el-option v-for="t in taskTypes" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="指标" required>
          <el-input v-if="execDlg.lockMetric" :model-value="execDlg.metricCode" readonly />
          <el-select v-else v-model="execDlg.metricCode" filterable clearable
            placeholder="输入编码/名称模糊搜索" style="width:100%">
            <el-option v-for="m in metricOptions" :key="m.metricCode"
              :label="`${m.metricCode} · ${m.metricName || ''}`" :value="m.metricCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="执行时间" required>
          <el-date-picker v-model="execDlg.dataDate" type="date" value-format="YYYY-MM-DD"
            placeholder="选择数据日期（不能大于今天）" :disabled-date="disabledFuture" style="width:100%" />
        </el-form-item>
        <el-form-item label="原因" required>
          <el-input v-model="execDlg.reason" type="textarea" :rows="3" placeholder="高危操作，必填原因" />
        </el-form-item>
        <div class="audit-hint">⚠ 将写入 run_task 并记入审计日志</div>
      </el-form>
      <template #footer>
        <el-button @click="execDlg.show = false">取消</el-button>
        <el-button type="primary" :loading="execDlg.submitting" @click="confirmExecute">确认执行</el-button>
      </template>
    </el-dialog>

    <!-- 批量执行对话框 -->
    <el-dialog v-model="batchDlg.show" title="批量执行" width="520px" :close-on-click-modal="false">
      <el-form label-width="100px">
        <el-form-item label="已选指标">
          <span>{{ batchDlg.metricCodes.length }} 个：{{ batchDlg.metricCodes.join('、') }}</span>
        </el-form-item>
        <el-form-item label="数据日期" required>
          <el-date-picker v-model="batchDlg.dataDate" type="date" value-format="YYYY-MM-DD"
            placeholder="选择数据日期（不能大于今天）" :disabled-date="disabledFuture" style="width:100%" />
        </el-form-item>
        <el-form-item label="原因" required>
          <el-input v-model="batchDlg.reason" type="textarea" :rows="3" placeholder="一条原因套用整批，高危必填" />
        </el-form-item>
        <div class="audit-hint">⚠ 逐指标同步执行，整批记入一条审计</div>
      </el-form>
      <template #footer>
        <el-button @click="batchDlg.show = false">取消</el-button>
        <el-button type="primary" :loading="batchDlg.submitting" @click="confirmBatch">确认执行</el-button>
      </template>
    </el-dialog>

    <!-- 历史抽屉（右侧）-->
    <el-drawer v-model="hist.show" :title="`执行历史 · ${hist.metricCode || ''}`" size="52%"
      :destroy-on-close="true">
      <el-table :data="hist.rows" size="default" v-loading="hist.loading" empty-text="暂无执行记录">
        <el-table-column label="执行时间" width="170">
          <template #default="{row}">{{ fmtTime(row.createdTime) }}</template>
        </el-table-column>
        <el-table-column label="开始时间" width="170">
          <template #default="{row}">{{ fmtTime(row.startTime) }}</template>
        </el-table-column>
        <el-table-column label="结束时间" width="170">
          <template #default="{row}">{{ fmtTime(row.endTime) }}</template>
        </el-table-column>
        <el-table-column label="计算结果" min-width="160">
          <template #default="{row}">
            <el-tag :class="statusCls(row.status)" effect="plain" size="small">{{ statusLabel(row.status) }}</el-tag>
            <div v-if="row.errorMsg" class="err-inline" :title="row.errorMsg">{{ row.errorMsg }}</div>
          </template>
        </el-table-column>
        <el-table-column label="发起人" width="150">
          <template #default="{row}">{{ row.startedByName || row.startedBy || '-' }}</template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination
          v-model:current-page="hist.pageNo"
          v-model:page-size="hist.pageSize"
          :total="hist.total"
          :page-sizes="[20, 50]"
          background
          layout="total, sizes, prev, pager, next"
          @current-change="loadHistory"
          @size-change="() => { hist.pageNo = 1; loadHistory(); }"
        />
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { listMetricSummary, batchExecuteMetrics, executeMetric, listMetrics } from '@/api/perf';
import { listDictItems } from '@/api/system';

// 状态字典 + badge 配色
const STATUS_MAP = {
  PENDING: { label: '待执行', cls: 'tag-info' },
  RUNNING: { label: '执行中', cls: 'tag-warning' },
  SUCCESS: { label: '完成', cls: 'tag-success' },
  PARTIAL: { label: '部分成功', cls: 'tag-warning' },
  PARTIAL_FAILED: { label: '部分失败', cls: 'tag-warning' },
  FAILED: { label: '失败', cls: 'tag-danger' },
  CANCELLED: { label: '已取消', cls: 'tag-info' }
};
const statusCls = (s) => STATUS_MAP[s]?.cls || 'tag-info';
const statusLabel = (s) => STATUS_MAP[s]?.label || s || '-';

function fmtTime(t) {
  if (!t) return '-';
  return String(t).replace('T', ' ').slice(0, 19);
}
const today = () => new Date().toISOString().slice(0, 10);
const disabledFuture = (d) => { const t = new Date(); t.setHours(0, 0, 0, 0); return d.getTime() > t.getTime(); };

// 任务类型字典（默认兜底一项，字典拉到后覆盖）
const taskTypes = ref([{ value: 'METRIC_RECALC', label: '指标重算' }]);
// 指标类型 → 后端 task_type 的映射（目前仅指标重算 → METRIC_RUN 日志类型）
const TASK_TYPE_TO_BACKEND = { METRIC_RECALC: 'METRIC_RUN' };

// 过滤 + 列表
const query = reactive({ taskType: 'METRIC_RECALC', keyword: '' });
const rows = ref([]);
const total = ref(0);
const loading = ref(false);
const pageNo = ref(1);
const pageSize = ref(20);
const selected = ref([]);

function onSelectionChange(sel) { selected.value = sel; }

async function reload() {
  loading.value = true;
  try {
    const r = await listMetricSummary({
      taskType: TASK_TYPE_TO_BACKEND[query.taskType] || 'METRIC_RUN',
      metricKeyword: query.keyword?.trim() || undefined,
      pageNo: pageNo.value,
      pageSize: pageSize.value
    });
    rows.value = r?.records || [];
    total.value = r?.total ?? rows.value.length;
  } catch { /* http 拦截器已提示 */ } finally { loading.value = false; }
}
function onSearch() { pageNo.value = 1; reload(); }
function onReset() { query.taskType = 'METRIC_RECALC'; query.keyword = ''; pageNo.value = 1; reload(); }

// 指标下拉（新增用）
const metricOptions = ref([]);
async function loadMetricOptions() {
  try {
    const list = await listMetrics();
    metricOptions.value = (Array.isArray(list) ? list : []).map(m => ({
      metricCode: m.metricCode, metricName: m.metricName
    }));
  } catch { metricOptions.value = []; }
}

// 新增 / 执行 对话框
const execDlg = reactive({ show: false, lockMetric: false, taskType: 'METRIC_RECALC',
  metricCode: '', dataDate: '', reason: '', submitting: false });

function openAdd() {
  Object.assign(execDlg, { show: true, lockMetric: false, taskType: 'METRIC_RECALC',
    metricCode: '', dataDate: '', reason: '', submitting: false });
}
function openExecute(row) {
  Object.assign(execDlg, { show: true, lockMetric: true, taskType: 'METRIC_RECALC',
    metricCode: row.metricCode, dataDate: '', reason: '', submitting: false });
}
async function confirmExecute() {
  if (!execDlg.metricCode) return ElMessage.warning('请选择指标');
  if (!execDlg.dataDate) return ElMessage.warning('执行时间必填');
  if (execDlg.dataDate > today()) return ElMessage.warning(`执行时间不能大于今天（${today()}）`);
  if (!execDlg.reason || !execDlg.reason.trim()) return ElMessage.warning('原因必填');
  execDlg.submitting = true;
  try {
    await executeMetric(execDlg.metricCode, {
      dataDate: execDlg.dataDate, cascade: true, async: true, reason: execDlg.reason.trim()
    });
    ElMessage.success('已触发执行，已记入审计');
    execDlg.show = false;
    reload();
  } catch { /* executeMetric 内部已提示 */ } finally { execDlg.submitting = false; }
}

// 批量执行对话框
const batchDlg = reactive({ show: false, metricCodes: [], dataDate: '', reason: '', submitting: false });
function openBatch() {
  batchDlg.metricCodes = selected.value.map(r => r.metricCode);
  batchDlg.dataDate = ''; batchDlg.reason = ''; batchDlg.submitting = false; batchDlg.show = true;
}
async function confirmBatch() {
  if (!batchDlg.metricCodes.length) return ElMessage.warning('未选择指标');
  if (!batchDlg.dataDate) return ElMessage.warning('数据日期必填');
  if (batchDlg.dataDate > today()) return ElMessage.warning(`数据日期不能大于今天（${today()}）`);
  if (!batchDlg.reason || !batchDlg.reason.trim()) return ElMessage.warning('原因必填');
  batchDlg.submitting = true;
  try {
    const r = await batchExecuteMetrics({
      metricCodes: batchDlg.metricCodes, dataDate: batchDlg.dataDate, reason: batchDlg.reason.trim()
    });
    ElMessage.success(`批量执行完成：成功 ${r?.success ?? 0} / 失败 ${r?.failed ?? 0}`);
    if (r?.failed) {
      const bad = (r.results || []).filter(x => x.status === 'FAILED')
        .map(x => `${x.metricCode}: ${x.errorMsg || ''}`).join('；');
      if (bad) ElMessage.warning(`失败明细：${bad}`);
    }
    batchDlg.show = false;
    reload();
  } catch { /* 拦截器已提示 */ } finally { batchDlg.submitting = false; }
}

// 历史抽屉
const hist = reactive({ show: false, metricCode: '', rows: [], total: 0, loading: false, pageNo: 1, pageSize: 20 });
function openHistory(row) {
  hist.metricCode = row.metricCode; hist.pageNo = 1; hist.show = true;
  loadHistory();
}
async function loadHistory() {
  hist.loading = true;
  try {
    // 复用既有 GET /perf/run-tasks?taskKey=；避免循环依赖动态引入
    const { listRunTasks } = await import('@/api/perf');
    const r = await listRunTasks({
      taskType: 'METRIC_RUN', taskKey: hist.metricCode,
      pageNo: hist.pageNo, pageSize: hist.pageSize
    });
    hist.rows = r?.records || [];
    hist.total = r?.total ?? hist.rows.length;
  } catch { hist.rows = []; hist.total = 0; } finally { hist.loading = false; }
}

onMounted(async () => {
  // 加载任务类型字典（失败保留兜底项）
  try {
    const items = await listDictItems('PERF_TASK_TYPE');
    if (Array.isArray(items) && items.length) {
      taskTypes.value = items.map(it => ({ value: it.dictValue || it.dictCode, label: it.dictLabel }));
      query.taskType = taskTypes.value[0].value;
    }
  } catch { /* 保留兜底 */ }
  loadMetricOptions();
  reload();
});
</script>

<style lang="scss" scoped>
.page-h h1 .sub { font-size: 13px; color: $text-3; margin-left: 12px; font-weight: 400; }
.table { padding: 0; padding-bottom: 12px; }
.pager { display: flex; justify-content: flex-end; padding: 12px 14px; }
.pager :deep(.el-pagination) { flex-wrap: wrap; row-gap: 8px; justify-content: flex-end; }
.mono { font-family: ui-monospace, monospace; font-size: 12px; background: $bg-soft; padding: 2px 6px; border-radius: 3px; }
.sub-name { color: $text-2; font-size: 13px; }
.audit-hint { font-size: 12px; color: #999; margin-left: 100px; }
.err-inline { color: #991b1b; font-size: 12px; margin-top: 4px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
</style>
```

- [ ] **Step 2: 构建校验**

Run: `cd xanzc_frontend && npm run build`
Expected: 构建成功。

- [ ] **Step 3: 手动烟测（开发服务器）**

Run: `cd xanzc_frontend && npm run dev`（另需 yiti 后端 18081 在跑）。浏览器进 `/#/perf/task-monitor`：
- 列表按指标显示 指标/创建时间/计算次数；任务类型下拉出现"指标重算"。
- 勾选多行 → "批量执行"可点 → 弹窗填数据日期+原因 → 提示"成功 X / 失败 Y"。
- 行"执行" → 弹窗指标锁定 + 数据日期 + 原因 → 提示已触发 → 计算次数 +1。
- "新增" → 指标下拉可模糊搜索 → 执行成功后该指标进入列表。
- 行"历史" → 右侧抽屉列出该指标历次执行（执行/开始/结束/结果/发起人）。

- [ ] **Step 4: Commit**

```bash
git add xanzc_frontend/src/views/perf/TaskMonitor.vue
git commit -m "feat(perf-fe): 任务监控页重写-分组列表+新增/执行/批量执行/历史抽屉"
```

---

## Task 10: 端到端回归

**Files:** 无（验证任务）

- [ ] **Step 1: 后端全模块回归**

Run: `mvn -q clean install -DskipTests && mvn -q -pl performance-engine-center verify`
Expected: 新增 surefire + failsafe 全绿；无既有回归（既有本地已知红项见模块 CLAUDE.md，不因本次新增）。

- [ ] **Step 2: 前端构建**

Run: `cd xanzc_frontend && npm run build`
Expected: 成功。

- [ ] **Step 3: 联调冒烟（按 Task 9 Step 3 清单）**

确认四个动作端到端可用，且指标库"立即执行"产生的记录出现在监控汇总（同指标计算次数 +1、历史新增一条）。

- [ ] **Step 4: 文档回填**

在 `performance-engine-center/CLAUDE.md` 版本变更日志追加一条（日期 2026-07-15）：任务监控页新增/执行/批量执行/历史，复用 PERF_RUN_TASK 按 task_key 分组，新增端点 `GET /run-tasks/metric-summary`、`POST /metrics/batch-execute`，资源 `P_PERF_RT_SUM`/`P_PERF_MTR_BEXEC`，字典 `PERF_TASK_TYPE`。并顺带勘误 `uk_task_key（已应用）` → 实际不存在。

```bash
git add performance-engine-center/CLAUDE.md
git commit -m "docs(perf): 任务监控 新增/执行/批量执行/历史 变更日志 + uk_task_key 勘误"
```

---

## Self-Review 检查记录

- **Spec 覆盖**：列表分组(Task1-3) / 新增执行(Task9 复用 executeMetric) / 批量执行(Task4-6,9) / 历史抽屉(Task9) / 字典+资源(Task7) / 前端 api(Task8) —— 全覆盖。
- **Placeholder**：控制器 IT 基类名以"参照既有 *ControllerIT"标注（模块基类名需执行时对齐，已给出替换说明），非空泛占位；其余步骤均含完整代码。
- **类型一致**：`MetricSummaryDTO`(metricCode/metricName/firstCreatedTime/runCount)、`BatchExecuteReqDTO`/`RespDTO.Item`、`selectMetricSummary/countMetricSummary`、`pageMetricSummary`、`batchExecute(List,LocalDate)` 在定义与调用处签名一致；前端 `listMetricSummary/batchExecuteMetrics` 与后端路径/字段一致。
- **风险**：控制器 IT 基类/登录辅助名是唯一需执行期确认项；`SYS_DICT` 读取路径已按 `DictFacade.toItemDTO(SysDict)` 核实。
