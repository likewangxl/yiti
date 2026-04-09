package com.bank.branch.platform.workflow.api.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 超时规则响应 DTO
 */
@Data
public class TimeoutRuleRespDTO {

    /** 规则ID */
    private String id;

    /** 流程定义KEY */
    private String processDefinitionKey;

    /** 流程定义名称 */
    private String processDefinitionName;

    /** 节点KEY */
    private String nodeKey;

    /** 节点名称 */
    private String nodeName;

    /** 预警小时数 */
    private Integer warningHours;

    /** 超时小时数 */
    private Integer timeoutHours;

    /** 创建时间 */
    private LocalDateTime createdTime;

    /** 更新时间 */
    private LocalDateTime updatedTime;
}
