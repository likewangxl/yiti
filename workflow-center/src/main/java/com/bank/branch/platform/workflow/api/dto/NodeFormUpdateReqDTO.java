package com.bank.branch.platform.workflow.api.dto;

import lombok.Data;

import java.util.List;

/**
 * 流程节点表单更新请求 DTO
 */
@Data
public class NodeFormUpdateReqDTO {

    /** 表单字段定义列表 */
    private List<FormFieldDTO> formFields;

    /** 可编辑字段 Key 列表 */
    private List<String> editableFields;

    /** 必填字段 Key 列表 */
    private List<String> requiredFields;
}
