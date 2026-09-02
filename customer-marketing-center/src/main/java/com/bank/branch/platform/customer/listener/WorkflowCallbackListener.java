package com.bank.branch.platform.customer.listener;

import com.bank.branch.platform.customer.entity.CustLead;
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
 *   <li>APPROVED + leadOp=CREATE/UPDATE/DELETE → 状态更新为 APPROVED，同步调用
 *       {@link LeadCallbackReconcileService#reconcileApproved} 装配客户主档（V1.11#1 起）</li>
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
    private final com.bank.branch.platform.governance.api.NotifyApi notifyApi;

    /**
     * 监听工作流流程完成事件，处理线索状态流转。
     * <p>
     * businessKey 格式为 {@code LEAD:{leadId}}，非线索相关流程直接忽略。
     * 按 {@code event.outcome()} 分发委托 {@link LeadCallbackReconcileService}。
     * </p>
     *
     * <p>
     * <strong>事务策略</strong>：
     * <ul>
     *   <li>{@code @Transactional(REQUIRES_NEW)}：在 AFTER_COMMIT 阶段开启新事务，
     *       让 reconcileApproved / reconcileRejected 在独立事务内完成状态更新与客户主档装配；</li>
     *   <li>V1.11#1 方向 C：已消除嵌套 @TransactionalEventListener，reconcileApproved 内
     *       直接同步调用 assemblerService，REQUIRES_NEW 作为防御层保留；</li>
     *   <li>移除 {@code fallbackExecution = true}：AFTER_COMMIT + 显式新事务已足够。</li>
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
     * <p>
     * <strong>状态机假设（FU-13 显式说明，2026-04-29）</strong>：本方法委托
     * {@link LeadCallbackReconcileService#reconcileApproved} / {@code reconcileRejected}，
     * 二者通过 {@code conditionalUpdateStatus(IN_APPROVAL → 目标态)} 推进状态：
     * <ul>
     *   <li><strong>前置假设</strong>：lead 当前状态 = {@code IN_APPROVAL}（由
     *       {@code LeadService.submitForApproval} 在提交审批时设定）；</li>
     *   <li><strong>非 IN_APPROVAL 行为</strong>：conditionalUpdate 返 0 行受影响，
     *       reconcile 早返回（log.warn 但不抛异常），<strong>不</strong>发布下游事件，
     *       保证幂等（重复事件 / 补偿与 listener 主路径并发触发场景下不会双写 cust_master）；</li>
     *   <li><strong>非 LEAD 前缀 businessKey</strong>：本 listener 仅处理 {@code LEAD:*} 前缀的流程，
     *       其他业务（如资产立项、诉求支持）由各自 listener 监听同一 {@code ProcessCompletedEvent} 处理。</li>
     * </ul>
     * </p>
     *
     * @param event 流程完成事件（来自 workflow-center ProcessCompletedListener，载荷见
     *              {@link com.bank.branch.platform.workflow.api.event.ProcessCompletedEvent}）
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
                notifyApplicant(lead, "驳回",
                        "您的线索审批已被驳回" + (event.reason() != null ? "：" + event.reason() : "") + "。");
            } else {
                reconcileService.reconcileApproved(lead, processInstanceId);
                notifyApplicant(lead, "通过", "您的线索审批已通过，客户主档已生效。");
            }
        } catch (Exception e) {
            // 业务自定义 ERROR 日志：统一告警面，不影响 Spring 事件循环 / 后续 listener 链
            log.error("[WorkflowCallbackListener] 处理流程完成事件异常 businessKey={} processInstanceId={}",
                    businessKey, processInstanceId, e);
        }
    }

    /**
     * 审批结束后给申请人（lead.createdBy）发通知，失败不阻断主流程.
     */
    private void notifyApplicant(CustLead lead, String result, String content) {
        if (lead.getCreatedBy() == null || lead.getCreatedBy().isBlank()) {
            return;
        }
        try {
            notifyApi.sendNotification(com.bank.branch.platform.governance.api.dto.NotificationCmd.builder()
                    .targetEmpId(lead.getCreatedBy())
                    .title("线索审批" + result)
                    .content(content + "（线索编号：" + lead.getLeadNo() + "）")
                    .notifyType("WORKFLOW")
                    .bizType("LEAD")
                    .bizId(lead.getId())
                    .build());
        } catch (Exception e) {
            log.warn("[WorkflowCallbackListener] 发送通知失败 leadId={}, err={}", lead.getId(), e.getMessage());
        }
    }
}
