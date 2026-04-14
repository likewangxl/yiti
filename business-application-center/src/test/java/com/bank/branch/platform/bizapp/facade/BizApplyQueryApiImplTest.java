package com.bank.branch.platform.bizapp.facade;

import com.bank.branch.platform.bizapp.api.dto.BizApplyStatDTO;
import com.bank.branch.platform.bizapp.api.dto.RunningAppCountDTO;
import com.bank.branch.platform.bizapp.entity.LoanApply;
import com.bank.branch.platform.bizapp.mapper.LoanApplyMapper;
import com.bank.branch.platform.bizapp.mapper.SupportRequestMapper;
import com.bank.branch.platform.bizapp.service.BizApplySearchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * {@link BizApplyQueryApiImpl} 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class BizApplyQueryApiImplTest {

    @Mock
    private LoanApplyMapper loanApplyMapper;

    @Mock
    private SupportRequestMapper supportRequestMapper;

    @Mock
    private BizApplySearchService bizApplySearchService;

    @InjectMocks
    private BizApplyQueryApiImpl bizApplyQueryApiImpl;

    // ------------------------------------------------------------------
    // 测试辅助方法
    // ------------------------------------------------------------------

    private LoanApply loanWithStatus(String id, String status) {
        LoanApply e = new LoanApply();
        e.setId(id);
        e.setStatus(status);
        return e;
    }

    // ------------------------------------------------------------------
    // 测试用例
    // ------------------------------------------------------------------

    @Test
    void countRunningApplications_shouldAggregateBothDomains() {
        // Arrange
        String custId = "CUST001";
        // 贷款：2条IN_APPROVAL
        when(loanApplyMapper.selectByCustId(custId)).thenReturn(List.of(
                loanWithStatus("L1", "IN_APPROVAL"),
                loanWithStatus("L2", "IN_APPROVAL"),
                loanWithStatus("L3", "COMPLETED")
        ));
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
        String custId = "CUST001";
        when(loanApplyMapper.selectByCustId(custId)).thenReturn(List.of(
                loanWithStatus("L1", "IN_APPROVAL")
        ));

        // Act
        boolean result = bizApplyQueryApiImpl.hasRunningLoan(custId);

        // Assert
        assertThat(result).isTrue();
    }

    @Test
    void hasRunningLoan_noActive_shouldReturnFalse() {
        // Arrange
        String custId = "CUST002";
        when(loanApplyMapper.selectByCustId(custId)).thenReturn(List.of(
                loanWithStatus("L1", "COMPLETED"),
                loanWithStatus("L2", "REJECTED")
        ));

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
