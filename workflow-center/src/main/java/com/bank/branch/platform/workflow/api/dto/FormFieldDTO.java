package com.bank.branch.platform.workflow.api.dto;

import lombok.Data;

/**
 * 流程节点表单字段定义 DTO
 */
@Data
public class FormFieldDTO {

    /** 字段 Key */
    private String fieldKey;

    /** 字段名称（展示用） */
    private String fieldName;

    /** 字段类型：text / number / date / select / textarea */
    private String fieldType;

    /** 字段说明 */
    private String description;
}
