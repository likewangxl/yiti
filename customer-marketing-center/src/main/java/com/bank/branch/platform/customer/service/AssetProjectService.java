package com.bank.branch.platform.customer.service;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.customer.dto.asset.AssetProjectQuery;
import com.bank.branch.platform.customer.dto.asset.AssetProjectSaveRequest;
import com.bank.branch.platform.customer.dto.asset.AssetProjectSubmitResponse;
import com.bank.branch.platform.customer.dto.asset.AssetProjectUrgentContextVO;
import com.bank.branch.platform.customer.dto.asset.AssetProjectVO;
import com.bank.branch.platform.customer.entity.AssetProjectApply;
import com.bank.branch.platform.customer.entity.AssetProjectUrgentApply;
import com.bank.branch.platform.customer.entity.TouchTask;
import com.bank.branch.platform.customer.entity.TouchWorklog;
import com.bank.branch.platform.customer.entity.marketing.MarketingCustomerInfo;
import com.bank.branch.platform.customer.enums.CustomerErrorCode;
import com.bank.branch.platform.customer.mapper.AssetProjectApplyMapper;
import com.bank.branch.platform.customer.mapper.AssetProjectUrgentApplyMapper;
import com.bank.branch.platform.customer.mapper.TouchTaskMapper;
import com.bank.branch.platform.customer.mapper.TouchWorklogMapper;
import com.bank.branch.platform.customer.mapper.marketing.MarketingCustomerInfoMapper;
import com.bank.branch.platform.governance.api.FileApi;
import com.bank.branch.platform.workflow.api.WorkflowApi;
import com.bank.branch.platform.workflow.api.WorkflowQueryApi;
import com.bank.branch.platform.workflow.api.dto.ProcessDiagramDTO;
import com.bank.branch.platform.workflow.api.dto.ProcessDiagramNodeDTO;
import com.bank.branch.platform.workflow.api.dto.StartProcessCmd;
import com.bank.branch.platform.workflow.api.dto.TaskRespDTO;
import com.bank.branch.platform.workflow.api.dto.WorkflowLaunchResp;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/** 资产立项完整业务服务；运行时仅操作 MARKETING_ASSET_PROJECT_*。 */
@Service
@RequiredArgsConstructor
public class AssetProjectService {
    private static final String MAIN_BIZ_TYPE = "ASSET_PROJECT";
    private static final String URGENT_BIZ_TYPE = "ASSET_PROJECT_URGENT";
    private static final String PROCESS_DEFINITION_KEY = "loan_approve_v1";
    private static final Set<String> CANCELLABLE_NODES = Set.of("branch_approve", "corp_review");
    private static final Set<String> URGENT_ALLOWED_NODES = Set.of("branch_approve", "corp_review", "credit_check");

    private final AssetProjectApplyMapper applyMapper;
    private final AssetProjectUrgentApplyMapper urgentMapper;
    private final MarketingCustomerInfoMapper customerMapper;
    private final TouchTaskMapper touchTaskMapper;
    private final TouchWorklogMapper worklogMapper;
    private final WorkflowApi workflowApi;
    private final WorkflowQueryApi workflowQueryApi;
    private final FileApi fileApi;

    public PageResult<AssetProjectVO> page(AssetProjectQuery query, String empId, boolean admin) {
        normalizeQuery(query);
        List<TaskRespDTO> workflowTasks = workflowTasks(query, empId);
        List<Long> bizIds = workflowTasks.stream().map(TaskRespDTO::getBizId).map(this::tryParseId)
                .filter(Objects::nonNull).distinct().toList();
        int offset = (query.getPageNo() - 1) * query.getPageSize();
        List<AssetProjectApply> rows = applyMapper.selectPage(query, empId, bizIds, offset, query.getPageSize());
        long total = applyMapper.countPage(query, empId, bizIds);
        Map<Long, TaskRespDTO> taskByBiz = new LinkedHashMap<>();
        workflowTasks.forEach(task -> {
            Long id = tryParseId(task.getBizId());
            if (id != null) taskByBiz.putIfAbsent(id, task);
        });
        List<AssetProjectVO> records = rows.stream()
                .map(row -> toVO(row, empId, admin, false, taskByBiz.get(row.getId())))
                .toList();
        return PageResult.of(query.getPageNo(), query.getPageSize(), total, records);
    }

