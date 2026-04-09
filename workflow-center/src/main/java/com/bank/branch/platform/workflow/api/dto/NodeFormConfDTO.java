package com.bank.branch.platform.workflow.api.dto;

import lombok.Data;

import java.util.List;

/**
 * 节点表单配置 DTO
 * <p>
 * 用于任务详情接口（GET /api/workflow/tasks/{taskId}）的 nodeFormConf 字段，
 * 包含节点表单字段定义、可编辑字段和必填字段。
 * </p>
 */
@Data
public class NodeFormConfDTO {

    /** 流程定义KEY */
    private String processDefinitionKey;

    /** 节点KEY */
    private String nodeKey;

    /** 表单字段定义列表 */
    private List<FormFieldDTO> formFields;

    /** 可编辑字段Key列表 */
    private List<String> editableFields;

    /** 必填字段Key列表 */
    private List<String> requiredFields;
}