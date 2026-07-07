# 工作台办理跳转「待我审批」+ 按条件查询 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use subagent-driven-development (recommended) or executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让工作台「办理」按钮一步跳到「待我审批」tab 并自动弹审批；同时给「待我审批」加 4 字段筛选 + 后端分页。

**Architecture:** 方案 E —— workflow-center 新增 `TodoQueryApi` 暴露 businessKey + TaskRespDTO 反查；perf-engine-center 新增 `/api/perf/adjusts/my-todos` 用业务字段过滤分页；前端 Adjust.vue todo tab 切到新接口 + 加表单 + 加分页 + mounted 时按 query.tab/taskId 自动弹审批。

**Tech Stack:** Spring Boot 3.2.3 / MyBatis Plus / Flowable 7.0.1 / Vue 3 / Element Plus / 多模块 maven

---

## §0 字段映射与设计决策

**spec → 实体字段映射**（spec 写法 → 实际代码字段）：
- `applyId` → `id`（`PerfAllocAdjustApply.id`，字符串业务 ID）
- `custNo` → `custId`（实体字段名是 `custId`）
- `custName / ownerOrgName / createdByName` → **本期不返回**（前端 todo 列不显示 name，YAGNI；如未来要显示通过 OrgApi/UserApi 后置 join）

**复用 TaskRespDTO 而非新建 TaskMetaDTO**：workflow 已有 `TaskRespDTO`（含 taskId/nodeKey/taskName/claimable/title/startUser/startUserName/startOrgId/startOrgName/startTime/taskCreateTime/slaStatus/businessKey 等）满足前端列展示需要，新建 TaskMetaDTO 重复。**spec §5.1 的 TaskMetaDTO 实际由 TaskRespDTO 替代**。

**DTO 结构**：`AdjustTodoRespDTO` 用**扁平**结构（业务字段 + workflow 字段 merge 到一层），前端列模板兼容现有 `row.title / row.startUserName / row.taskCreateTime` 等访问路径，不需要重写。

**跨仓库分工**：
- **lf/yiti**：所有后端改动（workflow + perf）
- **wangyq/yiti**：所有前端改动（xanzc_frontend）
- 后端先 implement → mvn install 启动 18080 → 前端切接口验证 → 全部 commit（不主动 push）

---

## §1 文件清单

### 新建（lf/yiti 后端）

```
workflow-center/src/main/java/com/bank/branch/platform/workflow/
└── api/TodoQueryApi.java                       (新, 2 方法)

workflow-center/src/main/java/com/bank/branch/platform/workflow/facade/
└── TodoQueryFacade.java                        (新, 实现 TodoQueryApi)

workflow-center/src/test/java/com/bank/branch/platform/workflow/facade/
└── TodoQueryFacadeTest.java                    (新, W1/W2)

performance-engine-center/src/main/java/com/bank/branch/platform/performance/
├── controller/AllocAdjustTodoController.java   (新, GET /api/perf/adjusts/my-todos)
├── controller/dto/AdjustTodoRespDTO.java       (新)
├── service/adjust/AllocAdjustTodoService.java  (新)
└── mapper/PerfAllocAdjustTodoMapper.java       (新, 2 方法 count + select)

performance-engine-center/src/main/resources/mapper/performance/
└── PerfAllocAdjustTodoMapper.xml               (新, count + select)

performance-engine-center/src/test/java/com/bank/branch/platform/performance/service/adjust/
└── AllocAdjustTodoServiceTest.java             (新, T1-T8)
```

### 修改（wangyq/yiti 前端）

```
xanzc_frontend/src/api/perf.js                  (加 listMyAdjustTodos wrapper)
xanzc_frontend/src/views/workspace/Index.vue    (goHandle 加 tab=todo)
xanzc_frontend/src/views/perf/Adjust.vue        (todo tab 加表单+分页+自动弹审批)
```

### 数据库

**无 ALTER TABLE / 无新表**（用户红线，spec §3.2 确认）。

### PT_RESOURCE 登记

新接口 `GET /api/perf/adjusts/my-todos` 需登记到 PT_RESOURCE：
- url=`/api/perf/adjusts/my-todos`, method=`GET`, biz_type=`ALLOC_ADJUST`, biz_action=`READ`, is_menu=`0`, resource_id=`R_PERF_ADJ_TODO_LIST`（手工 INSERT，见 Task 6 步 6）

---

## §2 任务列表

### Task 1: workflow-center 暴露 TodoQueryApi

**Files:**
- Create: `workflow-center/src/main/java/com/bank/branch/platform/workflow/api/TodoQueryApi.java`
- Create: `workflow-center/src/main/java/com/bank/branch/platform/workflow/facade/TodoQueryFacade.java`
- Create: `workflow-center/src/test/java/com/bank/branch/platform/workflow/facade/TodoQueryFacadeTest.java`

- [ ] **Step 1: 写 failing test（W1 listMyTodoBusinessKeys + W2 findTaskRespByBusinessKeys）**

文件：`workflow-center/src/test/java/com/bank/branch/platform/workflow/facade/TodoQueryFacadeTest.java`

```java
package com.bank.branch.platform.workflow.facade;

import com.bank.branch.platform.workflow.api.dto.TaskRespDTO;
import com.bank.branch.platform.workflow.service.TodoQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.flowable.engine.TaskService;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TodoQueryFacadeTest {

    @Mock TaskService taskService;
    @Mock TodoQueryService todoQueryService;

    @InjectMocks TodoQueryFacade facade;

    @Test
    void w1_listMyTodoBusinessKeys_returnsDistinctBusinessKeys() {
        TaskQuery q = mock(TaskQuery.class, org.mockito.Answers.RETURNS_SELF);
        when(taskService.createTaskQuery()).thenReturn(q);

        Task t1 = mock(Task.class);
        Task t2 = mock(Task.class);
        Task t3 = mock(Task.class);
        when(t1.getProcessInstanceBusinessKey()).thenReturn("ALLOC_ADJUST:A1");
        when(t2.getProcessInstanceBusinessKey()).thenReturn("ALLOC_ADJUST:A2");
        when(t3.getProcessInstanceBusinessKey()).thenReturn("ALLOC_ADJUST:A1"); // 重复
        when(q.list()).thenReturn(Arrays.asList(t1, t2, t3));

        List<String> keys = facade.listMyTodoBusinessKeys("E001", "ALLOC_ADJUST");

        assertThat(keys).containsExactlyInAnyOrder("ALLOC_ADJUST:A1", "ALLOC_ADJUST:A2");
    }

    @Test
    void w2_findTaskRespByBusinessKeys_partialMatchReturnsOnlyFound() {
        TaskRespDTO dto1 = new TaskRespDTO();
        dto1.setTaskId("T1");
        dto1.setBusinessKey("ALLOC_ADJUST:A1");
        TaskRespDTO dto2 = new TaskRespDTO();
        dto2.setTaskId("T2");
        dto2.setBusinessKey("ALLOC_ADJUST:A2");
        // 只命中 A1 + A2，A3 不存在
        when(todoQueryService.findMyTaskRespByBusinessKeys("E001",
                Arrays.asList("ALLOC_ADJUST:A1", "ALLOC_ADJUST:A2", "ALLOC_ADJUST:A3")))
                .thenReturn(Arrays.asList(dto1, dto2));

        Map<String, TaskRespDTO> map = facade.findTaskRespByBusinessKeys("E001",
                Arrays.asList("ALLOC_ADJUST:A1", "ALLOC_ADJUST:A2", "ALLOC_ADJUST:A3"));

        assertThat(map).hasSize(2);
        assertThat(map.get("ALLOC_ADJUST:A1").getTaskId()).isEqualTo("T1");
        assertThat(map.get("ALLOC_ADJUST:A2").getTaskId()).isEqualTo("T2");
        assertThat(map).doesNotContainKey("ALLOC_ADJUST:A3");
    }

    @Test
    void w1_empty_returnsEmpty() {
        TaskQuery q = mock(TaskQuery.class, org.mockito.Answers.RETURNS_SELF);
        when(taskService.createTaskQuery()).thenReturn(q);
        when(q.list()).thenReturn(java.util.Collections.emptyList());

        assertThat(facade.listMyTodoBusinessKeys("E001", "ALLOC_ADJUST")).isEmpty();
    }
}
```

