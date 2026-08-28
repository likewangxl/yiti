package com.bank.branch.platform.bizapp.facade;

import com.bank.branch.platform.bizapp.api.dto.BizApplyStatDTO;
import com.bank.branch.platform.bizapp.api.dto.RunningAppCountDTO;
import com.bank.branch.platform.bizapp.mapper.SupportRequestMapper;
import com.bank.branch.platform.bizapp.service.BizApplySearchService;
import com.bank.branch.platform.customer.api.AssetProjectQueryApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * {@link BizApplyQueryApiImpl} 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class BizApplyQueryApiImplTest {

    @Mock
    private ObjectProvider<AssetProjectQueryApi> assetProjectQueryApiProvider;

    @Mock
    private AssetProjectQueryApi assetProjectQueryApi;

    @Mock
    private SupportRequestMapper supportRequestMapper;

    @Mock
    private BizApplySearchService bizApplySearchService;

    @InjectMocks
    private BizApplyQueryApiImpl bizApplyQueryApiImpl;

    // ------------------------------------------------------------------
    // 测试用例
    // ------------------------------------------------------------------

    @Test
    void countRunningApplications_shouldAggregateBothDomains() {
        // Arrange
        String custId = "1001";
        when(assetProjectQueryApiProvider.getIfAvailable()).thenReturn(assetProjectQueryApi);
        when(assetProjectQueryApi.countRunningByCustomer(1001L)).thenReturn(2L);
        // 支持：3条运行中
        when(supportRequestMapper.countRunningByCustomer(custId)).thenReturn(3L);

        // Act
        RunningAppCountDTO result = bizApplyQueryApiImpl.countRunningApplications(custId);

        // Assert
        assertThat(result.getRunningLoanCount()).isEqualTo(2L);
        assertThat(result.getRunningSupportCount()).isEqualTo(3L);
    }

    @Test
    void hasRunningLoan_withActive_shouldReturnTrue() {
        // Arrange
        String custId = "1001";
        when(assetProjectQueryApiProvider.getIfAvailable()).thenReturn(assetProjectQueryApi);
        when(assetProjectQueryApi.countRunningByCustomer(1001L)).thenReturn(1L);

        // Act
        boolean result = bizApplyQueryApiImpl.hasRunningLoan(custId);

        // Assert
        assertThat(result).isTrue();
    }

    @Test
    void hasRunningLoan_noActive_shouldReturnFalse() {
        // Arrange
        String custId = "1002";
        when(assetProjectQueryApiProvider.getIfAvailable()).thenReturn(assetProjectQueryApi);
        when(assetProjectQueryApi.countRunningByCustomer(1002L)).thenReturn(0L);

        // Act
        boolean result = bizApplyQueryApiImpl.hasRunningLoan(custId);

        // Assert
        assertThat(result).isFalse();
    }

    @Test
    void getEmpStatistics_shouldAggregate() {
        // Arrange
        String empId = "EMP001";
        BizApplyStatDTO stat = new BizApplyStatDTO();
        stat.setTotalLoans(10L);
        stat.setCompletedLoans(8L);
        stat.setTotalSupports(5L);
        stat.setCompletedSupports(4L);
        stat.setTotalCreditAmount(new BigDecimal("2000000.0000"));
        when(bizApplySearchService.getEmpStatistics(empId)).thenReturn(stat);

        // Act
        BizApplyStatDTO result = bizApplyQueryApiImpl.getEmpStatistics(empId);

        // Assert
        assertThat(result.getTotalLoans()).isEqualTo(10L);
        assertThat(result.getCompletedLoans()).isEqualTo(8L);
        assertThat(result.getTotalSupports()).isEqualTo(5L);
        assertThat(result.getCompletedSupports()).isEqualTo(4L);
        assertThat(result.getTotalCreditAmount()).isEqualByComparingTo("2000000.0000");
    }
}
