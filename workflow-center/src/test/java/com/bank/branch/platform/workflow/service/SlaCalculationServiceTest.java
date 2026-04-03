package com.bank.branch.platform.workflow.service;

import com.bank.branch.platform.governance.api.CalendarApi;
import com.bank.branch.platform.workflow.entity.WfTimeoutRule;
import com.bank.branch.platform.workflow.enums.SlaStatus;
import com.bank.branch.platform.workflow.mapper.TimeoutRuleMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * SlaCalculationService 单元测试。
 * <p>
 * 验证 SLA 红绿灯计算逻辑：
 * 无规则时默认绿灯，根据已耗工时与阈值比较返回正确状态。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class SlaCalculationServiceTest {

    @Mock
    private TimeoutRuleMapper timeoutRuleMapper;

    @Mock
    private CalendarApi calendarApi;

    @InjectMocks
    private SlaCalculationService slaCalculationService;

    /**
     * 无超时规则配置时，默认返回绿灯（安全状态）。
     */
    @Test
    void calculate_noRule_returnsGreen() {
        when(timeoutRuleMapper.selectByProcessDefKeyAndNodeKey("loan_approve", "userTask1"))
                .thenReturn(null);

        SlaStatus result = slaCalculationService.calculateSlaStatus(
                "loan_approve", "userTask1", LocalDateTime.now());

        assertThat(result).isEqualTo(SlaStatus.GREEN);
    }

    /**
     * 已耗工时在预警阈值内时，返回绿灯。
     * 规则：warning=16h, timeout=24h；1个工作日=8h → 8 < 16 → GREEN
     */
    @Test
    void calculate_withinWarning_returnsGreen() {
        WfTimeoutRule rule = new WfTimeoutRule();
        rule.setWarningHours(16);
        rule.setTimeoutHours(24);
        when(timeoutRuleMapper.selectByProcessDefKeyAndNodeKey("loan_approve", "userTask1"))
                .thenReturn(rule);
        when(calendarApi.countWorkingDays(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(1);

        SlaStatus result = slaCalculationService.calculateSlaStatus(
                "loan_approve", "userTask1", LocalDateTime.of(2026, 3, 30, 9, 0));

        assertThat(result).isEqualTo(SlaStatus.GREEN);
    }

    /**
     * 已耗工时介于预警和超时阈值之间时，返回黄灯。
     * 规则：warning=8h, timeout=24h；2个工作日=16h → 8 <= 16 < 24 → YELLOW
     */
    @Test
    void calculate_betweenWarningAndTimeout_returnsYellow() {
        WfTimeoutRule rule = new WfTimeoutRule();
        rule.setWarningHours(8);
        rule.setTimeoutHours(24);
        when(timeoutRuleMapper.selectByProcessDefKeyAndNodeKey("loan_approve", "userTask1"))
                .thenReturn(rule);
        when(calendarApi.countWorkingDays(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(2);

        SlaStatus result = slaCalculationService.calculateSlaStatus(
                "loan_approve", "userTask1", LocalDateTime.of(2026, 3, 28, 9, 0));

        assertThat(result).isEqualTo(SlaStatus.YELLOW);
    }

    /**
     * 已耗工时超过超时阈值时，返回红灯。
     * 规则：warning=8h, timeout=16h；3个工作日=24h → 24 >= 16 → RED
     */
    @Test
    void calculate_exceedsTimeout_returnsRed() {
        WfTimeoutRule rule = new WfTimeoutRule();
        rule.setWarningHours(8);
        rule.setTimeoutHours(16);
        when(timeoutRuleMapper.selectByProcessDefKeyAndNodeKey("loan_approve", "userTask1"))
                .thenReturn(rule);
        when(calendarApi.countWorkingDays(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(3);

        SlaStatus result = slaCalculationService.calculateSlaStatus(
                "loan_approve", "userTask1", LocalDateTime.of(2026, 3, 27, 9, 0));

        assertThat(result).isEqualTo(SlaStatus.RED);
    }
}
