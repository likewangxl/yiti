package com.bank.branch.platform.customer.service;

import com.bank.branch.platform.customer.entity.CustLead;
import com.bank.branch.platform.customer.entity.CustLeadManagerScope;
import com.bank.branch.platform.customer.entity.CustLeadTagRel;
import com.bank.branch.platform.customer.entity.CustMaster;
import com.bank.branch.platform.customer.entity.CustTagRel;
import com.bank.branch.platform.customer.entity.CustClaim;
import com.bank.branch.platform.customer.enums.CustMasterStatus;
import com.bank.branch.platform.customer.enums.LeadOp;
import com.bank.branch.platform.customer.event.CustomerDeletedEvent;
import com.bank.branch.platform.customer.mapper.CustLeadMapper;
import com.bank.branch.platform.customer.mapper.CustLeadManagerScopeMapper;
import com.bank.branch.platform.customer.mapper.CustLeadTagRelMapper;
import com.bank.branch.platform.customer.mapper.CustMasterMapper;
import com.bank.branch.platform.customer.mapper.CustClaimMapper;
import com.bank.branch.platform.customer.mapper.CustTagRelMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

/**
 * CustMasterAssemblerService 单元测试（TDD Red 阶段）
 * 验证从线索装配客户主档的三种操作：CREATE / UPDATE / DELETE
 */
@ExtendWith(MockitoExtension.class)
class CustMasterAssemblerServiceTest {

    @Mock
    private CustMasterMapper masterMapper;

    @Mock
    private CustLeadMapper leadMapper;

    @Mock
    private CustLeadManagerScopeMapper managerScopeMapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private CustClaimMapper claimMapper;

    @Mock
    private CustLeadTagRelMapper leadTagRelMapper;

    @Mock
    private CustTagRelMapper tagRelMapper;

    @Mock
    private TouchTaskService touchTaskService;

    @InjectMocks
    private CustMasterAssemblerService assemblerService;

    // ==================== CREATE 操作 ====================

    @Test
    void assembleFromLead_CREATE_shouldCreateNewCustomer() {
        // given: 一条 CREATE 操作的线索
        CustLead lead = buildLead("lead-001", LeadOp.CREATE.getCode(), null);
        when(masterMapper.insert(any(CustMaster.class))).thenReturn(1);

        // when
        assemblerService.assembleFromLead(lead);

        // then: 调用 insert，生成的客户主档包含必要字段
        ArgumentCaptor<CustMaster> captor = ArgumentCaptor.forClass(CustMaster.class);
        verify(masterMapper).insert(captor.capture());

        CustMaster created = captor.getValue();
        assertThat(created.getId()).isNotBlank();
        assertThat(created.getCustNo()).startsWith("CUST_");
        assertThat(created.getCustName()).isEqualTo(lead.getCustName());
        assertThat(created.getUnifiedCreditCode()).isEqualTo(lead.getUnifiedCreditCode());
        assertThat(created.getContactPerson()).isEqualTo(lead.getContactPerson());
        assertThat(created.getContactMobile()).isEqualTo(lead.getContactMobile());
        assertThat(created.getIndustry()).isEqualTo(lead.getIndustry());
        assertThat(created.getGroupType()).isEqualTo(lead.getGroupType());
        assertThat(created.getCustomerType()).isEqualTo(lead.getCustomerType());
        assertThat(created.getIsKeystone()).isEqualTo(lead.getIsKeystone());
        assertThat(created.getEnterpriseType()).isEqualTo(lead.getEnterpriseType());
        assertThat(created.getGroupName()).isEqualTo(lead.getGroupName());
        assertThat(created.getIsAccountOpened()).isEqualTo(lead.getIsAccountOpened());
        assertThat(created.getTouchRestricted()).isEqualTo(lead.getTouchRestricted());
        assertThat(created.getCustomerDesc()).isEqualTo(lead.getCustomerDesc());
        assertThat(created.getCreditAmount()).isEqualByComparingTo(lead.getCreditAmount());
        assertThat(created.getCreditExposureAmount()).isEqualByComparingTo(lead.getCreditExposureAmount());
        assertThat(created.getOwnerOrgId()).isEqualTo(lead.getOwnerOrgId());
        assertThat(created.getLeadId()).isEqualTo(lead.getId());
        assertThat(created.getStatus()).isEqualTo(CustMasterStatus.ACTIVE.getCode());
        assertThat(created.getDeleted()).isEqualTo(0);
        assertThat(created.getCreatedTime()).isNotNull();
        assertThat(created.getUpdatedTime()).isNotNull();
    }

