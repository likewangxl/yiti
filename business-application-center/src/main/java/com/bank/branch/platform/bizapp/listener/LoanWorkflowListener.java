package com.bank.branch.platform.bizapp.listener;

import com.bank.branch.platform.bizapp.entity.LoanApply;
import com.bank.branch.platform.bizapp.enums.LoanStatus;
import com.bank.branch.platform.bizapp.event.LoanApprovedEvent;
import com.bank.branch.platform.bizapp.event.LoanRejectedEvent;
import com.bank.branch.platform.bizapp.mapper.LoanApplyMapper;
import com.bank.branch.platform.workflow.api.event.ProcessCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 贷款审批工作流回调监听器。
 * <p>
 * 监听 workflow-center 发布的 {@link ProcessCompletedEvent}，
 * 过滤业务键前缀为 {@code LOAN:} 的事件，按 outcome 分派审批结果：
 * <ul>
 *   <li>APPROVED → 状态更新为 COMPLETED，发布 {@link LoanApprovedEvent}</li>
 *   <li>REJECTED → 状态更新为 REJECTED，发布 {@link LoanRejectedEvent}（携带 rejectReason）</li>
 * </ul>
 * 使用 {@code conditionalUpdateStatus} 实现幂等写入：仅当记录当前状态为 IN_APPROVAL 时才更新，
 * 返回 rowsAffected=0 表示已被其他实例处理，跳过事件发布。
 * <br>
 * 注解 {@code @TransactionalEventListener(AFTER_COMMIT)} 保证监听器在工作流事务提交后触发，
 * 避免读到未提交数据。
 * </p>
 * <p>
 * <strong>事务策略（P0 bug 防御性修复，与 customer 模块对齐）</strong>：
 * <ul>
 *   <li>{@code @Transactional(REQUIRES_NEW)}：在 AFTER_COMMIT 阶段开启新事务，让本方法内
 *       {@code conditionalUpdateStatus} 与 {@code publishEvent(LoanApprovedEvent / LoanRejectedEvent)}
 *       在新事务内执行；</li>
 *   <li>未来若新增 {@code @TransactionalEventListener(AFTER_COMMIT)} 的下游 listener 订阅
 *       LoanApproved/LoanRejected 事件，下游 listener 无需配置 fallbackExecution 即可正确触发；</li>
 *   <li>移除 {@code fallbackExecution = true}：与 REQUIRES_NEW 显式开新事务方案兼容，
 *       与 customer.WorkflowCallbackListener 修复 pattern 一致。</li>
 * </ul>
 * 详见 customer 模块红 IT {@code WorkflowCallbackEventChainBugIT}。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoanWorkflowListener {

    private final LoanApplyMapper loanMapper;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 处理流程完成事件（AFTER_COMMIT 阶段触发）。
     * <p>
     * 1. 过滤非 LOAN: 前缀的业务键，直接返回。<br>
     * 2. 从业务键提取贷款申请ID。<br>
     * 3. 执行条件更新（IN_APPROVAL → target），若影响行数为 0 则已被处理，跳过。<br>
     * 4. 查询最新申请信息，按 outcome 发布对应领域事件。<br>
     * 5. 捕获所有异常，仅记录日志，不抛出（防止影响工作流/事务机制）。
     * </p>
     *
     * @param event 流程完成事件
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void onProcessCompleted(ProcessCompletedEvent event) {
        String businessKey = event.businessKey();
        String processInstanceId = event.processInstanceId();

        try {
            // 1. 过滤非贷款业务
            if (businessKey == null || !businessKey.startsWith("LOAN:")) {
                return;
            }

            // 2. 提取贷款申请ID：businessKey = "LOAN:{loanId}"
            String loanId = businessKey.substring("LOAN:".length());

            // 3. 按 outcome 确定目标状态
            String targetStatus = "REJECTED".equals(event.outcome())
                    ? LoanStatus.REJECTED.getCode()
                    : LoanStatus.COMPLETED.getCode();

            // 4. 条件更新（幂等）：仅当状态为 IN_APPROVAL 时才更新，避免多实例重复处理
            int rowsAffected = loanMapper.conditionalUpdateStatus(
                    loanId, LoanStatus.IN_APPROVAL.getCode(), targetStatus, "SYSTEM");
            if (rowsAffected == 0) {
                log.warn("[LoanWorkflowListener] 状态已被其他实例处理，跳过 id={}, processInstanceId={}",
                        loanId, processInstanceId);
                return;
            }

            log.info("[LoanWorkflowListener] 贷款申请 {} 状态更新为 {}，processInstanceId={}",
                    loanId, targetStatus, processInstanceId);

            // 5. 查询申请信息用于事件载荷
            LoanApply loan = loanMapper.selectById(loanId);
            if (loan == null) {
                log.warn("[LoanWorkflowListener] 贷款申请 {} 更新后查询不到记录，不发布事件", loanId);
                return;
            }

            // 6. 按 outcome 发布对应领域事件
            if ("REJECTED".equals(event.outcome())) {
                // 驳回：携带驳回原因（reason 允许为 null）
                eventPublisher.publishEvent(new LoanRejectedEvent(
                        loanId,
                        loan.getApplyNo(),
                        loan.getCustId(),
                        loan.getOwnerOrgId(),
                        event.reason()
                ));
            } else {
                // 审批通过
                eventPublisher.publishEvent(new LoanApprovedEvent(
                        loanId,
                        loan.getApplyNo(),
                        loan.getCustId(),
                        loan.getOwnerOrgId(),
                        loan.getCreditAmount()
                ));
            }

        } catch (Exception e) {
            // 捕获所有异常，防止影响工作流线程或 Spring 事务机制
            log.error("[LoanWorkflowListener] 处理流程完成事件异常，businessKey={}, processInstanceId={}",
                    businessKey, processInstanceId, e);
        }
    }
}
