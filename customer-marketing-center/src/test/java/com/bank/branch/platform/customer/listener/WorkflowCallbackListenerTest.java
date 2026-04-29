package com.bank.branch.platform.customer.listener;

import com.bank.branch.platform.workflow.api.event.ProcessCompletedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WorkflowCallbackListener 单元测试（TDD）
 * <p>
 * 验证 onProcessCompleted 方法的事务策略：
 * <ul>
 *   <li>{@code @TransactionalEventListener(phase=AFTER_COMMIT)} —— 文档 06 要求"回调在事务提交后执行"</li>
 *   <li>{@code @Transactional(REQUIRES_NEW)} —— P0 bug 修复：让本方法内 publishEvent(LeadApprovedEvent)
 *       在新事务内 publish，下游 LeadApprovedListener (AFTER_COMMIT, 无 fallbackExecution) 才能正确触发</li>
 *   <li>移除 {@code fallbackExecution = true} —— 与 REQUIRES_NEW 配合后无需 fallback 路径</li>
 * </ul>
 * </p>
 */
class WorkflowCallbackListenerTest {

    /**
     * 验证 onProcessCompleted 方法标注了 @TransactionalEventListener(phase=AFTER_COMMIT)。
     */
    @Test
    void onProcessCompleted_isAnnotatedWithTransactionalEventListener() throws NoSuchMethodException {
        Method m = WorkflowCallbackListener.class.getMethod(
                "onProcessCompleted",
                ProcessCompletedEvent.class);

        TransactionalEventListener ann = m.getAnnotation(TransactionalEventListener.class);

        assertThat(ann).as("方法应标注 @TransactionalEventListener").isNotNull();
        assertThat(ann.phase())
                .as("phase 应为 AFTER_COMMIT，确保回调在事务提交后执行")
                .isEqualTo(TransactionPhase.AFTER_COMMIT);
        assertThat(ann.fallbackExecution())
                .as("fallbackExecution 应为 false：与 @Transactional(REQUIRES_NEW) 配合后，"
                        + "AFTER_COMMIT 显式开新事务即可，无需 fallback 路径")
                .isFalse();
    }

    /**
     * 验证 onProcessCompleted 方法标注了 @Transactional(REQUIRES_NEW)。
     * <p>
     * P0 bug 修复要求：本方法内 publishEvent(LeadApprovedEvent / LeadDeletedEvent) 必须在新事务内 publish，
     * 下游 LeadApprovedListener / LeadDeletedListener (AFTER_COMMIT) 才能正确触发。
     * </p>
     */
    @Test
    void onProcessCompleted_isAnnotatedWithTransactionalRequiresNew() throws NoSuchMethodException {
        Method m = WorkflowCallbackListener.class.getMethod(
                "onProcessCompleted",
                ProcessCompletedEvent.class);

        Transactional ann = m.getAnnotation(Transactional.class);

        assertThat(ann).as("方法应标注 @Transactional 以让 publishEvent 在新事务内发布").isNotNull();
        assertThat(ann.propagation())
                .as("propagation 应为 REQUIRES_NEW：AFTER_COMMIT 阶段开启新事务，"
                        + "让内部 publishEvent 与下游 AFTER_COMMIT listener 正确链接")
                .isEqualTo(Propagation.REQUIRES_NEW);
        assertThat(ann.rollbackFor())
                .as("rollbackFor 应包含 Exception.class 以保证异常时回滚")
                .contains(Exception.class);
    }
}
