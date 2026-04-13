package com.bank.branch.platform.portal.api.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

/**
 * 工作台数据聚合 DTO（不可变）
 *
 * <p>一次调用返回工作台全部四个区域数据（待办/通知/指标/快捷入口）。
 * 部分子调用失败时通过 aggregateErrors 记录错误信息，不影响整体返回。</p>
 */
@Value
@Builder
public class WorkspaceDTO {

    /** 待办总数 */
    int todoCount;

    /** 未读通知数量 */
    int unreadNotificationCount;

    /** 最近待办列表 */
    List<TodoItemDTO> recentTodos;

    /** 最近通知列表 */
    List<NotificationItemDTO> recentNotifications;

    /** 指标卡片（使用 PortalMetricCard） */
    List<PortalMetricCard> metricCards;

    /** 新增流程快捷入口 */
    List<ShortcutDTO> shortcuts;

    /** 聚合子调用错误信息（区域 -> 错误码） */
    Map<String, String> aggregateErrors;
}
