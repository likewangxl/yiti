package com.bank.branch.platform.performance.listener;

import com.bank.branch.platform.performance.entity.CustAllocRelation;
import com.bank.branch.platform.performance.entity.PerfAllocAdjustApply;
import com.bank.branch.platform.performance.entity.PerfAllocAdjustItem;
import com.bank.branch.platform.performance.event.AllocationAdjustmentApprovedEvent;
import com.bank.branch.platform.performance.event.PerfEventPublisher;
import com.bank.branch.platform.performance.mapper.CustAllocRelationMapper;
import com.bank.branch.platform.performance.mapper.PerfAllocAdjustApplyMapper;
import com.bank.branch.platform.performance.mapper.PerfAllocAdjustItemMapper;
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

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AllocAdjustCompletedListener 单元测试 (V1.2 Q2.3 + Q2.6).
 *
 * <p>订阅 {@code workflow-center} 的 {@code ProcessCompletedEvent}，
 * 按 businessKey 前缀 {@code ALLOC_ADJUST:} 筛选本模块关心的流程，
 * APPROVED 时：
 * <ol>
 *   <li>读 perf_alloc_adjust_item 得到明细</li>
 *   <li>为每个 item 插入 cust_alloc_relation 新记录（旧记录通过 effective_date 切分时间线）</li>
 *   <li>更新 apply.status=APPROVED</li>
 *   <li>发布 {@link AllocationAdjustmentApprovedEvent}</li>
 * </ol>
 * REJECTED 时：仅更新 apply.status=REJECTED，不改 cust_alloc_relation，不发事件.
 * businessKey 前缀不匹配时：直接忽略.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AllocAdjustCompletedListenerTest {

    @Mock
    private PerfAllocAdjustApplyMapper applyMapper;

    @Mock
    private PerfAllocAdjustItemMapper itemMapper;

    @Mock
    private CustAllocRelationMapper allocRelationMapper;

    @Mock
    private PerfEventPublisher eventPublisher;

    @InjectMocks
    private AllocAdjustCompletedListener listener;

    private PerfAllocAdjustApply buildApply() {
        PerfAllocAdjustApply a = new PerfAllocAdjustApply();
        a.setId("APP_001");
        a.setApplyNo("AA20260423ABCDEFGH");
        a.setCustId("CUST_001");
        a.setAllocDim("RULE");
        a.setBizKind("CORP_LOAN");
        a.setStatus("IN_APPROVAL");
        a.setBusinessKey("ALLOC_ADJUST:APP_001");
        a.setOwnerOrgId("ORG_001");
        a.setCreatedBy("admin");
        return a;
    }

    private PerfAllocAdjustItem item(String empId, String ratio) {
        PerfAllocAdjustItem i = new PerfAllocAdjustItem();
        i.setId("IT_" + empId);
        i.setApplyId("APP_001");
        i.setEmpId(empId);
        i.setRatio(new BigDecimal(ratio));
        return i;
    }

    @BeforeEach
    void setupCommon() {
        when(applyMapper.selectByBusinessKey("ALLOC_ADJUST:APP_001"))
                .thenReturn(buildApply());
        when(itemMapper.selectByApplyId("APP_001"))
                .thenReturn(Arrays.asList(
                        item("EMP_A", "60.00"),
                        item("EMP_B", "40.00")));
    }

    @Test
    @DisplayName("非 ALLOC_ADJUST: 前缀 businessKey 直接跳过")
    void skip_whenBusinessKeyPrefixNotMatch() {
        ProcessCompletedEvent event =
                new ProcessCompletedEvent(
                        "PI_X", "LEAD:xxx", "APPROVED", null);
        listener.onProcessCompleted(event);

        verify(applyMapper, never()).selectByBusinessKey(anyString());
        verify(applyMapper, never()).updateStatus(anyString(), anyString(), anyString());
        verify(allocRelationMapper, never()).insert(any(CustAllocRelation.class));
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("APPROVED 流程 → 插入新分配关系 + 更新 status + 发事件")
    void approved_insertsAllocation_updatesStatus_publishesEvent() {
        ProcessCompletedEvent event =
                new ProcessCompletedEvent(
                        "PI_APP_001", "ALLOC_ADJUST:APP_001", "APPROVED", "审批通过");
        listener.onProcessCompleted(event);

        // 每个 item 对应一条 cust_alloc_relation insert
        ArgumentCaptor<CustAllocRelation> relCap = ArgumentCaptor.forClass(CustAllocRelation.class);
        verify(allocRelationMapper, org.mockito.Mockito.times(2)).insert(relCap.capture());
        List<CustAllocRelation> inserted = relCap.getAllValues();
        assertThat(inserted).extracting(CustAllocRelation::getEmpId)
                .containsExactlyInAnyOrder("EMP_A", "EMP_B");
        assertThat(inserted).extracting(CustAllocRelation::getCustId)
                .containsOnly("CUST_001");
        assertThat(inserted).extracting(CustAllocRelation::getAllocDim)
                .containsOnly("RULE");
        assertThat(inserted).extracting(CustAllocRelation::getBizKind)
                .containsOnly("CORP_LOAN");

        // 更新状态 APPROVED（不覆写 processInstanceId，传 null）
        verify(applyMapper).updateStatus("APP_001", "APPROVED", null);

        // 发事件
        ArgumentCaptor<AllocationAdjustmentApprovedEvent> evCap =
                ArgumentCaptor.forClass(AllocationAdjustmentApprovedEvent.class);
        verify(eventPublisher).publish(evCap.capture());
        AllocationAdjustmentApprovedEvent ev = evCap.getValue();
        assertThat(ev.getApplyId()).isEqualTo("APP_001");
        assertThat(ev.getCustId()).isEqualTo("CUST_001");
        assertThat(ev.getAllocDim()).isEqualTo("RULE");
        assertThat(ev.getBizKind()).isEqualTo("CORP_LOAN");
        assertThat(ev.getItemCount()).isEqualTo(2);
        assertThat(ev.topic()).isEqualTo("performance.allocation-adjustment.approved.v1");
    }

    @Test
    @DisplayName("REJECTED 流程 → 仅更新 status=REJECTED，不改分配关系，不发事件")
    void rejected_onlyUpdatesStatus_noAllocationChange_noEvent() {
        ProcessCompletedEvent event =
                new ProcessCompletedEvent(
                        "PI_APP_001", "ALLOC_ADJUST:APP_001", "REJECTED", "不符合规则");
        listener.onProcessCompleted(event);

        verify(applyMapper).updateStatus("APP_001", "REJECTED", null);
        verify(allocRelationMapper, never()).insert(any(CustAllocRelation.class));
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("apply 不存在 → 幂等跳过，不抛异常")
    void apply_notFound_skipsSilently() {
        when(applyMapper.selectByBusinessKey("ALLOC_ADJUST:APP_001"))
                .thenReturn(null);

        ProcessCompletedEvent event =
                new ProcessCompletedEvent(
                        "PI_APP_001", "ALLOC_ADJUST:APP_001", "APPROVED", null);
        listener.onProcessCompleted(event);

        verify(applyMapper, never()).updateStatus(anyString(), anyString(), anyString());
        verify(allocRelationMapper, never()).insert(any(CustAllocRelation.class));
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("ORIGIN（原业绩分配）明细不落地为新分配关系，仅 NEW 明细生成 cust_alloc_relation")
    void originItems_excludedFromAllocation() {
        PerfAllocAdjustItem origin = item("EMP_OLD", "100.00");
        origin.setItemKind("ORIGIN");
        PerfAllocAdjustItem neo = item("EMP_A", "60.00");
        neo.setItemKind("NEW");
        when(itemMapper.selectByApplyId("APP_001")).thenReturn(Arrays.asList(origin, neo));

        listener.onProcessCompleted(
                new ProcessCompletedEvent("PI_APP_001", "ALLOC_ADJUST:APP_001", "APPROVED", null));

        // 仅 NEW 明细落地为分配关系；ORIGIN 仅用于会签/留痕，不得成为生效分配
        ArgumentCaptor<CustAllocRelation> cap = ArgumentCaptor.forClass(CustAllocRelation.class);
        verify(allocRelationMapper, org.mockito.Mockito.times(1)).insert(cap.capture());
        assertThat(cap.getValue().getEmpId()).isEqualTo("EMP_A");
        // 事件 itemCount 也只计 NEW
        ArgumentCaptor<AllocationAdjustmentApprovedEvent> evCap =
                ArgumentCaptor.forClass(AllocationAdjustmentApprovedEvent.class);
        verify(eventPublisher).publish(evCap.capture());
        assertThat(evCap.getValue().getItemCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("手工录入客户 cust_id 为 null → 分配关系 cust_id 回退用 cust_no（避免 NOT NULL 插入失败致状态卡死）")
    void manualCustomer_nullCustId_fallsBackToCustNo() {
        PerfAllocAdjustApply manual = buildApply();
        manual.setCustId(null);     // 手工录入客户：主档未命中，cust_id 按设计为 null
        manual.setCustNo("bbc");    // 客户号只在 cust_no
        when(applyMapper.selectByBusinessKey("ALLOC_ADJUST:APP_001")).thenReturn(manual);

        ProcessCompletedEvent event =
                new ProcessCompletedEvent(
                        "PI_APP_001", "ALLOC_ADJUST:APP_001", "APPROVED", null);
        listener.onProcessCompleted(event);

        // 关键：relation.cust_id 必须非空（回退 cust_no），否则 DB NOT NULL 约束抛异常 → 状态卡在 IN_APPROVAL
        ArgumentCaptor<CustAllocRelation> relCap = ArgumentCaptor.forClass(CustAllocRelation.class);
        verify(allocRelationMapper, org.mockito.Mockito.times(2)).insert(relCap.capture());
        assertThat(relCap.getAllValues()).extracting(CustAllocRelation::getCustId)
                .containsOnly("bbc");
        // 状态正常推进
        verify(applyMapper).updateStatus("APP_001", "APPROVED", null);
    }

    @Test
    @DisplayName("RETAIL_CARD 对 retail_v1 流程也能正常落地（bizKind 回放到事件）")
    void retailBizKind_flowsThrough() {
        PerfAllocAdjustApply retail = buildApply();
        retail.setBizKind("RETAIL_CARD");
        when(applyMapper.selectByBusinessKey("ALLOC_ADJUST:APP_001")).thenReturn(retail);

        ProcessCompletedEvent event =
                new ProcessCompletedEvent(
                        "PI_RET_001", "ALLOC_ADJUST:APP_001", "APPROVED", null);
        listener.onProcessCompleted(event);

        ArgumentCaptor<AllocationAdjustmentApprovedEvent> evCap =
                ArgumentCaptor.forClass(AllocationAdjustmentApprovedEvent.class);
        verify(eventPublisher).publish(evCap.capture());
        assertThat(evCap.getValue().getBizKind()).isEqualTo("RETAIL_CARD");
    }
}
