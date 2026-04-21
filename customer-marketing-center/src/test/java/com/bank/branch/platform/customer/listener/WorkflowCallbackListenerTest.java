package com.bank.branch.platform.customer.listener;

import com.bank.branch.platform.workflow.listener.ProcessCompletedListener;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WorkflowCallbackListener 单元测试（TDD）
 * <p>
 * 验证 onProcessCompleted 方法使用 @TransactionalEventListener(phase=AFTER_COMMIT)
 * 以满足文档 06 事务边界要求："回调在事务提交后执行"。
 * </p>
 */
class WorkflowCallbackListenerTest {

    /**
     * 验证 onProcessCompleted 方法标注了 @TransactionalEventListener
     * 且 phase 为 AFTER_COMMIT，fallbackExecution 为 true。
     */
    @Test
    void onProcessCompleted_isAnnotatedWithTransactionalEventListener() throws NoSuchMethodException {
        Method m = WorkflowCallbackListener.class.getMethod(
                "onProcessCompleted",
                ProcessCompletedListener.ProcessCompletedEvent.class);

        TransactionalEventListener ann = m.getAnnotation(TransactionalEventListener.class);

        assertThat(ann).as("方法应标注 @TransactionalEventListener").isNotNull();
        assertThat(ann.phase())
                .as("phase 应为 AFTER_COMMIT，确保回调在事务提交后执行")
                .isEqualTo(TransactionPhase.AFTER_COMMIT);
        assertThat(ann.fallbackExecution())
                .as("fallbackExecution=true 允许在无事务上下文时执行（测试场景）")
                .isTrue();
    }
}
