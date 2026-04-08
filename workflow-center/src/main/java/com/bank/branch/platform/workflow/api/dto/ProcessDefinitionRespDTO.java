package com.bank.branch.platform.workflow.api.dto;

import lombok.Data;

/**
 * 流程定义响应 DTO
 * <p>
 * 对齐设计文档 D.7: GET /api/admin/workflow/process-definitions
 * </p>
 */
@Data
public class ProcessDefinitionRespDTO {

    /** 流程定义ID (格式: key:version:id) */
    private String processDefinitionId;

    /** 流程定义Key */
    private String processDefinitionKey;

    /** 流程定义名称 */
    private String processDefinitionName;

    /** 版本号 */
    private Integer version;

    /** 部署ID */
    private String deploymentId;

    /** 部署时间 */
    private String deploymentTime;

    /** 是否挂起 */
    private Boolean suspended;

    /** 描述 */
    private String description;
}
