package com.bank.branch.platform.redengine.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.redengine.api.dto.ReTaskAssignmentDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskAssignmentPageQueryDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskCreateReqDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskCreateRespDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskDetailDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskListItemDTO;
import com.bank.branch.platform.redengine.api.dto.ReTaskPageQueryDTO;
import com.bank.branch.platform.redengine.service.ReTaskManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 红色引擎任务管理入口。
 * <p>管理端只负责请求绑定、统一鉴权声明和响应包装；任务对象、发布状态、数据范围
 * 以及实例/分配生成均由 {@link ReTaskManagementService} 在服务层重新校验。</p>
 */
@Slf4j
@Tag(name = "红色引擎-任务管理")
@RestController
@RequestMapping("/api/re/tasks")
@RequiredArgsConstructor
public class ReTaskManagementController {

    private final ReTaskManagementService taskManagementService;
    private final CurrentUserApi currentUserApi;

    /** 查询已发布任务分页。 */
    @Operation(summary = "任务管理分页")
    @GetMapping
    @BizAuth(bizType = BizType.RED_ENGINE, action = BizAction.LIST)
    public ResponseWrapper<ReTaskListItemDTO> page(
            @Valid @ModelAttribute ReTaskPageQueryDTO query) {
        String operatorId = currentUserApi.getCurrentEmpId();
        PageResult<ReTaskListItemDTO> result = taskManagementService.page(query, operatorId);
        log.info("[ReTaskManagementController.page] operatorId={}, total={}", operatorId, result.getTotal());
        return ResponseWrapper.page(result);
    }

    /** 新增并发布一条任务定义。 */
    @Operation(summary = "新增发布任务")
    @PostMapping
    @BizAuth(bizType = BizType.RED_ENGINE, action = BizAction.WRITE)
    @AuditLog(action = "RE_TASK_PUBLISH", resourceType = "RE_TASK")
    public ResponseWrapper<ReTaskCreateRespDTO> createAndPublish(
            @Valid @RequestBody ReTaskCreateReqDTO request) {
        String operatorId = currentUserApi.getCurrentEmpId();
        ReTaskCreateRespDTO result = taskManagementService.createAndPublish(request, operatorId);
        log.info("[ReTaskManagementController.createAndPublish] operatorId={}, taskId={}",
                operatorId, result.getTaskId());
        return ResponseWrapper.success(result);
    }

    /** 查询任务配置详情。 */
    @Operation(summary = "任务详情")
    @GetMapping("/{taskId}")
    @BizAuth(bizType = BizType.RED_ENGINE, action = BizAction.READ)
    public ResponseWrapper<ReTaskDetailDTO> getDetail(@PathVariable Long taskId) {
        String operatorId = currentUserApi.getCurrentEmpId();
        ReTaskDetailDTO result = taskManagementService.getDetail(taskId, operatorId);
        log.info("[ReTaskManagementController.getDetail] operatorId={}, taskId={}, found={}",
                operatorId, taskId, result != null);
        return ResponseWrapper.success(result);
    }

    /** 查询任务下各党支部填报汇总分页。 */
    @Operation(summary = "任务支部填报汇总")
    @GetMapping("/{taskId}/assignments")
    @BizAuth(bizType = BizType.RED_ENGINE, action = BizAction.LIST)
    public ResponseWrapper<ReTaskAssignmentDTO> pageAssignments(
            @PathVariable Long taskId,
            @Valid @ModelAttribute ReTaskAssignmentPageQueryDTO query) {
        String operatorId = currentUserApi.getCurrentEmpId();
        PageResult<ReTaskAssignmentDTO> result = taskManagementService.pageAssignments(taskId, query, operatorId);
        log.info("[ReTaskManagementController.pageAssignments] operatorId={}, taskId={}, total={}",
                operatorId, taskId, result.getTotal());
        return ResponseWrapper.page(result);
    }
}
