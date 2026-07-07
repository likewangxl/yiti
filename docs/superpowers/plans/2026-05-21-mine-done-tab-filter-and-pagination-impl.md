# 「我的申请」+「已审批」tab V2 扩展 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use subagent-driven-development or executing-plans。

**Goal:** 复用 V1 todo tab 模式给「我的申请」和「已审批」加查询条件 + 分页 + 防越权硬约束。

**Architecture:** workflow 加 done 反查 2 方法；perf 加 2 个新 my-* 端点（my-applies / my-done）+ Mapper 复用；前端 mine/done tab 加表单 + 分页 + 切接口。

**Tech Stack:** Spring Boot 3.2.3 / MyBatis Plus / Flowable 7.0.1 / Vue 3 / Element Plus

---

## §0 任务拓扑

5 个 commit（依赖顺序）：

```
C1 workflow done API (TodoQueryApi 加 2 方法 + facade + service + test)
   ↓
C2 perf mapper 扩 + my-applies (controller + service + test)   ← mine 不依赖 workflow
C3 perf my-done (controller + service + test)                  ← 依赖 C1 + C2 mapper
C4 wangyq SQL (PT_RESOURCE 2 个新端点)
C5 wangyq 前端 (perf.js wrappers + Adjust.vue mine/done tab)
```

---

### Task 1 (C1): workflow TodoQueryApi 加 done 反查

**Files:**
- Modify: `workflow-center/src/main/java/com/bank/branch/platform/workflow/api/TodoQueryApi.java`
- Modify: `workflow-center/src/main/java/com/bank/branch/platform/workflow/facade/TodoQueryFacade.java`
- Modify: `workflow-center/src/main/java/com/bank/branch/platform/workflow/service/TodoQueryService.java`
- Modify: `workflow-center/src/test/java/com/bank/branch/platform/workflow/facade/TodoQueryFacadeTest.java`

- [ ] **Step 1: 在 TodoQueryApi 加 2 个 done 方法签名**

```java
/** 已办：某员工某 bizType 下所有历史已办 task 的 businessKey（去重）。 */
List<String> listMyDoneBusinessKeys(String empId, String bizType);

/** 已办：按 businessKey 反查 TaskRespDTO（走 HistoryService）。 */
Map<String, TaskRespDTO> findDoneTaskRespByBusinessKeys(String empId, List<String> businessKeys);
```

- [ ] **Step 2: 在 TodoQueryFacade 加实现（thin wrapper 委托 service）**

```java
@Override
public List<String> listMyDoneBusinessKeys(String empId, String bizType) {
    log.debug("[TodoQueryFacade.listMyDoneBusinessKeys] empId={}, bizType={}", empId, bizType);
    return todoQueryService.listMyDoneBusinessKeys(empId, bizType);
}

@Override
public Map<String, TaskRespDTO> findDoneTaskRespByBusinessKeys(String empId, List<String> businessKeys) {
    log.debug("[TodoQueryFacade.findDoneTaskRespByBusinessKeys] empId={}, keys={}",
            empId, businessKeys == null ? 0 : businessKeys.size());
    if (empId == null || businessKeys == null || businessKeys.isEmpty()) {
        return Collections.emptyMap();
    }
    return todoQueryService.findDoneTaskRespByBusinessKeys(empId, businessKeys).stream()
            .filter(d -> d.getBusinessKey() != null)
            .collect(Collectors.toMap(TaskRespDTO::getBusinessKey, d -> d, (a, b) -> a));
}
```

- [ ] **Step 3: 在 TodoQueryService 加 2 个 public 方法（用 HistoryService）**

在 listMyTodoBusinessKeys 方法之后追加。

```java
/**
 * 查询当前员工已办（finished）task 的 businessKey（去重）。
 * <p>走 HistoryService.createHistoricTaskInstanceQuery + finished + taskAssignee=empId
 * + processInstanceId 批量查 BIZ_PROCESS_MAP 过滤 bizType。</p>
 */
public List<String> listMyDoneBusinessKeys(String empId, String bizType) {
    if (empId == null || bizType == null) {
        return new ArrayList<>();
    }
    List<HistoricTaskInstance> tasks = historyService.createHistoricTaskInstanceQuery()
            .taskAssignee(empId)
            .finished()
            .list();
    if (tasks.isEmpty()) {
        return new ArrayList<>();
    }
    List<String> piids = tasks.stream()
            .map(HistoricTaskInstance::getProcessInstanceId)
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());
    if (piids.isEmpty()) {
        return new ArrayList<>();
    }
    List<BizProcessMap> maps = bizProcessMapMapper.selectByProcessInstanceIdsAndBizType(piids, bizType);
    return maps.stream()
            .map(BizProcessMap::getBusinessKey)
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());
}

/**
 * 按 businessKey 列表反查已办 TaskRespDTO（鉴权：仅返该员工 assignee 的）。
 */
public List<TaskRespDTO> findDoneTaskRespByBusinessKeys(String empId, List<String> businessKeys) {
    if (empId == null || businessKeys == null || businessKeys.isEmpty()) {
        return new ArrayList<>();
    }
    // 1. businessKey → processInstanceId
    Set<String> targetPiids = new HashSet<>();
    for (String bk : businessKeys) {
        BizProcessMap map = bizProcessMapMapper.selectByBusinessKey(bk);
        if (map != null && map.getProcessInstanceId() != null) {
            targetPiids.add(map.getProcessInstanceId());
        }
    }
    if (targetPiids.isEmpty()) {
        return new ArrayList<>();
    }
    // 2. 拿当前员工已办 HistoricTask 内存过滤 piid
    List<HistoricTaskInstance> tasks = historyService.createHistoricTaskInstanceQuery()
            .taskAssignee(empId)
            .finished()
            .list();
    return tasks.stream()
            .filter(t -> targetPiids.contains(t.getProcessInstanceId()))
            .map(this::convertHistoricTaskToDTO)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
}
```

**注**：现有 `convertHistoricTaskToDTO(HistoricTaskInstance)` 在 line 340 已存在（与 V1 todo 复用 convertTaskToDTO 同样思路），直接 method reference。

