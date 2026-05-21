package com.bank.branch.platform.performance.service.adjust;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.customer.api.CustomerQueryApi;
import com.bank.branch.platform.customer.api.dto.CustomerDTO;
import com.bank.branch.platform.performance.controller.dto.AllocAdjustRespDTO;
import com.bank.branch.platform.performance.entity.PerfAllocAdjustApply;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.mapper.CustAllocRelationMapper;
import com.bank.branch.platform.performance.mapper.PerfAllocAdjustApplyMapper;
import com.bank.branch.platform.performance.mapper.PerfAllocAdjustItemMapper;
import com.bank.branch.platform.performance.service.adjust.cmd.SubmitAllocAdjustCmd;
import com.bank.branch.platform.performance.service.scope.PerfScopeHelper;
import com.bank.branch.platform.workflow.api.WorkflowApi;
import com.bank.branch.platform.workflow.api.dto.StartProcessCmd;
import com.bank.branch.platform.workflow.api.dto.WorkflowLaunchResp;
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
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AllocAdjustService 单元测试 (V1.2 Q2.2).
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>正常提交 CORP_LOAN → 路由 corp_v1 BPMN</li>
 *   <li>客户不存在抛 VALIDATION_FAILED</li>
 *   <li>items 为空抛 VALIDATION_FAILED</li>
 *   <li>items 同一员工重复抛 VALIDATION_FAILED</li>
 *   <li>ratio 之和超过 100（RULE 维度）抛 VALIDATION_FAILED</li>
 *   <li>applyNo 自动生成、持久化 apply + items、回写 processInstanceId</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AllocAdjustServiceTest {

    @Mock
    private PerfAllocAdjustApplyMapper applyMapper;

    @Mock
    private PerfAllocAdjustItemMapper itemMapper;

    @Mock
    private CustAllocRelationMapper allocRelationMapper;

    @Mock
    private CustomerQueryApi customerQueryApi;

    @Mock
    private WorkflowApi workflowApi;

    @Mock
    private CurrentUserApi currentUserApi;

    @Mock
    private PerfScopeHelper perfScopeHelper;

    @InjectMocks
    private AllocAdjustService service;

    private SubmitAllocAdjustCmd baseCmd(String bizKind) {
        return SubmitAllocAdjustCmd.builder()
                .custNo("CN-001")
                .allocDim("RULE")
                .bizKind(bizKind)
                .ownerOrgId("ORG_001")
                .applicant("admin")
                .reason("年度岗位调整")
                .items(Arrays.asList(
                        SubmitAllocAdjustCmd.Item.builder()
                                .empId("EMP_A").ratio(new BigDecimal("60.00")).build(),
                        SubmitAllocAdjustCmd.Item.builder()
                                .empId("EMP_B").ratio(new BigDecimal("40.00")).build()))
                .build();
    }

    @BeforeEach
    void setUp() {
        // 默认按客户编号 CN-001 命中，内部主键 = CUST_001
        CustomerDTO cust = new CustomerDTO();
        cust.setId("CUST_001");
        cust.setCustNo("CN-001");
        when(customerQueryApi.getCustomerByCustNo("CN-001")).thenReturn(Optional.of(cust));
    }

    @Test
    @DisplayName("提交 CORP_LOAN → 启动 perf_alloc_adjust_corp_v1 BPMN")
    void submit_corpLoan_startsCorpWorkflow() {
        when(workflowApi.startProcess(any(StartProcessCmd.class)))
                .thenReturn(new WorkflowLaunchResp("PI_001", null, null));

        String id = service.submit(baseCmd("CORP_LOAN"));

        assertThat(id).isNotBlank();
        ArgumentCaptor<StartProcessCmd> cmdCaptor = ArgumentCaptor.forClass(StartProcessCmd.class);
        verify(workflowApi).startProcess(cmdCaptor.capture());
        assertThat(cmdCaptor.getValue().getProcessDefinitionKey())
                .isEqualTo("perf_alloc_adjust_corp_v1");
        assertThat(cmdCaptor.getValue().getBizType()).isEqualTo("ALLOC_ADJUST");

        // 回写 processInstanceId
        verify(applyMapper).updateStatus(id, "IN_APPROVAL", "PI_001");
    }

    @Test
    @DisplayName("提交 RETAIL_CARD → 启动 perf_alloc_adjust_retail_v1 BPMN")
    void submit_retailCard_startsRetailWorkflow() {
        when(workflowApi.startProcess(any(StartProcessCmd.class)))
                .thenReturn(new WorkflowLaunchResp("PI_002", null, null));

        String id = service.submit(baseCmd("RETAIL_CARD"));

        assertThat(id).isNotBlank();
        ArgumentCaptor<StartProcessCmd> cmdCaptor = ArgumentCaptor.forClass(StartProcessCmd.class);
        verify(workflowApi).startProcess(cmdCaptor.capture());
        assertThat(cmdCaptor.getValue().getProcessDefinitionKey())
                .isEqualTo("perf_alloc_adjust_retail_v1");
    }

    @Test
    @DisplayName("insert apply + batchInsert items 均被调用，status=IN_APPROVAL")
    void submit_persistsApplyAndItems() {
        when(workflowApi.startProcess(any(StartProcessCmd.class)))
                .thenReturn(new WorkflowLaunchResp("PI_003", null, null));

        service.submit(baseCmd("CORP_LOAN"));

        ArgumentCaptor<PerfAllocAdjustApply> applyCap = ArgumentCaptor.forClass(PerfAllocAdjustApply.class);
        verify(applyMapper).insert(applyCap.capture());
        PerfAllocAdjustApply apply = applyCap.getValue();
        assertThat(apply.getStatus()).isEqualTo("IN_APPROVAL");
        assertThat(apply.getApplyNo()).isNotBlank();
        // apply.cust_id 列保存的是按 custNo 查找到的内部主键 id
        assertThat(apply.getCustId()).isEqualTo("CUST_001");
        assertThat(apply.getAllocDim()).isEqualTo("RULE");
        assertThat(apply.getBizKind()).isEqualTo("CORP_LOAN");
        assertThat(apply.getOwnerOrgId()).isEqualTo("ORG_001");
        assertThat(apply.getCreatedBy()).isEqualTo("admin");
        assertThat(apply.getBusinessKey()).contains(apply.getId());

        verify(itemMapper).batchInsert(anyList());
    }

    @Test
    @DisplayName("客户编号不存在 → 抛 VALIDATION_FAILED，不发起流程")
    void submit_customerNotFound_throws() {
        when(customerQueryApi.getCustomerByCustNo("CN-001")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submit(baseCmd("CORP_LOAN")))
                .isInstanceOf(PerfException.class)
                .extracting(e -> ((PerfException) e).getErrorCode())
                .isEqualTo(PerfErrorCode.VALIDATION_FAILED);

        verify(workflowApi, never()).startProcess(any());
        verify(applyMapper, never()).insert(any(PerfAllocAdjustApply.class));
    }

    @Test
    @DisplayName("items 为空 → 抛 VALIDATION_FAILED")
    void submit_emptyItems_throws() {
        SubmitAllocAdjustCmd cmd = baseCmd("CORP_LOAN");
        cmd.setItems(Collections.emptyList());

        assertThatThrownBy(() -> service.submit(cmd))
                .isInstanceOf(PerfException.class)
                .extracting(e -> ((PerfException) e).getErrorCode())
                .isEqualTo(PerfErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("items 存在同员工重复 → 抛 VALIDATION_FAILED")
    void submit_duplicateEmpId_throws() {
        SubmitAllocAdjustCmd cmd = baseCmd("CORP_LOAN");
        cmd.setItems(Arrays.asList(
                SubmitAllocAdjustCmd.Item.builder().empId("EMP_A").ratio(new BigDecimal("60")).build(),
                SubmitAllocAdjustCmd.Item.builder().empId("EMP_A").ratio(new BigDecimal("40")).build()));

        assertThatThrownBy(() -> service.submit(cmd))
                .isInstanceOf(PerfException.class)
                .extracting(e -> ((PerfException) e).getErrorCode())
                .isEqualTo(PerfErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("RULE 维度 ratio 之和 > 100 → 抛 VALIDATION_FAILED")
    void submit_ratioSumExceeds_throws() {
        SubmitAllocAdjustCmd cmd = baseCmd("CORP_LOAN");
        cmd.setItems(Arrays.asList(
                SubmitAllocAdjustCmd.Item.builder().empId("EMP_A").ratio(new BigDecimal("70")).build(),
                SubmitAllocAdjustCmd.Item.builder().empId("EMP_B").ratio(new BigDecimal("50")).build()));

        assertThatThrownBy(() -> service.submit(cmd))
                .isInstanceOf(PerfException.class)
                .extracting(e -> ((PerfException) e).getErrorCode())
                .isEqualTo(PerfErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("必填字段缺失（custNo 为空）→ 抛 VALIDATION_FAILED")
    void submit_blankCustNo_throws() {
        SubmitAllocAdjustCmd cmd = baseCmd("CORP_LOAN");
        cmd.setCustNo(null);

        assertThatThrownBy(() -> service.submit(cmd))
                .isInstanceOf(PerfException.class)
                .extracting(e -> ((PerfException) e).getErrorCode())
                .isEqualTo(PerfErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("allocDim 非法值 → 抛 VALIDATION_FAILED")
    void submit_invalidAllocDim_throws() {
        SubmitAllocAdjustCmd cmd = baseCmd("CORP_LOAN");
        cmd.setAllocDim("INVALID_DIM");

        assertThatThrownBy(() -> service.submit(cmd))
                .isInstanceOf(PerfException.class)
                .extracting(e -> ((PerfException) e).getErrorCode())
                .isEqualTo(PerfErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("未识别 biz_kind 前缀 → 抛 VALIDATION_FAILED (BIZ_KIND_INVALID)")
    void submit_unknownBizKindPrefix_throws() {
        SubmitAllocAdjustCmd cmd = baseCmd("UNKNOWN_KIND");

        assertThatThrownBy(() -> service.submit(cmd))
                .isInstanceOf(PerfException.class);
    }

    // ========== 响应 DTO 回填 custNo（基于 cust_master 反查） ==========

    @Test
    @DisplayName("getByIdDto → 用 apply.custId(内部主键) 反查客户，回填 custNo 到响应")
    void getByIdDto_populatesCustNoFromCustomerLookup() {
        PerfAllocAdjustApply apply = new PerfAllocAdjustApply();
        apply.setId("APPLY_X");
        apply.setApplyNo("AA-X");
        apply.setCustId("CUST_X");
        apply.setStatus("IN_APPROVAL");
        when(applyMapper.selectByAllocApplyId("APPLY_X")).thenReturn(apply);
        when(itemMapper.selectByApplyId("APPLY_X")).thenReturn(Collections.emptyList());
        CustomerDTO cust = new CustomerDTO();
        cust.setId("CUST_X");
        cust.setCustNo("CN-X");
        when(customerQueryApi.getCustomer("CUST_X")).thenReturn(Optional.of(cust));

        AllocAdjustRespDTO dto = service.getByIdDto("APPLY_X");

        assertThat(dto.getCustId()).isEqualTo("CUST_X");
        assertThat(dto.getCustNo()).isEqualTo("CN-X");
    }

    @Test
    @DisplayName("getByIdDto → 客户已删除时 custNo 留 null，custId 仍回显")
    void getByIdDto_customerMissing_leavesCustNoNull() {
        PerfAllocAdjustApply apply = new PerfAllocAdjustApply();
        apply.setId("APPLY_Y");
        apply.setApplyNo("AA-Y");
        apply.setCustId("CUST_GONE");
        when(applyMapper.selectByAllocApplyId("APPLY_Y")).thenReturn(apply);
        when(itemMapper.selectByApplyId("APPLY_Y")).thenReturn(Collections.emptyList());
        when(customerQueryApi.getCustomer("CUST_GONE")).thenReturn(Optional.empty());

        AllocAdjustRespDTO dto = service.getByIdDto("APPLY_Y");

        assertThat(dto.getCustId()).isEqualTo("CUST_GONE");
        assertThat(dto.getCustNo()).isNull();
    }

    @Test
    @DisplayName("pageDto → 批量反查 cust_master，按内部主键 → custNo 映射回填到每行")
    void pageDto_populatesCustNoFromCustomerBatchLookup() {
        when(currentUserApi.getCurrentEmpId()).thenReturn("USER_PAGE");
        when(perfScopeHelper.getFragment(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(PerfScopeHelper.ScopeColumns.class),
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new PerfScopeHelper.Fragment("1=1", java.util.Map.of()));

        PerfAllocAdjustApply r1 = new PerfAllocAdjustApply();
        r1.setId("A1"); r1.setCustId("C1");
        PerfAllocAdjustApply r2 = new PerfAllocAdjustApply();
        r2.setId("A2"); r2.setCustId("C2");
        when(applyMapper.selectByConditionsWithScope(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(Arrays.asList(r1, r2));
        when(applyMapper.countByConditionsWithScope(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(2L);

        CustomerDTO c1 = new CustomerDTO(); c1.setId("C1"); c1.setCustNo("CN-1");
        CustomerDTO c2 = new CustomerDTO(); c2.setId("C2"); c2.setCustNo("CN-2");
        when(customerQueryApi.listCustomers(org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(Arrays.asList(c1, c2));

        PageResult<AllocAdjustRespDTO> page = service.pageDto(null, null, null, null, null, 1, 20);

        assertThat(page.getRecords()).hasSize(2);
        assertThat(page.getRecords().get(0).getCustId()).isEqualTo("C1");
        assertThat(page.getRecords().get(0).getCustNo()).isEqualTo("CN-1");
        assertThat(page.getRecords().get(1).getCustId()).isEqualTo("C2");
        assertThat(page.getRecords().get(1).getCustNo()).isEqualTo("CN-2");
    }

    // ========== V1.4 S1.3: WORKFLOW_PARTICIPANT scope 路径 ==========

    @Test
    @DisplayName("V1.4 S1.3: pageDto → 调 PerfScopeHelper 5 参 overload，bizKeyCol=\"business_key\"，prefix=\"perf_alloc_adjust_\"")
    void pageDto_callsPerfScopeHelperWithBusinessKeyColAndAllocPrefix() {
        when(currentUserApi.getCurrentEmpId()).thenReturn("USER_WF");
        // Mock scope helper 返回 business_key IN (...) 片段
        PerfScopeHelper.Fragment frag = new PerfScopeHelper.Fragment(
                "business_key IN (#{scopeParams.bizKey0})",
                java.util.Map.of("bizKey0", "ALLOC_ADJUST:APPLY_001"));
        when(perfScopeHelper.getFragment(
                org.mockito.ArgumentMatchers.eq("USER_WF"),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.argThat(cols ->
                        cols != null && "business_key".equals(cols.bizKeyCol())),
                org.mockito.ArgumentMatchers.eq("perf_alloc_adjust_")))
                .thenReturn(frag);
        // Mock mapper 返回空列表（不关心结果）
        when(applyMapper.selectByConditionsWithScope(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.Collections.emptyList());
        when(applyMapper.countByConditionsWithScope(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(0L);

        service.pageDto(null, null, null, null, null, 1, 20);

        // 验证 perfScopeHelper 被调用时 bizKeyCol="business_key" + prefix="perf_alloc_adjust_"
        verify(perfScopeHelper).getFragment(
                org.mockito.ArgumentMatchers.eq("USER_WF"),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.argThat(cols ->
                        cols != null && "business_key".equals(cols.bizKeyCol())),
                org.mockito.ArgumentMatchers.eq("perf_alloc_adjust_"));
    }

    @Test
    @DisplayName("V1.4 S1.3: WORKFLOW_PARTICIPANT 无参与记录（fail-close）→ 分页结果空")
    void pageDto_workflowParticipantFailClose_returnsEmptyPage() {
        when(currentUserApi.getCurrentEmpId()).thenReturn("USER_NOWF");
        when(perfScopeHelper.getFragment(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(PerfScopeHelper.ScopeColumns.class),
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(PerfScopeHelper.Fragment.failClose());
        when(applyMapper.selectByConditionsWithScope(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.eq("1=0"),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.Collections.emptyList());
        when(applyMapper.countByConditionsWithScope(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("1=0"),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(0L);

        PageResult<AllocAdjustRespDTO> result = service.pageDto(null, null, null, null, null, 1, 20);

        assertThat(result.getTotal()).isEqualTo(0);
        assertThat(result.getRecords()).isEmpty();
    }
}
