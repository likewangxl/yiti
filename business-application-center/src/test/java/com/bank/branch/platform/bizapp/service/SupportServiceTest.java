package com.bank.branch.platform.bizapp.service;

import com.bank.branch.platform.bizapp.api.converter.SupportRequestDTOConverter;
import com.bank.branch.platform.bizapp.dto.resp.SubmitRespDTO;
import com.bank.branch.platform.bizapp.dto.resp.SupportRequestCreateRespDTO;
import com.bank.branch.platform.bizapp.entity.SupportRequest;
import com.bank.branch.platform.bizapp.enums.SupportScenario;
import com.bank.branch.platform.bizapp.enums.SupportSourceType;
import com.bank.branch.platform.bizapp.enums.SupportStatus;
import com.bank.branch.platform.bizapp.event.SupportSubmittedEvent;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.bizapp.mapper.SupportRequestMapper;
import com.bank.branch.platform.customer.api.CustomerQueryApi;
import com.bank.branch.platform.governance.api.FileApi;
import com.bank.branch.platform.workflow.api.WorkflowApi;
import com.bank.branch.platform.workflow.api.dto.StartProcessCmd;
import com.bank.branch.platform.workflow.api.dto.WorkflowLaunchResp;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SupportService 单元测试（TDD）。
 * 含 SupportSourceType 枚举契约断言。
 */
@ExtendWith(MockitoExtension.class)
class SupportServiceTest {

    @Mock
    private SupportRequestMapper supportMapper;

    @Mock
    private SupportScenarioRouter scenarioRouter;

    @Mock
    private SupportProductSplitService splitService;

    @Mock
    private BizStateMachine bizStateMachine;

    @Mock
    private BizNoGenerator bizNoGenerator;

    @Mock
    private WorkflowApi workflowApi;

    @Mock
    private CustomerQueryApi customerQueryApi;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private SupportRequestDTOConverter supportRequestDTOConverter;

    @Mock
    private FileApi fileApi;

    @InjectMocks
    private SupportService supportService;

    @BeforeEach
    void allowLegacyExistingCustomerFixtures() {
        // 旧 7 参数入口现同样执行机构认领校验；既有单测夹具默认视为已认领。
        lenient().when(customerQueryApi.isClaimedByOrg(anyString(), anyString())).thenReturn(true);
    }

    // ==================== SupportSourceType 枚举契约 ====================

    @Test
    @DisplayName("SupportSourceType 字典：无 MANUAL，有 EXISTING_CUSTOMER 与 TOUCH_TASK")
    void supportSourceType_enumContractMatchesDoc() {
        assertThat(SupportSourceType.values())
                .extracting(SupportSourceType::name)
                .containsExactlyInAnyOrder("EXISTING_CUSTOMER", "TOUCH_TASK");
    }

    // ==================== create ====================

    @Test
    void create_scenarioA_singleProduct_shouldDelegateSplit() {
        // given
        when(customerQueryApi.isValidCustomer("CUST001")).thenReturn(true);
        when(scenarioRouter.route(any(), any(), any())).thenReturn(SupportScenario.A);
        SupportRequest sr = buildRequest("SR001", SupportStatus.DRAFT);
        // splitService 负责写入 submitGroupId，模拟真实行为
        sr.setSubmitGroupId("grp001");
        sr.setProductId("P001");
        when(splitService.splitByProducts(any(), eq("CUST001"), any(), eq("E10001"), eq("ORG001")))
                .thenReturn(List.of(sr));

        // when
        SupportRequestCreateRespDTO result = supportService.create(
                List.of("P001"), "CUST001", null, null, null, "E10001", "ORG001");

        // then
        assertThat(result.getProductCount()).isEqualTo(1);
        assertThat(result.getSubmitGroupId()).isEqualTo("grp001");
        assertThat(result.getRequests()).hasSize(1);
        assertThat(result.getRequests().get(0).getScenario()).isEqualTo("A");
        verify(splitService).splitByProducts(any(), eq("CUST001"), any(), eq("E10001"), eq("ORG001"));
    }

