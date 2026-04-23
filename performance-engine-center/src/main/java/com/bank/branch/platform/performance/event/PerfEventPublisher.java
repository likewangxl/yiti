package com.bank.branch.platform.performance.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * performance 模块领域事件发布器（V1.2 Q1.1）.
 *
 * <p>职责：
 * <ul>
 *   <li>Spring 事务活跃时，注册 {@link TransactionSynchronization}，
 *       在 {@code afterCommit} 阶段投递事件 —— 保证事务提交后才送达，事务回滚事件不投递</li>
 *   <li>无事务场景下，直接同步投递</li>
 *   <li>事件发布失败不回滚主事务，仅记日志（事件总线故障不能阻塞业务）</li>
 * </ul>
 *
 * <p>消费方约定使用 {@code @EventListener} 或 {@code @TransactionalEventListener(AFTER_COMMIT)}。
 *
 * @since V1.2 Q1.1
 */
@Slf4j
@Component
public class PerfEventPublisher {

    private final ApplicationEventPublisher delegate;

    public PerfEventPublisher(ApplicationEventPublisher delegate) {
        this.delegate = delegate;
    }

    /**
     * 发布领域事件.
     *
     * <p>事务内：注册 afterCommit 回调；事务外：直接同步投递。
     *
     * @param event 领域事件（非 null）
     */
    public void publish(PerfDomainEvent event) {
        if (event == null) {
            return;
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    safePublish(event);
                }
            });
        } else {
            safePublish(event);
        }
    }

    /**
     * 实际投递动作 —— 发布失败不扩散异常.
     *
     * <p>V1.2 Q4.3：catch 范围从 {@link Exception} 扩展为 {@link Throwable}，
     * 守护监听器/multicaster 抛 {@link Error}（例如未来切到自定义 multicaster
     * 或 Kafka 客户端直接抛出原生 Error）的场景。事件总线故障不能阻塞业务主事务。
     */
    private void safePublish(PerfDomainEvent event) {
        try {
            delegate.publishEvent(event);
        } catch (Throwable t) {
            log.error("[PerfEventPublisher] publishEvent failed: topic={}, eventId={}",
                    event.topic(), event.getEventId(), t);
        }
    }
}
