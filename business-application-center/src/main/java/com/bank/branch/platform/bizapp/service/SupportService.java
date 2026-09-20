package com.bank.branch.platform.bizapp.service;

import com.bank.branch.platform.bizapp.api.converter.SupportRequestDTOConverter;
import com.bank.branch.platform.bizapp.api.dto.SupportRequestDTO;
import com.bank.branch.platform.bizapp.api.dto.SupportRequestListItemDTO;
import com.bank.branch.platform.bizapp.dto.resp.SubmitRespDTO;
import com.bank.branch.platform.bizapp.dto.resp.SupportRequestCreateRespDTO;
import com.bank.branch.platform.bizapp.entity.SupportRequest;
import com.bank.branch.platform.bizapp.enums.BizAppErrorCode;
import com.bank.branch.platform.bizapp.enums.SupportScenario;
import com.bank.branch.platform.bizapp.enums.SupportSourceType;
import com.bank.branch.platform.bizapp.enums.SupportStatus;
import com.bank.branch.platform.bizapp.event.SupportSubmittedEvent;
import com.bank.branch.platform.bizapp.mapper.SupportRequestMapper;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.common.security.context.DataScopeContext;
import com.bank.branch.platform.common.security.enums.DataScopeType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.customer.api.CustomerQueryApi;
import com.bank.branch.platform.customer.api.TouchTaskQueryApi;
import com.bank.branch.platform.customer.api.dto.TouchTaskDTO;
import com.bank.branch.platform.governance.api.FileApi;
import com.bank.branch.platform.portal.api.ProductApi;
import com.bank.branch.platform.workflow.api.WorkflowApi;
import com.bank.branch.platform.workflow.api.TodoQueryApi;
import com.bank.branch.platform.workflow.api.dto.StartProcessCmd;
import com.bank.branch.platform.workflow.api.dto.TaskRespDTO;
import com.bank.branch.platform.workflow.api.dto.WorkflowLaunchResp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 中台支持申请服务（发起侧视图）。
 * <p>
 * 提供创建（含自动拆单）、提交、撤回、删除草稿、查询等发起侧操作。
 * 场景A（产品直达）通过 SupportProductSplitService 拆单；
 * 场景B（部门承接）创建单条记录，需指定 supportDeptId。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupportService {

    private final SupportRequestMapper supportMapper;
    private final SupportScenarioRouter scenarioRouter;
    private final SupportProductSplitService splitService;
    private final BizStateMachine bizStateMachine;
    private final BizNoGenerator bizNoGenerator;
    private final WorkflowApi workflowApi;
    private final TodoQueryApi todoQueryApi;
    private final CustomerQueryApi customerQueryApi;
    private final ApplicationEventPublisher eventPublisher;
    private final SupportRequestDTOConverter supportRequestDTOConverter;
    /** 来源校验只依赖公开查询契约；可为空仅用于兼容旧单元测试构造。 */
    private final TouchTaskQueryApi touchTaskQueryApi;
    private final UserApi userApi;
    private final ProductApi productApi;
    private final SupportProcessLogService supportProcessLogService;
    private final FileApi fileApi;

    /**
     * 新契约入口：sourceType 放在末尾，避免破坏已发布的 7 参数调用方。
     */
    @Transactional
    public SupportRequestCreateRespDTO create(List<String> productIds, String custId,
                                               String sourceTouchTaskId, String otherDemand,
                                               String supportDeptId, String operatorEmpId,
                                               String orgCode, String sourceType) {
        return createInternal(productIds, custId, sourceTouchTaskId, otherDemand,
                supportDeptId, operatorEmpId, orgCode, sourceType, false, null, true);
    }

    /** 严格创建入口：仅传并行确认时的兼容重载，附件可省略。 */
    @Transactional
    public SupportRequestCreateRespDTO create(List<String> productIds, String custId,
                                               String sourceTouchTaskId, String otherDemand,
                                               String supportDeptId, String operatorEmpId,
                                               String orgCode, String sourceType,
                                               Boolean confirmParallel) {
        return create(productIds, custId, sourceTouchTaskId, otherDemand, supportDeptId,
                operatorEmpId, orgCode, sourceType, confirmParallel, null);
    }

    /** 严格创建入口：支持并行申请确认和创建页附件关联。 */
    @Transactional
    public SupportRequestCreateRespDTO create(List<String> productIds, String custId,
                                               String sourceTouchTaskId, String otherDemand,
                                               String supportDeptId, String operatorEmpId,
                                               String orgCode, String sourceType,
                                               Boolean confirmParallel, List<String> attachmentIds) {
        return createInternal(productIds, custId, sourceTouchTaskId, otherDemand,
                supportDeptId, operatorEmpId, orgCode, sourceType, confirmParallel,
                attachmentIds, true);
    }

    /** 新契约入口：支持 sourceType 置于首位的调用约定。 */
    @Transactional
    public SupportRequestCreateRespDTO create(String sourceType, List<String> productIds,
                                               String custId, String sourceTouchTaskId,
                                               String otherDemand, String supportDeptId,
                                               String operatorEmpId, String orgCode) {
        return createInternal(productIds, custId, sourceTouchTaskId, otherDemand,
                supportDeptId, operatorEmpId, orgCode, sourceType, false, null, true);
    }

    /** 严格创建入口（sourceType 首参兼容形式）：仅传并行确认时的重载。 */
    @Transactional
    public SupportRequestCreateRespDTO create(String sourceType, List<String> productIds,
                                               String custId, String sourceTouchTaskId,
                                               String otherDemand, String supportDeptId,
                                               String operatorEmpId, String orgCode,
                                               Boolean confirmParallel) {
        return create(sourceType, productIds, custId, sourceTouchTaskId, otherDemand,
                supportDeptId, operatorEmpId, orgCode, confirmParallel, null);
    }

    /** 严格创建入口（sourceType 首参兼容形式）：支持并行申请确认和附件关联。 */
    @Transactional
    public SupportRequestCreateRespDTO create(String sourceType, List<String> productIds,
                                               String custId, String sourceTouchTaskId,
                                               String otherDemand, String supportDeptId,
                                               String operatorEmpId, String orgCode,
                                               Boolean confirmParallel, List<String> attachmentIds) {
        return createInternal(productIds, custId, sourceTouchTaskId, otherDemand,
                supportDeptId, operatorEmpId, orgCode, sourceType, confirmParallel,
                attachmentIds, true);
    }

    /**
     * 创建中台支持申请（含自动拆单逻辑）。
     * <p>
     * 场景A：调用 splitService.splitByProducts() 创建多条 DRAFT 记录，同批共享 submitGroupId；
     * 场景B：创建单条 DRAFT 记录，supportDeptId 必填，自动生成 submitGroupId。
     * </p>
     *
     * @param productIds        产品ID列表（场景A使用）
     * @param custId            客户ID
     * @param sourceTouchTaskId 来源触达任务ID（可选）
     * @param otherDemand       其他需求说明（场景B使用）
     * @param supportDeptId     承接部门（场景B使用）
     * @param operatorEmpId     操作人工号
     * @param orgCode           归属机构编码
     * @return 创建响应 DTO，含 submitGroupId、productCount 及各条申请明细
     */
    @Transactional
    public SupportRequestCreateRespDTO create(List<String> productIds, String custId,
                                               String sourceTouchTaskId, String otherDemand,
                                               String supportDeptId, String operatorEmpId, String orgCode) {
        // 保留旧服务契约，但不能绕过来源/机构校验：未显式指定 sourceType 时按
        // EXISTING_CUSTOMER（有 sourceTouchTaskId 时按 TOUCH_TASK）处理。
        return createInternal(productIds, custId, sourceTouchTaskId, otherDemand,
                supportDeptId, operatorEmpId, orgCode,
                StringUtils.hasText(sourceTouchTaskId) ? SupportSourceType.TOUCH_TASK.getCode() : null,
                false, null, true);
    }

    private SupportRequestCreateRespDTO createInternal(List<String> productIds, String custId,
                                               String sourceTouchTaskId, String otherDemand,
                                               String supportDeptId, String operatorEmpId, String orgCode,
                                               String sourceType, Boolean confirmParallel,
                                               List<String> attachmentIds,
                                               boolean strictSourceValidation) {
        log.info("[SupportService.create] custId={}, operator={}", custId, operatorEmpId);

        // 1. 校验客户有效性
        if (customerQueryApi != null && !customerQueryApi.isValidCustomer(custId)) {
            throw new BizException(
                    BizAppErrorCode.CUSTOMER_NOT_VALID.getCode(),
                    BizAppErrorCode.CUSTOMER_NOT_VALID.getMessage()
            );
        }

        String normalizedSourceType = normalizeSourceType(sourceType, sourceTouchTaskId);
        if (strictSourceValidation) {
            validateSource(normalizedSourceType, sourceTouchTaskId, custId, operatorEmpId, orgCode);
            validateParallel(custId, confirmParallel);
        }

        // 2. 场景路由
        SupportScenario scenario = scenarioRouter.route(productIds, otherDemand, supportDeptId);

        // 3. 按场景创建并组装响应 DTO
        if (scenario == SupportScenario.A) {
            // 场景A：splitService 负责生成 submitGroupId 并写入每条 entity
            List<SupportRequest> entities =
                    splitService.splitByProducts(productIds, custId, sourceTouchTaskId, operatorEmpId, orgCode);

            if (entities != null) {
                entities.forEach(entity -> entity.setSourceType(normalizedSourceType));
                entities.forEach(entity -> bindAttachments(entity.getId(), attachmentIds));
            }

            // splitService 已保证同批记录共享同一 submitGroupId，取第一条即可
            String submitGroupId = entities.isEmpty() ? "" : entities.get(0).getSubmitGroupId();

            List<SupportRequestCreateRespDTO.CreatedItem> items = entities.stream()
                    .map(e -> SupportRequestCreateRespDTO.CreatedItem.builder()
                            .id(e.getId())
                            .requestNo(e.getRequestNo())
                            .productId(e.getProductId())
                            .scenario(SupportScenario.A.name())
                            .processInstanceId(null) // 草稿阶段尚未启动工作流
                            .build())
                    .collect(Collectors.toList());

            log.info("[SupportService.create] 场景A，创建 {} 条 SupportRequest，submitGroupId={}",
                    entities.size(), submitGroupId);

            return SupportRequestCreateRespDTO.builder()
                    .submitGroupId(submitGroupId)
                    .productCount(entities.size())
                    .requests(items)
                    .build();
        } else {
            // 场景B：创建单条记录，自行生成 submitGroupId
            String submitGroupId = UUID.randomUUID().toString().replace("-", "");
            LocalDateTime now = LocalDateTime.now();
            SupportRequest entity = new SupportRequest();
            entity.setId(UUID.randomUUID().toString().replace("-", ""));
            entity.setRequestNo(bizNoGenerator.generateSupportNo());
            entity.setSubmitGroupId(submitGroupId);
            entity.setCustId(custId);
            entity.setSourceTouchTaskId(sourceTouchTaskId);
            entity.setSourceType(normalizedSourceType);
            entity.setOtherDemand(otherDemand);
            entity.setSupportDeptId(supportDeptId);
            entity.setStatus(SupportStatus.DRAFT.getCode());
            // 统一 businessKey 格式 "SUPPORT:{id}"，与 BizStateMachine / SupportProductSplitService 保持一致
            String businessKey = "SUPPORT:" + entity.getId();
            entity.setBusinessKey(businessKey);
            entity.setOwnerOrgId(orgCode);
            entity.setCreatedBy(operatorEmpId);
            entity.setCreatedTime(now);
            entity.setUpdatedBy(operatorEmpId);
            entity.setUpdatedTime(now);
            entity.setDeleted(0);

            supportMapper.insert(entity);
            bindAttachments(entity.getId(), attachmentIds);
            log.info("[SupportService.create] 场景B，创建 SupportRequest id={}, submitGroupId={}",
                    entity.getId(), submitGroupId);

            SupportRequestCreateRespDTO.CreatedItem item = SupportRequestCreateRespDTO.CreatedItem.builder()
                    .id(entity.getId())
                    .requestNo(entity.getRequestNo())
                    .productId(null) // 场景B无 productId
                    .scenario(SupportScenario.B.name())
                    .processInstanceId(null) // 草稿阶段尚未启动工作流
                    .build();

            return SupportRequestCreateRespDTO.builder()
                    .submitGroupId(submitGroupId)
                    .productCount(1)
                    .requests(List.of(item))
                    .build();
        }
    }

    private String normalizeSourceType(String sourceType, String sourceTouchTaskId) {
        String normalized = StringUtils.hasText(sourceType)
                ? sourceType.trim().toUpperCase(java.util.Locale.ROOT)
                : (StringUtils.hasText(sourceTouchTaskId)
                ? SupportSourceType.TOUCH_TASK.getCode()
                : SupportSourceType.EXISTING_CUSTOMER.getCode());
        try {
            SupportSourceType.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new BizException(BizAppErrorCode.INVALID_DICT_VALUE.getCode(),
                    BizAppErrorCode.INVALID_DICT_VALUE.getMessage());
        }
        if (SupportSourceType.EXISTING_CUSTOMER.getCode().equals(normalized)
                && StringUtils.hasText(sourceTouchTaskId)) {
            throw new BizException(BizAppErrorCode.INVALID_DICT_VALUE.getCode(),
                    BizAppErrorCode.INVALID_DICT_VALUE.getMessage());
        }
        return normalized;
    }

    private void validateSource(String sourceType, String sourceTouchTaskId, String custId,
                                String operatorEmpId, String orgCode) {
        if (SupportSourceType.TOUCH_TASK.getCode().equals(sourceType)) {
            if (!StringUtils.hasText(sourceTouchTaskId) || touchTaskQueryApi == null) {
                throw new BizException(BizAppErrorCode.APPLY_NOT_FOUND.getCode(),
                        BizAppErrorCode.APPLY_NOT_FOUND.getMessage());
            }
            Optional<TouchTaskDTO> taskOpt = touchTaskQueryApi.getTouchTask(sourceTouchTaskId);
            if (taskOpt == null || taskOpt.isEmpty()) {
                throw new BizException(BizAppErrorCode.APPLY_NOT_FOUND.getCode(),
                        BizAppErrorCode.APPLY_NOT_FOUND.getMessage());
            }
            TouchTaskDTO task = taskOpt.get();
            if (!same(custId, task.getCustId())) {
                throw new BizException(BizAppErrorCode.TOUCH_TASK_CUSTOMER_MISMATCH.getCode(),
                        BizAppErrorCode.TOUCH_TASK_CUSTOMER_MISMATCH.getMessage());
            }
            if (!"SUCCESS".equalsIgnoreCase(task.getTaskStatus())) {
                throw new BizException(BizAppErrorCode.NOT_TOUCH_TASK_ASSIGNEE.getCode(),
                        BizAppErrorCode.NOT_TOUCH_TASK_ASSIGNEE.getMessage());
            }
            if (!same(operatorEmpId, task.getAssigneeEmpId())) {
                throw new BizException(BizAppErrorCode.NOT_TOUCH_TASK_ASSIGNEE.getCode(),
                        BizAppErrorCode.NOT_TOUCH_TASK_ASSIGNEE.getMessage());
            }
            return;
        }
        if (customerQueryApi == null || !customerQueryApi.isClaimedByOrg(custId, orgCode)) {
            throw new BizException(BizAppErrorCode.CUSTOMER_NOT_CLAIMED_BY_ORG.getCode(),
                    BizAppErrorCode.CUSTOMER_NOT_CLAIMED_BY_ORG.getMessage());
        }
    }

    /**
     * CD-09 并行申请确认：确认字段由客户端显式传递，不能仅依赖前端弹窗绕过。
     * 只要客户存在任一条在途支持申请，未确认就拒绝本次创建，并把当前数量返回给前端。
     */
    private void validateParallel(String custId, Boolean confirmParallel) {
        long runningCount = countRunningByCustomer(custId);
        if (runningCount > 0 && !Boolean.TRUE.equals(confirmParallel)) {
            throw new BizException(BizAppErrorCode.PARALLEL_APPLY_OVER_LIMIT.getCode(),
                    BizAppErrorCode.PARALLEL_APPLY_OVER_LIMIT.getMessage()
                            + "：当前在途申请数=" + runningCount + "，请确认是否并行发起");
        }
    }

    /** 暴露客户在途中台支持申请数，供创建防绕过校验及查询侧复用。 */
    @Transactional(readOnly = true)
    public long countRunningByCustomer(String custId) {
        if (!StringUtils.hasText(custId)) {
            return 0L;
        }
        return supportMapper.countRunningByCustomer(custId);
    }

    /** 将创建页附件关联到拆单后的每条支持申请。 */
    private void bindAttachments(String requestId, List<String> attachmentIds) {
        if (fileApi == null || !StringUtils.hasText(requestId) || attachmentIds == null) {
            return;
        }
        attachmentIds.stream()
                .filter(StringUtils::hasText)
                .distinct()
                .forEach(fileId -> fileApi.bindFile("SUPPORT_REQUEST", requestId, fileId, "ATTACHMENT"));
    }

    private boolean same(String left, String right) {
        return left != null && left.equals(right);
    }

    /**
     * 提交草稿（SELECT FOR UPDATE，validate DRAFT -> IN_APPROVAL，启动工作流）。
     * <p>
     * 场景A多拆单时，每条记录单独提交，每次 submit 只提交一条申请。
     * 返回的 processInstanceId 只反映本次被提交的那一条申请对应的流程实例。
     * </p>
     *
     * @param id            申请ID
     * @param operatorEmpId 操作人
     * @param orgCode       归属机构
     * @return 提交响应 DTO，含 processInstanceId、businessKey 和状态
     */
    @Transactional
    public SubmitRespDTO submit(String id, String operatorEmpId, String orgCode) {
        log.info("[SupportService.submit] id={}, operator={}", id, operatorEmpId);

        // SELECT FOR UPDATE 防并发
        SupportRequest request = supportMapper.selectForUpdate(id);
        if (request == null) {
            throw new BizException(
                    BizAppErrorCode.APPLY_NOT_FOUND.getCode(),
                    BizAppErrorCode.APPLY_NOT_FOUND.getMessage()
            );
        }

        // 权限校验：只有创建人可以提交
        if (!operatorEmpId.equals(request.getCreatedBy())) {
            throw new BizException(
                    BizAppErrorCode.NOT_APPLY_CREATOR.getCode(),
                BizAppErrorCode.NOT_APPLY_CREATOR.getMessage()
            );
        }
        assertInitiatingWrite(request, operatorEmpId);

        // 状态迁移校验：DRAFT -> IN_APPROVAL
        bizStateMachine.validateSupportTransition(request.getStatus(), SupportStatus.IN_APPROVAL.getCode());

        // 确定场景（场景A：有productId且无supportDeptId；场景B：有supportDeptId）
        boolean isScenarioA = StringUtils.hasText(request.getProductId())
                && !StringUtils.hasText(request.getSupportDeptId());
        String processDefinitionKey = isScenarioA
                ? SupportScenario.A.getProcessDefinitionKey()
                : SupportScenario.B.getProcessDefinitionKey();

        // 候选变量必须在启动流程时一次性传入。流程配置分别读取
        // VAR:assignedEmpId（场景A）和 VAR:dispatchEmpIds（场景B）。
        Map<String, Object> variables = new HashMap<>();
        if (isScenarioA) {
            String assignedEmpId = request.getAssignedEmpId();
            if (!StringUtils.hasText(assignedEmpId) && productApi != null
                    && StringUtils.hasText(request.getProductId())) {
                List<String> responsibleEmpIds = productApi.getProductResponsibleEmpIds(request.getProductId());
                if (responsibleEmpIds != null && !responsibleEmpIds.isEmpty()) {
                    assignedEmpId = responsibleEmpIds.stream()
                            .filter(StringUtils::hasText)
                            .findFirst().orElse(null);
                    request.setAssignedEmpId(assignedEmpId);
                }
            }
            // 生产环境 productApi 一定存在；null 仅兼容早期没有负责人字段的单元夹具。
            if (productApi != null && !StringUtils.hasText(assignedEmpId)) {
                throw new BizException(BizAppErrorCode.NOT_SUPPORT_DEPT_MEMBER.getCode(),
                        "产品负责人不能为空");
            }
            if (StringUtils.hasText(assignedEmpId)) {
                variables.put("assignedEmpId", assignedEmpId);
                variables.put("assignedEmpIds", List.of(assignedEmpId));
            }
        } else {
            List<String> dispatchEmpIds = Collections.emptyList();
            if (userApi != null) {
                List<String> candidates = userApi.getEmpIdsByRoleCodeAndOrg("SUPPORT_SE",
                        request.getSupportDeptId());
                dispatchEmpIds = candidates == null ? Collections.emptyList() : candidates.stream()
                        .filter(StringUtils::hasText).distinct().toList();
                if (dispatchEmpIds.isEmpty()) {
                    throw new BizException(BizAppErrorCode.NOT_SUPPORT_DEPT_MEMBER.getCode(),
                            "承接部门秘书候选人不能为空");
                }
            }
            variables.put("dispatchEmpIds", dispatchEmpIds);
            // 兼容候选组解析器的通用变量名，WF_NODE_CANDIDATE_CONF 以具体变量为准。
            variables.put("candidateEmpIds", dispatchEmpIds);
        }

        // businessKey 统一定义一次，后续 cmd 和 SubmitRespDTO 共用。
        String businessKey = "SUPPORT:" + id;

        // 启动工作流
        StartProcessCmd cmd = new StartProcessCmd();
        cmd.setBizType("SUPPORT");
        cmd.setBizId(id);
        cmd.setBusinessKey(businessKey);
        cmd.setProcessDefinitionKey(processDefinitionKey);
        cmd.setStartUser(operatorEmpId);
        cmd.setStartOrgId(orgCode);
        cmd.setTitle("中台支持申请-" + request.getRequestNo());
        cmd.setVariables(variables);

        WorkflowLaunchResp resp = workflowApi.startProcess(cmd);

        // 更新申请状态
        request.setStatus(SupportStatus.IN_APPROVAL.getCode());
        request.setProcessInstanceId(resp.getProcessInstanceId());
        request.setUpdatedBy(operatorEmpId);
        request.setUpdatedTime(LocalDateTime.now());
        supportMapper.updateById(request);

        // 发布提交事件
        eventPublisher.publishEvent(new SupportSubmittedEvent(
                id, request.getRequestNo(), request.getCustId(),
                request.getProductId(), request.getOwnerOrgId(), operatorEmpId
        ));

        log.info("[SupportService.submit] 申请 {} 已提交工作流，processInstanceId={}",
                id, resp.getProcessInstanceId());

        // 返回提交响应（含 processInstanceId，供前端跳转流程详情页）
        return new SubmitRespDTO(resp.getProcessInstanceId(), businessKey, SupportStatus.IN_APPROVAL.getCode());
    }

    /**
     * 撤回申请（IN_APPROVAL 或 IN_PROGRESS -> CANCELLED）。
     *
     * @param id            申请ID
     * @param operatorEmpId 操作人
     */
    @Transactional
    public void cancel(String id, String operatorEmpId) {
        // 旧 Java 调用方没有理由参数；新 REST 请求应使用三参方法。
        cancel(id, operatorEmpId, "用户撤回");
    }

    /** 撤回申请；只有创建人可以撤回，且工作流实例必须同步取消。 */
    @Transactional
    public void cancel(String id, String operatorEmpId, String reason) {
        log.info("[SupportService.cancel] id={}, operator={}", id, operatorEmpId);

        SupportRequest request = supportMapper.selectForUpdate(id);
        if (request == null) {
            throw new BizException(
                    BizAppErrorCode.APPLY_NOT_FOUND.getCode(),
                BizAppErrorCode.APPLY_NOT_FOUND.getMessage()
            );
        }

        assertInitiatingWrite(request, operatorEmpId);
        if (workflowApi != null && !StringUtils.hasText(reason)) {
            throw new BizException(BizAppErrorCode.CANCEL_REASON_REQUIRED.getCode(),
                    BizAppErrorCode.CANCEL_REASON_REQUIRED.getMessage());
        }

        bizStateMachine.validateSupportTransition(request.getStatus(), SupportStatus.CANCELLED.getCode());

        if (workflowApi != null && StringUtils.hasText(request.getProcessInstanceId())) {
            workflowApi.cancelProcess(request.getProcessInstanceId(), reason);
        }

        request.setStatus(SupportStatus.CANCELLED.getCode());
        request.setUpdatedBy(operatorEmpId);
        request.setUpdatedTime(LocalDateTime.now());
        supportMapper.updateById(request);

        log.info("[SupportService.cancel] 申请 {} 已撤回", id);
    }

    /**
     * 删除草稿（软删除）。
     *
     * @param id            申请ID
     * @param operatorEmpId 操作人
     */
    @Transactional
    public void deleteDraft(String id, String operatorEmpId) {
        log.info("[SupportService.deleteDraft] id={}, operator={}", id, operatorEmpId);

        SupportRequest request = supportMapper.selectForUpdate(id);
        if (request == null) {
            throw new BizException(
                    BizAppErrorCode.APPLY_NOT_FOUND.getCode(),
                BizAppErrorCode.APPLY_NOT_FOUND.getMessage()
            );
        }

        assertInitiatingWrite(request, operatorEmpId);

        // 只允许草稿状态删除
        if (!SupportStatus.DRAFT.getCode().equals(request.getStatus())) {
            throw new BizException(
                    BizAppErrorCode.NOT_DRAFT_STATUS.getCode(),
                    BizAppErrorCode.NOT_DRAFT_STATUS.getMessage()
            );
        }

        request.setDeleted(1);
        request.setUpdatedBy(operatorEmpId);
        request.setUpdatedTime(LocalDateTime.now());
        supportMapper.updateById(request);

        log.info("[SupportService.deleteDraft] 申请 {} 已删除", id);
    }

    /**
     * 按ID查询申请。
     *
     * @param id 申请ID
     * @return 申请实体
     */
    public SupportRequest getById(String id) {
        SupportRequest request = supportMapper.selectById(id);
        if (request == null) {
            throw new BizException(
                    BizAppErrorCode.APPLY_NOT_FOUND.getCode(),
                BizAppErrorCode.APPLY_NOT_FOUND.getMessage()
            );
        }
        DataScopeContext ctx = DataScopeContext.current();
        if (ctx != null && ctx.getScope() != null && !inInitiatingScope(request, ctx)) {
            throw new BizException(BizAppErrorCode.NOT_APPLY_CREATOR.getCode(),
                    BizAppErrorCode.NOT_APPLY_CREATOR.getMessage());
        }
        return request;
    }

    /**
     * 发起侧分页查询（返回 Entity，供内部使用）。
     *
     * @param keyword    关键词
     * @param status     状态筛选
     * @param ownerOrgId 归属机构
     * @param pageNo     页码
     * @param pageSize   每页大小
     * @return 分页结果（Entity）
     */
    public PageResult<SupportRequest> listPage(String keyword, String status,
                                                String ownerOrgId, int pageNo, int pageSize) {
        return listPage(keyword, status, ownerOrgId, pageNo, pageSize, false, null);
    }

    /**
     * 发起侧分页查询，并可由服务端强制限定当前创建人。
     *
     * <p>{@code onlyMine} 只接受 Controller 根据当前会话得到的员工号，调用方不能传入
     * 任意员工号。开启后仍保留当前机构/数据范围条件，并与创建人条件取交集。</p>
     *
     * @param keyword       关键词
     * @param status        状态筛选
     * @param ownerOrgId    归属机构
     * @param pageNo        页码
     * @param pageSize      每页大小
     * @param onlyMine      是否只查询当前创建人的申请
     * @param creatorEmpId  当前会话员工号，仅由服务端注入
     * @return 分页结果（Entity）
     */
    public PageResult<SupportRequest> listPage(String keyword, String status,
                                                String ownerOrgId, int pageNo, int pageSize,
                                                boolean onlyMine, String creatorEmpId) {
        log.info("[SupportService.listPage] ownerOrgId={}, pageNo={}, pageSize={}", ownerOrgId, pageNo, pageSize);

        DataScopeContext ctx = DataScopeContext.current();
        if (ctx != null && ctx.getScope() != null) {
            ownerOrgId = scopedOrg(ownerOrgId, ctx);
        }

        int offset = (pageNo - 1) * pageSize;
        if (onlyMine) {
            // onlyMine 缺少会话员工号时 fail close，不能退化为机构/全量查询。
            if (!StringUtils.hasText(creatorEmpId)) {
                return PageResult.of(pageNo, pageSize, 0L, Collections.emptyList());
            }
            List<SupportRequest> records = supportMapper.selectPageForSupportByCreator(
                    keyword, status, ownerOrgId, creatorEmpId, offset, pageSize);
            long total = supportMapper.countPageForSupportByCreator(
                    keyword, status, ownerOrgId, creatorEmpId);
            return PageResult.of(pageNo, pageSize, total, records);
        }

        List<SupportRequest> records;
        long total;
        if (ctx != null && (ctx.getScope() == DataScopeType.SELF_CREATED
                || ctx.getScope() == DataScopeType.SELF) && StringUtils.hasText(ctx.getEmpId())) {
            records = supportMapper.selectPageForSupportByCreator(keyword, status, ownerOrgId,
                    ctx.getEmpId(), offset, pageSize);
            total = supportMapper.countPageForSupportByCreator(keyword, status, ownerOrgId, ctx.getEmpId());
        } else if (ctx != null && ctx.getScope() == DataScopeType.ORG_SUBTREE
                && ctx.getOrgSubtreeCodes() != null && !ctx.getOrgSubtreeCodes().isEmpty()) {
            records = supportMapper.selectPageForSupportByOrgCodes(keyword, status,
                    ctx.getOrgSubtreeCodes(), offset, pageSize);
            total = supportMapper.countPageForSupportByOrgCodes(keyword, status, ctx.getOrgSubtreeCodes());
        } else {
            records = supportMapper.selectPageForSupport(keyword, status, ownerOrgId, offset, pageSize);
            total = supportMapper.countPageForSupport(keyword, status, ownerOrgId);
        }

        return PageResult.of(pageNo, pageSize, total, records);
    }

    private String scopedOrg(String requestedOrg, DataScopeContext ctx) {
        if (ctx.getScope() == DataScopeType.ALL || ctx.getScope() == DataScopeType.SELF_CREATED
                || ctx.getScope() == DataScopeType.SELF) {
            return StringUtils.hasText(ctx.getOrgCode()) ? ctx.getOrgCode() : requestedOrg;
        }
        if (ctx.getScope() == DataScopeType.ORG || ctx.getScope() == DataScopeType.ORG_SUBTREE) {
            return ctx.getOrgCode();
        }
        return requestedOrg;
    }

    private void assertInitiatingWrite(SupportRequest request, String operatorEmpId) {
        if (!same(operatorEmpId, request.getCreatedBy())) {
            throw new BizException(BizAppErrorCode.NOT_APPLY_CREATOR.getCode(),
                    BizAppErrorCode.NOT_APPLY_CREATOR.getMessage());
        }
        DataScopeContext ctx = DataScopeContext.current();
        if (ctx != null && ctx.getScope() != null && !inInitiatingScope(request, ctx)) {
            throw new BizException(BizAppErrorCode.NOT_APPLY_CREATOR.getCode(),
                    BizAppErrorCode.NOT_APPLY_CREATOR.getMessage());
        }
    }

    private boolean inInitiatingScope(SupportRequest request, DataScopeContext ctx) {
        DataScopeType scope = ctx.getScope();
        if (scope == DataScopeType.ALL) {
            return true;
        }
        if (scope == DataScopeType.SELF_CREATED || scope == DataScopeType.SELF
                || scope == DataScopeType.WORKFLOW_PARTICIPANT) {
            return same(ctx.getEmpId(), request.getCreatedBy());
        }
        if (scope == DataScopeType.ORG) {
            return same(ctx.getOrgCode(), request.getOwnerOrgId());
        }
        if (scope == DataScopeType.ORG_SUBTREE) {
            return ctx.getOrgSubtreeCodes() != null
                    && ctx.getOrgSubtreeCodes().contains(request.getOwnerOrgId());
        }
        return false;
    }

    /**
     * 发起侧分页查询（返回 ListItemDTO，供 REST 层使用）。
     * <p>
     * 通过 {@link SupportRequestDTOConverter#toListItems} 批量转换并填充展示字段，避免 N+1 查询。
     * REST 层禁止直接暴露 Entity，统一通过此方法获取列表数据。
     * </p>
     *
     * @param keyword    关键词
     * @param status     状态筛选
     * @param ownerOrgId 归属机构
     * @param pageNo     页码
     * @param pageSize   每页大小
     * @return 分页结果（ListItemDTO，不含 deleted 等内部字段）
     */
    public PageResult<SupportRequestListItemDTO> listPageAsDTO(String keyword, String status,
                                                               String ownerOrgId, int pageNo, int pageSize) {
        return listPageAsDTO(keyword, status, ownerOrgId, pageNo, pageSize, false, null);
    }

    /**
     * 发起侧分页查询 DTO 版本，并支持服务端固定的本人创建人过滤。
     *
     * @param keyword       关键词
     * @param status        状态筛选
     * @param ownerOrgId    归属机构
     * @param pageNo        页码
     * @param pageSize      每页大小
     * @param onlyMine      是否只查询当前创建人的申请
     * @param creatorEmpId  当前会话员工号，仅由服务端注入
     * @return 分页 DTO
     */
    public PageResult<SupportRequestListItemDTO> listPageAsDTO(String keyword, String status,
                                                               String ownerOrgId, int pageNo, int pageSize,
                                                               boolean onlyMine, String creatorEmpId) {
        PageResult<SupportRequest> page = listPage(keyword, status, ownerOrgId, pageNo, pageSize,
                onlyMine, creatorEmpId);
        List<SupportRequestListItemDTO> items = supportRequestDTOConverter.toListItems(page.getRecords());
        enrichActiveTaskMetadata(page.getRecords(), items);
        return PageResult.of(pageNo, pageSize, page.getTotal(), items);
    }

    private void enrichActiveTaskMetadata(List<SupportRequest> records,
                                          List<SupportRequestListItemDTO> items) {
        if (todoQueryApi == null || records == null || items == null || records.isEmpty()) {
            return;
        }
        List<String> processInstanceIds = records.stream()
                .map(SupportRequest::getProcessInstanceId)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
        if (processInstanceIds.isEmpty()) {
            return;
        }
        Map<String, TaskRespDTO> tasks = todoQueryApi
                .findActiveTaskRespByProcessInstanceIds(processInstanceIds);
        if (tasks == null || tasks.isEmpty()) {
            return;
        }
        for (int i = 0; i < Math.min(records.size(), items.size()); i++) {
            TaskRespDTO task = tasks.get(records.get(i).getProcessInstanceId());
            if (task != null) {
                items.get(i).setCurrentNodeName(task.getTaskName());
                items.get(i).setCurrentNodeKey(task.getNodeKey());
                items.get(i).setSlaStatus(task.getSlaStatus());
            }
        }
    }

    /**
     * 按ID查询申请并转为 DTO（供 REST 层使用）。
     * <p>
     * REST 层禁止直接暴露 Entity，通过此方法获取详情数据，不含 deleted 等内部字段。
     * </p>
     *
     * @param id 申请ID
     * @return 申请 DTO（含 custName/productName/supportDeptName 冗余字段）
     * @throws BizException BIZ-40401 如果不存在
     */
    public SupportRequestDTO getByIdAsDTO(String id) {
        SupportRequest entity = getById(id);
        return supportRequestDTOConverter.toDTO(entity);
    }
}
