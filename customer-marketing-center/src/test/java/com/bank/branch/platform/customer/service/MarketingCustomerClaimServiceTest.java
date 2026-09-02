package com.bank.branch.platform.customer.service;

import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.customer.dto.req.ReTouchReqDTO;
import com.bank.branch.platform.customer.entity.marketing.MarketingCustomerClaim;
import com.bank.branch.platform.customer.entity.marketing.MarketingCustomerInfo;
import com.bank.branch.platform.customer.entity.marketing.MarketingLeadInfo;
import com.bank.branch.platform.customer.entity.TouchTask;
import com.bank.branch.platform.customer.enums.CustomerErrorCode;
import com.bank.branch.platform.customer.mapper.TouchTaskMapper;
import com.bank.branch.platform.customer.mapper.marketing.MarketingCustomerClaimMapper;
import com.bank.branch.platform.customer.mapper.marketing.MarketingCustomerInfoMapper;
import com.bank.branch.platform.customer.mapper.marketing.MarketingLeadInfoMapper;
import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.customer.mapper.marketing.MarketingLeadTagRelMapper;
import com.bank.branch.platform.governance.api.DictApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 目标营销表认领提交的重读、状态和唯一维度契约。 */
@ExtendWith(MockitoExtension.class)
class MarketingCustomerClaimServiceTest {

    @Mock
    private MarketingCustomerClaimMapper claimMapper;
    @Mock
    private MarketingLeadInfoMapper leadMapper;
    @Mock
    private MarketingCustomerInfoMapper customerMapper;

    @Mock
    private TouchTaskMapper touchTaskMapper;

    @Mock
    private TouchTaskService touchTaskService;

    @Mock
    private DictApi dictApi;

    @Mock
    private OrgApi orgApi;

    @Mock
    private MarketingLeadTagRelMapper leadTagRelMapper;

    @InjectMocks
    private MarketingCustomerClaimService service;

    @Test
    void claim_shouldReReadApprovedPublicAvailableLeadAndOwnerBeforeInsert() {
        MarketingLeadInfo lead = lead(11L, 101L);
        MarketingCustomerInfo customer = customer(101L);
        when(leadMapper.selectForUpdate(11L)).thenReturn(lead);
        when(customerMapper.selectForUpdate(101L)).thenReturn(customer);
        when(claimMapper.selectActiveBySourceLeadAndClaimedBy(11L, "EMP-1")).thenReturn(null);
        when(claimMapper.insert(any(MarketingCustomerClaim.class))).thenReturn(1);

        MarketingCustomerClaim result = service.claim(11L, "ORG-1", "EMP-1");

        assertThat(result.getCustId()).isEqualTo(101L);
        assertThat(result.getSourceLeadId()).isEqualTo(11L);
        assertThat(result.getClaimedBy()).isEqualTo("EMP-1");
        assertThat(result.getClaimStatus()).isEqualTo("CLAIMED");
        verify(leadMapper).selectForUpdate(11L);
        verify(customerMapper).selectForUpdate(101L);
    }

