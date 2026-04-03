package com.bank.branch.platform.workflow.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 流程节点表单配置实体，对应 wf_node_form_conf 表。
 * <p>
 * 配置每个流程节点的审批表单字段定义、可编辑字段和必填字段。
 * form_fields / editable_fields / required_fields 均为 JSON 数组格式。
 * </p>
 */
@Data
public class WfNodeFormConf {

    /** 配置ID（UUID主键），对应 id */
    private String id;

    /** 流程定义KEY，对应 process_definition_key */
    private String processDefinitionKey;

    /** 节点KEY，对应 node_key */
    private String nodeKey;

    /** 表单字段配置（JSON数组），对应 form_fields */
    private String formFields;

    /** 可编辑字段列表（JSON数组），对应 editable_fields */
    private String editableFields;

    /** 必填字段列表（JSON数组），对应 required_fields */
    private String requiredFields;

    /** 创建时间，对应 created_time */
    private LocalDateTime createdTime;

    /** 更新时间，对应 updated_time */
    private LocalDateTime updatedTime;
}
