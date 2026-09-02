package com.bank.branch.platform.redengine.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.redengine.api.dto.ReTaskApproveReqDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskRejectReqDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskSubmissionReqDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskNature;
import com.bank.branch.platform.redengine.api.dto.ReTaskWorkflowActionRespDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskWorkflowAssignmentDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskWorkflowPageQueryDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskWorkflowTab;
import com.bank.branch.platform.redengine.service.ReTaskWorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 任务报送、支部审核和组织审核 REST 入口。
 *
 * <p>为兼容已交付前端，审核工作台同时暴露 {@code /api/re/reviews/tasks/...} 路径；
 * 业务实现仍统一落到同一个自管状态机。每个公开方法都声明 {@link BizAuth}，
 * 服务层还会基于 assignment 实体重复执行数据范围校验。</p>
 */
@Slf4j
@Tag(name = "红色引擎-任务工作流")
@RestController
@RequestMapping("/api/re")
@RequiredArgsConstructor
public class ReTaskWorkflowController {

    private final ReTaskWorkflowService workflowService;
    private final CurrentUserApi currentUserApi;

    /** 将前端兼容的 PERIODIC 查询值归一为任务域的 SCHEDULED 枚举。 */
    @InitBinder
    void bindWorkflowEnums(WebDataBinder binder) {
        binder.registerCustomEditor(ReTaskNature.class,
                new java.beans.PropertyEditorSupport() {
                    @Override
                    public void setAsText(String text) {
                        setValue(ReTaskNature.fromValue(text));
                    }
                });
        binder.registerCustomEditor(ReTaskWorkflowTab.class,
                new java.beans.PropertyEditorSupport() {
                    @Override
                    public void setAsText(String text) {
                        setValue(ReTaskWorkflowTab.fromValue(text));
                    }
                });
    }

    /** 报送员任务待办/已处理列表。 */
    @Operation(summary = "我的任务列表")
    @GetMapping("/tasks/my-assignments")
    @BizAuth(bizType = BizType.RED_ENGINE, action = BizAction.LIST)
    public ResponseWrapper<ReTaskWorkflowAssignmentDTO> listMyAssignments(
            @Valid @ModelAttribute ReTaskWorkflowPageQueryDTO query) {
        String operatorId = currentUserApi.getCurrentEmpId();
        PageResult<ReTaskWorkflowAssignmentDTO> result = workflowService.listMyAssignments(query, operatorId);
        return ResponseWrapper.page(result);
    }

    /** 报送员/审核员按 assignment 查询任务详情。 */
    @Operation(summary = "任务分配详情")
    @GetMapping("/tasks/assignments/{assignmentId}")
    @BizAuth(bizType = BizType.RED_ENGINE, action = BizAction.READ)
    public ResponseWrapper<ReTaskWorkflowAssignmentDTO> getAssignment(@PathVariable Long assignmentId) {
        String operatorId = currentUserApi.getCurrentEmpId();
        return ResponseWrapper.success(workflowService.getAssignment(assignmentId, operatorId));
    }

    /** 报送员提交或重新提交任务。 */
    @Operation(summary = "提交任务填报")
    @PostMapping("/tasks/submissions")
    @BizAuth(bizType = BizType.RED_ENGINE, action = BizAction.WRITE)
    @AuditLog(action = "RE_TASK_SUBMIT", resourceType = "RE_TASK_SUBMISSION")
    public ResponseWrapper<ReTaskWorkflowActionRespDTO> submit(
            @Valid @RequestBody ReTaskSubmissionReqDTO request) {
        String operatorId = currentUserApi.getCurrentEmpId();
        return ResponseWrapper.success(workflowService.submit(request, operatorId));
    }

    /** 支部书记审核队列；路径与前端角色工作台契约保持一致。 */
    @Operation(summary = "支部审核任务列表")
    @GetMapping({"/reviews/tasks/branch/queue", "/tasks/branch-review"})
    @BizAuth(bizType = BizType.RED_ENGINE, action = BizAction.LIST)
    public ResponseWrapper<ReTaskWorkflowAssignmentDTO> listBranchReviews(
            @Valid @ModelAttribute ReTaskWorkflowPageQueryDTO query) {
        String operatorId = currentUserApi.getCurrentEmpId();
        return ResponseWrapper.page(workflowService.listBranchReviews(query, operatorId));
    }

    /** 支部书记审核详情。 */
    @Operation(summary = "支部审核任务详情")
    @GetMapping({"/reviews/tasks/branch/{assignmentId}", "/tasks/branch-review/{assignmentId}"})
    @BizAuth(bizType = BizType.RED_ENGINE, action = BizAction.READ)
    public ResponseWrapper<ReTaskWorkflowAssignmentDTO> getBranchReview(
            @PathVariable Long assignmentId) {
        String operatorId = currentUserApi.getCurrentEmpId();
        return ResponseWrapper.success(workflowService.getBranchReview(assignmentId, operatorId));
    }

