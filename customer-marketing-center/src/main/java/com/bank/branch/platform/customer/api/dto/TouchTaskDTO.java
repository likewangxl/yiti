package com.bank.branch.platform.customer.api.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 触达任务对外 DTO
 * <p>
 * 用于跨模块传递客户触达任务信息，包含任务类型、SLA 预警、完成结果及日志数量等。
 * 触达任务是客户营销的核心执行单元，与工作流引擎深度集成。
 * </p>
 */
@Data
public class TouchTaskDTO {

    /** 触达任务 ID */
    private String id;

    /** 客户 ID */
    private String custId;

    /** 客户名称 */
    private String custName;

    /** 归属机构 ID */
    private String orgId;

    /** 执行人工号 */
    private String assigneeEmpId;

    /** 执行人姓名 */
    private String assigneeEmpName;

    /** 任务类型: FIRST_TOUCH / FOLLOW_UP */
    private String taskType;

    /** 任务状态: PENDING / IN_PROGRESS / SUCCESS / CANCELLED */
    private String taskStatus;

    /** 工作流业务键 */
    private String businessKey;

    /** 已停用兼容字段；正式模型一任务多日志，调用方应按任务查询日志。 */
    private String worklogId;

    /** SLA 截止时间 */
    private LocalDateTime slaDeadline;

    /** 是否触发 SLA 预警 */
    private Boolean slaWarning;

    /** 预计完成时间 */
    private LocalDateTime expectedFinishAt;

    /** 实际完成时间 */
    private LocalDateTime actualFinishAt;

    /** 完成结果: SUCCESS / CANCELLED */
    private String finishResult;

    /** 取消原因 */
    private String cancelReason;

    /** 触达日志数量 */
    private Integer logCount;

    /** 任务下所有有效日志登记的参与人工号 */
    private List<String> participantEmpIds;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
