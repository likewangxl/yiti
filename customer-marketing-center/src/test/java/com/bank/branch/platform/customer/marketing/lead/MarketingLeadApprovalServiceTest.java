package com.bank.branch.platform.customer.marketing.lead;

import com.bank.branch.platform.customer.entity.marketing.MarketingLeadInfo;
import com.bank.branch.platform.customer.mapper.marketing.MarketingLeadInfoMapper;
import com.bank.branch.platform.customer.service.marketing.MarketingLeadApprovalService;
import com.bank.branch.platform.workflow.api.WorkflowApi;
import com.bank.branch.platform.workflow.api.WorkflowQueryApi;
import com.bank.branch.platform.workflow.api.dto.TaskRespDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 页面四只处理 MANUAL/LEAD_IMPORT 的审批服务契约。 */
@ExtendWith(MockitoExtension.class)
class MarketingLeadApprovalServiceTest {

    @Mock
    private WorkflowQueryApi workflowQueryApi;
    @Mock
    private WorkflowApi workflowApi;
    @Mock
    private MarketingLeadInfoMapper leadMapper;
    @Mock
    private com.bank.branch.platform.customer.mapper.marketing.MarketingCustomerInfoMapper customerMapper;

    @InjectMocks
    private MarketingLeadApprovalService service;

    @Test
    void pendingFiltersOutTagImportTasks() {
        TaskRespDTO manual = task(11L);
        TaskRespDTO tag = task(12L);
        when(workflowQueryApi.queryTodoList("EMP_1", "LEAD", "企业", 1, 20))
                .thenReturn(com.bank.branch.platform.common.web.PageResult.of(1, 20, 2,
                        List.of(manual, tag)));
        MarketingLeadInfo manualLead = lead(11L, "MANUAL");
        MarketingLeadInfo tagLead = lead(12L, "TAG_IMPORT");
        when(leadMapper.selectActiveById(11L)).thenReturn(manualLead);
        when(leadMapper.selectActiveById(12L)).thenReturn(tagLead);

        var result = service.pending("企业", 1, 20, "EMP_1");

        assertEquals(1, result.getRecords().size());
        assertEquals(11L, result.getRecords().get(0).getLeadId());
        assertEquals("NEW_ACCOUNT", result.getRecords().get(0).getLeadType());
        assertEquals("MANUFACTURING", result.getRecords().get(0).getIndustry());
        assertEquals("PUBLIC", result.getRecords().get(0).getDistributionMode());
    }

    @Test
    void approveAndRejectUseConditionalStatusTransition() {
        MarketingLeadInfo lead = lead(21L, "MANUAL");
        lead.setLeadStatus("IN_APPROVAL");
        when(leadMapper.selectForUpdate(21L)).thenReturn(lead);
        when(leadMapper.updateStatusIf(21L, "IN_APPROVAL", "APPROVED", "EMP_1", null)).thenReturn(1);
        service.approve(21L, "TASK_1", "EMP_1", "同意");
        verify(workflowApi).approveByEmp("TASK_1", "EMP_1", "同意");

        when(leadMapper.selectForUpdate(21L)).thenReturn(lead);
        when(leadMapper.updateStatusIf(21L, "IN_APPROVAL", "REJECTED", "EMP_1", "资料不完整"))
                .thenReturn(1);
        service.reject(21L, "TASK_1", "EMP_1", "资料不完整");
        verify(workflowApi).rejectByEmp("TASK_1", "EMP_1", "资料不完整");
    }

    private TaskRespDTO task(long id) {
        TaskRespDTO task = new TaskRespDTO();
        task.setBizId(String.valueOf(id));
        task.setBizType("LEAD");
        return task;
    }

    private MarketingLeadInfo lead(long id, String source) {
        MarketingLeadInfo lead = new MarketingLeadInfo();
        lead.setId(id);
        lead.setLeadSource(source);
        lead.setLeadStatus("IN_APPROVAL");
        lead.setLeadType("NEW_ACCOUNT");
        lead.setIndustry("MANUFACTURING");
        lead.setDistributionMode("PUBLIC");
        return lead;
    }
}
