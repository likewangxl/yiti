package com.bank.branch.platform.bizapp.listener;

import com.bank.branch.platform.bizapp.entity.SupportRequest;
import com.bank.branch.platform.bizapp.enums.SupportStatus;
import com.bank.branch.platform.bizapp.event.SupportCompletedEvent;
import com.bank.branch.platform.bizapp.event.SupportRejectedEvent;
import com.bank.branch.platform.bizapp.mapper.SupportRequestMapper;
import com.bank.branch.platform.workflow.listener.ProcessCompletedListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 中场支持申请工作流回调监听器。
 * <p>
 * 监听工作流中心发布的 {@link ProcessCompletedListener.ProcessCompletedEvent} 事件，
 * 根据 businessKey 前缀（SUPPORT:）识别支持申请相关流程，按 outcome 分派审批结果：
 * <ul>
 *   <li>APPROVED → 状态更新为 COMPLETED，发布 {@link SupportCompletedEvent}</li>
 *   <li>REJECTED → 状态更新为 REJECTED，发布 {@link SupportRejectedEvent}（携带 rejectReason）</li>
 * </ul>
 * 使用 {@code conditionalUpdateStatus} 实现幂等写入：仅当记录当前状态为 IN_APPROVAL 时才更新，
 * 返回 rowsAffected=0 表示已被其他实例处理，跳过事件发布。
 * <br>
 * 注解 {@code @TransactionalEventListener(AFTER_COMMIT)} 保证监听器在工作流事务提交后触发，
 * 避免读到未提交数据。
 * <br>
 * 注意：必须捕获所有异常，不允许异常传播到 Flowable 流程引擎或 Spring 事务机制。
 * </p>
 * <p>
 * <strong>事务策略（P0 bug 防御性修复，与 customer 模块对齐）</strong>：
 * <ul>
 *   <li>{@code @Transactional(REQUIRES_NEW)}：在 AFTER_COMMIT 阶段开启新事务，让本方法内
 *       {@code conditionalUpdateStatus} 与 {@code publishEvent(SupportCompletedEvent / SupportRejectedEvent)}
 *       在新事务内执行；</li>
 *   <li>未来若新增 {@code @TransactionalEventListener(AFTER_COMMIT)} 的下游 listener 订阅
 *       SupportCompleted/SupportRejected 事件，下游 listener 无需配置 fallbackExecution 即可正确触发；</li>
 *   <li>移除 {@code fallbackExecution = true}：与 REQUIRES_NEW 显式开新事务方案兼容，
 *       与 customer.WorkflowCallbackListener 修复 pattern 一致。</li>
 * </ul>
 * 详见 customer 模块红 IT {@code WorkflowCallbackEventChainBugIT}。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SupportWorkflowListener {

    private final SupportRequestMapper supportMapper;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 监听工作流流程完成事件，处理中场支持申请状态流转（AFTER_COMMIT 阶段触发）。
     * <p>
     * businessKey 格式为 {@code SUPPORT:{requestId}}，非支持申请相关流程直接忽略。<br>
     * 1. 过滤非 SUPPORT: 前缀的业务键，直接返回。<br>
     * 2. 按 outcome 确定目标状态。<br>
     * 3. 执行条件更新（IN_APPROVAL → target），若影响行数为 0 则已被处理，跳过。<br>
     * 4. 查询最新申请信息，按 outcome 发布对应领域事件。<br>
     * 5. 捕获所有异常，确保工作流事件处理不阻塞主流程。
     * </p>
     *
     * @param event 流程完成事件（来自 workflow-center ProcessCompletedListener）
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void onProcessCompleted(ProcessCompletedListener.ProcessCompletedEvent event) {
        try {
            String businessKey = event.businessKey();

            // 1. 只处理支持申请相关流程
            if (businessKey == null || !businessKey.startsWith("SUPPORT:")) {
                return;
            }

            String requestId = businessKey.substring("SUPPORT:".length());
            log.debug("[SupportWorkflowListener.onProcessCompleted] processInstanceId={}, requestId={}",
                    event.processInstanceId(), requestId);

            // 2. 按 outcome 确定目标状态
            String targetStatus = "REJECTED".equals(event.outcome())
                    ? SupportStatus.REJECTED.getCode()
                    : SupportStatus.COMPLETED.getCode();

            // 3. 条件更新（幂等）：仅当状态为 IN_APPROVAL 时才更新，避免多实例重复处理
            int rowsAffected = supportMapper.conditionalUpdateStatus(
                    requestId, SupportStatus.IN_APPROVAL.getCode(), targetStatus, "SYSTEM");
            if (rowsAffected == 0) {
                log.warn("[SupportWorkflowListener] 状态已被其他实例处理，跳过 id={}, processInstanceId={}",
                        requestId, event.processInstanceId());
                return;
            }

            log.info("[SupportWorkflowListener] 支持申请 {} 状态更新为 {}，processInstanceId={}",
                    requestId, targetStatus, event.processInstanceId());

            // 4. 查询申请信息用于事件载荷
            SupportRequest request = supportMapper.selectById(requestId);
            if (request == null) {
                log.warn("[SupportWorkflowListener] 支持申请 {} 更新后查询不到记录，不发布事件", requestId);
                return;
            }

            // 5. 按 outcome 发布对应领域事件
            if ("REJECTED".equals(event.outcome())) {
                // 驳回：发布 SupportRejectedEvent，携带驳回原因（reason 允许为 null）
                eventPublisher.publishEvent(new SupportRejectedEvent(
                        requestId,
                        request.getRequestNo(),
                        request.getCustId(),
                        request.getCreatedBy(),
                        event.reason()
                ));
            } else {
                // 审批通过：发布 SupportCompletedEvent
                eventPublisher.publishEvent(new SupportCompletedEvent(
                        requestId,
                        request.getRequestNo(),
                        request.getCustId(),
                        request.getProductId(),
                        request.getAssignedEmpId(),
                        true
                ));
            }

        } catch (Exception e) {
            // 必须捕获所有异常，防止异常传播到 Flowable 引擎影响流程状态
            log.error("[SupportWorkflowListener] 处理流程完成事件时发生异常，businessKey={}，异常将被忽略",
                    event.businessKey(), e);
        }
    }
}