- [ ] **Step 4: 在 TodoQueryFacadeTest 加 2 个 done test case (W4 + W5)**

```java
@Test
void w4_listMyDoneBusinessKeys_delegatesToService() {
    when(todoQueryService.listMyDoneBusinessKeys("E001", "ALLOC_ADJUST"))
            .thenReturn(Arrays.asList("ALLOC_ADJUST:A1", "ALLOC_ADJUST:A2"));

    List<String> keys = facade.listMyDoneBusinessKeys("E001", "ALLOC_ADJUST");

    assertThat(keys).containsExactly("ALLOC_ADJUST:A1", "ALLOC_ADJUST:A2");
}

@Test
void w5_findDoneTaskRespByBusinessKeys_buildsMap() {
    TaskRespDTO dto1 = new TaskRespDTO();
    dto1.setTaskId("T1");
    dto1.setBusinessKey("ALLOC_ADJUST:A1");
    when(todoQueryService.findDoneTaskRespByBusinessKeys("E001",
            Arrays.asList("ALLOC_ADJUST:A1", "ALLOC_ADJUST:A2")))
            .thenReturn(Arrays.asList(dto1));

    Map<String, TaskRespDTO> map = facade.findDoneTaskRespByBusinessKeys("E001",
            Arrays.asList("ALLOC_ADJUST:A1", "ALLOC_ADJUST:A2"));

    assertThat(map).hasSize(1);
    assertThat(map.get("ALLOC_ADJUST:A1").getTaskId()).isEqualTo("T1");
}
```

- [ ] **Step 5: 跑 test 验证 5/5（W1-W3 已有 + W4-W5 新）**

```bash
cd /home/djdev/lf/yiti && mvn test -pl workflow-center -Dtest=TodoQueryFacadeTest
```
Expected: 5/5 PASS。

- [ ] **Step 6: install workflow 让 perf 拿到新 API**

```bash
cd /home/djdev/lf/yiti && mvn install -pl workflow-center -DskipTests -am
```

- [ ] **Step 7: commit**

```bash
git -C /home/djdev/lf/yiti add workflow-center/
git -C /home/djdev/lf/yiti commit -m "feat(workflow): TodoQueryApi 加 done 反查 2 方法（已办 businessKey + TaskRespDTO）"
```

---

### Task 2 (C2): perf my-applies + mapper 扩展

**Files:**
- Modify: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/mapper/PerfAllocAdjustTodoMapper.java` (加 2 方法 mine)
- Modify: `performance-engine-center/src/main/resources/mapper/performance/PerfAllocAdjustTodoMapper.xml` (加 2 SQL mine + 复用 todoWhere)
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/adjust/AllocAdjustMineService.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/controller/AllocAdjustMineController.java`
- Create: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/service/adjust/AllocAdjustMineServiceTest.java`

- [ ] **Step 1: 在 PerfAllocAdjustTodoMapper 加 2 个 mine 方法**

```java
/** 我的申请：count（createdBy 必填）。 */
long countMyApplies(@Param("createdBy") String createdBy,
                    @Param("keyword") String keyword,
                    @Param("allocDim") String allocDim,
                    @Param("bizKind") String bizKind,
                    @Param("status") String status,
                    @Param("dateFrom") LocalDateTime dateFrom,
                    @Param("dateToExclusive") LocalDateTime dateToExclusive);

/** 我的申请：select（createdBy 必填），按 created_time DESC, id DESC。 */
List<PerfAllocAdjustApply> selectMyApplies(@Param("createdBy") String createdBy,
                                           @Param("keyword") String keyword,
                                           @Param("allocDim") String allocDim,
                                           @Param("bizKind") String bizKind,
                                           @Param("status") String status,
                                           @Param("dateFrom") LocalDateTime dateFrom,
                                           @Param("dateToExclusive") LocalDateTime dateToExclusive,
                                           @Param("offset") int offset,
                                           @Param("pageSize") int pageSize);
```

- [ ] **Step 2: 在 PerfAllocAdjustTodoMapper.xml 加 mine SQL + mineWhere 片段**

在 todoWhere 后追加：

```xml
<!-- 我的申请 where（createdBy 必填硬约束 + status 可选 + 4 字段同 todo） -->
<sql id="mineWhere">
    WHERE a.created_by = #{createdBy}
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
    <if test="status != null and status != ''">
        AND a.status = #{status}
    </if>
    <if test="dateFrom != null">
        AND a.created_time &gt;= #{dateFrom}
    </if>
    <if test="dateToExclusive != null">
        AND a.created_time &lt; #{dateToExclusive}
    </if>
</sql>

<select id="countMyApplies" resultType="long">
    SELECT COUNT(1)
    FROM PERF_ALLOC_ADJUST_APPLY a
    <include refid="mineWhere"/>
</select>

<select id="selectMyApplies"
        resultType="com.bank.branch.platform.performance.entity.PerfAllocAdjustApply">
    SELECT a.id, a.apply_no AS applyNo, a.cust_id AS custId, a.alloc_dim AS allocDim,
           a.biz_kind AS bizKind, a.account_no AS accountNo, a.status,
           a.business_key AS businessKey, a.process_instance_id AS processInstanceId,
           a.owner_org_id AS ownerOrgId, a.remark,
           a.created_by AS createdBy, a.created_time AS createdTime,
           a.updated_by AS updatedBy, a.updated_time AS updatedTime
    FROM PERF_ALLOC_ADJUST_APPLY a
    <include refid="mineWhere"/>
    ORDER BY a.created_time DESC, a.id DESC
    LIMIT #{offset}, #{pageSize}
