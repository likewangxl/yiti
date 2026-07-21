package com.bank.branch.platform.workflow.api.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 转交历史展示项 DTO：审批流监控详情抽屉底部「转交历史」用。
 * <p>
 * 与收发件箱的 {@link TransferItemDTO} 的区别在于<b>额外解析了三方姓名</b>——历史视图要回答
 * 「谁转给谁、谁认领了、谁拒绝了、为什么拒绝」，只给工号可读性太差；收发件箱是当事人自己看
 * 自己的记录，工号足够，故不动其契约。姓名由 {@code getUserByEmpIds} 批量解析，非逐条查询。
 * </p>
 */
@Data
public class TransferHistoryDTO {

    /** 转交记录ID */
    private String id;

    /** 节点标识 */
    private String nodeKey;

    /** 节点名称（转交发起时的任务名快照） */
    private String nodeName;

    /** 被转出者工号；为空表示本次是「指派」（任务尚无人签收，从候选池直接指定） */
    private String fromEmpId;

    /** 被转出者姓名，fromEmpId 为空时同为空 */
    private String fromName;

    /** 发起人工号（秘书岗/行长，可能不同于被转出者） */
    private String initiatorEmpId;

    /** 发起人姓名 */
    private String initiatorName;

    /** 接收人工号 */
    private String toEmpId;

    /** 接收人姓名 */
    private String toName;

    /**
     * 状态：PENDING_ACCEPT 待认领 / ACCEPTED 已认领 / REJECTED 已拒绝
     * / CANCELLED 已撤回 / INVALIDATED 已失效（原任务被删）
     */
    private String status;

    /** 转交原因（发起时填写，必填） */
    private String transferReason;

    /** 拒绝原因（仅 REJECTED 有值） */
    private String rejectReason;

    /** 发起时间 */
    private LocalDateTime initiatedTime;

    /** 决策时间（认领/拒绝/撤回发生的时间；PENDING_ACCEPT 时为空） */
    private LocalDateTime decidedTime;
}