    @Test
    void claim_shouldFailClosedWhenLeadIsNotPublicAvailable() {
        MarketingLeadInfo lead = lead(11L, 101L);
        lead.setDistributionMode("SCOPE");
        when(leadMapper.selectForUpdate(11L)).thenReturn(lead);

        assertThatThrownBy(() -> service.claim(11L, "ORG-1", "EMP-1"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("待认领");

        verify(claimMapper, org.mockito.Mockito.never()).insert(any(MarketingCustomerClaim.class));
    }

    @Test
    void claim_shouldTranslateUniqueConflictToAlreadyClaimed() {
        MarketingLeadInfo lead = lead(11L, 101L);
        when(leadMapper.selectForUpdate(11L)).thenReturn(lead);
        when(customerMapper.selectForUpdate(101L)).thenReturn(customer(101L));
        doThrow(new DuplicateKeyException("uk_source_lead_claimed_by"))
                .when(claimMapper).insert(any(MarketingCustomerClaim.class));

        assertThatThrownBy(() -> service.claim(11L, "ORG-1", "EMP-1"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已认领");
    }

    @Test
    void startTouch_shouldBindClaimIdAsTaskSourceBizId() {
        MarketingCustomerClaim claim = claim(21L, 101L, "EMP-1");
        when(claimMapper.selectForUpdate(21L)).thenReturn(claim);
        when(touchTaskMapper.selectActiveByCustAndAssignee("101", "EMP-1")).thenReturn(java.util.List.of());
        TouchTask created = new TouchTask();
        created.setId(301L);
        when(touchTaskService.createFirstTouchTask("101", "ORG-1", "EMP-1", null, 21L))
                .thenReturn(created);

        TouchTask result = service.startTouch("21", null, "EMP-1", "ORG-1");

        assertThat(result.getId()).isEqualTo(301L);
        verify(touchTaskService).createFirstTouchTask("101", "ORG-1", "EMP-1", null, 21L);
    }

    @Test
    void reTouch_shouldBindClaimIdAsTaskSourceBizId() {
        MarketingCustomerClaim claim = claim(22L, 102L, "EMP-2");
        when(claimMapper.selectForUpdate(22L)).thenReturn(claim);
        when(touchTaskMapper.selectActiveByCustAndAssignee("102", "EMP-2")).thenReturn(java.util.List.of());
        TouchTask created = new TouchTask();
        created.setId(302L);
        ReTouchReqDTO req = new ReTouchReqDTO();
        req.setReason("客户需要补充授信材料后再次沟通");
        when(touchTaskService.createFollowUpTask("102", "ORG-1", "EMP-2",
                "客户需要补充授信材料后再次沟通", null, 22L)).thenReturn(created);

        TouchTask result = service.reTouch("22", req, "EMP-2", "ORG-1");

        assertThat(result.getId()).isEqualTo(302L);
        verify(touchTaskService).createFollowUpTask("102", "ORG-1", "EMP-2",
                "客户需要补充授信材料后再次沟通", null, 22L);
    }

    @Test
    void startTouch_shouldKeepDuplicateGuardForRunningTask() {
        MarketingCustomerClaim claim = claim(23L, 103L, "EMP-3");
        when(claimMapper.selectForUpdate(23L)).thenReturn(claim);
        when(touchTaskMapper.selectActiveByCustAndAssignee("103", "EMP-3"))
                .thenReturn(java.util.List.of(new TouchTask()));

        assertThatThrownBy(() -> service.startTouch("23", null, "EMP-3", "ORG-1"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", CustomerErrorCode.RE_TOUCH_HAS_RUNNING.getCode());

        verify(touchTaskService, never()).createFirstTouchTask(
                any(String.class), any(String.class), any(String.class), any(String.class), any(Long.class));
    }

    private MarketingCustomerClaim claim(Long id, Long custId, String empId) {
        MarketingCustomerClaim claim = new MarketingCustomerClaim();
        claim.setId(id);
        claim.setCustId(custId);
        claim.setOrgId("ORG-1");
        claim.setClaimedBy(empId);
        claim.setMaintainerEmpId(empId);
        claim.setClaimStatus("CLAIMED");
        return claim;
    }

    private MarketingLeadInfo lead(Long id, Long custId) {
        MarketingLeadInfo lead = new MarketingLeadInfo();
        lead.setId(id);
        lead.setCustId(custId);
        lead.setLeadStatus("APPROVED");
        lead.setDistributionMode("PUBLIC");
        lead.setPoolStatus("AVAILABLE");
        lead.setRecordStatus("ACTIVE");
        return lead;
    }

    private MarketingCustomerInfo customer(Long id) {
        MarketingCustomerInfo customer = new MarketingCustomerInfo();
        customer.setId(id);
        customer.setRecordStatus("ACTIVE");
        customer.setOwnershipStatus("UNASSIGNED");
        return customer;
    }
}
