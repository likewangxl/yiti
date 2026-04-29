package com.bank.branch.platform.customer.listener;

import com.bank.branch.platform.customer.entity.CustLead;
import com.bank.branch.platform.customer.event.LeadApprovedEvent;
import com.bank.branch.platform.customer.event.LeadDeletedEvent;
import com.bank.branch.platform.customer.event.LeadRejectedEvent;
import com.bank.branch.platform.customer.mapper.CustLeadMapper;
import com.bank.branch.platform.customer.service.LeadCallbackReconcileService;
import com.bank.branch.platform.workflow.api.event.ProcessCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 工作流回调监听器。
 * <p>
 * 监听工作流中心发布的 {@link ProcessCompletedEvent} 事件，
 * 根据 businessKey 前缀（LEAD:）识别线索相关流程，按 outcome 委托
 * {@link LeadCallbackReconcileService} 处理审批结果（与
 * {@code bizapp.LoanWorkflowListener} pattern 对齐）：
 * <ul>
 *   <li>APPROVED + leadOp=CREATE/UPDATE → 状态更新为 APPROVED，发布 {@link LeadApprovedEvent}</li>
 *   <li>APPROVED + leadOp=DELETE → 状态更新为 APPROVED，发布 {@link LeadDeletedEvent}</li>
 *   <li>REJECTED → 状态更新为 REJECTED，发布 {@link LeadRejectedEvent}（不创建/失效 cust_master）</li>
 * </ul>
 * </p>
 * <p>
 * <strong>FU-14 重构</strong>：原 {@code handleApproved} / {@code handleRejected} private
 * 方法被抽到 {@link LeadCallbackReconcileService}，支持
 * {@code LeadCallbackCompensationService} 补偿任务以同样的语义推进 stuck IN_APPROVAL leads。
 * 行为完全等价，仅做"代码移动 + 共用"。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowCallbackListener {

    private final CustLeadMapper leadMapper;
    private final LeadCallbackReconcileService reconcileService;

    /**
     * 监听工作流流程完成事件，处理线索状态流转。
     * <p>
     * businessKey 格式为 {@code LEAD:{leadId}}，非线索相关流程直接忽略。
     * 按 {@code event.outcome()} 分发委托 {@link LeadCallbackReconcileService}。
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
     * </p>
     *
     * <p>
     * <strong>FU-2 异常兜底</strong>：整体 try-catch 捕获所有异常，仅记录业务自定义 ERROR 日志，
     * 避免冒泡到 Spring AFTER_COMMIT 调用栈触发框架级
     * "TransactionSynchronization.afterCompletion threw exception" ERROR。
     * 副作用：listener 异常被自吞后，{@code cust_lead.lead_status} 可能停留在 IN_APPROVAL。
     * <strong>FU-14 通过定时补偿任务 {@code LeadCallbackCompensationService} 兜底</strong>。
     * </p>
     *
     * @param event 流程完成事件（来自 workflow-center ProcessCompletedListener）
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void onProcessCompleted(ProcessCompletedEvent event) {
        String businessKey = event.businessKey();
        String processInstanceId = event.processInstanceId();
        log.info("[WorkflowCallbackListener.onProcessCompleted] processInstanceId={}, businessKey={}, outcome={}",
                processInstanceId, businessKey, event.outcome());

        try {
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

            // 按 event.outcome() 分发委托 LeadCallbackReconcileService（FU-14 抽出公共逻辑）
            if ("REJECTED".equals(event.outcome())) {
                reconcileService.reconcileRejected(lead, processInstanceId, event.reason());
            } else {
                reconcileService.reconcileApproved(lead, processInstanceId);
            }
        } catch (Exception e) {
            // 业务自定义 ERROR 日志：统一告警面，不影响 Spring 事件循环 / 后续 listener 链
            log.error("[WorkflowCallbackListener] 处理流程完成事件异常 businessKey={} processInstanceId={}",
                    businessKey, processInstanceId, e);
        }
    }
}
