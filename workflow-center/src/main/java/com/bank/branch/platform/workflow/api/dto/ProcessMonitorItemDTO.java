package com.bank.branch.platform.workflow.api.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审批流监控列表项 DTO。
 * <p>用于监控页展示流程实例的关键信息，含发起人/当前处理人的机构编码（批量补全，避免 N+1）。</p>
 */
@Data
public class ProcessMonitorItemDTO {

    /** Flowable 流程实例ID */
    private String processInstanceId;

    /** 业务键（格式：BIZ_TYPE:{id}） */
    private String businessKey;

    /** 业务类型 */
    private String bizType;

    /** 流程标题 */
    private String title;

    /** 流程状态：RUNNING / COMPLETED / CANCELLED */
    private String processStatus;

    /** 发起人工号 */
    private String startUser;

    /** 发起人主机构编码（批量补） */
    private String startUserOrgCode;

    /** 当前处理人工号 */
    private String currentAssignee;

    /** 当前处理人主机构编码（批量补） */
    private String currentAssigneeOrgCode;

    /**
     * 当前活跃 Flowable 任务ID（批量补，见 {@code ProcessMonitorService#resolveActiveTaskIds}）。
     * <p>
     * 供前端"审批流监控"页的转交入口使用——转交发起端点
     * （{@code POST /api/workflow/monitor/tasks/{taskId}/transfer}）按 taskId 而非
     * processInstanceId 定位任务，本字段补上这一环，否则前端拿不到可用于发起转交的 taskId。
     * 已结束流程（COMPLETED/CANCELLED）或查询瞬间恰好处于节点切换空档时可能为空。
     * </p>
     */
    private String currentTaskId;

    /** 发起时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;
}
