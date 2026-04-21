package com.bank.branch.platform.customer.controller;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.aop.annotation.AuditLog;
import com.bank.branch.platform.common.security.annotation.BizAuth;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.customer.dto.req.TouchCancelReqDTO;
import com.bank.branch.platform.customer.dto.req.TouchLogReqDTO;
import com.bank.branch.platform.customer.entity.TouchLog;
import com.bank.branch.platform.customer.entity.TouchTask;
import com.bank.branch.platform.customer.service.TouchLogService;
import com.bank.branch.platform.customer.service.TouchTaskService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 触达任务 REST 控制器。
 * <p>
 * 提供触达任务的分页查询、详情查询、完成、取消及触达日志管理接口。
 * 完成和取消为写操作，配置 @AuditLog 审计；取消为高危操作须携带原因。
 * </p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/touch-tasks")
@Validated
@Tag(name = "触达任务管理")
public class TouchTaskController {

    private final TouchTaskService touchTaskService;
    private final TouchLogService touchLogService;
    private final CurrentUserApi currentUserApi;
    private final ObjectMapper objectMapper;

    /**
     * 分页查询触达任务列表。
     *
     * @param keyword       关键词（模糊匹配 task_no），可为空
     * @param status        任务状态过滤（PENDING/SUCCESS/CANCELLED），可为空
     * @param assigneeEmpId 执行人工号过滤，可为空
     * @param pageNo        页码，默认 1
     * @param pageSize      每页大小，默认 20
     * @return 分页的触达任务列表
     */
    @GetMapping
    @BizAuth(bizType = BizType.TOUCH_TASK, action = BizAction.LIST)
    @Operation(summary = "分页查询触达任务列表")
    public ResponseWrapper<TouchTask> listPage(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String assigneeEmpId,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        log.info("[TouchTaskController.listPage] keyword={}, status={}, assigneeEmpId={}, pageNo={}, pageSize={}",
                keyword, status, assigneeEmpId, pageNo, pageSize);
        PageResult<TouchTask> result = touchTaskService.listPage(keyword, status, assigneeEmpId, pageNo, pageSize);
        return ResponseWrapper.page(result);
    }

    /**
     * 查询触达任务详情。
     *
     * @param id 任务ID
     * @return 触达任务详情
     */
    @GetMapping("/{id}")
    @BizAuth(bizType = BizType.TOUCH_TASK, action = BizAction.READ)
    @Operation(summary = "查询触达任务详情")
    public ResponseWrapper<TouchTask> getById(@PathVariable String id) {
        log.info("[TouchTaskController.getById] id={}", id);
        TouchTask task = touchTaskService.getById(id);
        return ResponseWrapper.success(task);
    }

    /**
     * 标记触达任务成功。
     * <p>
     * 允许起始状态：PENDING、IN_PROGRESS。完成后发布 TouchCompletedEvent。
     * 非法状态转移（如已完成/已取消任务）返回 CUST-40010。
     * </p>
     *
     * @param id 任务ID
     * @return 操作结果
     */
    @PostMapping("/{id}/success")
    @BizAuth(bizType = BizType.TOUCH_TASK, action = BizAction.WRITE)
    @AuditLog(action = "MARK_TOUCH_TASK_SUCCESS", resourceType = "TOUCH_TASK")
    @Operation(summary = "标记触达任务成功")
    public ResponseWrapper<Void> markSuccess(@PathVariable String id) {
        log.info("[TouchTaskController.markSuccess] id={}", id);
        touchTaskService.markSuccess(id);
        return ResponseWrapper.success();
    }

    /**
     * 取消触达任务（高危操作）。
     * <p>
     * 取消原因为必填字段，由 @AuditLog 切面记录审计日志。
     * </p>
     *
     * @param id  任务ID
     * @param req 取消请求 DTO（包含 reason）
     * @return 操作结果
     */
    @PostMapping("/{id}/cancel")
    @BizAuth(bizType = BizType.TOUCH_TASK, action = BizAction.WRITE)
    @AuditLog(action = "CANCEL_TOUCH_TASK", resourceType = "TOUCH_TASK", reasonRequired = true)
    @Operation(summary = "取消触达任务")
    public ResponseWrapper<Void> cancel(@PathVariable String id,
                                        @Valid @RequestBody TouchCancelReqDTO req) {
        log.info("[TouchTaskController.cancel] id={}", id);
        touchTaskService.cancel(id, req.getReason());
        return ResponseWrapper.success();
    }

    /**
     * 新增触达日志（幂等接口）。
     * <p>
     * 由移动端提交，通过 clientUuid 保证幂等。
     * photoUrls 列表序列化为 JSON 数组字符串存储。
     * </p>
     *
     * @param id  任务ID
     * @param req 触达日志请求 DTO
     * @return 新增（或已存在）的触达日志
     */
    @PostMapping("/{id}/logs")
    @BizAuth(bizType = BizType.TOUCH_TASK, action = BizAction.WRITE)
    @Operation(summary = "新增触达日志")
    public ResponseWrapper<TouchLog> addLog(@PathVariable String id,
                                            @Valid @RequestBody TouchLogReqDTO req) {
        log.info("[TouchTaskController.addLog] taskId={}, clientUuid={}", id, req.getClientUuid());
        String empId = currentUserApi.getCurrentEmpId();
        String orgId = currentUserApi.getCurrentOrgCode();

        // 将 List<String> photoUrls 序列化为 JSON 数组字符串
        String photoUrlsJson = null;
        if (!CollectionUtils.isEmpty(req.getPhotoUrls())) {
            try {
                photoUrlsJson = objectMapper.writeValueAsString(req.getPhotoUrls());
            } catch (JsonProcessingException e) {
                log.warn("[TouchTaskController.addLog] failed to serialize photoUrls: {}", e.getMessage());
            }
        }

        TouchLog result = touchLogService.addLog(
                id, req.getClientUuid(), req.getLogContent(),
                photoUrlsJson, empId, orgId);
        return ResponseWrapper.success(result);
    }

    /**
     * 查询触达任务的日志列表。
     *
     * @param id 任务ID
     * @return 该任务的触达日志列表（按 log_time 降序）
     */
    @GetMapping("/{id}/logs")
    @BizAuth(bizType = BizType.TOUCH_TASK, action = BizAction.READ)
    @Operation(summary = "查询触达日志列表")
    public ResponseWrapper<List<TouchLog>> listLogs(@PathVariable String id) {
        log.info("[TouchTaskController.listLogs] taskId={}", id);
        List<TouchLog> logs = touchLogService.listByTaskId(id);
        return ResponseWrapper.success(logs);
    }
}
