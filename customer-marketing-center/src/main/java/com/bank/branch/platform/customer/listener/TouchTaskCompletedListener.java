package com.bank.branch.platform.customer.listener;

import com.bank.branch.platform.customer.event.TouchCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 触达任务完成事件监听器。
 * <p>
 * 监听 {@link TouchCompletedEvent}，在触达任务完成事务提交后执行后续处理逻辑。
 * 当前实现仅记录日志，后续可扩展为：创建 FOLLOW_UP 跟进任务、更新客户活跃度统计等。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TouchTaskCompletedListener {

    /**
     * 处理触达任务完成事件。
     *
     * @param event 触达任务完成事件，包含 taskId、taskNo、custId、assigneeEmpId、taskType
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(TouchCompletedEvent event) {
        log.info("[TouchTaskCompletedListener] 触达任务完成 taskId={}, taskNo={}, custId={}, taskType={}",
                event.getTaskId(), event.getTaskNo(), event.getCustId(), event.getTaskType());
        // 后续可扩展: 创建后续跟进任务、更新客户活跃度等
    }
}
