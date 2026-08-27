package com.bank.branch.platform.customer.marketing.lead;

import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.UserDTO;
import com.bank.branch.platform.customer.dto.marketing.lead.LeadCreateRequest;
import com.bank.branch.platform.customer.dto.marketing.lead.LeadDetailResponse;
import com.bank.branch.platform.customer.dto.marketing.lead.LeadUpdateRequest;
import com.bank.branch.platform.customer.dto.marketing.lead.MarketingCustomerSnapshot;
import com.bank.branch.platform.customer.entity.marketing.MarketingCustomerTag;
import com.bank.branch.platform.customer.entity.marketing.MarketingLeadInfo;
import com.bank.branch.platform.customer.entity.marketing.MarketingLeadManagerScope;
import com.bank.branch.platform.customer.entity.marketing.MarketingLeadTagRel;
import com.bank.branch.platform.customer.enums.CustomerRoleCode;
import com.bank.branch.platform.customer.mapper.marketing.MarketingCustomerTagMapper;
import com.bank.branch.platform.customer.mapper.marketing.MarketingLeadInfoMapper;
import com.bank.branch.platform.customer.mapper.marketing.MarketingLeadManagerScopeMapper;
import com.bank.branch.platform.customer.mapper.marketing.MarketingLeadTagRelMapper;
import com.bank.branch.platform.customer.service.marketing.MarketingLeadEntryService;
import com.bank.branch.platform.governance.api.FileApi;
import com.bank.branch.platform.governance.api.dto.FileObjectDTO;
import com.bank.branch.platform.workflow.api.WorkflowApi;
import com.bank.branch.platform.workflow.api.dto.WorkflowLaunchResp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 页面三手工线索服务的状态和防重契约。 */
@ExtendWith(MockitoExtension.class)
class MarketingLeadEntryServiceTest {

    @Mock
    private MarketingLeadInfoMapper leadMapper;
    @Mock
    private com.bank.branch.platform.customer.mapper.marketing.MarketingCustomerInfoMapper customerMapper;
    @Mock
    private WorkflowApi workflowApi;
    @Mock
    private MarketingLeadManagerScopeMapper managerScopeMapper;
    @Mock
    private MarketingLeadTagRelMapper leadTagRelMapper;
    @Mock
    private MarketingCustomerTagMapper tagMapper;
    @Mock
    private UserApi userApi;
    @Mock
    private FileApi fileApi;

    @InjectMocks
    private MarketingLeadEntryService service;

    @Test
    void createRejectsAnotherInFlightLeadWithOperatorVisibleConflict() {
        MarketingLeadInfo existing = new MarketingLeadInfo();
        existing.setId(17L);
        existing.setLeadNo("LEAD_OLD");
        existing.setLeadStatus("IN_APPROVAL");
        existing.setEntryEmpId("EMP_OLD");
        existing.setEntryOrgId("ORG_OLD");
        when(leadMapper.selectActiveByCreditCode("91320100ABC1234567")).thenReturn(existing);

        LeadCreateRequest request = new LeadCreateRequest();
        request.setCustName("测试企业");
        request.setUnifiedCreditCode("91320100ABC1234567");

        var ex = assertThrows(RuntimeException.class,
                () -> service.createDraft(request, "EMP_NEW", "ORG_NEW"));

        assertEquals("MARKETING_LEAD_PENDING_DUPLICATE", ((com.bank.branch.platform.common.web.exception.BizException) ex).getCode());
        verify(leadMapper, never()).insert(any(MarketingLeadInfo.class));
    }

    @Test
    void updateOnlyAllowsDraftOwnedByCurrentEmployeeAndReleasesDedupOnCancel() {
        MarketingLeadInfo draft = new MarketingLeadInfo();
        draft.setId(9L);
        draft.setLeadStatus("DRAFT");
        draft.setEntryEmpId("EMP_1");
        draft.setUnifiedCreditCode("91320100ABC1234567");
        draft.setActiveDedupKey("91320100ABC1234567");
        when(leadMapper.selectForUpdate(9L)).thenReturn(draft);
        when(leadMapper.updateStatusIf(9L, "DRAFT", "CANCELLED", "EMP_1", null)).thenReturn(1);

        LeadUpdateRequest update = new LeadUpdateRequest();
        update.setCustName("已修改企业");
        MarketingLeadInfo result = service.updateDraft(9L, update, "EMP_1", "ORG_1");
        assertEquals("已修改企业", result.getCustName());
        verify(leadMapper).updateById(draft);

        service.cancel(9L, "EMP_1");
        assertEquals("CANCELLED", draft.getLeadStatus());
        assertEquals(null, draft.getActiveDedupKey());
    }

