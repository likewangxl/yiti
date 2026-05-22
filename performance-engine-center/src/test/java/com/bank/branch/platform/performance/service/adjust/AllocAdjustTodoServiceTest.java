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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AllocAdjustTodoService 单测：覆盖 plan §8.1 T1-T8。
 */
@ExtendWith(MockitoExtension.class)
class AllocAdjustTodoServiceTest {

    @Mock TodoQueryApi workflowTodoApi;
    @Mock PerfAllocAdjustTodoMapper mapper;

    @InjectMocks AllocAdjustTodoService service;

    private static final String EMP = "E001";
    private static final String BIZ_TYPE = "ALLOC_ADJUST";

    @Test
    void t1_emptyTodoKeys_returnsEmptyWithoutDbHit() {
        when(workflowTodoApi.listMyTodoBusinessKeys(EMP, BIZ_TYPE)).thenReturn(Collections.emptyList());

        PageResult<AdjustTodoRespDTO> r = service.listMyTodos(EMP, null, null, null, null, null, 1, 20);

        assertThat(r.getTotal()).isEqualTo(0);
        assertThat(r.getRecords()).isEmpty();
        verify(mapper, never()).countMyTodos(any(), any(), any(), any(), any(), any());
    }

    @Test
    void t2_allFiltersHitsOne() {
        when(workflowTodoApi.listMyTodoBusinessKeys(EMP, BIZ_TYPE))
                .thenReturn(Arrays.asList("ALLOC_ADJUST:A1"));
        when(mapper.countMyTodos(eq(Arrays.asList("A1")), eq("kw"), eq("CUST"), eq("LOAN"), any(), any()))
                .thenReturn(1L);
        PerfAllocAdjustApply a = buildApply("A1", "ADJ001", "C1", "CUST", "LOAN");
        when(mapper.selectMyTodos(eq(Arrays.asList("A1")), eq("kw"), eq("CUST"), eq("LOAN"), any(), any(), eq(0), eq(20)))
                .thenReturn(Arrays.asList(a));
        Map<String, TaskRespDTO> meta = new HashMap<>();
        meta.put("ALLOC_ADJUST:A1", buildTask("T1", "biz_dept_review"));
        when(workflowTodoApi.findTaskRespByBusinessKeys(eq(EMP), eq(Arrays.asList("ALLOC_ADJUST:A1")))).thenReturn(meta);

        PageResult<AdjustTodoRespDTO> r = service.listMyTodos(EMP, "kw", "CUST", "LOAN",
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 21), 1, 20);

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
        when(workflowTodoApi.listMyTodoBusinessKeys(EMP, BIZ_TYPE))
                .thenReturn(Arrays.asList("ALLOC_ADJUST:A1", "ALLOC_ADJUST:A2"));
        when(mapper.countMyTodos(any(), eq("ADJ"), isNull(), isNull(), isNull(), isNull())).thenReturn(2L);
        when(mapper.selectMyTodos(any(), eq("ADJ"), isNull(), isNull(), isNull(), isNull(), eq(0), eq(20)))
                .thenReturn(Arrays.asList(buildApply("A1", "ADJ001", "C1", "CUST", "LOAN"),
                                          buildApply("A2", "ADJ002", "C2", "ORG", "DEPOSIT")));
        when(workflowTodoApi.findTaskRespByBusinessKeys(any(), any())).thenReturn(Collections.emptyMap());

        PageResult<AdjustTodoRespDTO> r = service.listMyTodos(EMP, "ADJ", null, null, null, null, 1, 20);