    @Test
    void create_scenarioA_multiProduct_shouldReturnMultipleRecords() {
        // given
        when(customerQueryApi.isValidCustomer("CUST001")).thenReturn(true);
        when(scenarioRouter.route(any(), any(), any())).thenReturn(SupportScenario.A);
        SupportRequest sr1 = buildRequest("SR001", SupportStatus.DRAFT);
        sr1.setSubmitGroupId("grpABC");
        sr1.setProductId("P001");
        SupportRequest sr2 = buildRequest("SR002", SupportStatus.DRAFT);
        sr2.setSubmitGroupId("grpABC");
        sr2.setProductId("P002");
        when(splitService.splitByProducts(any(), eq("CUST001"), any(), eq("E10001"), eq("ORG001")))
                .thenReturn(List.of(sr1, sr2));

        // when
        SupportRequestCreateRespDTO result = supportService.create(
                List.of("P001", "P002"), "CUST001", null, null, null, "E10001", "ORG001");

        // then
        assertThat(result.getProductCount()).isEqualTo(2);
        assertThat(result.getRequests()).hasSize(2);
        assertThat(result.getSubmitGroupId()).isEqualTo("grpABC");
    }

    @Test
    void create_scenarioA_submitGroupId_sharedAcrossItems() {
        // given: 场景A多产品，所有 CreatedItem 共享同一 submitGroupId
        when(customerQueryApi.isValidCustomer("CUST001")).thenReturn(true);
        when(scenarioRouter.route(any(), any(), any())).thenReturn(SupportScenario.A);
        SupportRequest sr1 = buildRequest("SR001", SupportStatus.DRAFT);
        sr1.setSubmitGroupId("grpXXX");
        SupportRequest sr2 = buildRequest("SR002", SupportStatus.DRAFT);
        sr2.setSubmitGroupId("grpXXX");
        when(splitService.splitByProducts(any(), eq("CUST001"), any(), eq("E10001"), eq("ORG001")))
                .thenReturn(List.of(sr1, sr2));

        // when
        SupportRequestCreateRespDTO result = supportService.create(
                List.of("P001", "P002"), "CUST001", null, null, null, "E10001", "ORG001");

        // then: submitGroupId 来自 entities（splitService 已写入）
        assertThat(result.getSubmitGroupId()).isEqualTo("grpXXX");
        assertThat(result.getProductCount()).isEqualTo(2);
        assertThat(result.getRequests()).hasSize(2)
                .allSatisfy(item -> assertThat(item.getScenario()).isEqualTo("A"));
    }

    @Test
    void create_scenarioB_withOtherDemand_shouldCreateSingleRecord() {
        // given
        when(customerQueryApi.isValidCustomer("CUST001")).thenReturn(true);
        when(scenarioRouter.route(any(), any(), any())).thenReturn(SupportScenario.B);
        when(bizNoGenerator.generateSupportNo()).thenReturn("SR20260414000001");
        when(supportMapper.insert(any(SupportRequest.class))).thenReturn(1);

        // when
        SupportRequestCreateRespDTO result = supportService.create(
                Collections.emptyList(), "CUST001", null, "咨询债券", "DEPT001", "E10001", "ORG001");

        // then
        assertThat(result.getProductCount()).isEqualTo(1);
        assertThat(result.getSubmitGroupId()).isNotBlank();
        assertThat(result.getRequests()).hasSize(1);
        assertThat(result.getRequests().get(0).getScenario()).isEqualTo("B");
        verify(supportMapper).insert(any(SupportRequest.class));
        verify(splitService, never()).splitByProducts(any(), any(), any(), any(), any());
    }

