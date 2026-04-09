package com.bank.branch.platform.workflow.api.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 流程进度图节点 DTO
 */
@Data
public class ProcessDiagramNodeDTO {

    /** BPMN 节点ID */
    private String nodeKey;

    /** 节点名称 */
    private String nodeName;

    /** 节点类型：startEvent / userTask / exclusiveGateway / endEvent */
    private String nodeType;

    /** 节点状态：COMPLETED / ACTIVE / PENDING */
    private String status;

    /** 处理人工号 */
    private String assignee;

    /** 处理人姓名 */
    private String assigneeName;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;
}