    /** 支部书记审核通过，但不自动越过单独提交至组织的步骤。 */
    @Operation(summary = "支部审核通过")
    @PostMapping({"/reviews/tasks/branch/{assignmentId}/approve", "/tasks/branch-review/{assignmentId}/approve"})
    @BizAuth(bizType = BizType.RED_ENGINE, action = BizAction.WRITE)
    @AuditLog(action = "RE_TASK_BRANCH_APPROVE", resourceType = "RE_TASK_SUBMISSION")
    public ResponseWrapper<ReTaskWorkflowActionRespDTO> approveBranch(
            @PathVariable Long assignmentId,
            @RequestBody(required = false) ReTaskApproveReqDTO request) {
        String operatorId = currentUserApi.getCurrentEmpId();
        return ResponseWrapper.success(workflowService.approveBranch(assignmentId, request, operatorId));
    }

    /** 支部书记驳回，意见必填，任务退回报送员。 */
    @Operation(summary = "支部驳回任务")
    @PostMapping({"/reviews/tasks/branch/{assignmentId}/reject", "/tasks/branch-review/{assignmentId}/reject"})
    @BizAuth(bizType = BizType.RED_ENGINE, action = BizAction.WRITE)
    @AuditLog(action = "RE_TASK_BRANCH_REJECT", resourceType = "RE_TASK_SUBMISSION", reasonRequired = true)
    public ResponseWrapper<ReTaskWorkflowActionRespDTO> rejectBranch(
            @PathVariable Long assignmentId,
            @Valid @RequestBody ReTaskRejectReqDTO request) {
        String operatorId = currentUserApi.getCurrentEmpId();
        return ResponseWrapper.success(workflowService.rejectBranch(assignmentId, request, operatorId));
    }

    /** 支部书记单独提交至组织审核。 */
    @Operation(summary = "提交组织审核")
    @PostMapping({"/reviews/tasks/branch/{assignmentId}/submit-to-org", "/tasks/branch-review/{assignmentId}/submit-to-org"})
    @BizAuth(bizType = BizType.RED_ENGINE, action = BizAction.WRITE)
    @AuditLog(action = "RE_TASK_SUBMIT_TO_ORG", resourceType = "RE_TASK_SUBMISSION")
    public ResponseWrapper<ReTaskWorkflowActionRespDTO> submitToOrg(
            @PathVariable Long assignmentId,
            @RequestBody(required = false) ReTaskApproveReqDTO request) {
        String operatorId = currentUserApi.getCurrentEmpId();
        return ResponseWrapper.success(workflowService.submitToOrg(assignmentId, request, operatorId));
    }

    /** 组织审核队列。 */
    @Operation(summary = "组织审核任务列表")
    @GetMapping({"/reviews/tasks/org/queue", "/tasks/org-review"})
    @BizAuth(bizType = BizType.RED_ENGINE, action = BizAction.LIST)
    public ResponseWrapper<ReTaskWorkflowAssignmentDTO> listOrgReviews(
            @Valid @ModelAttribute ReTaskWorkflowPageQueryDTO query) {
        String operatorId = currentUserApi.getCurrentEmpId();
        return ResponseWrapper.page(workflowService.listOrgReviews(query, operatorId));
    }

    /** 组织审核详情。 */
    @Operation(summary = "组织审核任务详情")
    @GetMapping({"/reviews/tasks/org/{assignmentId}", "/tasks/org-review/{assignmentId}"})
    @BizAuth(bizType = BizType.RED_ENGINE, action = BizAction.READ)
    public ResponseWrapper<ReTaskWorkflowAssignmentDTO> getOrgReview(
            @PathVariable Long assignmentId) {
        String operatorId = currentUserApi.getCurrentEmpId();
        return ResponseWrapper.success(workflowService.getOrgReview(assignmentId, operatorId));
    }

    /** 组织审核通过任务。 */
    @Operation(summary = "组织审核通过")
    @PostMapping({"/reviews/tasks/org/{assignmentId}/approve", "/tasks/org-review/{assignmentId}/approve"})
    @BizAuth(bizType = BizType.RED_ENGINE, action = BizAction.WRITE)
    @AuditLog(action = "RE_TASK_ORG_APPROVE", resourceType = "RE_TASK_SUBMISSION")
    public ResponseWrapper<ReTaskWorkflowActionRespDTO> approveOrg(
            @PathVariable Long assignmentId,
            @RequestBody(required = false) ReTaskApproveReqDTO request) {
        String operatorId = currentUserApi.getCurrentEmpId();
        return ResponseWrapper.success(workflowService.approveOrg(assignmentId, request, operatorId));
    }

    /** 组织审核驳回任务，意见必填并退回报送员。 */
    @Operation(summary = "组织驳回任务")
    @PostMapping({"/reviews/tasks/org/{assignmentId}/reject", "/tasks/org-review/{assignmentId}/reject"})
    @BizAuth(bizType = BizType.RED_ENGINE, action = BizAction.WRITE)
    @AuditLog(action = "RE_TASK_ORG_REJECT", resourceType = "RE_TASK_SUBMISSION", reasonRequired = true)
    public ResponseWrapper<ReTaskWorkflowActionRespDTO> rejectOrg(
            @PathVariable Long assignmentId,
            @Valid @RequestBody ReTaskRejectReqDTO request) {
        String operatorId = currentUserApi.getCurrentEmpId();
        return ResponseWrapper.success(workflowService.rejectOrg(assignmentId, request, operatorId));
    }
}
