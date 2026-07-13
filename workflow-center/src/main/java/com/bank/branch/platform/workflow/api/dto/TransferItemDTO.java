package com.bank.branch.platform.workflow.api.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 转交待认领展示项 DTO，收件箱（{@code GET /api/workflow/transfers/inbox}）与发件箱
 * （{@code GET /api/workflow/transfers/outbox}）复用同一形状——字段对齐
 * {@code WfTaskTransfer} 实体（{@code TaskTransferService#listInbox}/{@code listOutbox}
 * 内部转换），不直接把实体暴露给 Controller/前端。
 */
@Data
public class TransferItemDTO {

    /** 转交记录ID */
    private String id;

    /** Flowable 流程实例ID */
    private String processInstanceId;

    /** 任务ID */
    private String taskId;

    /** 业务键（格式：BIZ_TYPE:{id}），可能为空（查不到 biz_process_map 映射时） */
    private String businessKey;

    /** 业务类型，可能为空 */
    private String bizType;

    /** 节点定义 Key */
    private String nodeKey;

    /** 节点名称 */
    private String nodeName;

    /** 原办理人工号（被转出者，取任务 assignee，可能不同于发起人） */
    private String fromEmpId;

    /** 发起人工号（可能是原办理人本人，也可能是代为发起的秘书） */
    private String initiatorEmpId;

    /** 接收人工号 */
    private String toEmpId;

    /** 发起人机构编码 */
    private String orgCode;

    /** 转交状态：PENDING_ACCEPT / ACCEPTED / REJECTED / CANCELLED */
    private String status;

    /** 转交原因（发起时填写） */
    private String transferReason;

    /** 拒绝理由（仅 REJECTED 状态有值） */
    private String rejectReason;

    /** 发起时间 */
    private LocalDateTime initiatedTime;

    /** 决策时间（认领/拒绝/撤回的时间，PENDING_ACCEPT 状态为空） */
    private LocalDateTime decidedTime;
}
