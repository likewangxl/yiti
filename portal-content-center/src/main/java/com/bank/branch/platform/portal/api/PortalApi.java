package com.bank.branch.platform.portal.api;

import com.bank.branch.platform.portal.api.dto.WorkspaceDTO;

/**
 * 门户聚合对外接口。
 * 提供工作台数据聚合能力，被其他模块调用。
 *
 * <p>所有方法均为只读查询。子调用失败时通过降级机制保证整体可用，
 * 错误信息收集到 {@link WorkspaceDTO#getAggregateErrors()} 中。</p>
 *
 * @author portal-content-center
 * @since V1.0
 */
public interface PortalApi {

    /**
     * 工作台数据聚合。
     * 一次调用返回工作台需要的全部数据（待办/通知/指标/快捷入口）。
     *
     * @param empId 员工工号
     * @return 工作台数据传输对象
     */
    WorkspaceDTO getWorkspace(String empId);

    /**
     * 获取用户待办数量。
     * 被其他模块调用，用于显示角标计数。
     *
     * @param empId 员工工号
     * @return 待办总数
     */
    int getTodoCount(String empId);

    /**
     * 获取用户未读通知数量。
     * 被其他模块调用，用于显示角标计数。
     *
     * @param empId 员工工号
     * @return 未读通知数量
     */
    int getUnreadNotificationCount(String empId);
}
