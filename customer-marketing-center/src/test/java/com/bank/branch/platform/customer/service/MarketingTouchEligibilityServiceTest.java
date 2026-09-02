package com.bank.branch.platform.customer.service;

import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.customer.entity.marketing.MarketingCustomerInfo;
import com.bank.branch.platform.customer.entity.marketing.MarketingTouchEligibilityRule;
import com.bank.branch.platform.customer.mapper.TouchEligibilityMapper;
import com.bank.branch.platform.customer.mapper.marketing.MarketingCustomerInfoMapper;
import com.bank.branch.platform.customer.mapper.marketing.MarketingCustomerTagRelMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/** 正式 MARKETING_* 客户触达资格校验测试。 */
@ExtendWith(MockitoExtension.class)
class MarketingTouchEligibilityServiceTest {

    @Mock
    private MarketingCustomerInfoMapper customerMapper;
    @Mock
    private MarketingCustomerTagRelMapper ruleMapper;
    @Mock
    private TouchEligibilityMapper worklogMapper;

    private MarketingTouchEligibilityService service;

    @BeforeEach
    void setUp() {
        service = new MarketingTouchEligibilityService(customerMapper, ruleMapper, worklogMapper);
    }

    @Test
    void shouldReadFormalMarketingCustomerAndTagRuleForNumericCustomerId() {
        LocalDateTime asOf = LocalDateTime.of(2026, 9, 2, 10, 0);
        MarketingCustomerInfo customer = customer(1L, 1, 0);
        MarketingTouchEligibilityRule rule = rule(9L, "重点企业", "MONTH", 5);
        when(customerMapper.selectActiveById(1L)).thenReturn(customer);
        when(ruleMapper.selectEffectiveTouchRules(eq(1L), eq(asOf))).thenReturn(List.of(rule));
        when(worklogMapper.countValidWorklogs(eq("1"), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(0L);

        assertThatCode(() -> service.assertEligible("1", asOf)).doesNotThrowAnyException();
    }

    @Test
    void openedCustomerWithoutStockTagShouldBeRejected() {
        LocalDateTime asOf = LocalDateTime.of(2026, 9, 2, 10, 0);
        when(customerMapper.selectActiveById(1L)).thenReturn(customer(1L, 1, 1));
        when(ruleMapper.selectEffectiveTouchRules(eq(1L), eq(asOf)))
                .thenReturn(List.of(rule(9L, "重点企业", "MONTH", 5)));

        assertThatThrownBy(() -> service.assertEligible("1", asOf))
                .isInstanceOf(BizException.class)
                .hasMessage("该企业经判定已开户，无法再创建工作日志");
    }

    @Test
    void shouldRejectWhenFormalRuleReachesNaturalCycleLimit() {
        LocalDateTime asOf = LocalDateTime.of(2026, 9, 2, 10, 0);
        when(customerMapper.selectActiveById(1L)).thenReturn(customer(1L, 1, 0));
        when(ruleMapper.selectEffectiveTouchRules(eq(1L), eq(asOf)))
                .thenReturn(List.of(rule(9L, "重点企业", "DAY", 2)));
        when(worklogMapper.countValidWorklogs(eq("1"), eq(LocalDateTime.of(2026, 9, 2, 0, 0)),
                eq(LocalDateTime.of(2026, 9, 3, 0, 0)))).thenReturn(2L);

        assertThatThrownBy(() -> service.assertEligible("1", asOf))
                .isInstanceOf(BizException.class)
                .hasMessage("客户触达次数已达到标签周期上限");
    }

    private static MarketingCustomerInfo customer(Long id, Integer restricted, Integer opened) {
        MarketingCustomerInfo customer = new MarketingCustomerInfo();
        customer.setId(id);
        customer.setTouchRestricted(restricted);
        customer.setIsAccountOpened(opened);
        customer.setRecordStatus("ACTIVE");
        return customer;
    }

    private static MarketingTouchEligibilityRule rule(Long tagId, String tagName,
                                                       String cycleType, int maxTouchCount) {
        MarketingTouchEligibilityRule rule = new MarketingTouchEligibilityRule();
        rule.setTagId(tagId);
        rule.setTagName(tagName);
        rule.setCycleType(cycleType);
        rule.setMaxTouchCount(maxTouchCount);
        return rule;
    }

}
