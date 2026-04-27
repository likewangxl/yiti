package com.bank.branch.platform.customer.listener;

import com.bank.branch.platform.customer.entity.CustLead;
import com.bank.branch.platform.customer.enums.LeadOp;
import com.bank.branch.platform.customer.enums.LeadStatus;
import com.bank.branch.platform.customer.event.LeadApprovedEvent;
import com.bank.branch.platform.customer.event.LeadDeletedEvent;
import com.bank.branch.platform.customer.event.LeadRejectedEvent;
import com.bank.branch.platform.customer.mapper.CustLeadMapper;
import com.bank.branch.platform.workflow.listener.ProcessCompletedListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;

/**
 * 工作流回调监听器。
 * <p>
 * 监听工作流中心发布的 {@link ProcessCompletedListener.ProcessCompletedEvent} 事件，
 * 根据 businessKey 前缀（LEAD:）识别线索相关流程，按 outcome 分发审批结果（与
 * {@code bizapp.LoanWorkflowListener} pattern 对齐）：
 * <ul>
 *   <li>APPROVED + leadOp=CREATE/UPDATE → 状态更新为 APPROVED，发布 {@link LeadApprovedEvent}</li>
 *   <li>APPROVED + leadOp=DELETE → 状态更新为 APPROVED，发布 {@link LeadDeletedEvent}</li>
 *   <li>REJECTED → 状态更新为 REJECTED，发布 {@link LeadRejectedEvent}（不创建/失效 cust_master）</li>
 * </ul>
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowCallbackListener {

    private final CustLeadMapper leadMapper;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 监听工作流流程完成事件，处理线索状态流转。
     * <p>
     * businessKey 格式为 {@code LEAD:{leadId}}，非线索相关流程直接忽略。
     * 按 {@code event.outcome()} 分发：
     * APPROVED → {@link #handleApproved}；REJECTED → {@link #handleRejected}。
     * 与 {@code bizapp.LoanWorkflowListener} pattern 对齐。
     * </p>
     *
     * <p>
     * <strong>事务策略（P0 bug 修复）</strong>：
     * <ul>
     *   <li>{@code @Transactional(REQUIRES_NEW)}：在 AFTER_COMMIT 阶段开启新事务，
     *       让本方法内 {@code publishEvent(LeadApprovedEvent / LeadDeletedEvent)} 在新事务内 publish；</li>
     *   <li>下游 {@link LeadApprovedListener} / {@link LeadDeletedListener} 都是
     *       {@code @TransactionalEventListener(AFTER_COMMIT)} 无 fallbackExecution，
     *       必须在事务内 publish 它们才能正确触发；</li>
     *   <li>移除 {@code fallbackExecution = true}：与 REQUIRES_NEW 配合时，
     *       AFTER_COMMIT + 显式新事务 = 既保证流程完成事务已 commit，又能让下游 listener 正确链接。</li>
     * </ul>
     * 详见红 IT {@code WorkflowCallbackEventChainBugIT}（commit message 中引用）。
     * </p>
     *
     * @param event 流程完成事件（来自 workflow-center ProcessCompletedListener）
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void onProcessCompleted(ProcessCompletedListener.ProcessCompletedEvent event) {
        String businessKey = event.businessKey();
        log.info("[WorkflowCallbackListener.onProcessCompleted] processInstanceId={}, businessKey={}, outcome={}",
                event.processInstanceId(), businessKey, event.outcome());

        // 只处理线索相关流程
        if (businessKey == null || !businessKey.startsWith("LEAD:")) {
            return;
        }

        String leadId = businessKey.substring("LEAD:".length());

        // 查询线索，如果不存在则跳过（幂等保护）
        CustLead lead = leadMapper.selectById(leadId);
        if (lead == null) {
            log.warn("[WorkflowCallbackListener] 线索 {} 不存在，跳过状态更新", leadId);
            return;
        }

        // 按 event.outcome() 分发审批结果（与 bizapp.LoanWorkflowListener pattern 对齐）
        if ("REJECTED".equals(event.outcome())) {
            handleRejected(lead, event.processInstanceId(), event.reason());
        } else {
            handleApproved(lead, event.processInstanceId());
        }
    }

    /**
     * 处理审批通过逻辑：更新线索状态为 APPROVED，根据操作类型发布对应事件。
     *
     * @param lead              线索实体
     * @param processInstanceId 流程实例ID
     */
    private void handleApproved(CustLead lead, String processInstanceId) {
        String leadId = lead.getId();

        // 更新状态为 APPROVED
        leadMapper.updateStatusById(leadId, LeadStatus.APPROVED.getCode(), "SYSTEM");
        log.info("[WorkflowCallbackListener] 线索 {} 审批通过，状态更新为 APPROVED", leadId);

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
            log.info("[WorkflowCallbackListener] 发布 LeadDeletedEvent, leadId={}", leadId);
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
            log.info("[WorkflowCallbackListener] 发布 LeadApprovedEvent, leadId={}, leadOp={}", leadId, lead.getLeadOp());
        }
    }

    /**
     * 处理审批驳回逻辑：更新线索状态为 REJECTED，发布 {@link LeadRejectedEvent}。
     * <p>
     * <strong>注意</strong>：驳回路径既不创建 cust_master（CREATE/UPDATE 时）也不失效（DELETE 时），
     * 仅推进线索状态并发布事件供未来扩展点订阅。
     * </p>
     *
     * @param lead              线索实体
     * @param processInstanceId 流程实例ID
     * @param rejectReason      驳回原因（来自 ProcessCompletedEvent.reason，可为 null）
     */
    private void handleRejected(CustLead lead, String processInstanceId, String rejectReason) {
        String leadId = lead.getId();

        // 更新状态为 REJECTED
        leadMapper.updateStatusById(leadId, LeadStatus.REJECTED.getCode(), "SYSTEM");
        log.info("[WorkflowCallbackListener] 线索 {} 审批驳回，状态更新为 REJECTED，processInstanceId={}",
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
        log.info("[WorkflowCallbackListener] 发布 LeadRejectedEvent, leadId={}, leadOp={}, reason={}",
                leadId, lead.getLeadOp(), rejectReason);
    }
}
