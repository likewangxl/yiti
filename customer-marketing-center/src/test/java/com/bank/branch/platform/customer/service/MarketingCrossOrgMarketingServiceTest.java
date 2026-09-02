package com.bank.branch.platform.customer.service;

import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.customer.dto.resp.CrossOrgApplyRespDTO;
import com.bank.branch.platform.customer.dto.resp.CrossOrgValidationRespDTO;
import com.bank.branch.platform.customer.entity.marketing.MarketingCrossOrgApply;
import com.bank.branch.platform.customer.entity.marketing.MarketingCrossOrgRule;
import com.bank.branch.platform.customer.entity.marketing.MarketingCustomerInfo;
import com.bank.branch.platform.customer.entity.marketing.MarketingCustomerPerformanceRelSnapshot;
import com.bank.branch.platform.customer.entity.TouchTask;
import com.bank.branch.platform.customer.mapper.marketing.MarketingCrossOrgApplyMapper;
import com.bank.branch.platform.customer.mapper.marketing.MarketingCrossOrgRuleMapper;
import com.bank.branch.platform.customer.mapper.marketing.MarketingCustomerInfoMapper;
import com.bank.branch.platform.customer.mapper.marketing.MarketingCustomerPerformanceRelSnapshotMapper;
import com.bank.branch.platform.customer.mapper.TouchTaskMapper;
import com.bank.branch.platform.governance.api.NotifyApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** MARKETING_CROSS_ORG_* 规则、状态和审批幂等契约。 */
@ExtendWith(MockitoExtension.class)
class MarketingCrossOrgMarketingServiceTest {

    @Mock
    private MarketingCrossOrgApplyMapper applyMapper;
    @Mock
    private MarketingCrossOrgRuleMapper ruleMapper;
    @Mock
    private MarketingCustomerInfoMapper customerMapper;
    @Mock
    private MarketingCustomerPerformanceRelSnapshotMapper performanceMapper;
    @Mock
    private TouchTaskMapper touchTaskMapper;
    @Mock
    private UserApi userApi;
    @Mock
    private OrgApi orgApi;
    @Mock
    private NotifyApi notifyApi;

    @InjectMocks
    private MarketingCrossOrgMarketingService service;

    @Test
    void validate_shouldUseMarketingSnapshotAndFailClosedForUnknownRule() {
        when(customerMapper.selectActiveById(101L)).thenReturn(customer(101L));
        MarketingCrossOrgRule unknown = new MarketingCrossOrgRule();
        unknown.setRuleCode("NEW_UNSUPPORTED_RULE");
        unknown.setRuleName("未知规则");
        unknown.setEnabled(1);
        unknown.setDataSource("MARKETING_CUSTOMER_INFO");
        unknown.setFailureMessage("规则未实现");
        when(ruleMapper.selectEnabledRules()).thenReturn(List.of(unknown));
        when(performanceMapper.selectActiveByCustId(101L)).thenReturn(List.of());

        CrossOrgValidationRespDTO result = service.validate(101L, "EMP-1", "ORG-2");

        assertThat(result.isValid()).isFalse();
        assertThat(result.getChecks()).singleElement().extracting(
                CrossOrgValidationRespDTO.CheckItem::isPassed).isEqualTo(false);
        verify(performanceMapper).selectActiveByCustId(101L);
    }

    @Test
    void create_shouldPersistInApprovalWithFourCheckSnapshots() {
        when(customerMapper.selectActiveById(101L)).thenReturn(customer(101L));
        when(ruleMapper.selectEnabledRules()).thenReturn(MarketingCrossOrgMarketingService.defaultRules());
        when(performanceMapper.selectActiveByCustId(101L)).thenReturn(List.of());
        when(applyMapper.countActiveByCustomerAndApplicant(101L, "EMP-1")).thenReturn(0L);
        when(applyMapper.insert(any(MarketingCrossOrgApply.class))).thenReturn(1);

        MarketingCrossOrgApply result = service.create(101L, "跨机构营销", "EMP-1", "ORG-2");

        assertThat(result.getStatus()).isEqualTo("IN_APPROVAL");
        assertThat(result.getApplicantNotMainCheck()).isEqualTo(1);
        assertThat(result.getMainOrgDifferentCheck()).isEqualTo(1);
        assertThat(result.getApplicantNoPerformanceCheck()).isEqualTo(1);
        assertThat(result.getApplicantOrgNoPerformanceCheck()).isEqualTo(1);
    }

    @Test
    void approve_shouldCreateOneTaskOnlyAfterCasApproval() {
        MarketingCrossOrgApply pending = new MarketingCrossOrgApply();
        pending.setId(9L);
        pending.setCustId(101L);
        pending.setApplicantEmpId("EMP-1");
        pending.setApplicantOrgId("ORG-2");
        pending.setStatus("IN_APPROVAL");
        when(applyMapper.selectById(9L)).thenReturn(pending);
        when(applyMapper.updateInApprovalToApproved(any())).thenReturn(1);
        when(touchTaskMapper.insert(any(TouchTask.class))).thenAnswer(invocation -> {
            TouchTask task = invocation.getArgument(0);
            task.setId(88L);
            return 1;
        });

        service.approve(9L, "REVIEWER", true);

        verify(touchTaskMapper).insert(any(TouchTask.class));
        verify(applyMapper).updateGeneratedTouchTask(9L, 88L);

        pending.setStatus("APPROVED");
        pending.setGeneratedTouchTaskId(88L);
        when(applyMapper.selectById(9L)).thenReturn(pending);
        service.approve(9L, "REVIEWER", true);
        verify(touchTaskMapper).insert(any(TouchTask.class));
    }

    @Test
    void get_shouldExposeCanReviewOnlyForReviewerAndInApproval() {
        MarketingCrossOrgApply pending = new MarketingCrossOrgApply();
        pending.setId(9L);
        pending.setApplicantEmpId("EMP-1");
        pending.setStatus("IN_APPROVAL");
        when(applyMapper.selectById(9L)).thenReturn(pending);

        CrossOrgApplyRespDTO result = service.get(9L, "REVIEWER", true);

        assertThat(result.isCanReview()).isTrue();
        assertThat(service.get(9L, "EMP-1", false).isCanReview()).isFalse();
    }

    private MarketingCustomerInfo customer(Long id) {
        MarketingCustomerInfo customer = new MarketingCustomerInfo();
        customer.setId(id);
        customer.setCustName("客户A");
        customer.setCustNo("C001");
        customer.setMainManagerId("EMP-9");
        customer.setMainOrgId("ORG-1");
        customer.setOwnershipStatus("ASSIGNED");
        customer.setRecordStatus("ACTIVE");
        return customer;
    }
}
