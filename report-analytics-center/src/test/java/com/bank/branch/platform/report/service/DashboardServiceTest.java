package com.bank.branch.platform.report.service;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.dto.OrgDTO;
import com.bank.branch.platform.customer.api.CustomerQueryApi;
import com.bank.branch.platform.customer.api.dto.CustomerDTO;
import com.bank.branch.platform.governance.api.AuditApi;
import com.bank.branch.platform.performance.api.KpiApi;
import com.bank.branch.platform.performance.api.MetricApi;
import com.bank.branch.platform.report.config.DashboardPresidentMetrics;
import com.bank.branch.platform.report.dto.resp.KpiCardItem;
import com.bank.branch.platform.report.dto.resp.PresidentDashboardRespDTO;
import com.bank.branch.platform.report.enums.RptErrorCode;
import com.bank.branch.platform.report.exception.RptException;
import com.bank.branch.platform.report.service.impl.DashboardServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * {@link DashboardService} 单元测试（Task M2.2.1，Red）.
 *
 * <p>覆盖：
 * <ol>
 *   <li>R_PRESIDENT 角色通过 → 返回 PresidentDashboardRespDTO 含 5 区块字段</li>
 *   <li>非 R_PRESIDENT 角色 → RPT-40301 DASHBOARD_NO_ACCESS</li>
 *   <li>dataDate 为 null 时回填默认值（today，V1 简化）</li>
 * </ol>
 *
 * <p>对照 plan L1684-L1735，mock 链路：CurrentUserApi(R_PRESIDENT) + OrgApi.getOrgSubtreeCodes
 * + MetricApi.getOrgMetricValues + CustomerQueryApi.searchCustomers + AuditApi.log（异步包装）.
 *
 * <p>{@code @MockitoSettings(strictness=LENIENT)}：仪表盘装配会按 12 个月循环 mock，
 * 部分 stub 路径在某测试中可能未触发，避免 UnnecessaryStubbingException 干扰。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DashboardServiceTest {

    @Mock
    CurrentUserApi currentUserApi;
    @Mock
    OrgApi orgApi;
    @Mock
    MetricApi metricApi;
    @Mock
    CustomerQueryApi customerQueryApi;
    @Mock
    AuditApi auditApi;
    @Mock
    KpiApi kpiApi;

    @InjectMocks
    DashboardServiceImpl service;

    @Test
    void getPresidentDashboard_withRoleR_PRESIDENT_returnsAll5Sections() {
        // 1) Arrange: R_PRESIDENT 角色 + ORG001 + 子机构集合
        // 生产代码用 ROLE_ID 校验（"R_PRESIDENT"），早期 mock 误用 getCurrentRoleCodes
        when(currentUserApi.getCurrentRoleIds()).thenReturn(Set.of("R_PRESIDENT"));
        when(currentUserApi.getCurrentEmpId()).thenReturn("E_PRES_001");
        when(currentUserApi.getCurrentOrgCode()).thenReturn("ORG001");
        when(orgApi.getOrgSubtreeCodes("ORG001")).thenReturn(Set.of("ORG001", "ORG002", "ORG003"));

        OrgDTO org001 = new OrgDTO();
        org001.setOrgCode("ORG001");
        org001.setOrgName("总行");
        OrgDTO org002 = new OrgDTO();
        org002.setOrgCode("ORG002");
        org002.setOrgName("XX 支行");
        OrgDTO org003 = new OrgDTO();
        org003.setOrgCode("ORG003");
        org003.setOrgName("YY 支行");
        lenient().when(orgApi.getOrg("ORG001")).thenReturn(org001);
        lenient().when(orgApi.getOrg("ORG002")).thenReturn(org002);
        lenient().when(orgApi.getOrg("ORG003")).thenReturn(org003);

        // 2) Arrange: CORE_METRICS 一次性查询返回 4 项
        Map<String, BigDecimal> coreValues = new HashMap<>();
        coreValues.put("DEP_BAL_ORG", new BigDecimal("1520.00"));
        coreValues.put("LOAN_BAL_ORG", new BigDecimal("980.50"));
        coreValues.put("NPL_RATIO_ORG", new BigDecimal("1.42"));
        coreValues.put("NEW_VALID_CUST_ORG_MONTH", new BigDecimal("428"));
        coreValues.put("FEE_INCOME_ORG_MONTH", new BigDecimal("12.80"));
        when(metricApi.getOrgMetricValues(eq("ORG001"), any(LocalDate.class),
                eq(DashboardPresidentMetrics.CORE_METRICS))).thenReturn(coreValues);

        // 3) Arrange: 趋势 / 排名 / Top 客户 走 MetricApi 单条循环（lenient mock 兜底）
        Map<String, BigDecimal> singleDep = new HashMap<>();
        singleDep.put("DEP_BAL_ORG", new BigDecimal("1500.00"));
        Map<String, BigDecimal> singleLoan = new HashMap<>();
        singleLoan.put("LOAN_BAL_ORG", new BigDecimal("950.00"));
        Map<String, BigDecimal> rankingValues = new HashMap<>();
        rankingValues.put("KPI_TOTAL_SCORE_ORG", new BigDecimal("88.5"));
        rankingValues.put("DEP_BAL_ORG", new BigDecimal("1520.00"));

        // 趋势 12 月循环 mock：所有 dataDate 都返回单条样本（lenient）
        lenient().when(metricApi.getOrgMetricValues(eq("ORG001"), any(LocalDate.class),
                eq(List.of("DEP_BAL_ORG")))).thenReturn(singleDep);
        lenient().when(metricApi.getOrgMetricValues(eq("ORG001"), any(LocalDate.class),
                eq(List.of("LOAN_BAL_ORG")))).thenReturn(singleLoan);
        // 子机构排行 mock
        lenient().when(metricApi.getOrgMetricValues(anyString(), any(LocalDate.class),
                eq(DashboardPresidentMetrics.RANKING_METRICS))).thenReturn(rankingValues);

        // 4) Arrange: Top 客户
        CustomerDTO c1 = new CustomerDTO();
        c1.setId("CUST001");
        c1.setCustName("张三");
        CustomerDTO c2 = new CustomerDTO();
        c2.setId("CUST002");
        c2.setCustName("李四");
        when(customerQueryApi.searchCustomers(any(), eq(10))).thenReturn(List.of(c1, c2));
        Map<String, BigDecimal> custValues = new HashMap<>();
        custValues.put("AUM_TOTAL_CUST", new BigDecimal("8000.00"));
        custValues.put("PROFIT_CONTRIB_CUST", new BigDecimal("98.50"));
        lenient().when(metricApi.getCustMetricValues(anyString(), any(LocalDate.class),
                eq(DashboardPresidentMetrics.CUST_CONTRIBUTION_METRICS))).thenReturn(custValues);

        // 5) Act
        LocalDate dataDate = LocalDate.parse("2026-04-09");
        PresidentDashboardRespDTO resp = service.getPresidentDashboard(null, dataDate);

        // 6) Assert：5 区块齐全
        assertThat(resp).isNotNull();
        assertThat(resp.getDataDate()).isEqualTo(dataDate);
        assertThat(resp.getSummaryMetrics())
                .containsKeys("DEP_BAL_ORG", "LOAN_BAL_ORG", "NPL_RATIO_ORG",
                        "FEE_INCOME_ORG_MONTH", "NEW_VALID_CUST_ORG_MONTH");
        assertThat(resp.getDepositTrend()).isNotNull();
        assertThat(resp.getDepositTrend().getXAxis()).hasSize(12);
        assertThat(resp.getLoanTrend()).isNotNull();
        assertThat(resp.getLoanTrend().getXAxis()).hasSize(12);
        assertThat(resp.getOrgRanking()).isNotNull();
        assertThat(resp.getTopCustomers()).isNotNull();

        // V1.14 # 2 stats 数组契约：5 项有序，metricCode/label/unit 与 KPI_CARD_METRICS 1:1 对齐
        List<KpiCardItem> stats = resp.getStats();
        assertThat(stats).hasSize(5);
        assertThat(stats).extracting(KpiCardItem::getMetricCode)
                .containsExactly("DEP_BAL_ORG", "LOAN_BAL_ORG", "NPL_RATIO_ORG",
                        "FEE_INCOME_ORG_MONTH", "NEW_VALID_CUST_ORG_MONTH");
        assertThat(stats).extracting(KpiCardItem::getLabel)
                .containsExactly("存款日均", "贷款余额", "不良贷款率",
                        "中间业务收入", "本月新增有效客户");
        assertThat(stats).extracting(KpiCardItem::getUnit)
                .containsExactly("亿", "亿", "%", "万", "");
        // value 透传 mock 数据
        assertThat(stats.get(0).getValue()).isEqualByComparingTo("1520.00");
        assertThat(stats.get(2).getValue()).isEqualByComparingTo("1.42");
        assertThat(stats.get(4).getValue()).isEqualByComparingTo("428");
    }

    @Test
    void getPresidentDashboard_missingMetricValues_statsDegrade_toNullValueAndDashTrend() {
        // 1) Arrange: R_PRESIDENT 通过权限，但 mock 仅 2 项有值，3 项 metric_code 上游缺失
        when(currentUserApi.getCurrentRoleIds()).thenReturn(Set.of("R_PRESIDENT"));
        when(currentUserApi.getCurrentEmpId()).thenReturn("E_PRES_002");
        when(currentUserApi.getCurrentOrgCode()).thenReturn("ORG001");
        when(orgApi.getOrgSubtreeCodes("ORG001")).thenReturn(Set.of("ORG001"));
        OrgDTO org001 = new OrgDTO();
        org001.setOrgCode("ORG001");
        org001.setOrgName("总行");
        when(orgApi.getOrg("ORG001")).thenReturn(org001);

        // 趋势/排名/客户路径先放 lenient 兜底空 Map（mockito 规则：后写覆盖前写，需保证精确 mock 在后）
        lenient().when(metricApi.getOrgMetricValues(anyString(), any(LocalDate.class), anyList()))
                .thenReturn(new HashMap<>());
        lenient().when(customerQueryApi.searchCustomers(any(), eq(10))).thenReturn(List.of());
        // 再放 strict 精确 mock：仅 2 项 metric_code 有值（缺 NPL_RATIO_ORG / FEE_INCOME_ORG_MONTH / NEW_VALID_CUST_ORG_MONTH）
        Map<String, BigDecimal> partial = new HashMap<>();
        partial.put("DEP_BAL_ORG", new BigDecimal("1500.00"));
        partial.put("LOAN_BAL_ORG", new BigDecimal("950.00"));
        when(metricApi.getOrgMetricValues(eq("ORG001"), any(LocalDate.class),
                eq(DashboardPresidentMetrics.CORE_METRICS))).thenReturn(partial);

        // 2) Act
        PresidentDashboardRespDTO resp = service.getPresidentDashboard(null, LocalDate.parse("2026-04-09"));

        // 3) Assert: 5 项 stats 仍齐全，缺值 3 项 value=null trend="--" trendType="flat"
        List<KpiCardItem> stats = resp.getStats();
        assertThat(stats).hasSize(5);
        // DEP_BAL_ORG / LOAN_BAL_ORG 有值
        assertThat(stats.get(0).getValue()).isEqualByComparingTo("1500.00");
        assertThat(stats.get(1).getValue()).isEqualByComparingTo("950.00");
        // NPL_RATIO_ORG / FEE_INCOME_ORG_MONTH / NEW_VALID_CUST_ORG_MONTH 缺
        assertThat(stats.get(2).getValue()).isNull();
        assertThat(stats.get(2).getTrend()).isEqualTo("--");
        assertThat(stats.get(2).getTrendType()).isEqualTo("flat");
        assertThat(stats.get(3).getValue()).isNull();
        assertThat(stats.get(3).getTrend()).isEqualTo("--");
        assertThat(stats.get(4).getValue()).isNull();
        assertThat(stats.get(4).getTrend()).isEqualTo("--");
        // 缺值时 label/unit 仍由 KPI_CARD_METRICS 填充
        assertThat(stats.get(2).getLabel()).isEqualTo("不良贷款率");
        assertThat(stats.get(2).getUnit()).isEqualTo("%");
    }

    // 已去掉服务层角色白名单校验：访问控制交由菜单授权（有"行长仪表盘"菜单即可看），
    // 故原 getPresidentDashboard_withoutRolePresident_throwsRpt40301 用例删除。

    @Test
    void getPresidentDashboard_withNullDataDate_fallsBackToToday() {
        // dataDate 默认值兜底（V1 简化为当天）
        when(currentUserApi.getCurrentRoleIds()).thenReturn(Set.of("R_PRESIDENT"));
        when(currentUserApi.getCurrentOrgCode()).thenReturn("ORG001");
        when(orgApi.getOrgSubtreeCodes("ORG001")).thenReturn(Set.of("ORG001"));
        when(metricApi.getOrgMetricValues(anyString(), any(LocalDate.class), anyList()))
                .thenReturn(Map.of());
        lenient().when(customerQueryApi.searchCustomers(any(), eq(10))).thenReturn(List.of());

        PresidentDashboardRespDTO resp = service.getPresidentDashboard(null, null);

        assertThat(resp.getDataDate()).isNotNull();
        assertThat(resp.getDataDate()).isEqualTo(LocalDate.now());
    }
}