    @Transactional
    public AssetProjectVO create(AssetProjectSaveRequest request, String empId, String orgId) {
        MarketingCustomerInfo customer = validateCustomerAndSource(request, empId);
        validateAmounts(request);
        LocalDateTime now = LocalDateTime.now();
        AssetProjectApply entity = new AssetProjectApply();
        entity.setApplyNo(generateNo("AP"));
        copyDraftFields(entity, request, customer);
        entity.setApplicantEmpId(empId);
        entity.setApplicantOrgId(orgId);
        entity.setStatus("DRAFT");
        entity.setRecordStatus("ACTIVE");
        entity.setCreatedBy(empId);
        entity.setCreatedTime(now);
        entity.setUpdatedBy(empId);
        entity.setUpdatedTime(now);
        entity.setLockVersion(0);
        applyMapper.insert(entity);
        bindAttachments(entity.getId(), request.getAttachmentIds());
        return toVO(entity, empId, false, true, null);
    }

    @Transactional
    public AssetProjectVO update(Long id, AssetProjectSaveRequest request, String empId, boolean admin) {
        AssetProjectApply current = requireActive(id);
        requireDraftOwner(current, empId, admin);
        if (request.getLockVersion() == null || !request.getLockVersion().equals(current.getLockVersion())) {
            throw error(CustomerErrorCode.ASSET_PROJECT_STATUS_CONFLICT);
        }
        MarketingCustomerInfo customer = validateCustomerAndSource(request, current.getApplicantEmpId());
        validateAmounts(request);
        copyDraftFields(current, request, customer);
        current.setUpdatedBy(empId);
        current.setUpdatedTime(LocalDateTime.now());
        if (applyMapper.updateDraftCas(current, request.getLockVersion()) != 1) {
            throw error(CustomerErrorCode.ASSET_PROJECT_STATUS_CONFLICT);
        }
        current.setLockVersion(current.getLockVersion() + 1);
        bindAttachments(id, request.getAttachmentIds());
        return toVO(current, empId, admin, true, null);
    }

    @Transactional
    public AssetProjectSubmitResponse submit(Long id, String empId, String orgId, boolean admin) {
        AssetProjectApply current = applyMapper.selectForUpdate(id);
        if (current == null) throw error(CustomerErrorCode.ASSET_PROJECT_NOT_FOUND);
        requireDraftOwner(current, empId, admin);
        validateRequiredForSubmit(current);
        AssetProjectSaveRequest validation = toSaveRequest(current);
        validateCustomerAndSource(validation, current.getApplicantEmpId());
        validateAmounts(validation);
        String businessKey = "ASSET_PROJECT:" + id;
        StartProcessCmd cmd = startCmd(MAIN_BIZ_TYPE, String.valueOf(id), businessKey,
                "资产立项审批-" + current.getApplyNo(), empId, orgId);
        WorkflowLaunchResp workflow = workflowApi.startProcess(cmd);
        LocalDateTime now = LocalDateTime.now();
        if (applyMapper.markSubmitted(id, current.getLockVersion(), businessKey,
                workflow.getProcessInstanceId(), empId, now) != 1) {
            throw error(CustomerErrorCode.ASSET_PROJECT_STATUS_CONFLICT);
        }
        return new AssetProjectSubmitResponse(workflow.getProcessInstanceId(), businessKey, "IN_APPROVAL");
    }

    @Transactional
    public void delete(Long id, Integer lockVersion, String empId, boolean admin) {
        AssetProjectApply current = requireActive(id);
        requireDraftOwner(current, empId, admin);
        if (lockVersion == null || applyMapper.markDeleted(id, lockVersion, empId, LocalDateTime.now()) != 1) {
            throw error(CustomerErrorCode.ASSET_PROJECT_STATUS_CONFLICT);
        }
    }

