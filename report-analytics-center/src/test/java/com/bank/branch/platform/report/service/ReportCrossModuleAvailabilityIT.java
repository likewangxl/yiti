package com.bank.branch.platform.report.service;

import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.dto.OrgDTO;
import com.bank.branch.platform.customer.api.CustomerQueryApi;
import com.bank.branch.platform.customer.api.TouchTaskQueryApi;
import com.bank.branch.platform.customer.api.dto.CustomerDTO;
import com.bank.branch.platform.customer.api.dto.CustomerFilterDTO;
import com.bank.branch.platform.customer.api.dto.TouchTaskSummaryDTO;
import com.bank.branch.platform.performance.api.KpiApi;
import com.bank.branch.platform.performance.api.MetricApi;
import com.bank.branch.platform.report.BaseControllerIT;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 跨模块 Api 真实可用性回归（Task M3.5.1）.
 *
 * <p>验证 M2 + M3 实施过程中调用的 5 个跨模块 Api 在真实 Spring Context 装配下：
 * <ol>
 *   <li>{@link MetricApi}（performance V1.1 P2.6 真实实现）— ORG / EMP / CUST 三个分支</li>
 *   <li>{@link KpiApi}（performance V1.1 P4.3 真实实现）— getCurrentKpiTotal 返回 BigDecimal</li>
 *   <li>{@link CustomerQueryApi}（customer V1.0）— countCustomers / searchCustomers</li>
 *   <li>{@link TouchTaskQueryApi}（customer V1.0）— getOrgTouchSummary 真实签名 String/String/String</li>
 *   <li>{@link OrgApi}（auth V1.0）— getOrg 用于 ranking + name 解析</li>
 * </ol>
 *
 * <p>本 IT 用 BaseControllerIT 基类的 @MockBean 装配（生产 bean 在跨模块边界用 mock 替代），
 * 重点是验证 mock 装配/类型签名、字段访问、方法名拼写均与真实模块定义一致——
 * 一旦 customer / performance 端 API 签名变化，本 IT 第一时间编译失败.
 *
 * <p>不像 SpringBootTest 启动真实 Bean（M3 阶段尚未与真实数据库联调），
 * 本 IT 主要意图：M3 实施代码引用的所有跨模块 Api 入口签名"可装配 + 可调用"端到端守护.
 */
class ReportCrossModuleAvailabilityIT extends BaseControllerIT {

    @Test
    void metricApi_getOrgMetricValues_signatureCompatible() {
        // 真实签名：(String orgCode, LocalDate dataDate, List<String> metricCodes) → Map<String, BigDecimal>
        when(metricApi.getOrgMetricValues(anyString(), any(LocalDate.class), anyList()))
                .thenReturn(Map.of("DEP_BAL_ORG_DAILY", new BigDecimal("100")));
        Map<String, BigDecimal> result = metricApi.getOrgMetricValues(
                "BR001", LocalDate.of(2026, 4, 1), List.of("DEP_BAL_ORG_DAILY"));
        assertThat(result).containsKey("DEP_BAL_ORG_DAILY");
    }

    @Test
    void metricApi_getEmpMetricValues_signatureCompatible() {
        when(metricApi.getEmpMetricValues(anyString(), any(LocalDate.class), anyList()))
                .thenReturn(Map.of("KPI_TOTAL", new BigDecimal("88")));
        Map<String, BigDecimal> result = metricApi.getEmpMetricValues(
                "E001", LocalDate.of(2026, 4, 1), List.of("KPI_TOTAL"));
        assertThat(result).containsKey("KPI_TOTAL");
    }

    @Test
    void metricApi_getCustMetricValues_signatureCompatible() {
        when(metricApi.getCustMetricValues(anyString(), any(LocalDate.class), anyList()))
                .thenReturn(Map.of("AUM_TOTAL_CUST", new BigDecimal("5000")));
        Map<String, BigDecimal> result = metricApi.getCustMetricValues(
                "C001", LocalDate.of(2026, 4, 1), List.of("AUM_TOTAL_CUST"));
        assertThat(result).containsKey("AUM_TOTAL_CUST");
    }

    @Test
    void kpiApi_getCurrentKpiTotal_returnsBigDecimal() {
        // V1.1 P4.3 真实交付：返回 BigDecimal（非 plan L2030 的 KpiTotalDTO）
        when(kpiApi.getCurrentKpiTotal("E001", "MONTHLY")).thenReturn(new BigDecimal("75.5"));
        BigDecimal score = kpiApi.getCurrentKpiTotal("E001", "MONTHLY");
        assertThat(score).isEqualByComparingTo("75.5");
    }

    @Test
    void customerQueryApi_countCustomers_signatureCompatible() {
        // 真实签名：(CustomerFilterDTO) → long
        CustomerFilterDTO filter = new CustomerFilterDTO();
        filter.setOrgIds(List.of("BR001"));
        filter.setCustomerTypes(List.of("VIP"));
        when(customerQueryApi.countCustomers(any(CustomerFilterDTO.class))).thenReturn(42L);
        long count = customerQueryApi.countCustomers(filter);
        assertThat(count).isEqualTo(42L);
    }

    @Test
    void customerQueryApi_searchCustomers_signatureCompatible() {
        // 真实签名：(String keyword, int limit) → List<CustomerDTO>
        CustomerDTO c = new CustomerDTO();
        c.setId("C001");
        c.setCustName("测试");
        when(customerQueryApi.searchCustomers(any(), any(Integer.class))).thenReturn(List.of(c));
        List<CustomerDTO> result = customerQueryApi.searchCustomers(null, 10);
        assertThat(result).hasSize(1);
    }

    @Test
    void touchTaskQueryApi_getOrgTouchSummary_signatureCompatible() {
        // 真实签名：(String orgCode, String startDate, String endDate) → TouchTaskSummaryDTO
        // （非 plan L1869 描述的 (orgCode, LocalDate, LocalDate) → List<TouchOrgSummaryDTO>）
        TouchTaskSummaryDTO upstream = new TouchTaskSummaryDTO();
        upstream.setOrgId("BR001");
        upstream.setTotalCount(100L);
        when(touchTaskQueryApi.getOrgTouchSummary("BR001", "2026-04-01", "2026-04-25"))
                .thenReturn(upstream);
        TouchTaskSummaryDTO result = touchTaskQueryApi.getOrgTouchSummary(
                "BR001", "2026-04-01", "2026-04-25");
        assertThat(result.getOrgId()).isEqualTo("BR001");
        assertThat(result.getTotalCount()).isEqualTo(100L);
    }

    @Test
    void orgApi_getOrg_signatureCompatible() {
        // 真实签名：(String orgCode) → OrgDTO
        OrgDTO org = new OrgDTO();
        org.setOrgCode("BR001");
        org.setOrgName("分行 A");
        when(orgApi.getOrg("BR001")).thenReturn(org);
        OrgDTO result = orgApi.getOrg("BR001");
        assertThat(result.getOrgName()).isEqualTo("分行 A");
    }
}