- [ ] **Step 2: 跑测试确认 fail（class 还没建）**

```bash
cd /home/djdev/lf/yiti
mvn test -pl workflow-center -Dtest=TodoQueryFacadeTest
```
Expected: FAIL with "TodoQueryFacade not found" 或 compile error。

- [ ] **Step 3: 建 TodoQueryApi 接口**

文件：`workflow-center/src/main/java/com/bank/branch/platform/workflow/api/TodoQueryApi.java`

```java
package com.bank.branch.platform.workflow.api;

import com.bank.branch.platform.workflow.api.dto.TaskRespDTO;

import java.util.List;
import java.util.Map;

/**
 * 待办查询 API（供 perf 等业务模块按 businessKey 反查 task 元信息）。
 * <p>设计目的：业务模块需要按业务字段二次过滤待办，但不应直连 Flowable 表；
 * 本 API 提供 businessKey 列表 + 反向 join 能力，把"业务字段过滤"留给业务模块，
 * "Flowable 查询"留在 workflow-center。</p>
 */
public interface TodoQueryApi {

    /**
     * 查询某员工某 bizType 的所有待办 task 的 processInstanceBusinessKey。
     *
     * @param empId   员工 ID
     * @param bizType 业务类型，匹配 processInstanceBusinessKey 前缀（如 "ALLOC_ADJUST"）
     * @return businessKey 列表（去重，可能为空）
     */
    List<String> listMyTodoBusinessKeys(String empId, String bizType);

    /**
     * 按 businessKey 批量反查 TaskRespDTO（含 taskId/nodeKey/title/SLA 等完整 task 元信息）。
     * empId 用于鉴权：只返该员工候选或受理的 task，防越权。
     *
     * @param empId        员工 ID
     * @param businessKeys 待查的 businessKey 列表
     * @return 以 businessKey 为 key 的 Map（不命中的 key 在 Map 中缺失）
     */
    Map<String, TaskRespDTO> findTaskRespByBusinessKeys(String empId, List<String> businessKeys);
}
```

- [ ] **Step 4: 实现 TodoQueryFacade**

先看现有 `TodoQueryService.queryTodoList` 的 TaskQuery 链构造方式（同 facade 复用），第 5 步在 service 里加一个 `findMyTaskRespByBusinessKeys` 方法供 facade 调用。

文件：`workflow-center/src/main/java/com/bank/branch/platform/workflow/facade/TodoQueryFacade.java`

```java
package com.bank.branch.platform.workflow.facade;

import com.bank.branch.platform.workflow.api.TodoQueryApi;
import com.bank.branch.platform.workflow.api.dto.TaskRespDTO;
import com.bank.branch.platform.workflow.service.TodoQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * TodoQueryApi 默认实现：复用 TaskService 查 BusinessKey + 委托 TodoQueryService 反查 TaskRespDTO。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TodoQueryFacade implements TodoQueryApi {

    private final TaskService taskService;
    private final TodoQueryService todoQueryService;

    @Override
    public List<String> listMyTodoBusinessKeys(String empId, String bizType) {
        log.debug("[TodoQueryFacade.listMyTodoBusinessKeys] empId={}, bizType={}", empId, bizType);
        if (empId == null || bizType == null) {
            return Collections.emptyList();
        }
        // 与 TodoQueryService.queryTodoList 同样的过滤口径：候选 OR 受理 + bizType 前缀
        List<Task> tasks = taskService.createTaskQuery()
                .taskCandidateOrAssigned(empId)
                .processInstanceBusinessKeyLike(bizType + ":%")
                .list();
        return tasks.stream()
                .map(Task::getProcessInstanceBusinessKey)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, TaskRespDTO> findTaskRespByBusinessKeys(String empId, List<String> businessKeys) {
        log.debug("[TodoQueryFacade.findTaskRespByBusinessKeys] empId={}, keys={}", empId, businessKeys == null ? 0 : businessKeys.size());
        if (empId == null || businessKeys == null || businessKeys.isEmpty()) {
            return Collections.emptyMap();
        }
        List<TaskRespDTO> dtos = todoQueryService.findMyTaskRespByBusinessKeys(empId, businessKeys);
        return dtos.stream()
                .filter(d -> d.getBusinessKey() != null)
                .collect(Collectors.toMap(TaskRespDTO::getBusinessKey, d -> d, (a, b) -> a));
    }
}
```

- [ ] **Step 5: 在 TodoQueryService 加 findMyTaskRespByBusinessKeys 方法**

修改：`workflow-center/src/main/java/com/bank/branch/platform/workflow/service/TodoQueryService.java`

在 class 内追加 public 方法（建议放在 `queryTodoList` 方法附近）：

```java
/**
 * 按 businessKey 批量查询当前用户可处理的 TaskRespDTO（候选 OR 受理）。
 * <p>复用 queryTodoList 的 DTO 装配逻辑（拆出 mapToDto 私有方法可复用，本次直接 inline 转换）。
 * 仅返该员工有权处理的 task；businessKey 在 ACT_RU_TASK 不存在或不属于该员工时不返回。</p>
 *
 * @param empId        员工 ID
 * @param businessKeys 待查的 businessKey 列表
 * @return TaskRespDTO 列表（顺序按 Flowable 查询返回顺序，无序）
 */
public List<TaskRespDTO> findMyTaskRespByBusinessKeys(String empId, List<String> businessKeys) {
    if (empId == null || businessKeys == null || businessKeys.isEmpty()) {
        return java.util.Collections.emptyList();
    }
    // 用 IN 一次性查 Flowable Task 表，避免 N+1
    List<Task> tasks = taskService.createTaskQuery()
            .taskCandidateOrAssigned(empId)
            .processInstanceBusinessKeyIn(businessKeys)
            .list();
    // 直接复用现有私有方法 convertTaskToDTO（queryTodoList 已用，line 129）
    return tasks.stream()
            .map(this::convertTaskToDTO)
            .filter(java.util.Objects::nonNull)
            .collect(java.util.stream.Collectors.toList());
}
```

**注**：现有 `convertTaskToDTO(Task)` 是 private 方法，本类内调用无需改可见性。

- [ ] **Step 6: 跑测试确认 pass**

```bash
cd /home/djdev/lf/yiti
mvn test -pl workflow-center -Dtest=TodoQueryFacadeTest
```
Expected: PASS（3 个 test）。

- [ ] **Step 7: 跑整个 workflow-center 测试避免回归**

```bash
mvn test -pl workflow-center
```
Expected: 所有现有测试 PASS。

- [ ] **Step 8: commit**

```bash
cd /home/djdev/lf/yiti
git add workflow-center/
git commit -m "$(cat <<'EOF'
feat(workflow): 暴露 TodoQueryApi 供 perf 按 businessKey 反查 task

新增 2 个方法：
- listMyTodoBusinessKeys(empId, bizType) → 所有待办的 businessKey
- findTaskRespByBusinessKeys(empId, keys) → businessKey -> TaskRespDTO

用于业务模块（如 perf 业绩调整）按业务字段二次过滤待办，
保持「workflow 是唯一调用 Flowable 模块」边界。

EOF
)"
```

---

### Task 2: perf 新建 AdjustTodoRespDTO

**Files:**
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/controller/dto/AdjustTodoRespDTO.java`

- [ ] **Step 1: 建 DTO**

```java
package com.bank.branch.platform.performance.controller.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 「我的待审批 - 业绩调整」单条响应 DTO（扁平结构）。
 * <p>业务字段来自 PERF_ALLOC_ADJUST_APPLY，workflow 字段来自 TaskRespDTO，merge 到一层兼容前端现有列模板。</p>
 */
