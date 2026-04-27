package com.bank.branch.platform.customer.listener;

import com.bank.branch.platform.customer.entity.CustLead;
import com.bank.branch.platform.customer.enums.LeadOp;
import com.bank.branch.platform.customer.enums.LeadStatus;
import com.bank.branch.platform.customer.event.LeadApprovedEvent;
import com.bank.branch.platform.customer.event.LeadDeletedEvent;
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
 * 根据 businessKey 前缀（LEAD:）识别线索相关流程，更新线索状态为 APPROVED 或 REJECTED。
 * <br>
 * 注意：{@link ProcessCompletedListener.ProcessCompletedEvent} 已携带 outcome 字段（APPROVED/REJECTED），
 * 当前实现暂未使用 outcome，将在 Task 1.5 改造时消费该字段以区分 APPROVED/REJECTED。
 * <br>
 * 审批结果：
 * - APPROVED + leadOp=CREATE/UPDATE → 发布 {@link LeadApprovedEvent}
 * - APPROVED + leadOp=DELETE → 发布 {@link LeadDeletedEvent}
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
     * 简化实现：流程完成即视为 APPROVED；生产中需从流程变量 approved=true/false 判断。
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
        log.info("[WorkflowCallbackListener.onProcessCompleted] processInstanceId={}, businessKey={}",
                event.processInstanceId(), businessKey);

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

        // TODO(Task 1.5): 使用 event.outcome() 区分 APPROVED/REJECTED，当前暂时将所有完成流程视为 APPROVED
        handleApproved(lead, event.processInstanceId());
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
     * 外部回调入口（供工作流模块在无 Spring 事件时直接调用）。
     * <p>
     * 当工作流回调通过 REST 方式触发时，由 Controller 层调用此方法。
     * approved=true 表示通过，false 表示拒绝。
     * </p>
     *
     * @param leadId            线索ID
     * @param approved          审批结果
     * @param operatorEmpId     审批人
     */
    public void handleWorkflowCallback(String leadId, boolean approved, String operatorEmpId) {
        log.info("[WorkflowCallbackListener.handleWorkflowCallback] leadId={}, approved={}, operator={}",
                leadId, approved, operatorEmpId);

        CustLead lead = leadMapper.selectById(leadId);
        if (lead == null) {
            log.warn("[WorkflowCallbackListener] 线索 {} 不存在，跳过回调处理", leadId);
            return;
        }

        if (approved) {
            // 审批通过
            leadMapper.updateStatusById(leadId, LeadStatus.APPROVED.getCode(), operatorEmpId);

            if (LeadOp.DELETE.getCode().equals(lead.getLeadOp())) {
                eventPublisher.publishEvent(new LeadDeletedEvent(
                        leadId, lead.getLeadNo(), lead.getSourceCustId(), operatorEmpId));
            } else {
                eventPublisher.publishEvent(new LeadApprovedEvent(
                        leadId, lead.getLeadNo(), lead.getLeadOp(),
                        lead.getSourceCustId(), lead.getOwnerOrgId(), operatorEmpId));
            }
        } else {
            // 审批拒绝
            leadMapper.updateStatusById(leadId, LeadStatus.REJECTED.getCode(), operatorEmpId);
            log.info("[WorkflowCallbackListener] 线索 {} 审批拒绝，状态更新为 REJECTED", leadId);
        }
    }
}
