package com.bank.branch.platform.bizapp.service;

import com.bank.branch.platform.bizapp.dto.resp.LoanDetailResp;
import com.bank.branch.platform.bizapp.dto.resp.SubmitRespDTO;
import com.bank.branch.platform.bizapp.entity.LoanApply;
import com.bank.branch.platform.bizapp.event.LoanSubmittedEvent;
import com.bank.branch.platform.bizapp.enums.LoanStatus;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.bizapp.mapper.LoanApplyMapper;
import com.bank.branch.platform.customer.api.CustomerQueryApi;
import com.bank.branch.platform.customer.api.TouchTaskQueryApi;
import com.bank.branch.platform.customer.api.dto.CustomerDTO;
import com.bank.branch.platform.customer.api.dto.TouchTaskDTO;
import com.bank.branch.platform.workflow.api.WorkflowApi;
import com.bank.branch.platform.workflow.api.WorkflowQueryApi;
import com.bank.branch.platform.workflow.api.dto.ProcessDiagramDTO;
import com.bank.branch.platform.workflow.api.dto.ProcessDiagramNodeDTO;
import com.bank.branch.platform.workflow.api.dto.StartProcessCmd;
import com.bank.branch.platform.workflow.api.dto.WorkflowLaunchResp;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * LoanService 单元测试（TDD）。
 * 共20个测试用例。
 */
@ExtendWith(MockitoExtension.class)
class LoanServiceTest {

    @Mock
    private LoanApplyMapper loanMapper;

    @Mock
    private BizStateMachine bizStateMachine;

    @Mock
    private BizNoGenerator bizNoGenerator;

    @Mock
    private WorkflowApi workflowApi;

    @Mock
    private WorkflowQueryApi workflowQueryApi;

    @Mock
    private CustomerQueryApi customerQueryApi;

    @Mock
    private TouchTaskQueryApi touchTaskQueryApi;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private LoanService loanService;

    // ==================== createDraft ====================

    @Test
    void createDraft_validInput_shouldInsertAndReturn() {
        // given
        when(customerQueryApi.isValidCustomer("CUST001")).thenReturn(true);
        when(customerQueryApi.getCustomer("CUST001")).thenReturn(java.util.Optional.of(companyCustomer("CUST001")));
        when(customerQueryApi.isClaimedByOrg("CUST001", "ORG001")).thenReturn(true);
        when(bizNoGenerator.generateLoanNo()).thenReturn("LA20260414000001");
        when(loanMapper.insert(any(LoanApply.class))).thenReturn(1);

        // when
        LoanApply result = loanService.createDraft(
                "CUST001", null,
                "CORP", "WORKING_CAPITAL", "GUARANTEE",
                new BigDecimal("100.0000"), new BigDecimal("80.0000"),
                "E001", "ORG001"
        );

        // then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isNotNull().hasSize(32);
        assertThat(result.getApplyNo()).isEqualTo("LA20260414000001");
        assertThat(result.getStatus()).isEqualTo(LoanStatus.DRAFT.getCode());
        assertThat(result.getDeleted()).isEqualTo(0);
        assertThat(result.getCustId()).isEqualTo("CUST001");
        verify(loanMapper).insert(any(LoanApply.class));
    }

    @Test
    void createDraft_invalidCustomer_shouldThrowBIZ40301() {
        // given
        when(customerQueryApi.isValidCustomer("INVALID")).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> loanService.createDraft(
                "INVALID", null, null, null, null, null, null, "E001", "ORG001"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "BIZ-40301");

        verify(loanMapper, never()).insert(any(LoanApply.class));
    }

    @Test
    void createDraft_customerNotClaimedByOrg_shouldThrowBIZ40303() {
        // given
        when(customerQueryApi.isValidCustomer("CUST001")).thenReturn(true);
        when(customerQueryApi.getCustomer("CUST001")).thenReturn(java.util.Optional.of(companyCustomer("CUST001")));
        when(customerQueryApi.isClaimedByOrg("CUST001", "ORG001")).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> loanService.createDraft(
                "CUST001", null, null, null, null, null, null, "E001", "ORG001"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "BIZ-40303");

        verify(loanMapper, never()).insert(any(LoanApply.class));
    }

