package com.bank.branch.platform.customer.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 触达任务完成事件
 * 触达任务完成后发布，用于统计和后续流程
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TouchCompletedEvent {

    /** 任务ID */
    private String taskId;

    /** 任务编号 */
    private String taskNo;

    /** 客户ID */
    private String custId;

    /** 执行人工号 */
    private String assigneeEmpId;

    /** 任务类型: FIRST_TOUCH / FOLLOW_UP */
    private String taskType;
}