        assertThat(r.getTotal()).isEqualTo(2);
        assertThat(r.getRecords()).hasSize(2);
    }

    @Test
    void t4_allocDimFilter() {
        when(workflowTodoApi.listMyTodoBusinessKeys(EMP, BIZ_TYPE)).thenReturn(Arrays.asList("ALLOC_ADJUST:A1"));
        when(mapper.countMyTodos(any(), isNull(), eq("CUST"), isNull(), isNull(), isNull())).thenReturn(1L);
        when(mapper.selectMyTodos(any(), isNull(), eq("CUST"), isNull(), isNull(), isNull(), eq(0), eq(20)))
                .thenReturn(Arrays.asList(buildApply("A1", "ADJ001", "C1", "CUST", "LOAN")));
        when(workflowTodoApi.findTaskRespByBusinessKeys(any(), any())).thenReturn(Collections.emptyMap());

        PageResult<AdjustTodoRespDTO> r = service.listMyTodos(EMP, null, "CUST", null, null, null, 1, 20);

        assertThat(r.getRecords().get(0).getAllocDim()).isEqualTo("CUST");
    }

    @Test
    void t5_dateRangeClosedInterval_passesDateToPlusOneDay() {
        when(workflowTodoApi.listMyTodoBusinessKeys(EMP, BIZ_TYPE)).thenReturn(Arrays.asList("ALLOC_ADJUST:A1"));
        when(mapper.countMyTodos(any(), any(), any(), any(),
                eq(LocalDateTime.of(2026, 5, 21, 0, 0)),
                eq(LocalDateTime.of(2026, 5, 22, 0, 0)))).thenReturn(0L);

        service.listMyTodos(EMP, null, null, null,
                LocalDate.of(2026, 5, 21),
                LocalDate.of(2026, 5, 21), 1, 20);

        verify(mapper).countMyTodos(any(), any(), any(), any(),
                eq(LocalDateTime.of(2026, 5, 21, 0, 0)),
                eq(LocalDateTime.of(2026, 5, 22, 0, 0)));
    }

    @Test
    void t6_pagination_page2Size10() {
        when(workflowTodoApi.listMyTodoBusinessKeys(EMP, BIZ_TYPE))
                .thenReturn(Arrays.asList("ALLOC_ADJUST:A1"));
        when(mapper.countMyTodos(any(), any(), any(), any(), any(), any())).thenReturn(15L);
        when(mapper.selectMyTodos(any(), any(), any(), any(), any(), any(), eq(10), eq(10)))
                .thenReturn(Collections.nCopies(5, buildApply("A1", "ADJ001", "C1", "CUST", "LOAN")));
        when(workflowTodoApi.findTaskRespByBusinessKeys(any(), any())).thenReturn(Collections.emptyMap());

        PageResult<AdjustTodoRespDTO> r = service.listMyTodos(EMP, null, null, null, null, null, 2, 10);

        assertThat(r.getTotal()).isEqualTo(15);
        assertThat(r.getRecords()).hasSize(5);
        assertThat(r.getPageNo()).isEqualTo(2);
    }

    @Test
    void t7_workflowReturns5ButDbOnly3() {
        when(workflowTodoApi.listMyTodoBusinessKeys(EMP, BIZ_TYPE))
                .thenReturn(Arrays.asList("ALLOC_ADJUST:A1", "ALLOC_ADJUST:A2", "ALLOC_ADJUST:A3",
                                          "ALLOC_ADJUST:A4", "ALLOC_ADJUST:A5"));
        when(mapper.countMyTodos(any(), any(), any(), any(), any(), any())).thenReturn(3L);
        when(mapper.selectMyTodos(any(), any(), any(), any(), any(), any(), eq(0), eq(20)))
                .thenReturn(Arrays.asList(buildApply("A1", "ADJ001", "C1", "CUST", "LOAN"),
                                          buildApply("A2", "ADJ002", "C2", "ORG", "DEPOSIT"),
                                          buildApply("A3", "ADJ003", "C3", "EMP", "SUPPORT")));
        when(workflowTodoApi.findTaskRespByBusinessKeys(any(), any())).thenReturn(Collections.emptyMap());

        PageResult<AdjustTodoRespDTO> r = service.listMyTodos(EMP, null, null, null, null, null, 1, 20);

        assertThat(r.getTotal()).isEqualTo(3);
        assertThat(r.getRecords()).hasSize(3);
    }

    @Test
    void t8_taskMetaMissing_setsNullsNoNpe() {
        when(workflowTodoApi.listMyTodoBusinessKeys(EMP, BIZ_TYPE)).thenReturn(Arrays.asList("ALLOC_ADJUST:A1"));
        when(mapper.countMyTodos(any(), any(), any(), any(), any(), any())).thenReturn(1L);
        when(mapper.selectMyTodos(any(), any(), any(), any(), any(), any(), eq(0), eq(20)))
                .thenReturn(Arrays.asList(buildApply("A1", "ADJ001", "C1", "CUST", "LOAN")));
        when(workflowTodoApi.findTaskRespByBusinessKeys(any(), any())).thenReturn(Collections.emptyMap());

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