    @Test
    void createPersistsValidatedScopeTagsAndAttachments() {
        doAnswer(invocation -> {
            MarketingLeadInfo lead = invocation.getArgument(0);
            lead.setId(101L);
            return 1;
        }).when(leadMapper).insert(any(MarketingLeadInfo.class));
        UserDTO manager = enabledManager("EMP_2", "ORG_2");
        when(userApi.getUserByEmpId("EMP_2")).thenReturn(manager);
        when(userApi.getUserRoleCodes("EMP_2"))
                .thenReturn(Set.of(CustomerRoleCode.CUSTOMER_MARKETING_MANAGER));
        MarketingCustomerTag tag = usableTag(7L, "重点客户");
        when(tagMapper.selectById(7L)).thenReturn(tag);

        LeadCreateRequest request = baseRequest();
        request.setDistributionMode("SCOPE");
        request.setManagerEmpIds(List.of("EMP_2", "EMP_2"));
        request.setTagIds(List.of(7L, 7L));
        request.setAttachmentIds(List.of("FILE-1", "FILE-1"));

        MarketingLeadInfo result = service.createDraft(request, "EMP_1", "ORG_1");

        assertEquals(101L, result.getId());
        var scopeCaptor = org.mockito.ArgumentCaptor.forClass(MarketingLeadManagerScope.class);
        verify(managerScopeMapper).insert(scopeCaptor.capture());
        assertEquals("EMP_2", scopeCaptor.getValue().getManagerEmpId());
        assertEquals("ORG_2", scopeCaptor.getValue().getManagerOrgId());
        assertEquals("SCOPE", scopeCaptor.getValue().getAssignmentType());
        assertEquals(0, scopeCaptor.getValue().getIsPrimary());
        var tagCaptor = org.mockito.ArgumentCaptor.forClass(MarketingLeadTagRel.class);
        verify(leadTagRelMapper).insert(tagCaptor.capture());
        assertEquals("重点客户", tagCaptor.getValue().getTagNameSnapshot());
        verify(fileApi).bindFile("LEAD", "101", "FILE-1", "ATTACHMENT");
    }

    @Test
    void createOwnerUsesExistingMainManagerSnapshotAsPrimaryReceiver() {
        doAnswer(invocation -> {
            MarketingLeadInfo lead = invocation.getArgument(0);
            lead.setId(102L);
            return 1;
        }).when(leadMapper).insert(any(MarketingLeadInfo.class));
        com.bank.branch.platform.customer.entity.marketing.MarketingCustomerInfo customer =
                new com.bank.branch.platform.customer.entity.marketing.MarketingCustomerInfo();
        customer.setId(9L);
        customer.setMainManagerId("OWNER_1");
        customer.setMainOrgId("ORG_OWNER");
        when(customerMapper.selectOne(any())).thenReturn(customer);
        when(userApi.getUserByEmpId("OWNER_1")).thenReturn(enabledManager("OWNER_1", "ORG_OWNER"));
        when(userApi.getUserRoleCodes("OWNER_1"))
                .thenReturn(Set.of(CustomerRoleCode.LEGACY_RELATIONSHIP_MANAGER));

        LeadCreateRequest request = baseRequest();
        request.setDistributionMode("PUBLIC");
        service.createDraft(request, "EMP_1", "ORG_1");

        var captor = org.mockito.ArgumentCaptor.forClass(MarketingLeadManagerScope.class);
        verify(managerScopeMapper).insert(captor.capture());
        assertEquals("OWNER_1", captor.getValue().getManagerEmpId());
        assertEquals("OWNER", captor.getValue().getAssignmentType());
        assertEquals(1, captor.getValue().getIsPrimary());
    }

    @Test
    void createPublicLeadDoesNotPersistAnyReceiver() {
        doAnswer(invocation -> {
            MarketingLeadInfo lead = invocation.getArgument(0);
            lead.setId(103L);
            return 1;
        }).when(leadMapper).insert(any(MarketingLeadInfo.class));
        LeadCreateRequest request = baseRequest();
        request.setDistributionMode("PUBLIC");

        service.createDraft(request, "EMP_1", "ORG_1");

        verify(managerScopeMapper, never()).insert(any(MarketingLeadManagerScope.class));
    }

