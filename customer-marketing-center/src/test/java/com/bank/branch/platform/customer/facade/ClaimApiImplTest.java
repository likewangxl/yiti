package com.bank.branch.platform.customer.facade;

import com.bank.branch.platform.customer.api.dto.CustClaimDTO;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ClaimApiImpl 单元测试（TDD Red-Green）
 * 验证 6 个契约方法的路径正确性与 CLAIMED 状态过滤。
 */
@ExtendWith(MockitoExtension.class)
class ClaimApiImplTest {

    @Mock
    private CustClaimMapper custClaimMapper;

    @InjectMocks
    private ClaimApiImpl claimApiImpl;

    // =========================================================
    // getClaim(custId, orgCode)
    // =========================================================

    @Test
    void getClaim_returnsClaimedRecord() {
        // given
        CustClaim claim = makeClaim("claim-001", "C1", "O1", ClaimStatus.CLAIMED.getCode());
        when(custClaimMapper.selectByCustIdAndOrgId("C1", "O1")).thenReturn(claim);

        // when
        Optional<CustClaimDTO> result = claimApiImpl.getClaim("C1", "O1");

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo("claim-001");
        verify(custClaimMapper).selectByCustIdAndOrgId("C1", "O1");
    }

    @Test
    void getClaim_returnsEmptyForCancelledRecord() {
        // given — 状态为 CANCELLED，应过滤掉
        CustClaim cancelled = makeClaim("claim-002", "C1", "O1", ClaimStatus.CANCELLED.getCode());
        when(custClaimMapper.selectByCustIdAndOrgId("C1", "O1")).thenReturn(cancelled);

        // when
        Optional<CustClaimDTO> result = claimApiImpl.getClaim("C1", "O1");

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void getClaim_returnsEmptyWhenNotFound() {
        // given
        when(custClaimMapper.selectByCustIdAndOrgId("C1", "O_NONE")).thenReturn(null);

        // when
        Optional<CustClaimDTO> result = claimApiImpl.getClaim("C1", "O_NONE");

        // then
        assertThat(result).isEmpty();
    }

    // =========================================================
    // getEmpClaims(empId)
    // =========================================================

    @Test
    void getEmpClaims_returnsActiveList() {
        // given
        CustClaim c1 = makeClaim("claim-001", "C1", "O1", ClaimStatus.CLAIMED.getCode());
        c1.setMaintainerEmpId("E1");
        CustClaim c2 = makeClaim("claim-002", "C2", "O1", ClaimStatus.CLAIMED.getCode());
        c2.setMaintainerEmpId("E1");
        when(custClaimMapper.selectActiveByEmp("E1")).thenReturn(Arrays.asList(c1, c2));

        // when
        List<CustClaimDTO> result = claimApiImpl.getEmpClaims("E1");

        // then
        assertThat(result).hasSize(2);
        verify(custClaimMapper).selectActiveByEmp("E1");
    }

    @Test
    void getEmpClaims_returnsEmptyListWhenNone() {
        // given
        when(custClaimMapper.selectActiveByEmp("E_NONE")).thenReturn(Collections.emptyList());

        // when
        List<CustClaimDTO> result = claimApiImpl.getEmpClaims("E_NONE");

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void getEmpClaims_returnsEmptyListWhenMapperReturnsNull() {
        // given — mapper 可能返回 null（防御）
        when(custClaimMapper.selectActiveByEmp("E_NULL")).thenReturn(null);

        // when
        List<CustClaimDTO> result = claimApiImpl.getEmpClaims("E_NULL");

        // then
        assertThat(result).isEmpty();
    }

    // =========================================================
    // getOrgClaims(orgCode)
    // =========================================================

    @Test
    void getOrgClaims_returnsActiveList() {
        // given
        CustClaim c1 = makeClaim("claim-001", "C1", "O1", ClaimStatus.CLAIMED.getCode());
        CustClaim c2 = makeClaim("claim-002", "C2", "O1", ClaimStatus.CLAIMED.getCode());
        when(custClaimMapper.selectActiveByOrg("O1")).thenReturn(Arrays.asList(c1, c2));

        // when
        List<CustClaimDTO> result = claimApiImpl.getOrgClaims("O1");

        // then
        assertThat(result).hasSize(2);
        verify(custClaimMapper).selectActiveByOrg("O1");
    }

    @Test
    void getOrgClaims_returnsEmptyListWhenNone() {
        // given
        when(custClaimMapper.selectActiveByOrg("O_NONE")).thenReturn(Collections.emptyList());

        // when
        List<CustClaimDTO> result = claimApiImpl.getOrgClaims("O_NONE");

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void getOrgClaims_returnsEmptyListWhenMapperReturnsNull() {
        // given
        when(custClaimMapper.selectActiveByOrg("O_NULL")).thenReturn(null);

        // when
        List<CustClaimDTO> result = claimApiImpl.getOrgClaims("O_NULL");

        // then
        assertThat(result).isEmpty();
    }

    // =========================================================
    // isClaimActive(custId, orgCode)
    // =========================================================

    @Test
    void isClaimActive_trueForClaimed() {
        // given
        CustClaim claim = makeClaim("claim-001", "C1", "O1", ClaimStatus.CLAIMED.getCode());
        when(custClaimMapper.selectByCustIdAndOrgId("C1", "O1")).thenReturn(claim);

        // when / then
        assertThat(claimApiImpl.isClaimActive("C1", "O1")).isTrue();
    }

    @Test
    void isClaimActive_falseForCancelled() {
        // given
        CustClaim cancelled = makeClaim("claim-002", "C1", "O1", ClaimStatus.CANCELLED.getCode());
        when(custClaimMapper.selectByCustIdAndOrgId("C1", "O1")).thenReturn(cancelled);

        // when / then
        assertThat(claimApiImpl.isClaimActive("C1", "O1")).isFalse();
    }

    @Test
    void isClaimActive_falseWhenNotFound() {
        // given
        when(custClaimMapper.selectByCustIdAndOrgId("C1", "O_NONE")).thenReturn(null);

        // when / then
        assertThat(claimApiImpl.isClaimActive("C1", "O_NONE")).isFalse();
    }

    // =========================================================
    // countEmpClaims(empId)
    // =========================================================

    @Test
    void countEmpClaims_returnsCountFromMapper() {
        // given
        when(custClaimMapper.countActiveByEmp("E1")).thenReturn(3L);

        // when
        long count = claimApiImpl.countEmpClaims("E1");

        // then
        assertThat(count).isEqualTo(3L);
        verify(custClaimMapper).countActiveByEmp("E1");
    }

    @Test
    void countEmpClaims_returnsZeroWhenMapperReturnsNull() {
        // given — mapper 极端情况返回 null
        when(custClaimMapper.countActiveByEmp("E_NULL")).thenReturn(null);

        // when
        long count = claimApiImpl.countEmpClaims("E_NULL");

        // then
        assertThat(count).isZero();
    }

    // =========================================================
    // countOrgClaims(orgCode)
    // =========================================================

    @Test
    void countOrgClaims_returnsCountFromMapper() {
        // given
        when(custClaimMapper.countActiveByOrg("O1")).thenReturn(7L);

        // when
        long count = claimApiImpl.countOrgClaims("O1");

        // then
        assertThat(count).isEqualTo(7L);
        verify(custClaimMapper).countActiveByOrg("O1");
    }

    @Test
    void countOrgClaims_returnsZeroWhenMapperReturnsNull() {
        // given
        when(custClaimMapper.countActiveByOrg("O_NULL")).thenReturn(null);

        // when
        long count = claimApiImpl.countOrgClaims("O_NULL");

        // then
        assertThat(count).isZero();
    }

    // =========================================================
    // 工具方法
    // =========================================================

    private CustClaim makeClaim(String id, String custId, String orgId, String status) {
        CustClaim c = new CustClaim();
        c.setId(id);
        c.setCustId(custId);
        c.setOrgId(orgId);
        c.setClaimStatus(status);
        return c;
    }
}
