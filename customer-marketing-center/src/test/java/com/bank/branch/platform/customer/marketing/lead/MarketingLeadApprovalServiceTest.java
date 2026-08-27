package com.bank.branch.platform.customer.marketing.lead;

import com.bank.branch.platform.customer.dto.marketing.lead.LeadDetailResponse;
import com.bank.branch.platform.customer.entity.marketing.MarketingLeadInfo;
import com.bank.branch.platform.customer.entity.marketing.MarketingLeadManagerScope;
import com.bank.branch.platform.customer.entity.marketing.MarketingLeadTagRel;
import com.bank.branch.platform.customer.mapper.marketing.MarketingLeadInfoMapper;
import com.bank.branch.platform.customer.mapper.marketing.MarketingLeadManagerScopeMapper;
import com.bank.branch.platform.customer.mapper.marketing.MarketingLeadTagRelMapper;
import com.bank.branch.platform.customer.service.marketing.MarketingLeadApprovalService;
import com.bank.branch.platform.governance.api.FileApi;
import com.bank.branch.platform.governance.api.dto.FileObjectDTO;
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
    @Mock
    private MarketingLeadManagerScopeMapper managerScopeMapper;
    @Mock
    private MarketingLeadTagRelMapper leadTagRelMapper;
    @Mock
    private FileApi fileApi;

    @InjectMocks
    private MarketingLeadApprovalService service;

    @Test
    void pendingFiltersOutTagImportTasks() {
        TaskRespDTO manual = task(11L);
        TaskRespDTO tag = task(12L);
        when(workflowQueryApi.queryTodoList("EMP_1", "LEAD", null, 1, 100))
                .thenReturn(com.bank.branch.platform.common.web.PageResult.of(1, 100, 2,
                        List.of(manual, tag)));
        MarketingLeadInfo manualLead = lead(11L, "MANUAL");
        manualLead.setCustName("示例企业");
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
    void historySeparatesApprovedAndRejectedWithExactTotal() {
        TaskRespDTO approvedTask = task(31L);
        TaskRespDTO rejectedTask = task(32L);
        when(workflowQueryApi.queryDoneList("EMP_1", "LEAD", null, 1, 100))
                .thenReturn(com.bank.branch.platform.common.web.PageResult.of(1, 100, 2,
                        List.of(approvedTask, rejectedTask)));
        MarketingLeadInfo approved = lead(31L, "MANUAL");
        approved.setLeadStatus("APPROVED");
        MarketingLeadInfo rejected = lead(32L, "LEAD_IMPORT");
        rejected.setLeadStatus("REJECTED");
        when(leadMapper.selectActiveById(31L)).thenReturn(approved);
        when(leadMapper.selectActiveById(32L)).thenReturn(rejected);

        var result = service.history(null, "APPROVED", 1, 1, "EMP_1");

        assertEquals(1, result.getTotal());
        assertEquals(1, result.getRecords().size());
        assertEquals(31L, result.getRecords().get(0).getLeadId());
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

    @Test
    void detailReturnsSameReceiverTagAndAttachmentCollectionsAsEntryDetail() {
        MarketingLeadInfo lead = lead(41L, "MANUAL");
        when(leadMapper.selectActiveById(41L)).thenReturn(lead);
        MarketingLeadManagerScope scope = new MarketingLeadManagerScope();
        scope.setManagerEmpId("EMP_2");
        when(managerScopeMapper.selectList(any())).thenReturn(List.of(scope));
        MarketingLeadTagRel tag = new MarketingLeadTagRel();
        tag.setTagId(9L);
        when(leadTagRelMapper.selectList(any())).thenReturn(List.of(tag));
        FileObjectDTO attachment = new FileObjectDTO();
        attachment.setId("FILE-9");
        when(fileApi.listBizFiles("LEAD", "41")).thenReturn(List.of(attachment));

        LeadDetailResponse detail = service.detail(41L, "APPROVER_1");

        assertEquals(List.of("EMP_2"), detail.getManagerEmpIds());
        assertEquals(List.of(9L), detail.getTagIds());
        assertEquals(List.of(attachment), detail.getAttachments());
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
