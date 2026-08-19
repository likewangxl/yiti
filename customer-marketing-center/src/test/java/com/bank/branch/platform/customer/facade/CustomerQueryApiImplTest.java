package com.bank.branch.platform.customer.facade;

import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.customer.api.dto.CustClaimDTO;
import com.bank.branch.platform.customer.api.dto.CustomerDTO;
import com.bank.branch.platform.customer.api.dto.CustomerFilterDTO;
import com.bank.branch.platform.customer.api.dto.RunningFlowDTO;
import com.bank.branch.platform.customer.entity.CustClaim;
import com.bank.branch.platform.customer.entity.CustMaster;
import com.bank.branch.platform.customer.entity.M98CustMaster;
import com.bank.branch.platform.customer.entity.TouchTask;
import com.bank.branch.platform.customer.enums.ClaimStatus;
import com.bank.branch.platform.customer.mapper.CustClaimMapper;
import com.bank.branch.platform.customer.mapper.CustMasterMapper;
import com.bank.branch.platform.customer.mapper.M98CustMasterMapper;
import com.bank.branch.platform.customer.mapper.TouchTaskMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CustomerQueryApiImpl 单元测试（TDD）
 * 验证 CustomerQueryApi 契约实现的正确性。
 * 全面覆盖 9 个接口方法，共 19 个测试用例。
 */
@ExtendWith(MockitoExtension.class)
class CustomerQueryApiImplTest {

    @Mock
    private CustMasterMapper custMasterMapper;

    @Mock
    private M98CustMasterMapper m98CustMasterMapper;

    @Mock
    private CustClaimMapper custClaimMapper;

    @Mock
    private TouchTaskMapper touchTaskMapper;

    @InjectMocks
    private CustomerQueryApiImpl customerQueryApiImpl;

    // ==================== getCustomer ====================

    @Test
    void getCustomer_returnsOptionalEmptyWhenNotFound() {
        // given
        when(custMasterMapper.selectById("NA")).thenReturn(null);

        // when
        Optional<CustomerDTO> result = customerQueryApiImpl.getCustomer("NA");

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void getCustomer_returnsMappedDTO() {
        // given
        CustMaster e = new CustMaster();
        e.setId("C1");
        e.setCustName("测试公司");
        when(custMasterMapper.selectById("C1")).thenReturn(e);

        // when
        Optional<CustomerDTO> result = customerQueryApiImpl.getCustomer("C1");

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo("C1");
        assertThat(result.get().getCustName()).isEqualTo("测试公司");
        verify(custMasterMapper).selectById("C1");
    }

    // ==================== getCustomerByCustNo ====================

    @Test
    void getCustomerByCustNo_returnsOptionalEmptyWhenNotFound() {
        when(m98CustMasterMapper.selectByCustNo("NA-NO")).thenReturn(null);

        Optional<CustomerDTO> result = customerQueryApiImpl.getCustomerByCustNo("NA-NO");

        assertThat(result).isEmpty();
    }

    @Test
    void getCustomerByCustNo_returnsMappedDTOWithInternalId() {
        M98CustMaster e = new M98CustMaster();
        e.setId("C1");
        e.setCustNo("CN-001");
        e.setCustName("测试公司");
        when(m98CustMasterMapper.selectByCustNo("CN-001")).thenReturn(e);

        Optional<CustomerDTO> result = customerQueryApiImpl.getCustomerByCustNo("CN-001");

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo("C1");
        assertThat(result.get().getCustNo()).isEqualTo("CN-001");
        assertThat(result.get().getCustName()).isEqualTo("测试公司");
        verify(m98CustMasterMapper).selectByCustNo("CN-001");
    }

    // ==================== listCustomers ====================

    @Test
    void listCustomers_throwsWhenOver500() {
        // given
        List<String> ids = IntStream.range(0, 501).mapToObj(i -> "C" + i).toList();

        // when / then
        assertThatThrownBy(() -> customerQueryApiImpl.listCustomers(ids))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "COMMON-40000");
    }

