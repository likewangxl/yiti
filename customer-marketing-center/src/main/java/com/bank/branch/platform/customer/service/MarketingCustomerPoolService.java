package com.bank.branch.platform.customer.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.dto.OrgDTO;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.customer.api.dto.CustomerDTO;
import com.bank.branch.platform.customer.entity.marketing.MarketingLeadTagRel;
import com.bank.branch.platform.customer.mapper.marketing.MarketingCustomerClaimMapper;
import com.bank.branch.platform.customer.mapper.marketing.MarketingLeadTagRelMapper;
import com.bank.branch.platform.governance.api.DictApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 基于目标 MARKETING_* 表的客户池查询服务。
 *
 * <p>待认领池不持久化独立池实体，查询由线索、客户主档和认领关系组合得到。
 * SQL 已强制收敛到 APPROVED + PUBLIC + AVAILABLE，并且只排除当前员工自己的
 * 有效认领，不影响其他员工继续认领公开线索。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketingCustomerPoolService {

    private final MarketingCustomerClaimMapper claimMapper;
    private final DictApi dictApi;
    private final OrgApi orgApi;
    private final MarketingLeadTagRelMapper leadTagRelMapper;

    /** 查询当前员工的目标待认领池。 */
    public PageResult<CustomerDTO> listAvailable(String keyword, String empId,
                                                 int pageNo, int pageSize) {
        int safePageNo = Math.max(1, pageNo);
        int safePageSize = Math.min(Math.max(1, pageSize), 100);
        int offset = (safePageNo - 1) * safePageSize;
        List<CustomerDTO> records = claimMapper.selectAvailablePoolPage(keyword, empId, offset, safePageSize);
        fillDisplayNames(records);
        long total = claimMapper.countAvailablePoolPage(keyword, empId);
        return PageResult.of(safePageNo, safePageSize, total, records == null ? List.of() : records);
    }

    private void fillDisplayNames(List<CustomerDTO> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        Map<String, String> orgNames = loadOrgNames(records);
        Map<String, String> industryNames = new HashMap<>();
        Map<String, String> customerTypeNames = new HashMap<>();
        Map<String, String> groupTypeNames = new HashMap<>();
        Map<String, String> enterpriseTypeNames = new HashMap<>();
        Map<Long, List<String>> tagNames = loadTagNames(records);
        for (CustomerDTO item : records) {
            if (item == null) {
                continue;
            }
            String industry = item.getIndustry();
            if (StringUtils.hasText(industry) && !industryNames.containsKey(industry)) {
                industryNames.put(industry, translate("INDUSTRY", industry));
            }
            String customerType = item.getCustomerType();
            if (StringUtils.hasText(customerType) && !customerTypeNames.containsKey(customerType)) {
                customerTypeNames.put(customerType, translate("CUSTOMER_TYPE", customerType));
            }
            String groupType = item.getGroupType();
            if (StringUtils.hasText(groupType) && !groupTypeNames.containsKey(groupType)) {
                groupTypeNames.put(groupType, translate("GROUP_TYPE", groupType));
            }
            String enterpriseType = item.getEnterpriseType();
            if (StringUtils.hasText(enterpriseType) && !enterpriseTypeNames.containsKey(enterpriseType)) {
                enterpriseTypeNames.put(enterpriseType, translate("ENTERPRISE_TYPE", enterpriseType));
            }
            item.setIndustryName(industryNames.get(industry));
            item.setCustomerTypeName(customerTypeNames.get(customerType));
            item.setGroupTypeName(groupTypeNames.get(groupType));
            item.setEnterpriseTypeName(enterpriseTypeNames.get(enterpriseType));
            item.setOwnerOrgName(orgNames.get(item.getOwnerOrgId()));
            Long leadId = parseLong(item.getSourceLeadId());
            item.setTagNames(leadId == null ? List.of() : tagNames.getOrDefault(leadId, List.of()));
        }
    }

    private Map<Long, List<String>> loadTagNames(List<CustomerDTO> records) {
        List<Long> leadIds = records.stream().filter(Objects::nonNull)
                .map(CustomerDTO::getSourceLeadId).map(this::parseLong)
                .filter(Objects::nonNull).distinct().toList();
        if (leadIds.isEmpty()) return Map.of();
        List<MarketingLeadTagRel> relations = leadTagRelMapper.selectList(Wrappers
                .<MarketingLeadTagRel>lambdaQuery()
                .in(MarketingLeadTagRel::getLeadId, leadIds)
                .orderByAsc(MarketingLeadTagRel::getId));
        if (relations == null || relations.isEmpty()) return Map.of();
        return relations.stream()
                .filter(Objects::nonNull)
                .filter(item -> item.getLeadId() != null && StringUtils.hasText(item.getTagNameSnapshot()))
                .collect(Collectors.groupingBy(MarketingLeadTagRel::getLeadId,
                        Collectors.mapping(MarketingLeadTagRel::getTagNameSnapshot,
                                Collectors.collectingAndThen(
                                        Collectors.toCollection(java.util.LinkedHashSet::new), List::copyOf))));
    }

    private Long parseLong(String value) {
        if (!StringUtils.hasText(value)) return null;
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Map<String, String> loadOrgNames(List<CustomerDTO> records) {
        List<String> codes = records.stream()
                .filter(Objects::nonNull)
                .map(CustomerDTO::getOwnerOrgId)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (codes.isEmpty()) {
            return Map.of();
        }
        List<OrgDTO> orgs = orgApi.getOrgsByCodes(codes);
        if (orgs == null || orgs.isEmpty()) {
            return Map.of();
        }
        Map<String, String> result = new HashMap<>();
        for (OrgDTO org : orgs) {
            if (org != null && StringUtils.hasText(org.getOrgCode())) {
                result.put(org.getOrgCode(), org.getOrgName());
            }
        }
        return result;
    }

    private String translate(String dictType, String code) {
        String label = dictApi.getDictLabel(dictType, code);
        return StringUtils.hasText(label) && !Objects.equals(code, label) ? label : null;
    }
}
