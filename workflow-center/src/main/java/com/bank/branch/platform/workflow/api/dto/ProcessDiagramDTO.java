package com.bank.branch.platform.workflow.api.dto;

import lombok.Data;

import java.util.List;

/**
 * 流程进度图结构化 DTO
 */
@Data
public class ProcessDiagramDTO {

    /** 流程实例ID */
    private String processInstanceId;

    /** 流程定义KEY */
    private String processDefinitionKey;

    /** 节点列表 */
    private List<ProcessDiagramNodeDTO> nodes;
}