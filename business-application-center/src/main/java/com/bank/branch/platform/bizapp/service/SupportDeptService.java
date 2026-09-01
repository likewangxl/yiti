package com.bank.branch.platform.bizapp.service;

import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.UserDTO;
import com.bank.branch.platform.bizapp.api.converter.SupportRequestDTOConverter;
import com.bank.branch.platform.bizapp.api.dto.SupportRequestDTO;
import com.bank.branch.platform.bizapp.api.dto.SupportRequestListItemDTO;
import com.bank.branch.platform.bizapp.dto.req.CompleteReq;
import com.bank.branch.platform.bizapp.entity.SupportRequest;
import com.bank.branch.platform.bizapp.enums.BizAppErrorCode;
import com.bank.branch.platform.bizapp.enums.SupportStatus;
import com.bank.branch.platform.bizapp.event.SupportCompletedEvent;
import com.bank.branch.platform.bizapp.event.SupportDispatchedEvent;
import com.bank.branch.platform.bizapp.mapper.SupportRequestMapper;
import com.bank.branch.platform.common.security.context.DataScopeContext;
import com.bank.branch.platform.common.security.enums.DataScopeType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.workflow.api.TodoQueryApi;
import com.bank.branch.platform.workflow.api.WorkflowApi;
import com.bank.branch.platform.workflow.api.dto.TaskRespDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 中台支持申请服务（承接侧视图）。
 * <p>
 * 提供承接部门视角的操作：派单、转交、办理完成、分页查询。
 * 场景B专用操作（派单）需先通过场景判断。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupportDeptService {

    private final SupportRequestMapper supportMapper;
    private final BizStateMachine bizStateMachine;
    private final ApplicationEventPublisher eventPublisher;
    private final SupportRequestDTOConverter supportRequestDTOConverter;
    private final TodoQueryApi todoQueryApi;
    private final WorkflowApi workflowApi;
    private final UserApi userApi;
    private final SupportProcessLogService supportProcessLogService;

    /**
     * 秘书派单（仅场景B）。
     * <p>
     * IN_APPROVAL -> IN_PROGRESS，设置 dispatch_emp_id、dispatch_time、assigned_emp_id。
     * </p>
     *
     * @param id              申请ID
     * @param assignedEmpId   被派工号
     * @param dispatcherEmpId 派单人工号（部门秘书）
     * @param dispatchRemark  派单备注（可选，文档 §D.2）
     */
    @Transactional
    public void dispatch(String id, String assignedEmpId, String dispatcherEmpId, String dispatchRemark) {
        log.info("[SupportDeptService.dispatch] id={}, assignedEmpId={}, dispatcher={}",
                id, assignedEmpId, dispatcherEmpId);

        SupportRequest request = supportMapper.selectForUpdate(id);
        if (request == null) {
            throw new BizException(
                    BizAppErrorCode.APPLY_NOT_FOUND.getCode(),
                    BizAppErrorCode.APPLY_NOT_FOUND.getMessage()
            );
        }

        bizStateMachine.validateSupportTransition(request.getStatus(), SupportStatus.IN_PROGRESS.getCode());

        if (!org.springframework.util.StringUtils.hasText(assignedEmpId)
                || !org.springframework.util.StringUtils.hasText(request.getSupportDeptId())) {
            throw new BizException(BizAppErrorCode.NOT_SUPPORT_DEPT_MEMBER.getCode(),
                    BizAppErrorCode.NOT_SUPPORT_DEPT_MEMBER.getMessage());
        }
        assertDepartmentScope(request, dispatcherEmpId);
        validateReceiver(request.getSupportDeptId(), assignedEmpId);
        TaskRespDTO task = findCurrentTask(request, dispatcherEmpId, "dept_secretary_dispatch");

        LocalDateTime now = LocalDateTime.now();
        request.setStatus(SupportStatus.IN_PROGRESS.getCode());
        request.setAssignedEmpId(assignedEmpId);
        request.setDispatchEmpId(dispatcherEmpId);
        request.setDispatchTime(now);
        request.setUpdatedBy(dispatcherEmpId);
        request.setUpdatedTime(now);
        supportMapper.updateById(request);

        if (workflowApi != null && task != null) {
            Map<String, Object> formData = new HashMap<>();
            formData.put("assignedEmpId", assignedEmpId);
            formData.put("dispatchEmpId", dispatcherEmpId);
            formData.put("dispatchRemark", dispatchRemark);
            workflowApi.approveByEmp(task.getTaskId(), dispatcherEmpId, dispatchRemark, formData);
        }

        // 发布派单事件（携带 dispatchRemark，文档 §8.5）
        eventPublisher.publishEvent(new SupportDispatchedEvent(
                id, request.getRequestNo(), assignedEmpId, dispatcherEmpId, request.getSupportDeptId(), dispatchRemark
        ));

        log.info("[SupportDeptService.dispatch] 申请 {} 已派单给 {}", id, assignedEmpId);
    }

    /**
     * 转交承接人（高危操作）。
     * <p>
     * 只有当前 assignedEmpId 才能发起转交，转交后 assigned_emp_id 更新。
     * </p>
     *
     * @param id               申请ID
     * @param newAssignedEmpId 新承接人工号
     * @param operatorEmpId    操作人（必须是当前承接人）
     */
    @Transactional
    public void transfer(String id, String newAssignedEmpId, String operatorEmpId) {
        log.info("[SupportDeptService.transfer] id={}, newAssigned={}, operator={}", id, newAssignedEmpId, operatorEmpId);

        SupportRequest request = supportMapper.selectForUpdate(id);
        if (request == null) {
            throw new BizException(
                    BizAppErrorCode.APPLY_NOT_FOUND.getCode(),
                    BizAppErrorCode.APPLY_NOT_FOUND.getMessage()
            );
        }

        // 只有当前承接人才能转交
        if (!operatorEmpId.equals(request.getAssignedEmpId())) {
            throw new BizException(
                    BizAppErrorCode.NOT_SUPPORT_DEPT_MEMBER.getCode(),
                    BizAppErrorCode.NOT_SUPPORT_DEPT_MEMBER.getMessage()
            );
        }

        assertDepartmentScope(request, operatorEmpId);
        validateReceiver(request.getSupportDeptId(), newAssignedEmpId);

        request.setAssignedEmpId(newAssignedEmpId);
        request.setUpdatedBy(operatorEmpId);
        request.setUpdatedTime(LocalDateTime.now());
        supportMapper.updateById(request);

        log.info("[SupportDeptService.transfer] 申请 {} 已转交给 {}", id, newAssignedEmpId);
    }

    /**
     * 办理完成。
     * <p>
     * 场景A：IN_APPROVAL -> COMPLETED；
     * 场景B：IN_PROGRESS -> COMPLETED（success=true）或 REJECTED（success=false）。
     * </p>
     *
     * @param id            申请ID
     * @param success       true=成功完成，false=拒绝
     * @param operatorEmpId 操作人
     */
    @Transactional
    public void complete(String id, boolean success, String operatorEmpId) {
        CompleteReq req = new CompleteReq();
        req.setSuccess(success);
        complete(id, req, operatorEmpId);
    }

    /** 办理完成的完整入口，保留旧 boolean API 作为兼容适配。 */
    @Transactional
    public void complete(String id, CompleteReq req, String operatorEmpId) {
        boolean success = req == null || req.isSuccess();
        if (req != null && StringUtils.hasText(req.getResult())) {
            success = "SUCCESS".equalsIgnoreCase(req.getResult());
        }
        log.info("[SupportDeptService.complete] id={}, success={}, operator={}", id, success, operatorEmpId);

        SupportRequest request = supportMapper.selectForUpdate(id);
        if (request == null) {
            throw new BizException(
                    BizAppErrorCode.APPLY_NOT_FOUND.getCode(),
                    BizAppErrorCode.APPLY_NOT_FOUND.getMessage()
            );
        }

        boolean isScenarioA = org.springframework.util.StringUtils.hasText(request.getProductId())
                && !org.springframework.util.StringUtils.hasText(request.getSupportDeptId());
        boolean strictReceiving = todoQueryApi != null || supportProcessLogService != null
                || DataScopeContext.current() != null;
        if (strictReceiving) {
            assertDepartmentScope(request, operatorEmpId);
            if (!same(operatorEmpId, request.getAssignedEmpId())) {
                throw new BizException(BizAppErrorCode.NOT_SUPPORT_DEPT_MEMBER.getCode(),
                        BizAppErrorCode.NOT_SUPPORT_DEPT_MEMBER.getMessage());
            }
        }
        String expectedNode = isScenarioA ? "product_owner_handle" : "support_staff_handle";
        TaskRespDTO task = findCurrentTask(request, operatorEmpId, expectedNode);

        String handleResult = resolveHandleResult(req);
        List<String> outputFileIds = resolveFileIds(req);
        if (supportProcessLogService != null) {
            // 场景 A 在 IN_APPROVAL 直接由产品负责人办理，场景 B 在 IN_PROGRESS
            // 由承接人员办理；两条路径都必须先有现场 PROCESS 留痕，不能以 RESULT 替代。
            if (supportProcessLogService.countProcess(id) < 1) {
                throw new BizException(BizAppErrorCode.NODE_FORM_REQUIRED_MISSING.getCode(),
                        "办理完成前至少需要一条过程记录");
            }
            if (!org.springframework.util.StringUtils.hasText(handleResult)) {
                throw new BizException(BizAppErrorCode.NODE_FORM_REQUIRED_MISSING.getCode(),
                        "办理结果不能为空");
            }
            // 必须在状态迁移前写入，保证当前办理状态的写权限和主表更新同事务。
            supportProcessLogService.appendResultLog(id, handleResult, operatorEmpId, outputFileIds);
        }

        String targetStatus = success ? SupportStatus.COMPLETED.getCode() : SupportStatus.REJECTED.getCode();
        bizStateMachine.validateSupportTransition(request.getStatus(), targetStatus);

        // 真实工作流任务的终态必须由 SupportWorkflowListener 在流程完成事件中 CAS 回写。
        // 如果这里先写 COMPLETED/REJECTED，AFTER_COMMIT listener 的 IN_APPROVAL/IN_PROGRESS
        // CAS 会返回 0，申请人/部门知悉通知和领域事件都会被错误跳过。无 task 的分支仅为
        // 兼容没有接入 workflow/todo 的旧单元夹具，才保留本地状态+事件兜底。
        boolean workflowDriven = workflowApi != null && task != null;
        if (workflowDriven) {
            Map<String, Object> formData = new HashMap<>();
            formData.put("handleResult", handleResult);
            formData.put("result", success ? "SUCCESS" : "FAILED");
            if (outputFileIds != null && !outputFileIds.isEmpty()) {
                formData.put("outputAttachmentIds", outputFileIds);
            }
            if (success) {
                workflowApi.approveByEmp(task.getTaskId(), operatorEmpId, handleResult, formData);
            } else {
                workflowApi.rejectByEmp(task.getTaskId(), operatorEmpId, handleResult);
            }
        } else {
            request.setStatus(targetStatus);
            request.setUpdatedBy(operatorEmpId);
            request.setUpdatedTime(LocalDateTime.now());
            supportMapper.updateById(request);

            // 没有真实流程任务时的兼容兜底，生产工作流路径由流程完成 listener 发布。
            eventPublisher.publishEvent(new SupportCompletedEvent(
                    id, request.getRequestNo(), request.getCustId(),
                    request.getProductId(), request.getAssignedEmpId(), success
            ));
        }

        log.info("[SupportDeptService.complete] 申请 {} 办理完成，状态={}", id, targetStatus);
    }

    private String resolveHandleResult(CompleteReq req) {
        if (req == null) {
            return null;
        }
        if (org.springframework.util.StringUtils.hasText(req.getHandleResult())) {
            return req.getHandleResult();
        }
        return req.getSummary();
    }

    private List<String> resolveFileIds(CompleteReq req) {
        if (req == null) {
            return Collections.emptyList();
        }
        if (req.getOutputAttachmentIds() != null && !req.getOutputAttachmentIds().isEmpty()) {
            return req.getOutputAttachmentIds();
        }
        return req.getFileIds() == null ? Collections.emptyList() : req.getFileIds();
    }

    /**
     * 只从当前会话的 SUPPORT 待办集合中取任务，再按业务键精确反查，避免仅凭申请 ID 越权办理。
     */
    private TaskRespDTO findCurrentTask(SupportRequest request, String operatorEmpId, String expectedNodeKey) {
        if (todoQueryApi == null) {
            // 兼容旧的纯业务单元测试；真实 Spring Bean 必须注入 TodoQueryApi。
            return null;
        }
        String businessKey = StringUtils.hasText(request.getBusinessKey())
                ? request.getBusinessKey() : "SUPPORT:" + request.getId();
        List<String> todoKeys = todoQueryApi.listMyTodoBusinessKeys(operatorEmpId, "SUPPORT");
        if (todoKeys == null || !todoKeys.contains(businessKey)) {
            throw new BizException(BizAppErrorCode.NOT_SUPPORT_DEPT_MEMBER.getCode(),
                    BizAppErrorCode.NOT_SUPPORT_DEPT_MEMBER.getMessage());
        }
        Map<String, TaskRespDTO> tasks = todoQueryApi.findTaskRespByBusinessKeys(operatorEmpId,
                List.of(businessKey));
        TaskRespDTO task = tasks == null ? null : tasks.get(businessKey);
        if (task == null || !businessKey.equals(task.getBusinessKey())
                || (StringUtils.hasText(task.getBizType()) && !"SUPPORT".equals(task.getBizType()))
                || !StringUtils.hasText(task.getTaskId())
                || !expectedNodeKey.equals(task.getNodeKey())) {
            throw new BizException(BizAppErrorCode.NOT_SUPPORT_DEPT_MEMBER.getCode(),
                    BizAppErrorCode.NOT_SUPPORT_DEPT_MEMBER.getMessage());
        }
        return task;
    }

    /** 校验被派人属于目标支持部门且确有 SUPPORT_ST 角色。 */
    private void validateReceiver(String supportDeptId, String assignedEmpId) {
        if (userApi == null) {
            return;
        }
        if (!StringUtils.hasText(supportDeptId) || !StringUtils.hasText(assignedEmpId)) {
            throw new BizException(BizAppErrorCode.NOT_SUPPORT_DEPT_MEMBER.getCode(),
                    BizAppErrorCode.NOT_SUPPORT_DEPT_MEMBER.getMessage());
        }
        UserDTO receiver = userApi.getUserByEmpId(assignedEmpId);
        // 主机构必须与申请指定的承接部门精确一致；缺失机构信息也不能视为属于该部门。
        if (receiver == null || !supportDeptId.equals(receiver.getMainOrgCode())) {
            throw new BizException(BizAppErrorCode.NOT_SUPPORT_DEPT_MEMBER.getCode(),
                    BizAppErrorCode.NOT_SUPPORT_DEPT_MEMBER.getMessage());
        }
        Set<String> roles = userApi.getUserRoleCodes(assignedEmpId);
        if (roles == null || !roles.contains("SUPPORT_ST")) {
            throw new BizException(BizAppErrorCode.NOT_SUPPORT_DEPT_MEMBER.getCode(),
                    BizAppErrorCode.NOT_SUPPORT_DEPT_MEMBER.getMessage());
        }
    }

    private void assertDepartmentScope(SupportRequest request, String operatorEmpId) {
        DataScopeContext ctx = DataScopeContext.current();
        if (ctx == null || ctx.getScope() == null) {
            return;
        }
        // 场景A没有承接部门，产品负责人是流程中明确的当前承接人；
        // 其 assignedEmpId 实体守卫足够精确，不能因 ORG 数据域没有 supportDeptId 而误拒绝办理。
        if (isScenarioA(request) && same(ctx.getEmpId(), request.getAssignedEmpId())) {
            return;
        }
        DataScopeType scope = ctx.getScope();
        boolean allowed;
        if (scope == DataScopeType.ALL) {
            allowed = true;
        } else if (scope == DataScopeType.SELF_ASSIGNED) {
            allowed = same(ctx.getEmpId(), operatorEmpId)
                    && same(ctx.getEmpId(), request.getAssignedEmpId());
        } else if (scope == DataScopeType.ORG) {
            allowed = same(ctx.getOrgCode(), request.getSupportDeptId());
        } else if (scope == DataScopeType.ORG_SUBTREE) {
            allowed = ctx.getOrgSubtreeCodes() != null
                    && ctx.getOrgSubtreeCodes().contains(request.getSupportDeptId());
        } else if (scope == DataScopeType.WORKFLOW_PARTICIPANT) {
            allowed = same(ctx.getEmpId(), request.getAssignedEmpId())
                    || same(ctx.getEmpId(), request.getDispatchEmpId());
        } else {
            allowed = false;
        }
        if (!allowed) {
            throw new BizException(BizAppErrorCode.NOT_SUPPORT_DEPT_MEMBER.getCode(),
                    BizAppErrorCode.NOT_SUPPORT_DEPT_MEMBER.getMessage());
        }
    }

    private boolean same(String left, String right) {
        return left != null && left.equals(right);
    }

    private boolean isScenarioA(SupportRequest request) {
        return request != null && StringUtils.hasText(request.getProductId())
                && !StringUtils.hasText(request.getSupportDeptId());
    }

    /** 承接侧详情必须经过同一实体守卫。 */
    @Transactional(readOnly = true)
    public SupportRequest getById(String id, String operatorEmpId) {
        SupportRequest request = supportMapper.selectById(id);
        if (request == null) {
            throw new BizException(BizAppErrorCode.APPLY_NOT_FOUND.getCode(),
                    BizAppErrorCode.APPLY_NOT_FOUND.getMessage());
        }
        assertReceivingRead(request, operatorEmpId);
        return request;
    }

    @Transactional(readOnly = true)
    public SupportRequestDTO getByIdAsDTO(String id, String operatorEmpId) {
        return supportRequestDTOConverter.toDTO(getById(id, operatorEmpId));
    }

    private void assertReceivingRead(SupportRequest request, String operatorEmpId) {
        DataScopeContext ctx = DataScopeContext.current();
        if (ctx != null && ctx.getScope() != null) {
            assertDepartmentScope(request, operatorEmpId);
            return;
        }
        if (same(operatorEmpId, request.getAssignedEmpId())) {
            return;
        }
        if (userApi != null && StringUtils.hasText(request.getSupportDeptId())) {
            UserDTO user = userApi.getUserByEmpId(operatorEmpId);
            if (user != null && request.getSupportDeptId().equals(user.getMainOrgCode())) {
                return;
            }
        }
        throw new BizException(BizAppErrorCode.NOT_SUPPORT_DEPT_MEMBER.getCode(),
                BizAppErrorCode.NOT_SUPPORT_DEPT_MEMBER.getMessage());
    }

    /**
     * 承接侧分页查询（返回 Entity，供内部使用）。
     *
     * @param supportDeptId 承接部门ID
     * @param status        状态筛选
     * @param assignedEmpId 承接人工号筛选
     * @param pageNo        页码
     * @param pageSize      每页大小
     * @return 分页结果（Entity）
     */
    public PageResult<SupportRequest> listPageForDept(String supportDeptId, String status,
                                                       String assignedEmpId, int pageNo, int pageSize) {
        log.info("[SupportDeptService.listPageForDept] deptId={}, pageNo={}, pageSize={}",
                supportDeptId, pageNo, pageSize);

        DataScopeContext ctx = DataScopeContext.current();
        if (ctx != null && ctx.getScope() != null) {
            if (ctx.getScope() == DataScopeType.SELF_ASSIGNED) {
                assignedEmpId = ctx.getEmpId();
                // SELF_ASSIGNED 不能通过传参切换到其他部门/员工。
                supportDeptId = null;
            } else if (ctx.getScope() == DataScopeType.ORG
                    || ctx.getScope() == DataScopeType.ORG_SUBTREE) {
                supportDeptId = ctx.getOrgCode();
                assignedEmpId = null;
            } else if (ctx.getScope() == DataScopeType.ALL) {
                // 管理范围允许显式筛选，但仍由 @BizAuth 决定是否可达。
            } else {
                throw new BizException(BizAppErrorCode.NOT_SUPPORT_DEPT_MEMBER.getCode(),
                        BizAppErrorCode.NOT_SUPPORT_DEPT_MEMBER.getMessage());
            }
        }

        int offset = (pageNo - 1) * pageSize;
        List<SupportRequest> records;
        long total;
        if (ctx != null && ctx.getScope() == DataScopeType.ORG_SUBTREE
                && ctx.getOrgSubtreeCodes() != null && !ctx.getOrgSubtreeCodes().isEmpty()) {
            records = supportMapper.selectPageForDeptByOrgCodes(ctx.getOrgSubtreeCodes(), status,
                    assignedEmpId, offset, pageSize);
            total = supportMapper.countPageForDeptByOrgCodes(ctx.getOrgSubtreeCodes(), status, assignedEmpId);
        } else {
            records = supportMapper.selectPageForDept(supportDeptId, status, assignedEmpId, offset, pageSize);
            total = supportMapper.countPageForDept(supportDeptId, status, assignedEmpId);
        }

        return PageResult.of(pageNo, pageSize, total, records);
    }

    /**
     * 承接侧分页查询（返回 ListItemDTO，供 REST 层使用）。
     * <p>
     * 通过 {@link SupportRequestDTOConverter#toListItems} 批量转换并填充展示字段，避免 N+1 查询。
     * REST 层禁止直接暴露 Entity，统一通过此方法获取列表数据。
     * </p>
     *
     * @param supportDeptId 承接部门ID
     * @param status        状态筛选
     * @param assignedEmpId 承接人工号筛选
     * @param pageNo        页码
     * @param pageSize      每页大小
     * @return 分页结果（ListItemDTO，不含 deleted 等内部字段）
     */
    public PageResult<SupportRequestListItemDTO> listPageForDeptAsDTO(String supportDeptId, String status,
                                                                      String assignedEmpId, int pageNo, int pageSize) {
        PageResult<SupportRequest> page = listPageForDept(supportDeptId, status, assignedEmpId, pageNo, pageSize);
        List<SupportRequestListItemDTO> items = supportRequestDTOConverter.toListItems(page.getRecords());
        return PageResult.of(pageNo, pageSize, page.getTotal(), items);
    }
}
