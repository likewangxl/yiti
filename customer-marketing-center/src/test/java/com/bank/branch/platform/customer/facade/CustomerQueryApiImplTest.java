package com.bank.branch.platform.customer.facade;

import com.bank.branch.platform.customer.entity.CustClaim;
import com.bank.branch.platform.customer.entity.CustMaster;
import com.bank.branch.platform.customer.enums.ClaimStatus;
import com.bank.branch.platform.customer.mapper.CustClaimMapper;
import com.bank.branch.platform.customer.mapper.CustMasterMapper;
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
 * CustomerQueryApiImpl 单元测试（TDD）
 * 验证委托调用路径及业务逻辑正确。
 */
@ExtendWith(MockitoExtension.class)
class CustomerQueryApiImplTest {

    @Mock
    private CustMasterMapper custMasterMapper;

    @Mock
    private CustClaimMapper custClaimMapper;

    @InjectMocks
    private CustomerQueryApiImpl customerQueryApiImpl;

    @Test
    void getCustomer_shouldDelegateToCustMasterMapper() {
        // given
        CustMaster master = new CustMaster();
        master.setId("cust-001");
        master.setCustNo("C20240101001");
        master.setCustName("测试公司");
        when(custMasterMapper.selectById("cust-001")).thenReturn(master);

        // when
        CustMaster result = customerQueryApiImpl.getCustomer("cust-001");

        // then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo("cust-001");
        assertThat(result.getCustName()).isEqualTo("测试公司");
        verify(custMasterMapper).selectById("cust-001");
    }

    @Test
    void getCustomerByCustNo_shouldDelegateToCustMasterMapper() {
        // given
        CustMaster master = new CustMaster();
        master.setId("cust-001");
        master.setCustNo("C20240101001");
        when(custMasterMapper.selectByCustNo("C20240101001")).thenReturn(master);

        // when
        CustMaster result = customerQueryApiImpl.getCustomerByCustNo("C20240101001");

        // then
        assertThat(result).isNotNull();
        assertThat(result.getCustNo()).isEqualTo("C20240101001");
        verify(custMasterMapper).selectByCustNo("C20240101001");
    }

    @Test
    void isValidCustomer_shouldReturnTrueWhenExists() {
        // given
        CustMaster master = new CustMaster();
        master.setId("cust-001");
        when(custMasterMapper.selectById("cust-001")).thenReturn(master);

        // when
        boolean result = customerQueryApiImpl.isValidCustomer("cust-001");

        // then
        assertThat(result).isTrue();
        verify(custMasterMapper).selectById("cust-001");
    }

    @Test
    void isValidCustomer_shouldReturnFalseWhenNotExists() {
        // given
        when(custMasterMapper.selectById("not-exist")).thenReturn(null);

        // when
        boolean result = customerQueryApiImpl.isValidCustomer("not-exist");

        // then
        assertThat(result).isFalse();
        verify(custMasterMapper).selectById("not-exist");
    }

    @Test
    void isClaimedByOrg_shouldReturnTrueWhenClaimExists() {
        // given
        CustClaim claim = new CustClaim();
        claim.setId("claim-001");
        claim.setCustId("cust-001");
        claim.setOrgId("ORG001");
        claim.setClaimStatus(ClaimStatus.CLAIMED.getCode());
        when(custClaimMapper.selectByCustIdAndOrgId("cust-001", "ORG001")).thenReturn(claim);

        // when
        boolean result = customerQueryApiImpl.isClaimedByOrg("cust-001", "ORG001");

        // then
        assertThat(result).isTrue();
        verify(custClaimMapper).selectByCustIdAndOrgId("cust-001", "ORG001");
    }

    @Test
    void isClaimedByOrg_shouldReturnFalseWhenClaimNotExists() {
        // given
        when(custClaimMapper.selectByCustIdAndOrgId("cust-001", "ORG001")).thenReturn(null);

        // when
        boolean result = customerQueryApiImpl.isClaimedByOrg("cust-001", "ORG001");

        // then
        assertThat(result).isFalse();
        verify(custClaimMapper).selectByCustIdAndOrgId("cust-001", "ORG001");
    }

    @Test
    void getCustomerClaims_shouldReturnAllClaimsForCustomer() {
        // given
        CustClaim claim1 = new CustClaim();
        claim1.setId("claim-001");
        claim1.setCustId("cust-001");
        claim1.setOrgId("ORG001");

        CustClaim claim2 = new CustClaim();
        claim2.setId("claim-002");
        claim2.setCustId("cust-001");
        claim2.setOrgId("ORG002");

        when(custClaimMapper.selectByCustId("cust-001")).thenReturn(Arrays.asList(claim1, claim2));

        // when
        List<CustClaim> result = customerQueryApiImpl.getCustomerClaims("cust-001");

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getOrgId()).isEqualTo("ORG001");
        verify(custClaimMapper).selectByCustId("cust-001");
    }

    @Test
    void getCustomerClaims_shouldReturnEmptyListWhenNoClaims() {
        // given
        when(custClaimMapper.selectByCustId("cust-001")).thenReturn(Collections.emptyList());

        // when
        List<CustClaim> result = customerQueryApiImpl.getCustomerClaims("cust-001");

        // then
        assertThat(result).isEmpty();
        verify(custClaimMapper).selectByCustId("cust-001");
    }
}