@Data
public class AdjustTodoRespDTO {

    // ===== 业务字段（PERF_ALLOC_ADJUST_APPLY） =====
    private String id;
    private String applyNo;
    private String custId;
    private String allocDim;
    private String bizKind;
    private String ownerOrgId;
    private String createdBy;
    private LocalDateTime createdTime;
    private String businessKey;

    // ===== workflow 字段（来自 TaskRespDTO） =====
    private String taskId;
    private String nodeKey;
    private String taskName;
    private String title;
    private Boolean claimable;
    private String startUser;
    private String startUserName;
    private String startOrgId;
    private String startOrgName;
    private LocalDateTime startTime;
    private LocalDateTime taskCreateTime;
    private String slaStatus;
}
```

- [ ] **Step 2: commit（无单测，纯 DTO）**

```bash
cd /home/djdev/lf/yiti
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/controller/dto/AdjustTodoRespDTO.java
git commit -m "$(cat <<'EOF'
feat(perf): 新增 AdjustTodoRespDTO（业务+workflow 字段扁平结构）

供 GET /api/perf/adjusts/my-todos 响应使用。

EOF
)"
```

---

### Task 3: perf 新建 PerfAllocAdjustTodoMapper + xml

**Files:**
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/mapper/PerfAllocAdjustTodoMapper.java`
- Create: `performance-engine-center/src/main/resources/mapper/performance/PerfAllocAdjustTodoMapper.xml`

- [ ] **Step 1: Mapper 接口**

```java
package com.bank.branch.platform.performance.mapper;

import com.bank.branch.platform.performance.entity.PerfAllocAdjustApply;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 按 businessKey IN 集合 + 业务字段过滤 + 分页查询业绩调整申请。
 * <p>用于「我的待审批」按 businessKey 批量取业务行。</p>
 */
@Mapper
public interface PerfAllocAdjustTodoMapper {

    /**
     * 按 applyIds IN 集合 + 业务字段过滤计数。
     */
    long countMyTodos(@Param("applyIds") List<String> applyIds,
                      @Param("keyword") String keyword,
                      @Param("allocDim") String allocDim,
                      @Param("bizKind") String bizKind,
                      @Param("dateFrom") LocalDateTime dateFrom,
                      @Param("dateToExclusive") LocalDateTime dateToExclusive);

    /**
     * 按 applyIds IN 集合 + 业务字段过滤 + 分页查询，按 created_time DESC。
     */
    List<PerfAllocAdjustApply> selectMyTodos(@Param("applyIds") List<String> applyIds,
                                             @Param("keyword") String keyword,
                                             @Param("allocDim") String allocDim,
                                             @Param("bizKind") String bizKind,
                                             @Param("dateFrom") LocalDateTime dateFrom,
                                             @Param("dateToExclusive") LocalDateTime dateToExclusive,
                                             @Param("offset") int offset,
                                             @Param("pageSize") int pageSize);
}
```

