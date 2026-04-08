package com.bank.branch.platform.workflow.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.workflow.api.dto.ApproveReqDTO;
import com.bank.branch.platform.workflow.api.dto.RejectReqDTO;
import com.bank.branch.platform.workflow.api.dto.TaskDetailRespDTO;
import com.bank.branch.platform.workflow.api.dto.TaskRespDTO;
import com.bank.branch.platform.workflow.api.dto.TransferReqDTO;
import com.bank.branch.platform.workflow.service.TaskOperationService;
import com.bank.branch.platform.workflow.service.TodoQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 任务操作控制器
 * <p>
 * 提供待办/已办查询、任务详情、签收、审批、驳回、转交等接口。
 * 无 @BizAuth 注解，通过 empId 自然过滤数据。
 * </p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/workflow/tasks")
@Tag(name = "工作流任务", description = "待办/已办查询与任务操作")
public class TaskController {

    private final TodoQueryService todoQueryService;
    private final TaskOperationService taskOperationService;
    private final CurrentUserApi currentUserApi;

    /**
     * 查询待办列表（分页）
     * 设计文档 A.1: GET /api/workflow/tasks
     *
     * @param bizType  业务类型过滤（可选）
     * @param keyword  关键字搜索（可选）
     * @param pageNo   页码，默认 1
     * @param pageSize 每页条数，默认 20
     * @return 分页待办列表
     */
    @GetMapping
    @Operation(summary = "查询待办列表")
    public ResponseWrapper<TaskRespDTO> queryTodoList(
            @RequestParam(value = "bizType", required = false) String bizType,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        log.debug("[TaskController.queryTodoList] bizType={}, keyword={}, pageNo={}, pageSize={}",
                bizType, keyword, pageNo, pageSize);
        String empId = currentUserApi.getCurrentEmpId();
        PageResult<TaskRespDTO> result = todoQueryService.queryTodoList(empId, bizType, keyword, pageNo, pageSize);
        return ResponseWrapper.page(result);
    }

    /**
     * 查询已办列表（分页）
     * 设计文档 A.2: GET /api/workflow/tasks/done
     *
     * @param bizType  业务类型过滤（可选）
     * @param keyword  关键字搜索（可选）
     * @param pageNo   页码，默认 1
     * @param pageSize 每页条数，默认 20
     * @return 分页已办列表
     */
    @GetMapping("/done")
    @Operation(summary = "查询已办列表")
    public ResponseWrapper<TaskRespDTO> queryDoneList(
            @RequestParam(value = "bizType", required = false) String bizType,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        log.debug("[TaskController.queryDoneList] bizType={}, keyword={}, pageNo={}, pageSize={}",
                bizType, keyword, pageNo, pageSize);
        String empId = currentUserApi.getCurrentEmpId();
        PageResult<TaskRespDTO> result = todoQueryService.queryDoneList(empId, bizType, keyword, pageNo, pageSize);
        return ResponseWrapper.page(result);
    }

    /**
     * 获取任务详情
     * 设计文档 A.3: GET /api/workflow/tasks/{taskId}
     *
     * @param taskId 任务ID
     * @return 任务详情
     */
    @GetMapping("/{taskId}")
    @Operation(summary = "获取任务详情")
    public ResponseWrapper<TaskDetailRespDTO> getTaskDetail(
            @PathVariable(value = "taskId") String taskId) {
        String empId = currentUserApi.getCurrentEmpId();
        log.debug("[TaskController.getTaskDetail] taskId={}", taskId);
        TaskDetailRespDTO detail = todoQueryService.getTaskDetail(taskId, empId);
        return ResponseWrapper.success(detail);
    }

    /**
     * 签收任务
     * 设计文档 B.1: POST /api/workflow/tasks/{taskId}/claim
     *
     * @param taskId 任务ID
     * @return 成功响应
     */
    @PostMapping("/{taskId}/claim")
    @Operation(summary = "签收任务")
    public ResponseWrapper<Void> claimTask(
            @PathVariable(value = "taskId") String taskId) {
        log.info("[TaskController.claimTask] taskId={}", taskId);
        taskOperationService.claimTask(taskId);
        return ResponseWrapper.success();
    }

    /**
     * 审批通过任务
     * 设计文档 B.2: POST /api/workflow/tasks/{taskId}/approve
     *
     * @param taskId 任务ID
     * @param req    审批请求 (opinion, formData)
     * @return 成功响应
     */
    @PostMapping("/{taskId}/approve")
    @Operation(summary = "审批通过")
    public ResponseWrapper<Void> approveTask(
            @PathVariable(value = "taskId") String taskId,
            @Valid @RequestBody ApproveReqDTO req) {
        log.info("[TaskController.approveTask] taskId={}, opinion={}", taskId, req.getOpinion());
        taskOperationService.approveTask(taskId, req);
        return ResponseWrapper.success();
    }

    /**
     * 驳回任务
     * 设计文档 B.3: POST /api/workflow/tasks/{taskId}/reject
     *
     * @param taskId 任务ID
     * @param req    驳回请求 (opinion)
     * @return 成功响应
     */
    @PostMapping("/{taskId}/reject")
    @Operation(summary = "驳回任务")
    public ResponseWrapper<Void> rejectTask(
            @PathVariable(value = "taskId") String taskId,
            @Valid @RequestBody RejectReqDTO req) {
        log.info("[TaskController.rejectTask] taskId={}, opinion={}", taskId, req.getOpinion());
        taskOperationService.rejectTask(taskId, req);
        return ResponseWrapper.success();
    }

    /**
     * 转交任务
     * 设计文档 B.4: POST /api/workflow/tasks/{taskId}/transfer
     *
     * @param taskId 任务ID
     * @param req    转交请求 (targetEmpId, reason)
     * @return 成功响应
     */
    @PostMapping("/{taskId}/transfer")
    @Operation(summary = "转交任务")
    public ResponseWrapper<Void> transferTask(
            @PathVariable(value = "taskId") String taskId,
            @Valid @RequestBody TransferReqDTO req) {
        log.info("[TaskController.transferTask] taskId={}, targetEmpId={}", taskId, req.getTargetEmpId());
        taskOperationService.transferTask(taskId, req);
        return ResponseWrapper.success();
    }
}
