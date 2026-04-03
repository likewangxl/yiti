package com.bank.branch.platform.workflow.api.dto;

import lombok.Data;

import java.util.List;

/**
 * 任务详情响应DTO。
 * <p>
 * 包含任务基础信息、表单字段配置和审批日志，
 * 用于任务详情（GET /api/workflow/tasks/{taskId}）接口。
 * 前端根据 formFields/editableFields/requiredFields 动态渲染审批表单。
 * </p>
 */
@Data
public class TaskDetailRespDTO {

    /** 任务基础信息 */
    private TaskRespDTO taskInfo;

    /** 表单字段配置（JSON数组） */
    private String formFields;

    /** 可编辑字段列表（JSON数组） */
    private String editableFields;

    /** 必填字段列表（JSON数组） */
    private String requiredFields;

    /** 审批日志列表 */
    private List<ApprovalLogDTO> approvalLogs;
}
