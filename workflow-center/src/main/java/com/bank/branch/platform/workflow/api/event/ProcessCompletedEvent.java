package com.bank.branch.platform.workflow.api.event;

/**
 * 流程完成事件，由 {@code ProcessCompletedListener} 在 Flowable 流程实例结束时发布，
 * 供其他模块通过 Spring {@code @TransactionalEventListener} 监听处理后续业务逻辑。
 *
 * <p><strong>包位置（FU-17，2026-04-29 由 {@code workflow.listener} 迁移至 {@code workflow.api.event}）</strong>：</p>
 * <p>跨模块依赖规则要求 customer / bizapp / performance 等业务模块只依赖 workflow-center 的
 * {@code api/} 子包，不依赖 {@code listener/} 内部实现。本 record 从 {@code ProcessCompletedListener}
 * 内部嵌套类抽出为独立顶级 record，归属 {@code api.event} 子包，明确"事件载荷=对外契约"语义。</p>
 *
 * <p><strong>语义说明</strong>：</p>
 * <ul>
 *   <li>{@code outcome} — 下游业务语义（{@code APPROVED} / {@code REJECTED}），与
 *       {@code biz_process_map.processStatus}（{@code COMPLETED} / {@code CANCELLED}）独立，不可混淆。
 *       例如 {@code approved=false} 在 {@code processStatus} 中映射为 {@code CANCELLED}，
 *       在 {@code event.outcome} 中映射为 {@code REJECTED}。</li>
 *   <li>{@code reason} — 审批备注或驳回原因，可为 {@code null}。</li>
 * </ul>
 *
 * @param processInstanceId 流程实例 ID
 * @param businessKey       业务键（格式：{@code BIZ_TYPE:bizId}，如 {@code LOAN:LOAN001}）
 * @param outcome           下游业务审批结论（{@code APPROVED} / {@code REJECTED}）
 * @param reason            审批原因或驳回说明，可为 {@code null}
 */
public record ProcessCompletedEvent(
        String processInstanceId,
        String businessKey,
        String outcome,
        String reason
) {
}
