package com.bank.branch.platform.customer.service;

import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.BizScopeApi;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.OrgDTO;
import com.bank.branch.platform.auth.api.dto.DataScopeContext;
import com.bank.branch.platform.auth.api.dto.UserDTO;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.security.enums.DataScopeType;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.customer.dto.req.LeadCreateReqDTO;
import com.bank.branch.platform.customer.dto.req.LeadUpdateReqDTO;
import com.bank.branch.platform.customer.entity.CustLead;
import com.bank.branch.platform.customer.entity.CustLeadManagerScope;
import com.bank.branch.platform.customer.entity.CustLeadTagRel;
import com.bank.branch.platform.customer.entity.CustMaster;
import com.bank.branch.platform.customer.entity.CustTag;
import com.bank.branch.platform.customer.dto.resp.LeadRespDTO;
import com.bank.branch.platform.customer.dto.resp.MainManagerLookupRespDTO;
import com.bank.branch.platform.customer.enums.CustomerErrorCode;
import com.bank.branch.platform.customer.mapper.CustLeadManagerScopeMapper;
import com.bank.branch.platform.customer.mapper.CustLeadMapper;
import com.bank.branch.platform.customer.mapper.CustLeadTagRelMapper;
import com.bank.branch.platform.customer.mapper.CustMasterMapper;
import com.bank.branch.platform.customer.mapper.CustTagMapper;
import com.bank.branch.platform.governance.api.FileApi;
import com.bank.branch.platform.governance.api.dto.FileObjectDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 0811 会议纪要一期：线索录入和分配规则。 */
@ExtendWith(MockitoExtension.class)
class LeadEntryServiceTest {

    @Mock private CustLeadMapper leadMapper;
    @Mock private CustLeadManagerScopeMapper managerScopeMapper;
    @Mock private CustLeadTagRelMapper leadTagRelMapper;
    @Mock private CustTagMapper tagMapper;
    @Mock private CustMasterMapper masterMapper;
    @Mock private OrgApi orgApi;
    @Mock private BizScopeApi bizScopeApi;
    @Mock private UserApi userApi;
    @Mock private FileApi fileApi;

    @InjectMocks private LeadEntryService service;

    @Test
    void createDraft_subbranchUserMustBeAssignedToSelf() {
        LeadCreateReqDTO req = baseRequest();
        req.setDistributionMode("PUBLIC");
        req.setAttachmentIds(List.of("FILE-1"));
        OrgDTO org = org("SUB001", 3);
        when(orgApi.getOrg("SUB001")).thenReturn(org);
        when(leadMapper.insert(any(CustLead.class))).thenReturn(1);
        when(managerScopeMapper.insert(any(CustLeadManagerScope.class))).thenReturn(1);

        CustLead result = service.createDraft(req, "E001", "SUB001", false);

        assertThat(result.getLeadType()).isEqualTo("NEW_ACCOUNT");
        assertThat(result.getDistributionMode()).isEqualTo("OWNER");
        assertThat(result.getMainManagerId()).isEqualTo("E001");
        assertThat(result.getMainManagerOrgId()).isEqualTo("SUB001");
        ArgumentCaptor<CustLeadManagerScope> scope = ArgumentCaptor.forClass(CustLeadManagerScope.class);
        verify(managerScopeMapper).insert(scope.capture());
        assertThat(scope.getValue().getManagerEmpId()).isEqualTo("E001");
        assertThat(scope.getValue().getAssignmentType()).isEqualTo("OWNER");
        assertThat(scope.getValue().getIsPrimary()).isEqualTo(1);
        verify(fileApi).bindFile("LEAD", result.getId(), "FILE-1", "ATTACHMENT");
    }

