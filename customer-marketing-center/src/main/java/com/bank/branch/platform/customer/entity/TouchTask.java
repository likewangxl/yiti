package com.bank.branch.platform.customer.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 触达任务实体，对应 touch_task 表。
 * <p>
 * 客户认领后系统自动创建 FIRST_TOUCH 类型触达任务，首次成功后可创建 FOLLOW_UP 任务。
 * sla_status 由定时任务刷新：GREEN→YELLOW（达预警时间）→RED（超计划完成时间且未完成）。
 * task_status 枚举：PENDING/SUCCESS/CANCELLED。
 * 注意：该表使用 org_id（非 assignee_org_id），无 claim_id 和 deleted 字段。
 * </p>
 */
@Data
public class TouchTask {

    /** 主键ID（UUID，32位去连字符），对应 id */
    private String id;

    /** 任务编号（对外展示，唯一），对应 task_no */
    private String taskNo;

    /** 客户ID（关联 cust_master.id），对应 cust_id */
    private String custId;

    /** 所属机构代码，对应 org_id */
    private String orgId;

    /** 执行人（员工工号），对应 assignee_emp_id */
    private String assigneeEmpId;

    /** 任务类型：FIRST_TOUCH-首次触达/FOLLOW_UP-后续跟进，对应 task_type */
    private String taskType;

    /** 任务状态：PENDING/SUCCESS/CANCELLED，对应 task_status */
    private String taskStatus;

    /** 计划完成时间，对应 plan_finish_time */
    private LocalDateTime planFinishTime;

    /** 预警时间（SLA 黄灯阈值），对应 warning_time */
    private LocalDateTime warningTime;

    /** SLA 状态：GREEN/YELLOW/RED，对应 sla_status */
    private String slaStatus;

    /** SLA 预警标记（契约字段），slaStatus 为 YELLOW 或 RED 时为 true，对应 sla_warning */
    private Boolean slaWarning;

    /** 流程业务键（格式 TOUCH:{taskId}），对应 business_key */
    private String businessKey;

    /** 成功完成时间，对应 success_time */
    private LocalDateTime successTime;

    /** 取消时间，对应 cancel_time */
    private LocalDateTime cancelTime;

    /** 创建时间，对应 created_time */
    private LocalDateTime createdTime;

    /** 最后更新时间，对应 updated_time */
    private LocalDateTime updatedTime;
}
