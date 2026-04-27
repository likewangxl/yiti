package com.bank.branch.platform.customer.service;

import com.bank.branch.platform.customer.entity.CustLead;
import com.bank.branch.platform.customer.enums.LeadOp;
import com.bank.branch.platform.customer.enums.LeadStatus;
import com.bank.branch.platform.customer.event.LeadApprovedEvent;
import com.bank.branch.platform.customer.event.LeadDeletedEvent;
import com.bank.branch.platform.customer.event.LeadRejectedEvent;
import com.bank.branch.platform.customer.mapper.CustLeadMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 线索回调对账 Service —— 抽离 {@code WorkflowCallbackListener} 中
 * APPROVED/REJECTED 推进 + 事件发布的公共逻辑，供 listener 主路径与
 * {@link LeadCallbackCompensationService} 补偿路径共用。
 * <p>
 * <strong>幂等保护</strong>：两条 reconcile 路径均通过
 * {@code conditionalUpdateStatus(IN_APPROVAL → 目标态)} 实现 CAS-like 推进，
 * 重复进入或并发推进时第二次返回 0 行受影响并提前返回，<strong>不</strong>再
 * 发布下游事件，避免 cust_master 重复创建 / 失效。
 * </p>
 * <p>
 * <strong>注意</strong>：行为与原 listener handleApproved / handleRejected 内联实现
 * 完全一致，不修改任何业务语义；本 Service 只做"代码移动 + 共用"。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeadCallbackReconcileService {

    private final CustLeadMapper leadMapper;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 处理审批通过 —— 状态从 IN_APPROVAL 推进到 APPROVED 并按 leadOp 发布
     * {@link LeadApprovedEvent} / {@link LeadDeletedEvent}。
     * <p>
     * 与原 {@code WorkflowCallbackListener.handleApproved} 内联实现完全一致：
     * <ul>
     *   <li>{@code conditionalUpdateStatus(IN_APPROVAL → APPROVED)} 返 0 → 早返回（幂等）；</li>
     *   <li>{@code leadOp == DELETE} → 发布 {@link LeadDeletedEvent}；</li>
     *   <li>否则（{@code CREATE / UPDATE}）→ 发布 {@link LeadApprovedEvent}。</li>
     * </ul>
     * </p>
     * <p>
     * <strong>事务策略</strong>：使用 {@code REQUIRES_NEW} 独立事务，让本方法内
     * {@code publishEvent(LeadApprovedEvent / LeadDeletedEvent)} 在新事务内 publish；
     * 事务 commit 后下游 {@code @TransactionalEventListener(AFTER_COMMIT)}
     * （{@link com.bank.branch.platform.customer.listener.LeadApprovedListener}
     * 等）才能正确触发链式处理。这同时支持两种调用路径：
     * <ul>
     *   <li>listener 主路径（{@code WorkflowCallbackListener.onProcessCompleted}
     *       本身已是 REQUIRES_NEW，嵌套 reconcile REQUIRES_NEW 仍正确）；</li>
     *   <li>补偿任务路径（{@code LeadCallbackCompensationService.scanAndCompensate}
     *       无外层事务，REQUIRES_NEW 在此处提供事务边界让 AFTER_COMMIT 链生效）。</li>
     * </ul>
     * </p>
     *
     * @param lead              线索实体（必须为 IN_APPROVAL 状态，否则 conditionalUpdate 返 0 早返回）
     * @param processInstanceId 流程实例 ID（用于日志归因）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void reconcileApproved(CustLead lead, String processInstanceId) {
        String leadId = lead.getId();

        // 幂等保护：仅当当前状态为 IN_APPROVAL 时才推进为 APPROVED
        int rowsAffected = leadMapper.conditionalUpdateStatus(
                leadId, LeadStatus.IN_APPROVAL.getCode(), LeadStatus.APPROVED.getCode(), "SYSTEM");
        if (rowsAffected == 0) {
            log.warn("[LeadCallbackReconcileService] 线索 {} 状态已被其他实例处理，跳过 APPROVED 事件发布，processInstanceId={}",
                    leadId, processInstanceId);
            return;
        }
        log.info("[LeadCallbackReconcileService] 线索 {} 审批通过，状态更新为 APPROVED", leadId);

        // 根据操作类型发布不同事件
        if (LeadOp.DELETE.getCode().equals(lead.getLeadOp())) {
            // 删除类型：发布 LeadDeletedEvent
            LeadDeletedEvent deletedEvent = new LeadDeletedEvent(
                    leadId,
                    lead.getLeadNo(),
                    lead.getSourceCustId(),
                    "SYSTEM"
            );
            eventPublisher.publishEvent(deletedEvent);
            log.info("[LeadCallbackReconcileService] 发布 LeadDeletedEvent, leadId={}", leadId);
        } else {
            // CREATE/UPDATE 类型：发布 LeadApprovedEvent
            LeadApprovedEvent approvedEvent = new LeadApprovedEvent(
                    leadId,
                    lead.getLeadNo(),
                    lead.getLeadOp(),
                    lead.getSourceCustId(),
                    lead.getOwnerOrgId(),
                    "SYSTEM"
            );
            eventPublisher.publishEvent(approvedEvent);
            log.info("[LeadCallbackReconcileService] 发布 LeadApprovedEvent, leadId={}, leadOp={}", leadId, lead.getLeadOp());
        }
    }

    /**
     * 处理审批驳回 —— 状态从 IN_APPROVAL 推进到 REJECTED 并发布
     * {@link LeadRejectedEvent}（V1 暂无下游消费，保留扩展点）。
     * <p>
     * 与原 {@code WorkflowCallbackListener.handleRejected} 内联实现完全一致：
     * 驳回路径既不创建 cust_master（CREATE/UPDATE 时）也不失效（DELETE 时），
     * 仅推进线索状态并发布事件。
     * </p>
     * <p>
     * <strong>事务策略</strong>：与 {@link #reconcileApproved} 同 {@code REQUIRES_NEW}，
     * 让 {@code publishEvent(LeadRejectedEvent)} 在新事务内 publish，
     * 兼容补偿任务无外层事务的调用上下文。
     * </p>
     *
     * @param lead              线索实体（必须为 IN_APPROVAL 状态）
     * @param processInstanceId 流程实例 ID（用于日志归因）
     * @param rejectReason      驳回原因，可为 null（补偿场景由调用方填占位）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void reconcileRejected(CustLead lead, String processInstanceId, String rejectReason) {
        String leadId = lead.getId();

        // 幂等保护：仅当当前状态为 IN_APPROVAL 时才推进为 REJECTED
        int rowsAffected = leadMapper.conditionalUpdateStatus(
                leadId, LeadStatus.IN_APPROVAL.getCode(), LeadStatus.REJECTED.getCode(), "SYSTEM");
        if (rowsAffected == 0) {
            log.warn("[LeadCallbackReconcileService] 线索 {} 状态已被其他实例处理，跳过 REJECTED 事件发布，processInstanceId={}",
                    leadId, processInstanceId);
            return;
        }
        log.info("[LeadCallbackReconcileService] 线索 {} 审批驳回，状态更新为 REJECTED，processInstanceId={}",
                leadId, processInstanceId);

        // 发布 LeadRejectedEvent（V1 暂无下游消费，保留扩展点）
        LeadRejectedEvent rejectedEvent = new LeadRejectedEvent(
                leadId,
                lead.getLeadNo(),
                lead.getLeadOp(),
                lead.getSourceCustId(),
                lead.getOwnerOrgId(),
                rejectReason,
                "SYSTEM"
        );
        eventPublisher.publishEvent(rejectedEvent);
        log.info("[LeadCallbackReconcileService] 发布 LeadRejectedEvent, leadId={}, leadOp={}, reason={}",
                leadId, lead.getLeadOp(), rejectReason);
    }
}
