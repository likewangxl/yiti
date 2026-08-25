package com.bank.branch.platform.customer.service;

import com.bank.branch.platform.auth.api.dto.DataScopeContext;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.security.enums.DataScopeType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.customer.entity.CustLead;
import com.bank.branch.platform.customer.mapper.CustLeadMapper;
import com.bank.branch.platform.workflow.api.WorkflowQueryApi;
import com.bank.branch.platform.workflow.api.dto.TaskRespDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 0811 会议纪要一期：线索审批列表与已审批记录 Excel 导出。 */
@ExtendWith(MockitoExtension.class)
class LeadApprovalServiceTest {

    @Mock private WorkflowQueryApi workflowQueryApi;
    @Mock private CustLeadMapper leadMapper;
    @InjectMocks private LeadApprovalService service;

    @Test
    void list_historyDelegatesToWorkflowDoneList() {
        TaskRespDTO task = new TaskRespDTO();
        task.setBizId("LEAD-1");
        PageResult<TaskRespDTO> expected = PageResult.of(1, 20, 1, List.of(task));
        when(workflowQueryApi.queryDoneList("E001", "LEAD", "科技", 1, 20)).thenReturn(expected);

        PageResult<TaskRespDTO> actual = service.list("HISTORY", "科技", 1, 20, "E001");

        assertThat(actual).isSameAs(expected);
        verify(workflowQueryApi).queryDoneList("E001", "LEAD", "科技", 1, 20);
    }

    @Test
    void exportReviewed_includesApprovedAndRejectedRecordsAsXlsx() {
        CustLead lead = new CustLead();
        lead.setLeadNo("LEAD-001");
        lead.setCustName("测试科技");
        lead.setUnifiedCreditCode("91610131MA6U12345X");
        lead.setCreditAmount(new BigDecimal("1000000"));
        lead.setCreditExposureAmount(new BigDecimal("300000"));
        lead.setLeadStatus("APPROVED");
        DataScopeContext scope = new DataScopeContext(
                DataScopeType.ORG_SUBTREE, "E001", "BR001", Set.of("BR001", "SUB001"),
                BizType.LEAD, BizAction.EXPORT);
        when(leadMapper.selectReviewedForExport(
                null, null, "ORG_SUBTREE", "E001", Set.of("BR001", "SUB001")))
                .thenReturn(List.of(lead));

        byte[] bytes = service.exportReviewed(null, null, scope);

        assertThat(bytes).hasSizeGreaterThan(100);
        assertThat(bytes[0]).isEqualTo((byte) 'P');
        assertThat(bytes[1]).isEqualTo((byte) 'K');
        verify(leadMapper).selectReviewedForExport(
                null, null, "ORG_SUBTREE", "E001", Set.of("BR001", "SUB001"));
    }
}