    @Transactional
    public void cancel(Long id, String reason, String empId, boolean admin) {
        AssetProjectApply current = applyMapper.selectForUpdate(id);
        if (current == null) throw error(CustomerErrorCode.ASSET_PROJECT_NOT_FOUND);
        requireOwner(current, empId, admin);
        if (!"IN_APPROVAL".equals(current.getStatus()) || !StringUtils.hasText(current.getProcessInstanceId())) {
            throw error(CustomerErrorCode.ASSET_PROJECT_STATUS_CONFLICT);
        }
        ProcessDiagramNodeDTO active = singleActiveNode(current.getProcessInstanceId());
        if (!CANCELLABLE_NODES.contains(active.getNodeKey())) {
            throw new BizException(CustomerErrorCode.ASSET_PROJECT_INVALID.getCode(), "当前审批节点不允许撤回");
        }
        workflowApi.cancelProcess(current.getProcessInstanceId(), reason);
        if (applyMapper.conditionalUpdateStatus(id, "IN_APPROVAL", "CANCELLED", empId, LocalDateTime.now()) != 1) {
            throw error(CustomerErrorCode.ASSET_PROJECT_STATUS_CONFLICT);
        }
    }

    public AssetProjectVO detail(Long id, String empId, boolean admin) {
        AssetProjectApply current = requireActive(id);
        ensureReadable(current, empId, admin);
        return toVO(current, empId, admin, true, null);
    }

    public AssetProjectUrgentContextVO urgentContext(Long id, String empId, boolean admin) {
        AssetProjectApply current = requireActive(id);
        requireOwner(current, empId, admin);
        MarketingCustomerInfo customer = customerMapper.selectActiveById(current.getCustId());
        AssetProjectUrgentContextVO.AssetProjectUrgentContextVOBuilder builder = AssetProjectUrgentContextVO.builder()
                .assetProjectId(id).applyNo(current.getApplyNo()).projectName(current.getProjectName())
                .customerName(customer == null ? null : customer.getCustName());
        if (!"IN_APPROVAL".equals(current.getStatus())) {
            return builder.allowed(false).unavailableReason("仅审批中的项目可申请加急").build();
        }
        if (Integer.valueOf(1).equals(current.getIsUrgent())) {
            return builder.allowed(false).unavailableReason("该项目已加急").build();
        }
        if (urgentMapper.selectActiveByAssetProjectId(id) != null) {
            return builder.allowed(false).unavailableReason("已有在途加急申请").build();
        }
        ProcessDiagramNodeDTO node = singleActiveNode(current.getProcessInstanceId());
        boolean allowed = URGENT_ALLOWED_NODES.contains(node.getNodeKey());
        return builder.currentNodeKey(node.getNodeKey()).currentNodeName(node.getNodeName())
                .currentTaskId(node.getTaskId()).allowed(allowed)
                .unavailableReason(allowed ? null : "当前节点不允许申请加急").build();
    }

