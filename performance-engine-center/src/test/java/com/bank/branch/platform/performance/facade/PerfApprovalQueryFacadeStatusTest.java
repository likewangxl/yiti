package com.bank.branch.platform.performance.facade;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.customer.api.CustomerQueryApi;
import com.bank.branch.platform.performance.api.dto.AllocAdjustApprovalItemDTO;
import com.bank.branch.platform.performance.controller.dto.AdjustTodoRespDTO;
import com.bank.branch.platform.performance.mapper.PerfAllocAdjustApplyMapper;
import com.bank.branch.platform.performance.service.adjust.AllocAdjustDoneService;
import com.bank.branch.platform.performance.service.adjust.AllocAdjustTodoService;
import com.bank.branch.platform.portal.api.AddressBookApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PerfApprovalQueryFacadeStatusTest {

    @Mock private AllocAdjustTodoService allocAdjustTodoService;
    @Mock private AllocAdjustDoneService allocAdjustDoneService;
    @Mock private AddressBookApi addressBookApi;
    @Mock private CustomerQueryApi customerQueryApi;
    @Mock private PerfAllocAdjustApplyMapper allocAdjustApplyMapper;

    @InjectMocks private PerfApprovalQueryFacade facade;

    private static AdjustTodoRespDTO todo(String id, String status) {
        AdjustTodoRespDTO d = new AdjustTodoRespDTO();
        d.setId(id);
        d.setStatus(status);
        d.setCreatedTime(LocalDateTime.of(2026, 6, 4, 9, 0, 0));
        return d;
    }

    @Test
    void pending_queriesOnlyTodo_notDone() {
        when(allocAdjustTodoService.listMyTodosByEmp(any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(PageResult.of(1, 500, 1L, List.of(todo("T1", "IN_APPROVAL"))));

        PageResult<AllocAdjustApprovalItemDTO> page =
                facade.listAllocAdjustApprovals("U001", "PENDING", 1, 100);

        assertThat(page.getRecords()).extracting(AllocAdjustApprovalItemDTO::getPerfAdjustNo).containsExactly("T1");
        verify(allocAdjustDoneService, never()).listMyDones(any(), any(), any(), any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void done_queriesOnlyDone_notTodo() {
        when(allocAdjustDoneService.listMyDones(any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(PageResult.of(1, 500, 1L, List.of(todo("D1", "APPROVED"))));

        PageResult<AllocAdjustApprovalItemDTO> page =
                facade.listAllocAdjustApprovals("U001", "DONE", 1, 100);

        assertThat(page.getRecords()).extracting(AllocAdjustApprovalItemDTO::getPerfAdjustNo).containsExactly("D1");
        verify(allocAdjustTodoService, never()).listMyTodosByEmp(any(), any(), any(), any(), any(), any(), anyInt(), anyInt());
    }
}
