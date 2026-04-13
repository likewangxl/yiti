package com.bank.branch.platform.portal.facade;

import com.bank.branch.platform.portal.api.PortalApi;
import com.bank.branch.platform.portal.api.dto.WorkspaceDTO;
import com.bank.branch.platform.portal.service.WorkspaceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 门户聚合 Facade 实现
 *
 * <p>实现 {@link PortalApi} 接口，委托给 {@link WorkspaceService} 处理。
 * 所有方法均为只读查询，不提供写操作。</p>
 *
 * <p>注意：{@code getWorkspace(empId)} 中的 empId 参数供跨模块调用方传入，
 * 但实际聚合逻辑内部通过 CurrentUserApi 获取当前用户上下文，
 * 因为跨模块调用发生在同一请求线程内，当前用户上下文已由 auth 层注入。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PortalFacade implements PortalApi {

    private final WorkspaceService workspaceService;

    /**
     * 工作台数据聚合。
     * 委托给 WorkspaceService.getWorkspace()。
     *
     * @param empId 员工工号（由 CurrentUserApi 在请求上下文中保证一致）
     * @return 工作台数据传输对象
     */
    @Override
    public WorkspaceDTO getWorkspace(String empId) {
        return workspaceService.getWorkspace();
    }

    /**
     * 获取用户待办数量。
     * 委托给 WorkspaceService.getTodoCount()。
     *
     * @param empId 员工工号
     * @return 待办总数
     */
    @Override
    public int getTodoCount(String empId) {
        return workspaceService.getTodoCount(empId);
    }

    /**
     * 获取用户未读通知数量。
     * 委托给 WorkspaceService.getUnreadNotificationCount()。
     *
     * @param empId 员工工号
     * @return 未读通知数量
     */
    @Override
    public int getUnreadNotificationCount(String empId) {
        return workspaceService.getUnreadNotificationCount(empId);
    }
}
