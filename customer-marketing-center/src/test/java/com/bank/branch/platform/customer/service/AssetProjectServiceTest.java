package com.bank.branch.platform.customer.service;

import com.bank.branch.platform.auth.api.BizScopeApi;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.customer.dto.asset.AssetProjectSaveRequest;
import com.bank.branch.platform.customer.entity.AssetProjectApply;
import com.bank.branch.platform.customer.entity.AssetProjectUrgentApply;
import com.bank.branch.platform.customer.entity.TouchTask;
import com.bank.branch.platform.customer.entity.TouchWorklog;
import com.bank.branch.platform.customer.entity.marketing.MarketingCustomerInfo;
import com.bank.branch.platform.customer.mapper.AssetProjectApplyMapper;
import com.bank.branch.platform.customer.mapper.AssetProjectUrgentApplyMapper;
import com.bank.branch.platform.customer.mapper.TouchTaskMapper;
import com.bank.branch.platform.customer.mapper.TouchWorklogMapper;
import com.bank.branch.platform.customer.mapper.marketing.MarketingCustomerClaimMapper;
import com.bank.branch.platform.customer.mapper.marketing.MarketingCustomerInfoMapper;
import com.bank.branch.platform.governance.api.FileApi;
import com.bank.branch.platform.governance.api.dto.FileObjectDTO;
import com.bank.branch.platform.workflow.api.WorkflowApi;
import com.bank.branch.platform.workflow.api.WorkflowQueryApi;
import com.bank.branch.platform.workflow.api.dto.StartProcessCmd;
import com.bank.branch.platform.workflow.api.dto.ProcessDiagramDTO;
import com.bank.branch.platform.workflow.api.dto.ProcessDiagramNodeDTO;
import com.bank.branch.platform.workflow.api.dto.WorkflowLaunchResp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetProjectServiceTest {
    @Mock private AssetProjectApplyMapper applyMapper;
    @Mock private AssetProjectUrgentApplyMapper urgentMapper;
    @Mock private MarketingCustomerInfoMapper customerMapper;
    @Mock private TouchTaskMapper touchTaskMapper;
    @Mock private TouchWorklogMapper worklogMapper;
    @Mock private MarketingCustomerClaimMapper customerClaimMapper;
    @Mock private BizScopeApi bizScopeApi;
    @Mock private WorkflowApi workflowApi;
    @Mock private WorkflowQueryApi workflowQueryApi;
    @Mock private FileApi fileApi;

    @InjectMocks private AssetProjectService service;

    @Test
    void create_shouldPersistFormalAssetProjectAndBindAttachments() {
        AssetProjectSaveRequest request = validRequest();
        request.setAttachmentIds(List.of("file-1"));
        MarketingCustomerInfo customer = customer();
        when(customerMapper.selectActiveById(101L)).thenReturn(customer);
        when(applyMapper.insert(any(AssetProjectApply.class))).thenAnswer(invocation -> {
            invocation.<AssetProjectApply>getArgument(0).setId(9001L);
            return 1;
        });
        when(urgentMapper.selectByAssetProjectId(9001L)).thenReturn(List.of());
        when(fileApi.listBizFiles("ASSET_PROJECT", "9001")).thenReturn(List.of());

        var result = service.create(request, "E001", "ORG001", false);

        assertThat(result.getId()).isEqualTo(9001L);
        assertThat(result.getStatus()).isEqualTo("DRAFT");
        assertThat(result.getCustomerName()).isEqualTo("示例客户");
        verify(fileApi).bindFile("ASSET_PROJECT", "9001", "file-1", "ATTACHMENT");
    }

    @Test
    void create_projectLoanExceedsInvestment_shouldRemainAWarningAndPersist() {
        AssetProjectSaveRequest request = validRequest();
        request.setProjectTotalInvestment(new BigDecimal("100000"));
        request.setProjectLoanAmount(new BigDecimal("100001"));
        when(customerMapper.selectActiveById(101L)).thenReturn(customer());
        when(applyMapper.insert(any(AssetProjectApply.class))).thenAnswer(invocation -> {
            invocation.<AssetProjectApply>getArgument(0).setId(9001L);
            return 1;
        });
        when(urgentMapper.selectByAssetProjectId(9001L)).thenReturn(List.of());
        when(fileApi.listBizFiles("ASSET_PROJECT", "9001")).thenReturn(List.of());

        var result = service.create(request, "E001", "ORG001", false);

        assertThat(result.getId()).isEqualTo(9001L);
        verify(applyMapper).insert(any(AssetProjectApply.class));
    }

    @Test
    void create_creditExposureExceedsCredit_shouldRejectBeforeInsert() {
        AssetProjectSaveRequest request = validRequest();
        request.setCreditAmount(new BigDecimal("100000"));
        request.setCreditExposureAmount(new BigDecimal("100001"));
        when(customerMapper.selectActiveById(101L)).thenReturn(customer());

        assertThatThrownBy(() -> service.create(request, "E001", "ORG001", false))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("授信敞口不能超过授信金额");
        verify(applyMapper, never()).insert(any(AssetProjectApply.class));
    }

    @Test
    void create_sourceWorklogMustBelongToSourceTaskAndCustomer() {
        AssetProjectSaveRequest request = validRequest();
        request.setSourceTouchTaskId(31L);
        request.setSourceWorklogId(41L);
        TouchTask task = new TouchTask();
        task.setId(31L);
        task.setCustId(101L);
        task.setAssigneeEmpId("E001");
        TouchWorklog worklog = new TouchWorklog();
        worklog.setId(41L);
        worklog.setTaskId(99L);
        worklog.setCustId(101L);
        when(customerMapper.selectActiveById(101L)).thenReturn(customer());
        when(touchTaskMapper.selectById("31")).thenReturn(task);
        when(worklogMapper.selectById(41L)).thenReturn(worklog);

        assertThatThrownBy(() -> service.create(request, "E001", "ORG001", false))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("触达来源与客户不匹配");
        verify(applyMapper, never()).insert(any(AssetProjectApply.class));
    }

    @Test
    void create_shouldBindEachAttachmentOnlyOnce() {
        AssetProjectSaveRequest request = validRequest();
        request.setAttachmentIds(List.of("file-1", "file-1", "file-2"));
        when(customerMapper.selectActiveById(101L)).thenReturn(customer());
        when(applyMapper.insert(any(AssetProjectApply.class))).thenAnswer(invocation -> {
            invocation.<AssetProjectApply>getArgument(0).setId(9001L);
            return 1;
        });
        when(urgentMapper.selectByAssetProjectId(9001L)).thenReturn(List.of());
        when(fileApi.listBizFiles("ASSET_PROJECT", "9001")).thenReturn(List.of());

        service.create(request, "E001", "ORG001", false);

        verify(fileApi, times(1)).bindFile("ASSET_PROJECT", "9001", "file-1", "ATTACHMENT");
        verify(fileApi, times(1)).bindFile("ASSET_PROJECT", "9001", "file-2", "ATTACHMENT");
    }

    @Test
    void update_staleLockVersion_shouldFailWithoutBindingAttachments() {
        AssetProjectSaveRequest request = validRequest();
        request.setLockVersion(3);
        request.setAttachmentIds(List.of("file-1"));
        AssetProjectApply current = draft();
        current.setLockVersion(3);
        when(applyMapper.selectActiveById(9001L)).thenReturn(current);
        when(customerMapper.selectActiveById(101L)).thenReturn(customer());
        when(applyMapper.updateDraftCas(any(AssetProjectApply.class), eq(3))).thenReturn(0);

        assertThatThrownBy(() -> service.update(9001L, request, "E001", "ORG001", false))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("状态已变化");
        verify(fileApi, never()).bindFile(any(), any(), any(), any());
    }

    @Test
    void submit_shouldLockDraftAndUseStableBusinessKey() {
        AssetProjectApply current = draft();
        current.setLockVersion(2);
        when(applyMapper.selectForUpdate(9001L)).thenReturn(current);
        when(customerMapper.selectActiveById(101L)).thenReturn(customer());
        WorkflowLaunchResp launch = new WorkflowLaunchResp();
        launch.setProcessInstanceId("PI-1");
        when(workflowApi.startProcess(any(StartProcessCmd.class))).thenReturn(launch);
        when(applyMapper.markSubmitted(eq(9001L), eq(2), eq("ASSET_PROJECT:9001"),
                eq("PI-1"), eq("E001"), any())).thenReturn(1);

        var result = service.submit(9001L, "E001", "ORG001", false);

        assertThat(result.getBusinessKey()).isEqualTo("ASSET_PROJECT:9001");
        assertThat(result.getStatus()).isEqualTo("IN_APPROVAL");
        verify(workflowApi).startProcess(org.mockito.ArgumentMatchers.argThat(cmd ->
                "ASSET_PROJECT".equals(cmd.getBizType())
                        && "9001".equals(cmd.getBizId())
                        && "ASSET_PROJECT:9001".equals(cmd.getBusinessKey())));
    }

    @Test
    void create_customerOutsideMainManagerClaimAndDataScope_shouldReject() {
        AssetProjectSaveRequest request = validRequest();
        MarketingCustomerInfo customer = customer();
        customer.setMainManagerId("E999");
        customer.setMainOrgId("ORG999");
        when(customerMapper.selectActiveById(101L)).thenReturn(customer);
        when(customerClaimMapper.countActiveByCustomerAndEmp(101L, "E001")).thenReturn(0);
        when(bizScopeApi.checkWritePermission("E001", com.bank.branch.platform.common.security.enums.BizType.CUSTOMER,
                "ORG999", "E999")).thenReturn(false);

        assertThatThrownBy(() -> service.create(request, "E001", "ORG001", false))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("无权操作");
        verify(applyMapper, never()).insert(any(AssetProjectApply.class));
    }

    @Test
    void create_activeClaim_shouldAllowCustomerWithoutMainManagerOrOrgScope() {
        AssetProjectSaveRequest request = validRequest();
        MarketingCustomerInfo customer = customer();
        customer.setMainManagerId("E999");
        customer.setMainOrgId("ORG999");
        when(customerMapper.selectActiveById(101L)).thenReturn(customer);
        when(customerClaimMapper.countActiveByCustomerAndEmp(101L, "E001")).thenReturn(1);
        when(applyMapper.insert(any(AssetProjectApply.class))).thenAnswer(invocation -> {
            invocation.<AssetProjectApply>getArgument(0).setId(9001L);
            return 1;
        });
        when(urgentMapper.selectByAssetProjectId(9001L)).thenReturn(List.of());
        when(fileApi.listBizFiles("ASSET_PROJECT", "9001")).thenReturn(List.of());

        assertThat(service.create(request, "E001", "ORG001", false).getId()).isEqualTo(9001L);
    }

    @Test
    void update_shouldUnbindRemovedAttachmentAndBindNewAttachment() {
        AssetProjectSaveRequest request = validRequest();
        request.setLockVersion(3);
        request.setAttachmentIds(List.of("file-new"));
        AssetProjectApply current = draft();
        current.setLockVersion(3);
        when(applyMapper.selectActiveById(9001L)).thenReturn(current);
        when(customerMapper.selectActiveById(101L)).thenReturn(customer());
        when(applyMapper.updateDraftCas(any(AssetProjectApply.class), eq(3))).thenReturn(1);
        FileObjectDTO old = new FileObjectDTO();
        old.setId("file-old");
        old.setFileRole("ATTACHMENT");
        when(fileApi.listBizFiles("ASSET_PROJECT", "9001")).thenReturn(List.of(old), List.of());
        when(urgentMapper.selectByAssetProjectId(9001L)).thenReturn(List.of());

        service.update(9001L, request, "E001", "ORG001", false);

        verify(fileApi).unbindFile("ASSET_PROJECT", "9001", "file-old");
        verify(fileApi).bindFile("ASSET_PROJECT", "9001", "file-new", "ATTACHMENT");
    }

    @Test
    void submit_localCasFailure_shouldThrowSoSharedWorkflowTransactionRollsBack() {
        AssetProjectApply current = draft();
        current.setLockVersion(2);
        when(applyMapper.selectForUpdate(9001L)).thenReturn(current);
        when(customerMapper.selectActiveById(101L)).thenReturn(customer());
        WorkflowLaunchResp launch = new WorkflowLaunchResp();
        launch.setProcessInstanceId("PI-ORPHAN-GUARD");
        when(workflowApi.startProcess(any(StartProcessCmd.class))).thenReturn(launch);
        when(applyMapper.markSubmitted(eq(9001L), eq(2), eq("ASSET_PROJECT:9001"),
                eq("PI-ORPHAN-GUARD"), eq("E001"), any())).thenReturn(0);

        assertThatThrownBy(() -> service.submit(9001L, "E001", "ORG001", false))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("状态已变化");
    }

    @Test
    void requestUrgent_localUpdateFailure_shouldThrowSoSharedWorkflowTransactionRollsBack() {
        AssetProjectApply current = draft();
        current.setStatus("IN_APPROVAL");
        current.setIsUrgent(0);
        current.setProcessInstanceId("PI-MAIN");
        when(applyMapper.selectForUpdate(9001L)).thenReturn(current);
        when(urgentMapper.selectActiveByAssetProjectId(9001L)).thenReturn(null);
        ProcessDiagramNodeDTO node = new ProcessDiagramNodeDTO();
        node.setNodeKey("branch_approve");
        node.setNodeType("userTask");
        node.setNodeName("分行审批");
        node.setStatus("ACTIVE");
        node.setTaskId("TASK-1");
        ProcessDiagramDTO diagram = new ProcessDiagramDTO();
        diagram.setNodes(List.of(node));
        when(workflowQueryApi.getProcessNodes("PI-MAIN")).thenReturn(diagram);
        when(urgentMapper.insert(any(AssetProjectUrgentApply.class))).thenAnswer(invocation -> {
            invocation.<com.bank.branch.platform.customer.entity.AssetProjectUrgentApply>getArgument(0).setId(71L);
            return 1;
        });
        when(workflowApi.startProcess(any(StartProcessCmd.class)))
                .thenReturn(new WorkflowLaunchResp("PI-U1", "ASSET_PROJECT_URGENT:71", "TASK-U1"));
        when(urgentMapper.updateById(any(AssetProjectUrgentApply.class))).thenReturn(0);

        assertThatThrownBy(() -> service.requestUrgent(9001L, "客户明确要求加快", "E001", "ORG001", false))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("状态已变化");
    }

    private AssetProjectSaveRequest validRequest() {
        AssetProjectSaveRequest request = new AssetProjectSaveRequest();
        request.setCustId(101L);
        request.setProjectName("产业园一期");
        request.setProjectType("FIXED_ASSET");
        request.setBizType("PROJECT_LOAN");
        request.setGuaranteeType("MORTGAGE");
        request.setProjectTotalInvestment(new BigDecimal("1000000"));
        request.setProjectLoanAmount(new BigDecimal("800000"));
        request.setCreditAmount(new BigDecimal("600000"));
        request.setCreditExposureAmount(new BigDecimal("400000"));
        return request;
    }

    private MarketingCustomerInfo customer() {
        MarketingCustomerInfo customer = new MarketingCustomerInfo();
        customer.setId(101L);
        customer.setCustName("示例客户");
        customer.setMainManagerId("E001");
        customer.setMainOrgId("ORG001");
        return customer;
    }

    private AssetProjectApply draft() {
        AssetProjectApply apply = new AssetProjectApply();
        apply.setId(9001L);
        apply.setApplyNo("AP001");
        apply.setCustId(101L);
        apply.setApplicantEmpId("E001");
        apply.setApplicantOrgId("ORG001");
        apply.setStatus("DRAFT");
        apply.setRecordStatus("ACTIVE");
        apply.setProjectName("产业园一期");
        apply.setProjectType("FIXED_ASSET");
        apply.setBizType("PROJECT_LOAN");
        apply.setGuaranteeType("MORTGAGE");
        apply.setProjectTotalInvestment(new BigDecimal("1000000"));
        apply.setProjectLoanAmount(new BigDecimal("800000"));
        apply.setCreditAmount(new BigDecimal("600000"));
        apply.setCreditExposureAmount(new BigDecimal("400000"));
        apply.setLockVersion(0);
        return apply;
    }
}
