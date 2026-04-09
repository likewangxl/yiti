package com.bank.branch.platform.governance.controller;

import com.bank.branch.platform.common.security.context.DataScopeContext;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.governance.api.dto.NotificationDTO;
import com.bank.branch.platform.governance.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 通知管理控制器
 * 提供用户通知的查询、未读计数和已读标记接口
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notifications")
@Tag(name = "通知管理", description = "用户通知的查询与已读管理")
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * 分页查询当前用户通知列表
     *
     * @param isRead   是否已读，可为 null（全部）
     * @param pageNo   页码，默认 1
     * @param pageSize 每页条数，默认 20
     * @return 分页结果
     */
    @GetMapping
    @Operation(summary = "查询通知列表")
    public ResponseWrapper<NotificationDTO> queryNotifications(
            @RequestParam(value = "isRead", required = false) Boolean isRead,
            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        String empId = DataScopeContext.current().getEmpId();
        log.debug("[NotificationController.queryNotifications] empId={}, isRead={}, pageNo={}, pageSize={}",
                empId, isRead, pageNo, pageSize);
        PageResult<NotificationDTO> result = notificationService.queryNotifications(empId, isRead, pageNo, pageSize);
        return ResponseWrapper.page(result);
    }

    /**
     * 查询当前用户未读通知数量
     *
     * @return 未读数量
     */
    @GetMapping("/unread-count")
    @Operation(summary = "查询未读通知数量")
    public ResponseWrapper<Integer> countUnread() {
        String empId = DataScopeContext.current().getEmpId();
        log.debug("[NotificationController.countUnread] empId={}", empId);
        int count = notificationService.countUnread(empId);
        return ResponseWrapper.success(count);
    }

    /**
     * 将单条通知标记为已读
     *
     * @param id 通知ID（路径参数）
     * @return 成功响应
     */
    @PutMapping("/{id}/read")
    @Operation(summary = "标记通知为已读")
    public ResponseWrapper<Void> markAsRead(@PathVariable(value = "id") String id) {
        String empId = DataScopeContext.current().getEmpId();
        log.info("[NotificationController.markAsRead] id={}, empId={}", id, empId);
        notificationService.markAsReadForUser(id, empId);
        return ResponseWrapper.success();
    }

    /**
     * 将当前用户的所有未读通知标记为已读
     *
     * @return 标记已读的条数
     */
    @PutMapping("/read-all")
    @Operation(summary = "全部标记为已读")
    public ResponseWrapper<Integer> markAllAsRead() {
        String empId = DataScopeContext.current().getEmpId();
        log.info("[NotificationController.markAllAsRead] empId={}", empId);
        int count = notificationService.markAllAsRead(empId);
        return ResponseWrapper.success(count);
    }

    /**
     * 查看通知详情，同时自动标记为已读（E.5）
     *
     * @param id 通知ID（路径参数）
     * @return 通知详情
     */
    @GetMapping("/{id}")
    @Operation(summary = "查看通知详情")
    public ResponseWrapper<NotificationDTO> getNotificationDetail(@PathVariable(value = "id") String id) {
        String empId = DataScopeContext.current().getEmpId();
        log.info("[NotificationController.getNotificationDetail] id={}, empId={}", id, empId);
        // 查询通知详情（带权限校验）
        NotificationDTO notification = notificationService.getByIdForUser(id, empId);
        // 自动标记为已读（带权限校验）
        notificationService.markAsReadForUser(id, empId);
        return ResponseWrapper.success(notification);
    }
}
