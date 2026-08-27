package com.bank.branch.platform.customer.service;

import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.customer.entity.marketing.MarketingCustomerClaim;
import com.bank.branch.platform.customer.entity.marketing.MarketingCustomerInfo;
import com.bank.branch.platform.customer.entity.marketing.MarketingLeadInfo;
import com.bank.branch.platform.customer.mapper.marketing.MarketingCustomerClaimMapper;
import com.bank.branch.platform.customer.mapper.marketing.MarketingCustomerInfoMapper;
import com.bank.branch.platform.customer.mapper.marketing.MarketingLeadInfoMapper;
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
