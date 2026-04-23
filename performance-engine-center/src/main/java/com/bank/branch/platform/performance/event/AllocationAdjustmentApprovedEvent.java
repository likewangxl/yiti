package com.bank.branch.platform.performance.event;

import lombok.Getter;

/**
 * 客户分配调整审批通过事件（V1.2 Q1.1）.
 *
 * <p>触发场景：客户分配调整 BPMN 审批流程 End 事件 Listener 调用 performance 完成处理后。
 *
 * <p>消费方：cust_alloc_relation 缓存失效、报表快照重算、客户经理通知。
 *
 * @since V1.2 Q1.1
 */
@Getter
public class AllocationAdjustmentApprovedEvent extends PerfDomainEvent {

    /** 调整申请 ID. */
    private final String applyId;

    /** 客户 ID. */
    private final String custId;

    /** 分配维度 (OWNER / SERVICE / CHANNEL 等). */
    private final String allocDim;

    /** 业务种类 (DEPOSIT / LOAN 等). */
    private final String bizKind;

    /** 本次调整涉及的明细条目数. */
    private final int itemCount;

    /** 审批通过人 empId. */
    private final String approvedBy;

    public AllocationAdjustmentApprovedEvent(String traceId,
                                             String applyId,
                                             String custId,
                                             String allocDim,
                                             String bizKind,
                                             int itemCount,
                                             String approvedBy) {
        super(traceId);
        this.applyId = applyId;
        this.custId = custId;
        this.allocDim = allocDim;
        this.bizKind = bizKind;
        this.itemCount = itemCount;
        this.approvedBy = approvedBy;
    }

    @Override
    public String topic() {
        return "performance.allocation-adjustment.approved.v1";
    }
}