    @Test
    void assembleFromLead_CREATE_shouldAppendLeadTagSnapshotToCustomerTags() {
        CustLead lead = buildLead("lead-tag", LeadOp.CREATE.getCode(), null);
        CustLeadTagRel snapshot = new CustLeadTagRel();
        snapshot.setLeadId(lead.getId());
        snapshot.setTagId("tag-stock");
        snapshot.setTagNameSnapshot("存量客户");
        when(leadTagRelMapper.selectList(any())).thenReturn(List.of(snapshot));
        when(tagRelMapper.selectByCustIdAndTagId(any(), org.mockito.ArgumentMatchers.eq("tag-stock")))
                .thenReturn(null);
        when(masterMapper.insert(any(CustMaster.class))).thenReturn(1);

        assemblerService.assembleFromLead(lead);

        ArgumentCaptor<CustTagRel> relationCaptor = ArgumentCaptor.forClass(CustTagRel.class);
        verify(tagRelMapper).insert(relationCaptor.capture());
        assertThat(relationCaptor.getValue().getCustId()).isNotBlank();
        assertThat(relationCaptor.getValue().getTagId()).isEqualTo("tag-stock");
        assertThat(relationCaptor.getValue().getActive()).isEqualTo(1);
    }

    @Test
    void assembleFromLead_UPDATE_shouldReactivateExistingLeadTagSnapshot() {
        String custId = "cust-tag-1";
        CustLead lead = buildLead("lead-tag-update", LeadOp.UPDATE.getCode(), custId);
        CustLeadTagRel snapshot = new CustLeadTagRel();
        snapshot.setLeadId(lead.getId());
        snapshot.setTagId("tag-stock");
        when(masterMapper.selectById(custId)).thenReturn(new CustMaster());
        when(leadTagRelMapper.selectList(any())).thenReturn(List.of(snapshot));
        CustTagRel existing = new CustTagRel();
        existing.setId("rel-1");
        existing.setCustId(custId);
        existing.setTagId("tag-stock");
        existing.setActive(0);
        when(tagRelMapper.selectByCustIdAndTagId(custId, "tag-stock")).thenReturn(existing);

        assemblerService.assembleFromLead(lead);

        ArgumentCaptor<CustTagRel> relationCaptor = ArgumentCaptor.forClass(CustTagRel.class);
        verify(tagRelMapper).updateById(relationCaptor.capture());
        assertThat(relationCaptor.getValue().getId()).isEqualTo("rel-1");
        assertThat(relationCaptor.getValue().getActive()).isEqualTo(1);
        assertThat(relationCaptor.getValue().getExpiredTime()).isNull();
    }

    @Test
    void assembleFromLead_OWNER_shouldCreateClaimAndFirstTouchTask() {
        CustLead lead = buildLead("lead-owner", LeadOp.CREATE.getCode(), null);
        lead.setDistributionMode("OWNER");
        lead.setMainManagerId("E20001");
        lead.setMainManagerOrgId("ORG_SH_001");
        when(masterMapper.insert(any(CustMaster.class))).thenReturn(1);
        when(claimMapper.insert(any(CustClaim.class))).thenReturn(1);

        assemblerService.assembleFromLead(lead);

        ArgumentCaptor<CustClaim> claimCaptor = ArgumentCaptor.forClass(CustClaim.class);
        verify(claimMapper).insert(claimCaptor.capture());
        CustClaim claim = claimCaptor.getValue();
        assertThat(claim.getClaimedBy()).isEqualTo("E20001");
        assertThat(claim.getOrgId()).isEqualTo("ORG_SH_001");
        verify(touchTaskService).createFirstTouchTask(
                claim.getCustId(), "ORG_SH_001", "E20001", null);
    }