    @Test
    void createDefaultsRequiredBooleanSnapshotsWhenCallerOmitsThem() {
        doAnswer(invocation -> {
            MarketingLeadInfo lead = invocation.getArgument(0);
            lead.setId(104L);
            return 1;
        }).when(leadMapper).insert(any(MarketingLeadInfo.class));

        MarketingLeadInfo result = service.createDraft(baseRequest(), "EMP_1", "ORG_1");

        assertEquals(0, result.getIsKeystone());
        assertEquals(1, result.getTouchRestricted());
    }

    @Test
    void createRejectsInvalidTagBeforeMainRowAndWritesNoRelationsOrAttachments() {
        when(tagMapper.selectById(99L)).thenReturn(null);
        LeadCreateRequest request = baseRequest();
        request.setTagIds(List.of(99L));
        request.setAttachmentIds(List.of("FILE-1"));

        var exception = assertThrows(com.bank.branch.platform.common.web.exception.BizException.class,
                () -> service.createDraft(request, "EMP_1", "ORG_1"));

        assertEquals("MARKETING_LEAD_TAG_INVALID", exception.getCode());
        verify(leadMapper, never()).insert(any(MarketingLeadInfo.class));
        verify(managerScopeMapper, never()).insert(any(MarketingLeadManagerScope.class));
        verify(leadTagRelMapper, never()).insert(any(MarketingLeadTagRel.class));
        verify(fileApi, never()).bindFile(any(), any(), any(), any());
    }

    @Test
    void lookupRequiresCustomerNameOrUnifiedCreditCode() {
        var exception = assertThrows(com.bank.branch.platform.common.web.exception.BizException.class,
                () -> service.lookupCustomer("  ", null));

        assertEquals("MARKETING_CUSTOMER_LOOKUP_REQUIRED", exception.getCode());
        verify(customerMapper, never()).selectActiveMatches(any(), any());
    }

    @Test
    void lookupPrioritizesCreditCodeAndEnrichesMainManagerNames() {
        com.bank.branch.platform.customer.entity.marketing.MarketingCustomerInfo customer =
                new com.bank.branch.platform.customer.entity.marketing.MarketingCustomerInfo();
        customer.setId(20L);
        customer.setCustName("信用代码命中企业");
        customer.setMainManagerId("OWNER_2");
        customer.setMainOrgId("ORG_2");
        when(customerMapper.selectActiveMatches("91320100ABC1234567", null))
                .thenReturn(List.of(customer));
        UserDTO owner = enabledManager("OWNER_2", "ORG_2");
        owner.setDisplayName("王经理");
        owner.setMainOrgName("公司业务部");
        when(userApi.getUserByEmpId("OWNER_2")).thenReturn(owner);

        MarketingCustomerSnapshot result = service.lookupCustomer(
                "另一个名称", " 91320100abc1234567 ");

        assertEquals(20L, result.getId());
        assertEquals("OWNER_2", result.getMainManagerId());
        assertEquals("王经理", result.getMainManagerName());
        assertEquals("公司业务部", result.getMainOrgName());
        verify(customerMapper).selectActiveMatches("91320100ABC1234567", null);
    }

    @Test
    void lookupByExactCustomerNameRejectsAmbiguousActiveCustomers() {
        com.bank.branch.platform.customer.entity.marketing.MarketingCustomerInfo first =
                new com.bank.branch.platform.customer.entity.marketing.MarketingCustomerInfo();
        com.bank.branch.platform.customer.entity.marketing.MarketingCustomerInfo second =
                new com.bank.branch.platform.customer.entity.marketing.MarketingCustomerInfo();
        when(customerMapper.selectActiveMatches(null, "重名企业"))
                .thenReturn(List.of(first, second));

        var exception = assertThrows(com.bank.branch.platform.common.web.exception.BizException.class,
                () -> service.lookupCustomer(" 重名企业 ", null));

        assertEquals("MARKETING_CUSTOMER_LOOKUP_AMBIGUOUS", exception.getCode());
    }

