package com.bank.branch.platform.it;

import com.bank.branch.platform.bizapp.api.BizApplyQueryApi;
import com.bank.branch.platform.bizapp.api.SupportApi;
import com.bank.branch.platform.bizapp.dto.resp.SupportRequestCreateRespDTO;
import com.bank.branch.platform.bizapp.service.SupportService;
import com.bank.branch.platform.customer.api.CustomerQueryApi;
import com.bank.branch.platform.customer.api.TouchTaskQueryApi;
import com.bank.branch.platform.it.config.TestMockConfig;
import com.bank.branch.platform.portal.api.ProductApi;
import com.bank.branch.platform.portal.api.dto.ProductDTO;
import com.bank.branch.platform.workflow.api.WorkflowApi;
import com.bank.branch.platform.workflow.api.dto.StartProcessCmd;
import com.bank.branch.platform.workflow.api.dto.WorkflowLaunchResp;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * business-application-center 在 bootstrap 下的最小集成闭环验证。
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestMockConfig.class)
@Sql(scripts = {"/business-application-schema.sql", "/business-application-data.sql"})
class BusinessApplicationCenterIT {

    private static final String OPERATOR_EMP_ID = "user001";
    private static final String OPERATOR_ORG_ID = "BJ_CY";
    private static final String CUSTOMER_ID = "CUST_BIZ_IT_001";

    @Autowired
    private SupportService supportService;

    @Autowired
    private SupportApi supportApi;

    @Autowired
    private BizApplyQueryApi bizApplyQueryApi;

    @MockBean
    private WorkflowApi workflowApi;

    @MockBean
    private CustomerQueryApi customerQueryApi;

    @MockBean
    private TouchTaskQueryApi touchTaskQueryApi;

    @MockBean
    private ProductApi productApi;

    @Test
    @DisplayName("bizapp 集成 - 中场支持流程链路按既定流程定义发起")
    void supportWorkflowChains_useExpectedProcessDefinitionKeys() {
        when(customerQueryApi.isValidCustomer(CUSTOMER_ID)).thenReturn(true);
        when(customerQueryApi.isClaimedByOrg(CUSTOMER_ID, OPERATOR_ORG_ID)).thenReturn(true);

        ProductDTO product = ProductDTO.builder()
                .id("PROD_BIZ_IT_001")
                .productCode("PROD_BIZ_IT_001")
                .productName("供应链融资")
                .productCategory("CAT_LOAN")
                .supportForSupportRequest(true)
                .productDeptOrgCode("DEPT_BIZ_IT_001")
                .responsibleEmpIds(List.of("user002"))
                .status("ACTIVE")
                .build();
        when(productApi.getProduct("PROD_BIZ_IT_001")).thenReturn(Optional.of(product));
        when(productApi.getProductResponsibleEmpIds("PROD_BIZ_IT_001")).thenReturn(List.of("user002"));

        when(workflowApi.startProcess(any())).thenReturn(
                new WorkflowLaunchResp("PI_SUPPORT_SIMPLE_IT_001", "SUPPORT:support-simple-it", null),
                new WorkflowLaunchResp("PI_SUPPORT_COMPLEX_IT_001", "SUPPORT:support-complex-it", null)
        );

        SupportRequestCreateRespDTO.CreatedItem supportSimple = supportService.create(
                List.of("PROD_BIZ_IT_001"),
                CUSTOMER_ID,
                null,
                null,
                null,
                OPERATOR_EMP_ID,
                OPERATOR_ORG_ID
        ).getRequests().get(0);
        supportService.submit(supportSimple.getId(), OPERATOR_EMP_ID, OPERATOR_ORG_ID);

        SupportRequestCreateRespDTO.CreatedItem supportComplex = supportService.create(
                List.of(),
                CUSTOMER_ID,
                null,
                "需要中后台联合支持",
                "DEPT_BIZ_IT_001",
                OPERATOR_EMP_ID,
                OPERATOR_ORG_ID
        ).getRequests().get(0);
        supportService.submit(supportComplex.getId(), OPERATOR_EMP_ID, OPERATOR_ORG_ID);

        ArgumentCaptor<StartProcessCmd> processCaptor = ArgumentCaptor.forClass(StartProcessCmd.class);
        verify(workflowApi, times(2)).startProcess(processCaptor.capture());
        assertThat(processCaptor.getAllValues())
                .extracting(StartProcessCmd::getProcessDefinitionKey)
                .containsExactly("support_simple_v1", "support_complex_v1");

        assertThat(supportApi.getSupportRequest(supportSimple.getId())).isPresent();
        assertThat(supportApi.getSupportRequest(supportComplex.getId())).isPresent();
        assertThat(bizApplyQueryApi.countRunningApplications(CUSTOMER_ID))
                .satisfies(dto -> {
                    assertThat(dto.getRunningLoanCount()).isZero();
                    assertThat(dto.getRunningSupportCount()).isEqualTo(2L);
                });
    }
}
