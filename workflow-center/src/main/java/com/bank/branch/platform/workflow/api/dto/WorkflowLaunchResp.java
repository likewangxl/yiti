package com.bank.branch.platform.workflow.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 流程启动响应
 * <p>
 * 包含流程实例ID、业务键和首个任务ID（如果第一个节点是 userTask）。
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowLaunchResp {

    /** Flowable 流程实例ID */
    private String processInstanceId;

    /** 业务键，与 StartProcessCmd.businessKey 一致 */
    private String businessKey;

    /** 首个任务ID，若第一个节点是自动任务则为 null */
    private String firstTaskId;
}