    @Test
    void updateCannotBypassExistingOwnershipByRequestingPublicDistribution() {
        MarketingLeadInfo draft = new MarketingLeadInfo();
        draft.setId(10L);
        draft.setLeadStatus("DRAFT");
        draft.setEntryEmpId("EMP_1");
        draft.setUnifiedCreditCode("91320100ABC1234567");
        draft.setDistributionMode("OWNER");
        draft.setMainManagerIdSnapshot("OWNER_1");
        draft.setMainOrgIdSnapshot("ORG_OWNER");
        when(leadMapper.selectForUpdate(10L)).thenReturn(draft);
        when(userApi.getUserByEmpId("OWNER_1")).thenReturn(enabledManager("OWNER_1", "ORG_OWNER"));
        when(userApi.getUserRoleCodes("OWNER_1"))
                .thenReturn(Set.of(CustomerRoleCode.LEGACY_RELATIONSHIP_MANAGER));
        LeadUpdateRequest request = new LeadUpdateRequest();
        request.setDistributionMode("PUBLIC");

        MarketingLeadInfo result = service.updateDraft(10L, request, "EMP_1", "ORG_1");

        assertEquals("OWNER", result.getDistributionMode());
        var captor = org.mockito.ArgumentCaptor.forClass(MarketingLeadManagerScope.class);
        verify(managerScopeMapper).insert(captor.capture());
        assertEquals("OWNER_1", captor.getValue().getManagerEmpId());
        assertEquals(1, captor.getValue().getIsPrimary());
    }

    @Test
    void updateRejectedLeadRestoresDraftAndClearsPreviousReviewMetadata() {
        MarketingLeadInfo rejected = new MarketingLeadInfo();
        rejected.setId(12L);
        rejected.setLeadStatus("REJECTED");
        rejected.setEntryEmpId("EMP_1");
        rejected.setUnifiedCreditCode("91320100ABC1234567");
        rejected.setActiveDedupKey(null);
        rejected.setSubmittedBy("EMP_1");
        rejected.setSubmittedTime(java.time.LocalDateTime.now().minusDays(1));
        rejected.setBusinessKey("LEAD:12");
        rejected.setProcessInstanceId("PROCESS_OLD");
        rejected.setReviewedBy("REVIEWER_1");
        rejected.setReviewedTime(java.time.LocalDateTime.now().minusHours(1));
        rejected.setRejectReason("资料不完整");
        rejected.setDistributionMode("PUBLIC");
        when(leadMapper.selectForUpdate(12L)).thenReturn(rejected);
        when(leadMapper.selectActiveByCreditCode("91320100ABC1234567")).thenReturn(null);
        when(leadMapper.clearApprovalMetadataForDraft(12L, "EMP_1")).thenReturn(1);
        LeadUpdateRequest request = new LeadUpdateRequest();
        request.setCustName("修改后企业");

        MarketingLeadInfo result = service.updateDraft(12L, request, "EMP_1", "ORG_1");

        assertEquals("DRAFT", result.getLeadStatus());
        assertEquals("91320100ABC1234567", result.getActiveDedupKey());
        assertEquals(null, result.getSubmittedBy());
        assertEquals(null, result.getSubmittedTime());
        assertEquals(null, result.getBusinessKey());
        assertEquals(null, result.getProcessInstanceId());
        assertEquals(null, result.getReviewedBy());
        assertEquals(null, result.getReviewedTime());
        assertEquals(null, result.getRejectReason());
        verify(leadMapper).updateById(rejected);
        verify(leadMapper).clearApprovalMetadataForDraft(12L, "EMP_1");
    }