    @Transactional
    public AssetProjectSubmitResponse requestUrgent(Long id, String reason, String empId, String orgId, boolean admin) {
        AssetProjectApply current = applyMapper.selectForUpdate(id);
        if (current == null) throw error(CustomerErrorCode.ASSET_PROJECT_NOT_FOUND);
        requireOwner(current, empId, admin);
        if (!"IN_APPROVAL".equals(current.getStatus()) || Integer.valueOf(1).equals(current.getIsUrgent())) {
            throw error(CustomerErrorCode.ASSET_PROJECT_URGENT_NOT_ALLOWED);
        }
        if (urgentMapper.selectActiveByAssetProjectId(id) != null) {
            throw error(CustomerErrorCode.ASSET_PROJECT_URGENT_DUPLICATE);
        }
        ProcessDiagramNodeDTO node = singleActiveNode(current.getProcessInstanceId());
        if (!URGENT_ALLOWED_NODES.contains(node.getNodeKey()) || !StringUtils.hasText(node.getTaskId())) {
            throw error(CustomerErrorCode.ASSET_PROJECT_URGENT_NOT_ALLOWED);
        }
        LocalDateTime now = LocalDateTime.now();
        AssetProjectUrgentApply urgent = new AssetProjectUrgentApply();
        urgent.setUrgentApplyNo(generateNo("AU"));
        urgent.setAssetProjectApplyId(id);
        urgent.setCustId(current.getCustId());
        urgent.setRequestedAtNodeKey(node.getNodeKey());
        urgent.setRequestedAtTaskId(node.getTaskId());
        urgent.setApplyReason(reason.trim());
        urgent.setStatus("DRAFT");
        urgent.setActiveDedupKey("ASSET_PROJECT:" + id);
        urgent.setRequestedBy(empId);
        urgent.setRequestedOrgId(orgId);
        urgent.setRequestedTime(now);
        urgent.setRecordStatus("ACTIVE");
        urgent.setCreatedBy(empId);
        urgent.setCreatedTime(now);
        urgent.setUpdatedBy(empId);
        urgent.setUpdatedTime(now);
        urgent.setLockVersion(0);
        urgentMapper.insert(urgent);
        String businessKey = "ASSET_PROJECT_URGENT:" + urgent.getId();
        WorkflowLaunchResp workflow = workflowApi.startProcess(startCmd(URGENT_BIZ_TYPE,
                String.valueOf(urgent.getId()), businessKey, "资产立项加急审批-" + current.getApplyNo(), empId, orgId));
        urgent.setStatus("IN_APPROVAL");
        urgent.setBusinessKey(businessKey);
        urgent.setProcessInstanceId(workflow.getProcessInstanceId());
        urgent.setUpdatedTime(LocalDateTime.now());
        urgentMapper.updateById(urgent);
        return new AssetProjectSubmitResponse(workflow.getProcessInstanceId(), businessKey, "IN_APPROVAL");
    }

    public List<AssetProjectUrgentApply> urgentApplies(Long id, String empId, boolean admin) {
        AssetProjectApply current = requireActive(id);
        ensureReadable(current, empId, admin);
        return urgentMapper.selectByAssetProjectId(id);
    }

    private AssetProjectVO toVO(AssetProjectApply row, String empId, boolean admin, boolean detail, TaskRespDTO task) {
        AssetProjectVO vo = new AssetProjectVO();
        vo.setId(row.getId()); vo.setApplyNo(row.getApplyNo()); vo.setCustId(row.getCustId());
        MarketingCustomerInfo customer = customerMapper.selectActiveById(row.getCustId());
        if (customer != null) {
            vo.setCustNo(customer.getCustNo()); vo.setCustomerName(customer.getCustName());
            vo.setUnifiedCreditCode(customer.getUnifiedCreditCode());
            vo.setMainManagerId(customer.getMainManagerId()); vo.setMainOrgId(customer.getMainOrgId());
        }
        vo.setSourceTouchTaskId(row.getSourceTouchTaskId()); vo.setSourceWorklogId(row.getSourceWorklogId());
        if (row.getSourceTouchTaskId() != null) {
            TouchTask sourceTask = touchTaskMapper.selectById(String.valueOf(row.getSourceTouchTaskId()));
            if (sourceTask != null) vo.setSourceTouchTaskNo(sourceTask.getTaskNo());
        }
        if (row.getSourceWorklogId() != null) {
            TouchWorklog log = worklogMapper.selectById(row.getSourceWorklogId());
            if (log != null) vo.setSourceWorklogNo(log.getWorklogNo());
        }
        vo.setProjectName(row.getProjectName()); vo.setProjectType(row.getProjectType()); vo.setBizType(row.getBizType());
        vo.setGuaranteeType(row.getGuaranteeType()); vo.setProjectTotalInvestment(row.getProjectTotalInvestment());
        vo.setProjectLoanAmount(row.getProjectLoanAmount()); vo.setCreditAmount(row.getCreditAmount());
        vo.setCreditExposureAmount(row.getCreditExposureAmount()); vo.setUrgent(Integer.valueOf(1).equals(row.getIsUrgent()));
        vo.setKeyProject(Integer.valueOf(1).equals(row.getIsKeyProject())); vo.setUrgentSource(row.getUrgentSource());
        vo.setApplicantEmpId(row.getApplicantEmpId()); vo.setApplicantOrgId(row.getApplicantOrgId()); vo.setStatus(row.getStatus());
        vo.setBusinessKey(row.getBusinessKey()); vo.setProcessInstanceId(row.getProcessInstanceId());
        vo.setSubmittedTime(row.getSubmittedTime()); vo.setCompletedTime(row.getCompletedTime());
        vo.setCreatedTime(row.getCreatedTime()); vo.setUpdatedTime(row.getUpdatedTime()); vo.setLockVersion(row.getLockVersion());
        boolean owner = admin || empId.equals(row.getApplicantEmpId());
        vo.setCanEdit(owner && "DRAFT".equals(row.getStatus())); vo.setCanSubmit(vo.isCanEdit()); vo.setCanDelete(vo.isCanEdit());
        vo.setCanCancel(owner && "IN_APPROVAL".equals(row.getStatus()));
        vo.setCanApplyUrgent(owner && "IN_APPROVAL".equals(row.getStatus()) && !vo.isUrgent());
        if (task != null) applyTask(vo, task);
        if (detail) enrichDetail(vo, row);
        return vo;
    }