    @Test
    void listCustomers_returnsEmptyForEmptyInput() {
        // when
        List<CustomerDTO> result = customerQueryApiImpl.listCustomers(Collections.emptyList());

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void listCustomers_returnsEmptyForNullInput() {
        // when
        List<CustomerDTO> result = customerQueryApiImpl.listCustomers(null);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void listCustomers_queriesByIds() {
        // given
        CustMaster e1 = new CustMaster();
        e1.setId("C1");
        e1.setCustName("公司A");
        CustMaster e2 = new CustMaster();
        e2.setId("C2");
        e2.setCustName("公司B");
        when(custMasterMapper.selectByIds(Arrays.asList("C1", "C2"))).thenReturn(Arrays.asList(e1, e2));

        // when
        List<CustomerDTO> result = customerQueryApiImpl.listCustomers(Arrays.asList("C1", "C2"));

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getCustName()).isEqualTo("公司A");
        assertThat(result.get(1).getCustName()).isEqualTo("公司B");
    }

    // ==================== searchCustomers ====================

    @Test
    void searchCustomers_throwsWhenKeywordBlank() {
        // when / then
        assertThatThrownBy(() -> customerQueryApiImpl.searchCustomers("", 10))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "COMMON-40000");
        assertThatThrownBy(() -> customerQueryApiImpl.searchCustomers(null, 10))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "COMMON-40000");
    }

    @Test
    void searchCustomers_throwsWhenLimitOutOfRange() {
        // when / then
        assertThatThrownBy(() -> customerQueryApiImpl.searchCustomers("测试", 0))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "COMMON-40000");
        assertThatThrownBy(() -> customerQueryApiImpl.searchCustomers("测试", 51))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "COMMON-40000");
    }

    @Test
    void searchCustomers_returnsMappedResults() {
        // given
        CustMaster e = new CustMaster();
        e.setId("C1");
        e.setCustName("测试公司");
        when(custMasterMapper.searchByKeyword(eq("测试"), eq(10))).thenReturn(List.of(e));

        // when
        List<CustomerDTO> result = customerQueryApiImpl.searchCustomers("测试", 10);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCustName()).isEqualTo("测试公司");
        verify(custMasterMapper).searchByKeyword("测试", 10);
    }

    // ==================== isValidCustomer ====================

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
    }

    @Test
    void isValidCustomer_shouldReturnFalseWhenNotExists() {
        // given
        when(custMasterMapper.selectById("not-exist")).thenReturn(null);

        // when
        boolean result = customerQueryApiImpl.isValidCustomer("not-exist");

        // then
        assertThat(result).isFalse();
    }

    // ==================== isClaimedByOrg ====================

    @Test
    void isClaimedByOrg_returnsTrueWhenClaimExists() {
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
    }

    @Test
    void isClaimedByOrg_returnsFalseWhenNotClaimed() {
        // given
        when(custClaimMapper.selectByCustIdAndOrgId("cust-001", "ORG001")).thenReturn(null);

        // when
        boolean result = customerQueryApiImpl.isClaimedByOrg("cust-001", "ORG001");

        // then
        assertThat(result).isFalse();
    }

    @Test
    void isClaimedByOrg_returnsFalseWhenStatusNotClaimed() {
        // given
        CustClaim claim = new CustClaim();
        claim.setId("claim-001");
        claim.setClaimStatus(ClaimStatus.CANCELLED.getCode());
        when(custClaimMapper.selectByCustIdAndOrgId("cust-001", "ORG001")).thenReturn(claim);

        // when
        boolean result = customerQueryApiImpl.isClaimedByOrg("cust-001", "ORG001");

        // then
        assertThat(result).isFalse();
    }

    // ==================== getCustomerClaims ====================

    @Test
    void getCustomerClaims_returnsDTOListWithOnlyClaimed() {
        // given
        CustClaim claimActive = new CustClaim();
        claimActive.setId("claim-001");
        claimActive.setCustId("cust-001");
        claimActive.setOrgId("ORG001");
        claimActive.setClaimStatus(ClaimStatus.CLAIMED.getCode());

        CustClaim claimCancelled = new CustClaim();
        claimCancelled.setId("claim-002");
        claimCancelled.setCustId("cust-001");
        claimCancelled.setOrgId("ORG002");
        claimCancelled.setClaimStatus(ClaimStatus.CANCELLED.getCode());

        when(custClaimMapper.selectByCustId("cust-001")).thenReturn(Arrays.asList(claimActive, claimCancelled));

        // when
        List<CustClaimDTO> result = customerQueryApiImpl.getCustomerClaims("cust-001");

        // then — 仅返回 CLAIMED 状态
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo("claim-001");
        assertThat(result.get(0)).isInstanceOf(CustClaimDTO.class);
    }

    @Test
    void getCustomerClaims_returnsEmptyListWhenNoClaims() {
        // given
        when(custClaimMapper.selectByCustId("cust-001")).thenReturn(Collections.emptyList());

        // when
        List<CustClaimDTO> result = customerQueryApiImpl.getCustomerClaims("cust-001");

        // then
        assertThat(result).isEmpty();
    }

    // ==================== hasRunningProcess ====================

    @Test
    void hasRunningProcess_returnsTrueForTouchTaskInProgress() {
        // given
        when(touchTaskMapper.countActiveByCust("C1")).thenReturn(2L);

        // when
        boolean result = customerQueryApiImpl.hasRunningProcess("C1", "TOUCH_TASK");

        // then
        assertThat(result).isTrue();
    }

    @Test
    void hasRunningProcess_returnsFalseForNoActiveTask() {
        // given
        when(touchTaskMapper.countActiveByCust("C1")).thenReturn(0L);

        // when
        boolean result = customerQueryApiImpl.hasRunningProcess("C1", "TOUCH_TASK");

        // then
        assertThat(result).isFalse();
    }

    @Test
    void hasRunningProcess_returnsFalseForUnknownBizType() {
        // when
        boolean result = customerQueryApiImpl.hasRunningProcess("C1", "UNKNOWN_TYPE");

        // then — 未知 bizType 直接返回 false，不调用 mapper
        assertThat(result).isFalse();
    }

    // ==================== listRunningProcesses ====================

    @Test
    void listRunningProcesses_returnsTouchTaskFlows() {
        // given
        TouchTask t = new TouchTask();
        t.setId("T1");
        t.setBusinessKey("TOUCH:T1");
        t.setOrgId("ORG001");
        t.setAssigneeEmpId("E001");
        t.setTaskStatus("PENDING");
        when(touchTaskMapper.selectActiveByCust("C1")).thenReturn(List.of(t));

        // when
        List<RunningFlowDTO> result = customerQueryApiImpl.listRunningProcesses("C1");

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getBizType()).isEqualTo("TOUCH_TASK");
        assertThat(result.get(0).getBizId()).isEqualTo("T1");
        assertThat(result.get(0).getBusinessKey()).isEqualTo("TOUCH:T1");
        assertThat(result.get(0).getStatus()).isEqualTo("PENDING");
    }

    // ==================== countCustomers ====================

    @Test
    void countCustomers_appliesKeywordFilter() {
        // given
        CustomerFilterDTO filter = new CustomerFilterDTO();
        filter.setKeyword("测试");
        when(custMasterMapper.countByFilter(any(CustomerFilterDTO.class))).thenReturn(5L);

        // when
        long count = customerQueryApiImpl.countCustomers(filter);

        // then
        assertThat(count).isEqualTo(5L);
        verify(custMasterMapper).countByFilter(filter);
    }

    @Test
    void countCustomers_handlesNullFilter() {
        // given
        when(custMasterMapper.countByFilter(any(CustomerFilterDTO.class))).thenReturn(0L);

        // when — 传 null 不应抛出异常
        long count = customerQueryApiImpl.countCustomers(null);

        // then
        assertThat(count).isEqualTo(0L);
        verify(custMasterMapper).countByFilter(any(CustomerFilterDTO.class));
    }
}
