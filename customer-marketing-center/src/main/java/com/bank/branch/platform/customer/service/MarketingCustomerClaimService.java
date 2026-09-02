package com.bank.branch.platform.customer.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.dto.OrgDTO;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.customer.dto.req.ReTouchReqDTO;
import com.bank.branch.platform.customer.dto.resp.ClaimedCustomerRespDTO;
import com.bank.branch.platform.customer.entity.TouchTask;
import com.bank.branch.platform.customer.entity.marketing.MarketingCustomerClaim;
import com.bank.branch.platform.customer.entity.marketing.MarketingCustomerInfo;
import com.bank.branch.platform.customer.entity.marketing.MarketingLeadInfo;
import com.bank.branch.platform.customer.entity.marketing.MarketingLeadTagRel;
import com.bank.branch.platform.customer.enums.CustomerErrorCode;
import com.bank.branch.platform.customer.mapper.TouchTaskMapper;
import com.bank.branch.platform.customer.mapper.marketing.MarketingCustomerClaimMapper;
import com.bank.branch.platform.customer.mapper.marketing.MarketingCustomerInfoMapper;
import com.bank.branch.platform.customer.mapper.marketing.MarketingLeadInfoMapper;
import com.bank.branch.platform.customer.mapper.marketing.MarketingLeadTagRelMapper;
import com.bank.branch.platform.governance.api.DictApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 目标营销表认领服务。
 *
 * <p>认领请求必须重新锁定线索和客户主档后再判断状态与主办权，避免页面看到
 * 可认领但提交时主办权已变化仍写入关系。数据库唯一索引
 * {@code (source_lead_id, claimed_by)} 是并发兜底。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketingCustomerClaimService {

    private static final String CLAIMED = "CLAIMED";
    private static final String CANCELLED = "CANCELLED";

    private final MarketingCustomerClaimMapper claimMapper;
    private final MarketingLeadInfoMapper leadMapper;
    private final MarketingCustomerInfoMapper customerMapper;
    private final TouchTaskMapper touchTaskMapper;
    private final TouchTaskService touchTaskService;
    private final DictApi dictApi;
    private final OrgApi orgApi;
    private final MarketingLeadTagRelMapper leadTagRelMapper;

    /**
     * 认领一条公开线索。
     *
     * @param sourceLeadId 来源线索 ID
     * @param orgId        当前员工机构
     * @param empId        当前员工工号
     * @return 新认领关系
     */
    @Transactional
    public MarketingCustomerClaim claim(Long sourceLeadId, String orgId, String empId) {
        if (sourceLeadId == null || !StringUtils.hasText(orgId) || !StringUtils.hasText(empId)) {
            throw business("CUST-42218", "认领线索、机构和员工不能为空");
        }

        MarketingLeadInfo lead = leadMapper.selectForUpdate(sourceLeadId);
        if (lead == null || !"ACTIVE".equals(lead.getRecordStatus())) {
            throw new BizException(CustomerErrorCode.LEAD_NOT_FOUND.getCode(),
                    CustomerErrorCode.LEAD_NOT_FOUND.getMessage());
        }
        if (!"APPROVED".equals(lead.getLeadStatus())
                || !"PUBLIC".equals(lead.getDistributionMode())
                || !"AVAILABLE".equals(lead.getPoolStatus())) {
            throw business("CUST-42219", "该线索当前不在待认领池");
        }
        if (lead.getCustId() == null) {
            throw business("CUST-42219", "待认领线索尚未关联有效客户");
        }

        MarketingCustomerInfo customer = customerMapper.selectForUpdate(lead.getCustId());
        if (customer == null || !"ACTIVE".equals(customer.getRecordStatus())) {
            throw new BizException(CustomerErrorCode.CUSTOMER_NOT_FOUND.getCode(),
                    CustomerErrorCode.CUSTOMER_NOT_FOUND.getMessage());
        }
        if (hasValidOwner(customer)) {
            throw business("CUST-42219", "该客户已有有效主办，不能从待认领池认领");
        }
        if (claimMapper.selectActiveBySourceLeadAndClaimedBy(sourceLeadId, empId) != null) {
            throw new BizException(CustomerErrorCode.CUSTOMER_ALREADY_CLAIMED.getCode(),
                    CustomerErrorCode.CUSTOMER_ALREADY_CLAIMED.getMessage());
        }

        LocalDateTime now = LocalDateTime.now();
        MarketingCustomerClaim claim = new MarketingCustomerClaim();
        claim.setCustId(lead.getCustId());
        claim.setSourceLeadId(sourceLeadId);
        claim.setOrgId(orgId);
        claim.setClaimedBy(empId);
        claim.setMaintainerEmpId(empId);
        claim.setClaimStatus(CLAIMED);
        claim.setClaimTime(now);
        claim.setCreatedBy(empId);
        claim.setCreatedTime(now);
        claim.setUpdatedBy(empId);
        claim.setUpdatedTime(now);
        try {
            claimMapper.insert(claim);
        } catch (DuplicateKeyException ex) {
            log.warn("[MarketingCustomerClaimService.claim] duplicate source lead claim, leadId={}, empId={}",
                    sourceLeadId, empId);
            throw new BizException(CustomerErrorCode.CUSTOMER_ALREADY_CLAIMED.getCode(),
                    CustomerErrorCode.CUSTOMER_ALREADY_CLAIMED.getMessage());
        }
        return claim;
    }

    /** 查询本人已认领池，支持未触达/已触达页签、关键词和分配来源筛选。 */
    public PageResult<ClaimedCustomerRespDTO> listClaimed(String empId, String tab, String keyword,
                                                          String sourceType, int pageNo, int pageSize) {
        int safePageNo = Math.max(1, pageNo);
        int safePageSize = Math.min(Math.max(1, pageSize), 100);
        int offset = (safePageNo - 1) * safePageSize;
        List<ClaimedCustomerRespDTO> records = claimMapper.selectClaimedCustomerPage(
                empId, normalizeTab(tab), keyword, sourceType, offset, safePageSize);
        fillDisplayNames(records);
        long total = claimMapper.countClaimedCustomerPage(empId, normalizeTab(tab), keyword, sourceType);
        return PageResult.of(safePageNo, safePageSize, total, records == null ? List.of() : records);
    }

    /** 取消目标营销表认领关系；只允许该认领维护人操作。 */
    @Transactional
    public void cancel(String claimId, String reason, String operatorEmpId, String operatorOrgId) {
        if (!StringUtils.hasText(reason)) {
            throw new BizException(CustomerErrorCode.CANCEL_REASON_REQUIRED.getCode(),
                    CustomerErrorCode.CANCEL_REASON_REQUIRED.getMessage());
        }
        Long id = parseId(claimId);
        MarketingCustomerClaim claim = claimMapper.selectForUpdate(id);
        if (claim == null) {
            throw new BizException(CustomerErrorCode.CLAIM_NOT_FOUND.getCode(),
                    CustomerErrorCode.CLAIM_NOT_FOUND.getMessage());
        }
        if (!Objects.equals(operatorOrgId, claim.getOrgId())) {
            throw new BizException(CustomerErrorCode.CLAIM_ORG_FORBIDDEN.getCode(),
                    CustomerErrorCode.CLAIM_ORG_FORBIDDEN.getMessage());
        }
        if (!Objects.equals(operatorEmpId, claim.getClaimedBy())
                && !Objects.equals(operatorEmpId, claim.getMaintainerEmpId())) {
            throw new BizException(CustomerErrorCode.TOUCH_TASK_ACCESS_FORBIDDEN.getCode(),
                    CustomerErrorCode.TOUCH_TASK_ACCESS_FORBIDDEN.getMessage());
        }
        if (!CLAIMED.equals(claim.getClaimStatus())) {
            throw new BizException(CustomerErrorCode.CROSS_ORG_STATUS_CONFLICT.getCode(),
                    "认领关系已取消");
        }
        LocalDateTime now = LocalDateTime.now();
        MarketingCustomerClaim update = new MarketingCustomerClaim();
        update.setId(id);
        update.setClaimStatus(CANCELLED);
        update.setCancelTime(now);
        update.setCancelReason(reason.trim());
        update.setUpdatedBy(operatorEmpId);
        update.setUpdatedTime(now);
        claimMapper.updateById(update);
    }

    /** 从目标认领关系手动发起首次触达，仍兼容当前 TouchTaskService。 */
    @Transactional
    public TouchTask startTouch(String claimId, String planFinishTime,
                                String operatorEmpId, String operatorOrgId) {
        MarketingCustomerClaim claim = requireOwnedActive(claimId, operatorEmpId, operatorOrgId);
        String custId = String.valueOf(claim.getCustId());
        List<TouchTask> active = touchTaskMapper.selectActiveByCustAndAssignee(
                custId, claim.getMaintainerEmpId());
        if (active != null && !active.isEmpty()) {
            throw new BizException(CustomerErrorCode.RE_TOUCH_HAS_RUNNING.getCode(),
                    CustomerErrorCode.RE_TOUCH_HAS_RUNNING.getMessage());
        }
        return touchTaskService.createFirstTouchTask(custId, claim.getOrgId(),
                claim.getMaintainerEmpId(), planFinishTime);
    }

    /** 从目标认领关系再次发起后续触达，仍兼容当前 TouchTaskService。 */
    @Transactional
    public TouchTask reTouch(String claimId, ReTouchReqDTO req,
                             String operatorEmpId, String operatorOrgId) {
        MarketingCustomerClaim claim = requireOwnedActive(claimId, operatorEmpId, operatorOrgId);
        String custId = String.valueOf(claim.getCustId());
        List<TouchTask> active = touchTaskMapper.selectActiveByCustAndAssignee(
                custId, claim.getMaintainerEmpId());
        if (active != null && !active.isEmpty()) {
            throw new BizException(CustomerErrorCode.RE_TOUCH_HAS_RUNNING.getCode(),
                    CustomerErrorCode.RE_TOUCH_HAS_RUNNING.getMessage());
        }
        return touchTaskService.createFollowUpTask(custId, claim.getOrgId(), claim.getMaintainerEmpId(),
                req == null ? null : req.getReason(), req == null ? null : req.getPlanFinishTime());
    }

    private MarketingCustomerClaim requireOwnedActive(String claimId, String empId, String orgId) {
        MarketingCustomerClaim claim = claimMapper.selectForUpdate(parseId(claimId));
        if (claim == null) {
            throw new BizException(CustomerErrorCode.CLAIM_NOT_FOUND.getCode(),
                    CustomerErrorCode.CLAIM_NOT_FOUND.getMessage());
        }
        if (!Objects.equals(orgId, claim.getOrgId())) {
            throw new BizException(CustomerErrorCode.CLAIM_ORG_FORBIDDEN.getCode(),
                    CustomerErrorCode.CLAIM_ORG_FORBIDDEN.getMessage());
        }
        if (!CLAIMED.equals(claim.getClaimStatus())
                || (!Objects.equals(empId, claim.getClaimedBy())
                && !Objects.equals(empId, claim.getMaintainerEmpId()))) {
            throw new BizException(CustomerErrorCode.TOUCH_TASK_ACCESS_FORBIDDEN.getCode(),
                    CustomerErrorCode.TOUCH_TASK_ACCESS_FORBIDDEN.getMessage());
        }
        return claim;
    }

    private boolean hasValidOwner(MarketingCustomerInfo customer) {
        // 只有明确的 UNASSIGNED 且无主办快照才允许公共池认领；UNKNOWN/CONFLICT
        // 等异常状态一律 fail-close，交由主办权处理流程处置。
        return !"UNASSIGNED".equals(customer.getOwnershipStatus())
                || StringUtils.hasText(customer.getMainManagerId());
    }

    private String normalizeTab(String tab) {
        if (!StringUtils.hasText(tab) || "ALL".equalsIgnoreCase(tab)) {
            return null;
        }
        if ("UNTOUCHED".equalsIgnoreCase(tab) || "TOUCHED".equalsIgnoreCase(tab)) {
            return tab.toUpperCase();
        }
        throw business("CUST-40014", "已认领客户页签仅支持UNTOUCHED或TOUCHED");
    }

    private void fillDisplayNames(List<ClaimedCustomerRespDTO> records) {
        if (records == null || records.isEmpty() || dictApi == null || orgApi == null) {
            return;
        }
        List<String> codes = records.stream().filter(Objects::nonNull)
                .map(ClaimedCustomerRespDTO::getOwnerOrgId)
                .filter(StringUtils::hasText).distinct().toList();
        Map<String, String> orgNames = new HashMap<>();
        if (!codes.isEmpty()) {
            List<OrgDTO> orgs = orgApi.getOrgsByCodes(codes);
            if (orgs != null) {
                for (OrgDTO org : orgs) {
                    if (org != null && StringUtils.hasText(org.getOrgCode())) {
                        orgNames.put(org.getOrgCode(), org.getOrgName());
                    }
                }
            }
        }
        Map<String, String> industryNames = new HashMap<>();
        Map<String, String> customerTypeNames = new HashMap<>();
        Map<Long, List<String>> tagNames = loadTagNames(records);
        for (ClaimedCustomerRespDTO row : records) {
            if (row == null) continue;
            String industry = row.getIndustry();
            if (StringUtils.hasText(industry) && !industryNames.containsKey(industry)) {
                industryNames.put(industry, translatedLabel("INDUSTRY", industry));
            }
            String type = row.getCustomerType();
            if (StringUtils.hasText(type) && !customerTypeNames.containsKey(type)) {
                customerTypeNames.put(type, translatedLabel("CUSTOMER_TYPE", type));
            }
            row.setIndustryName(industryNames.get(industry));
            row.setCustomerTypeName(customerTypeNames.get(type));
            row.setOwnerOrgName(orgNames.get(row.getOwnerOrgId()));
            Long leadId = parseNullableId(row.getSourceLeadId());
            row.setTagNames(leadId == null ? List.of() : tagNames.getOrDefault(leadId, List.of()));
        }
    }

    private Map<Long, List<String>> loadTagNames(List<ClaimedCustomerRespDTO> records) {
        List<Long> leadIds = records.stream().filter(Objects::nonNull)
                .map(ClaimedCustomerRespDTO::getSourceLeadId).map(this::parseNullableId)
                .filter(Objects::nonNull).distinct().toList();
        if (leadIds.isEmpty()) return Map.of();
        List<MarketingLeadTagRel> relations = leadTagRelMapper.selectList(Wrappers
                .<MarketingLeadTagRel>lambdaQuery()
                .in(MarketingLeadTagRel::getLeadId, leadIds)
                .orderByAsc(MarketingLeadTagRel::getId));
        if (relations == null || relations.isEmpty()) return Map.of();
        return relations.stream().filter(Objects::nonNull)
                .filter(item -> item.getLeadId() != null && StringUtils.hasText(item.getTagNameSnapshot()))
                .collect(Collectors.groupingBy(MarketingLeadTagRel::getLeadId,
                        Collectors.mapping(MarketingLeadTagRel::getTagNameSnapshot,
                                Collectors.collectingAndThen(
                                        Collectors.toCollection(java.util.LinkedHashSet::new), List::copyOf))));
    }

    private Long parseNullableId(String value) {
        if (!StringUtils.hasText(value)) return null;
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String translatedLabel(String type, String code) {
        String label = dictApi.getDictLabel(type, code);
        return StringUtils.hasText(label) && !Objects.equals(code, label) ? label : null;
    }

    private Long parseId(String value) {
        try {
            return Long.valueOf(value);
        } catch (RuntimeException ex) {
            throw new BizException(CustomerErrorCode.CLAIM_NOT_FOUND.getCode(),
                    CustomerErrorCode.CLAIM_NOT_FOUND.getMessage());
        }
    }

    private BizException business(String code, String message) {
        return new BizException(code, message);
    }
}