    @Test
    void submitRejectedLeadRestoresDedupAndStartsAnewApprovalProcess() {
        MarketingLeadInfo rejected = new MarketingLeadInfo();
        rejected.setId(13L);
        rejected.setLeadNo("MLEAD_13");
        rejected.setLeadStatus("REJECTED");
        rejected.setEntryEmpId("EMP_1");
        rejected.setUnifiedCreditCode("91320100ABC1234567");
        rejected.setDistributionMode("PUBLIC");
        rejected.setReviewedBy("REVIEWER_1");
        rejected.setRejectReason("退回原因");
        when(leadMapper.selectForUpdate(13L)).thenReturn(rejected);
        when(leadMapper.selectForUpdateByCreditCode("91320100ABC1234567")).thenReturn(null);
        WorkflowLaunchResp launch = new WorkflowLaunchResp();
        launch.setProcessInstanceId("PROCESS_NEW");
        when(workflowApi.startProcess(any())).thenReturn(launch);
        when(leadMapper.prepareRejectedResubmission(
                eq(13L), eq("EMP_1"), any(), eq("LEAD:13"),
                eq("91320100ABC1234567"), eq("EMP_1"))).thenReturn(1);
        when(leadMapper.updateProcessInstanceId(13L, "PROCESS_NEW", "REJECTED", "EMP_1"))
                .thenReturn(1);
        when(leadMapper.updateStatusIf(13L, "REJECTED", "IN_APPROVAL", "EMP_1", null))
                .thenReturn(1);
        MarketingLeadInfo inApproval = new MarketingLeadInfo();
        inApproval.setId(13L);
        inApproval.setLeadStatus("IN_APPROVAL");
        when(leadMapper.selectActiveById(13L)).thenReturn(inApproval);

        var result = service.submit(13L, "EMP_1", "ORG_1");

        assertEquals("IN_APPROVAL", result.getLeadStatus());
        assertEquals("91320100ABC1234567", rejected.getActiveDedupKey());
        assertEquals("EMP_1", rejected.getSubmittedBy());
        assertEquals("LEAD:13", rejected.getBusinessKey());
        assertEquals(null, rejected.getProcessInstanceId());
        assertEquals(null, rejected.getReviewedBy());
        assertEquals(null, rejected.getRejectReason());
        verify(workflowApi).startProcess(any());
        verify(leadMapper).prepareRejectedResubmission(
                eq(13L), eq("EMP_1"), any(), eq("LEAD:13"),
                eq("91320100ABC1234567"), eq("EMP_1"));
        verify(leadMapper).updateProcessInstanceId(13L, "PROCESS_NEW", "REJECTED", "EMP_1");
        verify(leadMapper).updateStatusIf(13L, "REJECTED", "IN_APPROVAL", "EMP_1", null);
    }

    @Test
    void submitDraftWritesNewProcessInstanceIdBeforeStateTransition() {
        MarketingLeadInfo draft = new MarketingLeadInfo();
        draft.setId(14L);
        draft.setLeadNo("MLEAD_14");
        draft.setLeadStatus("DRAFT");
        draft.setEntryEmpId("EMP_1");
        draft.setUnifiedCreditCode("91320100ABC1234567");
        draft.setDistributionMode("PUBLIC");
        when(leadMapper.selectForUpdate(14L)).thenReturn(draft);
        when(leadMapper.selectForUpdateByCreditCode("91320100ABC1234567")).thenReturn(null);
        WorkflowLaunchResp launch = new WorkflowLaunchResp();
        launch.setProcessInstanceId("PROCESS_DRAFT");
        when(workflowApi.startProcess(any())).thenReturn(launch);
        when(leadMapper.updateProcessInstanceId(14L, "PROCESS_DRAFT", "DRAFT", "EMP_1"))
                .thenReturn(1);
        when(leadMapper.updateStatusIf(14L, "DRAFT", "IN_APPROVAL", "EMP_1", null))
                .thenReturn(1);

        var result = service.submit(14L, "EMP_1", "ORG_1");

        assertEquals("IN_APPROVAL", result.getLeadStatus());
        verify(leadMapper).updateById(draft);
        verify(leadMapper).updateProcessInstanceId(14L, "PROCESS_DRAFT", "DRAFT", "EMP_1");
        verify(leadMapper).updateStatusIf(14L, "DRAFT", "IN_APPROVAL", "EMP_1", null);
    }

    @Test
    void submitDraftDoesNotSwallowProcessInstanceWriteConflict() {
        MarketingLeadInfo draft = new MarketingLeadInfo();
        draft.setId(15L);
        draft.setLeadNo("MLEAD_15");
        draft.setLeadStatus("DRAFT");
        draft.setEntryEmpId("EMP_1");
        draft.setUnifiedCreditCode("91320100ABC1234567");
        draft.setDistributionMode("PUBLIC");
        when(leadMapper.selectForUpdate(15L)).thenReturn(draft);
        when(leadMapper.selectForUpdateByCreditCode("91320100ABC1234567")).thenReturn(null);
        WorkflowLaunchResp launch = new WorkflowLaunchResp();
        launch.setProcessInstanceId("PROCESS_NOT_PERSISTED");
        when(workflowApi.startProcess(any())).thenReturn(launch);
        when(leadMapper.updateProcessInstanceId(15L, "PROCESS_NOT_PERSISTED", "DRAFT", "EMP_1"))
                .thenReturn(0);

        var exception = assertThrows(com.bank.branch.platform.common.web.exception.BizException.class,
                () -> service.submit(15L, "EMP_1", "ORG_1"));

        assertEquals("MARKETING_LEAD_STATE_CONFLICT", exception.getCode());
        verify(leadMapper, never()).updateStatusIf(15L, "DRAFT", "IN_APPROVAL", "EMP_1", null);
    }