    @Test
    void assembleFromLead_SCOPE_shouldCreateClaimsForEveryScopedManagerWithoutTouchTask() {
        CustLead lead = buildLead("lead-scope", LeadOp.CREATE.getCode(), null);
        lead.setDistributionMode("SCOPE");

        CustLeadManagerScope first = new CustLeadManagerScope();
        first.setLeadId(lead.getId());
        first.setManagerEmpId("E20001");
        first.setManagerOrgId("ORG_SH_001");
        first.setAssignmentType("SCOPE");
        CustLeadManagerScope second = new CustLeadManagerScope();
        second.setLeadId(lead.getId());
        second.setManagerEmpId("E20002");
        second.setManagerOrgId("ORG_SH_002");
        second.setAssignmentType("SCOPE");

        when(masterMapper.insert(any(CustMaster.class))).thenReturn(1);
        when(managerScopeMapper.selectList(any())).thenReturn(List.of(first, second));
        when(claimMapper.insert(any(CustClaim.class))).thenReturn(1);

        assemblerService.assembleFromLead(lead);

        ArgumentCaptor<CustClaim> claimCaptor = ArgumentCaptor.forClass(CustClaim.class);
        verify(claimMapper, times(2)).insert(claimCaptor.capture());
        assertThat(claimCaptor.getAllValues())
                .extracting(CustClaim::getClaimedBy, CustClaim::getOrgId, CustClaim::getClaimStatus)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("E20001", "ORG_SH_001", "CLAIMED"),
                        org.assertj.core.groups.Tuple.tuple("E20002", "ORG_SH_002", "CLAIMED"));
        verifyNoInteractions(touchTaskService);
    }

    // ==================== UPDATE 操作 ====================

    @Test
    void assembleFromLead_UPDATE_shouldUpdateExistingCustomer() {
        // given: 一条 UPDATE 操作的线索，sourceCustId 指向已有客户
        String custId = "cust-001";
        CustLead lead = buildLead("lead-002", LeadOp.UPDATE.getCode(), custId);

        CustMaster existing = new CustMaster();
        existing.setId(custId);
        existing.setCustName("老名称");
        existing.setStatus(CustMasterStatus.ACTIVE.getCode());
        existing.setDeleted(0);
        when(masterMapper.selectById(custId)).thenReturn(existing);
        when(masterMapper.updateById(any(CustMaster.class))).thenReturn(1);

        // when
        assemblerService.assembleFromLead(lead);

        // then: 调用 updateById，更新字段
        ArgumentCaptor<CustMaster> captor = ArgumentCaptor.forClass(CustMaster.class);
        verify(masterMapper).updateById(captor.capture());

        CustMaster updated = captor.getValue();
        assertThat(updated.getId()).isEqualTo(custId);
        assertThat(updated.getCustName()).isEqualTo(lead.getCustName());
        assertThat(updated.getUnifiedCreditCode()).isEqualTo(lead.getUnifiedCreditCode());
        assertThat(updated.getContactPerson()).isEqualTo(lead.getContactPerson());
        assertThat(updated.getContactMobile()).isEqualTo(lead.getContactMobile());
        assertThat(updated.getUpdatedTime()).isNotNull();
    }

    // ==================== DELETE 操作 ====================

    @Test
    void assembleFromLead_DELETE_shouldMarkInactive() {
        // given: 一条 DELETE 操作的线索，sourceCustId 指向已有客户
        String custId = "cust-002";
        CustLead lead = buildLead("lead-003", LeadOp.DELETE.getCode(), custId);

        CustMaster existing = new CustMaster();
        existing.setId(custId);
        existing.setCustNo("CUST_00001");
        existing.setStatus(CustMasterStatus.ACTIVE.getCode());
        existing.setDeleted(0);
        when(masterMapper.selectById(custId)).thenReturn(existing);
        when(masterMapper.updateById(any(CustMaster.class))).thenReturn(1);

        // when
        assemblerService.assembleFromLead(lead);

        // then: 调用 updateById，设置 status=INACTIVE + deleted=1
        ArgumentCaptor<CustMaster> masterCaptor = ArgumentCaptor.forClass(CustMaster.class);
        verify(masterMapper).updateById(masterCaptor.capture());

        CustMaster inactivated = masterCaptor.getValue();
        assertThat(inactivated.getId()).isEqualTo(custId);
        assertThat(inactivated.getStatus()).isEqualTo(CustMasterStatus.INACTIVE.getCode());
        assertThat(inactivated.getDeleted()).isEqualTo(1);

        // 同时发布 CustomerDeletedEvent
        ArgumentCaptor<CustomerDeletedEvent> eventCaptor = ArgumentCaptor.forClass(CustomerDeletedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        CustomerDeletedEvent event = eventCaptor.getValue();
        assertThat(event.getCustId()).isEqualTo(custId);
        assertThat(event.getCustNo()).isEqualTo(existing.getCustNo());
        assertThat(event.getOperatorEmpId()).isEqualTo(lead.getCreatedBy());
    }

    // ============================= 私有辅助方法 =============================

    /**
     * 构造测试用线索实体
     */
    private CustLead buildLead(String id, String leadOp, String sourceCustId) {
        CustLead lead = new CustLead();
        lead.setId(id);
        lead.setLeadNo("LEAD_" + id);
        lead.setLeadOp(leadOp);
        lead.setSourceCustId(sourceCustId);
        lead.setCustName("测试客户_" + id);
        lead.setUnifiedCreditCode("91310000MA1FL4LL3X");
        lead.setContactPerson("张三");
        lead.setContactMobile("13800138000");
        lead.setIndustry("FINANCE");
        lead.setGroupType("LISTED");
        lead.setCustomerType("CORP");
        lead.setIsKeystone(1);
        lead.setEnterpriseType("STATE_OWNED");
        lead.setGroupName("测试集团");
        lead.setIsAccountOpened(0);
        lead.setTouchRestricted(1);
        lead.setCustomerDesc("测试客户描述");
        lead.setCreditAmount(new BigDecimal("1000000.00"));
        lead.setCreditExposureAmount(new BigDecimal("500000.00"));
        lead.setOwnerOrgId("ORG_SZ_001");
        lead.setCreatedBy("E10001");
        return lead;
    }
}
