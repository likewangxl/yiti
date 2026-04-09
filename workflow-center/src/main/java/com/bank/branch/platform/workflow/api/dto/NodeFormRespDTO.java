package com.bank.branch.platform.workflow.api.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 节点表单配置响应 DTO
 */
@Data
public class NodeFormRespDTO {

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

    /** 表单字段定义列表 */
    private List<FormFieldDTO> formFields;

    /** 可编辑字段Key列表 */
    private List<String> editableFields;

    /** 必填字段Key列表 */
    private List<String> requiredFields;

    /** 创建时间 */
    private LocalDateTime createdTime;

    /** 更新时间 */
    private LocalDateTime updatedTime;
}
