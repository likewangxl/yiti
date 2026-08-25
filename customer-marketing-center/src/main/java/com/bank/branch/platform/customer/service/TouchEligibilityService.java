package com.bank.branch.platform.customer.service;

import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.customer.entity.CustMaster;
import com.bank.branch.platform.customer.entity.CustTag;
import com.bank.branch.platform.customer.entity.CustTagRel;
import com.bank.branch.platform.customer.entity.TouchLimitRule;
import com.bank.branch.platform.customer.enums.CustomerErrorCode;
import com.bank.branch.platform.customer.enums.TouchLimitCycleUnit;
import com.bank.branch.platform.customer.mapper.CustMasterMapper;
import com.bank.branch.platform.customer.mapper.CustTagMapper;
import com.bank.branch.platform.customer.mapper.CustTagRelMapper;
import com.bank.branch.platform.customer.mapper.TouchEligibilityMapper;
import com.bank.branch.platform.customer.mapper.TouchLimitRuleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 首次/再次触达前的客户资格校验。
 * <p>
 * 资格规则独立于触达任务和工作日志迁移服务：只读取客户、标签、标签规则及
 * xa 工作日志统计，不创建或修改任何触达数据。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class TouchEligibilityService {

    private static final String STOCK_CUSTOMER_TAG = "存量客户";
    private static final String DEFAULT_CYCLE_UNIT = TouchLimitCycleUnit.MONTH.name();
    private static final int DEFAULT_MAX_TOUCHES = 5;

    private final CustMasterMapper masterMapper;
    private final CustTagRelMapper tagRelMapper;
    private final CustTagMapper tagMapper;
    private final TouchLimitRuleMapper ruleMapper;
    private final TouchEligibilityMapper eligibilityMapper;

    /** 使用当前时间进行资格校验，供 ClaimService 的首次/再次触达入口调用。 */
    public void assertEligible(String custId) {
        assertEligible(custId, LocalDateTime.now());
    }

    /**
     * 使用指定时间进行资格校验，便于稳定测试自然周期边界。
     */
    public void assertEligible(String custId, LocalDateTime now) {
        CustMaster customer = masterMapper.selectById(custId);
        if (customer == null) {
            throw businessError(CustomerErrorCode.CUSTOMER_NOT_FOUND);
        }

        // 只有明确的 0 表示关闭限制；历史数据 null 按受限处理。
        if (Integer.valueOf(0).equals(customer.getTouchRestricted())) {
            return;
        }

        LocalDateTime currentTime = now == null ? LocalDateTime.now() : now;
        List<EffectiveTag> effectiveTags = findEffectiveTags(custId, currentTime);
        if (Integer.valueOf(1).equals(customer.getIsAccountOpened())
                && effectiveTags.stream().noneMatch(item -> STOCK_CUSTOMER_TAG.equals(item.tag().getTagName()))) {
            throw businessError(CustomerErrorCode.TOUCH_RESTRICTED_ACCOUNT_OPENED);
        }

        Map<String, TouchLimitRule> rulesByTagId = loadRules(effectiveTags);
        Map<CycleWindow, Long> countsByWindow = new HashMap<>();
        for (EffectiveTag effectiveTag : effectiveTags) {
            TouchLimitRule rule = rulesByTagId.get(effectiveTag.tag().getId());
            String cycleUnit = rule == null || rule.getCycleUnit() == null
                    ? DEFAULT_CYCLE_UNIT : rule.getCycleUnit();
            int maxTouches = rule == null || rule.getMaxTouches() == null
                    ? DEFAULT_MAX_TOUCHES : rule.getMaxTouches();
            if (maxTouches < 1) {
                maxTouches = DEFAULT_MAX_TOUCHES;
            }
            CycleWindow window = cycleWindow(cycleUnit, currentTime);
            long count = countsByWindow.computeIfAbsent(window,
                    item -> eligibilityMapper.countValidWorklogs(
                            customer.getId(), item.start(), item.end()));
            if (count >= maxTouches) {
                throw businessError(CustomerErrorCode.TOUCH_LIMIT_REACHED);
            }
        }
    }

    private List<EffectiveTag> findEffectiveTags(String custId, LocalDateTime now) {
        List<CustTagRel> relations = tagRelMapper.selectByCustId(custId);
        if (relations == null || relations.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, CustTag> tagsById = loadTags(relations);
        List<EffectiveTag> result = new ArrayList<>();
        for (CustTagRel relation : relations) {
            if (relation == null || relation.getTagId() == null
                    || (relation.getActive() != null && !Integer.valueOf(1).equals(relation.getActive()))) {
                continue;
            }
            if (relation.getEffectiveTime() != null
                    && relation.getEffectiveTime().isAfter(now)) {
                continue;
            }
            if (relation.getExpiredTime() != null
                    && !relation.getExpiredTime().isAfter(now)) {
                continue;
            }
            CustTag tag = tagsById.get(relation.getTagId());
            if (tag == null
                    || !Integer.valueOf(0).equals(tag.getDeleted())
                    || !"ACTIVE".equals(tag.getStatus())
                    || (tag.getApprovalStatus() != null
                    && !"APPROVED".equals(tag.getApprovalStatus()))
                    || (tag.getExpiresAt() != null
                    && tag.getExpiresAt().isBefore(now.toLocalDate()))) {
                continue;
            }
            result.add(new EffectiveTag(relation, tag));
        }
        return result;
    }

    private Map<String, CustTag> loadTags(List<CustTagRel> relations) {
        List<String> tagIds = relations.stream()
                .filter(item -> item != null && item.getTagId() != null)
                .map(CustTagRel::getTagId)
                .distinct()
                .toList();
        if (tagIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, CustTag> result = new HashMap<>();
        List<CustTag> enabledTags = tagMapper.selectEnabledByIds(tagIds);
        if (enabledTags != null) {
            enabledTags.stream().filter(item -> item != null && item.getId() != null)
                    .forEach(item -> result.put(item.getId(), item));
        }
        return result;
    }

    private Map<String, TouchLimitRule> loadRules(List<EffectiveTag> effectiveTags) {
        List<String> tagIds = effectiveTags.stream()
                .map(item -> item.tag().getId())
                .filter(item -> item != null)
                .distinct()
                .toList();
        if (tagIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, TouchLimitRule> result = new HashMap<>();
        List<TouchLimitRule> batchRules = ruleMapper.selectByTagIds(tagIds);
        if (batchRules != null) {
            batchRules.stream().filter(item -> item != null && item.getTagId() != null)
                    .forEach(item -> result.put(item.getTagId(), item));
        }
        return result;
    }

    private CycleWindow cycleWindow(String cycleUnit, LocalDateTime now) {
        LocalDate date = now.toLocalDate();
        LocalDate startDate;
        LocalDate endDate;
        switch (cycleUnit) {
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

    private BizException businessError(CustomerErrorCode errorCode) {
        return new BizException(errorCode.getCode(), errorCode.getMessage());
    }

    private record EffectiveTag(CustTagRel relation, CustTag tag) {
    }

    private record CycleWindow(LocalDateTime start, LocalDateTime end) {
    }
}