    private void enrichDetail(AssetProjectVO vo, AssetProjectApply row) {
        vo.setAttachments(fileApi.listBizFiles(MAIN_BIZ_TYPE, String.valueOf(row.getId())));
        vo.setUrgentApplies(urgentMapper.selectByAssetProjectId(row.getId()));
        if (StringUtils.hasText(row.getProcessInstanceId())) {
            ProcessDiagramDTO diagram = workflowQueryApi.getProcessNodes(row.getProcessInstanceId());
            vo.setProcessNodes(diagram == null ? List.of() : diagram.getNodes());
            vo.setApprovalLogs(workflowQueryApi.getProcessHistory(row.getProcessInstanceId()));
            if (diagram != null && diagram.getNodes() != null) {
                diagram.getNodes().stream().filter(n -> "ACTIVE".equals(n.getStatus()) && "userTask".equals(n.getNodeType()))
                        .findFirst().ifPresent(node -> {
                            vo.setCurrentNode(node.getNodeName()); vo.setCurrentProcessor(node.getAssigneeName());
                            vo.setWorkflowTaskId(node.getTaskId());
                        });
            }
        }
    }

    private void applyTask(AssetProjectVO vo, TaskRespDTO task) {
        vo.setCurrentNode(task.getTaskName()); vo.setCurrentProcessor(task.getAssignee()); vo.setWorkflowTaskId(task.getTaskId());
    }

    private void copyDraftFields(AssetProjectApply entity, AssetProjectSaveRequest request, MarketingCustomerInfo customer) {
        entity.setCustId(request.getCustId()); entity.setSourceTouchTaskId(request.getSourceTouchTaskId());
        entity.setSourceWorklogId(request.getSourceWorklogId()); entity.setProjectName(trim(request.getProjectName()));
        entity.setProjectType(trim(request.getProjectType())); entity.setBizType(trim(request.getBizType()));
        entity.setGuaranteeType(trim(request.getGuaranteeType())); entity.setProjectTotalInvestment(request.getProjectTotalInvestment());
        entity.setProjectLoanAmount(request.getProjectLoanAmount()); entity.setCreditAmount(request.getCreditAmount());
        entity.setCreditExposureAmount(request.getCreditExposureAmount()); entity.setIsUrgent(Boolean.TRUE.equals(request.getUrgent()) ? 1 : 0);
        entity.setIsKeyProject(Boolean.TRUE.equals(request.getKeyProject()) ? 1 : 0);
        entity.setUrgentSource(Boolean.TRUE.equals(request.getUrgent()) ? "INITIATION" : null);
        entity.setMainManagerIdSnapshot(customer.getMainManagerId()); entity.setMainOrgIdSnapshot(customer.getMainOrgId());
    }

