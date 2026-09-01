package com.bank.branch.platform.bizapp.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 中台支持申请派单事件。
 * 在承接部门秘书完成派单（IN_APPROVAL -> IN_PROGRESS）时发布。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SupportDispatchedEvent {

    /** 申请ID */
    private String requestId;

    /** 申请编号 */
    private String requestNo;

    /** 被派人工号 */
    private String assignedEmpId;

    /** 派单人工号（部门秘书） */
    private String dispatcherEmpId;

    /** 承接部门ORG_CODE */
    private String supportDeptId;

    /** 派单备注（可选，文档 §8.5） */
    private String dispatchRemark;
}
