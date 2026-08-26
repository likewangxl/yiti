package com.bank.branch.platform.customer.service.marketing;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.customer.dto.marketing.lead.LeadCreateRequest;
import com.bank.branch.platform.customer.dto.marketing.lead.LeadDetailResponse;
import com.bank.branch.platform.customer.dto.marketing.lead.LeadQuery;
import com.bank.branch.platform.customer.dto.marketing.lead.LeadSubmitResponse;
import com.bank.branch.platform.customer.dto.marketing.lead.LeadUpdateRequest;
import com.bank.branch.platform.customer.dto.marketing.lead.MarketingCustomerSnapshot;
import com.bank.branch.platform.customer.entity.marketing.MarketingCustomerInfo;
import com.bank.branch.platform.customer.entity.marketing.MarketingLeadInfo;
import com.bank.branch.platform.customer.mapper.marketing.MarketingCustomerInfoMapper;
import com.bank.branch.platform.customer.mapper.marketing.MarketingLeadInfoMapper;
import com.bank.branch.platform.workflow.api.WorkflowApi;
import com.bank.branch.platform.workflow.api.dto.StartProcessCmd;
import com.bank.branch.platform.workflow.api.dto.WorkflowLaunchResp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 页面三手工线索服务。
 *
 * <p>这里固定使用 MANUAL 来源，并把列表粒度保持为一条线索。所有状态改变都在
 * 本服务重读并加锁后完成，避免依赖前端的状态和客户主档快照。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketingLeadEntryService {

    static final String MANUAL = "MANUAL";
    static final String DRAFT = "DRAFT";
    static final String SUBMITTED = "SUBMITTED";
    static final String IN_APPROVAL = "IN_APPROVAL";
    static final String CANCELLED = "CANCELLED";

    private final MarketingLeadInfoMapper leadMapper;
    private final MarketingCustomerInfoMapper customerMapper;
    private final WorkflowApi workflowApi;

    /** 查询登录人自己的手工线索记录，不按客户聚合。 */
    public PageResult<MarketingLeadInfo> list(LeadQuery query, String operatorEmpId) {
        LeadQuery safeQuery = query == null ? new LeadQuery() : query;
        int pageNo = safePageNo(safeQuery.getPageNo());
        int pageSize = safePageSize(safeQuery.getPageSize());
        int offset = (pageNo - 1) * pageSize;
        List<MarketingLeadInfo> records = leadMapper.selectManualPage(
                safeQuery.getKeyword(), safeQuery.getStatus(), operatorEmpId, offset, pageSize);
        long total = leadMapper.countManualPage(
                safeQuery.getKeyword(), safeQuery.getStatus(), operatorEmpId);
        return PageResult.of(pageNo, pageSize, total, records == null ? List.of() : records);
    }

    /**
     * 创建手工线索草稿。命中在途线索时直接拒绝；命中已存在客户只反显当前客户并保存快照，
     * 不会在审批前修改客户主档。
     */
    @Transactional
    public MarketingLeadInfo createDraft(LeadCreateRequest request,
                                         String operatorEmpId,
                                         String operatorOrgId) {
        requireRequest(request);
        String creditCode = normalizeCreditCode(request.getUnifiedCreditCode());
        MarketingLeadInfo pending = leadMapper.selectActiveByCreditCode(creditCode);
        if (pending != null) {
            throw duplicatePending(pending);
        }

        MarketingCustomerInfo customer = findCustomer(creditCode);
        LocalDateTime now = LocalDateTime.now();
        MarketingLeadInfo lead = new MarketingLeadInfo();
        lead.setLeadNo(generateLeadNo(now));
        lead.setLeadSource(MANUAL);
        lead.setLeadStatus(DRAFT);
        lead.setRecordStatus("ACTIVE");
        lead.setPoolStatus("NOT_READY");
        lead.setActiveDedupKey(creditCode);
        lead.setEntryEmpId(operatorEmpId);
        lead.setEntryOrgId(operatorOrgId);
        lead.setEntryTime(now);
        lead.setCreatedBy(operatorEmpId);
        lead.setCreatedTime(now);
        lead.setUpdatedBy(operatorEmpId);
        lead.setUpdatedTime(now);
        lead.setLockVersion(0);
        copyRequest(request, lead);
        lead.setUnifiedCreditCode(creditCode);

        if (customer == null) {
            lead.setLeadType(defaultValue(request.getLeadType(), "NEW_ACCOUNT"));
            lead.setCustomerMatchStatus("NEW_CUSTOMER");
            lead.setDistributionMode(defaultValue(request.getDistributionMode(), "PUBLIC"));
        } else {
            lead.setCustId(customer.getId());
            lead.setCustNoSnapshot(customer.getCustNo());
            lead.setCustomerMatchStatus(customer.getIsAccountOpened() != null
                    && customer.getIsAccountOpened() == 1
                    ? "MATCHED_EXISTING_OPENED" : "MATCHED_EXISTING_UNOPENED");
            lead.setLeadType("EXISTING_MARKETING");
            lead.setBaseCustomerProfileVersion(customer.getProfileVersion());
            copyCustomerDefaults(customer, lead, request);
            if (StringUtils.hasText(customer.getMainManagerId())) {
                // 存量客户有主办权时只能进入主办人路径，不能借 PUBLIC/SCOPE 绕过主办权。
                lead.setDistributionMode("OWNER");
                lead.setMainManagerIdSnapshot(customer.getMainManagerId());
                lead.setMainOrgIdSnapshot(customer.getMainOrgId());
            } else {
                lead.setDistributionMode(defaultValue(request.getDistributionMode(), "OWNER"));
            }
        }

        try {
            leadMapper.insert(lead);
        } catch (DuplicateKeyException ex) {
            throw duplicatePending(lead);
        }
        return lead;
    }

    /** 兼容需要显式指定导入来源的内部装配路径；页面三手工入口不调用此重载。 */
    public MarketingLeadInfo createDraft(LeadCreateRequest request, String operatorEmpId,
                                         String operatorOrgId, String ignoredSource) {
        return createDraft(request, operatorEmpId, operatorOrgId);
    }

    /** 更新本人 DRAFT 草稿；统一社会信用代码改变时同步重建在途去重键。 */
    @Transactional
    public MarketingLeadInfo updateDraft(Long leadId, LeadUpdateRequest request,
                                         String operatorEmpId, String operatorOrgId) {
        MarketingLeadInfo current = requireOwnedDraft(leadId, operatorEmpId);
        if (request == null) {
            throw error("MARKETING_LEAD_REQUEST_INVALID", "线索请求不能为空");
        }
        String nextCreditCode = request.getUnifiedCreditCode() == null
                ? current.getUnifiedCreditCode()
                : normalizeCreditCode(request.getUnifiedCreditCode());
        if (!Objects.equals(nextCreditCode, current.getUnifiedCreditCode())) {
            MarketingLeadInfo pending = leadMapper.selectActiveByCreditCode(nextCreditCode);
            if (pending != null && !Objects.equals(pending.getId(), leadId)) {
                throw duplicatePending(pending);
            }
            current.setUnifiedCreditCode(nextCreditCode);
            current.setActiveDedupKey(nextCreditCode);
            MarketingCustomerInfo customer = findCustomer(nextCreditCode);
            if (customer != null) {
                current.setCustId(customer.getId());
                current.setCustNoSnapshot(customer.getCustNo());
                current.setCustomerMatchStatus(customer.getIsAccountOpened() != null
                        && customer.getIsAccountOpened() == 1
                        ? "MATCHED_EXISTING_OPENED" : "MATCHED_EXISTING_UNOPENED");
                current.setLeadType("EXISTING_MARKETING");
                current.setBaseCustomerProfileVersion(customer.getProfileVersion());
                current.setMainManagerIdSnapshot(customer.getMainManagerId());
                current.setMainOrgIdSnapshot(customer.getMainOrgId());
                if (StringUtils.hasText(customer.getMainManagerId())) {
                    current.setDistributionMode("OWNER");
                }
            } else {
                current.setCustId(null);
                current.setCustomerMatchStatus("NEW_CUSTOMER");
                current.setLeadType("NEW_ACCOUNT");
            }
        }
        copyNonNull(request, current);
        current.setUnifiedCreditCode(nextCreditCode);
        current.setUpdatedBy(operatorEmpId);
        current.setUpdatedTime(LocalDateTime.now());
        // 主办客户不得在草稿编辑中改成公共范围。
        if (StringUtils.hasText(current.getMainManagerIdSnapshot())) {
            current.setDistributionMode("OWNER");
        }
        leadMapper.updateById(current);
        return current;
    }

    /** 查询本人录入的单条详情，并反显当前客户主档。 */
    public LeadDetailResponse getDetail(Long leadId, String operatorEmpId) {
        MarketingLeadInfo lead = leadMapper.selectActiveById(leadId);
        if (lead == null || !Objects.equals(lead.getEntryEmpId(), operatorEmpId)) {
            throw error("MARKETING_LEAD_NOT_FOUND", "线索不存在");
        }
        LeadDetailResponse response = new LeadDetailResponse();
        response.setLead(lead);
        MarketingCustomerInfo customer = lead.getCustId() == null
                ? findCustomer(lead.getUnifiedCreditCode()) : findCustomerById(lead.getCustId());
        if (customer != null) {
            response.setCurrentCustomer(toSnapshot(customer));
            response.setProfileChanged(profileChanged(lead, customer));
        }
        return response;
    }

    /** 仅按统一社会信用代码查询客户主档，用于新增页面反显。 */
    public MarketingCustomerSnapshot lookupCustomer(String unifiedCreditCode) {
        String normalized = normalizeCreditCode(unifiedCreditCode);
        MarketingCustomerInfo customer = findCustomer(normalized);
        return customer == null ? null : toSnapshot(customer);
    }

    /**
     * 提交单条线索审批。启动工作流成功后才落 IN_APPROVAL；流程启动异常会使事务回滚。
     */
    @Transactional
    public LeadSubmitResponse submit(Long leadId, String operatorEmpId, String operatorOrgId) {
        MarketingLeadInfo lead = requireOwnedDraft(leadId, operatorEmpId);
        MarketingLeadInfo duplicate = leadMapper.selectForUpdateByCreditCode(lead.getUnifiedCreditCode());
        if (duplicate != null && !Objects.equals(duplicate.getId(), leadId)) {
            throw duplicatePending(duplicate);
        }
        if (!StringUtils.hasText(lead.getDistributionMode())) {
            throw error("MARKETING_LEAD_DISTRIBUTION_INVALID", "线索分配方式不能为空");
        }
        if ("OWNER".equals(lead.getDistributionMode())
                && lead.getCustId() != null && !StringUtils.hasText(lead.getMainManagerIdSnapshot())) {
            throw error("MARKETING_LEAD_OWNER_REQUIRED", "存量客户缺少有效主办权，请先处理主办关系");
        }

        LocalDateTime now = LocalDateTime.now();
        lead.setSubmittedBy(operatorEmpId);
        lead.setSubmittedTime(now);
        lead.setBusinessKey("LEAD:" + leadId);
        lead.setUpdatedBy(operatorEmpId);
        lead.setUpdatedTime(now);
        leadMapper.updateById(lead);

        StartProcessCmd command = new StartProcessCmd();
        command.setBizType("LEAD");
        command.setBizId(String.valueOf(leadId));
        command.setBusinessKey(lead.getBusinessKey());
        command.setProcessDefinitionKey("lead_approve_v1");
        command.setStartUser(operatorEmpId);
        command.setStartOrgId(operatorOrgId);
        command.setTitle("线索审批-" + lead.getLeadNo());
        WorkflowLaunchResp launch = workflowApi.startProcess(command);
        if (launch == null || !StringUtils.hasText(launch.getProcessInstanceId())) {
            throw error("MARKETING_LEAD_WORKFLOW_START_FAILED", "线索审批流程启动失败");
        }
        int changed = leadMapper.updateStatusIf(leadId, DRAFT, IN_APPROVAL, operatorEmpId, null);
        if (changed != 1) {
            throw error("MARKETING_LEAD_STATE_CONFLICT", "线索状态已发生变化，请刷新后重试");
        }
        MarketingLeadInfo result = leadMapper.selectActiveById(leadId);
        LeadSubmitResponse response = new LeadSubmitResponse();
        response.setLeadId(leadId);
        response.setLeadStatus(IN_APPROVAL);
        response.setProcessInstanceId(launch.getProcessInstanceId());
        response.setBusinessKey(lead.getBusinessKey());
        if (result != null) {
            result.setProcessInstanceId(launch.getProcessInstanceId());
            result.setBusinessKey(lead.getBusinessKey());
        }
        return response;
    }

    /** 取消尚未进入不可取消阶段的本人线索，释放 active_dedup_key。 */
    @Transactional
    public void cancel(Long leadId, String operatorEmpId) {
        MarketingLeadInfo lead = leadMapper.selectForUpdate(leadId);
        if (lead == null || !Objects.equals(lead.getEntryEmpId(), operatorEmpId)) {
            throw error("MARKETING_LEAD_NOT_FOUND", "线索不存在");
        }
        if (!DRAFT.equals(lead.getLeadStatus()) && !SUBMITTED.equals(lead.getLeadStatus())) {
            throw error("MARKETING_LEAD_CANCEL_FORBIDDEN", "当前线索状态不允许取消");
        }
        if (leadMapper.updateStatusIf(leadId, lead.getLeadStatus(), CANCELLED, operatorEmpId, null) != 1) {
            throw error("MARKETING_LEAD_STATE_CONFLICT", "线索状态已发生变化，请刷新后重试");
        }
        // Mockito 单测和无回写 Mapper 的调用方仍能看到业务对象状态；真实数据库以 CAS 为准。
        lead.setLeadStatus(CANCELLED);
        lead.setActiveDedupKey(null);
    }

    private MarketingLeadInfo requireOwnedDraft(Long leadId, String operatorEmpId) {
        MarketingLeadInfo lead = leadMapper.selectForUpdate(leadId);
        if (lead == null || !Objects.equals(lead.getEntryEmpId(), operatorEmpId)) {
            throw error("MARKETING_LEAD_NOT_FOUND", "线索不存在");
        }
        if (!DRAFT.equals(lead.getLeadStatus())) {
            throw error("MARKETING_LEAD_EDIT_FORBIDDEN", "仅草稿状态线索可编辑或提交");
        }
        return lead;
    }

    private MarketingCustomerInfo findCustomer(String creditCode) {
        if (!StringUtils.hasText(creditCode)) {
            return null;
        }
        return customerMapper.selectOne(new QueryWrapper<MarketingCustomerInfo>()
                .eq("unified_credit_code", normalizeCreditCode(creditCode))
                .eq("record_status", "ACTIVE")
                .last("LIMIT 1"));
    }

    private MarketingCustomerInfo findCustomerById(Long id) {
        return id == null ? null : customerMapper.selectActiveById(id);
    }

    private void copyRequest(LeadCreateRequest request, MarketingLeadInfo target) {
        BeanUtils.copyProperties(request, target,
                "unifiedCreditCode", "leadType", "distributionMode", "mainManagerId", "mainOrgId",
                "managerEmpIds", "tagIds", "custNo", "isAccountOpenedSnapshot");
        target.setCustNoSnapshot(request.getCustNo());
        target.setIsAccountOpenedSnapshot(request.getIsAccountOpenedSnapshot());
        target.setMainManagerIdSnapshot(request.getMainManagerId());
        target.setMainOrgIdSnapshot(request.getMainOrgId());
        target.setLeadType(request.getLeadType());
        target.setDistributionMode(request.getDistributionMode());
    }

    private void copyNonNull(LeadUpdateRequest request, MarketingLeadInfo target) {
        BeanUtils.copyProperties(request, target,
                "unifiedCreditCode", "leadType", "distributionMode", "mainManagerId", "mainOrgId",
                "custNo", "isAccountOpenedSnapshot");
        if (request.getCustName() != null) target.setCustName(request.getCustName());
        if (request.getCustNo() != null) target.setCustNoSnapshot(request.getCustNo());
        if (request.getLeadType() != null) target.setLeadType(request.getLeadType());
        if (request.getDistributionMode() != null) target.setDistributionMode(request.getDistributionMode());
        if (request.getMainManagerId() != null) target.setMainManagerIdSnapshot(request.getMainManagerId());
        if (request.getMainOrgId() != null) target.setMainOrgIdSnapshot(request.getMainOrgId());
        if (request.getIsAccountOpenedSnapshot() != null) {
            target.setIsAccountOpenedSnapshot(request.getIsAccountOpenedSnapshot());
        }
    }

    private void copyCustomerDefaults(MarketingCustomerInfo customer, MarketingLeadInfo lead,
                                      LeadCreateRequest request) {
        // 只将请求未覆盖的基础字段从当前主档带入线索快照。
        copyIfNull(request.getCustName(), customer.getCustName(), lead::setCustName);
        copyIfNull(request.getLegalRepresentative(), customer.getLegalRepresentative(), lead::setLegalRepresentative);
        copyIfNull(request.getRegisteredCapital(), customer.getRegisteredCapital(), lead::setRegisteredCapital);
        copyIfNull(request.getRegisteredAddress(), customer.getRegisteredAddress(), lead::setRegisteredAddress);
        copyIfNull(request.getBusinessAddress(), customer.getBusinessAddress(), lead::setBusinessAddress);
        copyIfNull(request.getBusinessScope(), customer.getBusinessScope(), lead::setBusinessScope);
        copyIfNull(request.getContactPerson(), customer.getContactPerson(), lead::setContactPerson);
        copyIfNull(request.getContactMobile(), customer.getContactMobile(), lead::setContactMobile);
        copyIfNull(request.getIndustry(), customer.getIndustry(), lead::setIndustry);
        copyIfNull(request.getGroupType(), customer.getGroupType(), lead::setGroupType);
        copyIfNull(request.getGroupName(), customer.getGroupName(), lead::setGroupName);
        copyIfNull(request.getCustomerType(), customer.getCustomerType(), lead::setCustomerType);
        copyIfNull(request.getEnterpriseType(), customer.getEnterpriseType(), lead::setEnterpriseType);
        if (request.getIsKeystone() == null) lead.setIsKeystone(customer.getIsKeystone());
        if (request.getIsAccountOpenedSnapshot() == null) lead.setIsAccountOpenedSnapshot(customer.getIsAccountOpened());
        if (request.getTouchRestricted() == null) lead.setTouchRestricted(customer.getTouchRestricted());
        copyIfNull(request.getCustomerDesc(), customer.getCustomerDesc(), lead::setCustomerDesc);
        copyIfNull(request.getCreditAmount(), customer.getCreditAmount(), lead::setCreditAmount);
        copyIfNull(request.getCreditExposureAmount(), customer.getCreditExposureAmount(), lead::setCreditExposureAmount);
    }

    private <T> void copyIfNull(T requestValue, T currentValue, java.util.function.Consumer<T> setter) {
        setter.accept(requestValue == null ? currentValue : requestValue);
    }

    private MarketingCustomerSnapshot toSnapshot(MarketingCustomerInfo customer) {
        MarketingCustomerSnapshot snapshot = new MarketingCustomerSnapshot();
        BeanUtils.copyProperties(customer, snapshot);
        return snapshot;
    }

    private boolean profileChanged(MarketingLeadInfo lead, MarketingCustomerInfo customer) {
        return !Objects.equals(lead.getCustName(), customer.getCustName())
                || !Objects.equals(lead.getContactPerson(), customer.getContactPerson())
                || !Objects.equals(lead.getContactMobile(), customer.getContactMobile())
                || !Objects.equals(lead.getRegisteredAddress(), customer.getRegisteredAddress())
                || !Objects.equals(lead.getBusinessAddress(), customer.getBusinessAddress())
                || !Objects.equals(lead.getCreditExposureAmount(), customer.getCreditExposureAmount());
    }

    private void requireRequest(LeadCreateRequest request) {
        if (request == null || !StringUtils.hasText(request.getCustName())
                || !StringUtils.hasText(request.getUnifiedCreditCode())) {
            throw error("MARKETING_LEAD_REQUEST_INVALID", "企业名称和统一社会信用代码不能为空");
        }
    }

    static String normalizeCreditCode(String creditCode) {
        return creditCode == null ? null : creditCode.replaceAll("\\s+", "").trim().toUpperCase();
    }

    private String generateLeadNo(LocalDateTime now) {
        return "MLEAD_" + now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + "_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    private String defaultValue(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase() : fallback;
    }

    private BizException duplicatePending(MarketingLeadInfo pending) {
        String detail = "统一社会信用代码已有在途线索：" + pending.getLeadNo()
                + "，状态=" + pending.getLeadStatus()
                + "，录入人=" + pending.getEntryEmpId()
                + "，机构=" + pending.getEntryOrgId()
                + "，时间=" + pending.getEntryTime();
        return error("MARKETING_LEAD_PENDING_DUPLICATE", detail);
    }

    private BizException error(String code, String message) {
        return new BizException(code, message);
    }

    private int safePageNo(Integer pageNo) {
        return pageNo == null || pageNo < 1 ? 1 : pageNo;
    }

    private int safePageSize(Integer pageSize) {
        return pageSize == null ? 20 : Math.min(Math.max(pageSize, 1), 100);
    }
}
