package com.bank.branch.platform.performance.listener;

import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.UserDTO;
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
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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

    @Mock
    private UserApi userApi;

    @InjectMocks
    private AllocAdjustCompletedListener listener;

    private UserDTO userDto(String empId, String name, String orgCode, String orgName) {
        UserDTO u = new UserDTO();
        u.setEmpId(empId);
        u.setUsername(empId);
        u.setDisplayName(name);
        u.setMainOrgCode(orgCode);
        u.setMainOrgName(orgName);
        return u;
    }

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
        a.setCustType("CORP");
        a.setCreatedBy("admin");
        return a;
    }

    private PerfAllocAdjustItem item(String empId, String ratio) {
        return item(empId, ratio, null);
    }

    private PerfAllocAdjustItem item(String empId, String ratio, String itemKind) {
        PerfAllocAdjustItem i = new PerfAllocAdjustItem();
        i.setId("IT_" + empId);
        i.setApplyId("APP_001");
        i.setItemKind(itemKind);
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
        // 工号→用户：审批通过插入时回填 fullname/dept_no/dept_name
        when(userApi.getUserByEmpIds(any()))
                .thenReturn(Arrays.asList(
                        userDto("EMP_A", "张三", "O1", "机构一"),
                        userDto("EMP_B", "李四", "O2", "机构二")));
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
    @DisplayName("APPROVED → 先把该客户(cust_id)全部 is_original=2 旧分配置为原(is_original=1)，再插入新分配(is_original=2)")
    void approved_marksAllCustomerOriginal_insertsNewAsCurrent() {
        listener.onProcessCompleted(
                new ProcessCompletedEvent("PI_APP_001", "ALLOC_ADJUST:APP_001", "APPROVED", null));

        // 插入新分配前：按 cust_id 把该客户全部 is_original=2 的存量分配标记为原分配(is_original=1)
        // 并把失效日期 end_date 置为当天，确保只有本次新插入的记录 is_original=2
        // account_no 由审批申请传入；本例 apply.accountNo 为 null（RULE 维度）→ 按 IS NULL 匹配
        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(allocRelationMapper);
        inOrder.verify(allocRelationMapper).markAllOriginalByCustId(
                eq("CUST_001"), isNull(), eq(LocalDate.now()));
        inOrder.verify(allocRelationMapper, org.mockito.Mockito.times(2))
                .insert(any(CustAllocRelation.class));

        // 新分配默认 is_original=2，cust_type 取自审批申请
        ArgumentCaptor<CustAllocRelation> cap = ArgumentCaptor.forClass(CustAllocRelation.class);
        verify(allocRelationMapper, org.mockito.Mockito.times(2)).insert(cap.capture());
        assertThat(cap.getAllValues()).extracting(CustAllocRelation::getIsOriginal).containsOnly("2");
        assertThat(cap.getAllValues()).extracting(CustAllocRelation::getCustType).containsOnly("CORP");
    }

    @Test
    @DisplayName("APPROVED → 降级时 account_no 由审批申请传入（ACCOUNT 维度按具体账号过滤）")
    void approved_marksOriginalScopedByAccountNo() {
        PerfAllocAdjustApply acct = buildApply();
        acct.setAllocDim("ACCOUNT");
        acct.setAccountNo("ACC_888");
        when(applyMapper.selectByBusinessKey("ALLOC_ADJUST:APP_001")).thenReturn(acct);

        listener.onProcessCompleted(
                new ProcessCompletedEvent("PI_APP_001", "ALLOC_ADJUST:APP_001", "APPROVED", null));

        verify(allocRelationMapper).markAllOriginalByCustId(
                eq("CUST_001"), eq("ACC_888"), eq(LocalDate.now()));
    }

    @Test
    @DisplayName("APPROVED → 插入分配时按工号回填 fullname/dept_no/dept_name（UserApi 解析）")
    void approved_fillsFullnameAndDeptFromUserApi() {
        listener.onProcessCompleted(
                new ProcessCompletedEvent("PI_APP_001", "ALLOC_ADJUST:APP_001", "APPROVED", null));

        ArgumentCaptor<CustAllocRelation> cap = ArgumentCaptor.forClass(CustAllocRelation.class);
        verify(allocRelationMapper, org.mockito.Mockito.times(2)).insert(cap.capture());
        CustAllocRelation a = cap.getAllValues().stream()
                .filter(r -> "EMP_A".equals(r.getEmpId())).findFirst().orElseThrow();
        assertThat(a.getFullname()).isEqualTo("张三");
        assertThat(a.getDeptNo()).isEqualTo("O1");
        assertThat(a.getDeptName()).isEqualTo("机构一");
        CustAllocRelation b = cap.getAllValues().stream()
                .filter(r -> "EMP_B".equals(r.getEmpId())).findFirst().orElseThrow();
        assertThat(b.getFullname()).isEqualTo("李四");
        assertThat(b.getDeptNo()).isEqualTo("O2");
        assertThat(b.getDeptName()).isEqualTo("机构二");
    }

    @Test
    @DisplayName("REJECTED 流程 → 仅更新 status=REJECTED，不改分配关系，不发事件，不标记原分配")
    void rejected_onlyUpdatesStatus_noAllocationChange_noEvent() {
        ProcessCompletedEvent event =
                new ProcessCompletedEvent(
                        "PI_APP_001", "ALLOC_ADJUST:APP_001", "REJECTED", "不符合规则");
        listener.onProcessCompleted(event);

        verify(applyMapper).updateStatus("APP_001", "REJECTED", null);
        verify(allocRelationMapper, never()).insert(any(CustAllocRelation.class));
        verify(allocRelationMapper, never()).markAllOriginalByCustId(any(), any(), any());
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
    @DisplayName("ITEM 全部明细落地为生效分配（item_kind 已废弃，原业绩分配不再存于 ITEM）")
    void allItemsLandAsAllocation() {
        // selectByApplyId 已在 setupCommon stub 为 EMP_A(60)/EMP_B(40) 两条调整明细
        listener.onProcessCompleted(
                new ProcessCompletedEvent("PI_APP_001", "ALLOC_ADJUST:APP_001", "APPROVED", null));

        ArgumentCaptor<CustAllocRelation> cap = ArgumentCaptor.forClass(CustAllocRelation.class);
        verify(allocRelationMapper, org.mockito.Mockito.times(2)).insert(cap.capture());
        assertThat(cap.getAllValues()).extracting(CustAllocRelation::getEmpId)
                .containsExactlyInAnyOrder("EMP_A", "EMP_B");
        ArgumentCaptor<AllocationAdjustmentApprovedEvent> evCap =
                ArgumentCaptor.forClass(AllocationAdjustmentApprovedEvent.class);
        verify(eventPublisher).publish(evCap.capture());
        assertThat(evCap.getValue().getItemCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("APPROVED 只把 NEW 明细落地，ORIGIN 仅作为申请快照保留")
    void approved_filtersOriginItems_beforePersistingRelations() {
        when(itemMapper.selectByApplyId("APP_001"))
                .thenReturn(Arrays.asList(
                        item("EMP_NEW", "100.00", "NEW"),
                        item("EMP_ORIGIN", "100.00", "ORIGIN")));

        listener.onProcessCompleted(
                new ProcessCompletedEvent("PI_APP_001", "ALLOC_ADJUST:APP_001", "APPROVED", null));

        ArgumentCaptor<CustAllocRelation> cap = ArgumentCaptor.forClass(CustAllocRelation.class);
        verify(allocRelationMapper).insert(cap.capture());
        assertThat(cap.getValue().getEmpId()).isEqualTo("EMP_NEW");
        verify(allocRelationMapper).markAllOriginalByCustId(
                eq("CUST_001"), isNull(), eq(LocalDate.now()));
        ArgumentCaptor<AllocationAdjustmentApprovedEvent> eventCap =
                ArgumentCaptor.forClass(AllocationAdjustmentApprovedEvent.class);
        verify(eventPublisher).publish(eventCap.capture());
        assertThat(eventCap.getValue().getItemCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("NEW 维度审批通过不标记旧当前分配，仅写入 NEW 明细")
    void approved_newDimension_skipsMarkingOldCurrentRelations() {
        PerfAllocAdjustApply apply = buildApply();
        apply.setAllocDim("NEW");
        when(applyMapper.selectByBusinessKey("ALLOC_ADJUST:APP_001")).thenReturn(apply);
        when(itemMapper.selectByApplyId("APP_001"))
                .thenReturn(List.of(item("EMP_NEW", "100.00", "NEW")));

        listener.onProcessCompleted(
                new ProcessCompletedEvent("PI_APP_001", "ALLOC_ADJUST:APP_001", "APPROVED", null));

        verify(allocRelationMapper).insert(any(CustAllocRelation.class));
        verify(allocRelationMapper, never()).markAllOriginalByCustId(any(), any(), any());
    }

    @Test
    @DisplayName("cust_id 即客户编号 → 分配关系 cust_id 直接取 apply.cust_id")
    void custId_usedDirectlyForRelation() {
        PerfAllocAdjustApply manual = buildApply();
        manual.setCustId("bbc");    // cust_id 即用户输入的客户编号（cust_no 字段已并入 cust_id）
        when(applyMapper.selectByBusinessKey("ALLOC_ADJUST:APP_001")).thenReturn(manual);

        ProcessCompletedEvent event =
                new ProcessCompletedEvent(
                        "PI_APP_001", "ALLOC_ADJUST:APP_001", "APPROVED", null);
        listener.onProcessCompleted(event);

        ArgumentCaptor<CustAllocRelation> relCap = ArgumentCaptor.forClass(CustAllocRelation.class);
        verify(allocRelationMapper, org.mockito.Mockito.times(2)).insert(relCap.capture());
        assertThat(relCap.getAllValues()).extracting(CustAllocRelation::getCustId)
                .containsOnly("bbc");
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
