package com.bank.branch.platform.customer.api.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 在途流程对外 DTO
 * <p>
 * 用于跨模块传递当前正在运行中的工作流概要信息，包含业务类型、归属机构人员及流程启动时间。
 * 主要供 workflow-center 和 report-analytics-center 查询使用，展示流程运行全貌。
 * </p>
 */
@Data
public class RunningFlowDTO {

    /** 业务类型: LEAD / TOUCH_TASK / LOAN_APPROVAL / MIDFIELD_SUPPORT */
    private String bizType;

    /** 业务 ID */
    private String bizId;

    /** 工作流业务键 */
    private String businessKey;

    /** 归属机构 ID */
    private String orgId;

    /** 归属机构名称 */
    private String orgName;

    /** 发起人工号 */
    private String empId;

    /** 发起人姓名 */
    private String empName;

    /** 流程状态: PENDING / IN_PROGRESS */
    private String status;

    /** 流程启动时间 */
    private LocalDateTime startedAt;
}
