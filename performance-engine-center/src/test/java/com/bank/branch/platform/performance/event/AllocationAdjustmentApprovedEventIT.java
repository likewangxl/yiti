package com.bank.branch.platform.performance.event;

import com.bank.branch.platform.customer.api.CustomerQueryApi;
import com.bank.branch.platform.performance.entity.PerfAllocAdjustApply;
import com.bank.branch.platform.performance.entity.PerfAllocAdjustItem;
import com.bank.branch.platform.performance.listener.AllocAdjustCompletedListener;
import com.bank.branch.platform.performance.mapper.CustAllocRelationMapper;
import com.bank.branch.platform.performance.mapper.PerfAllocAdjustApplyMapper;
import com.bank.branch.platform.performance.mapper.PerfAllocAdjustItemMapper;
import com.bank.branch.platform.performance.support.PerfTestApp;
import com.bank.branch.platform.performance.support.PerfTestConfig;
import com.bank.branch.platform.workflow.api.WorkflowApi;
import com.bank.branch.platform.workflow.api.event.ProcessCompletedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 分配关系调整审批通过事件端到端验证 (V1.2 Q2.6).
 *
 * <p>在完整 Spring 上下文中：
 * <ol>
 *   <li>持久化一条 IN_APPROVAL 的 PerfAllocAdjustApply + items</li>
 *   <li>在事务内模拟 workflow-center 发布 ProcessCompletedEvent(APPROVED)</li>
 *   <li>验证 AllocAdjustCompletedListener 执行完毕后
 *       AllocationAdjustmentApprovedEvent 被 {@link PerfEventPublisher} 通过
 *       Spring {@link ApplicationEventPublisher} 投递</li>
 *   <li>断言事件字段：custId/allocDim/bizKind/itemCount/approvedBy/topic</li>
 * </ol>
 *
 * <p>说明：
 * <ul>
 *   <li>CustAllocRelationMapper 通过 @MockBean 屏蔽实际写库，避免测试数据残留
 *       （事务回滚可能受独立事务 REQUIRES_NEW 影响）</li>
 *   <li>测试数据使用 TEST_Q26_ 前缀便于追踪</li>
 * </ul>
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = {
        PerfTestApp.class,
        PerfTestConfig.class,
        AllocationAdjustmentApprovedEventIT.TestEventCollector.class
})
@ActiveProfiles("test")
class AllocationAdjustmentApprovedEventIT {

    @MockBean
    private CustAllocRelationMapper custAllocRelationMapper;

    @MockBean
    private CustomerQueryApi customerQueryApi;

    @MockBean
    private WorkflowApi workflowApi;

    @Autowired
    private PerfAllocAdjustApplyMapper applyMapper;

    @Autowired
    private PerfAllocAdjustItemMapper itemMapper;

    @Autowired
    private ApplicationEventPublisher springEventPublisher;

    @Autowired
    private PlatformTransactionManager txManager;

    @Autowired
    private TestEventCollector collector;

    @BeforeEach
    void setUp() {
        collector.received.clear();
        // Mapper mock：下游写入不抛，便于 listener 顺利完成
        when(custAllocRelationMapper.insert(any())).thenReturn(1);
    }

