package com.bank.branch.platform.bizapp.listener;

import com.bank.branch.platform.bizapp.entity.SupportRequest;
import com.bank.branch.platform.bizapp.enums.SupportStatus;
import com.bank.branch.platform.bizapp.event.SupportCompletedEvent;
import com.bank.branch.platform.bizapp.mapper.SupportRequestMapper;
import com.bank.branch.platform.workflow.listener.ProcessCompletedListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 中场支持申请工作流回调监听器。
 * <p>
 * 监听工作流中心发布的 {@link ProcessCompletedListener.ProcessCompletedEvent} 事件，
 * 根据 businessKey 前缀（SUPPORT:）识别支持申请相关流程，更新状态为 COMPLETED 并发布领域事件。
 * <br>
 * 场景A（productId 非null，dispatchEmpId 为null）：流程完成即视为 COMPLETED。
 * 场景B：同样简化为流程完成 = COMPLETED（生产中需从流程变量读取审批结果）。
 * <br>
 * 注意：必须捕获所有异常，不允许异常传播到 Flowable 流程引擎。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SupportWorkflowListener {

    private final SupportRequestMapper supportMapper;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 监听工作流流程完成事件，处理中场支持申请状态流转。
     * <p>
     * businessKey 格式为 {@code SUPPORT:{requestId}}，非支持申请相关流程直接忽略。
     * 捕获所有异常，确保工作流事件处理不阻塞主流程。
     * </p>
     *
     * @param event 流程完成事件（来自 workflow-center ProcessCompletedListener）
     */
    @EventListener
    public void onProcessCompleted(ProcessCompletedListener.ProcessCompletedEvent event) {
        try {
            String businessKey = event.businessKey();
            log.info("[SupportWorkflowListener.onProcessCompleted] processInstanceId={}, businessKey={}",
                    event.processInstanceId(), businessKey);

            // 只处理支持申请相关流程
            if (businessKey == null || !businessKey.startsWith("SUPPORT:")) {
                return;
            }

            String requestId = businessKey.substring("SUPPORT:".length());

            // 查询申请，不存在则跳过（幂等保护）
            SupportRequest request = supportMapper.selectByBusinessKey(businessKey);
            if (request == null) {
                log.warn("[SupportWorkflowListener] 支持申请（businessKey={}）不存在，跳过状态更新", businessKey);
                return;
            }

            // 更新状态为 COMPLETED
            // 简化实现：流程完成视为成功。生产中应从流程变量 approved=true/false 判断
            request.setStatus(SupportStatus.COMPLETED.getCode());
            request.setUpdatedBy("SYSTEM");
            request.setUpdatedTime(LocalDateTime.now());
            supportMapper.updateById(request);

            log.info("[SupportWorkflowListener] 支持申请 {} 流程完成，状态更新为 COMPLETED", requestId);

            // 发布完成事件
            eventPublisher.publishEvent(new SupportCompletedEvent(
                    requestId,
                    request.getRequestNo(),
                    request.getCustId(),
                    request.getProductId(),
                    request.getAssignedEmpId(),
                    true
            ));

        } catch (Exception e) {
            // 必须捕获所有异常，防止异常传播到 Flowable 引擎影响流程状态
            log.error("[SupportWorkflowListener] 处理流程完成事件时发生异常，businessKey={}，异常将被忽略",
                    event.businessKey(), e);
        }
    }
}
