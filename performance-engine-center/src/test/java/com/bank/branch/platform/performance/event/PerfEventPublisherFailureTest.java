package com.bank.branch.platform.performance.event;

import com.bank.branch.platform.performance.support.PerfTestApp;
import com.bank.branch.platform.performance.support.PerfTestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * PerfEventPublisher 消费者异常 / Error 降级守护测试 (V1.2 Q4.3, Red).
 *
 * <p>Q1 已实现 safePublish 吞掉 Exception，Q4.3 强化守护：
 * <ul>
 *   <li>真实监听器场景：监听器抛 RuntimeException / Error 时 publish 不扩散</li>
 *   <li>事务场景：监听器 afterCommit 异常不影响调用方</li>
 *   <li><strong>直面 Throwable 场景：</strong>构造一个直接抛 {@link Error} 的 {@link ApplicationEventPublisher}
 *       （绕过 Spring multicaster 的 Error 包装），验证 {@link PerfEventPublisher} 自身 catch 能兜住，
 *       不依赖 Spring SimpleApplicationEventMulticaster 将 Error 包装成 UndeclaredThrowableException</li>
 * </ul>
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = {
        PerfTestApp.class,
        PerfTestConfig.class,
        PerfEventPublisherFailureTest.FailureTestListeners.class
})
@ActiveProfiles("test")
class PerfEventPublisherFailureTest {

    @Autowired
    private PerfEventPublisher publisher;

    @Autowired
    private PlatformTransactionManager txManager;

    @Autowired
    private FailureTestListeners listeners;

    @BeforeEach
    void reset() {
        listeners.throwingReceived.clear();
        listeners.errorReceived.clear();
        listeners.followupCounter.set(0);
    }

    /** 无事务场景：监听器抛 RuntimeException 不应扩散到 publish 调用方. */
    @Test
    @DisplayName("[Red] 监听器抛 RuntimeException 时 publish 不扩散异常")
    void listenerRuntimeException_swallowedByPublisher() {
        ThrowingEvent event = new ThrowingEvent("tid-rte");

        assertThatCode(() -> publisher.publish(event)).doesNotThrowAnyException();

        assertThat(listeners.throwingReceived).hasSize(1);
    }

    /** 无事务场景：监听器抛 Error 时 publisher 必须吞掉（Spring multicaster 会包成 UndeclaredThrowableException，Q1 catch Exception 能兜住）. */
    @Test
    @DisplayName("[Red] 监听器抛 Error 时 publish 也不扩散（Spring multicaster 包装路径）")
    void listenerError_swallowedByPublisher() {
        ErrorEvent event = new ErrorEvent("tid-err");

        assertThatCode(() -> publisher.publish(event)).doesNotThrowAnyException();

        assertThat(listeners.errorReceived).hasSize(1);
    }

    /** 事务场景：监听器 afterCommit 抛 Error，主事务应已 commit，不扩散到 publish 调用方. */
    @Test
    @DisplayName("[Red] 事务内 publish + 监听器抛 Error：afterCommit 阶段不扩散")
    void txCommit_withListenerError_doesNotPropagate() {
        ErrorEvent event = new ErrorEvent("tid-tx-err");
        TransactionTemplate tt = new TransactionTemplate(txManager);

        assertThatCode(() -> tt.execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                publisher.publish(event);
            }
        })).doesNotThrowAnyException();

        assertThat(listeners.errorReceived).hasSize(1);
    }

    /** 多监听器：前者抛异常不应影响 publish 调用方. */
    @Test
    @DisplayName("[Red] 多监听器前者抛异常不阻断 publish 调用方")
    void multipleListeners_errorInOne_doesNotBreakPublisher() {
        MultiListenerEvent event = new MultiListenerEvent("tid-multi");

        assertThatCode(() -> publisher.publish(event)).doesNotThrowAnyException();

        assertThat(listeners.followupCounter.get()).isGreaterThanOrEqualTo(1);
    }

    /**
     * 直面 Throwable 场景：用独立构造的 PerfEventPublisher 包一个直接抛 Error 的
     * ApplicationEventPublisher，验证 safePublish 自身的 catch 能兜住。
     *
     * <p>Q1 safePublish 只 catch Exception，此 case 应 Red（扩展到 Throwable 后 Green）。
     */
    @Test
    @DisplayName("[Red] ApplicationEventPublisher 直接抛 Error 时 PerfEventPublisher 也吞掉")
    void delegateThrowsError_swallowedBySafePublish() {
        ApplicationEventPublisher delegate = Mockito.mock(ApplicationEventPublisher.class);
        Mockito.doThrow(new Error("delegate boom")).when(delegate).publishEvent(Mockito.any(Object.class));

        PerfEventPublisher isolated = new PerfEventPublisher(delegate);
        ThrowingEvent event = new ThrowingEvent("tid-delegate-error");

        // 本断言在 Q1 的 catch Exception 下会 Red（Error 扩散），catch Throwable 后 Green
        assertThatCode(() -> isolated.publish(event)).doesNotThrowAnyException();
    }

    /** 专门抛 RuntimeException 的事件. */
    static class ThrowingEvent extends PerfDomainEvent {
        ThrowingEvent(String traceId) { super(traceId); }
        @Override public String topic() { return "performance.test.throwing.v1"; }
    }

    /** 专门抛 Error 的事件. */
    static class ErrorEvent extends PerfDomainEvent {
        ErrorEvent(String traceId) { super(traceId); }
        @Override public String topic() { return "performance.test.error.v1"; }
    }

    /** 多监听器触发事件. */
    static class MultiListenerEvent extends PerfDomainEvent {
        MultiListenerEvent(String traceId) { super(traceId); }
        @Override public String topic() { return "performance.test.multi.v1"; }
    }

    @Component
    static class FailureTestListeners {
        final CopyOnWriteArrayList<ThrowingEvent> throwingReceived = new CopyOnWriteArrayList<>();
        final CopyOnWriteArrayList<ErrorEvent> errorReceived = new CopyOnWriteArrayList<>();
        final AtomicInteger followupCounter = new AtomicInteger();

        @EventListener
        public void onThrowing(ThrowingEvent e) {
            throwingReceived.add(e);
            throw new RuntimeException("listener boom");
        }

        @EventListener
        public void onError(ErrorEvent e) {
            errorReceived.add(e);
            throw new Error("listener error");
        }

        @EventListener
        public void onMultiFirst(MultiListenerEvent e) {
            followupCounter.incrementAndGet();
            throw new RuntimeException("first listener fail");
        }

        @EventListener
        public void onMultiSecond(MultiListenerEvent e) {
            followupCounter.incrementAndGet();
        }
    }
}
