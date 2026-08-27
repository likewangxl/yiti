package com.bank.branch.platform.customer.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.OrgDTO;
import com.bank.branch.platform.auth.api.dto.UserDTO;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.customer.dto.resp.CrossOrgApplyRespDTO;
import com.bank.branch.platform.customer.dto.resp.CrossOrgValidationRespDTO;
import com.bank.branch.platform.customer.entity.marketing.MarketingCrossOrgApply;
import com.bank.branch.platform.customer.entity.marketing.MarketingCrossOrgRule;
import com.bank.branch.platform.customer.entity.marketing.MarketingCustomerInfo;
import com.bank.branch.platform.customer.entity.marketing.MarketingCustomerPerformanceRelSnapshot;
import com.bank.branch.platform.customer.entity.marketing.MarketingTouchTask;
import com.bank.branch.platform.customer.mapper.marketing.MarketingCrossOrgApplyMapper;
import com.bank.branch.platform.customer.mapper.marketing.MarketingCrossOrgRuleMapper;
import com.bank.branch.platform.customer.mapper.marketing.MarketingCustomerInfoMapper;
import com.bank.branch.platform.customer.mapper.marketing.MarketingCustomerPerformanceRelSnapshotMapper;
import com.bank.branch.platform.customer.mapper.marketing.MarketingTouchTaskMapper;
import com.bank.branch.platform.customer.enums.CustomerErrorCode;
import com.bank.branch.platform.governance.api.NotifyApi;
import com.bank.branch.platform.governance.api.dto.NotificationCmd;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 基于目标 MARKETING_* 表的跨机构营销服务。
 *
 * <p>默认规则只引用营销客户主档和业绩关系快照，不直连 CCRM。数据库配置了未知
 * 规则时一律失败关闭；审批状态 CAS 成功后才生成一次兼容现有 TouchTaskService
 * 的触达任务。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketingCrossOrgMarketingService {

    public static final String APPLICANT_NOT_MAIN = "APPLICANT_NOT_MAIN_MANAGER";
    public static final String MAIN_ORG_DIFFERENT = "MAIN_ORG_DIFFERENT";
    public static final String APPLICANT_NO_PERFORMANCE = "APPLICANT_NO_PERFORMANCE";
    public static final String APPLICANT_ORG_NO_PERFORMANCE = "APPLICANT_ORG_NO_PERFORMANCE";

    private static final String IN_APPROVAL = "IN_APPROVAL";
    private static final String APPROVED = "APPROVED";
    private static final String REJECTED = "REJECTED";

    private final MarketingCrossOrgApplyMapper applyMapper;
    private final MarketingCrossOrgRuleMapper ruleMapper;
    private final MarketingCustomerInfoMapper customerMapper;
    private final MarketingCustomerPerformanceRelSnapshotMapper performanceMapper;
    private final MarketingTouchTaskMapper touchTaskMapper;
    private final UserApi userApi;
    private final OrgApi orgApi;
    private final NotifyApi notifyApi;

    /** 默认四项规则，数据源只使用本模块营销表。 */
    public static List<MarketingCrossOrgRule> defaultRules() {
        return List.of(
                rule(APPLICANT_NOT_MAIN, "申请人不是客户主办", "MARKETING_CUSTOMER_INFO",
                        "申请人是当前主办客户经理", 10),
                rule(MAIN_ORG_DIFFERENT, "申请机构与主办机构不同", "MARKETING_CUSTOMER_INFO",
                        "申请机构与主办机构相同", 20),
                rule(APPLICANT_NO_PERFORMANCE, "申请人无业绩归属",
                        "MARKETING_CUSTOMER_PERFORMANCE_REL_SNAPSHOT", "申请人已有业绩归属", 30),
                rule(APPLICANT_ORG_NO_PERFORMANCE, "申请机构无业绩归属",
                        "MARKETING_CUSTOMER_PERFORMANCE_REL_SNAPSHOT", "申请机构已有业绩归属", 40));
    }

    private static MarketingCrossOrgRule rule(String code, String name, String source,
                                                String message, int sortNo) {
        MarketingCrossOrgRule rule = new MarketingCrossOrgRule();
        rule.setRuleCode(code);
        rule.setRuleName(name);
        rule.setEnabled(1);
        rule.setDataSource(source);
        rule.setFailureMessage(message);
        rule.setSortNo(sortNo);
        return rule;
    }

    /** 执行当前启用的规则并返回安全的校验摘要。 */
    public CrossOrgValidationRespDTO validate(Long custId, String applicantEmpId, String applicantOrgId) {
        MarketingCustomerInfo customer = requireCustomer(custId);
        List<MarketingCustomerPerformanceRelSnapshot> relations = performanceMapper.selectActiveByCustId(custId);
        if (relations == null) relations = List.of();
        List<MarketingCrossOrgRule> rules = ruleMapper.selectEnabledRules();
        if (rules == null || rules.isEmpty()) rules = defaultRules();

        List<CrossOrgValidationRespDTO.CheckItem> checks = new ArrayList<>();
        for (MarketingCrossOrgRule rule : rules) {
            boolean passed = evaluate(rule, customer, relations, applicantEmpId, applicantOrgId);
            checks.add(new CrossOrgValidationRespDTO.CheckItem(rule.getRuleCode(), rule.getRuleName(), passed,
                    passed ? "校验通过" : safeFailureMessage(rule), rule.getDataSource()));
        }
        CrossOrgValidationRespDTO result = new CrossOrgValidationRespDTO();
        result.setValid(!checks.isEmpty() && checks.stream().allMatch(CrossOrgValidationRespDTO.CheckItem::isPassed));
        result.setCustId(String.valueOf(customer.getId()));
        result.setCustNo(customer.getCustNo());
        result.setCustName(customer.getCustName());
        result.setMainManagerId(customer.getMainManagerId());
        result.setMainOrgId(customer.getMainOrgId());
        result.setSnapshotTime(LocalDateTime.now());
        result.setHasApplicantPerformance(hasPerformanceForEmp(relations, applicantEmpId));
        result.setHasApplicantOrgPerformance(hasPerformanceForOrg(relations, applicantOrgId));
        result.setChecks(checks);
        fillOwnerNames(result);
        return result;
    }

    private boolean evaluate(MarketingCrossOrgRule rule, MarketingCustomerInfo customer,
                             List<MarketingCustomerPerformanceRelSnapshot> relations,
                             String applicantEmpId, String applicantOrgId) {
        if (rule == null || !Objects.equals(rule.getEnabled(), 1)
                || !StringUtils.hasText(rule.getRuleCode())
                || !supportedDataSource(rule)) {
            return false;
        }
        return switch (rule.getRuleCode()) {
            case APPLICANT_NOT_MAIN -> !Objects.equals(customer.getMainManagerId(), applicantEmpId);
            case MAIN_ORG_DIFFERENT -> StringUtils.hasText(customer.getMainOrgId())
                    && StringUtils.hasText(applicantOrgId)
                    && !Objects.equals(customer.getMainOrgId(), applicantOrgId);
            case APPLICANT_NO_PERFORMANCE -> !hasPerformanceForEmp(relations, applicantEmpId);
            case APPLICANT_ORG_NO_PERFORMANCE -> !hasPerformanceForOrg(relations, applicantOrgId);
            default -> false;
        };
    }

    /** 规则代码与目标快照表必须成对出现，避免配置误把校验路由回 CCRM 等未知数据源。 */
    private boolean supportedDataSource(MarketingCrossOrgRule rule) {
        String expected = switch (rule.getRuleCode()) {
            case APPLICANT_NOT_MAIN, MAIN_ORG_DIFFERENT -> "MARKETING_CUSTOMER_INFO";
            case APPLICANT_NO_PERFORMANCE, APPLICANT_ORG_NO_PERFORMANCE ->
                    "MARKETING_CUSTOMER_PERFORMANCE_REL_SNAPSHOT";
            default -> null;
        };
        return expected != null && expected.equalsIgnoreCase(rule.getDataSource());
    }

    /** 新建申请，保存四项校验快照并进入 IN_APPROVAL。 */
    @Transactional
    public MarketingCrossOrgApply create(Long custId, String reason,
                                         String applicantEmpId, String applicantOrgId) {
        if (!StringUtils.hasText(reason)) {
            throw new BizException(CustomerErrorCode.CROSS_ORG_REASON_REQUIRED.getCode(),
                    CustomerErrorCode.CROSS_ORG_REASON_REQUIRED.getMessage());
        }
        CrossOrgValidationRespDTO validation = validate(custId, applicantEmpId, applicantOrgId);
        if (!validation.isValid()) {
            throw new BizException(CustomerErrorCode.CROSS_ORG_VALIDATION_FAILED.getCode(),
                    CustomerErrorCode.CROSS_ORG_VALIDATION_FAILED.getMessage());
        }
        if (applyMapper.countActiveByCustomerAndApplicant(custId, applicantEmpId) > 0) {
            throw new BizException(CustomerErrorCode.CROSS_ORG_ACTIVE_DUPLICATE.getCode(),
                    CustomerErrorCode.CROSS_ORG_ACTIVE_DUPLICATE.getMessage());
        }
        LocalDateTime now = LocalDateTime.now();
        MarketingCrossOrgApply apply = new MarketingCrossOrgApply();
        apply.setApplyNo("CROSS" + now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"))
                + Integer.toHexString(System.identityHashCode(apply)).toUpperCase());
        apply.setCustId(custId);
        apply.setApplicantEmpId(applicantEmpId);
        apply.setApplicantOrgId(applicantOrgId);
        apply.setMainManagerIdSnapshot(validation.getMainManagerId());
        apply.setMainOrgIdSnapshot(validation.getMainOrgId());
        apply.setApplicantNotMainCheck(check(validation, APPLICANT_NOT_MAIN));
        apply.setMainOrgDifferentCheck(check(validation, MAIN_ORG_DIFFERENT));
        apply.setApplicantNoPerformanceCheck(check(validation, APPLICANT_NO_PERFORMANCE));
        apply.setApplicantOrgNoPerformanceCheck(check(validation, APPLICANT_ORG_NO_PERFORMANCE));
        apply.setCheckSnapshotTime(validation.getSnapshotTime());
        apply.setApplyReason(reason.trim());
        apply.setStatus(IN_APPROVAL);
        apply.setBusinessKey("CROSS_ORG_MARKETING:" + apply.getApplyNo());
        apply.setCreatedBy(applicantEmpId);
        apply.setCreatedTime(now);
        apply.setUpdatedBy(applicantEmpId);
        apply.setUpdatedTime(now);
        try {
            applyMapper.insert(apply);
        } catch (DuplicateKeyException ex) {
            throw new BizException(CustomerErrorCode.CROSS_ORG_ACTIVE_DUPLICATE.getCode(),
                    CustomerErrorCode.CROSS_ORG_ACTIVE_DUPLICATE.getMessage());
        }
        notifyOriginalOwner(apply, validation.getCustName());
        return apply;
    }

    /** 审批通过：只有 CAS 成功的请求才创建触达任务；重复审批不会重复建任务。 */
    @Transactional
    public void approve(Long id, String reviewerEmpId, boolean reviewer) {
        requireReviewer(reviewer);
        MarketingCrossOrgApply existing = requireApply(id);
        if (APPROVED.equals(existing.getStatus())) {
            ensureGeneratedTouchTask(existing);
            return;
        }
        if (!IN_APPROVAL.equals(existing.getStatus())) throw statusConflict();
        MarketingCrossOrgApply update = reviewUpdate(existing, reviewerEmpId);
        if (applyMapper.updateInApprovalToApproved(update) != 1) {
            MarketingCrossOrgApply latest = applyMapper.selectById(id);
            if (latest != null && APPROVED.equals(latest.getStatus())) {
                ensureGeneratedTouchTask(latest);
                return;
            }
            throw statusConflict();
        }
        ensureGeneratedTouchTask(existing);
    }

    /** 审批退回并记录原因。 */
    @Transactional
    public void reject(Long id, String reason, String reviewerEmpId, boolean reviewer) {
        requireReviewer(reviewer);
        if (!StringUtils.hasText(reason)) {
            throw new BizException(CustomerErrorCode.CROSS_ORG_REASON_REQUIRED.getCode(), "退回原因不能为空");
        }
        MarketingCrossOrgApply existing = requireApply(id);
        if (!IN_APPROVAL.equals(existing.getStatus())) throw statusConflict();
        MarketingCrossOrgApply update = reviewUpdate(existing, reviewerEmpId);
        update.setRejectReason(reason.trim());
        if (applyMapper.updateInApprovalToRejected(update) != 1) throw statusConflict();
    }

    /** 普通用户只能看本人，审核人可查看全量；canReview 只对 IN_APPROVAL 返回 true。 */
    public List<CrossOrgApplyRespDTO> list(String status, String currentEmpId, boolean reviewer) {
        LambdaQueryWrapper<MarketingCrossOrgApply> wrapper = new LambdaQueryWrapper<MarketingCrossOrgApply>()
                .eq(StringUtils.hasText(status), MarketingCrossOrgApply::getStatus, status)
                .eq(!reviewer, MarketingCrossOrgApply::getApplicantEmpId, currentEmpId)
                .orderByDesc(MarketingCrossOrgApply::getCreatedTime);
        List<MarketingCrossOrgApply> applies = applyMapper.selectList(wrapper);
        if (applies == null) applies = List.of();
        return applies.stream().filter(Objects::nonNull)
                .map(item -> toResp(item, reviewer)).toList();
    }

    /** 按权限读取申请详情。 */
    public CrossOrgApplyRespDTO get(Long id, String currentEmpId, boolean reviewer) {
        MarketingCrossOrgApply apply = requireApply(id);
        if (!reviewer && !Objects.equals(currentEmpId, apply.getApplicantEmpId())) {
            throw new BizException(CustomerErrorCode.CROSS_ORG_REVIEW_FORBIDDEN.getCode(),
                    CustomerErrorCode.CROSS_ORG_REVIEW_FORBIDDEN.getMessage());
        }
        return toResp(apply, reviewer);
    }

    private CrossOrgApplyRespDTO toResp(MarketingCrossOrgApply apply, boolean reviewer) {
        CrossOrgApplyRespDTO dto = new CrossOrgApplyRespDTO();
        dto.setId(apply.getId() == null ? null : String.valueOf(apply.getId()));
        dto.setApplyNo(apply.getApplyNo());
        dto.setCustId(apply.getCustId() == null ? null : String.valueOf(apply.getCustId()));
        dto.setApplicantEmpId(apply.getApplicantEmpId());
        dto.setApplicantOrgId(apply.getApplicantOrgId());
        dto.setMainManagerId(apply.getMainManagerIdSnapshot());
        dto.setMainOrgId(apply.getMainOrgIdSnapshot());
        dto.setApplicantNotMainCheck(apply.getApplicantNotMainCheck());
        dto.setMainOrgDifferentCheck(apply.getMainOrgDifferentCheck());
        dto.setApplicantNoPerformanceCheck(apply.getApplicantNoPerformanceCheck());
        dto.setApplicantOrgNoPerformanceCheck(apply.getApplicantOrgNoPerformanceCheck());
        dto.setApplyReason(apply.getApplyReason());
        dto.setStatus(apply.getStatus());
        dto.setGeneratedTouchTaskId(apply.getGeneratedTouchTaskId() == null
                ? null : String.valueOf(apply.getGeneratedTouchTaskId()));
        dto.setReviewedBy(apply.getReviewedBy());
        dto.setReviewedTime(apply.getReviewedTime());
        dto.setRejectReason(apply.getRejectReason());
        dto.setCreatedTime(apply.getCreatedTime());
        dto.setCanReview(reviewer && IN_APPROVAL.equals(apply.getStatus()));
        MarketingCustomerInfo customer = apply.getCustId() == null ? null
                : customerMapper.selectActiveById(apply.getCustId());
        if (customer != null) {
            dto.setCustNo(customer.getCustNo());
            dto.setCustName(customer.getCustName());
        }
        fillApplicantName(dto);
        fillOwnerNames(dto);
        return dto;
    }

    private MarketingCrossOrgApply reviewUpdate(MarketingCrossOrgApply existing, String reviewerEmpId) {
        MarketingCrossOrgApply update = new MarketingCrossOrgApply();
        update.setId(existing.getId());
        update.setReviewedBy(reviewerEmpId);
        update.setReviewedTime(LocalDateTime.now());
        update.setUpdatedBy(reviewerEmpId);
        update.setUpdatedTime(LocalDateTime.now());
        return update;
    }

    /**
     * 申请与目标触达任务均使用 BIGINT 主键。按来源业务键先查后建，配合 task_no
     * 唯一键和同一事务，保证审批重试不会写出 UUID 到 BIGINT 列或重复生成任务。
     */
    private void ensureGeneratedTouchTask(MarketingCrossOrgApply apply) {
        if (apply.getGeneratedTouchTaskId() != null) return;
        MarketingTouchTask task = touchTaskMapper.selectBySource("CROSS_ORG", apply.getId());
        if (task == null) {
            LocalDateTime now = LocalDateTime.now();
            task = new MarketingTouchTask();
            task.setTaskNo("MKT-CROSS-" + apply.getApplyNo());
            task.setCustId(apply.getCustId());
            task.setSourceType("CROSS_ORG");
            task.setSourceBizId(apply.getId());
            task.setOrgId(apply.getApplicantOrgId());
            task.setAssigneeEmpId(apply.getApplicantEmpId());
            task.setTaskType("FIRST_TOUCH");
            task.setTaskStatus("PENDING");
            task.setSlaStatus("BLUE");
            task.setCreatedBy(apply.getApplicantEmpId());
            task.setCreatedTime(now);
            task.setUpdatedBy(apply.getApplicantEmpId());
            task.setUpdatedTime(now);
            task.setLockVersion(0);
            try {
                touchTaskMapper.insert(task);
            } catch (DuplicateKeyException duplicate) {
                task = touchTaskMapper.selectBySource("CROSS_ORG", apply.getId());
            }
        }
        if (task == null || task.getId() == null) {
            throw new BizException(CustomerErrorCode.INTERNAL_ERROR.getCode(), "跨机构营销触达任务创建失败");
        }
        applyMapper.updateGeneratedTouchTask(apply.getId(), task.getId());
    }

    private MarketingCrossOrgApply requireApply(Long id) {
        MarketingCrossOrgApply result = applyMapper.selectById(id);
        if (result == null) {
            throw new BizException(CustomerErrorCode.CROSS_ORG_APPLY_NOT_FOUND.getCode(),
                    CustomerErrorCode.CROSS_ORG_APPLY_NOT_FOUND.getMessage());
        }
        return result;
    }

    private MarketingCustomerInfo requireCustomer(Long id) {
        MarketingCustomerInfo customer = customerMapper.selectActiveById(id);
        if (customer == null) {
            throw new BizException(CustomerErrorCode.CUSTOMER_NOT_FOUND.getCode(),
                    CustomerErrorCode.CUSTOMER_NOT_FOUND.getMessage());
        }
        return customer;
    }

    private boolean hasPerformanceForEmp(List<MarketingCustomerPerformanceRelSnapshot> relations, String empId) {
        return StringUtils.hasText(empId) && relations.stream().anyMatch(item ->
                Objects.equals(empId, item.getRelatedEmpId())
                        || ("EMP".equals(item.getSubjectType()) && Objects.equals(empId, item.getSubjectId())));
    }

    private boolean hasPerformanceForOrg(List<MarketingCustomerPerformanceRelSnapshot> relations, String orgId) {
        return StringUtils.hasText(orgId) && relations.stream().anyMatch(item ->
                Objects.equals(orgId, item.getRelatedOrgId())
                        || ("ORG".equals(item.getSubjectType()) && Objects.equals(orgId, item.getSubjectId())));
    }

    private int check(CrossOrgValidationRespDTO validation, String code) {
        return validation.getChecks().stream().filter(item -> Objects.equals(code, item.getRuleCode()))
                .findFirst().map(item -> item.isPassed() ? 1 : 0).orElse(0);
    }

    private String safeFailureMessage(MarketingCrossOrgRule rule) {
        return StringUtils.hasText(rule.getFailureMessage()) ? rule.getFailureMessage() : "规则校验未通过";
    }

    private void fillApplicantName(CrossOrgApplyRespDTO dto) {
        if (userApi == null || !StringUtils.hasText(dto.getApplicantEmpId())) return;
        UserDTO user = userApi.getUserByEmpId(dto.getApplicantEmpId());
        if (user != null) {
            dto.setApplicantName(user.getDisplayName());
            if (!StringUtils.hasText(dto.getApplicantOrgName())) dto.setApplicantOrgName(user.getMainOrgName());
        }
    }

    private void fillOwnerNames(CrossOrgValidationRespDTO dto) {
        if (userApi != null && StringUtils.hasText(dto.getMainManagerId())) {
            UserDTO user = userApi.getUserByEmpId(dto.getMainManagerId());
            if (user != null) dto.setMainManagerName(user.getDisplayName());
        }
        if (orgApi != null && StringUtils.hasText(dto.getMainOrgId())) {
            List<OrgDTO> orgs = orgApi.getOrgsByCodes(List.of(dto.getMainOrgId()));
            if (orgs != null && !orgs.isEmpty()) dto.setMainOrgName(orgs.get(0).getOrgName());
        }
    }

    private void fillOwnerNames(CrossOrgApplyRespDTO dto) {
        if (userApi != null && StringUtils.hasText(dto.getMainManagerId())) {
            UserDTO user = userApi.getUserByEmpId(dto.getMainManagerId());
            if (user != null) dto.setMainManagerName(user.getDisplayName());
        }
        if (orgApi != null && StringUtils.hasText(dto.getMainOrgId())) {
            List<OrgDTO> orgs = orgApi.getOrgsByCodes(List.of(dto.getMainOrgId()));
            if (orgs != null && !orgs.isEmpty()) dto.setMainOrgName(orgs.get(0).getOrgName());
        }
    }

    private void notifyOriginalOwner(MarketingCrossOrgApply apply, String custName) {
        if (notifyApi == null || !StringUtils.hasText(apply.getMainManagerIdSnapshot())) return;
        try {
            notifyApi.sendNotification(NotificationCmd.builder()
                    .targetEmpId(apply.getMainManagerIdSnapshot())
                    .title("客户跨机构营销申请通知")
                    .content("客户“" + custName + "”已由其他机构发起营销申请，请关注后续审批。")
                    .notifyType("WORKFLOW").bizType("CROSS_ORG_MARKETING")
                    .bizId(String.valueOf(apply.getId())).linkUrl("/customers/cross-org").build());
        } catch (Exception ex) {
            log.warn("[MarketingCrossOrgMarketingService] notify owner failed, applyNo={}, error={}",
                    apply.getApplyNo(), ex.getMessage());
        }
    }

    private void requireReviewer(boolean reviewer) {
        if (!reviewer) {
            throw new BizException(CustomerErrorCode.CROSS_ORG_REVIEW_FORBIDDEN.getCode(),
                    CustomerErrorCode.CROSS_ORG_REVIEW_FORBIDDEN.getMessage());
        }
    }

    private BizException statusConflict() {
        return new BizException(CustomerErrorCode.CROSS_ORG_STATUS_CONFLICT.getCode(),
                CustomerErrorCode.CROSS_ORG_STATUS_CONFLICT.getMessage());
    }
}