    @Test
    void create_invalidCustomer_shouldThrowBIZ40301() {
        // given
        when(customerQueryApi.isValidCustomer("CUST999")).thenReturn(false);

        // when / then
        assertThatThrownBy(() -> supportService.create(
                List.of("P001"), "CUST999", null, null, null, "E10001", "ORG001"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "BIZ-40301");
    }

    @Test
    void create_withRunningSupportAndNoConfirmation_shouldThrowBIZ40907WithCount() {
        when(customerQueryApi.isValidCustomer("CUST001")).thenReturn(true);
        when(supportMapper.countRunningByCustomer("CUST001")).thenReturn(2L);

        assertThatThrownBy(() -> supportService.create(
                List.of("P001"), "CUST001", null, null, null, "E10001", "ORG001",
                SupportSourceType.EXISTING_CUSTOMER.getCode(), false, List.of()))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "BIZ-40907")
                .hasMessageContaining("2");
        verify(scenarioRouter, never()).route(any(), any(), any());
    }

    @Test
    void create_withRunningSupportAndConfirmation_shouldCreateAndBindEachSplitAttachment() {
        when(customerQueryApi.isValidCustomer("CUST001")).thenReturn(true);
        when(supportMapper.countRunningByCustomer("CUST001")).thenReturn(1L);
        when(scenarioRouter.route(any(), any(), any())).thenReturn(SupportScenario.A);

        SupportRequest sr1 = buildRequest("SR001", SupportStatus.DRAFT);
        sr1.setSubmitGroupId("grp-confirmed");
        sr1.setProductId("P001");
        SupportRequest sr2 = buildRequest("SR002", SupportStatus.DRAFT);
        sr2.setSubmitGroupId("grp-confirmed");
        sr2.setProductId("P002");
        when(splitService.splitByProducts(any(), eq("CUST001"), any(), eq("E10001"), eq("ORG001")))
                .thenReturn(List.of(sr1, sr2));

        SupportRequestCreateRespDTO result = supportService.create(
                List.of("P001", "P002"), "CUST001", null, null, null, "E10001", "ORG001",
                SupportSourceType.EXISTING_CUSTOMER.getCode(), true, List.of("FILE-1", "FILE-2"));

        assertThat(result.getProductCount()).isEqualTo(2);
        verify(fileApi).bindFile("SUPPORT_REQUEST", "SR001", "FILE-1", "ATTACHMENT");
        verify(fileApi).bindFile("SUPPORT_REQUEST", "SR001", "FILE-2", "ATTACHMENT");
        verify(fileApi).bindFile("SUPPORT_REQUEST", "SR002", "FILE-1", "ATTACHMENT");
        verify(fileApi).bindFile("SUPPORT_REQUEST", "SR002", "FILE-2", "ATTACHMENT");
    }

    // ==================== submit ====================

    @Test
    void submit_validDraft_shouldStartWorkflowAndReturnDTO() {
        // given: 场景A（有productId，无supportDeptId）
        SupportRequest draft = buildRequest("SR001", SupportStatus.DRAFT);
        draft.setCreatedBy("E10001");
        draft.setProductId("P001");
        when(supportMapper.selectForUpdate("SR001")).thenReturn(draft);
        doNothing().when(bizStateMachine).validateSupportTransition(anyString(), anyString());
        when(workflowApi.startProcess(any(StartProcessCmd.class)))
                .thenReturn(new WorkflowLaunchResp("PID001", "SUPPORT:SR001", "T001"));
        when(supportMapper.updateById(any(SupportRequest.class))).thenReturn(1);

        // when
        SubmitRespDTO result = supportService.submit("SR001", "E10001", "ORG001");

        // then
        verify(workflowApi).startProcess(any(StartProcessCmd.class));
        verify(supportMapper).updateById(any(SupportRequest.class));
        verify(eventPublisher).publishEvent(any(SupportSubmittedEvent.class));
        // 验证返回值含 processInstanceId
        assertThat(result).isNotNull();
        assertThat(result.getProcessInstanceId()).isEqualTo("PID001");
        assertThat(result.getBusinessKey()).isEqualTo("SUPPORT:SR001");
        assertThat(result.getStatus()).isEqualTo(SupportStatus.IN_APPROVAL.getCode());
    }

