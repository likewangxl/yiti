package com.bank.branch.platform.portal.api.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

/**
 * 工作台待办项 DTO（不可变）
 *
 * <p>用于工作台数据聚合，从 WorkflowQueryApi 适配而来。</p>
 */
@Value
@Builder
public class TodoItemDTO {

    /** 任务ID */
    String taskId;

    /** 流程实例ID */
    String processInstanceId;

    /** 流程名称（中文） */
    String processName;

    /** 当前任务标题 */
    String taskTitle;

    /** 发起人姓名 */
    String initiatorName;

    /** 发起时间 */
    LocalDateTime initiatedTime;

    /** 红绿灯状态 GREEN/YELLOW/RED */
    String lightStatus;

    /** 超时信息描述 */
    String overdueInfo;

    /** 业务详情页URL */
    String bizDetailUrl;
}
