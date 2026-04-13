package com.bank.branch.platform.customer.dto.resp;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 触达报告视图对象（聚合查询结果）。
 * <p>
 * 由 TouchReportMapper 通过 touch_task LEFT JOIN cust_master 聚合而来，
 * 包含任务基本信息、客户名称以及该任务关联的日志条数。
 * </p>
 */
@Data
public class TouchReportVO {

    /** 任务编号（对外展示，唯一），对应 touch_task.task_no */
    private String taskNo;

    /** 任务类型：FIRST_TOUCH/FOLLOW_UP，对应 touch_task.task_type */
    private String taskType;

    /** 任务状态：PENDING/SUCCESS/CANCELLED，对应 touch_task.task_status */
    private String taskStatus;

    /** SLA 状态：GREEN/YELLOW/RED，对应 touch_task.sla_status */
    private String slaStatus;

    /** 客户名称（来自 cust_master.cust_name，客户已删除则为 null） */
    private String custName;

    /** 执行人工号，对应 touch_task.assignee_emp_id */
    private String assigneeEmpId;

    /** 所属机构代码，对应 touch_task.org_id */
    private String orgId;

    /** 计划完成时间，对应 touch_task.plan_finish_time */
    private LocalDateTime planFinishTime;

    /** 成功完成时间，对应 touch_task.success_time */
    private LocalDateTime successTime;

    /** 关联触达日志条数（子查询 COUNT） */
    private Long logCount;
}
