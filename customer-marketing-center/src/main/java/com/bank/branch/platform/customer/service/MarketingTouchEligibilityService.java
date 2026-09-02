package com.bank.branch.platform.customer.service;

import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.customer.entity.marketing.MarketingCustomerInfo;
import com.bank.branch.platform.customer.entity.marketing.MarketingTouchEligibilityRule;
import com.bank.branch.platform.customer.enums.CustomerErrorCode;
import com.bank.branch.platform.customer.mapper.TouchEligibilityMapper;
import com.bank.branch.platform.customer.mapper.marketing.MarketingCustomerInfoMapper;
import com.bank.branch.platform.customer.mapper.marketing.MarketingCustomerTagRelMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 正式营销客户的触达资格校验。
 *
 * <p>目标认领/客户链路使用 {@code MARKETING_*} 表，不能调用兼容旧模型的
 * {@code CUST_MASTER}/{@code CUST_TAG_REL}/{@code CUST_TOUCH_LIMIT_RULE}。
 * 此服务专门读取正式营销主档、正式标签关系、标签触达规则和正式工作日志，
 * 首次触达与再次触达共用同一套校验。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketingTouchEligibilityService {

    private static final String STOCK_CUSTOMER_TAG = "存量客户";
    private static final String DEFAULT_CYCLE_TYPE = "MONTH";
    private static final int DEFAULT_MAX_TOUCH_COUNT = 5;

    private final MarketingCustomerInfoMapper customerMapper;
    private final MarketingCustomerTagRelMapper ruleMapper;
    private final TouchEligibilityMapper worklogMapper;

    /** 使用当前时间校验正式营销客户是否允许新建触达任务。 */
    public void assertEligible(String custId) {
        assertEligible(custId, LocalDateTime.now());
    }

    /**
     * 使用指定时间校验，便于测试自然周期边界及规则生效日期。
     *
     * @param custId 客户主档数字 ID
     * @param asOf   校验时点；为空时使用当前时间
     */
    public void assertEligible(String custId, LocalDateTime asOf) {
        Long customerId = parseId(custId);
        if (customerId == null) {
            throw businessError(CustomerErrorCode.CUSTOMER_NOT_FOUND);
        }
        LocalDateTime currentTime = asOf == null ? LocalDateTime.now() : asOf;
        MarketingCustomerInfo customer = customerMapper.selectActiveById(customerId);
        if (customer == null) {
            throw businessError(CustomerErrorCode.CUSTOMER_NOT_FOUND);
        }

        // 只有明确关闭限制才跳过开户/频次校验；历史 NULL 按受限处理。
        if (Integer.valueOf(0).equals(customer.getTouchRestricted())) {
            return;
        }

        List<MarketingTouchEligibilityRule> rules = ruleMapper.selectEffectiveTouchRules(customerId, currentTime);
        if (rules == null) {
            rules = List.of();
        }
        if (Integer.valueOf(1).equals(customer.getIsAccountOpened())
                && rules.stream().noneMatch(rule -> STOCK_CUSTOMER_TAG.equals(rule.getTagName()))) {
            throw businessError(CustomerErrorCode.TOUCH_RESTRICTED_ACCOUNT_OPENED);
        }

        Map<CycleWindow, Long> countsByWindow = new HashMap<>();
        for (MarketingTouchEligibilityRule rule : rules) {
            if (rule == null) {
                continue;
            }
            String cycleType = normalizeCycleType(rule.getCycleType());
            int maxTouchCount = rule.getMaxTouchCount() == null || rule.getMaxTouchCount() < 1
                    ? DEFAULT_MAX_TOUCH_COUNT : rule.getMaxTouchCount();
            CycleWindow window = cycleWindow(cycleType, currentTime);
            long count = countsByWindow.computeIfAbsent(window,
                    item -> worklogMapper.countValidWorklogs(
                            String.valueOf(customerId), item.start(), item.end()));
            if (count >= maxTouchCount) {
                throw businessError(CustomerErrorCode.TOUCH_LIMIT_REACHED);
            }
        }
    }

    private String normalizeCycleType(String cycleType) {
        String normalized = cycleType == null ? DEFAULT_CYCLE_TYPE : cycleType.toUpperCase();
        return switch (normalized) {
            case "DAY", "WEEK", "MONTH", "QUARTER", "YEAR" -> normalized;
            default -> DEFAULT_CYCLE_TYPE;
        };
    }

    private CycleWindow cycleWindow(String cycleType, LocalDateTime currentTime) {
        LocalDate date = currentTime.toLocalDate();
        LocalDate startDate;
        LocalDate endDate;
        switch (cycleType) {
            case "DAY" -> {
                startDate = date;
                endDate = date.plusDays(1);
            }
            case "WEEK" -> {
                startDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                endDate = startDate.plusWeeks(1);
            }
            case "QUARTER" -> {
                int firstMonth = ((date.getMonthValue() - 1) / 3) * 3 + 1;
                startDate = LocalDate.of(date.getYear(), firstMonth, 1);
                endDate = startDate.plusMonths(3);
            }
            case "YEAR" -> {
                startDate = LocalDate.of(date.getYear(), 1, 1);
                endDate = startDate.plusYears(1);
            }
            case "MONTH" -> {
                startDate = date.withDayOfMonth(1);
                endDate = startDate.plusMonths(1);
            }
            default -> {
                startDate = date.withDayOfMonth(1);
                endDate = startDate.plusMonths(1);
            }
        }
        return new CycleWindow(startDate.atStartOfDay(), endDate.atStartOfDay());
    }

    private Long parseId(String custId) {
        if (custId == null || custId.isBlank()) {
            return null;
        }
        try {
            long id = Long.parseLong(custId);
            return id > 0 ? id : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private BizException businessError(CustomerErrorCode errorCode) {
        return new BizException(errorCode.getCode(), errorCode.getMessage());
    }

    private record CycleWindow(LocalDateTime start, LocalDateTime end) {
    }
}
