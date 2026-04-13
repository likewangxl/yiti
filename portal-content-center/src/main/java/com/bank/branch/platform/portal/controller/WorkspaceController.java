package com.bank.branch.platform.portal.controller;

import com.bank.branch.platform.common.web.ResponseWrapper;
import com.bank.branch.platform.portal.api.dto.WorkspaceDTO;
import com.bank.branch.platform.portal.service.WorkspaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A.1 工作台聚合控制器
 *
 * <p>提供工作台数据聚合接口，一次调用返回待办、通知、指标、快捷入口等全部区域数据。
 * 不需要 @BizAuth 鉴权（工作台为用户级数据，登录即可访问）。</p>
 */
@RestController
@RequestMapping("/api/portal")
@RequiredArgsConstructor
@Tag(name = "工作台", description = "工作台聚合接口")
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    /**
     * A.1 获取工作台聚合数据
     *
     * <p>并行查询 shortcuts、todoCount、recentTodos、unreadNotificationCount、
     * recentNotifications、metricCards 六个维度数据，部分子查询失败时通过
     * aggregateErrors 字段返回错误信息，不影响整体响应。</p>
     *
     * @return 工作台聚合数据
     */
    @GetMapping("/workspace")
    @Operation(summary = "工作台数据聚合")
    public ResponseWrapper<WorkspaceDTO> getWorkspace() {
        return ResponseWrapper.success(workspaceService.getWorkspace());
    }
}