- [ ] **Step 2: Mapper xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.bank.branch.platform.performance.mapper.PerfAllocAdjustTodoMapper">

    <sql id="todoWhere">
        WHERE a.id IN
        <foreach collection="applyIds" item="id" open="(" separator="," close=")">
            #{id}
        </foreach>
        <if test="keyword != null and keyword != ''">
            AND (a.apply_no LIKE CONCAT('%', #{keyword}, '%')
                 OR a.cust_id LIKE CONCAT('%', #{keyword}, '%'))
        </if>
        <if test="allocDim != null and allocDim != ''">
            AND a.alloc_dim = #{allocDim}
        </if>
        <if test="bizKind != null and bizKind != ''">
            AND a.biz_kind = #{bizKind}
        </if>
        <if test="dateFrom != null">
            AND a.created_time &gt;= #{dateFrom}
        </if>
        <if test="dateToExclusive != null">
            AND a.created_time &lt; #{dateToExclusive}
        </if>
    </sql>

    <select id="countMyTodos" resultType="long">
        SELECT COUNT(1)
        FROM PERF_ALLOC_ADJUST_APPLY a
        <include refid="todoWhere"/>
    </select>

    <select id="selectMyTodos"
            resultType="com.bank.branch.platform.performance.entity.PerfAllocAdjustApply">
        SELECT a.id, a.apply_no AS applyNo, a.cust_id AS custId, a.alloc_dim AS allocDim,
               a.biz_kind AS bizKind, a.account_no AS accountNo, a.status,
               a.business_key AS businessKey, a.process_instance_id AS processInstanceId,
               a.owner_org_id AS ownerOrgId, a.remark,
               a.created_by AS createdBy, a.created_time AS createdTime,
               a.updated_by AS updatedBy, a.updated_time AS updatedTime
        FROM PERF_ALLOC_ADJUST_APPLY a
        <include refid="todoWhere"/>
        ORDER BY a.created_time DESC, a.id DESC
        LIMIT #{offset}, #{pageSize}
    </select>
</mapper>
```

- [ ] **Step 3: commit（mapper 单测留到 Service test 一起覆盖）**

```bash
cd /home/djdev/lf/yiti
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/mapper/PerfAllocAdjustTodoMapper.java \
        performance-engine-center/src/main/resources/mapper/performance/PerfAllocAdjustTodoMapper.xml
git commit -m "$(cat <<'EOF'
feat(perf): 新增 PerfAllocAdjustTodoMapper count + select 待审批分页查询

业务字段过滤：keyword(apply_no/cust_id) + allocDim + bizKind + dateRange。

EOF
)"
```

---

### Task 4: perf AllocAdjustTodoService（TDD, T1-T8）

**Files:**
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/adjust/AllocAdjustTodoService.java`
- Create: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/service/adjust/AllocAdjustTodoServiceTest.java`

- [ ] **Step 1: 写 failing test（T1-T8 全部一次性建）**

```java
package com.bank.branch.platform.performance.service.adjust;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.performance.controller.dto.AdjustTodoRespDTO;
import com.bank.branch.platform.performance.entity.PerfAllocAdjustApply;
import com.bank.branch.platform.performance.mapper.PerfAllocAdjustTodoMapper;
import com.bank.branch.platform.workflow.api.TodoQueryApi;
import com.bank.branch.platform.workflow.api.dto.TaskRespDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AllocAdjustTodoServiceTest {

    @Mock TodoQueryApi workflowApi;
    @Mock PerfAllocAdjustTodoMapper mapper;

    @InjectMocks AllocAdjustTodoService service;

    private static final String EMP = "E001";
    private static final String BIZ_TYPE = "ALLOC_ADJUST";

    @Test
    void t1_emptyTodoKeys_returnsEmptyWithoutDbHit() {
        when(workflowApi.listMyTodoBusinessKeys(EMP, BIZ_TYPE)).thenReturn(Collections.emptyList());

        PageResult<AdjustTodoRespDTO> r = service.listMyTodos(EMP, null, null, null, null, null, 1, 20);

        assertThat(r.getTotal()).isEqualTo(0);
        assertThat(r.getRecords()).isEmpty();
        verify(mapper, never()).countMyTodos(any(), any(), any(), any(), any(), any());
    }

    @Test
    void t2_allFiltersHitsOne() {
        when(workflowApi.listMyTodoBusinessKeys(EMP, BIZ_TYPE))
                .thenReturn(Arrays.asList("ALLOC_ADJUST:A1"));
        when(mapper.countMyTodos(eq(Arrays.asList("A1")), eq("kw"), eq("CUST"), eq("LOAN"), any(), any()))
                .thenReturn(1L);
        PerfAllocAdjustApply a = buildApply("A1", "ADJ001", "C1", "CUST", "LOAN");
        when(mapper.selectMyTodos(eq(Arrays.asList("A1")), eq("kw"), eq("CUST"), eq("LOAN"), any(), any(), eq(0), eq(20)))
                .thenReturn(Arrays.asList(a));
        Map<String, TaskRespDTO> meta = new HashMap<>();
        meta.put("ALLOC_ADJUST:A1", buildTask("T1", "biz_dept_review"));
        when(workflowApi.findTaskRespByBusinessKeys(eq(EMP), eq(Arrays.asList("ALLOC_ADJUST:A1")))).thenReturn(meta);

        LocalDateTime from = LocalDateTime.of(2026, 5, 1, 0, 0);
        PageResult<AdjustTodoRespDTO> r = service.listMyTodos(EMP, "kw", "CUST", "LOAN",
                from.toLocalDate(), from.toLocalDate(), 1, 20);

        assertThat(r.getTotal()).isEqualTo(1);
        assertThat(r.getRecords()).hasSize(1);
        AdjustTodoRespDTO row = r.getRecords().get(0);
        assertThat(row.getId()).isEqualTo("A1");
        assertThat(row.getApplyNo()).isEqualTo("ADJ001");
        assertThat(row.getTaskId()).isEqualTo("T1");
        assertThat(row.getNodeKey()).isEqualTo("biz_dept_review");
    }

    @Test
    void t3_keywordOnly() {
        when(workflowApi.listMyTodoBusinessKeys(EMP, BIZ_TYPE)).thenReturn(Arrays.asList("ALLOC_ADJUST:A1", "ALLOC_ADJUST:A2"));
        when(mapper.countMyTodos(any(), eq("ADJ"), isNull(), isNull(), isNull(), isNull())).thenReturn(2L);
        when(mapper.selectMyTodos(any(), eq("ADJ"), isNull(), isNull(), isNull(), isNull(), eq(0), eq(20)))
                .thenReturn(Arrays.asList(buildApply("A1", "ADJ001", "C1", "CUST", "LOAN"),
                                          buildApply("A2", "ADJ002", "C2", "ORG", "DEPOSIT")));
        when(workflowApi.findTaskRespByBusinessKeys(any(), any())).thenReturn(Collections.emptyMap());

        PageResult<AdjustTodoRespDTO> r = service.listMyTodos(EMP, "ADJ", null, null, null, null, 1, 20);

        assertThat(r.getTotal()).isEqualTo(2);
        assertThat(r.getRecords()).hasSize(2);
    }

    @Test
    void t4_allocDimFilter() {
        when(workflowApi.listMyTodoBusinessKeys(EMP, BIZ_TYPE)).thenReturn(Arrays.asList("ALLOC_ADJUST:A1"));
        when(mapper.countMyTodos(any(), isNull(), eq("CUST"), isNull(), isNull(), isNull())).thenReturn(1L);
        when(mapper.selectMyTodos(any(), isNull(), eq("CUST"), isNull(), isNull(), isNull(), eq(0), eq(20)))
                .thenReturn(Arrays.asList(buildApply("A1", "ADJ001", "C1", "CUST", "LOAN")));
        when(workflowApi.findTaskRespByBusinessKeys(any(), any())).thenReturn(Collections.emptyMap());

        PageResult<AdjustTodoRespDTO> r = service.listMyTodos(EMP, null, "CUST", null, null, null, 1, 20);

        assertThat(r.getRecords().get(0).getAllocDim()).isEqualTo("CUST");
    }

    @Test
    void t5_dateRangeClosedInterval_passesDateToPlusOneDay() {
        when(workflowApi.listMyTodoBusinessKeys(EMP, BIZ_TYPE)).thenReturn(Arrays.asList("ALLOC_ADJUST:A1"));
        when(mapper.countMyTodos(any(), any(), any(), any(),
                eq(LocalDateTime.of(2026, 5, 21, 0, 0)),
                eq(LocalDateTime.of(2026, 5, 22, 0, 0)))).thenReturn(0L);
        when(mapper.selectMyTodos(any(), any(), any(), any(),
                eq(LocalDateTime.of(2026, 5, 21, 0, 0)),
                eq(LocalDateTime.of(2026, 5, 22, 0, 0)), anyInt(), anyInt()))
                .thenReturn(Collections.emptyList());

        service.listMyTodos(EMP, null, null, null,
                java.time.LocalDate.of(2026, 5, 21),
                java.time.LocalDate.of(2026, 5, 21), 1, 20);

        // 验证 dateToExclusive = dateTo + 1 day（闭区间含 dateTo 当天）
        verify(mapper).countMyTodos(any(), any(), any(), any(),
                eq(LocalDateTime.of(2026, 5, 21, 0, 0)),
                eq(LocalDateTime.of(2026, 5, 22, 0, 0)));
    }

    @Test
    void t6_pagination_page2Size10() {
        when(workflowApi.listMyTodoBusinessKeys(EMP, BIZ_TYPE))
                .thenReturn(Arrays.asList("ALLOC_ADJUST:A1"));
        when(mapper.countMyTodos(any(), any(), any(), any(), any(), any())).thenReturn(15L);
        when(mapper.selectMyTodos(any(), any(), any(), any(), any(), any(), eq(10), eq(10)))
                .thenReturn(Collections.nCopies(5, buildApply("A1", "ADJ001", "C1", "CUST", "LOAN")));
        when(workflowApi.findTaskRespByBusinessKeys(any(), any())).thenReturn(Collections.emptyMap());

        PageResult<AdjustTodoRespDTO> r = service.listMyTodos(EMP, null, null, null, null, null, 2, 10);

        assertThat(r.getTotal()).isEqualTo(15);
        assertThat(r.getRecords()).hasSize(5);
        assertThat(r.getPageNo()).isEqualTo(2);
    }

    @Test
    void t7_workflowReturns5ButDbOnly3() {
        when(workflowApi.listMyTodoBusinessKeys(EMP, BIZ_TYPE))
                .thenReturn(Arrays.asList("ALLOC_ADJUST:A1", "ALLOC_ADJUST:A2", "ALLOC_ADJUST:A3",
                                          "ALLOC_ADJUST:A4", "ALLOC_ADJUST:A5"));
        when(mapper.countMyTodos(any(), any(), any(), any(), any(), any())).thenReturn(3L);
        when(mapper.selectMyTodos(any(), any(), any(), any(), any(), any(), eq(0), eq(20)))
                .thenReturn(Arrays.asList(buildApply("A1", "ADJ001", "C1", "CUST", "LOAN"),
                                          buildApply("A2", "ADJ002", "C2", "ORG", "DEPOSIT"),
                                          buildApply("A3", "ADJ003", "C3", "EMP", "SUPPORT")));
        when(workflowApi.findTaskRespByBusinessKeys(any(), any())).thenReturn(Collections.emptyMap());

        PageResult<AdjustTodoRespDTO> r = service.listMyTodos(EMP, null, null, null, null, null, 1, 20);

        assertThat(r.getTotal()).isEqualTo(3);
        assertThat(r.getRecords()).hasSize(3);
    }

    @Test
    void t8_taskMetaMissing_setsNullsNoNpe() {
        when(workflowApi.listMyTodoBusinessKeys(EMP, BIZ_TYPE)).thenReturn(Arrays.asList("ALLOC_ADJUST:A1"));
        when(mapper.countMyTodos(any(), any(), any(), any(), any(), any())).thenReturn(1L);
        when(mapper.selectMyTodos(any(), any(), any(), any(), any(), any(), eq(0), eq(20)))
                .thenReturn(Arrays.asList(buildApply("A1", "ADJ001", "C1", "CUST", "LOAN")));
        when(workflowApi.findTaskRespByBusinessKeys(any(), any())).thenReturn(Collections.emptyMap());

        PageResult<AdjustTodoRespDTO> r = service.listMyTodos(EMP, null, null, null, null, null, 1, 20);

        AdjustTodoRespDTO row = r.getRecords().get(0);
        assertThat(row.getId()).isEqualTo("A1");
        assertThat(row.getTaskId()).isNull();
        assertThat(row.getNodeKey()).isNull();
        assertThat(row.getClaimable()).isNull();
    }

    // ===== test helpers =====
    private PerfAllocAdjustApply buildApply(String id, String applyNo, String custId, String dim, String kind) {
        PerfAllocAdjustApply a = new PerfAllocAdjustApply();
        a.setId(id);
        a.setApplyNo(applyNo);
        a.setCustId(custId);
        a.setAllocDim(dim);
        a.setBizKind(kind);
        a.setOwnerOrgId("ORG_001");
        a.setCreatedBy("E001");
        a.setCreatedTime(LocalDateTime.of(2026, 5, 21, 10, 0));
        a.setBusinessKey("ALLOC_ADJUST:" + id);
        return a;
    }

    private TaskRespDTO buildTask(String taskId, String nodeKey) {
        TaskRespDTO t = new TaskRespDTO();
        t.setTaskId(taskId);
        t.setNodeKey(nodeKey);
        t.setTaskName("业务部门经办审批");
        t.setClaimable(true);
        t.setTitle("Task title");
        return t;
    }
}
```

- [ ] **Step 2: 跑测试确认 fail**

```bash
cd /home/djdev/lf/yiti
mvn test -pl performance-engine-center -Dtest=AllocAdjustTodoServiceTest
```
Expected: 8 个 FAIL（class 不存在）。

- [ ] **Step 3: 实现 AllocAdjustTodoService**

```java
package com.bank.branch.platform.performance.service.adjust;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.performance.controller.dto.AdjustTodoRespDTO;
import com.bank.branch.platform.performance.entity.PerfAllocAdjustApply;
import com.bank.branch.platform.performance.mapper.PerfAllocAdjustTodoMapper;
import com.bank.branch.platform.workflow.api.TodoQueryApi;
import com.bank.branch.platform.workflow.api.dto.TaskRespDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 「我的待审批 - 业绩调整」查询服务。
 * <p>
 * 数据流：
 * 1. 调 workflow TodoQueryApi 拿 ALLOC_ADJUST 待办的 businessKey
 * 2. applyIds = businessKey 列表去前缀
 * 3. mapper IN(applyIds) + 业务字段过滤 + 分页
 * 4. workflow 反查 TaskRespDTO，merge 进 AdjustTodoRespDTO
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AllocAdjustTodoService {

    private static final String BIZ_TYPE = "ALLOC_ADJUST";
    private static final String BUSINESS_KEY_PREFIX = BIZ_TYPE + ":";

    private final TodoQueryApi workflowTodoApi;
    private final PerfAllocAdjustTodoMapper mapper;

    /**
     * 查询当前用户的业绩调整待审批列表。
     *
     * @param empId    当前员工 ID（从 CurrentUserApi 传入，不接受前端参数）
     * @param keyword  关键字（apply_no/cust_id 模糊匹配），可空
     * @param allocDim 维度过滤（CUST/ORG/EMP），可空
     * @param bizKind  业务类型（LOAN/DEPOSIT 等），可空
     * @param dateFrom 申请时间起（闭），可空
     * @param dateTo   申请时间止（闭，service 内部转 +1day exclusive），可空
     * @param pageNo   页码（从 1）
     * @param pageSize 每页大小
     */
    public PageResult<AdjustTodoRespDTO> listMyTodos(String empId,
                                                    String keyword,
                                                    String allocDim,
                                                    String bizKind,
                                                    LocalDate dateFrom,
                                                    LocalDate dateTo,
                                                    int pageNo,
                                                    int pageSize) {
        log.debug("[AllocAdjustTodoService.listMyTodos] empId={}, kw={}, dim={}, kind={}, from={}, to={}, page={}/{}",
                empId, keyword, allocDim, bizKind, dateFrom, dateTo, pageNo, pageSize);

        // 1. 拿 workflow 待办 businessKey
        List<String> allTodoKeys = workflowTodoApi.listMyTodoBusinessKeys(empId, BIZ_TYPE);
        if (allTodoKeys.isEmpty()) {
            return PageResult.of(pageNo, pageSize, 0L, Collections.emptyList());
        }

        // 2. 去前缀拿 applyIds
        List<String> applyIds = allTodoKeys.stream()
                .map(k -> k.startsWith(BUSINESS_KEY_PREFIX) ? k.substring(BUSINESS_KEY_PREFIX.length()) : k)
                .collect(Collectors.toList());

        // 3. 闭区间转 exclusive
        LocalDateTime fromDt = dateFrom == null ? null : dateFrom.atStartOfDay();
        LocalDateTime toExclusive = dateTo == null ? null : dateTo.plusDays(1).atStartOfDay();

        // 4. count + select 分页
        long total = mapper.countMyTodos(applyIds, keyword, allocDim, bizKind, fromDt, toExclusive);
        if (total == 0) {
            return PageResult.of(pageNo, pageSize, 0L, Collections.emptyList());
        }
        int offset = (pageNo - 1) * pageSize;
        List<PerfAllocAdjustApply> applies = mapper.selectMyTodos(
                applyIds, keyword, allocDim, bizKind, fromDt, toExclusive, offset, pageSize);

        // 5. 反查 TaskRespDTO 元信息
        List<String> pageKeys = applies.stream()
                .map(a -> BUSINESS_KEY_PREFIX + a.getId())
                .collect(Collectors.toList());
        Map<String, TaskRespDTO> metaMap = workflowTodoApi.findTaskRespByBusinessKeys(empId, pageKeys);

        // 6. merge
        List<AdjustTodoRespDTO> records = applies.stream()
                .map(a -> mergeToDto(a, metaMap.get(BUSINESS_KEY_PREFIX + a.getId())))
                .collect(Collectors.toList());

        return PageResult.of(pageNo, pageSize, total, records);
    }

    private AdjustTodoRespDTO mergeToDto(PerfAllocAdjustApply a, TaskRespDTO t) {
        AdjustTodoRespDTO d = new AdjustTodoRespDTO();
        // 业务字段
        d.setId(a.getId());
        d.setApplyNo(a.getApplyNo());
        d.setCustId(a.getCustId());
        d.setAllocDim(a.getAllocDim());
        d.setBizKind(a.getBizKind());
        d.setOwnerOrgId(a.getOwnerOrgId());
        d.setCreatedBy(a.getCreatedBy());
        d.setCreatedTime(a.getCreatedTime());
        d.setBusinessKey(a.getBusinessKey());
        // workflow 字段（t 可能为 null）
        if (t != null) {
            d.setTaskId(t.getTaskId());
            d.setNodeKey(t.getNodeKey());
            d.setTaskName(t.getTaskName());
            d.setTitle(t.getTitle());
            d.setClaimable(t.getClaimable());
            d.setStartUser(t.getStartUser());
            d.setStartUserName(t.getStartUserName());
            d.setStartOrgId(t.getStartOrgId());
            d.setStartOrgName(t.getStartOrgName());
            d.setStartTime(t.getStartTime());
            d.setTaskCreateTime(t.getTaskCreateTime());
            d.setSlaStatus(t.getSlaStatus());
        }
        return d;
    }
}
```

**注意**：上述代码假设 `TaskRespDTO` 含 setter `setTaskId/setNodeKey/setTaskName/setTitle/setClaimable/setStartUser/setStartUserName/setStartOrgId/setStartOrgName/setStartTime/setTaskCreateTime/setSlaStatus`。如某字段在 TaskRespDTO 不存在（命名不同），执行时按现有字段修正——但 spec §0 已确认这些是现有字段。

- [ ] **Step 4: 跑测试确认 pass**

```bash
mvn test -pl performance-engine-center -Dtest=AllocAdjustTodoServiceTest
```
Expected: 8 个 PASS。

- [ ] **Step 5: commit**

```bash
cd /home/djdev/lf/yiti
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/adjust/AllocAdjustTodoService.java \
        performance-engine-center/src/test/java/com/bank/branch/platform/performance/service/adjust/AllocAdjustTodoServiceTest.java
git commit -m "$(cat <<'EOF'
feat(perf): AllocAdjustTodoService 业绩调整待审批分页查询 + 单测

TDD T1-T8 全绿：empty/全字段/keyword/allocDim/dateRange 闭区间/分页/
DB少于workflow/meta缺失。

数据流：workflow.listMyTodoBusinessKeys → mapper IN+过滤+分页 →
workflow.findTaskRespByBusinessKeys → merge 扁平 DTO。

EOF
)"
```

---

### Task 5: perf AllocAdjustTodoController + PT_RESOURCE 登记

**Files:**
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/controller/AllocAdjustTodoController.java`
- 手工 SQL：`docs/superpowers/sql/2026-05-21-pt-resource-adjust-my-todos.sql`

- [ ] **Step 1: Controller**

```java
package com.bank.branch.platform.performance.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.aop.annotation.BizAuth;
import com.bank.branch.platform.common.web.ApiResponse;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.performance.controller.dto.AdjustTodoRespDTO;
import com.bank.branch.platform.performance.service.adjust.AllocAdjustTodoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 业绩调整 - 我的待审批 列表端点。
 * <p>权限：登录用户查自己的待办，empId 从 CurrentUserApi 取，不接受前端参数避免越权。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/perf/adjusts")
@RequiredArgsConstructor
public class AllocAdjustTodoController {

    private final AllocAdjustTodoService service;
    private final CurrentUserApi currentUserApi;

    @GetMapping("/my-todos")
    @BizAuth(bizType = "ALLOC_ADJUST", bizAction = "READ")
    public ApiResponse<PageResult<AdjustTodoRespDTO>> listMyTodos(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "allocDim", required = false) String allocDim,
            @RequestParam(value = "bizKind", required = false) String bizKind,
            @RequestParam(value = "dateFrom", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(value = "dateTo", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {

        String empId = currentUserApi.getCurrentEmpId();
        if (pageSize > 100) pageSize = 100;
        log.info("[AllocAdjustTodoController.listMyTodos] empId={}, kw={}, dim={}, kind={}, from={}, to={}, page={}/{}",
                empId, keyword, allocDim, bizKind, dateFrom, dateTo, pageNo, pageSize);

        PageResult<AdjustTodoRespDTO> r = service.listMyTodos(empId, keyword, allocDim, bizKind, dateFrom, dateTo, pageNo, pageSize);
        return ApiResponse.success(r);
    }
}
```

**注意**：上述 `@BizAuth` 注解的包路径以项目实际为准（参考现有 AllocAdjustController 的 import 实际包名）。`ApiResponse` 同。

- [ ] **Step 2: PT_RESOURCE SQL（手工执行）**

文件：`docs/superpowers/sql/2026-05-21-pt-resource-adjust-my-todos.sql`

```sql
-- 业绩调整 - 我的待审批 list 接口登记
-- 执行时机：lf yiti 部署前手工 mysql 跑
INSERT INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, RESOURCE_NAME,
                         BIZ_TYPE, BIZ_ACTION, IS_MENU, RECORD_STATUS, SYS_CODE,
                         CREATE_TIME, UPDATE_TIME)
VALUES ('R_PERF_ADJ_TODO_LIST', '/api/perf/adjusts/my-todos', 'GET', '业绩调整-我的待审批',
        'ALLOC_ADJUST', 'READ', 0, 0, 'PLATFORM',
        NOW(), NOW())
ON DUPLICATE KEY UPDATE UPDATE_TIME = NOW();

-- 把该资源绑给现有审批角色（沿用 R_ADJUST_APPROVER 之类，按实际角色 ID 改）
-- 仅当存在该角色时绑定，否则按实际策略调整
INSERT IGNORE INTO PT_ROLE_RESOURCE (ROLE_ID, RESOURCE_ID)
SELECT r.ROLE_ID, 'R_PERF_ADJ_TODO_LIST'
  FROM PT_ROLE r
 WHERE r.ROLE_CODE IN ('SYS_ADMIN', 'ADJUST_APPROVER', 'BIZ_DEPT_MANAGER')
   AND r.RECORD_STATUS = 0;
```

- [ ] **Step 3: 在 lf yiti 数据库手工跑 SQL（部署前提）**

```bash
mysql -uroot -pdjdev yiti < /home/djdev/wangyq/yiti/docs/superpowers/sql/2026-05-21-pt-resource-adjust-my-todos.sql
```

- [ ] **Step 4: 启动 18080 验证端点**

```bash
cd /home/djdev/lf/yiti
mvn install -DskipTests
# kill 旧 PID
lsof -i:18080 | tail -1 | awk '{print $2}' | xargs -r kill -9
# 启动
nohup java -jar bootstrap/target/bootstrap-1.0.0-SNAPSHOT.jar > /tmp/yiti-18080.log 2>&1 &
sleep 8
# 验证端点存在（应返 401 未登录）
curl -s -o /dev/null -w "%{http_code}\n" --noproxy '*' http://127.0.0.1:18080/api/perf/adjusts/my-todos
```
Expected: HTTP 401（未登录，符合预期）。如果返 404 说明 controller 没扫到，检查包路径。

- [ ] **Step 5: commit**

```bash
cd /home/djdev/lf/yiti
git add performance-engine-center/src/main/java/com/bank/branch/platform/performance/controller/AllocAdjustTodoController.java
git commit -m "$(cat <<'EOF'
feat(perf): 新增 GET /api/perf/adjusts/my-todos 业绩调整待审批列表端点

@BizAuth(ALLOC_ADJUST/READ)，empId 取自 CurrentUserApi 防越权；
pageSize 上限 100；dateFrom/dateTo 闭区间，service 内部转 exclusive。

PT_RESOURCE 登记 SQL 见 docs/superpowers/sql/2026-05-21-pt-resource-adjust-my-todos.sql
（手工跑后才能被鉴权拦截器识别）。

EOF
)"
```

- [ ] **Step 6: commit SQL（在 wangyq 仓库）**

```bash
cd /home/djdev/wangyq
git add yiti/docs/superpowers/sql/2026-05-21-pt-resource-adjust-my-todos.sql
git commit -m "$(cat <<'EOF'
chore(sql): PT_RESOURCE 登记 /api/perf/adjusts/my-todos

配合 lf yiti 新接口；部署前 mysql 手工跑。

EOF
)"
```

---

### Task 6: 前端 api/perf.js 加 listMyAdjustTodos

**Files:**
- Modify: `xanzc_frontend/src/api/perf.js`

- [ ] **Step 1: 加 wrapper（直接返 PageResult 对象，不用 unwrapPage 因其会丢 total）**

在 perf.js 顶部确认 import：
```js
import { call } from './http';
```
（如果已有则跳过）。

在文件末尾追加：

```js
/**
 * 业绩调整 - 我的待审批 列表（后端分页 + 4 字段过滤）.
 * @param {object} params - { keyword?, allocDim?, bizKind?, dateFrom?, dateTo?, pageNo?, pageSize? }
 * @returns PageResult 对象 { pageNo, pageSize, total, records }
 *          （不走 unwrapPage 因为它只剥 records 返数组，会丢 total）
 */
export function listMyAdjustTodos(params = {}) {
  return call('get', '/perf/adjusts/my-todos',
    { params: { pageNo: 1, pageSize: 20, ...params } },
    { total: 0, records: [], pageNo: 1, pageSize: 20 }
  );
}
```

- [ ] **Step 2: commit（在 wangyq 仓库）**

```bash
cd /home/djdev/wangyq
git add yiti/xanzc_frontend/src/api/perf.js
git commit -m "$(cat <<'EOF'
feat(frontend): 新增 listMyAdjustTodos 调用 perf 新接口

对接 lf yiti GET /api/perf/adjusts/my-todos。

EOF
)"
```

---

### Task 7: 前端 workspace/Index.vue goHandle 加 tab=todo

**Files:**
- Modify: `xanzc_frontend/src/views/workspace/Index.vue`

- [ ] **Step 1: 改 goHandle**

原代码 line 174-184：
```js
function goHandle(row) {
  const id = row.id || row.taskId;
  if (!id) return;
  // 业绩调整审批：跳到 /perf/adjust 页内打开审批抽屉，不开独立菜单
  // 其他 bizType 暂时也走该路径（接入时再分流），保持"审批办理在业务页内"的设计
  if (['ALLOC_ADJUST', 'PERF_ALLOC_ADJUST'].includes(row.bizType)) {
    router.push({ path: '/perf/adjust', query: { taskId: id, action: 'open' } });
  } else {
    router.push({ path: '/perf/adjust', query: { taskId: id, action: 'open' } });
  }
}
```

改为：
```js
function goHandle(row) {
  const id = row.id || row.taskId;
  if (!id) return;
  // 业绩调整审批：跳到 /perf/adjust 的「待我审批」tab，自动弹审批
  // 其他 bizType 暂走同路径（接入时再分流），保持"审批办理在业务页内"的设计
  router.push({ path: '/perf/adjust', query: { tab: 'todo', taskId: id, action: 'open' } });
}
```

- [ ] **Step 2: commit**

```bash
cd /home/djdev/wangyq
git add yiti/xanzc_frontend/src/views/workspace/Index.vue
git commit -m "$(cat <<'EOF'
feat(workspace): 办理按钮跳 /perf/adjust 加 tab=todo 直达「待我审批」

之前默认落「我的申请」要手切，体验差；现一步直达对应 task 待办 tab。

EOF
)"
```

---

### Task 8: 前端 Adjust.vue todo tab 加表单 + 分页 + 切到新接口

**Files:**
- Modify: `xanzc_frontend/src/views/perf/Adjust.vue`

**改动概览**：
- script 区：删除 `listTodoTasks` 调用，改 `listMyAdjustTodos`；加 `todoFilters` reactive + `todoPager` reactive；改 `reloadTodo` 读 filters + pager + 写回 total
- template 区：todo tab 顶部加查询表单；表格下加 el-pagination

- [ ] **Step 1: script 改造 reactive state**

在 `// ============ 待我审批 ============` 区块（line 398 附近）修改：

```js
// ============ 待我审批 ============
import { listMyAdjustTodos } from '@/api/perf';

const todos = ref([]);
const todoLoading = ref(false);
const todoFilters = reactive({
  keyword: '',
  allocDim: '',
  bizKind: '',
  dateRange: null, // [startDate, endDate] from el-date-picker
});
const todoPager = reactive({
  pageNo: 1,
  pageSize: 20,
  total: 0,
});

async function reloadTodo() {
  todoLoading.value = true;
  try {
    const params = {
      keyword: todoFilters.keyword || undefined,
      allocDim: todoFilters.allocDim || undefined,
      bizKind: todoFilters.bizKind || undefined,
      dateFrom: todoFilters.dateRange?.[0] || undefined,
      dateTo: todoFilters.dateRange?.[1] || undefined,
      pageNo: todoPager.pageNo,
      pageSize: todoPager.pageSize,
    };
    const r = await listMyAdjustTodos(params);
    // r 是 PageResult 对象 { records, total, pageNo, pageSize }
    todos.value = r.records || [];
    todoPager.total = r.total || 0;
  } catch (err) {
    console.error('[reloadTodo] failed', err);
    todos.value = [];
    todoPager.total = 0;
  } finally {
    todoLoading.value = false;
  }
}

function resetTodoFilters() {
  todoFilters.keyword = '';
  todoFilters.allocDim = '';
  todoFilters.bizKind = '';
  todoFilters.dateRange = null;
  todoPager.pageNo = 1;
  reloadTodo();
}

function onTodoFilterChange() {
  todoPager.pageNo = 1;
  reloadTodo();
}
```

确认 import 区已有：
```js
import { reactive, ref, onMounted } from 'vue';
```
（如缺 `reactive` 补上）。

**注意**：原文件可能在文件顶部统一 import，请把 `listMyAdjustTodos` 加到现有 `import {...} from '@/api/perf'` 的解构里，**不要在 script 中间 import**（违反 Vue 单文件组件规范）。

- [ ] **Step 2: template 改 todo tab，顶部加表单 + 底部加分页**

替换 `<el-tab-pane v-if="canApprove" label="待我审批" name="todo">` 整块（line 61-102）为：

```html
<el-tab-pane v-if="canApprove" label="待我审批" name="todo">
  <div class="card-section">
    <el-form inline size="default">
      <el-form-item label="关键字">
        <el-input v-model="todoFilters.keyword" placeholder="申请编号 / 客户 ID" clearable style="width:200px"
                  @keyup.enter="onTodoFilterChange" />
      </el-form-item>
      <el-form-item label="维度">
        <el-select v-model="todoFilters.allocDim" clearable placeholder="全部" style="width:140px"
                   @change="onTodoFilterChange">
          <el-option value="CUST" label="客户" />
          <el-option value="ORG" label="机构" />
          <el-option value="EMP" label="员工" />
        </el-select>
      </el-form-item>
      <el-form-item label="业务类型">
        <el-select v-model="todoFilters.bizKind" clearable placeholder="全部" style="width:160px"
                   @change="onTodoFilterChange">
          <el-option value="LOAN" label="贷款" />
          <el-option value="DEPOSIT" label="存款" />
          <el-option value="SUPPORT" label="支援" />
        </el-select>
      </el-form-item>
      <el-form-item label="申请时间">
        <el-date-picker v-model="todoFilters.dateRange" type="daterange" value-format="YYYY-MM-DD"
                        range-separator="~" start-placeholder="开始" end-placeholder="结束"
                        style="width:240px" @change="onTodoFilterChange" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="onTodoFilterChange">查询</el-button>
        <el-button @click="resetTodoFilters">重置</el-button>
      </el-form-item>
    </el-form>
  </div>

  <div class="card-section table">
    <el-table :data="todos" size="default" empty-text="无符合条件的待审批" v-loading="todoLoading">
      <el-table-column label="标题" min-width="220">
        <template #default="{row}"><code class="mono">{{ row.title || row.businessKey }}</code></template>
      </el-table-column>
      <el-table-column label="当前节点" width="140" prop="taskName" />
      <el-table-column label="发起人" width="160">
        <template #default="{row}">
          {{ row.startUserName || '-' }}
          <span v-if="row.startUser" class="sub-id">({{ row.startUser }})</span>
        </template>
      </el-table-column>
      <el-table-column label="发起机构" width="220">
        <template #default="{row}">
          <template v-if="row.startOrgName || row.startOrgId">
            {{ row.startOrgId || '-' }}<span v-if="row.startOrgName"> · {{ row.startOrgName }}</span>
          </template>
          <template v-else>-</template>
        </template>
      </el-table-column>
      <el-table-column label="发起时间" width="160">
        <template #default="{row}">{{ fmt(row.startTime) }}</template>
      </el-table-column>
      <el-table-column label="任务到达" width="160">
        <template #default="{row}">{{ fmt(row.taskCreateTime) }}</template>
      </el-table-column>
      <el-table-column label="SLA" width="90">
        <template #default="{row}">
          <el-tag :class="slaCls(row.slaStatus)" effect="plain">{{ slaLabel(row.slaStatus) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{row}">
          <el-button link type="primary" size="small" @click="openTodoDetail(row)">查看申请</el-button>
          <el-button link type="success" size="small" @click="openApprove(row)">通过</el-button>
          <el-button link type="danger"  size="small" @click="openReject(row)">驳回</el-button>
        </template>
      </el-table-column>
    </el-table>
    <div class="pager">
      <el-pagination
        v-model:current-page="todoPager.pageNo"
        v-model:page-size="todoPager.pageSize"
        :page-sizes="[10, 20, 50]"
        :total="todoPager.total"
        background
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="reloadTodo"
        @current-change="reloadTodo"
      />
    </div>
  </div>
</el-tab-pane>
```

- [ ] **Step 3: 检查 listTodoTasks 是否还被其他地方用**

```bash
grep -n "listTodoTasks" /home/djdev/wangyq/yiti/xanzc_frontend/src/views/perf/Adjust.vue
```
如还有残留引用（应为 0，本任务把 todo tab 切走了），把 import 也删掉。

- [ ] **Step 4: 手工验证（浏览器 8090）**

启动前端 `cd /home/djdev/wangyq/yiti/xanzc_frontend && npm run dev`，浏览器开 `http://localhost:8090/#/perf/adjust`，点「待我审批」tab：
- [ ] 顶部表单 4 字段渲染 OK
- [ ] 表格数据加载 OK（如有待办）
- [ ] 分页条显示 total + 当前页
- [ ] 关键字 / 维度 / 业务类型 / 日期范围 单独筛选都触发 reload
- [ ] 「重置」清空 4 个字段 + 回第一页
- [ ] 翻页 / 改 pageSize 都重新拉数据
- [ ] empty 状态文案对（无数据时显示「无符合条件的待审批」）

- [ ] **Step 5: commit**

```bash
cd /home/djdev/wangyq
git add yiti/xanzc_frontend/src/views/perf/Adjust.vue
git commit -m "$(cat <<'EOF'
feat(perf-adjust): 待我审批 tab 加 4 字段查询 + 后端分页 + 切到新接口

- 顶部加表单：关键字 / 维度 / 业务类型 / 申请时间范围
- 表格底部加 el-pagination（10/20/50/page）
- 数据源切到 /api/perf/adjusts/my-todos（替代 /workflow/tasks）
- 查询/重置/翻页/改 pageSize 都触发 reload

EOF
)"
```

---

### Task 9: 前端 Adjust.vue mounted 自动弹审批

**Files:**
- Modify: `xanzc_frontend/src/views/perf/Adjust.vue`

- [ ] **Step 1: 加 mounted 处理跳转参数**

确认顶部 import 含 `useRoute`：
```js
import { useRoute } from 'vue-router';
```

在 setup script 区找已有 onMounted block（应该有，绑定 reloadMine/reloadDone 之类）。如没有，新建：

```js
const route = useRoute();

onMounted(async () => {
  // 读 query.tab 切 activeTab
  const queryTab = route.query.tab;
  if (queryTab && ['mine', 'todo', 'done'].includes(queryTab)) {
    activeTab.value = queryTab;
  }

  // 触发当前 tab 的首次加载
  reload();

  // 自动弹审批（仅当 tab=todo + action=open + taskId 三者齐）
  if (queryTab === 'todo' && route.query.action === 'open' && route.query.taskId) {
    await autoOpenApprove(route.query.taskId);
  }
});

async function autoOpenApprove(taskId) {
  // reload 是 sync 的，但 reloadTodo 是 async；等其完成
  await reloadTodo();
  const row = todos.value.find(t => t.taskId === taskId);
  if (row) {
    openApprove(row);
  } else {
    ElMessage.warning('任务已处理或不在当前页');
  }
}
```

**注意 1**：原 onMounted 如有现成逻辑，需 merge 而非覆盖。
**注意 2**：`reload()` 内部已根据 activeTab 选 reloadMine/reloadTodo/reloadDone，本设计依赖它先把 activeTab 切到 'todo' 再 await reloadTodo（手动调一次，确保 todos 数组就绪后再 find）。reload() 那次调用其实会重复 reloadTodo —— 优化为：当 queryTab==='todo'+action=='open' 时跳过 reload() 直接 awaitreloadTodo()；其他场景仍 reload()。

修订版：
```js
onMounted(async () => {
  const queryTab = route.query.tab;
  if (queryTab && ['mine', 'todo', 'done'].includes(queryTab)) {
    activeTab.value = queryTab;
  }

  const isAutoOpen = queryTab === 'todo' && route.query.action === 'open' && route.query.taskId;
  if (isAutoOpen) {
    // 走 todo tab + 自动弹：只 await reloadTodo 一次避免重复
    await reloadTodo();
    const row = todos.value.find(t => t.taskId === route.query.taskId);
    if (row) {
      openApprove(row);
    } else {
      ElMessage.warning('任务已处理或不在当前页');
    }
  } else {
    reload();
  }
});
```

确认 `ElMessage` 已 import：
```js
import { ElMessage, ElMessageBox } from 'element-plus';
```
（应该已有，因为 openApprove 用到 ElMessage）。

- [ ] **Step 2: 手工验证（浏览器）**

工作台 → 找一条 ALLOC_ADJUST 待办 → 点「办理」：
- [ ] URL 跳成 `/perf/adjust?tab=todo&taskId=xxx&action=open`
- [ ] Adjust.vue 落在「待我审批」tab（不是「我的申请」）
- [ ] 列表加载完后**自动弹出审批 dialog**（含 needsOriginalOwnerApprove 复选框或 prompt）

模拟跳转 taskId 不存在场景：手动改 URL 中 taskId 为不存在的 ID 刷新 → 应看到 `ElMessage.warning('任务已处理或不在当前页')`。

- [ ] **Step 3: commit**

```bash
cd /home/djdev/wangyq
git add yiti/xanzc_frontend/src/views/perf/Adjust.vue
git commit -m "$(cat <<'EOF'
feat(perf-adjust): mounted 读 query.tab/action/taskId 自动弹审批

工作台办理点击 → 自动落「待我审批」tab + 弹出对应 task 审批 dialog。
找不到 taskId 时（已处理/不在当前页）提示「任务已处理或不在当前页」。

EOF
)"
```

---

### Task 10: 全量回归测试 + 同步 wangyq → lf

**Files:** 无新建

- [ ] **Step 1: lf 后端跑全量测试**

```bash
cd /home/djdev/lf/yiti
mvn test
```
Expected: 所有现有 test 全绿（不能有回归）。

- [ ] **Step 2: 端到端冒烟（浏览器）**

- [ ] 工作台 → 点办理 → 落 todo tab + 弹审批
- [ ] todo tab → 输入关键字 → 查询命中
- [ ] todo tab → 选维度 CUST → 命中
- [ ] todo tab → 选业务类型 LOAN → 命中
- [ ] todo tab → 日期范围 today~today → 命中（验闭区间）
- [ ] todo tab → 翻第二页 / 改 pageSize=10
- [ ] todo tab → 重置 → 4 字段空 + 回第一页
- [ ] 跳转 taskId 不存在 → 提示「任务已处理或不在当前页」
- [ ] 「我的申请」「已审批」tab 行为未受影响

- [ ] **Step 3: 同步 wangyq 后端到 lf（如果用户要求）**

本次后端改动**全部在 lf 直接 commit**（按用户惯例：wangyq 改前端 / lf 改后端），不需要 wangyq → lf cherry-pick。如发现 wangyq 后端误改了，按上次去 redis 流程倒推。

- [ ] **Step 4: 询问用户是否 push gitee（按惯例不主动 push）**

> "实施完成，N 个 commit 落本地。要不要 push 到 gitee origin？"

---

## §3 验收 checklist（用户视角）

- [ ] 工作台办理一步直达「待我审批」+ 弹审批
- [ ] 4 字段筛选都生效
- [ ] 分页器翻页 / 改 pageSize 都 OK
- [ ] 找不到 taskId 提示文案对
- [ ] 现有 3 tab 行为无回归
- [ ] PT_RESOURCE 已登记，新接口 401 → 登录后 200 → 鉴权拦截链可触达

## §4 上线注意

- **执行顺序**：先 lf 后端（commit + mvn install + 重启 18080）→ wangyq 前端（commit + dev 验证）→ PT_RESOURCE SQL 必须在前端联调前在 yiti 库手工跑，否则鉴权 403
- **回滚**：每 task 一个 commit，单点 revert 不影响其他
- **零 ALTER TABLE / 零新表 / 零 Flyway**（合规）
