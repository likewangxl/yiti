package com.bank.branch.platform.performance.service.adjust;

import com.bank.branch.platform.customer.api.CustomerQueryApi;
import com.bank.branch.platform.customer.api.dto.CustomerDTO;
import com.bank.branch.platform.performance.entity.PerfAllocAdjustApply;
import com.bank.branch.platform.performance.mapper.PerfAllocAdjustApplyMapper;
import com.bank.branch.platform.performance.mapper.PerfAllocAdjustItemMapper;
import com.bank.branch.platform.performance.service.adjust.cmd.SubmitAllocAdjustCmd;
import com.bank.branch.platform.workflow.api.WorkflowApi;
import com.bank.branch.platform.workflow.api.dto.StartProcessCmd;
import com.bank.branch.platform.workflow.api.dto.WorkflowLaunchResp;
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
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 零售分配调整路由深度验证 (V1.2 Q2.5).
 *
 * <p>Q2.2 已覆盖 CORP_LOAN / RETAIL_CARD 两种路由的基础流程；本测试补充覆盖：
 * <ul>
 *   <li>RETAIL_LOAN / RETAIL_MORTGAGE 等其他零售 bizKind → retail_v1 BPMN</li>
 *   <li>ACCOUNT 维度调整（账号级别，accountNo 非空）</li>
 *   <li>StartProcessCmd 流程变量完整性：applyId / custId / bizKind / allocDim</li>
 *   <li>对公 CORP_DEPOSIT 等其他对公 bizKind → corp_v1 BPMN</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AllocAdjustRetailRoutingTest {

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

    private SubmitAllocAdjustCmd cmd(String bizKind, String allocDim, String accountNo) {
        return SubmitAllocAdjustCmd.builder()
                .custId("CUST_RET_001")
                .allocDim(allocDim)
                .bizKind(bizKind)
                .accountNo(accountNo)
                .ownerOrgId("ORG_RET")
                .applicant("ret_user")
                .reason("零售分配调整")
                .items(Collections.singletonList(
                        SubmitAllocAdjustCmd.Item.builder()
                                .empId("EMP_RET_A")
                                .ratio(new BigDecimal("100.00"))
                                .build()))
                .build();
    }

    private void stubValidCustomer() {
        CustomerDTO c = new CustomerDTO();
        c.setId("CUST_RET_001");
        when(customerQueryApi.getCustomer(anyString())).thenReturn(Optional.of(c));
        when(workflowApi.startProcess(any(StartProcessCmd.class)))
                .thenReturn(new WorkflowLaunchResp("PI_RET_AUTO", null, null));
    }

    @Test
    @DisplayName("RETAIL_LOAN → retail_v1")
    void retailLoan_routesToRetail() {
        stubValidCustomer();
        service.submit(cmd("RETAIL_LOAN", "RULE", null));

        ArgumentCaptor<StartProcessCmd> cap = ArgumentCaptor.forClass(StartProcessCmd.class);
        verify(workflowApi).startProcess(cap.capture());
        assertThat(cap.getValue().getProcessDefinitionKey())
                .isEqualTo(AllocAdjustService.PROCESS_KEY_RETAIL);
    }

    @Test
    @DisplayName("RETAIL_MORTGAGE → retail_v1")
    void retailMortgage_routesToRetail() {
        stubValidCustomer();
        service.submit(cmd("RETAIL_MORTGAGE", "RULE", null));

        ArgumentCaptor<StartProcessCmd> cap = ArgumentCaptor.forClass(StartProcessCmd.class);
        verify(workflowApi).startProcess(cap.capture());
        assertThat(cap.getValue().getProcessDefinitionKey())
                .isEqualTo(AllocAdjustService.PROCESS_KEY_RETAIL);
    }

    @Test
    @DisplayName("CORP_DEPOSIT → corp_v1")
    void corpDeposit_routesToCorp() {
        stubValidCustomer();
        service.submit(cmd("CORP_DEPOSIT", "RULE", null));

        ArgumentCaptor<StartProcessCmd> cap = ArgumentCaptor.forClass(StartProcessCmd.class);
        verify(workflowApi).startProcess(cap.capture());
        assertThat(cap.getValue().getProcessDefinitionKey())
                .isEqualTo(AllocAdjustService.PROCESS_KEY_CORP);
    }

    @Test
    @DisplayName("ACCOUNT 维度 + accountNo 透传到 apply 主表")
    void accountDim_persistsAccountNo() {
        stubValidCustomer();
        service.submit(cmd("RETAIL_CARD", "ACCOUNT", "6222-0001-2345"));

        ArgumentCaptor<PerfAllocAdjustApply> cap = ArgumentCaptor.forClass(PerfAllocAdjustApply.class);
        verify(applyMapper).insert(cap.capture());
        assertThat(cap.getValue().getAllocDim()).isEqualTo("ACCOUNT");
        assertThat(cap.getValue().getAccountNo()).isEqualTo("6222-0001-2345");
    }

    @Test
    @DisplayName("StartProcessCmd 流程变量包含 applyId/custId/bizKind/allocDim")
    void startProcessCmd_carriesAllRequiredVariables() {
        stubValidCustomer();
        String applyId = service.submit(cmd("RETAIL_CARD", "RULE", null));

        ArgumentCaptor<StartProcessCmd> cap = ArgumentCaptor.forClass(StartProcessCmd.class);
        verify(workflowApi).startProcess(cap.capture());
        StartProcessCmd started = cap.getValue();
        Map<String, Object> vars = started.getVariables();
        assertThat(vars).isNotNull();
        assertThat(vars).containsEntry("applyId", applyId);
        assertThat(vars).containsEntry("custId", "CUST_RET_001");
        assertThat(vars).containsEntry("bizKind", "RETAIL_CARD");
        assertThat(vars).containsEntry("allocDim", "RULE");

        // bizType / startUser / startOrgId / title 均应被填充
        assertThat(started.getBizType()).isEqualTo(AllocAdjustService.BIZ_TYPE);
        assertThat(started.getStartUser()).isEqualTo("ret_user");
        assertThat(started.getStartOrgId()).isEqualTo("ORG_RET");
        assertThat(started.getTitle()).contains("CUST_RET_001");
    }
}