    @Test
    void createDraft_branchUserCanSpecifyManagerScope() {
        LeadCreateReqDTO req = baseRequest();
        req.setDistributionMode("SCOPE");
        req.setManagerScopeIds(List.of("RM001", "RM002"));
        when(orgApi.getOrg("BR001")).thenReturn(org("BR001", 2));
        mockManager("RM001", "BR001");
        mockManager("RM002", "BR002");
        when(leadMapper.insert(any(CustLead.class))).thenReturn(1);
        when(managerScopeMapper.insert(any(CustLeadManagerScope.class))).thenReturn(1);

        CustLead result = service.createDraft(req, "HEAD001", "BR001", false);

        assertThat(result.getDistributionMode()).isEqualTo("SCOPE");
        assertThat(result.getMainManagerId()).isNull();
        verify(managerScopeMapper, org.mockito.Mockito.times(2)).insert(any(CustLeadManagerScope.class));
    }

    @Test
    void createDraft_scopeModeRequiresAtLeastOneCustomerManager() {
        LeadCreateReqDTO req = baseRequest();
        req.setDistributionMode("SCOPE");
        req.setManagerScopeIds(List.of());
        when(orgApi.getOrg("BR001")).thenReturn(org("BR001", 2));

        assertThatThrownBy(() -> service.createDraft(req, "HEAD001", "BR001", false))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", CustomerErrorCode.LEAD_DISTRIBUTION_INVALID.getCode());
        verify(leadMapper, never()).insert(any(CustLead.class));
    }

    @Test
    void createDraft_rejectsDisabledTag() {
        LeadCreateReqDTO req = baseRequest();
        req.setTagIdList(List.of("TAG-DISABLED"));
        when(orgApi.getOrg("SUB001")).thenReturn(org("SUB001", 3));
        when(leadMapper.insert(any(CustLead.class))).thenReturn(1);
        when(managerScopeMapper.insert(any(CustLeadManagerScope.class))).thenReturn(1);
        CustTag disabled = new CustTag();
        disabled.setId("TAG-DISABLED");
        disabled.setStatus("DISABLED");
        disabled.setDeleted(0);
        when(tagMapper.selectById("TAG-DISABLED")).thenReturn(disabled);

        assertThatThrownBy(() -> service.createDraft(req, "E001", "SUB001", false))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", CustomerErrorCode.TAG_NOT_FOUND.getCode());
        verify(leadTagRelMapper, never()).insert(any(CustLeadTagRel.class));
    }

    @Test
    void updateDraft_rejectsLeadOutsideOperatorDataScope() {
        CustLead lead = new CustLead();
        lead.setId("LEAD-1");
        lead.setLeadStatus("DRAFT");
        lead.setCreatedBy("E-OTHER");
        lead.setOwnerOrgId("ORG-OTHER");
        when(leadMapper.selectById("LEAD-1")).thenReturn(lead);
        when(bizScopeApi.checkWritePermission(
                "E001", com.bank.branch.platform.common.security.enums.BizType.LEAD,
                "ORG-OTHER", "E-OTHER")).thenReturn(false);

        LeadUpdateReqDTO req = new LeadUpdateReqDTO();
        req.setCustName("越权修改");

        assertThatThrownBy(() -> service.updateDraft("LEAD-1", req, "E001", "ORG-001", false))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", CustomerErrorCode.LEAD_WRITE_FORBIDDEN.getCode());
        verify(leadMapper, never()).updateById(any(CustLead.class));
    }