    private MarketingCustomerInfo validateCustomerAndSource(AssetProjectSaveRequest request, String empId) {
        MarketingCustomerInfo customer = customerMapper.selectActiveById(request.getCustId());
        if (customer == null) throw error(CustomerErrorCode.CUSTOMER_NOT_FOUND);
        if (request.getSourceTouchTaskId() == null && request.getSourceWorklogId() != null) {
            throw error(CustomerErrorCode.ASSET_PROJECT_SOURCE_INVALID);
        }
        if (request.getSourceTouchTaskId() != null) {
            TouchTask task = touchTaskMapper.selectById(String.valueOf(request.getSourceTouchTaskId()));
            if (task == null || !request.getCustId().equals(task.getCustId()) || !empId.equals(task.getAssigneeEmpId())) {
                throw error(CustomerErrorCode.ASSET_PROJECT_SOURCE_INVALID);
            }
            if (request.getSourceWorklogId() != null) {
                TouchWorklog log = worklogMapper.selectById(request.getSourceWorklogId());
                if (log == null || !request.getSourceTouchTaskId().equals(log.getTaskId())
                        || !request.getCustId().equals(log.getCustId())) {
                    throw error(CustomerErrorCode.ASSET_PROJECT_SOURCE_INVALID);
                }
            }
        }
        return customer;
    }

    private void validateAmounts(AssetProjectSaveRequest request) {
        List<BigDecimal> nonNegative = Stream.of(request.getProjectTotalInvestment(), request.getProjectLoanAmount(),
                request.getCreditAmount(), request.getCreditExposureAmount()).filter(Objects::nonNull).toList();
        if (nonNegative.stream().anyMatch(value -> value.signum() < 0)
                || request.getProjectTotalInvestment() != null && request.getProjectLoanAmount() != null
                && request.getProjectLoanAmount().compareTo(request.getProjectTotalInvestment()) > 0
                || request.getCreditAmount() != null && request.getCreditExposureAmount() != null
                && request.getCreditExposureAmount().compareTo(request.getCreditAmount()) > 0) {
            throw new BizException(CustomerErrorCode.ASSET_PROJECT_INVALID.getCode(),
                    "金额必须非负，项目贷款金额不能超过项目总投资，授信敞口不能超过授信金额");
        }
    }

    private void validateRequiredForSubmit(AssetProjectApply entity) {
        if (!StringUtils.hasText(entity.getProjectName()) || !StringUtils.hasText(entity.getProjectType())
                || !StringUtils.hasText(entity.getBizType()) || !StringUtils.hasText(entity.getGuaranteeType())
                || entity.getProjectTotalInvestment() == null || entity.getProjectLoanAmount() == null
                || entity.getCreditAmount() == null || entity.getCreditExposureAmount() == null) {
            throw new BizException(CustomerErrorCode.ASSET_PROJECT_INVALID.getCode(), "项目、业务、担保及金额信息填写完整后方可提交");
        }
    }

    private AssetProjectSaveRequest toSaveRequest(AssetProjectApply entity) {
        AssetProjectSaveRequest request = new AssetProjectSaveRequest();
        request.setCustId(entity.getCustId()); request.setSourceTouchTaskId(entity.getSourceTouchTaskId());
        request.setSourceWorklogId(entity.getSourceWorklogId()); request.setProjectName(entity.getProjectName());
        request.setProjectType(entity.getProjectType()); request.setBizType(entity.getBizType());
        request.setGuaranteeType(entity.getGuaranteeType()); request.setProjectTotalInvestment(entity.getProjectTotalInvestment());
        request.setProjectLoanAmount(entity.getProjectLoanAmount()); request.setCreditAmount(entity.getCreditAmount());
        request.setCreditExposureAmount(entity.getCreditExposureAmount());
        return request;
    }

    private void bindAttachments(Long id, List<String> attachmentIds) {
        if (attachmentIds == null) return;
        attachmentIds.stream().filter(StringUtils::hasText).distinct()
                .forEach(fileId -> fileApi.bindFile(MAIN_BIZ_TYPE, String.valueOf(id), fileId, "ATTACHMENT"));
    }

