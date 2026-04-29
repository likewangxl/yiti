package com.bank.branch.platform.performance.event;

import com.bank.branch.platform.performance.entity.PerfTargetAdjustApply;
import com.bank.branch.platform.performance.mapper.PerfTargetAdjustApplyMapper;
import com.bank.branch.platform.performance.mapper.PerfTargetValueMapper;
import com.bank.branch.platform.performance.support.PerfTestApp;
import com.bank.branch.platform.performance.support.PerfTestConfig;
import com.bank.branch.platform.workflow.api.event.ProcessCompletedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * 目标修正审批通过事件端到端验证 (V1.2 Q3.3).
 *
 * <p>在完整 Spring 上下文中：
 * <ol>
 *   <li>持久化一条 IN_APPROVAL 的 PerfTargetAdjustApply（remark 承载 adjustments JSON）</li>
 *   <li>在事务内模拟 workflow-center 发布 ProcessCompletedEvent(APPROVED)</li>
 *   <li>验证 TargetAdjustCompletedListener 执行完毕后
 *       TargetAdjustmentApprovedEvent 被 {@link PerfEventPublisher} 通过
 *       Spring {@link ApplicationEventPublisher} 投递</li>
 *   <li>断言事件字段：applyId/planId/subjectType/subjectId/cycleKey/approvedBy/topic</li>
 * </ol>
 *
 * <p>说明：
 * <ul>
 *   <li>PerfTargetValueMapper 通过 @MockBean 屏蔽实际写库（target_value 的 upsertBatch
 *       跨事务写入，避免测试数据残留）</li>
 *   <li>测试数据使用 TEST_Q33_ 前缀便于追踪</li>
 * </ul>
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = {
        PerfTestApp.class,
        PerfTestConfig.class,
        TargetAdjustmentApprovedEventIT.TestEventCollector.class
})
@ActiveProfiles("test")
class TargetAdjustmentApprovedEventIT {

    @MockBean
    private PerfTargetValueMapper targetValueMapper;

    @Autowired
    private PerfTargetAdjustApplyMapper applyMapper;

    @Autowired
    private ApplicationEventPublisher springEventPublisher;

    @Autowired
    private PlatformTransactionManager txManager;

    @Autowired
    private TestEventCollector collector;

    @BeforeEach
    void setUp() {
        collector.received.clear();
        // upsertBatch mock 返回成功（语义：N 条全插入）
        when(targetValueMapper.upsertBatch(anyList())).thenReturn(0);
    }

    @Test
    @DisplayName("APPROVED 流程 → TargetAdjustmentApprovedEvent 事件被发布")
    void approved_publishesEvent_viaPerfEventPublisher() {
        // 1. 准备申请（remark 承载 adjustments JSON）
        String applyId = "TEST_Q33_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String businessKey = "TARGET_ADJUST:" + applyId;
        PerfTargetAdjustApply apply = new PerfTargetAdjustApply();
        apply.setId(applyId);
        apply.setPlanId("TEST_Q33_PLAN_X");
        apply.setSubjectType("EMP");
        apply.setSubjectId("EMP_Q33_001");
        apply.setCycleKey("2026Q1");
        apply.setStatus("IN_APPROVAL");
        apply.setBusinessKey(businessKey);
        apply.setProcessInstanceId("PI_Q33_" + applyId);
        apply.setOwnerOrgId("ORG_Q33");
        apply.setRemark("{\"adjustments\":["
                + "{\"metricCode\":\"M_DEP_BAL_Q33\",\"oldValue\":100,\"newValue\":120}],"
                + "\"reason\":\"Q3.3 IT\"}");
        apply.setCreatedBy("q33_user");
        apply.setUpdatedBy("q33_user");
        apply.setCreatedTime(LocalDateTime.now());
        apply.setUpdatedTime(LocalDateTime.now());
        applyMapper.insert(apply);

        try {
            // 2. 在事务内发布 ProcessCompletedEvent（模拟 workflow-center afterCommit 触发）
            TransactionTemplate tt = new TransactionTemplate(txManager);
            tt.execute(new TransactionCallbackWithoutResult() {
                @Override
                protected void doInTransactionWithoutResult(TransactionStatus status) {
                    ProcessCompletedEvent event =
                            new ProcessCompletedEvent(
                                    "PI_Q33_" + applyId, businessKey, "APPROVED", "审批通过");
                    springEventPublisher.publishEvent(event);
                }
            });

            // 3. 断言 collector 收到 TargetAdjustmentApprovedEvent
            assertThat(collector.received).hasSize(1);
            TargetAdjustmentApprovedEvent ev = collector.received.get(0);
            assertThat(ev.getApplyId()).isEqualTo(applyId);
            assertThat(ev.getPlanId()).isEqualTo("TEST_Q33_PLAN_X");
            assertThat(ev.getSubjectType()).isEqualTo("EMP");
            assertThat(ev.getSubjectId()).isEqualTo("EMP_Q33_001");
            assertThat(ev.getCycleKey()).isEqualTo("2026Q1");
            assertThat(ev.getApprovedBy()).isEqualTo("q33_user");
            assertThat(ev.topic()).isEqualTo("performance.target-adjustment.approved.v1");
            assertThat(ev.getEventId()).hasSize(32).doesNotContain("-");
            assertThat(ev.getOccurredAt()).isNotNull();
        } finally {
            // PerfTargetAdjustApplyMapper 未提供 delete 方法，TEST_Q33_ 前缀易识别
        }
    }

    @Test
    @DisplayName("REJECTED 流程 → 不发布 TargetAdjustmentApprovedEvent")
    void rejected_doesNotPublishEvent() {
        String applyId = "TEST_Q33_R_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String businessKey = "TARGET_ADJUST:" + applyId;
        PerfTargetAdjustApply apply = new PerfTargetAdjustApply();
        apply.setId(applyId);
        apply.setPlanId("TEST_Q33_PLAN_R");
        apply.setSubjectType("ORG");
        apply.setSubjectId("ORG_Q33_101");
        apply.setCycleKey("2026Q2");
        apply.setStatus("IN_APPROVAL");
        apply.setBusinessKey(businessKey);
        apply.setProcessInstanceId("PI_Q33_R_" + applyId);
        apply.setOwnerOrgId("ORG_Q33");
        apply.setRemark("{\"adjustments\":[],\"reason\":\"REJECT IT\"}");
        apply.setCreatedBy("q33_user");
        apply.setUpdatedBy("q33_user");
        apply.setCreatedTime(LocalDateTime.now());
        apply.setUpdatedTime(LocalDateTime.now());
        applyMapper.insert(apply);

        try {
            TransactionTemplate tt = new TransactionTemplate(txManager);
            tt.execute(new TransactionCallbackWithoutResult() {
                @Override
                protected void doInTransactionWithoutResult(TransactionStatus status) {
                    ProcessCompletedEvent event =
                            new ProcessCompletedEvent(
                                    "PI_Q33_R_" + applyId, businessKey, "REJECTED", "不通过");
                    springEventPublisher.publishEvent(event);
                }
            });

            assertThat(collector.received).isEmpty();
        } finally {
            // cleanup placeholder
        }
    }

    /**
     * 测试 Listener：捕获发布的 TargetAdjustmentApprovedEvent 供断言.
     */
    @Component
    static class TestEventCollector {
        final CopyOnWriteArrayList<TargetAdjustmentApprovedEvent> received =
                new CopyOnWriteArrayList<>();

        @EventListener
        public void on(TargetAdjustmentApprovedEvent event) {
            received.add(event);
        }
    }
}
