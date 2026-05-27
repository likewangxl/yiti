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
        AdjustTodoRespDTO row = r.getRecords().get(0);
        assertThat(row.getId()).isEqualTo("A1");
        assertThat(row.getStatus()).isEqualTo("APPROVED");
    }

    @Test
    void m3_statusOnly() {
        when(mapper.countMyApplies(eq(EMP), isNull(), isNull(), isNull(), eq("DRAFT"), isNull(), isNull())).thenReturn(2L);
        PerfAllocAdjustApply a1 = buildApply("A1", "ADJ001", "C1", "CUST", "LOAN");
        a1.setStatus("DRAFT");
        PerfAllocAdjustApply a2 = buildApply("A2", "ADJ002", "C2", "ORG", "DEPOSIT");
        a2.setStatus("DRAFT");
        when(mapper.selectMyApplies(eq(EMP), isNull(), isNull(), isNull(), eq("DRAFT"), isNull(), isNull(), eq(0), eq(20)))
                .thenReturn(Arrays.asList(a1, a2));

        PageResult<AdjustTodoRespDTO> r = service.listMyApplies(EMP, null, null, null, "DRAFT", null, null, 1, 20);

        assertThat(r.getRecords()).hasSize(2);
        assertThat(r.getRecords().get(0).getStatus()).isEqualTo("DRAFT");
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
