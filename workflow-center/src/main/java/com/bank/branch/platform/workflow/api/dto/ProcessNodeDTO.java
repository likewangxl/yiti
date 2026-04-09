package com.bank.branch.platform.workflow.api.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 流程节点进度 DTO
 * <p>
 * 用于任务详情接口（GET /api/workflow/tasks/{taskId}）的 processProgress 字段，
 * 表示流程中各节点的执行状态。
 * </p>
 */
@Data
public class ProcessNodeDTO {

    /** BPMN 节点ID */
    private String nodeKey;

    /** 节点名称 */
    private String nodeName;

    /** 节点状态：COMPLETED / ACTIVE / PENDING */
    private String status;

    /** 处理人工号 */
    private String assignee;

    /** 处理人姓名 */
    private String assigneeName;

    /** 完成时间 */
    private LocalDateTime completeTime;
}
