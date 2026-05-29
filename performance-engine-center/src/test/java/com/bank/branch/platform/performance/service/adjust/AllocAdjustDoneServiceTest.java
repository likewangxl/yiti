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
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 21), 1, 20);

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

    @Test
    void d6_onlyPersonalWorkflowKeys_noGlobalFinishedUnion() {
        // 仅本人在 workflow 完成的两条业务键，查询用的 applyIds 必须恰好等于这两条（不混入他人完结申请）
        when(workflowTodoApi.listMyDoneBusinessKeys(EMP, BIZ_TYPE))
                .thenReturn(Arrays.asList("ALLOC_ADJUST:A1", "ALLOC_ADJUST:A2"));
        when(mapper.countMyDones(eq(Arrays.asList("A1", "A2")), any(), any(), any(), any(), any())).thenReturn(1L);
        when(mapper.selectMyDones(eq(Arrays.asList("A1", "A2")), any(), any(), any(), any(), any(), eq(0), eq(20)))
                .thenReturn(Arrays.asList(buildApply("A1", "ADJ001")));
        when(workflowTodoApi.findDoneTaskRespByBusinessKeys(any(), any())).thenReturn(Collections.emptyMap());

        PageResult<AdjustTodoRespDTO> r = service.listMyDones(EMP, null, null, null, null, null, 1, 20);

        // countMyDones/selectMyDones 以精确的本人键集合([A1,A2]) 调用 → stub 命中即证明无全局补充
        assertThat(r.getTotal()).isEqualTo(1);
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
