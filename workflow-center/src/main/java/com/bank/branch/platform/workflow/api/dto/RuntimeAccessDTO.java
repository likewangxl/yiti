package com.bank.branch.platform.workflow.api.dto;

import lombok.Data;

/**
 * 任务运行时权限 DTO
 * <p>
 * 用于任务详情接口（GET /api/workflow/tasks/{taskId}），
 * 表示当前用户对此任务的操作权限。
 * </p>
 */
@Data
public class RuntimeAccessDTO {

    /** 是否可签收 */
    private Boolean canClaim;

    /** 是否可审批 */
    private Boolean canApprove;

    /** 是否可驳回 */
    private Boolean canReject;

    /** 是否可转交 */
    private Boolean canTransfer;

    /** 是否当前处理人 */
    private Boolean isAssignee;

    /** 是否候选人 */
    private Boolean isCandidate;
}
