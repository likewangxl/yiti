package com.bank.branch.platform.bizapp.service;

import com.bank.branch.platform.bizapp.api.dto.BizApplyStatDTO;
import com.bank.branch.platform.bizapp.mapper.LoanApplyMapper;
import com.bank.branch.platform.bizapp.mapper.SupportRequestMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * {@link BizApplySearchService} 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class BizApplySearchServiceTest {

    @Mock
    private LoanApplyMapper loanMapper;

    @Mock
    private SupportRequestMapper supportMapper;

    @InjectMocks
    private BizApplySearchService bizApplySearchService;

    @Test
    void getEmpStatistics_shouldAggregateFromBothDomains() {
        // Arrange
        String empId = "EMP001";
        // loanMapper.countPage(null, null, null) -> 全量贷款总数
        when(loanMapper.countPage(isNull(), isNull(), isNull())).thenReturn(20L);
        // loanMapper.countCompletedByOrg 使用宽泛时间段
        when(loanMapper.countCompletedByOrg(isNull(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(15L);
        // sumCreditAmountByEmp 全量
        when(loanMapper.sumCreditAmountByEmp(eq(empId), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(new BigDecimal("1500000.0000"));
        // 支持申请总数
        when(supportMapper.countPageForSupport(isNull(), isNull(), isNull())).thenReturn(10L);
        // 支持申请已完成（按创建人）
        when(supportMapper.countCompletedByCreator(eq(empId), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(8L);

        // Act
        BizApplyStatDTO result = bizApplySearchService.getEmpStatistics(empId);

        // Assert
        assertThat(result.getTotalLoans()).isEqualTo(20L);
        assertThat(result.getCompletedLoans()).isEqualTo(15L);
        assertThat(result.getTotalSupports()).isEqualTo(10L);
        assertThat(result.getCompletedSupports()).isEqualTo(8L);
        assertThat(result.getTotalCreditAmount()).isEqualByComparingTo("1500000.0000");
    }

    @Test
    void getEmpStatisticsByPeriod_shouldFilterByDateRange() {
        // Arrange
        String empId = "EMP002";
        LocalDateTime start = LocalDateTime.of(2024, 1, 1, 0, 0, 0);
        LocalDateTime end = LocalDateTime.of(2024, 3, 31, 23, 59, 59);

        when(loanMapper.countCompletedByOrg(isNull(), eq(start), eq(end))).thenReturn(5L);
        when(loanMapper.sumCreditAmountByEmp(eq(empId), eq(start), eq(end)))
                .thenReturn(new BigDecimal("750000.0000"));
        when(supportMapper.countCompletedByCreator(eq(empId), eq(start), eq(end))).thenReturn(3L);

        // Act
        BizApplyStatDTO result = bizApplySearchService.getEmpStatisticsByPeriod(empId, start, end);

        // Assert
        assertThat(result.getCompletedLoans()).isEqualTo(5L);
        assertThat(result.getCompletedSupports()).isEqualTo(3L);
        assertThat(result.getTotalCreditAmount()).isEqualByComparingTo("750000.0000");
        // 时段统计中总数固定为0（mapper无按创建人+时段统计总数的方法）
        assertThat(result.getTotalLoans()).isEqualTo(0L);
        assertThat(result.getTotalSupports()).isEqualTo(0L);
    }
}
