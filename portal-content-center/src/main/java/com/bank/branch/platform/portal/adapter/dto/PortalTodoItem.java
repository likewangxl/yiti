package com.bank.branch.platform.portal.adapter.dto;

import lombok.Data;

/**
 * 工作台待办事项 DTO —— 工作台待办列表展示使用。
 *
 * @deprecated V1 临时占位，待 workflow-center WorkflowQueryFacade 创建后迁移
 */
@Deprecated
@Data
public class PortalTodoItem {

    /** 任务 ID */
    private String taskId;

    /** 流程实例 ID */
    private String processInstanceId;

    /** 流程名称 */
    private String processName;

    /** 任务标题 */
    private String taskTitle;

    /** 发起人姓名 */
    private String initiatorName;

    /** 发起时间 */
    private String initiatedTime;

    /** 红绿灯状态（green / yellow / red） */
    private String lightStatus;

    /** 超期信息 */
    private String overdueInfo;

    /** 业务详情跳转 URL */
    private String bizDetailUrl;
}
