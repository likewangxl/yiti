package com.bank.branch.platform.performance.event;

import lombok.Getter;

/**
 * 目标调整审批通过事件（V1.2 Q1.1）.
 *
 * <p>触发场景：目标调整 BPMN 审批流程 End 事件 Listener 调用 performance 完成处理后。
 *
 * <p>字段遵循 V1.2 DDL 权威修订：使用 subjectType/subjectId/cycleKey，
 * 而非 V1.1 规划里的 empId/metricCode（cycleKey 形如 "2026Q2"）。
 *
 * <p>消费方：配置缓存失效（{@code perf:target_value:*}）、报表快照重算、通知推送。
 *
 * @since V1.2 Q1.1
 */
@Getter
public class TargetAdjustmentApprovedEvent extends PerfDomainEvent {

    /** 调整申请 ID. */
    private final String applyId;

    /** 目标方案 ID（varchar(32)）. */
    private final String planId;

    /** 主体类型: EMP / ORG. */
    private final String subjectType;

    /** 主体 ID. */
    private final String subjectId;

    /** 周期键，形如 "2026Q2" / "202604" / "2026". */
    private final String cycleKey;

    /** 审批通过人 empId. */
    private final String approvedBy;

    public TargetAdjustmentApprovedEvent(String traceId,
                                         String applyId,
                                         String planId,
                                         String subjectType,
                                         String subjectId,
                                         String cycleKey,
                                         String approvedBy) {
        super(traceId);
        this.applyId = applyId;
        this.planId = planId;
        this.subjectType = subjectType;
        this.subjectId = subjectId;
        this.cycleKey = cycleKey;
        this.approvedBy = approvedBy;
    }

    @Override
    public String topic() {
        return "performance.target-adjustment.approved.v1";
    }
}
