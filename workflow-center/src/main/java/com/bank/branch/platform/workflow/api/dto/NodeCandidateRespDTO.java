package com.bank.branch.platform.workflow.api.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 节点候选人配置响应 DTO
 */
@Data
public class NodeCandidateRespDTO {

    /** 配置ID */
    private String id;

    /** 流程定义KEY */
    private String processDefinitionKey;

    /** 流程定义名称 */
    private String processDefinitionName;

    /** 节点KEY */
    private String nodeKey;

    /** 节点名称 */
    private String nodeName;

    /** 候选类型：ROLE / ORG / USER */
    private String candidateType;

    /** 候选值列表 */
    private List<String> candidateValue;

    /** 创建时间 */
    private LocalDateTime createdTime;

    /** 更新时间 */
    private LocalDateTime updatedTime;
}
