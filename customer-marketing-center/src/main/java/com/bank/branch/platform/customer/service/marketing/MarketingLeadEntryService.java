package com.bank.branch.platform.customer.service.marketing;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.UserDTO;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.customer.dto.marketing.lead.LeadCreateRequest;
import com.bank.branch.platform.customer.dto.marketing.lead.LeadDetailResponse;
import com.bank.branch.platform.customer.dto.marketing.lead.LeadQuery;
import com.bank.branch.platform.customer.dto.marketing.lead.LeadSubmitResponse;
import com.bank.branch.platform.customer.dto.marketing.lead.LeadUpdateRequest;
import com.bank.branch.platform.customer.dto.marketing.lead.MarketingCustomerSnapshot;
import com.bank.branch.platform.customer.entity.marketing.MarketingCustomerInfo;
import com.bank.branch.platform.customer.entity.marketing.MarketingCustomerTag;
import com.bank.branch.platform.customer.entity.marketing.MarketingLeadInfo;
import com.bank.branch.platform.customer.entity.marketing.MarketingLeadManagerScope;
import com.bank.branch.platform.customer.entity.marketing.MarketingLeadTagRel;
import com.bank.branch.platform.customer.enums.CustomerRoleCode;
import com.bank.branch.platform.customer.mapper.marketing.MarketingCustomerInfoMapper;
import com.bank.branch.platform.customer.mapper.marketing.MarketingCustomerTagMapper;
import com.bank.branch.platform.customer.mapper.marketing.MarketingLeadInfoMapper;
import com.bank.branch.platform.customer.mapper.marketing.MarketingLeadManagerScopeMapper;
import com.bank.branch.platform.customer.mapper.marketing.MarketingLeadTagRelMapper;
import com.bank.branch.platform.governance.api.FileApi;
import com.bank.branch.platform.governance.api.dto.FileObjectDTO;
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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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
    private final MarketingLeadManagerScopeMapper managerScopeMapper;
    private final MarketingLeadTagRelMapper leadTagRelMapper;
    private final MarketingCustomerTagMapper tagMapper;
    private final UserApi userApi;
    private final FileApi fileApi;

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

        // 物理表字段非空且无默认值，必须由领域服务兜底，不能依赖页面是否传值。
        if (lead.getIsKeystone() == null) {
            lead.setIsKeystone(0);
        }
        if (lead.getTouchRestricted() == null) {
            lead.setTouchRestricted(1);
        }

        // 先完整校验关系数据，避免明知请求无效仍尝试写入主表。
        List<MarketingLeadManagerScope> managerScopes = buildManagerScopes(
                lead, request.getManagerEmpIds(), operatorEmpId, now);
        List<MarketingLeadTagRel> tagSnapshots = buildTagSnapshots(
                request.getTagIds(), operatorEmpId, now);

        try {
            leadMapper.insert(lead);
        } catch (DuplicateKeyException ex) {
            throw duplicatePending(lead);
        }
        insertManagerScopes(lead.getId(), managerScopes);
        insertTagSnapshots(lead.getId(), tagSnapshots);
        bindAttachments(lead.getId(), request.getAttachmentIds());
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
        LocalDateTime relationTime = LocalDateTime.now();
        if (request.getDistributionMode() != null || request.getManagerEmpIds() != null
                || request.getMainManagerId() != null || request.getMainOrgId() != null) {
            replaceManagerScopes(current, request.getManagerEmpIds(), operatorEmpId, relationTime, true);
        }
        if (request.getTagIds() != null) {
            replaceTagSnapshots(current.getId(), request.getTagIds(), operatorEmpId, relationTime, true);
        }
        bindAttachments(current.getId(), request.getAttachmentIds());
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
        response.setManagerEmpIds(managerScopeMapper.selectList(Wrappers
                        .<MarketingLeadManagerScope>lambdaQuery()
                        .eq(MarketingLeadManagerScope::getLeadId, leadId)
                        .orderByAsc(MarketingLeadManagerScope::getId))
                .stream().map(MarketingLeadManagerScope::getManagerEmpId).toList());
        response.setTagIds(leadTagRelMapper.selectList(Wrappers
                        .<MarketingLeadTagRel>lambdaQuery()
                        .eq(MarketingLeadTagRel::getLeadId, leadId)
                        .orderByAsc(MarketingLeadTagRel::getId))
                .stream().map(MarketingLeadTagRel::getTagId).toList());
        List<FileObjectDTO> attachments = fileApi.listBizFiles("LEAD", String.valueOf(leadId));
        response.setAttachments(attachments == null ? List.of() : attachments);
        return response;
    }

    /** 仅按统一社会信用代码查询客户主档，用于新增页面反显。 */
    public MarketingCustomerSnapshot lookupCustomer(String unifiedCreditCode) {
        return lookupCustomer(null, unifiedCreditCode);
    }

    /**
     * 按统一社会信用代码或客户名称精确反显客户主档；两者同时存在时信用代码优先。
     * 客户名称可能存在重名，因此最多读取两条并对歧义显式报错，禁止静默选择第一条。
     */
    public MarketingCustomerSnapshot lookupCustomer(String customerName, String unifiedCreditCode) {
        String normalizedCreditCode = StringUtils.hasText(unifiedCreditCode)
                ? normalizeCreditCode(unifiedCreditCode) : null;
        String normalizedCustomerName = StringUtils.hasText(customerName)
                ? customerName.trim() : null;
        if (!StringUtils.hasText(normalizedCreditCode) && !StringUtils.hasText(normalizedCustomerName)) {
            throw error("MARKETING_CUSTOMER_LOOKUP_REQUIRED", "客户名称和统一社会信用代码至少填写一项");
        }
        List<MarketingCustomerInfo> matches = customerMapper.selectActiveMatches(
                normalizedCreditCode, normalizedCreditCode == null ? normalizedCustomerName : null);
        if (matches == null || matches.isEmpty()) {
            return null;
        }
        if (matches.size() > 1) {
            String message = normalizedCreditCode == null
                    ? "客户名称匹配到多条有效客户，请使用统一社会信用代码查询"
                    : "统一社会信用代码匹配到多条有效客户，请联系管理员处理重复主档";
            throw error("MARKETING_CUSTOMER_LOOKUP_AMBIGUOUS", message);
        }
        return toSnapshot(matches.get(0));
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
                "managerEmpIds", "tagIds", "attachmentIds", "custNo", "isAccountOpenedSnapshot");
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
                "managerEmpIds", "tagIds", "attachmentIds", "custNo", "isAccountOpenedSnapshot");
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
        if (StringUtils.hasText(customer.getMainManagerId())) {
            UserDTO manager = userApi.getUserByEmpId(customer.getMainManagerId());
            if (manager != null) {
                snapshot.setMainManagerName(manager.getDisplayName());
                snapshot.setMainOrgName(manager.getMainOrgName());
            }
        }
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

    /**
     * 按最终分配方式生成接收人快照。先完成全部人员校验，再删除旧关系，避免无效请求产生半成品。
     */
    private void replaceManagerScopes(MarketingLeadInfo lead, List<String> requestedManagerEmpIds,
                                      String operatorEmpId, LocalDateTime now, boolean deleteExisting) {
        List<MarketingLeadManagerScope> scopes = buildManagerScopes(
                lead, requestedManagerEmpIds, operatorEmpId, now);
        if (deleteExisting) {
            managerScopeMapper.delete(Wrappers.<MarketingLeadManagerScope>lambdaQuery()
                    .eq(MarketingLeadManagerScope::getLeadId, lead.getId()));
        }
        insertManagerScopes(lead.getId(), scopes);
    }

    private List<MarketingLeadManagerScope> buildManagerScopes(
            MarketingLeadInfo lead, List<String> requestedManagerEmpIds,
            String operatorEmpId, LocalDateTime now) {
        String mode = defaultValue(lead.getDistributionMode(), "PUBLIC");
        if ("PUBLIC".equals(mode)) {
            return List.of();
        }
        if ("OWNER".equals(mode)) {
            if (!StringUtils.hasText(lead.getMainManagerIdSnapshot())) {
                throw error("MARKETING_LEAD_OWNER_REQUIRED", "主办分配必须存在客户主办人快照");
            }
            UserDTO owner = requireCustomerManager(lead.getMainManagerIdSnapshot());
            String ownerOrgId = StringUtils.hasText(lead.getMainOrgIdSnapshot())
                    ? lead.getMainOrgIdSnapshot() : owner.getMainOrgCode();
            return List.of(toManagerScope(lead.getId(), owner.getEmpId(), ownerOrgId,
                    "OWNER", true, operatorEmpId, now));
        }
        if (!"SCOPE".equals(mode)) {
            throw error("MARKETING_LEAD_DISTRIBUTION_INVALID", "线索分配方式不合法");
        }
        Set<String> managerIds = normalizeManagerIds(requestedManagerEmpIds);
        if (managerIds.isEmpty()) {
            throw error("MARKETING_LEAD_DISTRIBUTION_INVALID", "范围分配必须选择至少一名客户经理");
        }
        List<MarketingLeadManagerScope> scopes = new ArrayList<>(managerIds.size());
        for (String managerId : managerIds) {
            UserDTO manager = requireCustomerManager(managerId);
            scopes.add(toManagerScope(lead.getId(), managerId, manager.getMainOrgCode(),
                    "SCOPE", false, operatorEmpId, now));
        }
        return scopes;
    }

    private UserDTO requireCustomerManager(String empId) {
        UserDTO user = userApi.getUserByEmpId(empId);
        if (user == null || !Boolean.TRUE.equals(user.getEnabled())
                || !StringUtils.hasText(user.getMainOrgCode())
                || !CustomerRoleCode.isCustomerManager(userApi.getUserRoleCodes(empId))) {
            throw error("MARKETING_LEAD_MANAGER_INVALID", "接收人不存在、已停用、缺少主机构或无客户经理角色：" + empId);
        }
        return user;
    }

    private MarketingLeadManagerScope toManagerScope(
            Long leadId, String managerEmpId, String managerOrgId, String assignmentType,
            boolean primary, String operatorEmpId, LocalDateTime now) {
        MarketingLeadManagerScope scope = new MarketingLeadManagerScope();
        scope.setLeadId(leadId);
        scope.setManagerEmpId(managerEmpId);
        scope.setManagerOrgId(managerOrgId);
        scope.setAssignmentType(assignmentType);
        scope.setIsPrimary(primary ? 1 : 0);
        scope.setCreatedBy(operatorEmpId);
        scope.setCreatedTime(now);
        return scope;
    }

    private Set<String> normalizeManagerIds(List<String> managerEmpIds) {
        Set<String> result = new LinkedHashSet<>();
        if (managerEmpIds != null) {
            managerEmpIds.stream().filter(StringUtils::hasText).map(String::trim).forEach(result::add);
        }
        return result;
    }

    private void insertManagerScopes(Long leadId, List<MarketingLeadManagerScope> scopes) {
        for (MarketingLeadManagerScope scope : scopes) {
            scope.setLeadId(leadId);
            managerScopeMapper.insert(scope);
        }
    }

    /** 替换线索标签快照；仅允许有效、已审批且启用的正式标签。 */
    private void replaceTagSnapshots(Long leadId, List<Long> requestedTagIds,
                                     String operatorEmpId, LocalDateTime now, boolean deleteExisting) {
        List<MarketingLeadTagRel> snapshots = buildTagSnapshots(requestedTagIds, operatorEmpId, now);
        if (deleteExisting) {
            leadTagRelMapper.delete(Wrappers.<MarketingLeadTagRel>lambdaQuery()
                    .eq(MarketingLeadTagRel::getLeadId, leadId));
        }
        insertTagSnapshots(leadId, snapshots);
    }

    private List<MarketingLeadTagRel> buildTagSnapshots(List<Long> requestedTagIds,
                                                         String operatorEmpId, LocalDateTime now) {
        List<MarketingLeadTagRel> snapshots = new ArrayList<>();
        Set<Long> tagIds = requestedTagIds == null
                ? Set.of() : new LinkedHashSet<>(requestedTagIds);
        for (Long tagId : tagIds) {
            MarketingCustomerTag tag = tagId == null ? null : tagMapper.selectById(tagId);
            if (tag == null || !"ACTIVE".equalsIgnoreCase(tag.getRecordStatus())
                    || !"APPROVED".equalsIgnoreCase(tag.getApprovalStatus())
                    || !"ENABLED".equalsIgnoreCase(tag.getStatus())) {
                throw error("MARKETING_LEAD_TAG_INVALID", "标签不存在、未审批通过或已停用：" + tagId);
            }
            MarketingLeadTagRel relation = new MarketingLeadTagRel();
            relation.setTagId(tagId);
            relation.setTagNameSnapshot(tag.getTagName());
            relation.setTagSource("MANUAL");
            relation.setCreatedBy(operatorEmpId);
            relation.setCreatedTime(now);
            snapshots.add(relation);
        }
        return snapshots;
    }

    private void insertTagSnapshots(Long leadId, List<MarketingLeadTagRel> snapshots) {
        for (MarketingLeadTagRel snapshot : snapshots) {
            snapshot.setLeadId(leadId);
            leadTagRelMapper.insert(snapshot);
        }
    }

    private void bindAttachments(Long leadId, List<String> attachmentIds) {
        if (leadId == null || attachmentIds == null) {
            return;
        }
        attachmentIds.stream().filter(StringUtils::hasText).map(String::trim)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new))
                .forEach(fileId -> fileApi.bindFile(
                        "LEAD", String.valueOf(leadId), fileId, "ATTACHMENT"));
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