    private AssetProjectApply requireActive(Long id) {
        AssetProjectApply entity = applyMapper.selectActiveById(id);
        if (entity == null) throw error(CustomerErrorCode.ASSET_PROJECT_NOT_FOUND);
        return entity;
    }

    private void requireDraftOwner(AssetProjectApply entity, String empId, boolean admin) {
        requireOwner(entity, empId, admin);
        if (!"DRAFT".equals(entity.getStatus())) throw error(CustomerErrorCode.ASSET_PROJECT_STATUS_CONFLICT);
    }

    private void requireOwner(AssetProjectApply entity, String empId, boolean admin) {
        if (!admin && !empId.equals(entity.getApplicantEmpId())) throw error(CustomerErrorCode.ASSET_PROJECT_ACCESS_FORBIDDEN);
    }

    private void ensureReadable(AssetProjectApply entity, String empId, boolean admin) {
        if (admin || empId.equals(entity.getApplicantEmpId())) return;
        Set<String> keys = workflowQueryApi.queryParticipatedBusinessKeys(empId, PROCESS_DEFINITION_KEY, 3650, 10000);
        if (keys == null || !keys.contains(entity.getBusinessKey())) throw error(CustomerErrorCode.ASSET_PROJECT_ACCESS_FORBIDDEN);
    }

    private ProcessDiagramNodeDTO singleActiveNode(String processInstanceId) {
        try {
            ProcessDiagramDTO diagram = workflowQueryApi.getProcessNodes(processInstanceId);
            List<ProcessDiagramNodeDTO> active = diagram == null || diagram.getNodes() == null ? List.of()
                    : diagram.getNodes().stream().filter(Objects::nonNull)
                    .filter(n -> "ACTIVE".equals(n.getStatus()) && "userTask".equals(n.getNodeType())).toList();
            if (active.size() != 1) throw error(CustomerErrorCode.ASSET_PROJECT_URGENT_NOT_ALLOWED);
            return active.get(0);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw error(CustomerErrorCode.WORKFLOW_ERROR);
        }
    }

    private StartProcessCmd startCmd(String bizType, String bizId, String businessKey, String title,
                                     String empId, String orgId) {
        StartProcessCmd cmd = new StartProcessCmd();
        cmd.setBizType(bizType); cmd.setBizId(bizId); cmd.setBusinessKey(businessKey);
        cmd.setProcessDefinitionKey(PROCESS_DEFINITION_KEY); cmd.setStartUser(empId); cmd.setStartOrgId(orgId); cmd.setTitle(title);
        return cmd;
    }

    private List<TaskRespDTO> workflowTasks(AssetProjectQuery query, String empId) {
        return switch (query.getTab()) {
            case "PENDING" -> workflowQueryApi.queryTodoList(empId, MAIN_BIZ_TYPE, query.getKeyword(), 1, 10000).getRecords();
            case "PROCESSED" -> workflowQueryApi.queryDoneList(empId, MAIN_BIZ_TYPE, query.getKeyword(), 1, 10000).getRecords();
            default -> List.of();
        };
    }

    private void normalizeQuery(AssetProjectQuery query) {
        String tab = StringUtils.hasText(query.getTab()) ? query.getTab().trim().toUpperCase() : "MY";
        if (!Set.of("MY", "PENDING", "PROCESSED").contains(tab)) tab = "MY";
        query.setTab(tab); query.setPageNo(Math.max(1, query.getPageNo() == null ? 1 : query.getPageNo()));
        query.setPageSize(Math.min(100, Math.max(1, query.getPageSize() == null ? 20 : query.getPageSize())));
    }

    private String generateNo(String prefix) {
        return prefix + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
    }

    private Long tryParseId(String value) {
        try { return value == null ? null : Long.valueOf(value); } catch (NumberFormatException ignored) { return null; }
    }

    private String trim(String value) { return value == null ? null : value.trim(); }
    private BizException error(CustomerErrorCode code) { return new BizException(code.getCode(), code.getMessage()); }
}
