package com.bank.branch.platform.performance.service.adjust;

import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.dto.OrgDTO;
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
    private OrgApi orgApi;

    @Mock
    private WorkflowApi workflowApi;

    @Mock
    private com.bank.branch.platform.performance.mapper.CustAllocRelationMapper allocRelationMapper;

    @Mock
    private com.bank.branch.platform.auth.api.UserApi userApi;

    @Mock
    private AllocAdjustPreviewService allocAdjustPreviewService;

    @InjectMocks
    private AllocAdjustService service;

    private SubmitAllocAdjustCmd cmd(String bizKind, String allocDim, String accountNo) {
        return SubmitAllocAdjustCmd.builder()
                .custId("CN-RET-001")
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
        c.setCustNo("CN-RET-001");
        when(customerQueryApi.getCustomerByCustNo(anyString())).thenReturn(Optional.of(c));
        // 发起机构级别（设计器网关分流依据 + submit 前置校验「仅限 2/3 级机构发起」）：种 2 级
        OrgDTO org = new OrgDTO();
        org.setOrgLevel(2);
        org.setOrgCode("ORG_L2");
        when(orgApi.getOrg(anyString())).thenReturn(org);
        // 原业绩所属机构负责人解析（startApprovalWorkflow 内 fail-fast 前置校验）：
        // 原分配人主机构=2级（就地），该机构 BRANCH_HEAD 持有者非空
        when(orgApi.getUserMainOrg(anyString())).thenReturn(org);
        when(userApi.getEmpIdsByRoleCodeAndOrg(anyString(), anyString()))
                .thenReturn(java.util.List.of("ORG_LEADER_1"));
        when(workflowApi.startProcess(any(StartProcessCmd.class)))
                .thenReturn(new WorkflowLaunchResp("PI_RET_AUTO", null, null));
        // 审批流已改走设计器动态流程：submit 经 resolveDesignerProcDefKey(flowKey) 取已部署 procDefKey。
        // 单测把「对公/零售设计器 flowKey」解析回原静态流程 KEY，路由断言语义保持不变。
        when(workflowApi.resolveDesignerProcDefKey("alloc_corp_designer"))
                .thenReturn(AllocAdjustService.PROCESS_KEY_CORP);
        when(workflowApi.resolveDesignerProcDefKey("alloc_retail_designer"))
                .thenReturn(AllocAdjustService.PROCESS_KEY_RETAIL);
        // 原业绩分配（历史审批通过）非空，使提交校验「至少 1 条原业绩分配」通过
        var owner = new com.bank.branch.platform.performance.api.dto.AllocAdjustPreviewItemDTO();
        owner.setEmpId("EMP_RET_A");
        when(allocAdjustPreviewService.getLastApprovedAllocPreview(anyString(), anyString()))
                .thenReturn(java.util.List.of(owner));
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
    @DisplayName("StartProcessCmd 流程变量包含 applyId/custId(内部主键)/custNo/bizKind/allocDim")
    void startProcessCmd_carriesAllRequiredVariables() {
        stubValidCustomer();
        String applyId = service.submit(cmd("RETAIL_CARD", "RULE", null));

        ArgumentCaptor<StartProcessCmd> cap = ArgumentCaptor.forClass(StartProcessCmd.class);
        verify(workflowApi).startProcess(cap.capture());
        StartProcessCmd started = cap.getValue();
        Map<String, Object> vars = started.getVariables();
        assertThat(vars).isNotNull();
        assertThat(vars).containsEntry("applyId", applyId);
        // custId/custNo 流程变量均写客户编号（cust_no 字段已并入 cust_id）
        assertThat(vars).containsEntry("custId", "CN-RET-001");
        assertThat(vars).containsEntry("custNo", "CN-RET-001");
        assertThat(vars).containsEntry("bizKind", "RETAIL_CARD");
        assertThat(vars).containsEntry("allocDim", "RULE");

        // bizType / startUser / startOrgId / title 均应被填充；标题用 custNo 便于人工识别
        assertThat(started.getBizType()).isEqualTo(AllocAdjustService.BIZ_TYPE);
        assertThat(started.getStartUser()).isEqualTo("ret_user");
        assertThat(started.getStartOrgId()).isEqualTo("ORG_RET");
        assertThat(started.getTitle()).contains("CN-RET-001");
    }
}