    @Test
    void createDraft_wrongTouchTaskAssignee_shouldThrowBIZ40302() {
        // given
        when(customerQueryApi.isValidCustomer("CUST001")).thenReturn(true);
        when(customerQueryApi.getCustomer("CUST001")).thenReturn(java.util.Optional.of(companyCustomer("CUST001")));
        when(customerQueryApi.isClaimedByOrg("CUST001", "ORG001")).thenReturn(true);
        TouchTaskDTO task = new TouchTaskDTO();
        task.setCustId("CUST001");
        task.setAssigneeEmpId("OTHER_EMP"); // 不是操作人
        when(touchTaskQueryApi.getTouchTask("TASK001")).thenReturn(java.util.Optional.of(task));

        // when & then
        assertThatThrownBy(() -> loanService.createDraft(
                "CUST001", "TASK001", null, null, null, null, null, "E001", "ORG001"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "BIZ-40302");

        verify(loanMapper, never()).insert(any(LoanApply.class));
    }

    @Test
    void createDraft_retailCustomer_shouldThrowBIZ40301() {
        when(customerQueryApi.isValidCustomer("CUST001")).thenReturn(true);
        CustomerDTO retail = new CustomerDTO();
        retail.setId("CUST001");
        retail.setCustomerType("RETAIL");
        when(customerQueryApi.getCustomer("CUST001")).thenReturn(java.util.Optional.of(retail));

        assertThatThrownBy(() -> loanService.createDraft(
                "CUST001", null, null, null, null, null, null, "E001", "ORG001"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "BIZ-40301");

        verify(loanMapper, never()).insert(any(LoanApply.class));
    }

    @Test
    void createDraft_touchTaskCustomerMismatch_shouldThrowBIZ40306() {
        when(customerQueryApi.isValidCustomer("CUST001")).thenReturn(true);
        when(customerQueryApi.getCustomer("CUST001")).thenReturn(java.util.Optional.of(companyCustomer("CUST001")));
        when(customerQueryApi.isClaimedByOrg("CUST001", "ORG001")).thenReturn(true);
        TouchTaskDTO task = new TouchTaskDTO();
        task.setCustId("OTHER_CUST");
        task.setAssigneeEmpId("E001");
        when(touchTaskQueryApi.getTouchTask("TASK001")).thenReturn(java.util.Optional.of(task));

        assertThatThrownBy(() -> loanService.createDraft(
                "CUST001", "TASK001", null, null, null, null, null, "E001", "ORG001"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "BIZ-40306");

        verify(loanMapper, never()).insert(any(LoanApply.class));
    }

    @Test
    void createDraft_exposureExceedsCredit_shouldThrowBIZ40905() {
        // given
        when(customerQueryApi.isValidCustomer("CUST001")).thenReturn(true);
        when(customerQueryApi.getCustomer("CUST001")).thenReturn(java.util.Optional.of(companyCustomer("CUST001")));
        when(customerQueryApi.isClaimedByOrg("CUST001", "ORG001")).thenReturn(true);

        // when & then
        assertThatThrownBy(() -> loanService.createDraft(
                "CUST001", null, null, null, null,
                new BigDecimal("50.0000"), new BigDecimal("100.0000"),
                "E001", "ORG001"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "BIZ-40905");

        verify(loanMapper, never()).insert(any(LoanApply.class));
    }

    private static CustomerDTO companyCustomer(String id) {
        CustomerDTO customer = new CustomerDTO();
        customer.setId(id);
        customer.setCustomerType("CORP");
        return customer;
    }

    // ==================== updateDraft ====================

    @Test
    void updateDraft_validDraft_shouldUpdate() {
        // given
        LoanApply existing = buildDraftLoan("L001", "E001");
        when(loanMapper.selectById("L001")).thenReturn(existing);
        when(loanMapper.updateById(any(LoanApply.class))).thenReturn(1);

        // when
        LoanApply result = loanService.updateDraft(
                "L001", "CORP", "WORKING_CAPITAL", "GUARANTEE",
                new BigDecimal("100.0000"), new BigDecimal("80.0000"), "E001"
        );

        // then
        assertThat(result).isNotNull();
        verify(loanMapper).updateById(any(LoanApply.class));
    }

    @Test
    void updateDraft_notFound_shouldThrowBIZ40401() {
        // given
        when(loanMapper.selectById("NOTEXIST")).thenReturn(null);

        // when & then
        assertThatThrownBy(() -> loanService.updateDraft(
                "NOTEXIST", null, null, null, null, null, "E001"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "BIZ-40401");
    }

    @Test
    void updateDraft_notDraftStatus_shouldThrowBIZ42303() {
        // given
        LoanApply existing = buildDraftLoan("L001", "E001");
        existing.setStatus(LoanStatus.IN_APPROVAL.getCode()); // 非草稿
        when(loanMapper.selectById("L001")).thenReturn(existing);

        // when & then
        assertThatThrownBy(() -> loanService.updateDraft(
                "L001", null, null, null, null, null, "E001"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "BIZ-42303");
    }

    @Test
    void updateDraft_notCreator_shouldThrowBIZ40305() {
        // given
        LoanApply existing = buildDraftLoan("L001", "E001");
        when(loanMapper.selectById("L001")).thenReturn(existing);

        // when & then
        assertThatThrownBy(() -> loanService.updateDraft(
                "L001", null, null, null, null, null, "OTHER_EMP"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "BIZ-40305");
    }

    // ==================== deleteDraft ====================

    @Test
    void deleteDraft_validDraft_shouldSoftDelete() {
        // given
        LoanApply existing = buildDraftLoan("L001", "E001");
        when(loanMapper.selectById("L001")).thenReturn(existing);
        when(loanMapper.updateById(any(LoanApply.class))).thenReturn(1);

        // when
        loanService.deleteDraft("L001", "E001");

        // then
        ArgumentCaptor<LoanApply> captor = ArgumentCaptor.forClass(LoanApply.class);
        verify(loanMapper).updateById(captor.capture());
        assertThat(captor.getValue().getDeleted()).isEqualTo(1);
    }

    @Test
    void deleteDraft_notDraftStatus_shouldThrowBIZ42303() {
        // given
        LoanApply existing = buildDraftLoan("L001", "E001");
        existing.setStatus(LoanStatus.IN_APPROVAL.getCode());
        when(loanMapper.selectById("L001")).thenReturn(existing);

        // when & then
        assertThatThrownBy(() -> loanService.deleteDraft("L001", "E001"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "BIZ-42303");
    }

    // ==================== submitForApproval ====================

    @Test
    void submitForApproval_validDraft_shouldStartWorkflowAndReturnDTO() {
        // given
        LoanApply existing = buildDraftLoan("L001", "E001");
        when(loanMapper.selectForUpdate("L001")).thenReturn(existing);
        WorkflowLaunchResp resp = new WorkflowLaunchResp("PI001", "LOAN:L001", null);
        when(workflowApi.startProcess(any(StartProcessCmd.class))).thenReturn(resp);
        when(loanMapper.updateById(any(LoanApply.class))).thenReturn(1);

        // when
        SubmitRespDTO result = loanService.submitForApproval("L001", "E001", "ORG001");

        // then
        verify(workflowApi).startProcess(any(StartProcessCmd.class));
        ArgumentCaptor<LoanApply> captor = ArgumentCaptor.forClass(LoanApply.class);
        verify(loanMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(LoanStatus.IN_APPROVAL.getCode());
        verify(eventPublisher).publishEvent(any(LoanSubmittedEvent.class));
        // 验证返回值含 processInstanceId
        assertThat(result).isNotNull();
        assertThat(result.getProcessInstanceId()).isEqualTo("PI001");
        assertThat(result.getBusinessKey()).isEqualTo("LOAN:L001");
        assertThat(result.getStatus()).isEqualTo(LoanStatus.IN_APPROVAL.getCode());
    }

    @Test
    void submitForApproval_workflowError_shouldThrowBIZ50001() {
        // given
        LoanApply existing = buildDraftLoan("L001", "E001");
        when(loanMapper.selectForUpdate("L001")).thenReturn(existing);
        when(workflowApi.startProcess(any(StartProcessCmd.class)))
                .thenThrow(new RuntimeException("workflow error"));

        // when & then
        assertThatThrownBy(() -> loanService.submitForApproval("L001", "E001", "ORG001"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "BIZ-50001");
    }

    // ==================== cancelApply ====================

    @Test
    void cancelApply_branchApproveActive_shouldCancelWorkflowBeforeBusinessStatus() {
        // given
        LoanApply existing = buildDraftLoan("L001", "E001");
        existing.setStatus(LoanStatus.IN_APPROVAL.getCode());
        existing.setProcessInstanceId("PI001");
        when(loanMapper.selectForUpdate("L001")).thenReturn(existing);
        when(workflowQueryApi.getProcessNodes("PI001"))
                .thenReturn(processDiagram("PI001", "branch_approve", "ACTIVE"));
        when(loanMapper.conditionalUpdateStatus("L001", LoanStatus.IN_APPROVAL.getCode(),
                LoanStatus.CANCELLED.getCode(), "E001")).thenReturn(1);

        // when
        loanService.cancelApply("L001", "客户主动放弃", "E001");

        // then
        verify(bizStateMachine).validateLoanTransition(LoanStatus.IN_APPROVAL.getCode(), LoanStatus.CANCELLED.getCode());
        verify(workflowApi).cancelProcess("PI001", "客户主动放弃");
        verify(loanMapper).conditionalUpdateStatus("L001", LoanStatus.IN_APPROVAL.getCode(),
                LoanStatus.CANCELLED.getCode(), "E001");

        var order = inOrder(workflowApi, loanMapper);
        order.verify(workflowApi).cancelProcess("PI001", "客户主动放弃");
        order.verify(loanMapper).conditionalUpdateStatus("L001", LoanStatus.IN_APPROVAL.getCode(),
                LoanStatus.CANCELLED.getCode(), "E001");
    }

    @Test
    void cancelApply_corpReviewActive_shouldCancel() {
        LoanApply existing = buildApprovalLoan("L001", "E001", "PI001");
        when(loanMapper.selectForUpdate("L001")).thenReturn(existing);
        when(workflowQueryApi.getProcessNodes("PI001"))
                .thenReturn(processDiagram("PI001", "corp_review", "ACTIVE"));
        when(loanMapper.conditionalUpdateStatus(anyString(), anyString(), anyString(), anyString())).thenReturn(1);

        loanService.cancelApply("L001", "客户需求变化", "E001");

        verify(workflowApi).cancelProcess("PI001", "客户需求变化");
        verify(loanMapper).conditionalUpdateStatus("L001", LoanStatus.IN_APPROVAL.getCode(),
                LoanStatus.CANCELLED.getCode(), "E001");
    }

    @Test
    void cancelApply_creditCheckActive_shouldRejectWithDomainError() {
        LoanApply existing = buildApprovalLoan("L001", "E001", "PI001");
        when(loanMapper.selectForUpdate("L001")).thenReturn(existing);
        when(workflowQueryApi.getProcessNodes("PI001"))
                .thenReturn(processDiagram("PI001", "credit_check", "ACTIVE"));

        assertThatThrownBy(() -> loanService.cancelApply("L001", "客户需求变化", "E001"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "BIZ-42304");

        verify(workflowApi, never()).cancelProcess(anyString(), anyString());
        verify(loanMapper, never()).conditionalUpdateStatus(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void cancelApply_creditApprovalActive_shouldRejectWithDomainError() {
        LoanApply existing = buildApprovalLoan("L001", "E001", "PI001");
        when(loanMapper.selectForUpdate("L001")).thenReturn(existing);
        when(workflowQueryApi.getProcessNodes("PI001"))
                .thenReturn(processDiagram("PI001", "credit_approval", "ACTIVE"));

        assertThatThrownBy(() -> loanService.cancelApply("L001", "客户需求变化", "E001"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "BIZ-42304");

        verify(workflowApi, never()).cancelProcess(anyString(), anyString());
        verify(loanMapper, never()).conditionalUpdateStatus(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void cancelApply_multipleActiveUserTasks_shouldFailClose() {
        LoanApply existing = buildApprovalLoan("L001", "E001", "PI001");
        when(loanMapper.selectForUpdate("L001")).thenReturn(existing);
        when(workflowQueryApi.getProcessNodes("PI001"))
                .thenReturn(processDiagram("PI001", List.of(
                        processNode("branch_approve", "ACTIVE", "userTask"),
                        processNode("unknown_node", "ACTIVE", "userTask"))));

        assertThatThrownBy(() -> loanService.cancelApply("L001", "客户需求变化", "E001"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "BIZ-50001");

        verify(workflowApi, never()).cancelProcess(anyString(), anyString());
        verify(loanMapper, never()).conditionalUpdateStatus(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void cancelApply_unknownActiveNode_shouldFailClose() {
        LoanApply existing = buildApprovalLoan("L001", "E001", "PI001");
        when(loanMapper.selectForUpdate("L001")).thenReturn(existing);
        when(workflowQueryApi.getProcessNodes("PI001"))
                .thenReturn(processDiagram("PI001", "unknown_node", "ACTIVE"));

        assertThatThrownBy(() -> loanService.cancelApply("L001", "客户需求变化", "E001"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "BIZ-50001");

        verify(workflowApi, never()).cancelProcess(anyString(), anyString());
        verify(loanMapper, never()).conditionalUpdateStatus(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void cancelApply_workflowNodeQueryFailure_shouldFailClose() {
        LoanApply existing = buildApprovalLoan("L001", "E001", "PI001");
        when(loanMapper.selectForUpdate("L001")).thenReturn(existing);
        when(workflowQueryApi.getProcessNodes("PI001")).thenThrow(new RuntimeException("query failed"));

        assertThatThrownBy(() -> loanService.cancelApply("L001", "客户需求变化", "E001"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "BIZ-50001");

        verify(workflowApi, never()).cancelProcess(anyString(), anyString());
        verify(loanMapper, never()).conditionalUpdateStatus(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void cancelApply_missingProcessInstanceId_shouldFailClose() {
        LoanApply existing = buildApprovalLoan("L001", "E001", null);
        when(loanMapper.selectForUpdate("L001")).thenReturn(existing);

        assertThatThrownBy(() -> loanService.cancelApply("L001", "客户需求变化", "E001"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "BIZ-50001");

        verifyNoWorkflowCancelOrStatusUpdate();
    }

    @Test
    void cancelApply_blankReason_shouldRejectBeforeQuery() {
        assertThatThrownBy(() -> loanService.cancelApply("L001", "   ", "E001"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "BIZ-40001");

        verify(loanMapper, never()).selectForUpdate(anyString());
        verifyNoWorkflowCancelOrStatusUpdate();
    }

    @Test
    void cancelApply_nonCreator_shouldRejectBeforeWorkflowQuery() {
        LoanApply existing = buildApprovalLoan("L001", "E001", "PI001");
        when(loanMapper.selectForUpdate("L001")).thenReturn(existing);

        assertThatThrownBy(() -> loanService.cancelApply("L001", "客户需求变化", "OTHER_EMP"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "BIZ-40305");

        verifyNoWorkflowCancelOrStatusUpdate();
        verify(workflowQueryApi, never()).getProcessNodes(anyString());
    }

    @Test
    void cancelApply_nonApprovalStatus_shouldRejectBeforeWorkflowQuery() {
        LoanApply existing = buildDraftLoan("L001", "E001");
        existing.setProcessInstanceId("PI001");
        when(loanMapper.selectForUpdate("L001")).thenReturn(existing);

        assertThatThrownBy(() -> loanService.cancelApply("L001", "客户需求变化", "E001"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "BIZ-42301");

        verifyNoWorkflowCancelOrStatusUpdate();
        verify(workflowQueryApi, never()).getProcessNodes(anyString());
    }

    @Test
    void cancelApply_conditionalStatusUpdateConflict_shouldThrowAndKeepFailSafe() {
        LoanApply existing = buildApprovalLoan("L001", "E001", "PI001");
        when(loanMapper.selectForUpdate("L001")).thenReturn(existing);
        when(workflowQueryApi.getProcessNodes("PI001"))
                .thenReturn(processDiagram("PI001", "branch_approve", "ACTIVE"));
        when(loanMapper.conditionalUpdateStatus(anyString(), anyString(), anyString(), anyString())).thenReturn(0);

        assertThatThrownBy(() -> loanService.cancelApply("L001", "客户需求变化", "E001"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "BIZ-42301");

        verify(workflowApi).cancelProcess("PI001", "客户需求变化");
        verify(loanMapper).conditionalUpdateStatus("L001", LoanStatus.IN_APPROVAL.getCode(),
                LoanStatus.CANCELLED.getCode(), "E001");
    }

    // ==================== getById ====================

    @Test
    void getById_exists_shouldReturn() {
        // given
        LoanApply existing = buildDraftLoan("L001", "E001");
        when(loanMapper.selectById("L001")).thenReturn(existing);

        // when
        LoanApply result = loanService.getById("L001");

        // then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo("L001");
    }

    @Test
    void getById_notExists_shouldThrowBIZ40401() {
        // given
        when(loanMapper.selectById("NOTEXIST")).thenReturn(null);

        // when & then
        assertThatThrownBy(() -> loanService.getById("NOTEXIST"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "BIZ-40401");
    }

    // ==================== listPage ====================

    @Test
    void listPage_shouldReturnPageResult() {
        // given
        LoanApply loan = buildDraftLoan("L001", "E001");
        when(loanMapper.selectPage(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(loan));
        when(loanMapper.countPage(any(), any(), any())).thenReturn(1L);

        // when
        PageResult<LoanApply> result = loanService.listPage(null, null, null, 1, 20);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getPageNo()).isEqualTo(1);
    }

    // ==================== getDetail ====================

    @Test
    @DisplayName("getDetail 应填充 custInfo 和 canOperate — 创建人+DRAFT → canOperate=true")
    void getDetail_populatesCustInfoAndCanOperate_creatorDraft_true() {
        // given
        LoanApply loan = new LoanApply();
        loan.setId("la001");
        loan.setCustId("cust001");
        loan.setCreatedBy("emp001");
        loan.setStatus(LoanStatus.DRAFT.getCode());
        when(loanMapper.selectById("la001")).thenReturn(loan);

        CustomerDTO cust = new CustomerDTO();
        cust.setId("cust001");
        cust.setCustName("测试客户");
        cust.setCustomerType("CORP");
        when(customerQueryApi.getCustomer("cust001")).thenReturn(Optional.of(cust));

        // when: 创建人 emp001 查详情
        LoanDetailResp resp1 = loanService.getDetail("la001", "emp001");

        // then
        assertThat(resp1.getCustInfo()).isNotNull();
        assertThat(resp1.getCustInfo().getCustName()).isEqualTo("测试客户");
        assertThat(resp1.getCustInfo().getCustType()).isEqualTo("CORP");
        assertThat(resp1.getCanOperate()).isTrue();
    }

    @Test
    @DisplayName("getDetail 非创建人 → canOperate=false")
    void getDetail_nonCreator_canOperateFalse() {
        // given
        LoanApply loan = new LoanApply();
        loan.setId("la001");
        loan.setCustId("cust001");
        loan.setCreatedBy("emp001");
        loan.setStatus(LoanStatus.DRAFT.getCode());
        when(loanMapper.selectById("la001")).thenReturn(loan);

        CustomerDTO cust = new CustomerDTO();
        cust.setId("cust001");
        cust.setCustName("测试客户");
        when(customerQueryApi.getCustomer("cust001")).thenReturn(Optional.of(cust));

        // when: 非创建人 other 查详情
        LoanDetailResp resp2 = loanService.getDetail("la001", "other");

        // then
        assertThat(resp2.getCanOperate()).isFalse();
    }

    @Test
    @DisplayName("getDetail 对 COMPLETED 状态 canOperate=false（终态不可操作）")
    void getDetail_completedStatus_canOperateFalse() {
        // given
        LoanApply loan = new LoanApply();
        loan.setId("la001");
        loan.setCustId("cust001");
        loan.setCreatedBy("emp001");
        loan.setStatus(LoanStatus.COMPLETED.getCode());
        when(loanMapper.selectById("la001")).thenReturn(loan);
        when(customerQueryApi.getCustomer(any())).thenReturn(Optional.empty());

        // when: 即使是创建人，终态也不可操作
        LoanDetailResp resp = loanService.getDetail("la001", "emp001");

        // then
        assertThat(resp.getCanOperate()).isFalse();
    }

    @Test
    @DisplayName("getDetail 客户信息不存在时 custInfo 为 null")
    void getDetail_customerNotFound_custInfoNull() {
        // given
        LoanApply loan = new LoanApply();
        loan.setId("la001");
        loan.setCustId("cust001");
        loan.setCreatedBy("emp001");
        loan.setStatus(LoanStatus.DRAFT.getCode());
        when(loanMapper.selectById("la001")).thenReturn(loan);
        when(customerQueryApi.getCustomer("cust001")).thenReturn(Optional.empty());

        // when
        LoanDetailResp resp = loanService.getDetail("la001", "emp001");

        // then
        assertThat(resp).isNotNull();
        assertThat(resp.getCustInfo()).isNull();
        // 客户不存在时，只要状态和创建人满足就 canOperate=true
        assertThat(resp.getCanOperate()).isTrue();
    }

    // ==================== 辅助方法 ====================

    private LoanApply buildDraftLoan(String id, String createdBy) {
        LoanApply loan = new LoanApply();
        loan.setId(id);
        loan.setApplyNo("LA20260414000001");
        loan.setCustId("CUST001");
        loan.setStatus(LoanStatus.DRAFT.getCode());
        loan.setCreatedBy(createdBy);
        loan.setOwnerOrgId("ORG001");
        loan.setDeleted(0);
        return loan;
    }

    private LoanApply buildApprovalLoan(String id, String createdBy, String processInstanceId) {
        LoanApply loan = buildDraftLoan(id, createdBy);
        loan.setStatus(LoanStatus.IN_APPROVAL.getCode());
        loan.setProcessInstanceId(processInstanceId);
        return loan;
    }

    private ProcessDiagramDTO processDiagram(String processInstanceId, String nodeKey, String status) {
        return processDiagram(processInstanceId, List.of(processNode(nodeKey, status, "userTask")));
    }

    private ProcessDiagramDTO processDiagram(String processInstanceId, List<ProcessDiagramNodeDTO> nodes) {
        ProcessDiagramDTO diagram = new ProcessDiagramDTO();
        diagram.setProcessInstanceId(processInstanceId);
        diagram.setNodes(nodes);
        return diagram;
    }

    private ProcessDiagramNodeDTO processNode(String nodeKey, String status, String nodeType) {
        ProcessDiagramNodeDTO node = new ProcessDiagramNodeDTO();
        node.setNodeKey(nodeKey);
        node.setNodeType(nodeType);
        node.setStatus(status);
        return node;
    }

    private void verifyNoWorkflowCancelOrStatusUpdate() {
        verify(workflowApi, never()).cancelProcess(anyString(), anyString());
        verify(loanMapper, never()).conditionalUpdateStatus(anyString(), anyString(), anyString(), anyString());
    }
}
