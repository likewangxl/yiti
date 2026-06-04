package com.bank.branch.platform.performance.facade;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.customer.api.CustomerQueryApi;
import com.bank.branch.platform.performance.api.dto.AllocAdjustApprovalItemDTO;
import com.bank.branch.platform.performance.entity.PerfAllocAdjustApply;
import com.bank.branch.platform.performance.mapper.PerfAllocAdjustApplyMapper;
import com.bank.branch.platform.performance.service.adjust.AllocAdjustDoneService;
import com.bank.branch.platform.performance.service.adjust.AllocAdjustTodoService;
import com.bank.branch.platform.portal.api.AddressBookApi;
import com.bank.branch.platform.portal.api.dto.EmployeeDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PerfApprovalQueryFacadeMyApplyTest {

    @Mock private AllocAdjustTodoService allocAdjustTodoService;
    @Mock private AllocAdjustDoneService allocAdjustDoneService;
    @Mock private AddressBookApi addressBookApi;
    @Mock private CustomerQueryApi customerQueryApi;
    @Mock private PerfAllocAdjustApplyMapper allocAdjustApplyMapper;

    @InjectMocks private PerfApprovalQueryFacade facade;

    @Test
    void listMyApplications_filtersByCreatedBy_andMapsSnapshotFields() {
        PerfAllocAdjustApply e = new PerfAllocAdjustApply();
        e.setId("PA_900");
        e.setApplyNo("AA20260604001");
        e.setCustId("C001");
        e.setCustName("某某客户");
        e.setCreatedBy("U001");
        e.setStatus("WITHDRAWN");
        e.setCreatedTime(LocalDateTime.of(2026, 6, 4, 10, 0, 0));

        when(allocAdjustApplyMapper.selectByConditions(
                eq(null), eq(null), eq(null), eq(null), eq("U001"), anyInt(), anyInt()))
                .thenReturn(List.of(e));
        when(allocAdjustApplyMapper.countByConditions(
                eq(null), eq(null), eq(null), eq(null), eq("U001")))
                .thenReturn(1L);
        when(addressBookApi.getEmployee("U001"))
                .thenReturn(Optional.of(EmployeeDTO.builder().empName("张三").build()));

        PageResult<AllocAdjustApprovalItemDTO> page =
                facade.listMyAllocAdjustApplications("U001", 1, 15);

        assertThat(page.getTotal()).isEqualTo(1L);
        AllocAdjustApprovalItemDTO item = page.getRecords().get(0);
        assertThat(item.getPerfAdjustNo()).isEqualTo("PA_900");
        assertThat(item.getCustName()).isEqualTo("某某客户");
        assertThat(item.getApplyFullname()).isEqualTo("张三");
        assertThat(item.getStatus()).isEqualTo("WITHDRAWN");
        assertThat(item.getCategory()).isEqualTo("MINE");
    }
}
