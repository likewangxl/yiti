package com.bank.branch.platform.performance.event;

import com.bank.branch.platform.performance.support.PerfTestApp;
import com.bank.branch.platform.performance.support.PerfTestConfig;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.LocalDate;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 事件订阅 Demo 集成测试 (V1.2 Q4.4).
 *
 * <p><strong>目的：</strong>向下游模块（customer-marketing / portal / report）展示如何订阅
 * performance 发布的 4 类领域事件。本类不是生产代码，仅作测试示例。
 *
 * <p><strong>订阅模式：</strong>
 * <ul>
 *   <li>同步订阅（{@code @EventListener}）—— 本 Demo 采用，便于同步断言</li>
 *   <li>事务后订阅（{@code @TransactionalEventListener(AFTER_COMMIT)}）—— 生产推荐，
 *       消费者自身若有 DB 写操作可保证源事务已 commit</li>
 *   <li>异步订阅（{@code @Async @EventListener}）—— 消费者耗时逻辑时使用，避免阻塞发布线程</li>
 * </ul>
 *
 * <p><strong>下游模块接入步骤（示例）：</strong>
 * <pre>{@code
 * // 1. 声明依赖：pom.xml 引入 performance-engine-center（仅为事件 POJO 访问）
 * // 2. 定义监听 Bean：
 * @Component
 * public class PerformanceEventSubscriber {
 *     @EventListener
 *     public void onKpiCalcCompleted(KpiCalcCompletedEvent e) {
 *         // 拉起报表快照刷新 / 缓存失效 / 通知推送
 *     }
 * }
 * // 3. 任意 ApplicationContext 启动时自动挂接 —— 无需额外配置
 * }</pre>
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = {
        PerfTestApp.class,
        PerfTestConfig.class,
        PerfEventConsumerDemoTest.DemoSubscriber.class
})
@ActiveProfiles("test")
class PerfEventConsumerDemoTest {

    @Autowired
    private PerfEventPublisher publisher;

    @Autowired
    private DemoSubscriber subscriber;

    @BeforeEach
    void reset() {
        subscriber.allReceived.clear();
        subscriber.sysControlReceived.clear();
        subscriber.kpiReceived.clear();
        subscriber.targetReceived.clear();
        subscriber.allocReceived.clear();
    }

    @Test
    @DisplayName("Demo 订阅者收到全部 4 类领域事件")
    void demo_subscriberReceives_allFourEvents() {
        // 构造并发布 4 类事件
        publisher.publish(new SysControlUpdatedEvent(
                "trace-demo-1", "EMP", "V1", "V2", "MANUAL", "admin"));
        publisher.publish(new KpiCalcCompletedEvent(
                "trace-demo-2", "DEMO_SCHEME", "MONTHLY",
                LocalDate.of(2026, 4, 30), LocalDate.of(2026, 5, 1), "V_DEMO", 10));
        publisher.publish(new TargetAdjustmentApprovedEvent(
                "trace-demo-3", "APPLY_001", "PLAN_T1", "EMP", "E001", "2026Q2", "boss"));
        publisher.publish(new AllocationAdjustmentApprovedEvent(
                "trace-demo-4", "APPLY_A1", "CUST_X", "OWNER", "DEPOSIT", 3, "approver1"));

        // 4 条共计
        assertThat(subscriber.allReceived).hasSize(4);

        // 分类监听器各自收到 1 条
        assertThat(subscriber.sysControlReceived).hasSize(1);
        assertThat(subscriber.sysControlReceived.get(0).topic())
                .isEqualTo("performance.sys-control.updated.v1");

        assertThat(subscriber.kpiReceived).hasSize(1);
        assertThat(subscriber.kpiReceived.get(0).getSchemeCode()).isEqualTo("DEMO_SCHEME");
        assertThat(subscriber.kpiReceived.get(0).getEmpCount()).isEqualTo(10);

        assertThat(subscriber.targetReceived).hasSize(1);
        assertThat(subscriber.targetReceived.get(0).getCycleKey()).isEqualTo("2026Q2");

        assertThat(subscriber.allocReceived).hasSize(1);
        assertThat(subscriber.allocReceived.get(0).getItemCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("Demo 订阅者按顺序接收事件，事件 id 不重复")
    void demo_eventIdsAreUnique() {
        publisher.publish(new SysControlUpdatedEvent(
                "trace-u1", "EMP", "V_A", "V_B", "MANUAL", "admin"));
        publisher.publish(new SysControlUpdatedEvent(
                "trace-u2", "ORG", "V_C", "V_D", "AUTO", "sys"));

        assertThat(subscriber.sysControlReceived).hasSize(2);
        String id1 = subscriber.sysControlReceived.get(0).getEventId();
        String id2 = subscriber.sysControlReceived.get(1).getEventId();
        assertThat(id1).isNotEqualTo(id2);
        assertThat(id1).hasSize(32);
        assertThat(id2).hasSize(32);
    }

    /**
     * Demo 下游订阅者——同时订阅 4 类 performance 领域事件的典型样板.
     */
    @Slf4j
    @Component
    static class DemoSubscriber {
        final CopyOnWriteArrayList<PerfDomainEvent> allReceived = new CopyOnWriteArrayList<>();
        final CopyOnWriteArrayList<SysControlUpdatedEvent> sysControlReceived = new CopyOnWriteArrayList<>();
        final CopyOnWriteArrayList<KpiCalcCompletedEvent> kpiReceived = new CopyOnWriteArrayList<>();
        final CopyOnWriteArrayList<TargetAdjustmentApprovedEvent> targetReceived = new CopyOnWriteArrayList<>();
        final CopyOnWriteArrayList<AllocationAdjustmentApprovedEvent> allocReceived = new CopyOnWriteArrayList<>();

        /** 版本控制事件：下游可据此刷新缓存 / 推送告警. */
        @EventListener
        public void onSysControlUpdated(SysControlUpdatedEvent e) {
            log.info("[Demo] 收到 SysControlUpdated: scopeDim={} {}→{} by {}",
                    e.getScopeDim(), e.getOldVersion(), e.getNewVersion(), e.getPublishBy());
            allReceived.add(e);
            sysControlReceived.add(e);
        }

        /** KPI 计算完成事件：下游可据此刷新报表快照 / 发送员工通知. */
        @EventListener
        public void onKpiCalcCompleted(KpiCalcCompletedEvent e) {
            log.info("[Demo] 收到 KpiCalcCompleted: scheme={} empCount={}",
                    e.getSchemeCode(), e.getEmpCount());
            allReceived.add(e);
            kpiReceived.add(e);
        }

        /** 目标调整审批通过事件：下游可据此失效 target_value 缓存 / 通知员工新目标. */
        @EventListener
        public void onTargetAdjustApproved(TargetAdjustmentApprovedEvent e) {
            log.info("[Demo] 收到 TargetAdjustmentApproved: plan={} subject={}/{} cycle={}",
                    e.getPlanId(), e.getSubjectType(), e.getSubjectId(), e.getCycleKey());
            allReceived.add(e);
            targetReceived.add(e);
        }

        /** 分配调整审批通过事件：下游可据此失效 cust_alloc_relation 缓存 / 通知客户经理. */
        @EventListener
        public void onAllocationAdjustApproved(AllocationAdjustmentApprovedEvent e) {
            log.info("[Demo] 收到 AllocationAdjustmentApproved: cust={} allocDim={} itemCount={}",
                    e.getCustId(), e.getAllocDim(), e.getItemCount());
            allReceived.add(e);
            allocReceived.add(e);
        }
    }
}
