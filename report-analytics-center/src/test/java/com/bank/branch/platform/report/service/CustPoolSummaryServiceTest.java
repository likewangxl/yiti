package com.bank.branch.platform.report.service;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.web.PageRequest;
import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.customer.api.CustomerQueryApi;
import com.bank.branch.platform.customer.api.dto.CustomerFilterDTO;
import com.bank.branch.platform.report.dto.req.CustPoolSummaryReqDTO;
import com.bank.branch.platform.report.dto.resp.CustPoolSummaryVO;
import com.bank.branch.platform.report.enums.RptErrorCode;
import com.bank.branch.platform.report.exception.RptException;
import com.bank.branch.platform.report.service.impl.CustPoolSummaryServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;

/**
 * CustPoolSummaryService 单元测试（Task M3.3.1，Red）.
 *
 * <p>覆盖 C.4 GET /customer-pool-summary 的 3 个分支：
 * <ol>
 *   <li>happy path：4 次 countCustomers 分别返回 total/VIP/NORMAL/POTENTIAL</li>
 *   <li>orgId 空 → 按当前用户 orgCode 兜底</li>
 *   <li>上游 countCustomers 异常 → 包装 RPT-50001</li>
 * </ol>
 *
 * <p>注：customer.CustomerQueryApi.countCustomers 实际签名 {@code (CustomerFilterDTO) → long}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CustPoolSummaryServiceTest {

    @Mock
    private CustomerQueryApi customerQueryApi;
    @Mock
    private CurrentUserApi currentUserApi;

    @InjectMocks
    private CustPoolSummaryServiceImpl service;

    @Test
    void getCustPoolSummary_happyPath_returnsBreakdown() {
        // Arrange：4 次 countCustomers 调用，按 customerTypes 区分
        when(customerQueryApi.countCustomers(argThat(filter -> filter != null
                && (filter.getCustomerTypes() == null || filter.getCustomerTypes().isEmpty()))))
                .thenReturn(100L);  // total（无 customerType filter）
        when(customerQueryApi.countCustomers(argThat(filter -> filter != null
                && filter.getCustomerTypes() != null && filter.getCustomerTypes().contains("VIP"))))
                .thenReturn(20L);
        when(customerQueryApi.countCustomers(argThat(filter -> filter != null
                && filter.getCustomerTypes() != null && filter.getCustomerTypes().contains("NORMAL"))))
                .thenReturn(60L);
        when(customerQueryApi.countCustomers(argThat(filter -> filter != null
                && filter.getCustomerTypes() != null && filter.getCustomerTypes().contains("POTENTIAL"))))
                .thenReturn(20L);

        CustPoolSummaryReqDTO req = new CustPoolSummaryReqDTO();
        req.setOrgId("BR001");

        PageResult<CustPoolSummaryVO> result = service.getCustPoolSummary(req, new PageRequest());

        assertThat(result.getRecords()).hasSize(1);
        CustPoolSummaryVO vo = result.getRecords().get(0);
        assertThat(vo.getOrgCode()).isEqualTo("BR001");
        assertThat(vo.getTotalCount()).isEqualTo(100L);
        assertThat(vo.getVipCount()).isEqualTo(20L);
        assertThat(vo.getNormalCount()).isEqualTo(60L);
        assertThat(vo.getPotentialCount()).isEqualTo(20L);
    }

    @Test
    void getCustPoolSummary_orgIdBlank_fallsBackToCurrentUserOrg() {
        when(currentUserApi.getCurrentOrgCode()).thenReturn("BR_CURR");
        when(customerQueryApi.countCustomers(any(CustomerFilterDTO.class))).thenReturn(0L);

        CustPoolSummaryReqDTO req = new CustPoolSummaryReqDTO();
        req.setOrgId(null);

        PageResult<CustPoolSummaryVO> result = service.getCustPoolSummary(req, new PageRequest());
        assertThat(result.getRecords().get(0).getOrgCode()).isEqualTo("BR_CURR");
    }

    @Test
    void getCustPoolSummary_upstreamThrows_wrapsAsRpt50001() {
        when(customerQueryApi.countCustomers(any(CustomerFilterDTO.class)))
                .thenThrow(new RuntimeException("upstream"));

        CustPoolSummaryReqDTO req = new CustPoolSummaryReqDTO();
        req.setOrgId("BR001");

        assertThatThrownBy(() -> service.getCustPoolSummary(req, new PageRequest()))
                .isInstanceOf(RptException.class)
                .satisfies(ex -> assertThat(((RptException) ex).getErrorCode())
                        .isEqualTo(RptErrorCode.CROSS_MODULE_CALL_FAILED));
    }
}
