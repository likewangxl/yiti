package com.bank.branch.platform.customer.service;

import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.customer.entity.CustLead;
import com.bank.branch.platform.customer.entity.CustMaster;
import com.bank.branch.platform.customer.enums.CustomerErrorCode;
import com.bank.branch.platform.customer.enums.LeadOp;
import com.bank.branch.platform.customer.enums.LeadStatus;
import com.bank.branch.platform.customer.mapper.CustLeadMapper;
import com.bank.branch.platform.customer.mapper.CustMasterMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * LeadVersionService 单元测试（TDD）
 * 测试版本管理域：修改版本和删除版本的创建逻辑。
 */
@ExtendWith(MockitoExtension.class)
class LeadVersionServiceTest {

    @Mock
    private CustLeadMapper leadMapper;

    @Mock
    private CustMasterMapper masterMapper;

    @InjectMocks
    private LeadVersionService leadVersionService;

    // ==================== createEditVersion ====================

    @Test
    void createEditVersion_shouldCreateNewVersionFromApproved() {
        // given: 存在已审批通过的线索版本（is_latest=1）
        CustLead latestLead = buildApprovedLead("lead-001", "cust-001", 1);
        CustMaster master = buildMaster("cust-001");

        when(masterMapper.selectById("cust-001")).thenReturn(master);
        when(leadMapper.selectLatestBySourceCustId("cust-001")).thenReturn(latestLead);
        when(leadMapper.updateById(any(CustLead.class))).thenReturn(1);
        when(leadMapper.insert(any(CustLead.class))).thenReturn(1);

        // when
        CustLead result = leadVersionService.createEditVersion(
                "cust-001",
                "更新客户名", null, null, null,
                null, null, null, null, null, null, null,
                null, null, null, null, null, null, null,
                "E001", "ORG001"
        );

        // then: 新版本 leadOp=UPDATE，versionNo=2，isLatest=1
        assertThat(result).isNotNull();
        assertThat(result.getLeadOp()).isEqualTo(LeadOp.UPDATE.getCode());
        assertThat(result.getVersionNo()).isEqualTo(2);
        assertThat(result.getIsLatest()).isEqualTo(1);
        assertThat(result.getSourceCustId()).isEqualTo("cust-001");
        assertThat(result.getPrevLeadId()).isEqualTo("lead-001");
        assertThat(result.getLeadStatus()).isEqualTo(LeadStatus.DRAFT.getCode());

        // 旧版本 isLatest 被置为 0
        ArgumentCaptor<CustLead> updateCaptor = ArgumentCaptor.forClass(CustLead.class);
        verify(leadMapper).updateById(updateCaptor.capture());
        assertThat(updateCaptor.getValue().getIsLatest()).isEqualTo(0);
        assertThat(updateCaptor.getValue().getId()).isEqualTo("lead-001");

        // 新版本被插入
        verify(leadMapper).insert(any(CustLead.class));
    }

    @Test
    void createEditVersion_shouldThrowWhenCustomerNotFound() {
        // given: 客户主档不存在
        when(masterMapper.selectById("not-exist")).thenReturn(null);

        // when/then
        assertThatThrownBy(() -> leadVersionService.createEditVersion(
                "not-exist",
                "更新客户名", null, null, null,
                null, null, null, null, null, null, null,
                null, null, null, null, null, null, null,
                "E001", "ORG001"
        ))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", CustomerErrorCode.CUSTOMER_NOT_FOUND.getCode());
    }

    @Test
    void createEditVersion_shouldOverrideTouchRestrictionWhenProvided() {
        CustLead latestLead = buildApprovedLead("lead-001", "cust-001", 1);
        CustMaster master = buildMaster("cust-001");
        master.setTouchRestricted(1);
        when(masterMapper.selectById("cust-001")).thenReturn(master);
        when(leadMapper.selectLatestBySourceCustId("cust-001")).thenReturn(latestLead);
        when(leadMapper.updateById(any(CustLead.class))).thenReturn(1);
        when(leadMapper.insert(any(CustLead.class))).thenReturn(1);

        CustLead result = leadVersionService.createEditVersion(
                "cust-001",
                "更新客户名", null, null, null,
                null, null, null, null, null, null,
                null, 0, null,
                null, null, null, null, null, null,
                "E001", "ORG001");

        assertThat(result.getTouchRestricted()).isEqualTo(0);
    }

    // ==================== createDeleteVersion ====================

    @Test
    void createDeleteVersion_shouldCreateDeleteLeadFromApproved() {
        // given: 存在已审批通过的线索
        CustLead latestLead = buildApprovedLead("lead-001", "cust-001", 1);
        CustMaster master = buildMaster("cust-001");

        when(masterMapper.selectById("cust-001")).thenReturn(master);
        when(leadMapper.selectLatestBySourceCustId("cust-001")).thenReturn(latestLead);
        when(leadMapper.updateById(any(CustLead.class))).thenReturn(1);
        when(leadMapper.insert(any(CustLead.class))).thenReturn(1);

        // when
        CustLead result = leadVersionService.createDeleteVersion("cust-001", "E001", "ORG001");

        // then: 新版本 leadOp=DELETE，versionNo=2，isLatest=1，状态 DRAFT
        assertThat(result).isNotNull();
        assertThat(result.getLeadOp()).isEqualTo(LeadOp.DELETE.getCode());
        assertThat(result.getVersionNo()).isEqualTo(2);
        assertThat(result.getIsLatest()).isEqualTo(1);
        assertThat(result.getSourceCustId()).isEqualTo("cust-001");
        assertThat(result.getLeadStatus()).isEqualTo(LeadStatus.DRAFT.getCode());

        // 旧版本 isLatest 被置为 0
        ArgumentCaptor<CustLead> updateCaptor = ArgumentCaptor.forClass(CustLead.class);
        verify(leadMapper).updateById(updateCaptor.capture());
        assertThat(updateCaptor.getValue().getIsLatest()).isEqualTo(0);

        verify(leadMapper).insert(any(CustLead.class));
    }

    @Test
    void createDeleteVersion_shouldThrowWhenCustomerNotFound() {
        // given: 客户主档不存在
        when(masterMapper.selectById("not-exist")).thenReturn(null);

        // when/then
        assertThatThrownBy(() -> leadVersionService.createDeleteVersion("not-exist", "E001", "ORG001"))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("code", CustomerErrorCode.CUSTOMER_NOT_FOUND.getCode());
    }

    // ============================= 辅助方法 =============================

    private CustLead buildApprovedLead(String id, String sourceCustId, int versionNo) {
        CustLead lead = new CustLead();
        lead.setId(id);
        lead.setLeadNo("LEAD_20260414_0001");
        lead.setLeadStatus(LeadStatus.APPROVED.getCode());
        lead.setLeadOp(LeadOp.CREATE.getCode());
        lead.setVersionNo(versionNo);
        lead.setIsLatest(1);
        lead.setSourceCustId(sourceCustId);
        lead.setCustName("测试企业");
        lead.setUnifiedCreditCode("91110000123456789X");
        lead.setCreatedBy("E001");
        lead.setOwnerOrgId("ORG001");
        lead.setDeleted(0);
        return lead;
    }

    private CustMaster buildMaster(String id) {
        CustMaster master = new CustMaster();
        master.setId(id);
        master.setCustName("测试企业");
        master.setUnifiedCreditCode("91110000123456789X");
        master.setStatus("ACTIVE");
        master.setDeleted(0);
        return master;
    }
}
