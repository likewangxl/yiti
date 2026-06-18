package com.bank.branch.platform.workflow.api.dto;

import lombok.Data;

import java.util.List;

/**
 * 任务详情响应DTO。
 * <p>
 * 包含任务基础信息、运行时权限、表单配置、流程进度和审批日志，
 * 用于任务详情（GET /api/workflow/tasks/{taskId}）接口。
 * 前端根据 formFields/editableFields/requiredFields 动态渲染审批表单。
 * </p>
 */
@Data
public class TaskDetailRespDTO {

    /** 任务基础信息 */
    private TaskRespDTO taskInfo;

    /** 运行时权限 */
    private RuntimeAccessDTO runtimeAccess;

    /** 节点表单配置 */
    private NodeFormConfDTO nodeFormConf;

    /** 流程进度节点列表 */
    private List<ProcessNodeDTO> processProgress;

    /** 审批日志列表 */
    private List<ApprovalLogDTO> approvalLogs;

    /**
     * 当前审批节点的「下一步走向」分支选项（来自设计器图当前节点的命名出边）。
     * <p>仅设计器动态流程（procDefKey 以 DSN_ 前缀）任务会填充；静态 BPMN 任务为空列表。
     * 前端当 size≥2 时渲染分支单选，选中项的 routeVariables 并入审批 formData。</p>
     */
    private List<BranchOptionDTO> outgoingBranches;
}
