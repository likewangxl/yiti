package com.bank.branch.platform.performance.service.adjust;

import com.bank.branch.platform.customer.api.CustomerQueryApi;
import com.bank.branch.platform.customer.api.dto.CustomerDTO;
import com.bank.branch.platform.performance.entity.PerfAllocAdjustApply;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.mapper.PerfAllocAdjustApplyMapper;
import com.bank.branch.platform.performance.mapper.PerfAllocAdjustItemMapper;
import com.bank.branch.platform.performance.service.adjust.cmd.SubmitAllocAdjustCmd;
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
    private CustomerQueryApi customerQueryApi;

    @Mock
    private WorkflowApi workflowApi;

    @InjectMocks
    private AllocAdjustService service;

    private SubmitAllocAdjustCmd baseCmd(String bizKind) {
        return SubmitAllocAdjustCmd.builder()
                .custId("CUST_001")
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
        // 默认客户存在
        CustomerDTO cust = new CustomerDTO();
        cust.setId("CUST_001");
        when(customerQueryApi.getCustomer("CUST_001")).thenReturn(Optional.of(cust));
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
        assertThat(apply.getCustId()).isEqualTo("CUST_001");
        assertThat(apply.getAllocDim()).isEqualTo("RULE");
        assertThat(apply.getBizKind()).isEqualTo("CORP_LOAN");
        assertThat(apply.getOwnerOrgId()).isEqualTo("ORG_001");
        assertThat(apply.getCreatedBy()).isEqualTo("admin");
        assertThat(apply.getBusinessKey()).contains(apply.getId());

        verify(itemMapper).batchInsert(anyList());
    }

    @Test
    @DisplayName("客户不存在 → 抛 VALIDATION_FAILED，不发起流程")
    void submit_customerNotFound_throws() {
        when(customerQueryApi.getCustomer("CUST_001")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submit(baseCmd("CORP_LOAN")))
                .isInstanceOf(PerfException.class)
                .extracting(e -> ((PerfException) e).getErrorCode())
                .isEqualTo(PerfErrorCode.VALIDATION_FAILED);

        verify(workflowApi, never()).startProcess(any());
        verify(applyMapper, never()).insert(any());
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
    @DisplayName("必填字段缺失（custId 为空）→ 抛 VALIDATION_FAILED")
    void submit_blankCustId_throws() {
        SubmitAllocAdjustCmd cmd = baseCmd("CORP_LOAN");
        cmd.setCustId(null);

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
}
