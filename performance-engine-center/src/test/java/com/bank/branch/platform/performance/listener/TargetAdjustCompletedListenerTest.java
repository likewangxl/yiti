package com.bank.branch.platform.performance.listener;

import com.bank.branch.platform.performance.entity.PerfTargetAdjustApply;
import com.bank.branch.platform.performance.entity.PerfTargetValue;
import com.bank.branch.platform.performance.event.PerfEventPublisher;
import com.bank.branch.platform.performance.event.TargetAdjustmentApprovedEvent;
import com.bank.branch.platform.performance.mapper.PerfTargetAdjustApplyMapper;
import com.bank.branch.platform.performance.mapper.PerfTargetValueMapper;
import com.bank.branch.platform.workflow.api.event.ProcessCompletedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TargetAdjustCompletedListener 单元测试 (V1.2 Q3.2b + Q3.3).
 *
 * <p>订阅 {@code workflow-center} 的 {@code ProcessCompletedEvent}，
 * 按 businessKey 前缀 {@code TARGET_ADJUST:} 筛选本模块关心的流程：
 * <ul>
 *   <li>APPROVED：
 *     <ol>
 *       <li>解析 apply.remark JSON 得到 adjustments</li>
 *       <li>对每个 adjustment，构造 PerfTargetValue 并调 upsertBatch 更新
 *           (planId, subjectType, subjectId, metricCode, cycleKey) 对应的 targetValue</li>
 *       <li>updateStatus(APPROVED)，processInstanceId 传 null 不覆写</li>
 *       <li>发布 {@link TargetAdjustmentApprovedEvent}</li>
 *     </ol>
 *   </li>
 *   <li>REJECTED：仅 updateStatus(REJECTED)，不改目标值，不发事件</li>
 *   <li>非 TARGET_ADJUST: 前缀：直接忽略</li>
 *   <li>apply 不存在：幂等跳过</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TargetAdjustCompletedListenerTest {

    @Mock
    private PerfTargetAdjustApplyMapper applyMapper;

    @Mock
    private PerfTargetValueMapper targetValueMapper;

    @Mock
    private PerfEventPublisher eventPublisher;

    @InjectMocks
    private TargetAdjustCompletedListener listener;

    private PerfTargetAdjustApply buildApply() {
        PerfTargetAdjustApply a = new PerfTargetAdjustApply();
        a.setId("TAA_001");
        a.setPlanId("PLAN_001");
        a.setSubjectType("EMP");
        a.setSubjectId("EMP_001");
        a.setCycleKey("2026Q1");
        a.setStatus("IN_APPROVAL");
        a.setBusinessKey("TARGET_ADJUST:TAA_001");
        a.setOwnerOrgId("ORG_001");
        a.setRemark("{\"adjustments\":["
                + "{\"metricCode\":\"M_DEP_BAL\",\"oldValue\":100,\"newValue\":120},"
                + "{\"metricCode\":\"M_FEE_INCOME\",\"oldValue\":50,\"newValue\":60}"
                + "],\"reason\":\"2026 Q1 目标上调 20%\"}");
        a.setCreatedBy("admin");
        return a;
    }

    @BeforeEach
    void setupCommon() {
        when(applyMapper.selectByBusinessKey("TARGET_ADJUST:TAA_001"))
                .thenReturn(buildApply());
    }

    @Test
    @DisplayName("非 TARGET_ADJUST: 前缀 businessKey 直接跳过")
    void skip_whenBusinessKeyPrefixNotMatch() {
        ProcessCompletedEvent event =
                new ProcessCompletedEvent(
                        "PI_X", "ALLOC_ADJUST:xxx", "APPROVED", null);
        listener.onProcessCompleted(event);

        verify(applyMapper, never()).selectByBusinessKey(anyString());
        verify(applyMapper, never()).updateStatus(anyString(), anyString(), anyString());
        verify(targetValueMapper, never()).upsertBatch(anyList());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("APPROVED 流程 → upsert target_value + 更新 status + 发事件")
    void approved_upsertsTargetValues_updatesStatus_publishesEvent() {
        ProcessCompletedEvent event =
                new ProcessCompletedEvent(
                        "PI_TAA_001", "TARGET_ADJUST:TAA_001", "APPROVED", "审批通过");
        listener.onProcessCompleted(event);

        // upsertBatch 被调用一次，list 长度 = adjustments 数（2）
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PerfTargetValue>> listCap = ArgumentCaptor.forClass(List.class);
        verify(targetValueMapper).upsertBatch(listCap.capture());
        List<PerfTargetValue> list = listCap.getValue();
        assertThat(list).hasSize(2);
        assertThat(list).extracting(PerfTargetValue::getMetricCode)
                .containsExactlyInAnyOrder("M_DEP_BAL", "M_FEE_INCOME");
        // newValue 落地
        assertThat(list).filteredOn(tv -> "M_DEP_BAL".equals(tv.getMetricCode()))
                .hasSize(1)
                .extracting(PerfTargetValue::getTargetValue)
                .first()
                .asString()
                .isEqualTo("120");
        // 五元组字段正确
        assertThat(list).extracting(PerfTargetValue::getPlanId).containsOnly("PLAN_001");
        assertThat(list).extracting(PerfTargetValue::getSubjectType).containsOnly("EMP");
        assertThat(list).extracting(PerfTargetValue::getSubjectId).containsOnly("EMP_001");
        assertThat(list).extracting(PerfTargetValue::getCycleKey).containsOnly("2026Q1");

        // 更新状态 APPROVED（不覆写 processInstanceId，传 null）
        verify(applyMapper).updateStatus("TAA_001", "APPROVED", null);

        // 发事件 TargetAdjustmentApprovedEvent
        ArgumentCaptor<TargetAdjustmentApprovedEvent> evCap =
                ArgumentCaptor.forClass(TargetAdjustmentApprovedEvent.class);
        verify(eventPublisher).publish(evCap.capture());
        TargetAdjustmentApprovedEvent ev = evCap.getValue();
        assertThat(ev.getApplyId()).isEqualTo("TAA_001");
        assertThat(ev.getPlanId()).isEqualTo("PLAN_001");
        assertThat(ev.getSubjectType()).isEqualTo("EMP");
        assertThat(ev.getSubjectId()).isEqualTo("EMP_001");
        assertThat(ev.getCycleKey()).isEqualTo("2026Q1");
        assertThat(ev.getApprovedBy()).isEqualTo("admin");
        assertThat(ev.topic()).isEqualTo("performance.target-adjustment.approved.v1");
    }

    @Test
    @DisplayName("REJECTED 流程 → 仅更新 status=REJECTED，不改目标值，不发事件")
    void rejected_onlyUpdatesStatus_noTargetValueChange_noEvent() {
        ProcessCompletedEvent event =
                new ProcessCompletedEvent(
                        "PI_TAA_001", "TARGET_ADJUST:TAA_001", "REJECTED", "不符合规则");
        listener.onProcessCompleted(event);

        verify(applyMapper).updateStatus("TAA_001", "REJECTED", null);
        verify(targetValueMapper, never()).upsertBatch(anyList());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("apply 不存在 → 幂等跳过，不抛异常")
    void apply_notFound_skipsSilently() {
        when(applyMapper.selectByBusinessKey("TARGET_ADJUST:TAA_001")).thenReturn(null);

        ProcessCompletedEvent event =
                new ProcessCompletedEvent(
                        "PI_TAA_001", "TARGET_ADJUST:TAA_001", "APPROVED", null);
        listener.onProcessCompleted(event);

        verify(applyMapper, never()).updateStatus(anyString(), anyString(), anyString());
        verify(targetValueMapper, never()).upsertBatch(anyList());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("subjectType=ORG 也正常落地（双维度）")
    void orgSubjectType_flowsThrough() {
        PerfTargetAdjustApply orgApply = buildApply();
        orgApply.setSubjectType("ORG");
        orgApply.setSubjectId("ORG_101");
        when(applyMapper.selectByBusinessKey("TARGET_ADJUST:TAA_001")).thenReturn(orgApply);

        ProcessCompletedEvent event =
                new ProcessCompletedEvent(
                        "PI_ORG_001", "TARGET_ADJUST:TAA_001", "APPROVED", null);
        listener.onProcessCompleted(event);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PerfTargetValue>> listCap = ArgumentCaptor.forClass(List.class);
        verify(targetValueMapper).upsertBatch(listCap.capture());
        assertThat(listCap.getValue()).extracting(PerfTargetValue::getSubjectType)
                .containsOnly("ORG");
        assertThat(listCap.getValue()).extracting(PerfTargetValue::getSubjectId)
                .containsOnly("ORG_101");

        ArgumentCaptor<TargetAdjustmentApprovedEvent> evCap =
                ArgumentCaptor.forClass(TargetAdjustmentApprovedEvent.class);
        verify(eventPublisher).publish(evCap.capture());
        assertThat(evCap.getValue().getSubjectType()).isEqualTo("ORG");
        assertThat(evCap.getValue().getSubjectId()).isEqualTo("ORG_101");
    }

    @Test
    @DisplayName("未识别 outcome（如 CANCELLED）→ 保守不改数据，不发事件")
    void unknownOutcome_noChanges() {
        ProcessCompletedEvent event =
                new ProcessCompletedEvent(
                        "PI_TAA_001", "TARGET_ADJUST:TAA_001", "CANCELLED", null);
        listener.onProcessCompleted(event);

        verify(applyMapper, never()).updateStatus(anyString(), anyString(), anyString());
        verify(targetValueMapper, never()).upsertBatch(anyList());
        verify(eventPublisher, never()).publish(any());
    }
}
