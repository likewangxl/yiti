package com.bank.branch.platform.customer.service;

import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.customer.dto.asset.AssetProjectSaveRequest;
import com.bank.branch.platform.customer.entity.AssetProjectApply;
import com.bank.branch.platform.customer.entity.marketing.MarketingCustomerInfo;
import com.bank.branch.platform.customer.mapper.AssetProjectApplyMapper;
import com.bank.branch.platform.customer.mapper.AssetProjectUrgentApplyMapper;
import com.bank.branch.platform.customer.mapper.TouchTaskMapper;
import com.bank.branch.platform.customer.mapper.TouchWorklogMapper;
import com.bank.branch.platform.customer.mapper.marketing.MarketingCustomerInfoMapper;
import com.bank.branch.platform.governance.api.FileApi;
import com.bank.branch.platform.workflow.api.WorkflowApi;
import com.bank.branch.platform.workflow.api.WorkflowQueryApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetProjectServiceTest {
    @Mock private AssetProjectApplyMapper applyMapper;
    @Mock private AssetProjectUrgentApplyMapper urgentMapper;
    @Mock private MarketingCustomerInfoMapper customerMapper;
    @Mock private TouchTaskMapper touchTaskMapper;
    @Mock private TouchWorklogMapper worklogMapper;
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

        var result = service.create(request, "E001", "ORG001");

        assertThat(result.getId()).isEqualTo(9001L);
        assertThat(result.getStatus()).isEqualTo("DRAFT");
        assertThat(result.getCustomerName()).isEqualTo("示例客户");
        verify(fileApi).bindFile("ASSET_PROJECT", "9001", "file-1", "ATTACHMENT");
    }

    @Test
    void create_projectLoanExceedsInvestment_shouldRejectBeforeInsert() {
        AssetProjectSaveRequest request = validRequest();
        request.setProjectTotalInvestment(new BigDecimal("100000"));
        request.setProjectLoanAmount(new BigDecimal("100001"));
        when(customerMapper.selectActiveById(101L)).thenReturn(customer());

        assertThatThrownBy(() -> service.create(request, "E001", "ORG001"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("项目贷款金额不能超过项目总投资");
        verify(applyMapper, never()).insert(any(AssetProjectApply.class));
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
}
