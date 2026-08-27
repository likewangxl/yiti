package com.bank.branch.platform.customer.service.marketing;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.customer.dto.marketing.lead.LeadApprovalTaskResponse;
import com.bank.branch.platform.customer.dto.marketing.lead.LeadDetailResponse;
import com.bank.branch.platform.customer.dto.marketing.lead.MarketingCustomerSnapshot;
import com.bank.branch.platform.customer.entity.marketing.MarketingCustomerInfo;
import com.bank.branch.platform.customer.entity.marketing.MarketingLeadInfo;
import com.bank.branch.platform.customer.entity.marketing.MarketingLeadManagerScope;
import com.bank.branch.platform.customer.entity.marketing.MarketingLeadTagRel;
import com.bank.branch.platform.customer.mapper.marketing.MarketingCustomerInfoMapper;
import com.bank.branch.platform.customer.mapper.marketing.MarketingLeadInfoMapper;
import com.bank.branch.platform.customer.mapper.marketing.MarketingLeadManagerScopeMapper;
import com.bank.branch.platform.customer.mapper.marketing.MarketingLeadTagRelMapper;
import com.bank.branch.platform.governance.api.FileApi;
import com.bank.branch.platform.governance.api.dto.FileObjectDTO;
import com.bank.branch.platform.workflow.api.WorkflowApi;
import com.bank.branch.platform.workflow.api.WorkflowQueryApi;
import com.bank.branch.platform.workflow.api.dto.TaskRespDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 页面四线索审批服务。
 *
 * <p>工作流查询和办理统一通过 workflow-center 的公开 API；本服务只负责把任务
 * 映射到新线索表，并对 MANUAL/LEAD_IMPORT 线索执行客户主档装配。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketingLeadApprovalService {

    private static final Set<String> PAGE_SOURCES = Set.of("MANUAL", "LEAD_IMPORT");
    private static final Set<String> REVIEW_RESULTS = Set.of("APPROVED", "REJECTED");
    private static final String IN_APPROVAL = "IN_APPROVAL";
    private static final int WORKFLOW_SCAN_PAGE_SIZE = 100;

    private final WorkflowQueryApi workflowQueryApi;
    private final WorkflowApi workflowApi;
    private final MarketingLeadInfoMapper leadMapper;
    private final MarketingCustomerInfoMapper customerMapper;
    private final MarketingLeadManagerScopeMapper managerScopeMapper;
    private final MarketingLeadTagRelMapper leadTagRelMapper;
    private final FileApi fileApi;

    /** 查询当前登录人的待审批线索，过滤标签导入线索。 */
    public PageResult<LeadApprovalTaskResponse> pending(String keyword, int pageNo,
                                                        int pageSize, String operatorEmpId) {
        List<LeadApprovalTaskResponse> records = scanWorkflowTasks(operatorEmpId, false).stream()
                .filter(item -> IN_APPROVAL.equals(item.getLeadStatus()))
                .filter(item -> matchesKeyword(item, keyword))
                .toList();
        return paginate(records, pageNo, pageSize);
    }

    /** 查询当前登录人实际办理过的审批记录，并按通过/退回结果互斥过滤。 */
    public PageResult<LeadApprovalTaskResponse> history(String keyword, String result, int pageNo,
                                                        int pageSize, String operatorEmpId) {
        String normalizedResult = normalizeReviewResult(result);
        List<LeadApprovalTaskResponse> records = scanWorkflowTasks(operatorEmpId, true).stream()
                .filter(item -> normalizedResult.equals(item.getLeadStatus()))
                .filter(item -> matchesKeyword(item, keyword))
                .toList();
        return paginate(records, pageNo, pageSize);
    }

    /** 查询审批详情；同时返回当前客户主档以支持差异视图。 */
    public LeadDetailResponse detail(Long leadId, String operatorEmpId) {
        MarketingLeadInfo lead = leadMapper.selectActiveById(leadId);
        if (lead == null || !PAGE_SOURCES.contains(lead.getLeadSource())) {
            throw error("MARKETING_LEAD_NOT_FOUND", "线索不存在");
        }
        LeadDetailResponse response = new LeadDetailResponse();
        response.setLead(lead);
        MarketingCustomerInfo customer = findCustomer(lead);
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

    /**
     * 审批通过。先由 WorkflowApi 校验当前任务权限，再以状态 CAS 推进业务状态；
     * CAS 为 0 时按幂等成功处理，避免 Flowable 回调和页面重复点击造成重复装配。
     */
    @Transactional
    public void approve(Long leadId, String taskId, String operatorEmpId, String opinion) {
        MarketingLeadInfo lead = requireApprovable(leadId);
        requireTaskId(taskId);
        checkProfileVersion(lead);
        workflowApi.approveByEmp(taskId, operatorEmpId, opinion);
        int changed = leadMapper.updateStatusIf(leadId, IN_APPROVAL, "APPROVED", operatorEmpId, null);
        if (changed == 0) {
            MarketingLeadInfo latest = leadMapper.selectActiveById(leadId);
            if (latest == null || !"APPROVED".equals(latest.getLeadStatus())) {
                throw error("MARKETING_LEAD_STATE_CONFLICT", "线索审批状态已发生变化，请刷新后重试");
            }
            return;
        }
        assembleCustomer(lead, operatorEmpId);
    }

    /** 审批驳回必须填写原因，不装配客户主档。 */
    @Transactional
    public void reject(Long leadId, String taskId, String operatorEmpId, String reason) {
        MarketingLeadInfo lead = requireApprovable(leadId);
        requireTaskId(taskId);
        if (!StringUtils.hasText(reason)) {
            throw error("MARKETING_LEAD_REJECT_REASON_REQUIRED", "驳回原因不能为空");
        }
        workflowApi.rejectByEmp(taskId, operatorEmpId, reason);
        int changed = leadMapper.updateStatusIf(leadId, IN_APPROVAL, "REJECTED", operatorEmpId, reason);
        if (changed == 0) {
            MarketingLeadInfo latest = leadMapper.selectActiveById(leadId);
            if (latest == null || !"REJECTED".equals(latest.getLeadStatus())) {
                throw error("MARKETING_LEAD_STATE_CONFLICT", "线索审批状态已发生变化，请刷新后重试");
            }
        }
    }

    private MarketingLeadInfo requireApprovable(Long leadId) {
        MarketingLeadInfo lead = leadMapper.selectForUpdate(leadId);
        if (lead == null || !PAGE_SOURCES.contains(lead.getLeadSource())) {
            throw error("MARKETING_LEAD_NOT_FOUND", "线索不存在");
        }
        if (!IN_APPROVAL.equals(lead.getLeadStatus())) {
            if ("APPROVED".equals(lead.getLeadStatus()) || "REJECTED".equals(lead.getLeadStatus())) {
                throw error("MARKETING_LEAD_ALREADY_FINISHED", "线索已完成审批");
            }
            throw error("MARKETING_LEAD_NOT_APPROVABLE", "当前线索不处于待审批状态");
        }
        return lead;
    }

    private void checkProfileVersion(MarketingLeadInfo lead) {
        if (lead.getCustId() == null || lead.getBaseCustomerProfileVersion() == null) return;
        MarketingCustomerInfo current = findCustomer(lead);
        if (current == null) return;
        if (!Objects.equals(lead.getBaseCustomerProfileVersion(), current.getProfileVersion())) {
            throw error("MARKETING_LEAD_PROFILE_CONFLICT", "客户主档已发生变化，请刷新审批详情后重新确认");
        }
    }

    private void assembleCustomer(MarketingLeadInfo lead, String operatorEmpId) {
        MarketingCustomerInfo customer = findCustomer(lead);
        LocalDateTime now = LocalDateTime.now();
        if (customer == null) {
            customer = new MarketingCustomerInfo();
            copyLeadFields(lead, customer);
            customer.setCurrentLeadId(lead.getId());
            customer.setProfileVersion(1);
            customer.setLockVersion(0);
            customer.setOwnershipStatus(StringUtils.hasText(lead.getMainManagerIdSnapshot())
                    ? "ASSIGNED" : "UNASSIGNED");
            customer.setMainManagerId(lead.getMainManagerIdSnapshot());
            customer.setMainOrgId(lead.getMainOrgIdSnapshot());
            customer.setOwnershipSource("LEAD");
            customer.setOwnershipMaintainMode("MANUAL");
            customer.setRecordStatus("ACTIVE");
            customer.setCreatedBy(operatorEmpId);
            customer.setCreatedTime(now);
            customer.setUpdatedBy(operatorEmpId);
            customer.setUpdatedTime(now);
            customerMapper.insert(customer);
            lead.setCustId(customer.getId());
            leadMapper.updateById(lead);
            return;
        }

        // 存量客户只更新允许营销线索维护的资料，不改客户号、统一信用代码、开户和主办权。
        copyExistingCustomerProfile(lead, customer);
        customer.setCurrentLeadId(lead.getId());
        customer.setProfileVersion((customer.getProfileVersion() == null ? 0 : customer.getProfileVersion()) + 1);
        customer.setUpdatedBy(operatorEmpId);
        customer.setUpdatedTime(now);
        customerMapper.updateById(customer);
        if (!Objects.equals(lead.getCustId(), customer.getId())) {
            lead.setCustId(customer.getId());
            leadMapper.updateById(lead);
        }
    }

    private void copyLeadFields(MarketingLeadInfo lead, MarketingCustomerInfo customer) {
        BeanUtils.copyProperties(lead, customer,
                "id", "custId", "leadNo", "leadType", "customerMatchStatus", "baseCustomerProfileVersion",
                "custNoSnapshot", "isAccountOpenedSnapshot", "leadSource", "distributionMode", "poolStatus",
                "mainManagerIdSnapshot", "mainOrgIdSnapshot", "entryEmpId", "entryOrgId", "entryTime",
                "leadStatus", "activeDedupKey", "submittedBy", "submittedTime", "businessKey",
                "processInstanceId", "importBatchId", "batchRowNo", "tagImportDetailId", "reviewedBy",
                "reviewedTime", "rejectReason", "remark", "recordStatus", "createdBy", "createdTime",
                "updatedBy", "updatedTime", "lockVersion");
        customer.setCustNo(lead.getCustNoSnapshot());
        customer.setIsAccountOpened(lead.getIsAccountOpenedSnapshot());
    }

    /** 存量审批只更新资料快照，保留客户号、统一社会信用代码、开户状态和主办权。 */
    private void copyExistingCustomerProfile(MarketingLeadInfo lead, MarketingCustomerInfo customer) {
        BeanUtils.copyProperties(lead, customer,
                "id", "custId", "leadNo", "leadType", "customerMatchStatus", "baseCustomerProfileVersion",
                "custNoSnapshot", "custNo", "unifiedCreditCode", "isAccountOpenedSnapshot", "isAccountOpened",
                "leadSource", "distributionMode", "poolStatus", "mainManagerIdSnapshot", "mainManagerOrgIdSnapshot",
                "mainManagerId", "mainOrgId", "ownershipStatus", "ownershipSource", "ownershipMaintainMode",
                "entryEmpId", "entryOrgId", "entryTime", "leadStatus", "activeDedupKey", "submittedBy",
                "submittedTime", "businessKey", "processInstanceId", "importBatchId", "batchRowNo",
                "tagImportDetailId", "reviewedBy", "reviewedTime", "rejectReason", "remark", "recordStatus",
                "createdBy", "createdTime", "updatedBy", "updatedTime", "lockVersion");
    }

    private MarketingCustomerInfo findCustomer(MarketingLeadInfo lead) {
        if (lead.getCustId() != null) {
            MarketingCustomerInfo byId = customerMapper.selectActiveById(lead.getCustId());
            if (byId != null) return byId;
        }
        if (!StringUtils.hasText(lead.getUnifiedCreditCode())) return null;
        return customerMapper.selectOne(new QueryWrapper<MarketingCustomerInfo>()
                .eq("unified_credit_code", lead.getUnifiedCreditCode())
                .eq("record_status", "ACTIVE")
                .last("LIMIT 1"));
    }

    /**
     * 工作流公开查询先按 Flowable 原始任务分页，再做业务类型过滤。页面还需排除
     * TAG_IMPORT，因此必须遍历当前审批人的完整任务集合后再进行业务分页，否则卡片
     * 数量和翻页会受“当前工作流页”影响。
     */
    private List<LeadApprovalTaskResponse> scanWorkflowTasks(String operatorEmpId, boolean done) {
        Map<String, TaskRespDTO> distinctTasks = new LinkedHashMap<>();
        int workflowPageNo = 1;
        long workflowTotal;
        do {
            PageResult<TaskRespDTO> page = done
                    ? workflowQueryApi.queryDoneList(operatorEmpId, "LEAD", null,
                            workflowPageNo, WORKFLOW_SCAN_PAGE_SIZE)
                    : workflowQueryApi.queryTodoList(operatorEmpId, "LEAD", null,
                            workflowPageNo, WORKFLOW_SCAN_PAGE_SIZE);
            if (page == null) break;
            workflowTotal = page.getTotal();
            if (page.getRecords() != null) {
                for (TaskRespDTO task : page.getRecords()) {
                    if (task == null) continue;
                    String key = StringUtils.hasText(task.getTaskId())
                            ? task.getTaskId()
                            : task.getProcessInstanceId() + ':' + task.getBizId();
                    distinctTasks.putIfAbsent(key, task);
                }
            }
            workflowPageNo++;
        } while ((long) (workflowPageNo - 1) * WORKFLOW_SCAN_PAGE_SIZE < workflowTotal);

        List<LeadApprovalTaskResponse> records = new ArrayList<>();
        for (TaskRespDTO task : distinctTasks.values()) {
            if (task == null || !"LEAD".equalsIgnoreCase(task.getBizType())
                    || !StringUtils.hasText(task.getBizId())) continue;
            Long leadId;
            try {
                leadId = Long.valueOf(task.getBizId());
            } catch (NumberFormatException ex) {
                continue;
            }
            MarketingLeadInfo lead = leadMapper.selectActiveById(leadId);
            if (lead == null || !PAGE_SOURCES.contains(lead.getLeadSource())) continue;
            records.add(toResponse(task, lead));
        }
        return records;
    }

    private LeadApprovalTaskResponse toResponse(TaskRespDTO task, MarketingLeadInfo lead) {
        LeadApprovalTaskResponse response = new LeadApprovalTaskResponse();
        response.setTask(task);
        response.setLeadId(lead.getId());
        response.setLeadNo(lead.getLeadNo());
        response.setCustName(lead.getCustName());
        response.setUnifiedCreditCode(lead.getUnifiedCreditCode());
        response.setLeadType(lead.getLeadType());
        response.setLeadSource(lead.getLeadSource());
        response.setIndustry(lead.getIndustry());
        response.setDistributionMode(lead.getDistributionMode());
        response.setCustomerMatchStatus(lead.getCustomerMatchStatus());
        response.setLeadStatus(lead.getLeadStatus());
        response.setSubmittedBy(lead.getSubmittedBy());
        response.setSubmittedTime(lead.getSubmittedTime());
        response.setReviewedBy(lead.getReviewedBy());
        response.setReviewedTime(lead.getReviewedTime());
        response.setRejectReason(lead.getRejectReason());
        MarketingCustomerInfo customer = findCustomer(lead);
        if (customer != null) response.setCurrentCustomer(toSnapshot(customer));
        return response;
    }

    private PageResult<LeadApprovalTaskResponse> paginate(List<LeadApprovalTaskResponse> all,
                                                           int pageNo, int pageSize) {
        int safePageNo = safePageNo(pageNo);
        int safePageSize = safePageSize(pageSize);
        long offset = (long) (safePageNo - 1) * safePageSize;
        int fromIndex = (int) Math.min(offset, all.size());
        int toIndex = Math.min(fromIndex + safePageSize, all.size());
        return PageResult.of(safePageNo, safePageSize, all.size(), all.subList(fromIndex, toIndex));
    }

    private boolean matchesKeyword(LeadApprovalTaskResponse item, String keyword) {
        if (!StringUtils.hasText(keyword)) return true;
        String expected = keyword.trim().toLowerCase(Locale.ROOT);
        return containsIgnoreCase(item.getCustName(), expected)
                || containsIgnoreCase(item.getUnifiedCreditCode(), expected)
                || containsIgnoreCase(item.getLeadNo(), expected)
                || containsIgnoreCase(item.getSubmittedBy(), expected);
    }

    private boolean containsIgnoreCase(String value, String expectedLowerCase) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(expectedLowerCase);
    }

    private String normalizeReviewResult(String result) {
        String normalized = StringUtils.hasText(result) ? result.trim().toUpperCase(Locale.ROOT) : null;
        if (normalized == null || !REVIEW_RESULTS.contains(normalized)) {
            throw error("MARKETING_LEAD_APPROVAL_RESULT_INVALID", "审批结果仅支持APPROVED或REJECTED");
        }
        return normalized;
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
                || !Objects.equals(lead.getCreditExposureAmount(), customer.getCreditExposureAmount());
    }

    private void requireTaskId(String taskId) {
        if (!StringUtils.hasText(taskId)) throw error("MARKETING_LEAD_TASK_REQUIRED", "审批任务不能为空");
    }

    private BizException error(String code, String message) { return new BizException(code, message); }

    private int safePageNo(int value) { return Math.max(value, 1); }

    private int safePageSize(int value) { return Math.min(Math.max(value, 1), 100); }
}
