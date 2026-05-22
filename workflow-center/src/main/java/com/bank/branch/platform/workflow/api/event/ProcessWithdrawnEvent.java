package com.bank.branch.platform.workflow.api.event;

/**
 * 流程被申请人撤回事件。
 * <p>由业务模块（如 perf）在调 Flowable deleteProcessInstance <strong>之前</strong> publish；
 * currentAssigneeEmpId 由发布方查 active task 拿到，避免 listener 收到时 active task 已不存在。</p>
 *
 * @param processInstanceId    Flowable 流程实例 ID
 * @param businessKey          业务键，如 "ALLOC_ADJUST:A1"
 * @param withdrawnByEmpId     撤回者（一般是申请人）
 * @param currentAssigneeEmpId 撤回时当前 active task 的受理人（可能为 null：未签收的候选组任务）
 * @param opinion              撤回原因
 */
public record ProcessWithdrawnEvent(String processInstanceId,
                                    String businessKey,
                                    String withdrawnByEmpId,
                                    String currentAssigneeEmpId,
                                    String opinion) {
}
