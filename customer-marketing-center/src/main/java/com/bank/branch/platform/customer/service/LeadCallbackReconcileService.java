package com.bank.branch.platform.customer.service;

import com.bank.branch.platform.customer.entity.CustLead;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * 线索回调对账 Service —— 抽离 {@code WorkflowCallbackListener} 中
 * APPROVED/REJECTED 推进 + 事件发布的公共逻辑，供 listener 主路径与
 * {@code LeadCallbackCompensationService} 补偿路径共用。
 * <p>
 * <strong>幂等保护</strong>：两条 reconcile 路径均通过
 * {@code conditionalUpdateStatus(IN_APPROVAL → 目标态)} 实现 CAS-like 推进，
 * 重复进入或并发推进时第二次返回 0 行受影响并提前返回，<strong>不</strong>再
 * 发布下游事件，避免 cust_master 重复创建 / 失效。
 * </p>
 * <p>
 * <strong>红 commit skeleton</strong>：本类两个方法均抛
 * {@link UnsupportedOperationException}，对应红 IT 应在 reconcile 路径上 fail；
 * 绿 commit 中替换为完整实现。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeadCallbackReconcileService {

    private final ApplicationEventPublisher eventPublisher;

    /**
     * 处理审批通过 —— 状态从 IN_APPROVAL 推进到 APPROVED 并按 leadOp 发布
     * {@code LeadApprovedEvent} / {@code LeadDeletedEvent} / {@code LeadUpdatedEvent}。
     * <p>
     * 对应原 {@code WorkflowCallbackListener.handleApproved} 内联实现，抽出后供
     * listener 主路径与补偿任务共用。
     * </p>
     *
     * @param lead              线索实体（必须为 IN_APPROVAL 状态，否则 conditionalUpdate 返 0 早返回）
     * @param processInstanceId 流程实例 ID（用于日志归因）
     */
    public void reconcileApproved(CustLead lead, String processInstanceId) {
        throw new UnsupportedOperationException("FU-14 reconcileApproved not implemented yet");
    }

    /**
     * 处理审批驳回 —— 状态从 IN_APPROVAL 推进到 REJECTED 并发布
     * {@code LeadRejectedEvent}（V1 暂无下游消费，保留扩展点）。
     * <p>
     * 对应原 {@code WorkflowCallbackListener.handleRejected} 内联实现，抽出后供
     * listener 主路径与补偿任务共用。
     * </p>
     *
     * @param lead              线索实体（必须为 IN_APPROVAL 状态）
     * @param processInstanceId 流程实例 ID（用于日志归因）
     * @param rejectReason      驳回原因，可为 null（补偿场景由调用方填占位）
     */
    public void reconcileRejected(CustLead lead, String processInstanceId, String rejectReason) {
        throw new UnsupportedOperationException("FU-14 reconcileRejected not implemented yet");
    }
}