</select>
```

- [ ] **Step 3: 建 AllocAdjustMineService**

```java
package com.bank.branch.platform.performance.service.adjust;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.performance.controller.dto.AdjustTodoRespDTO;
import com.bank.branch.platform.performance.entity.PerfAllocAdjustApply;
import com.bank.branch.platform.performance.mapper.PerfAllocAdjustTodoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 「我的申请」查询服务。
 * <p>createdBy 由 controller 从 CurrentUserApi 取硬注入，service 不接受外部 createdBy。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AllocAdjustMineService {

    private final PerfAllocAdjustTodoMapper mapper;

    public PageResult<AdjustTodoRespDTO> listMyApplies(String empId,
                                                       String keyword,
                                                       String allocDim,
                                                       String bizKind,
                                                       String status,
                                                       LocalDate dateFrom,
                                                       LocalDate dateTo,
                                                       int pageNo,
                                                       int pageSize) {
        log.debug("[AllocAdjustMineService.listMyApplies] empId={}, kw={}, dim={}, kind={}, status={}, from={}, to={}, page={}/{}",
                empId, keyword, allocDim, bizKind, status, dateFrom, dateTo, pageNo, pageSize);

        LocalDateTime fromDt = dateFrom == null ? null : dateFrom.atStartOfDay();
        LocalDateTime toExclusive = dateTo == null ? null : dateTo.plusDays(1).atStartOfDay();

        long total = mapper.countMyApplies(empId, keyword, allocDim, bizKind, status, fromDt, toExclusive);
        if (total == 0) {
            return PageResult.of(pageNo, pageSize, 0L, Collections.emptyList());
        }
        int offset = (pageNo - 1) * pageSize;
        List<PerfAllocAdjustApply> applies = mapper.selectMyApplies(
                empId, keyword, allocDim, bizKind, status, fromDt, toExclusive, offset, pageSize);

        List<AdjustTodoRespDTO> records = applies.stream()
                .map(this::mergeToDto)
                .collect(Collectors.toList());

        return PageResult.of(pageNo, pageSize, total, records);
    }

    private AdjustTodoRespDTO mergeToDto(PerfAllocAdjustApply a) {
        AdjustTodoRespDTO d = new AdjustTodoRespDTO();
        d.setId(a.getId());
        d.setApplyNo(a.getApplyNo());
        d.setCustId(a.getCustId());
        d.setAllocDim(a.getAllocDim());
        d.setBizKind(a.getBizKind());
        d.setOwnerOrgId(a.getOwnerOrgId());
        d.setCreatedBy(a.getCreatedBy());
        d.setCreatedTime(a.getCreatedTime());
        d.setBusinessKey(a.getBusinessKey());
        // task 字段不填（mine 不显示 workflow 信息）
        return d;
    }
}
```

- [ ] **Step 4: 建 AllocAdjustMineController**

```java
package com.bank.branch.platform.performance.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.performance.controller.dto.AdjustTodoRespDTO;
import com.bank.branch.platform.performance.service.adjust.AllocAdjustMineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 业绩调整 - 我的申请 列表端点。
 * <p>createdBy 硬约束 = currentEmpId，前端无法传 createdBy 越权查别人。</p>
 */
@Slf4j
@Tag(name = "业绩调整-我的申请")
@RestController
@RequestMapping("/api/perf/alloc-adjust")
@RequiredArgsConstructor
public class AllocAdjustMineController {

    private final AllocAdjustMineService service;
    private final CurrentUserApi currentUserApi;