    @Test
    void updateReplacesScopeAndTagRelationsAndBindsAttachments() {
        MarketingLeadInfo draft = new MarketingLeadInfo();
        draft.setId(9L);
        draft.setLeadStatus("DRAFT");
        draft.setEntryEmpId("EMP_1");
        draft.setUnifiedCreditCode("91320100ABC1234567");
        draft.setDistributionMode("SCOPE");
        when(leadMapper.selectForUpdate(9L)).thenReturn(draft);
        when(userApi.getUserByEmpId("EMP_3")).thenReturn(enabledManager("EMP_3", "ORG_3"));
        when(userApi.getUserRoleCodes("EMP_3"))
                .thenReturn(Set.of(CustomerRoleCode.CUSTOMER_MARKETING_MANAGER));
        when(tagMapper.selectById(8L)).thenReturn(usableTag(8L, "高潜客户"));
        LeadUpdateRequest request = new LeadUpdateRequest();
        request.setDistributionMode("SCOPE");
        request.setManagerEmpIds(List.of("EMP_3"));
        request.setTagIds(List.of(8L));
        request.setAttachmentIds(List.of("FILE-2"));

        service.updateDraft(9L, request, "EMP_1", "ORG_1");

        verify(managerScopeMapper).delete(any());
        verify(leadTagRelMapper).delete(any());
        verify(managerScopeMapper).insert(any(MarketingLeadManagerScope.class));
        verify(leadTagRelMapper).insert(any(MarketingLeadTagRel.class));
        verify(fileApi).bindFile("LEAD", "9", "FILE-2", "ATTACHMENT");
    }

    @Test
    void detailReturnsReceiverTagAndAttachmentIdsForEditEcho() {
        MarketingLeadInfo draft = new MarketingLeadInfo();
        draft.setId(9L);
        draft.setLeadStatus("DRAFT");
        draft.setEntryEmpId("EMP_1");
        when(leadMapper.selectActiveById(9L)).thenReturn(draft);
        MarketingLeadManagerScope scope = new MarketingLeadManagerScope();
        scope.setManagerEmpId("EMP_2");
        when(managerScopeMapper.selectList(any())).thenReturn(List.of(scope));
        MarketingLeadTagRel tagRel = new MarketingLeadTagRel();
        tagRel.setTagId(7L);
        when(leadTagRelMapper.selectList(any())).thenReturn(List.of(tagRel));
        FileObjectDTO file = new FileObjectDTO();
        file.setId("FILE-1");
        when(fileApi.listBizFiles("LEAD", "9")).thenReturn(List.of(file));

        LeadDetailResponse detail = service.getDetail(9L, "EMP_1");

        assertEquals(List.of("EMP_2"), detail.getManagerEmpIds());
        assertEquals(List.of(7L), detail.getTagIds());
        assertEquals(List.of(file), detail.getAttachments());
    }

    private LeadCreateRequest baseRequest() {
        LeadCreateRequest request = new LeadCreateRequest();
        request.setCustName("测试企业");
        request.setUnifiedCreditCode("91320100ABC1234567");
        return request;
    }

    private UserDTO enabledManager(String empId, String orgId) {
        UserDTO user = new UserDTO();
        user.setEmpId(empId);
        user.setEnabled(true);
        user.setMainOrgCode(orgId);
        return user;
    }

    private MarketingCustomerTag usableTag(Long id, String name) {
        MarketingCustomerTag tag = new MarketingCustomerTag();
        tag.setId(id);
        tag.setTagName(name);
        tag.setRecordStatus("ACTIVE");
        tag.setApprovalStatus("APPROVED");
        tag.setStatus("ENABLED");
        return tag;
    }
}
