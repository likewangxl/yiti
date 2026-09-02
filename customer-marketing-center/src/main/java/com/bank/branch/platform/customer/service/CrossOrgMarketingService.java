package com.bank.branch.platform.customer.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.UserDTO;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.customer.dto.resp.CrossOrgApplyRespDTO;
import com.bank.branch.platform.customer.dto.resp.CrossOrgValidationRespDTO;
import com.bank.branch.platform.customer.entity.CrossOrgMarketingApply;
import com.bank.branch.platform.customer.entity.CrossOrgMarketingRule;
import com.bank.branch.platform.customer.entity.CustMaster;
import com.bank.branch.platform.customer.entity.CustPerformanceRelationSnapshot;
import com.bank.branch.platform.customer.entity.TouchTask;
import com.bank.branch.platform.customer.enums.CustomerErrorCode;
import com.bank.branch.platform.customer.mapper.CrossOrgMarketingApplyMapper;
import com.bank.branch.platform.customer.mapper.CrossOrgMarketingRuleMapper;
import com.bank.branch.platform.customer.mapper.CustMasterMapper;
import com.bank.branch.platform.customer.mapper.CustPerformanceRelationSnapshotMapper;
import com.bank.branch.platform.governance.api.NotifyApi;
import com.bank.branch.platform.governance.api.dto.NotificationCmd;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** 跨机构客户营销申请服务。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CrossOrgMarketingService {

    public static final String APPLICANT_NOT_MAIN = "APPLICANT_NOT_MAIN_MANAGER";
    public static final String MAIN_ORG_DIFFERENT = "MAIN_ORG_DIFFERENT";
    public static final String APPLICANT_NO_PERFORMANCE = "APPLICANT_NO_PERFORMANCE";
    public static final String APPLICANT_ORG_NO_PERFORMANCE = "APPLICANT_ORG_NO_PERFORMANCE";

    private final CrossOrgMarketingApplyMapper applyMapper;
    private final CrossOrgMarketingRuleMapper ruleMapper;
    private final CustMasterMapper masterMapper;
    private final CustPerformanceRelationSnapshotMapper performanceMapper;
    private final TouchTaskService touchTaskService;
    private final UserApi userApi;
    private final NotifyApi notifyApi;

    /**
     * 新 MARKETING_* 表服务。保留旧构造器和旧实体作为兼容入口，Spring 运行时优先
     * 将数字 ID 的请求路由到目标服务，避免继续向旧跨机构表写入新业务数据。
     */
    @Autowired(required = false)
    private MarketingCrossOrgMarketingService marketingService;

    /** 返回内置的四条冻结规则；数据库无配置时作为安全默认值。 */
    public static List<CrossOrgMarketingRule> defaultRules() {
        return List.of(
                rule(APPLICANT_NOT_MAIN, "申请人不是客户主办", "MARKETING_CUSTOMER_INFO", "申请人是当前主办客户经理", 10),
                rule(MAIN_ORG_DIFFERENT, "申请机构与主办机构不同", "MARKETING_CUSTOMER_INFO", "申请机构与主办机构相同", 20),
                rule(APPLICANT_NO_PERFORMANCE, "申请人无业绩归属",
                        "MARKETING_CUSTOMER_PERFORMANCE_REL_SNAPSHOT", "申请人已有业绩归属", 30),
                rule(APPLICANT_ORG_NO_PERFORMANCE, "申请机构无业绩归属",
                        "MARKETING_CUSTOMER_PERFORMANCE_REL_SNAPSHOT", "申请机构已有业绩归属", 40));
    }

    private static CrossOrgMarketingRule rule(String code, String name, String source, String message, int sort) {
        CrossOrgMarketingRule rule = new CrossOrgMarketingRule();
        rule.setId(code);
        rule.setRuleCode(code);
        rule.setRuleName(name);
        rule.setEnabled(1);
        rule.setDataSource(source);
        rule.setFailureMessage(message);
        rule.setSortNo(sort);
        return rule;
    }

    /** 按数据库启用规则逐条执行并返回前端展示结果。 */
    public CrossOrgValidationRespDTO validate(String custId, String applicantEmpId, String applicantOrgId) {
        Long marketingCustId = parseLong(custId);
        if (marketingService != null && marketingCustId != null) {
            return marketingService.validate(marketingCustId, applicantEmpId, applicantOrgId);
        }
        CustMaster customer = requireCustomer(custId);
        List<CustPerformanceRelationSnapshot> relations = performanceMapper.selectActiveByCustId(customer.getId());
        if (relations == null) {
            relations = List.of();
        }
        List<CrossOrgMarketingRule> rules = ruleMapper.selectEnabledRules();
        if (rules == null || rules.isEmpty()) {
            rules = defaultRules();
        }

        List<CrossOrgValidationRespDTO.CheckItem> checks = new ArrayList<>();
        for (CrossOrgMarketingRule rule : rules) {
            boolean passed = evaluate(rule.getRuleCode(), customer, relations, applicantEmpId, applicantOrgId);
            checks.add(new CrossOrgValidationRespDTO.CheckItem(rule.getRuleCode(), rule.getRuleName(), passed,
                    passed ? "校验通过" : rule.getFailureMessage(), rule.getDataSource()));
        }
        CrossOrgValidationRespDTO result = new CrossOrgValidationRespDTO();
        result.setValid(!checks.isEmpty() && checks.stream().allMatch(CrossOrgValidationRespDTO.CheckItem::isPassed));
        result.setCustId(customer.getId());
        result.setCustNo(customer.getCustNo());
        result.setCustName(customer.getCustName());
        result.setMainManagerId(customer.getMainManagerId());
        result.setMainOrgId(customer.getMainOrgId());
        result.setSnapshotTime(LocalDateTime.now());
        result.setChecks(checks);
        return result;
    }

    private boolean evaluate(String code, CustMaster customer, List<CustPerformanceRelationSnapshot> relations,
                             String applicantEmpId, String applicantOrgId) {
        return switch (code) {
            case APPLICANT_NOT_MAIN -> !java.util.Objects.equals(customer.getMainManagerId(), applicantEmpId);
            case MAIN_ORG_DIFFERENT -> StringUtils.hasText(customer.getMainOrgId())
                    && !java.util.Objects.equals(customer.getMainOrgId(), applicantOrgId);
            case APPLICANT_NO_PERFORMANCE -> relations.stream().noneMatch(r ->
                    java.util.Objects.equals(applicantEmpId, r.getRelatedEmpId())
                            || ("EMP".equals(r.getSubjectType()) && java.util.Objects.equals(applicantEmpId, r.getSubjectId())));
            case APPLICANT_ORG_NO_PERFORMANCE -> relations.stream().noneMatch(r ->
                    java.util.Objects.equals(applicantOrgId, r.getRelatedOrgId())
                            || ("ORG".equals(r.getSubjectType()) && java.util.Objects.equals(applicantOrgId, r.getSubjectId())));
            default -> false; // 未识别的启用规则 fail-close。
        };
    }

    /** 新建申请并通知原主办及原主办机构。 */
    @Transactional
    public CrossOrgMarketingApply create(String custId, String reason, String applicantEmpId, String applicantOrgId) {
        Long marketingCustId = parseLong(custId);
        if (marketingService != null && marketingCustId != null) {
            return toLegacyApply(marketingService.create(marketingCustId, reason, applicantEmpId, applicantOrgId));
        }
        if (!StringUtils.hasText(reason)) {
            throw new BizException(CustomerErrorCode.CROSS_ORG_REASON_REQUIRED.getCode(),
                    CustomerErrorCode.CROSS_ORG_REASON_REQUIRED.getMessage());
        }
        CrossOrgValidationRespDTO validation = validate(custId, applicantEmpId, applicantOrgId);
        String actualCustId = validation.getCustId();
        Long duplicate = applyMapper.selectCount(new LambdaQueryWrapper<CrossOrgMarketingApply>()
                .eq(CrossOrgMarketingApply::getCustId, actualCustId)
                .eq(CrossOrgMarketingApply::getApplicantEmpId, applicantEmpId)
                .in(CrossOrgMarketingApply::getStatus, List.of("PENDING", "APPROVED")));
        if (duplicate != null && duplicate > 0) {
            throw new BizException(CustomerErrorCode.CROSS_ORG_ACTIVE_DUPLICATE.getCode(),
                    CustomerErrorCode.CROSS_ORG_ACTIVE_DUPLICATE.getMessage());
        }
        if (!validation.isValid()) {
            throw new BizException(CustomerErrorCode.CROSS_ORG_VALIDATION_FAILED.getCode(),
                    CustomerErrorCode.CROSS_ORG_VALIDATION_FAILED.getMessage());
        }
        LocalDateTime now = LocalDateTime.now();
        String id = UUID.randomUUID().toString().replace("-", "");
        CrossOrgMarketingApply entity = new CrossOrgMarketingApply();
        entity.setId(id);
        entity.setApplyNo("CROSS" + now.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + id.substring(0, 4).toUpperCase());
        entity.setCustId(actualCustId);
        entity.setCustNo(validation.getCustNo());
        entity.setApplicantEmpId(applicantEmpId);
        entity.setApplicantOrgId(applicantOrgId);
        entity.setMainManagerId(validation.getMainManagerId());
        entity.setMainOrgId(validation.getMainOrgId());
        entity.setApplicantNotMainCheck(check(validation, APPLICANT_NOT_MAIN));
        entity.setMainOrgDifferentCheck(check(validation, MAIN_ORG_DIFFERENT));
        entity.setApplicantNoPerformanceCheck(check(validation, APPLICANT_NO_PERFORMANCE));
        entity.setApplicantOrgNoPerformanceCheck(check(validation, APPLICANT_ORG_NO_PERFORMANCE));
        entity.setCheckSnapshotTime(validation.getSnapshotTime());
        entity.setApplyReason(reason.trim());
        entity.setStatus("PENDING");
        entity.setBusinessKey("CROSS_ORG_MARKETING:" + id);
        entity.setCreatedBy(applicantEmpId);
        entity.setCreatedTime(now);
        entity.setUpdatedBy(applicantEmpId);
        entity.setUpdatedTime(now);
        applyMapper.insert(entity);
        notifyOriginalOwners(entity, validation.getCustName());
        return entity;
    }

    private int check(CrossOrgValidationRespDTO result, String code) {
        return result.getChecks().stream().filter(item -> code.equals(item.getRuleCode()))
                .findFirst().map(item -> item.isPassed() ? 1 : 0).orElse(0);
    }

    /** 审核通过申请；CAS 成功后只创建一次申请人触达任务。 */
    @Transactional
    public void approve(String id, String reviewerEmpId) {
        Long marketingApplyId = parseLong(id);
        if (marketingService != null && marketingApplyId != null) {
            marketingService.approve(marketingApplyId, reviewerEmpId, true);
            return;
        }
        CrossOrgMarketingApply existing = requireApply(id);
        if (!"PENDING".equals(existing.getStatus())) {
            throw statusConflict();
        }
        CrossOrgMarketingApply update = reviewUpdate(id, reviewerEmpId);
        if (applyMapper.updatePendingToApproved(update) != 1) {
            throw statusConflict();
        }
        TouchTask task = touchTaskService.createFirstTouchTask(existing.getCustId(), existing.getApplicantOrgId(),
                existing.getApplicantEmpId(), null);
        applyMapper.updateGeneratedTask(id, String.valueOf(task.getId()));
        notifyApplicant(existing, "跨机构营销申请已通过", "审批通过，系统已生成触达任务。", reviewerEmpId);
    }

    /** 审核退回申请并记录原因。 */
    @Transactional
    public void reject(String id, String reason, String reviewerEmpId) {
        Long marketingApplyId = parseLong(id);
        if (marketingService != null && marketingApplyId != null) {
            marketingService.reject(marketingApplyId, reason, reviewerEmpId, true);
            return;
        }
        CrossOrgMarketingApply existing = requireApply(id);
        if (!"PENDING".equals(existing.getStatus())) {
            throw statusConflict();
        }
        if (!StringUtils.hasText(reason)) {
            throw new BizException(CustomerErrorCode.CROSS_ORG_REASON_REQUIRED.getCode(), "退回原因不能为空");
        }
        CrossOrgMarketingApply update = reviewUpdate(id, reviewerEmpId);
        update.setRejectReason(reason.trim());
        if (applyMapper.updatePendingToRejected(update) != 1) {
            throw statusConflict();
        }
        notifyApplicant(existing, "跨机构营销申请已退回", "退回原因：" + reason.trim(), reviewerEmpId);
    }

    private CrossOrgMarketingApply reviewUpdate(String id, String reviewerEmpId) {
        CrossOrgMarketingApply update = new CrossOrgMarketingApply();
        update.setId(id);
        update.setReviewedBy(reviewerEmpId);
        update.setReviewedTime(LocalDateTime.now());
        update.setUpdatedBy(reviewerEmpId);
        update.setUpdatedTime(LocalDateTime.now());
        return update;
    }

    /** 查询申请；审核角色可见全量，普通员工只见本人。 */
    public List<CrossOrgApplyRespDTO> list(String status, String currentEmpId, boolean reviewer) {
        if (marketingService != null) {
            return marketingService.list(status, currentEmpId, reviewer);
        }
        LambdaQueryWrapper<CrossOrgMarketingApply> query = new LambdaQueryWrapper<CrossOrgMarketingApply>()
                .eq(StringUtils.hasText(status), CrossOrgMarketingApply::getStatus, status)
                .eq(!reviewer, CrossOrgMarketingApply::getApplicantEmpId, currentEmpId)
                .orderByDesc(CrossOrgMarketingApply::getCreatedTime);
        return applyMapper.selectList(query).stream().map(item -> toResp(item, reviewer)).toList();
    }

    /** 按权限查询申请详情。 */
    public CrossOrgApplyRespDTO get(String id, String currentEmpId, boolean reviewer) {
        Long marketingApplyId = parseLong(id);
        if (marketingService != null && marketingApplyId != null) {
            return marketingService.get(marketingApplyId, currentEmpId, reviewer);
        }
        CrossOrgMarketingApply entity = requireApply(id);
        if (!reviewer && !java.util.Objects.equals(currentEmpId, entity.getApplicantEmpId())) {
            throw new BizException(CustomerErrorCode.CROSS_ORG_REVIEW_FORBIDDEN.getCode(),
                    CustomerErrorCode.CROSS_ORG_REVIEW_FORBIDDEN.getMessage());
        }
        return toResp(entity, reviewer);
    }

    private CrossOrgApplyRespDTO toResp(CrossOrgMarketingApply entity) {
        return toResp(entity, false);
    }

    private CrossOrgApplyRespDTO toResp(CrossOrgMarketingApply entity, boolean reviewer) {
        CrossOrgApplyRespDTO dto = new CrossOrgApplyRespDTO();
        org.springframework.beans.BeanUtils.copyProperties(entity, dto);
        CustMaster customer = masterMapper.selectById(entity.getCustId());
        if (customer != null) {
            dto.setCustName(customer.getCustName());
        }
        UserDTO applicant = userApi.getUserByEmpId(entity.getApplicantEmpId());
        if (applicant != null) {
            dto.setApplicantName(applicant.getDisplayName());
            dto.setApplicantOrgName(applicant.getMainOrgName());
        }
        dto.setCanReview(reviewer && "IN_APPROVAL".equals(entity.getStatus()));
        return dto;
    }

    private CrossOrgMarketingApply toLegacyApply(
            com.bank.branch.platform.customer.entity.marketing.MarketingCrossOrgApply source) {
        CrossOrgMarketingApply target = new CrossOrgMarketingApply();
        if (source == null) return target;
        BeanUtils.copyProperties(source, target);
        target.setId(source.getId() == null ? null : String.valueOf(source.getId()));
        target.setCustId(source.getCustId() == null ? null : String.valueOf(source.getCustId()));
        target.setGeneratedTouchTaskId(source.getGeneratedTouchTaskId() == null
                ? null : String.valueOf(source.getGeneratedTouchTaskId()));
        return target;
    }

    private void notifyOriginalOwners(CrossOrgMarketingApply entity, String custName) {
        Set<String> recipients = new LinkedHashSet<>();
        if (StringUtils.hasText(entity.getMainManagerId())) {
            recipients.add(entity.getMainManagerId());
        }
        if (StringUtils.hasText(entity.getMainOrgId())) {
            List<String> orgUsers = userApi.getEmpIdsByOrg(entity.getMainOrgId());
            if (orgUsers != null) recipients.addAll(orgUsers);
        }
        List<NotificationCmd> cmds = recipients.stream().map(empId -> NotificationCmd.builder()
                .targetEmpId(empId).title("客户跨机构营销申请通知")
                .content("客户“" + custName + "”已由其他机构发起营销申请，请关注后续审批。")
                .notifyType("WORKFLOW").bizType("CROSS_ORG_MARKETING").bizId(entity.getId())
                .linkUrl("/customers/cross-org").build()).toList();
        try {
            if (!cmds.isEmpty()) notifyApi.batchSendNotifications(cmds);
        } catch (Exception e) {
            log.warn("[CrossOrgMarketingService] 通知原主办机构失败 applyId={}, error={}", entity.getId(), e.getMessage());
        }
    }

    private void notifyApplicant(CrossOrgMarketingApply apply, String title, String content, String reviewer) {
        try {
            notifyApi.sendNotification(NotificationCmd.builder().targetEmpId(apply.getApplicantEmpId())
                    .title(title).content(content + "（审核人：" + reviewer + "）")
                    .notifyType("WORKFLOW").bizType("CROSS_ORG_MARKETING").bizId(apply.getId())
                    .linkUrl("/customers/cross-org").build());
        } catch (Exception e) {
            log.warn("[CrossOrgMarketingService] 通知申请人失败 applyId={}, error={}", apply.getId(), e.getMessage());
        }
    }

    private CustMaster requireCustomer(String custId) {
        CustMaster customer = masterMapper.selectById(custId);
        if (customer == null) {
            customer = masterMapper.selectByCustNo(custId);
        }
        if (customer == null) {
            throw new BizException(CustomerErrorCode.CUSTOMER_NOT_FOUND.getCode(),
                    CustomerErrorCode.CUSTOMER_NOT_FOUND.getMessage());
        }
        return customer;
    }

    private CrossOrgMarketingApply requireApply(String id) {
        CrossOrgMarketingApply entity = applyMapper.selectById(id);
        if (entity == null) {
            throw new BizException(CustomerErrorCode.CROSS_ORG_APPLY_NOT_FOUND.getCode(),
                    CustomerErrorCode.CROSS_ORG_APPLY_NOT_FOUND.getMessage());
        }
        return entity;
    }

    private BizException statusConflict() {
        return new BizException(CustomerErrorCode.CROSS_ORG_STATUS_CONFLICT.getCode(),
                CustomerErrorCode.CROSS_ORG_STATUS_CONFLICT.getMessage());
    }

    private Long parseLong(String value) {
        if (!StringUtils.hasText(value)) return null;
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