    @Operation(summary = "我的申请列表（业绩调整）")
    @GetMapping("/my-applies")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.LIST)
    public ResponseWrapper<PageResult<AdjustTodoRespDTO>> listMyApplies(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "allocDim", required = false) String allocDim,
            @RequestParam(value = "bizKind", required = false) String bizKind,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "dateFrom", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(value = "dateTo", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {

        String empId = currentUserApi.getCurrentEmpId();
        if (pageSize > 100) pageSize = 100;
        if (pageNo < 1) pageNo = 1;
        log.info("[AllocAdjustMineController.listMyApplies] empId={}, kw={}, dim={}, kind={}, status={}, from={}, to={}, page={}/{}",
                empId, keyword, allocDim, bizKind, status, dateFrom, dateTo, pageNo, pageSize);

        PageResult<AdjustTodoRespDTO> r = service.listMyApplies(
                empId, keyword, allocDim, bizKind, status, dateFrom, dateTo, pageNo, pageSize);
        return ResponseWrapper.success(r);
    }
}
```

- [ ] **Step 5: 写 AllocAdjustMineServiceTest（5 case）**

```java
package com.bank.branch.platform.performance.service.adjust;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.performance.controller.dto.AdjustTodoRespDTO;
import com.bank.branch.platform.performance.entity.PerfAllocAdjustApply;
import com.bank.branch.platform.performance.mapper.PerfAllocAdjustTodoMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AllocAdjustMineServiceTest {

    @Mock PerfAllocAdjustTodoMapper mapper;
    @InjectMocks AllocAdjustMineService service;

    private static final String EMP = "E001";

    @Test
    void m1_createdByHardConstraint_alwaysFirstArg() {
        when(mapper.countMyApplies(eq(EMP), any(), any(), any(), any(), any(), any())).thenReturn(0L);

        service.listMyApplies(EMP, null, null, null, null, null, null, 1, 20);

        verify(mapper).countMyApplies(eq(EMP), isNull(), isNull(), isNull(), isNull(), isNull(), isNull());
        verify(mapper, never()).selectMyApplies(any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void m2_allFiltersIncludingStatus() {
        when(mapper.countMyApplies(eq(EMP), eq("kw"), eq("CUST"), eq("LOAN"), eq("APPROVED"), any(), any())).thenReturn(1L);
        PerfAllocAdjustApply a = buildApply("A1", "ADJ001", "C1", "CUST", "LOAN");
        a.setStatus("APPROVED");
        when(mapper.selectMyApplies(eq(EMP), eq("kw"), eq("CUST"), eq("LOAN"), eq("APPROVED"), any(), any(), eq(0), eq(20)))
                .thenReturn(Arrays.asList(a));

        PageResult<AdjustTodoRespDTO> r = service.listMyApplies(EMP, "kw", "CUST", "LOAN", "APPROVED",
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 21), 1, 20);

        assertThat(r.getTotal()).isEqualTo(1);
        assertThat(r.getRecords().get(0).getId()).isEqualTo("A1");
    }

    @Test
    void m3_statusOnly() {
        when(mapper.countMyApplies(eq(EMP), isNull(), isNull(), isNull(), eq("DRAFT"), isNull(), isNull())).thenReturn(2L);
        when(mapper.selectMyApplies(eq(EMP), isNull(), isNull(), isNull(), eq("DRAFT"), isNull(), isNull(), eq(0), eq(20)))
                .thenReturn(Arrays.asList(buildApply("A1", "ADJ001", "C1", "CUST", "LOAN"),
                                          buildApply("A2", "ADJ002", "C2", "ORG", "DEPOSIT")));

        PageResult<AdjustTodoRespDTO> r = service.listMyApplies(EMP, null, null, null, "DRAFT", null, null, 1, 20);

        assertThat(r.getRecords()).hasSize(2);
    }

    @Test
    void m4_pagination_page2() {
        when(mapper.countMyApplies(eq(EMP), any(), any(), any(), any(), any(), any())).thenReturn(25L);
        when(mapper.selectMyApplies(eq(EMP), any(), any(), any(), any(), any(), any(), eq(20), eq(20)))
                .thenReturn(Collections.nCopies(5, buildApply("A1", "ADJ001", "C1", "CUST", "LOAN")));

        PageResult<AdjustTodoRespDTO> r = service.listMyApplies(EMP, null, null, null, null, null, null, 2, 20);

        assertThat(r.getTotal()).isEqualTo(25);
        assertThat(r.getPageNo()).isEqualTo(2);
        assertThat(r.getRecords()).hasSize(5);
    }

    @Test
    void m5_dateRangeClosedInterval() {
        when(mapper.countMyApplies(eq(EMP), any(), any(), any(), any(),
                eq(LocalDateTime.of(2026, 5, 21, 0, 0)),
                eq(LocalDateTime.of(2026, 5, 22, 0, 0)))).thenReturn(0L);

        service.listMyApplies(EMP, null, null, null, null,
                LocalDate.of(2026, 5, 21), LocalDate.of(2026, 5, 21), 1, 20);

        verify(mapper).countMyApplies(eq(EMP), any(), any(), any(), any(),
                eq(LocalDateTime.of(2026, 5, 21, 0, 0)),
                eq(LocalDateTime.of(2026, 5, 22, 0, 0)));
    }

    private PerfAllocAdjustApply buildApply(String id, String applyNo, String custId, String dim, String kind) {
        PerfAllocAdjustApply a = new PerfAllocAdjustApply();
        a.setId(id);
        a.setApplyNo(applyNo);
        a.setCustId(custId);
        a.setAllocDim(dim);
        a.setBizKind(kind);
        a.setOwnerOrgId("ORG_001");
        a.setCreatedBy(EMP);
        a.setCreatedTime(LocalDateTime.of(2026, 5, 21, 10, 0));
        a.setBusinessKey("ALLOC_ADJUST:" + id);
        return a;
    }
}
```

- [ ] **Step 6: 跑 test 验证 5/5 + 编译**

```bash
cd /home/djdev/lf/yiti && mvn test -pl performance-engine-center -Dtest=AllocAdjustMineServiceTest
mvn compile -pl performance-engine-center
```

- [ ] **Step 7: commit**

```bash
git -C /home/djdev/lf/yiti add performance-engine-center/
git -C /home/djdev/lf/yiti commit -m "feat(perf): 业绩调整-我的申请 my-applies 端点 + Mapper 扩 mine SQL + 单测 M1-M5"
```

---

### Task 3 (C3): perf my-done

**Files:**
- Modify: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/mapper/PerfAllocAdjustTodoMapper.java` (加 2 done 方法)
- Modify: `performance-engine-center/src/main/resources/mapper/performance/PerfAllocAdjustTodoMapper.xml` (加 2 done SQL，复用 todoWhere)
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/service/adjust/AllocAdjustDoneService.java`
- Create: `performance-engine-center/src/main/java/com/bank/branch/platform/performance/controller/AllocAdjustDoneController.java`
- Create: `performance-engine-center/src/test/java/com/bank/branch/platform/performance/service/adjust/AllocAdjustDoneServiceTest.java`

- [ ] **Step 1: 在 PerfAllocAdjustTodoMapper 加 2 个 done 方法**

```java
/** 已审批：count（IN applyIds + 4 字段过滤，与 todo 同 SQL 复用 todoWhere）。 */
long countMyDones(@Param("applyIds") List<String> applyIds,
                  @Param("keyword") String keyword,
                  @Param("allocDim") String allocDim,
                  @Param("bizKind") String bizKind,
                  @Param("dateFrom") LocalDateTime dateFrom,
                  @Param("dateToExclusive") LocalDateTime dateToExclusive);

/** 已审批：select（同上 + 分页）。 */
List<PerfAllocAdjustApply> selectMyDones(@Param("applyIds") List<String> applyIds,
                                         @Param("keyword") String keyword,
                                         @Param("allocDim") String allocDim,
                                         @Param("bizKind") String bizKind,
                                         @Param("dateFrom") LocalDateTime dateFrom,
                                         @Param("dateToExclusive") LocalDateTime dateToExclusive,
                                         @Param("offset") int offset,
                                         @Param("pageSize") int pageSize);
```

- [ ] **Step 2: 在 PerfAllocAdjustTodoMapper.xml 加 done SQL（复用 todoWhere）**

```xml
<select id="countMyDones" resultType="long">
    SELECT COUNT(1)
    FROM PERF_ALLOC_ADJUST_APPLY a
    <include refid="todoWhere"/>
</select>

<select id="selectMyDones"
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
```

- [ ] **Step 3: 建 AllocAdjustDoneService（复用 todo 模式）**

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

@Slf4j
@Service
@RequiredArgsConstructor
public class AllocAdjustDoneService {

    private static final String BIZ_TYPE = "ALLOC_ADJUST";
    private static final String BUSINESS_KEY_PREFIX = BIZ_TYPE + ":";

    private final TodoQueryApi workflowTodoApi;
    private final PerfAllocAdjustTodoMapper mapper;

    public PageResult<AdjustTodoRespDTO> listMyDones(String empId,
                                                    String keyword,
                                                    String allocDim,
                                                    String bizKind,
                                                    LocalDate dateFrom,
                                                    LocalDate dateTo,
                                                    int pageNo,
                                                    int pageSize) {
        log.debug("[AllocAdjustDoneService.listMyDones] empId={}, kw={}, dim={}, kind={}, from={}, to={}, page={}/{}",
                empId, keyword, allocDim, bizKind, dateFrom, dateTo, pageNo, pageSize);

        List<String> allKeys = workflowTodoApi.listMyDoneBusinessKeys(empId, BIZ_TYPE);
        if (allKeys.isEmpty()) {
            return PageResult.of(pageNo, pageSize, 0L, Collections.emptyList());
        }

        List<String> applyIds = allKeys.stream()
                .map(k -> k.startsWith(BUSINESS_KEY_PREFIX) ? k.substring(BUSINESS_KEY_PREFIX.length()) : k)
                .collect(Collectors.toList());

        LocalDateTime fromDt = dateFrom == null ? null : dateFrom.atStartOfDay();
        LocalDateTime toExclusive = dateTo == null ? null : dateTo.plusDays(1).atStartOfDay();

        long total = mapper.countMyDones(applyIds, keyword, allocDim, bizKind, fromDt, toExclusive);
        if (total == 0) {
            return PageResult.of(pageNo, pageSize, 0L, Collections.emptyList());
        }
        int offset = (pageNo - 1) * pageSize;
        List<PerfAllocAdjustApply> applies = mapper.selectMyDones(
                applyIds, keyword, allocDim, bizKind, fromDt, toExclusive, offset, pageSize);

        List<String> pageKeys = applies.stream()
                .map(a -> BUSINESS_KEY_PREFIX + a.getId())
                .collect(Collectors.toList());
        Map<String, TaskRespDTO> metaMap = workflowTodoApi.findDoneTaskRespByBusinessKeys(empId, pageKeys);

        List<AdjustTodoRespDTO> records = applies.stream()
                .map(a -> mergeToDto(a, metaMap.get(BUSINESS_KEY_PREFIX + a.getId())))
                .collect(Collectors.toList());

        return PageResult.of(pageNo, pageSize, total, records);
    }

    private AdjustTodoRespDTO mergeToDto(PerfAllocAdjustApply a, TaskRespDTO t) {
        AdjustTodoRespDTO d = new AdjustTodoRespDTO();
        d.setId(a.getId());
        d.setApplyNo(a.getApplyNo());
        d.setCustId(a.getCustId());
        d.setAllocDim(a.getAllocDim());
        d.setBizKind(a.getBizKind());
        d.setOwnerOrgId(a.getOwnerOrgId());
        d.setCreatedBy(a.getCreatedBy());
        d.setCreatedTime(a.getCreatedTime());
        d.setBusinessKey(a.getBusinessKey());
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

- [ ] **Step 4: 建 AllocAdjustDoneController**

```java
package com.bank.branch.platform.performance.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.performance.controller.dto.AdjustTodoRespDTO;
import com.bank.branch.platform.performance.service.adjust.AllocAdjustDoneService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Slf4j
@Tag(name = "业绩调整-已审批")
@RestController
@RequestMapping("/api/perf/alloc-adjust")
@RequiredArgsConstructor
public class AllocAdjustDoneController {

    private final AllocAdjustDoneService service;
    private final CurrentUserApi currentUserApi;

    @Operation(summary = "已审批列表（业绩调整）")
    @GetMapping("/my-done")
    @BizAuth(bizType = BizType.PERF_CONFIG, action = BizAction.LIST)
    public ResponseWrapper<PageResult<AdjustTodoRespDTO>> listMyDones(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "allocDim", required = false) String allocDim,
            @RequestParam(value = "bizKind", required = false) String bizKind,
            @RequestParam(value = "dateFrom", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(value = "dateTo", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {

        String empId = currentUserApi.getCurrentEmpId();
        if (pageSize > 100) pageSize = 100;
        if (pageNo < 1) pageNo = 1;
        log.info("[AllocAdjustDoneController.listMyDones] empId={}, kw={}, dim={}, kind={}, from={}, to={}, page={}/{}",
                empId, keyword, allocDim, bizKind, dateFrom, dateTo, pageNo, pageSize);

        PageResult<AdjustTodoRespDTO> r = service.listMyDones(
                empId, keyword, allocDim, bizKind, dateFrom, dateTo, pageNo, pageSize);
        return ResponseWrapper.success(r);
    }
}
```

- [ ] **Step 5: AllocAdjustDoneServiceTest（5 case，复用 V1 todo 测试模式）**

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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AllocAdjustDoneServiceTest {

    @Mock TodoQueryApi workflowTodoApi;
    @Mock PerfAllocAdjustTodoMapper mapper;
    @InjectMocks AllocAdjustDoneService service;

    private static final String EMP = "E001";
    private static final String BIZ_TYPE = "ALLOC_ADJUST";

    @Test
    void d1_emptyDoneKeys_returnsEmpty() {
        when(workflowTodoApi.listMyDoneBusinessKeys(EMP, BIZ_TYPE)).thenReturn(Collections.emptyList());

        PageResult<AdjustTodoRespDTO> r = service.listMyDones(EMP, null, null, null, null, null, 1, 20);

        assertThat(r.getTotal()).isEqualTo(0);
        verify(mapper, never()).countMyDones(any(), any(), any(), any(), any(), any());
    }

    @Test
    void d2_allFiltersHits() {
        when(workflowTodoApi.listMyDoneBusinessKeys(EMP, BIZ_TYPE)).thenReturn(Arrays.asList("ALLOC_ADJUST:A1"));
        when(mapper.countMyDones(eq(Arrays.asList("A1")), eq("kw"), eq("CUST"), eq("LOAN"), any(), any())).thenReturn(1L);
        when(mapper.selectMyDones(eq(Arrays.asList("A1")), eq("kw"), eq("CUST"), eq("LOAN"), any(), any(), eq(0), eq(20)))
                .thenReturn(Arrays.asList(buildApply("A1", "ADJ001")));
        Map<String, TaskRespDTO> meta = new HashMap<>();
        meta.put("ALLOC_ADJUST:A1", buildTask("T1"));
        when(workflowTodoApi.findDoneTaskRespByBusinessKeys(eq(EMP), eq(Arrays.asList("ALLOC_ADJUST:A1")))).thenReturn(meta);

        PageResult<AdjustTodoRespDTO> r = service.listMyDones(EMP, "kw", "CUST", "LOAN",
                java.time.LocalDate.of(2026, 5, 1), java.time.LocalDate.of(2026, 5, 21), 1, 20);

        assertThat(r.getRecords().get(0).getTaskId()).isEqualTo("T1");
    }

    @Test
    void d3_workflowMoreThanDb() {
        when(workflowTodoApi.listMyDoneBusinessKeys(EMP, BIZ_TYPE))
                .thenReturn(Arrays.asList("ALLOC_ADJUST:A1", "ALLOC_ADJUST:A2", "ALLOC_ADJUST:A3"));
        when(mapper.countMyDones(any(), any(), any(), any(), any(), any())).thenReturn(2L);
        when(mapper.selectMyDones(any(), any(), any(), any(), any(), any(), eq(0), eq(20)))
                .thenReturn(Arrays.asList(buildApply("A1", "ADJ001"), buildApply("A2", "ADJ002")));
        when(workflowTodoApi.findDoneTaskRespByBusinessKeys(any(), any())).thenReturn(Collections.emptyMap());

        PageResult<AdjustTodoRespDTO> r = service.listMyDones(EMP, null, null, null, null, null, 1, 20);

        assertThat(r.getTotal()).isEqualTo(2);
    }

    @Test
    void d4_metaMissing_noNpe() {
        when(workflowTodoApi.listMyDoneBusinessKeys(EMP, BIZ_TYPE)).thenReturn(Arrays.asList("ALLOC_ADJUST:A1"));
        when(mapper.countMyDones(any(), any(), any(), any(), any(), any())).thenReturn(1L);
        when(mapper.selectMyDones(any(), any(), any(), any(), any(), any(), eq(0), eq(20)))
                .thenReturn(Arrays.asList(buildApply("A1", "ADJ001")));
        when(workflowTodoApi.findDoneTaskRespByBusinessKeys(any(), any())).thenReturn(Collections.emptyMap());

        PageResult<AdjustTodoRespDTO> r = service.listMyDones(EMP, null, null, null, null, null, 1, 20);

        assertThat(r.getRecords().get(0).getTaskId()).isNull();
    }

    @Test
    void d5_pagination() {
        when(workflowTodoApi.listMyDoneBusinessKeys(EMP, BIZ_TYPE)).thenReturn(Arrays.asList("ALLOC_ADJUST:A1"));
        when(mapper.countMyDones(any(), any(), any(), any(), any(), any())).thenReturn(30L);
        when(mapper.selectMyDones(any(), any(), any(), any(), any(), any(), eq(10), eq(10)))
                .thenReturn(Collections.nCopies(10, buildApply("A1", "ADJ001")));
        when(workflowTodoApi.findDoneTaskRespByBusinessKeys(any(), any())).thenReturn(Collections.emptyMap());

        PageResult<AdjustTodoRespDTO> r = service.listMyDones(EMP, null, null, null, null, null, 2, 10);

        assertThat(r.getTotal()).isEqualTo(30);
        assertThat(r.getRecords()).hasSize(10);
        assertThat(r.getPageNo()).isEqualTo(2);
    }

    private PerfAllocAdjustApply buildApply(String id, String applyNo) {
        PerfAllocAdjustApply a = new PerfAllocAdjustApply();
        a.setId(id);
        a.setApplyNo(applyNo);
        a.setCustId("C1");
        a.setAllocDim("CUST");
        a.setBizKind("LOAN");
        a.setOwnerOrgId("ORG_001");
        a.setCreatedBy("E001");
        a.setCreatedTime(LocalDateTime.of(2026, 5, 21, 10, 0));
        a.setBusinessKey("ALLOC_ADJUST:" + id);
        return a;
    }

    private TaskRespDTO buildTask(String taskId) {
        TaskRespDTO t = new TaskRespDTO();
        t.setTaskId(taskId);
        t.setBusinessKey("ALLOC_ADJUST:A1");
        return t;
    }
}
```

- [ ] **Step 6: 跑 test 验证 5/5**

```bash
cd /home/djdev/lf/yiti && mvn test -pl performance-engine-center -Dtest=AllocAdjustDoneServiceTest
```

- [ ] **Step 7: commit**

```bash
git -C /home/djdev/lf/yiti add performance-engine-center/
git -C /home/djdev/lf/yiti commit -m "feat(perf): 业绩调整-已审批 my-done 端点 + 单测 D1-D5"
```

---

### Task 4 (C4): wangyq SQL PT_RESOURCE

**Files:**
- Create: `docs/superpowers/sql/2026-05-21-pt-resource-mine-done.sql`

- [ ] **Step 1: 写 SQL**

```sql
-- 业绩调整 - 我的申请 / 已审批 端点登记
-- 配合 GET /api/perf/alloc-adjust/my-applies + /my-done
-- 部署前手工 mysql 跑

INSERT INTO PT_RESOURCE (RESOURCE_ID, RESOURCE_URL, RESOURCE_METHOD, RESOURCE_NAME,
                         BIZ_TYPE, BIZ_ACTION, IS_MENU, RECORD_STATUS, SYS_CODE,
                         CREATE_TIME, UPDATE_TIME)
VALUES
  ('R_PERF_ADJ_MINE_LIST', '/api/perf/alloc-adjust/my-applies', 'GET', '业绩调整-我的申请',
   'PERF_CONFIG', 'LIST', 0, 0, 'PLATFORM', NOW(), NOW()),
  ('R_PERF_ADJ_DONE_LIST', '/api/perf/alloc-adjust/my-done', 'GET', '业绩调整-已审批',
   'PERF_CONFIG', 'LIST', 0, 0, 'PLATFORM', NOW(), NOW())
ON DUPLICATE KEY UPDATE UPDATE_TIME = NOW();

-- 绑给角色（mine 任何登录用户都该能看自己；done 限审批角色）
INSERT IGNORE INTO PT_ROLE_RESOURCE (ROLE_ID, RESOURCE_ID)
SELECT r.ROLE_ID, 'R_PERF_ADJ_MINE_LIST'
  FROM PT_ROLE r WHERE r.RECORD_STATUS = 0;

INSERT IGNORE INTO PT_ROLE_RESOURCE (ROLE_ID, RESOURCE_ID)
SELECT r.ROLE_ID, 'R_PERF_ADJ_DONE_LIST'
  FROM PT_ROLE r
 WHERE r.ROLE_CODE IN ('SYS_ADMIN', 'ADJUST_APPROVER', 'BIZ_DEPT_MANAGER')
   AND r.RECORD_STATUS = 0;
```

- [ ] **Step 2: commit**

```bash
git -C /home/djdev/wangyq/yiti add docs/superpowers/sql/2026-05-21-pt-resource-mine-done.sql
git -C /home/djdev/wangyq/yiti commit -m "chore(sql): PT_RESOURCE 登记 my-applies + my-done 2 端点"
```

---

### Task 5 (C5): wangyq 前端 perf.js + Adjust.vue mine/done tab

**Files:**
- Modify: `xanzc_frontend/src/api/perf.js` (加 2 wrapper)
- Modify: `xanzc_frontend/src/views/perf/Adjust.vue` (mine/done tab 表单 + 分页 + 切接口)

- [ ] **Step 1: api/perf.js 加 2 wrapper**

在 `listMyAdjustTodos` 后追加：

```js
/**
 * 业绩调整 - 我的申请（后端分页 + 5 字段过滤，createdBy 后端硬约束）.
 */
export function listMyAdjustApplies(params = {}) {
  return call('get', '/perf/alloc-adjust/my-applies',
    { params: { pageNo: 1, pageSize: 20, ...params } },
    { total: 0, records: [], pageNo: 1, pageSize: 20 }
  );
}

/**
 * 业绩调整 - 已审批（后端分页 + 4 字段过滤）.
 */
export function listMyAdjustDones(params = {}) {
  return call('get', '/perf/alloc-adjust/my-done',
    { params: { pageNo: 1, pageSize: 20, ...params } },
    { total: 0, records: [], pageNo: 1, pageSize: 20 }
  );
}
```

- [ ] **Step 2: Adjust.vue 顶部 import 加 2 个 wrapper + 删 listAdjusts/listDoneTasks（如果不再用）**

把：
```js
import {
  listAdjusts, submitAdjust, withdrawAdjust, getAdjustDetail,
  getAdjustApprovalHistory, listMyAdjustTodos
} from '@/api/perf';
import { listDoneTasks, approveTask, rejectTask, claimTask } from '@/api/workflow';
```

改为：
```js
import {
  submitAdjust, withdrawAdjust, getAdjustDetail,
  getAdjustApprovalHistory, listMyAdjustTodos, listMyAdjustApplies, listMyAdjustDones
} from '@/api/perf';
import { approveTask, rejectTask, claimTask } from '@/api/workflow';
```

（删 listAdjusts + listDoneTasks）

- [ ] **Step 3: 重写 mine tab 的 reactive state + reloadMine**

替换 `// ============ 我的申请 ============` 区块：

```js
// ============ 我的申请 ============
const rows = ref([]);
const loading = ref(false);
const mineFilters = reactive({
  keyword: '',
  allocDim: '',
  bizKind: '',
  status: '',
  dateRange: null,
});
const minePager = reactive({
  pageNo: 1,
  pageSize: 20,
  total: 0,
});
async function reloadMine() {
  loading.value = true;
  try {
    const params = {
      keyword: mineFilters.keyword || undefined,
      allocDim: mineFilters.allocDim || undefined,
      bizKind: mineFilters.bizKind || undefined,
      status: mineFilters.status || undefined,
      dateFrom: mineFilters.dateRange?.[0] || undefined,
      dateTo: mineFilters.dateRange?.[1] || undefined,
      pageNo: minePager.pageNo,
      pageSize: minePager.pageSize,
    };
    const r = await listMyAdjustApplies(params);
    rows.value = r.records || [];
    minePager.total = r.total || 0;
  } catch (err) {
    console.error('[reloadMine] failed', err);
    rows.value = [];
    minePager.total = 0;
  } finally {
    loading.value = false;
  }
}
function resetMineFilters() {
  mineFilters.keyword = '';
  mineFilters.allocDim = '';
  mineFilters.bizKind = '';
  mineFilters.status = '';
  mineFilters.dateRange = null;
  minePager.pageNo = 1;
  reloadMine();
}
function onMineFilterChange() {
  minePager.pageNo = 1;
  reloadMine();
}
```

- [ ] **Step 4: 重写 done tab 的 reactive state + reloadDone**

替换 `// ============ 已审批 ============` 区块：

```js
// ============ 已审批（已办） ============
const dones = ref([]);
const doneLoading = ref(false);
const doneFilters = reactive({
  keyword: '',
  allocDim: '',
  bizKind: '',
  dateRange: null,
});
const donePager = reactive({
  pageNo: 1,
  pageSize: 20,
  total: 0,
});
async function reloadDone() {
  doneLoading.value = true;
  try {
    const params = {
      keyword: doneFilters.keyword || undefined,
      allocDim: doneFilters.allocDim || undefined,
      bizKind: doneFilters.bizKind || undefined,
      dateFrom: doneFilters.dateRange?.[0] || undefined,
      dateTo: doneFilters.dateRange?.[1] || undefined,
      pageNo: donePager.pageNo,
      pageSize: donePager.pageSize,
    };
    const r = await listMyAdjustDones(params);
    dones.value = r.records || [];
    donePager.total = r.total || 0;
  } catch (err) {
    console.error('[reloadDone] failed', err);
    dones.value = [];
    donePager.total = 0;
  } finally {
    doneLoading.value = false;
  }
}
function resetDoneFilters() {
  doneFilters.keyword = '';
  doneFilters.allocDim = '';
  doneFilters.bizKind = '';
  doneFilters.dateRange = null;
  donePager.pageNo = 1;
  reloadDone();
}
function onDoneFilterChange() {
  donePager.pageNo = 1;
  reloadDone();
}
```

- [ ] **Step 5: 改 mine tab template 加表单 + 分页**

替换 mine tab `<el-tab-pane label="我的申请" name="mine">` 整块 template（line 16-58 区域）的开头到 table 前 + table 后加分页。具体：

在 mine `<el-tab-pane>` 内 `<el-table>` 前插入：

```html
<div class="card-section">
  <el-form inline size="default">
    <el-form-item label="关键字">
      <el-input v-model="mineFilters.keyword" placeholder="申请编号 / 客户 ID" clearable
                style="width:200px" @keyup.enter="onMineFilterChange" />
    </el-form-item>
    <el-form-item label="维度">
      <el-select v-model="mineFilters.allocDim" clearable placeholder="全部" style="width:140px"
                 @change="onMineFilterChange">
        <el-option value="CUST" label="客户" />
        <el-option value="ORG" label="机构" />
        <el-option value="EMP" label="员工" />
      </el-select>
    </el-form-item>
    <el-form-item label="业务类型">
      <el-select v-model="mineFilters.bizKind" clearable placeholder="全部" style="width:160px"
                 @change="onMineFilterChange">
        <el-option value="LOAN" label="贷款" />
        <el-option value="DEPOSIT" label="存款" />
        <el-option value="SUPPORT" label="支援" />
      </el-select>
    </el-form-item>
    <el-form-item label="状态">
      <el-select v-model="mineFilters.status" clearable placeholder="全部" style="width:140px"
                 @change="onMineFilterChange">
        <el-option value="DRAFT" label="草稿" />
        <el-option value="IN_APPROVAL" label="审批中" />
        <el-option value="APPROVED" label="已通过" />
        <el-option value="REJECTED" label="已驳回" />
        <el-option value="WITHDRAWN" label="已撤回" />
      </el-select>
    </el-form-item>
    <el-form-item label="申请时间">
      <el-date-picker v-model="mineFilters.dateRange" type="daterange" value-format="YYYY-MM-DD"
                      range-separator="~" start-placeholder="开始" end-placeholder="结束"
                      style="width:240px" @change="onMineFilterChange" />
    </el-form-item>
    <el-form-item>
      <el-button type="primary" @click="onMineFilterChange">查询</el-button>
      <el-button @click="resetMineFilters">重置</el-button>
    </el-form-item>
  </el-form>
</div>
```

在 mine table `</el-table>` 后加分页（在 `</div>` 闭合 card-section 之前）：

```html
<div class="pager">
  <el-pagination
    v-model:current-page="minePager.pageNo"
    v-model:page-size="minePager.pageSize"
    :page-sizes="[10, 20, 50]"
    :total="minePager.total"
    background
    layout="total, sizes, prev, pager, next, jumper"
    @size-change="reloadMine"
    @current-change="reloadMine"
  />
</div>
```

- [ ] **Step 6: 改 done tab template 加表单 + 分页**

同 mine 模式，在 done `<el-tab-pane label="已审批" name="done">` 内 `<el-table>` 前插入 4 字段表单（无 status），table 后加分页：

```html
<div class="card-section">
  <el-form inline size="default">
    <el-form-item label="关键字">
      <el-input v-model="doneFilters.keyword" placeholder="申请编号 / 客户 ID" clearable
                style="width:200px" @keyup.enter="onDoneFilterChange" />
    </el-form-item>
    <el-form-item label="维度">
      <el-select v-model="doneFilters.allocDim" clearable placeholder="全部" style="width:140px"
                 @change="onDoneFilterChange">
        <el-option value="CUST" label="客户" />
        <el-option value="ORG" label="机构" />
        <el-option value="EMP" label="员工" />
      </el-select>
    </el-form-item>
    <el-form-item label="业务类型">
      <el-select v-model="doneFilters.bizKind" clearable placeholder="全部" style="width:160px"
                 @change="onDoneFilterChange">
        <el-option value="LOAN" label="贷款" />
        <el-option value="DEPOSIT" label="存款" />
        <el-option value="SUPPORT" label="支援" />
      </el-select>
    </el-form-item>
    <el-form-item label="申请时间">
      <el-date-picker v-model="doneFilters.dateRange" type="daterange" value-format="YYYY-MM-DD"
                      range-separator="~" start-placeholder="开始" end-placeholder="结束"
                      style="width:240px" @change="onDoneFilterChange" />
    </el-form-item>
    <el-form-item>
      <el-button type="primary" @click="onDoneFilterChange">查询</el-button>
      <el-button @click="resetDoneFilters">重置</el-button>
    </el-form-item>
  </el-form>
</div>
```

分页器：

```html
<div class="pager">
  <el-pagination
    v-model:current-page="donePager.pageNo"
    v-model:page-size="donePager.pageSize"
    :page-sizes="[10, 20, 50]"
    :total="donePager.total"
    background
    layout="total, sizes, prev, pager, next, jumper"
    @size-change="reloadDone"
    @current-change="reloadDone"
  />
</div>
```

- [ ] **Step 7: build 验证编译**

```bash
cd /home/djdev/wangyq/yiti/xanzc_frontend && timeout 30 npx vite build --logLevel error 2>&1 | tail -5
```
Expected: 无 ERROR（sass deprecation warning OK）。

- [ ] **Step 8: commit**

```bash
git -C /home/djdev/wangyq/yiti add xanzc_frontend/src/api/perf.js xanzc_frontend/src/views/perf/Adjust.vue
git -C /home/djdev/wangyq/yiti commit -m "feat(perf-adjust): 我的申请+已审批 tab 加查询条件 + 分页 + 切新接口"
```

---

## §3 验收 checklist（用户视角）

- [ ] mine tab 5 字段筛选都生效（关键字/维度/业务类型/状态/申请时间）
- [ ] done tab 4 字段筛选都生效
- [ ] 两 tab 分页翻页 / 改 pageSize / 重置 OK
- [ ] mine tab 验证 createdBy 防越权（curl 直接打 endpoint 带 createdBy=别人 → 后端仍只返自己的）
- [ ] V1 todo tab 行为无回归（工作台跳转 + 自动弹审批仍工作）
- [ ] 2 条 PT_RESOURCE SQL 已跑

## §4 不做的事

- 不动 DB schema
- 不动 V1 todo tab
- 不重命名 mapper / DTO
- 不为 mine 加 task 字段渲染（DTO 留 null 即可）
