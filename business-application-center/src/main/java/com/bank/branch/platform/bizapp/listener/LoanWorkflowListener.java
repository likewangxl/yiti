package com.bank.branch.platform.bizapp.listener;

import com.bank.branch.platform.bizapp.entity.LoanApply;
import com.bank.branch.platform.bizapp.enums.LoanStatus;
import com.bank.branch.platform.bizapp.event.LoanApprovedEvent;
import com.bank.branch.platform.bizapp.mapper.LoanApplyMapper;
import com.bank.branch.platform.workflow.listener.ProcessCompletedListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 贷款审批工作流回调监听器。
 * <p>
 * 监听 workflow-center 发布的 {@link ProcessCompletedListener.ProcessCompletedEvent}，
 * 过滤业务键前缀为 {@code LOAN:} 的事件，更新贷款申请状态并发布领域事件。
 * 所有异常必须被捕获，不得向 Spring 事件机制传播（防止影响工作流线程）。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoanWorkflowListener {

    private final LoanApplyMapper loanMapper;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 处理流程完成事件。
     * <p>
     * 1. 过滤非 LOAN: 前缀的业务键，直接返回。
     * 2. 从业务键提取贷款申请ID，查询申请。
     * 3. V1 简化：全部完成均视为审批通过，更新状态为 COMPLETED。
     * 4. 发布 LoanApprovedEvent。
     * 5. 捕获所有异常，仅记录日志，不抛出。
     * </p>
     *
     * @param event 流程完成事件
     */
    @EventListener
    public void onProcessCompleted(ProcessCompletedListener.ProcessCompletedEvent event) {
        String businessKey = event.businessKey();
        String processInstanceId = event.processInstanceId();

        try {
            // 1. 过滤非贷款业务
            if (businessKey == null || !businessKey.startsWith("LOAN:")) {
                return;
            }

            // 2. 提取贷款申请ID：businessKey = "LOAN:{loanId}"
            String loanId = businessKey.substring("LOAN:".length());

            // 3. 查询申请
            LoanApply loan = loanMapper.selectById(loanId);
            if (loan == null) {
                log.warn("[LoanWorkflowListener] 贷款申请 {} 不存在，跳过状态更新（processInstanceId={}）",
                        loanId, processInstanceId);
                return;
            }

            // 4. V1 简化：流程完成 = 审批通过，更新状态为 COMPLETED
            loanMapper.updateStatusById(loanId, LoanStatus.COMPLETED.getCode(), "SYSTEM");
            log.info("[LoanWorkflowListener] 贷款申请 {} 审批完成，状态更新为 COMPLETED", loanId);

            // 5. 发布审批通过事件
            eventPublisher.publishEvent(new LoanApprovedEvent(
                    loanId,
                    loan.getApplyNo(),
                    loan.getCustId(),
                    loan.getOwnerOrgId(),
                    loan.getCreditAmount()
            ));

        } catch (Exception e) {
            // 捕获所有异常，防止影响工作流线程
            log.error("[LoanWorkflowListener] 处理流程完成事件异常，businessKey={}, processInstanceId={}",
                    businessKey, processInstanceId, e);
        }
    }
}