    @Test
    void submit_notDraft_shouldThrow() {
        // given: 已是 IN_APPROVAL 状态
        SupportRequest inApproval = buildRequest("SR001", SupportStatus.IN_APPROVAL);
        inApproval.setCreatedBy("E10001");
        when(supportMapper.selectForUpdate("SR001")).thenReturn(inApproval);
        // 状态机对 IN_APPROVAL -> IN_APPROVAL 抛异常
        org.mockito.Mockito.doThrow(new BizException("BIZ-42303", "申请非草稿状态不可编辑"))
                .when(bizStateMachine).validateSupportTransition(
                        eq(SupportStatus.IN_APPROVAL.getCode()),
                        eq(SupportStatus.IN_APPROVAL.getCode())
                );

        // 简单测试：非草稿状态直接拒绝
        assertThatThrownBy(() -> supportService.submit("SR001", "E10001", "ORG001"))
                .isInstanceOf(BizException.class);
    }

    @Test
    void submit_notCreator_shouldThrowBIZ40305() {
        // given: 创建人是 E10001，提交人是 E99999
        SupportRequest draft = buildRequest("SR001", SupportStatus.DRAFT);
        draft.setCreatedBy("E10001");
        when(supportMapper.selectForUpdate("SR001")).thenReturn(draft);

        // when / then
        assertThatThrownBy(() -> supportService.submit("SR001", "E99999", "ORG001"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "BIZ-40305");
    }

    @Test
    void submit_scenarioA_shouldUseSimpleProcess() {
        // given: 场景A由 productId 非null + supportDeptId 为null 判断
        SupportRequest draft = buildRequest("SR001", SupportStatus.DRAFT);
        draft.setCreatedBy("E10001");
        draft.setProductId("P001");
        draft.setSupportDeptId(null);
        when(supportMapper.selectForUpdate("SR001")).thenReturn(draft);
        doNothing().when(bizStateMachine).validateSupportTransition(anyString(), anyString());
        when(workflowApi.startProcess(any(StartProcessCmd.class)))
                .thenReturn(new WorkflowLaunchResp("PID001", "SUPPORT:SR001", "T001"));
        when(supportMapper.updateById(any(SupportRequest.class))).thenReturn(1);

        // when
        supportService.submit("SR001", "E10001", "ORG001");

        // then: processDefinitionKey 应为 support_simple_v1
        ArgumentCaptor<StartProcessCmd> captor = ArgumentCaptor.forClass(StartProcessCmd.class);
        verify(workflowApi).startProcess(captor.capture());
        assertThat(captor.getValue().getProcessDefinitionKey()).isEqualTo("support_simple_v1");
    }

    @Test
    void submit_scenarioB_shouldUseComplexProcess() {
        // given: 场景B由 supportDeptId 非null 判断
        SupportRequest draft = buildRequest("SR001", SupportStatus.DRAFT);
        draft.setCreatedBy("E10001");
        draft.setProductId(null);
        draft.setSupportDeptId("DEPT001");
        when(supportMapper.selectForUpdate("SR001")).thenReturn(draft);
        doNothing().when(bizStateMachine).validateSupportTransition(anyString(), anyString());
        when(workflowApi.startProcess(any(StartProcessCmd.class)))
                .thenReturn(new WorkflowLaunchResp("PID002", "SUPPORT:SR001", "T002"));
        when(supportMapper.updateById(any(SupportRequest.class))).thenReturn(1);

        // when
        supportService.submit("SR001", "E10001", "ORG001");

        // then: processDefinitionKey 应为 support_complex_v1
        ArgumentCaptor<StartProcessCmd> captor = ArgumentCaptor.forClass(StartProcessCmd.class);
        verify(workflowApi).startProcess(captor.capture());
        assertThat(captor.getValue().getProcessDefinitionKey()).isEqualTo("support_complex_v1");
    }

    // ==================== cancel ====================

    @Test
    void cancel_inApproval_shouldCancel() {
        // given
        SupportRequest inApproval = buildRequest("SR001", SupportStatus.IN_APPROVAL);
        inApproval.setCreatedBy("E10001");
        when(supportMapper.selectForUpdate("SR001")).thenReturn(inApproval);
        doNothing().when(bizStateMachine).validateSupportTransition(anyString(), anyString());
        when(supportMapper.updateById(any(SupportRequest.class))).thenReturn(1);

        // when
        supportService.cancel("SR001", "E10001");

        // then
        ArgumentCaptor<SupportRequest> captor = ArgumentCaptor.forClass(SupportRequest.class);
        verify(supportMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(SupportStatus.CANCELLED.getCode());
    }

    @Test
    void cancel_inProgress_shouldCancel() {
        // given
        SupportRequest inProgress = buildRequest("SR001", SupportStatus.IN_PROGRESS);
        inProgress.setCreatedBy("E10001");
        when(supportMapper.selectForUpdate("SR001")).thenReturn(inProgress);
        doNothing().when(bizStateMachine).validateSupportTransition(anyString(), anyString());
        when(supportMapper.updateById(any(SupportRequest.class))).thenReturn(1);

        // when
        supportService.cancel("SR001", "E10001");

        // then
        verify(supportMapper).updateById(any(SupportRequest.class));
    }

    // ==================== deleteDraft ====================

    @Test
    void deleteDraft_valid_shouldSoftDelete() {
        // given
        SupportRequest draft = buildRequest("SR001", SupportStatus.DRAFT);
        draft.setCreatedBy("E10001");
        when(supportMapper.selectForUpdate("SR001")).thenReturn(draft);
        when(supportMapper.updateById(any(SupportRequest.class))).thenReturn(1);

        // when
        supportService.deleteDraft("SR001", "E10001");

        // then
        ArgumentCaptor<SupportRequest> captor = ArgumentCaptor.forClass(SupportRequest.class);
        verify(supportMapper).updateById(captor.capture());
        assertThat(captor.getValue().getDeleted()).isEqualTo(1);
    }

    @Test
    void deleteDraft_notDraft_shouldThrow() {
        // given
        SupportRequest inApproval = buildRequest("SR001", SupportStatus.IN_APPROVAL);
        inApproval.setCreatedBy("E10001");
        when(supportMapper.selectForUpdate("SR001")).thenReturn(inApproval);

        // when / then
        assertThatThrownBy(() -> supportService.deleteDraft("SR001", "E10001"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "BIZ-42303");
    }

    // ==================== getById ====================

    @Test
    void getById_exists_shouldReturn() {
        // given
        SupportRequest sr = buildRequest("SR001", SupportStatus.DRAFT);
        when(supportMapper.selectById("SR001")).thenReturn(sr);

        // when
        SupportRequest result = supportService.getById("SR001");

        // then
        assertThat(result.getId()).isEqualTo("SR001");
    }

    @Test
    void getById_notFound_shouldThrow() {
        // given
        when(supportMapper.selectById("NOTEXIST")).thenReturn(null);

        // when / then
        assertThatThrownBy(() -> supportService.getById("NOTEXIST"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", "BIZ-40401");
    }

    // ==================== listPage ====================

    @Test
    void listPage_shouldReturnPageResult() {
        // given
        SupportRequest sr = buildRequest("SR001", SupportStatus.DRAFT);
        when(supportMapper.selectPageForSupport(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(sr));
        when(supportMapper.countPageForSupport(any(), any(), any())).thenReturn(1L);

        // when
        PageResult<SupportRequest> result = supportService.listPage(null, null, "ORG001", 1, 20);

        // then
        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getRecords()).hasSize(1);
    }

    // ==================== 辅助方法 ====================

    private SupportRequest buildRequest(String id, SupportStatus status) {
        SupportRequest sr = new SupportRequest();
        sr.setId(id);
        sr.setRequestNo("SR20260414000001");
        sr.setCustId("CUST001");
        sr.setStatus(status.getCode());
        sr.setBusinessKey("SUPPORT:" + id);
        sr.setOwnerOrgId("ORG001");
        sr.setDeleted(0);
        return sr;
    }
}
