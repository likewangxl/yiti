package com.bank.branch.platform.customer.service;

import com.bank.branch.platform.customer.entity.CustLead;
import com.bank.branch.platform.customer.entity.CustMaster;
import com.bank.branch.platform.customer.enums.CustMasterStatus;
import com.bank.branch.platform.customer.enums.LeadOp;
import com.bank.branch.platform.customer.event.CustomerDeletedEvent;
import com.bank.branch.platform.customer.mapper.CustLeadMapper;
import com.bank.branch.platform.customer.mapper.CustMasterMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    private ApplicationEventPublisher eventPublisher;

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
        lead.setCustomerDesc("测试客户描述");
        lead.setCreditAmount(new BigDecimal("1000000.00"));
        lead.setCreditExposureAmount(new BigDecimal("500000.00"));
        lead.setOwnerOrgId("ORG_SZ_001");
        lead.setCreatedBy("E10001");
        return lead;
    }
}