    @Test
    @DisplayName("APPROVED 流程 → AllocationAdjustmentApprovedEvent 事件被发布")
    void approved_publishesEvent_viaPerfEventPublisher() {
        // 1. 准备申请 + 明细（走真实 DB，事务外插入避免被流程回滚冲掉）
        String applyId = "TEST_Q26_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String businessKey = "ALLOC_ADJUST:" + applyId;
        PerfAllocAdjustApply apply = new PerfAllocAdjustApply();
        apply.setId(applyId);
        apply.setApplyNo("AA_Q26_" + applyId);
        apply.setCustId("TEST_Q26_CUST_X");
        apply.setAllocDim("RULE");
        apply.setBizKind("CORP_LOAN");
        apply.setStatus("IN_APPROVAL");
        apply.setBusinessKey(businessKey);
        apply.setProcessInstanceId("PI_Q26_" + applyId);
        apply.setOwnerOrgId("ORG_Q26");
        apply.setRemark("Q2.6 IT");
        apply.setCreatedBy("q26_user");
        apply.setUpdatedBy("q26_user");
        apply.setCreatedTime(java.time.LocalDateTime.now());
        apply.setUpdatedTime(java.time.LocalDateTime.now());
        applyMapper.insert(apply);

        PerfAllocAdjustItem it1 = new PerfAllocAdjustItem();
        it1.setId("TEST_Q26_I1_" + applyId);
        it1.setApplyId(applyId);
        it1.setEmpId("EMP_Q26_A");
        it1.setRatio(new BigDecimal("55.00"));
        PerfAllocAdjustItem it2 = new PerfAllocAdjustItem();
        it2.setId("TEST_Q26_I2_" + applyId);
        it2.setApplyId(applyId);
        it2.setEmpId("EMP_Q26_B");
        it2.setRatio(new BigDecimal("45.00"));
        itemMapper.batchInsert(Arrays.asList(it1, it2));

        try {
            // 2. 在事务内发布 ProcessCompletedEvent，模拟 workflow-center afterCommit 触发
            TransactionTemplate tt = new TransactionTemplate(txManager);
            tt.execute(new TransactionCallbackWithoutResult() {
                @Override
                protected void doInTransactionWithoutResult(TransactionStatus status) {
                    ProcessCompletedEvent event =
                            new ProcessCompletedEvent(
                                    "PI_Q26_" + applyId, businessKey, "APPROVED", "审批通过");
                    springEventPublisher.publishEvent(event);
                }
            });

            // 3. 断言 collector 收到 AllocationAdjustmentApprovedEvent
            assertThat(collector.received).hasSize(1);
            AllocationAdjustmentApprovedEvent ev = collector.received.get(0);
            assertThat(ev.getApplyId()).isEqualTo(applyId);
            assertThat(ev.getCustId()).isEqualTo("TEST_Q26_CUST_X");
            assertThat(ev.getAllocDim()).isEqualTo("RULE");
            assertThat(ev.getBizKind()).isEqualTo("CORP_LOAN");
            assertThat(ev.getItemCount()).isEqualTo(2);
            assertThat(ev.getApprovedBy()).isEqualTo("q26_user");
            assertThat(ev.topic()).isEqualTo("performance.allocation-adjustment.approved.v1");
            assertThat(ev.getEventId()).hasSize(32).doesNotContain("-");
            assertThat(ev.getOccurredAt()).isNotNull();
        } finally {
            // 清理（IT 不在外层事务回滚内，手工清理以保持 DB 干净）
            itemMapper.deleteByApplyId(applyId);
            // PerfAllocAdjustApply 没有 delete 方法，留存无妨（TEST_Q26_ 前缀易识别）
        }
    }

    @Test
    @DisplayName("REJECTED 流程 → 不发布 AllocationAdjustmentApprovedEvent")
    void rejected_doesNotPublishEvent() {
        String applyId = "TEST_Q26_R_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String businessKey = "ALLOC_ADJUST:" + applyId;
        PerfAllocAdjustApply apply = new PerfAllocAdjustApply();
        apply.setId(applyId);
        apply.setApplyNo("AA_Q26_R_" + applyId);
        apply.setCustId("TEST_Q26_CUST_R");
        apply.setAllocDim("RULE");
        apply.setBizKind("RETAIL_CARD");
        apply.setStatus("IN_APPROVAL");
        apply.setBusinessKey(businessKey);
        apply.setProcessInstanceId("PI_Q26_R_" + applyId);
        apply.setOwnerOrgId("ORG_Q26");
        apply.setCreatedBy("q26_user");
        apply.setUpdatedBy("q26_user");
        apply.setCreatedTime(java.time.LocalDateTime.now());
        apply.setUpdatedTime(java.time.LocalDateTime.now());
        applyMapper.insert(apply);

        try {
            TransactionTemplate tt = new TransactionTemplate(txManager);
            tt.execute(new TransactionCallbackWithoutResult() {
                @Override
                protected void doInTransactionWithoutResult(TransactionStatus status) {
                    ProcessCompletedEvent event =
                            new ProcessCompletedEvent(
                                    "PI_Q26_R_" + applyId, businessKey, "REJECTED", "不通过");
                    springEventPublisher.publishEvent(event);
                }
            });

            assertThat(collector.received).isEmpty();
        } finally {
            // 清理
        }
    }

    /**
     * 测试 Listener：捕获发布的 AllocationAdjustmentApprovedEvent 供断言.
     */
    @Component
    static class TestEventCollector {
        final CopyOnWriteArrayList<AllocationAdjustmentApprovedEvent> received =
                new CopyOnWriteArrayList<>();

        @EventListener
        public void on(AllocationAdjustmentApprovedEvent event) {
            received.add(event);
        }
    }
}
