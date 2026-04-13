package com.bank.branch.platform.customer.facade;

import com.bank.branch.platform.customer.entity.CustClaim;
import com.bank.branch.platform.customer.enums.ClaimStatus;
import com.bank.branch.platform.customer.mapper.CustClaimMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ClaimApiImpl 单元测试（TDD）
 * 验证委托调用路径正确。
 */
@ExtendWith(MockitoExtension.class)
class ClaimApiImplTest {

    @Mock
    private CustClaimMapper custClaimMapper;

    @InjectMocks
    private ClaimApiImpl claimApiImpl;

    @Test
    void getClaimsByCustomer_shouldReturnAllClaims() {
        // given
        CustClaim claim1 = new CustClaim();
        claim1.setId("claim-001");
        claim1.setCustId("cust-001");
        claim1.setOrgId("ORG001");
        claim1.setClaimStatus(ClaimStatus.CLAIMED.getCode());

        CustClaim claim2 = new CustClaim();
        claim2.setId("claim-002");
        claim2.setCustId("cust-001");
        claim2.setOrgId("ORG002");
        claim2.setClaimStatus(ClaimStatus.CANCELLED.getCode());

        when(custClaimMapper.selectByCustId("cust-001")).thenReturn(Arrays.asList(claim1, claim2));

        // when
        List<CustClaim> result = claimApiImpl.getClaimsByCustomer("cust-001");

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getCustId()).isEqualTo("cust-001");
        verify(custClaimMapper).selectByCustId("cust-001");
    }

    @Test
    void getClaimsByCustomer_shouldReturnEmptyListWhenNoClaims() {
        // given
        when(custClaimMapper.selectByCustId("cust-001")).thenReturn(Collections.emptyList());

        // when
        List<CustClaim> result = claimApiImpl.getClaimsByCustomer("cust-001");

        // then
        assertThat(result).isEmpty();
        verify(custClaimMapper).selectByCustId("cust-001");
    }

    @Test
    void getClaimByCustIdAndOrgId_shouldReturnClaim() {
        // given
        CustClaim claim = new CustClaim();
        claim.setId("claim-001");
        claim.setCustId("cust-001");
        claim.setOrgId("ORG001");
        claim.setClaimStatus(ClaimStatus.CLAIMED.getCode());
        when(custClaimMapper.selectByCustIdAndOrgId("cust-001", "ORG001")).thenReturn(claim);

        // when
        CustClaim result = claimApiImpl.getClaimByCustIdAndOrgId("cust-001", "ORG001");

        // then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo("claim-001");
        assertThat(result.getClaimStatus()).isEqualTo(ClaimStatus.CLAIMED.getCode());
        verify(custClaimMapper).selectByCustIdAndOrgId("cust-001", "ORG001");
    }

    @Test
    void getClaimByCustIdAndOrgId_shouldReturnNullWhenNotFound() {
        // given
        when(custClaimMapper.selectByCustIdAndOrgId("cust-001", "ORG999")).thenReturn(null);

        // when
        CustClaim result = claimApiImpl.getClaimByCustIdAndOrgId("cust-001", "ORG999");

        // then
        assertThat(result).isNull();
        verify(custClaimMapper).selectByCustIdAndOrgId("cust-001", "ORG999");
    }
}
