package com.bank.branch.platform.performance.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.TransactionStatus;
import com.bank.branch.platform.performance.support.PerfTestApp;
import com.bank.branch.platform.performance.support.PerfTestConfig;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.LocalDateTime;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PerfEventPublisher 事务后发布测试 (V1.2 Q1.1, Red).
 *
 * <p>覆盖场景:
 * <ul>
 *   <li>事务内调用 publish → commit 前 listener 不触发；commit 后触发</li>
 *   <li>事务回滚场景 → listener 不触发（事件未投递）</li>
 *   <li>无事务场景下 → 直接同步投递</li>
 * </ul>
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = {PerfTestApp.class, PerfTestConfig.class, PerfEventPublisherTest.TestListener.class})
@ActiveProfiles("test")
class PerfEventPublisherTest {

    @Autowired
    private PerfEventPublisher publisher;

    @Autowired
    private PlatformTransactionManager txManager;

    @Autowired
    private TestListener testListener;

    @BeforeEach
    void setUp() {
        testListener.reset();
    }

    /** 测试用事件. */
    static class SampleEvent extends PerfDomainEvent {
        SampleEvent(String traceId) {
            super(traceId);
        }

        @Override
        public String topic() {
            return "performance.test.sample.v1";
        }
    }

    /** 测试用 Listener, 记录被触发次数. */
    @Component
    static class TestListener {
        final CopyOnWriteArrayList<SampleEvent> received = new CopyOnWriteArrayList<>();

        @EventListener
        public void on(SampleEvent e) {
            received.add(e);
        }

        void reset() {
            received.clear();
        }
    }

    @Test
    @DisplayName("[Red] 事务内 publish, 事务 commit 后 listener 才触发")
    void publisher_publishesAfterCommit_only() {
        SampleEvent event = new SampleEvent("trace-abc");
        TransactionTemplate tt = new TransactionTemplate(txManager);

        tt.execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                publisher.publish(event);
                // 事务内 listener 还没收到
                assertThat(testListener.received).isEmpty();
            }
        });

        // 事务 commit 后 listener 已收到
        assertThat(testListener.received).hasSize(1);
        assertThat(testListener.received.get(0).getEventId()).isEqualTo(event.getEventId());
        assertThat(testListener.received.get(0).getTraceId()).isEqualTo("trace-abc");
        assertThat(testListener.received.get(0).topic()).isEqualTo("performance.test.sample.v1");
        assertThat(testListener.received.get(0).getOccurredAt()).isNotNull();
    }

    @Test
    @DisplayName("[Red] 事务回滚时 listener 不触发")
    void publisher_skipsEvent_onRollback() {
        SampleEvent event = new SampleEvent("trace-xyz");
        TransactionTemplate tt = new TransactionTemplate(txManager);

        try {
            tt.execute(new TransactionCallbackWithoutResult() {
                @Override
                protected void doInTransactionWithoutResult(TransactionStatus status) {
                    publisher.publish(event);
                    // 主动触发回滚
                    throw new RuntimeException("force rollback");
                }
            });
        } catch (RuntimeException ignored) {
            // 预期事务回滚
        }

        assertThat(testListener.received).isEmpty();
    }

    @Test
    @DisplayName("[Red] 无事务时直接同步投递")
    void publisher_directPublish_withoutTransaction() {
        SampleEvent event = new SampleEvent(null);

        publisher.publish(event);

        assertThat(testListener.received).hasSize(1);
        assertThat(testListener.received.get(0).getEventId()).isEqualTo(event.getEventId());
    }

    @Test
    @DisplayName("[Red] PerfDomainEvent 基类字段契约：eventId 32 字符 UUID 去横线")
    void event_baseFields_contract() {
        SampleEvent e1 = new SampleEvent("t");
        SampleEvent e2 = new SampleEvent("t");
        assertThat(e1.getEventId()).hasSize(32).doesNotContain("-");
        assertThat(e2.getEventId()).isNotEqualTo(e1.getEventId());
        assertThat(e1.getOccurredAt()).isBeforeOrEqualTo(LocalDateTime.now());
    }
}
