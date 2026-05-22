package com.bank.branch.platform.workflow.api.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 待办/已办任务列表响应DTO。
 * <p>
 * 包含任务基础信息、所属流程业务信息、SLA红绿灯状态和可签收标志。
 * 用于待办列表（GET /api/workflow/tasks）和已办列表（GET /api/workflow/tasks/done）接口。
 * </p>
 */
@Data
public class TaskRespDTO {

    /** Flowable 任务ID */
    private String taskId;

    /** 流程实例ID */
    private String processInstanceId;

    /** 业务键（格式：BIZ_TYPE:{id}） */
    private String businessKey;

    /** 业务类型：LEAD / LOAN / SUPPORT / TOUCH / TARGET_ADJUST / ALLOC_ADJUST */
    private String bizType;

    /** 业务对象ID */
    private String bizId;

    /** 流程标题 */
    private String title;

    /** 发起人工号 */
    private String startUser;

    /** 发起人姓名（按 startUser 反查 PT_USER.userchnname） */
    private String startUserName;

    /** 发起人机构编码（按 startUser 反查 EXT_USER_ORG → ORG_CODE） */
    private String startOrgId;

    /** 发起人机构名称（按 startOrgId 反查 EXT_ORG_INFO.ORG_NAME） */
    private String startOrgName;

    /** 流程发起时间 */
    private LocalDateTime startTime;

    /** 节点名称（如"公司部审核"） */
    private String taskName;

    /** 节点 KEY（taskDefinitionKey，如 biz_dept_review），前端按 nodeKey 决定审批面板表单 */
    private String nodeKey;

    /** 到达节点时间 */
    private LocalDateTime taskCreateTime;

    /** 当前处理人工号（未签收时为 null） */
    private String assignee;

    /** 候选组列表 */
    private List<String> candidateGroups;

    /** SLA红绿灯状态：GREEN / YELLOW / RED */
    private String slaStatus;

    /**
     * 流程实例状态：RUNNING（审批中）/ COMPLETED（完结，业务 APPROVED）/
     * CANCELLED（驳回，业务 REJECTED）。取自 biz_process_map.process_status，
     * 前端按此映射"审批中 / 完结 / 驳回"显示。已办列表必填，待办列表通常为 RUNNING。
     */
    private String processStatus;

    /** 黄灯预警时间点 */
    private LocalDateTime warningTime;

    /** 红灯超时时间点 */
    private LocalDateTime timeoutTime;

    /** 是否可领取（true = 用户在候选组中且任务未签收） */
    private Boolean claimable;

    // ========== 已办额外字段（通过 completeTime 是否为 null 区分）==========

    /** 办理完成时间 */
    private LocalDateTime completeTime;

    /** 审批结果：APPROVE / REJECT */
    private String approvalResult;

    /** 办理时填写的意见 */
    private String opinion;
}
