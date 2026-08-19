package com.bank.branch.platform.customer.service;

import com.bank.branch.platform.customer.entity.CustLead;
import com.bank.branch.platform.customer.enums.LeadStatus;
import com.bank.branch.platform.customer.event.LeadRejectedEvent;
import com.bank.branch.platform.customer.mapper.CustLeadMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 线索回调对账 Service —— 抽离 {@code WorkflowCallbackListener} 中
 * APPROVED/REJECTED 推进 + 同步处理的公共逻辑，供 listener 主路径与
 * {@link LeadCallbackCompensationService} 补偿路径共用。
 * <p>
 * <strong>幂等保护</strong>：两条 reconcile 路径均通过
 * {@code conditionalUpdateStatus(IN_APPROVAL → 目标态)} 实现 CAS-like 推进，
 * 重复进入或并发推进时第二次返回 0 行受影响并提前返回，<strong>不</strong>再
 * 触发下游处理，避免 CUSTOMER_MARKET_CUSTOMER 重复创建 / 失效。
 * </p>
 * <p>
 * <strong>V1.11#1 方向 C 改造</strong>：reconcileApproved 内原 publishEvent
 * (LeadApprovedEvent / LeadDeletedEvent) 已改为同步调用
 * {@link CustMasterAssemblerService#assembleFromLead}，消除嵌套
 * {@code @TransactionalEventListener(AFTER_COMMIT)} 导致 INSERT 不持久化的 bug。
 * reconcileRejected 内 publishEvent(LeadRejectedEvent) 保留（REJECTED 路径无嵌套监听器，
 * 且 V1 暂无消费，保留扩展点）。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeadCallbackReconcileService {

    private final CustLeadMapper leadMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final CustMasterAssemblerService assemblerService;

    /**
     * 处理审批通过 —— 状态从 IN_APPROVAL 推进到 APPROVED 并同步调用
     * {@link CustMasterAssemblerService#assembleFromLead} 完成客户主档装配。
     * <p>
     * 流程：
     * <ul>
     *   <li>{@code conditionalUpdateStatus(IN_APPROVAL → APPROVED)} 返 0 → 早返回（幂等）；</li>
     *   <li>推进成功后同步调用 {@link CustMasterAssemblerService#assembleFromLead}，
     *       由 assembler 内部按 leadOp（CREATE/UPDATE/DELETE）分支处理。</li>
     * </ul>
     * </p>
     * <p>
     * <strong>事务策略</strong>：使用 {@code REQUIRES_NEW} 独立事务，状态更新与
     * 客户主档装配在同一新事务中完成并提交，同时支持两种调用路径：
     * <ul>
     *   <li>listener 主路径（{@code WorkflowCallbackListener.onProcessCompleted}
     *       本身已是 REQUIRES_NEW，嵌套 reconcile REQUIRES_NEW 仍正确）；</li>
     *   <li>补偿任务路径（{@code LeadCallbackCompensationService.scanAndCompensate}
     *       无外层事务，REQUIRES_NEW 在此处提供事务边界）。</li>
     * </ul>
     * </p>
     * <p>
     * <strong>V1.11#1 方向 C 改造</strong>：原 publishEvent(LeadApprovedEvent /
     * LeadDeletedEvent) 改为同步调用 assembler，消除嵌套
     * {@code @TransactionalEventListener(AFTER_COMMIT)} 导致 INSERT 不持久化的 bug。
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
            log.warn("[LeadCallbackReconcileService] 线索 {} 状态已被其他实例处理，跳过装配，processInstanceId={}",
                    leadId, processInstanceId);
            return;
        }
        log.info("[LeadCallbackReconcileService] 线索 {} 审批通过，状态更新为 APPROVED", leadId);

        // 同步调用 assembler，由 assembler 内部按 leadOp（CREATE/UPDATE/DELETE）分支处理
        assemblerService.assembleFromLead(lead);
        log.info("[LeadCallbackReconcileService] 已同步装配客户主档, leadId={}, leadOp={}", leadId, lead.getLeadOp());
    }

    /**
     * 处理审批驳回 —— 状态从 IN_APPROVAL 推进到 REJECTED 并发布
     * {@link LeadRejectedEvent}（V1 暂无下游消费，保留扩展点）。
     * <p>
     * 与原 {@code WorkflowCallbackListener.handleRejected} 内联实现完全一致：
     * 驳回路径既不创建 CUSTOMER_MARKET_CUSTOMER（CREATE/UPDATE 时）也不失效（DELETE 时），
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

        // 事件不携带最终办理人工号，先以 SYSTEM 固化回调时间和驳回原因；
        // 具体办理人仍可从工作流已办明细查询。
        CustLead reviewSnapshot = new CustLead();
        reviewSnapshot.setId(leadId);
        reviewSnapshot.setReviewedBy("SYSTEM");
        reviewSnapshot.setReviewedTime(LocalDateTime.now());
        reviewSnapshot.setRejectReason(rejectReason);
        reviewSnapshot.setUpdatedBy("SYSTEM");
        reviewSnapshot.setUpdatedTime(LocalDateTime.now());
        leadMapper.updateById(reviewSnapshot);

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