    @Test
    void listCreatedPage_onlyQueriesLeadsEnteredByCurrentEmployee() {
        CustLead lead = new CustLead();
        lead.setId("LEAD-1");
        when(leadMapper.selectCreatedPage(null, null, null, "HEAD001", 0, 20))
                .thenReturn(List.of(lead));
        when(leadMapper.countCreatedPage(null, null, null, "HEAD001")).thenReturn(1L);

        PageResult<CustLead> result = service.listCreatedPage(
                null, null, null, 1, 20, "HEAD001");

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords()).extracting(CustLead::getId).containsExactly("LEAD-1");
        verify(bizScopeApi, never()).buildScopeContext(any(), any(), any());
    }

    @Test
    void getCreatedDetail_onlyReadsLeadEnteredByCurrentEmployee() {
        CustLead lead = new CustLead();
        lead.setId("LEAD-1");
        lead.setCreatedBy("E001");
        when(leadMapper.selectCreatedById("LEAD-1", "E001"))
                .thenReturn(lead);

        LeadRespDTO result = service.getCreatedDetail("LEAD-1", "E001");

        assertThat(result.getId()).isEqualTo("LEAD-1");
        verify(bizScopeApi, never()).buildScopeContext(any(), any(), any());
    }

    @Test
    void getDetail_containsFinancialDistributionTagsAndAttachments() {
        CustLead lead = new CustLead();
        lead.setId("LEAD-1");
        lead.setCustName("测试客户");
        lead.setCreditAmount(new java.math.BigDecimal("1000000"));
        lead.setCreditExposureAmount(new java.math.BigDecimal("300000"));
        lead.setDistributionMode("SCOPE");
        when(leadMapper.selectById("LEAD-1")).thenReturn(lead);

        CustLeadManagerScope scope = new CustLeadManagerScope();
        scope.setManagerEmpId("RM001");
        scope.setManagerOrgId("BR001");
        scope.setAssignmentType("SCOPE");
        when(managerScopeMapper.selectList(any())).thenReturn(List.of(scope));
        when(userApi.getUserName("RM001")).thenReturn("张经理");
        OrgDTO org = org("BR001", 2);
        org.setOrgName("西安分行");
        when(orgApi.getOrg("BR001")).thenReturn(org);

        CustLeadTagRel tag = new CustLeadTagRel();
        tag.setTagId("TAG-1");
        tag.setTagNameSnapshot("重点客户");
        when(leadTagRelMapper.selectList(any())).thenReturn(List.of(tag));
        FileObjectDTO file = new FileObjectDTO();
        file.setId("FILE-1");
        when(fileApi.listBizFiles("LEAD", "LEAD-1")).thenReturn(List.of(file));

        LeadRespDTO result = service.getDetail("LEAD-1");

        assertThat(result.getCreditAmount()).isEqualByComparingTo("1000000");
        assertThat(result.getCreditExposureAmount()).isEqualByComparingTo("300000");
        assertThat(result.getManagerScopes()).singleElement()
                .extracting("managerName", "managerOrgName")
                .containsExactly("张经理", "西安分行");
        assertThat(result.getTags()).singleElement().extracting("tagName").isEqualTo("重点客户");
        assertThat(result.getAttachments()).singleElement().extracting("id").isEqualTo("FILE-1");
    }

    @Test
    void lookupMainManager_returnsExistingCcrmOwnership() {
        CustMaster master = new CustMaster();
        master.setId("CUST-1");
        master.setCustNo("CCRM-001");
        master.setCustName("存量客户");
        master.setUnifiedCreditCode("91610131MA6U12345X");
        master.setMainManagerId("RM001");
        master.setMainOrgId("BR001");
        when(masterMapper.selectOne(any())).thenReturn(master);
        when(userApi.getUserName("RM001")).thenReturn("张经理");
        OrgDTO org = org("BR001", 2);
        org.setOrgName("西安分行");
        when(orgApi.getOrg("BR001")).thenReturn(org);

        MainManagerLookupRespDTO result = service.lookupMainManager("91610131MA6U12345X", null);

        assertThat(result.getExistingCustomer()).isTrue();
        assertThat(result.getHasMainOwnership()).isTrue();
        assertThat(result.getMainManagerId()).isEqualTo("RM001");
        assertThat(result.getMainManagerName()).isEqualTo("张经理");
    }

    private LeadCreateReqDTO baseRequest() {
        LeadCreateReqDTO req = new LeadCreateReqDTO();
        req.setCustName("西安测试科技有限公司");
        req.setUnifiedCreditCode("91610131MA6U12345X");
        return req;
    }

    private OrgDTO org(String code, int level) {
        OrgDTO dto = new OrgDTO();
        dto.setOrgCode(code);
        dto.setOrgLevel(level);
        return dto;
    }

    private void mockManager(String empId, String orgCode) {
        UserDTO user = new UserDTO();
        user.setEmpId(empId);
        user.setMainOrgCode(orgCode);
        user.setEnabled(true);
        when(userApi.getUserByEmpId(empId)).thenReturn(user);
        when(userApi.getUserRoleCodes(empId)).thenReturn(Set.of("CUST_MARKETING_MANAGER"));
    }
}
