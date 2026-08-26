package com.bank.branch.platform.customer.marketing.lead;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.customer.dto.marketing.lead.LeadCreateRequest;
import com.bank.branch.platform.customer.dto.marketing.lead.LeadUpdateRequest;
import com.bank.branch.platform.customer.entity.marketing.MarketingLeadInfo;
import com.bank.branch.platform.customer.mapper.marketing.MarketingLeadInfoMapper;
import com.bank.branch.platform.customer.mapper.marketing.MarketingLeadImportBatchMapper;
import com.bank.branch.platform.customer.mapper.marketing.MarketingLeadImportDetailMapper;
import com.bank.branch.platform.customer.service.marketing.MarketingLeadApprovalService;
import com.bank.branch.platform.customer.service.marketing.MarketingLeadEntryService;
import com.bank.branch.platform.workflow.api.WorkflowApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 页面三手工线索服务的状态和防重契约。 */
@ExtendWith(MockitoExtension.class)
class MarketingLeadEntryServiceTest {

    @Mock
    private MarketingLeadInfoMapper leadMapper;
    @Mock
    private com.bank.branch.platform.customer.mapper.marketing.MarketingCustomerInfoMapper customerMapper;
    @Mock
    private WorkflowApi workflowApi;

    @InjectMocks
    private MarketingLeadEntryService service;

    @Test
    void createRejectsAnotherInFlightLeadWithOperatorVisibleConflict() {
        MarketingLeadInfo existing = new MarketingLeadInfo();
        existing.setId(17L);
        existing.setLeadNo("LEAD_OLD");
        existing.setLeadStatus("IN_APPROVAL");
        existing.setEntryEmpId("EMP_OLD");
        existing.setEntryOrgId("ORG_OLD");
        when(leadMapper.selectActiveByCreditCode("91320100ABC1234567")).thenReturn(existing);

        LeadCreateRequest request = new LeadCreateRequest();
        request.setCustName("测试企业");
        request.setUnifiedCreditCode("91320100ABC1234567");

        var ex = assertThrows(RuntimeException.class,
                () -> service.createDraft(request, "EMP_NEW", "ORG_NEW"));

        assertEquals("MARKETING_LEAD_PENDING_DUPLICATE", ((com.bank.branch.platform.common.web.exception.BizException) ex).getCode());
        verify(leadMapper, never()).insert(any(MarketingLeadInfo.class));
    }

    @Test
    void updateOnlyAllowsDraftOwnedByCurrentEmployeeAndReleasesDedupOnCancel() {
        MarketingLeadInfo draft = new MarketingLeadInfo();
        draft.setId(9L);
        draft.setLeadStatus("DRAFT");
        draft.setEntryEmpId("EMP_1");
        draft.setUnifiedCreditCode("91320100ABC1234567");
        draft.setActiveDedupKey("91320100ABC1234567");
        when(leadMapper.selectForUpdate(9L)).thenReturn(draft);
        when(leadMapper.updateStatusIf(9L, "DRAFT", "CANCELLED", "EMP_1", null)).thenReturn(1);

        LeadUpdateRequest update = new LeadUpdateRequest();
        update.setCustName("已修改企业");
        MarketingLeadInfo result = service.updateDraft(9L, update, "EMP_1", "ORG_1");
        assertEquals("已修改企业", result.getCustName());
        verify(leadMapper).updateById(draft);

        service.cancel(9L, "EMP_1");
        assertEquals("CANCELLED", draft.getLeadStatus());
        assertEquals(null, draft.getActiveDedupKey());
    }
}
