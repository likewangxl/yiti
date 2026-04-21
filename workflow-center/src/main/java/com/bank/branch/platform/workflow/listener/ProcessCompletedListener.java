package com.bank.branch.platform.workflow.listener;

import com.bank.branch.platform.workflow.entity.BizProcessMap;
import com.bank.branch.platform.workflow.enums.ProcessStatus;
import com.bank.branch.platform.workflow.mapper.BizProcessMapMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.ExecutionListener;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 流程完成监听器。
 * <p>
 * 在 Flowable 流程实例结束时触发，更新 biz_process_map 表中
 * 对应记录的状态为 COMPLETED，设置结束时间，并发布流程完成事件。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessCompletedListener implements ExecutionListener {

    private final BizProcessMapMapper bizProcessMapMapper;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 流程结束事件回调。
     * <p>
     * 1. 根据流程实例ID查询业务流程映射
     * 2. 更新映射状态为 COMPLETED，设置结束时间和更新时间
     * 3. 发布流程完成事件供其他模块监听
     * </p>
     *
     * @param execution Flowable 委托执行对象
     */
    @Override
    public void notify(DelegateExecution execution) {
        String processInstanceId = execution.getProcessInstanceId();

        // 查询业务流程映射
        BizProcessMap map = bizProcessMapMapper.selectByProcessInstanceId(processInstanceId);
        if (map == null) {
            log.warn("[ProcessCompletedListener] 未找到流程 {} 的业务映射，跳过状态更新", processInstanceId);
            return;
        }

        Object approved = execution.getVariable("approved");
        ProcessStatus terminalStatus = Boolean.FALSE.equals(approved)
                ? ProcessStatus.CANCELLED
                : ProcessStatus.COMPLETED;

        // 更新流程状态为已完成/已取消
        map.setProcessStatus(terminalStatus.getCode());
        map.setEndTime(LocalDateTime.now());
        map.setUpdatedTime(LocalDateTime.now());
        bizProcessMapMapper.updateById(map);

        // 将流程变量 approved/reason 转换为下游业务语义 outcome/reason：
        //   - biz_process_map.processStatus 使用 workflow-center 自身状态语义（COMPLETED / CANCELLED）
        //   - event.outcome 是发给下游 bizapp 的业务语义（APPROVED / REJECTED），二者独立，不可混淆
        //   - approved=false 在 processStatus 里映射为 CANCELLED，在 event.outcome 里映射为 REJECTED
        String outcome = Boolean.FALSE.equals(approved) ? "REJECTED" : "APPROVED";
        String reason = asString(execution.getVariable("reason"));

        // 发布流程完成事件，携带 outcome 和 reason 供下游区分审批结果
        eventPublisher.publishEvent(new ProcessCompletedEvent(processInstanceId, map.getBusinessKey(), outcome, reason));

        log.info("[ProcessCompletedListener] 流程 {} 已完成", processInstanceId);
    }

    /**
     * null-safe 将对象转换为 String。
     *
     * @param value 任意对象
     * @return 转换后的字符串，null 时返回 null
     */
    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    /**
     * 流程完成事件，供其他模块监听处理后续业务逻辑。
     *
     * <p>语义说明：
     * <ul>
     *   <li>{@code outcome} — 下游业务语义（APPROVED / REJECTED / CANCELLED），与
     *       {@code biz_process_map.processStatus}（COMPLETED / CANCELLED）独立，不可混淆</li>
     *   <li>{@code reason} — 审批备注或驳回原因，可为 null</li>
     * </ul>
     *
     * @param processInstanceId 流程实例ID
     * @param businessKey       业务键（格式：BIZ_TYPE:bizId）
     * @param outcome           下游业务审批结论（APPROVED / REJECTED）
     * @param reason            审批原因或驳回说明，可为 null
     */
    public record ProcessCompletedEvent(
            String processInstanceId,
            String businessKey,
            String outcome,   // 下游业务语义：APPROVED / REJECTED
            String reason     // 审批原因，可为 null
    ) {
    }
}
