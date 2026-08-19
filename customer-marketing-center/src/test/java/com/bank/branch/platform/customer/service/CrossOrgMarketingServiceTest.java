package com.bank.branch.platform.customer.service;

import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.customer.dto.resp.CrossOrgValidationRespDTO;
import com.bank.branch.platform.customer.entity.CrossOrgMarketingApply;
import com.bank.branch.platform.customer.entity.CustMaster;
import com.bank.branch.platform.customer.entity.CustPerformanceRelationSnapshot;
import com.bank.branch.platform.customer.mapper.CrossOrgMarketingApplyMapper;
import com.bank.branch.platform.customer.mapper.CrossOrgMarketingRuleMapper;
import com.bank.branch.platform.customer.mapper.CustMasterMapper;
import com.bank.branch.platform.customer.mapper.CustPerformanceRelationSnapshotMapper;
import com.bank.branch.platform.governance.api.NotifyApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CrossOrgMarketingServiceTest {

    @Mock CrossOrgMarketingApplyMapper applyMapper;
    @Mock CrossOrgMarketingRuleMapper ruleMapper;
    @Mock CustMasterMapper masterMapper;
    @Mock CustPerformanceRelationSnapshotMapper performanceMapper;
    @Mock TouchTaskService touchTaskService;
    @Mock UserApi userApi;
    @Mock NotifyApi notifyApi;

    @Test
    void validate_shouldPassOnlyWhenAllFourConfiguredRulesPass() {
        CrossOrgMarketingService service = new CrossOrgMarketingService(
                applyMapper, ruleMapper, masterMapper, performanceMapper, touchTaskService, userApi, notifyApi);
        CustMaster customer = customer();
        when(masterMapper.selectById("C001")).thenReturn(customer);
        when(ruleMapper.selectEnabledRules()).thenReturn(CrossOrgMarketingService.defaultRules());
        when(performanceMapper.selectActiveByCustId("C001")).thenReturn(List.of());

        CrossOrgValidationRespDTO result = service.validate("C001", "E001", "ORG-A");

        assertThat(result.isValid()).isTrue();
        assertThat(result.getChecks()).hasSize(4).allMatch(CrossOrgValidationRespDTO.CheckItem::isPassed);
    }

    @Test
    void validate_shouldResolveCustomerNumberAndQueryPerformanceByCustomerId() {
        CrossOrgMarketingService service = new CrossOrgMarketingService(
                applyMapper, ruleMapper, masterMapper, performanceMapper, touchTaskService, userApi, notifyApi);
        CustMaster customer = customer();
        when(masterMapper.selectByCustNo("NO001")).thenReturn(customer);
        when(ruleMapper.selectEnabledRules()).thenReturn(CrossOrgMarketingService.defaultRules());
        when(performanceMapper.selectActiveByCustId("C001")).thenReturn(List.of());

        CrossOrgValidationRespDTO result = service.validate("NO001", "E001", "ORG-A");

        assertThat(result.getCustId()).isEqualTo("C001");
        verify(performanceMapper).selectActiveByCustId("C001");
    }

    @Test
    void approve_shouldBeIdempotentAndCreateTouchTaskForApplicant() {
        CrossOrgMarketingService service = new CrossOrgMarketingService(
                applyMapper, ruleMapper, masterMapper, performanceMapper, touchTaskService, userApi, notifyApi);
        CrossOrgMarketingApply pending = new CrossOrgMarketingApply();
        pending.setId("A001");
        pending.setCustId("C001");
        pending.setApplicantEmpId("E001");
        pending.setApplicantOrgId("ORG-A");
        pending.setStatus("PENDING");
        when(applyMapper.selectById("A001")).thenReturn(pending);
        when(applyMapper.updatePendingToApproved(any(CrossOrgMarketingApply.class))).thenReturn(1);
        when(touchTaskService.createFirstTouchTask("C001", "ORG-A", "E001", null))
                .thenReturn(new com.bank.branch.platform.customer.entity.TouchTask());

        service.approve("A001", "APPROVER");

        verify(touchTaskService).createFirstTouchTask("C001", "ORG-A", "E001", null);
        verify(applyMapper).updateGeneratedTask(any(), any());
    }

    private CustMaster customer() {
        CustMaster customer = new CustMaster();
        customer.setId("C001");
        customer.setCustNo("NO001");
        customer.setCustName("测试客户");
        customer.setMainManagerId("E999");
        customer.setMainOrgId("ORG-B");
        return customer;
    }
}
