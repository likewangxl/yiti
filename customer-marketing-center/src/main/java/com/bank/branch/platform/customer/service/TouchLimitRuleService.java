package com.bank.branch.platform.customer.service;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.customer.dto.req.TouchLimitRuleUpdateReqDTO;
import com.bank.branch.platform.customer.dto.resp.TouchLimitRuleRespDTO;
import com.bank.branch.platform.customer.entity.CustTag;
import com.bank.branch.platform.customer.entity.TouchLimitRule;
import com.bank.branch.platform.customer.enums.CustomerErrorCode;
import com.bank.branch.platform.customer.enums.TouchLimitCycleUnit;
import com.bank.branch.platform.customer.mapper.CustTagMapper;
import com.bank.branch.platform.customer.mapper.TouchLimitRuleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 客户标签触达周期规则服务。
 *
 * <p>规则表只存显式配置；列表读取时先取当前页标签，再批量加载规则，
 * 从而为未配置标签提供默认值且不产生 N+1 查询。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TouchLimitRuleService {

    private static final String INVALID_RULE_CODE = "VALID_003";
    private static final String DEFAULT_CYCLE_UNIT = TouchLimitCycleUnit.MONTH.name();
    private static final int DEFAULT_MAX_TOUCHES = 5;
    private static final int MAX_PAGE_SIZE = 100;

    private final TouchLimitRuleMapper ruleMapper;
    private final CustTagMapper tagMapper;

    /**
     * 按标签分页查看触达周期规则。
     * 标签查询本身过滤 deleted=0，规则查询严格限制为当前页标签 ID 的一次批量读取。
     */
    public PageResult<TouchLimitRuleRespDTO> listPage(String keyword, int pageNo, int pageSize) {
        int safePageNo = Math.max(pageNo, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        int offset = (safePageNo - 1) * safePageSize;

        List<CustTag> tags = tagMapper.selectPage(keyword, null, offset, safePageSize);
        if (tags == null) {
            tags = Collections.emptyList();
        }
        long total = tagMapper.countPage(keyword, null);

        List<TouchLimitRuleRespDTO> records = toResponse(tags);
        return PageResult.of(safePageNo, safePageSize, total, records);
    }

    /**
     * 修改一个标签的触达周期规则；不存在时插入，存在时保留创建审计字段并更新。
     * 标签存在性在服务层重新校验，避免仅依赖前端或物理外键。
     */
    @Transactional
    public void updateRule(String tagId, TouchLimitRuleUpdateReqDTO request, String operatorEmpId) {
        validateRequest(request);
        requireTagExists(tagId);

        LocalDateTime now = LocalDateTime.now();
        TouchLimitRule existing = ruleMapper.selectByTagId(tagId);
        if (existing == null) {
            TouchLimitRule created = new TouchLimitRule();
            created.setId(UUID.randomUUID().toString().replace("-", ""));
            created.setTagId(tagId);
            created.setCycleUnit(request.getCycleUnit());
            created.setMaxTouches(request.getMaxTouches());
            created.setCreatedBy(operatorEmpId);
            created.setCreatedTime(now);
            created.setUpdatedBy(operatorEmpId);
            created.setUpdatedTime(now);
            ruleMapper.insert(created);
            return;
        }

        TouchLimitRule update = new TouchLimitRule();
        update.setId(existing.getId());
        update.setCreatedBy(existing.getCreatedBy());
        update.setCreatedTime(existing.getCreatedTime());
        update.setCycleUnit(request.getCycleUnit());
        update.setMaxTouches(request.getMaxTouches());
        update.setUpdatedBy(operatorEmpId);
        update.setUpdatedTime(now);
        ruleMapper.updateById(update);
    }

    private List<TouchLimitRuleRespDTO> toResponse(List<CustTag> tags) {
        if (tags.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> tagIds = tags.stream()
                .map(CustTag::getId)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toList());
        Map<String, TouchLimitRule> rulesByTagId = Collections.emptyMap();
        if (!tagIds.isEmpty()) {
            List<TouchLimitRule> rules = ruleMapper.selectByTagIds(tagIds);
            if (rules != null && !rules.isEmpty()) {
                rulesByTagId = rules.stream()
                        .filter(rule -> rule != null && rule.getTagId() != null)
                        .collect(Collectors.toMap(TouchLimitRule::getTagId,
                                Function.identity(), (first, ignored) -> first, HashMap::new));
            }
        }

        Map<String, TouchLimitRule> finalRulesByTagId = rulesByTagId;
        return tags.stream().map(tag -> {
            TouchLimitRule rule = finalRulesByTagId.get(tag.getId());
            TouchLimitRuleRespDTO response = new TouchLimitRuleRespDTO();
            response.setTagId(tag.getId());
            response.setTagName(tag.getTagName());
            response.setTagStatus(tag.getStatus());
            response.setApprovalStatus(tag.getApprovalStatus());
            response.setCycleUnit(rule == null ? DEFAULT_CYCLE_UNIT : rule.getCycleUnit());
            response.setMaxTouches(rule == null ? DEFAULT_MAX_TOUCHES : rule.getMaxTouches());
            return response;
        }).collect(Collectors.toList());
    }

    private void requireTagExists(String tagId) {
        if (tagId == null || tagId.isBlank()) {
            throw tagNotFound();
        }
        CustTag tag = tagMapper.selectById(tagId);
        if (tag == null || Integer.valueOf(1).equals(tag.getDeleted())) {
            throw tagNotFound();
        }
    }

    private void validateRequest(TouchLimitRuleUpdateReqDTO request) {
        if (request == null || !TouchLimitCycleUnit.isSupported(request.getCycleUnit())
                || request.getMaxTouches() == null
                || request.getMaxTouches() < 1 || request.getMaxTouches() > 9999) {
            throw new BizException(INVALID_RULE_CODE, "触达周期规则参数无效");
        }
    }

    private BizException tagNotFound() {
        return new BizException(CustomerErrorCode.TAG_NOT_FOUND.getCode(),
                CustomerErrorCode.TAG_NOT_FOUND.getMessage());
    }
}
