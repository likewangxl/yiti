package com.bank.branch.platform.report.service;

import com.bank.branch.platform.auth.api.BizScopeApi;
import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.UserApi;
import com.bank.branch.platform.auth.api.dto.DataScopeContext;
import com.bank.branch.platform.auth.api.dto.OrgDTO;
import com.bank.branch.platform.auth.api.dto.UserDTO;
import com.bank.branch.platform.common.security.enums.BizAction;
import com.bank.branch.platform.common.security.enums.BizType;
import com.bank.branch.platform.common.security.enums.DataScopeType;
import com.bank.branch.platform.customer.api.CustomerQueryApi;
import com.bank.branch.platform.customer.api.dto.CustomerDTO;
import com.bank.branch.platform.performance.api.MetricApi;
import com.bank.branch.platform.performance.api.dto.MetricDefDTO;
import com.bank.branch.platform.report.dto.req.DynamicQueryReqDTO;
import com.bank.branch.platform.report.dto.resp.DynamicQueryRespDTO;
import com.bank.branch.platform.report.exception.RptException;
import com.bank.branch.platform.report.service.impl.DynamicQueryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * DynamicQueryService 单元测试（Task M1.2.1，Red）.
 *
 * <p>覆盖 A.2 POST /api/reports/dynamic-query 的分支：
 * <ul>
 *   <li>subject 不在数据范围（ALL 以外降级 SELF 命中不到） → RPT-40005</li>
 *   <li>invalid dim → RPT-40006</li>
 *   <li>happy path（EMP，ALL scope）→ 返回 columns + rows</li>
 * </ul>
 * <p>注：对象/指标个数上限（原 RPT-40007 / RPT-40008）已按业务要求取消。</p>
 */
@ExtendWith(MockitoExtension.class)
class DynamicQueryServiceTest {

    @Mock
    private BizScopeApi bizScopeApi;

    @Mock
    private CurrentUserApi currentUserApi;

    @Mock
    private MetricApi metricApi;

    @Mock
    private OrgApi orgApi;

    @Mock
    private CustomerQueryApi customerQueryApi;

    @Mock
    private UserApi userApi;

    @InjectMocks
    private DynamicQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        lenient().when(currentUserApi.getCurrentEmpId()).thenReturn("E001");
    }

    // 注：对象/指标个数上限（原 RPT-40007 / RPT-40008）已按业务要求取消，相关上限测试随之移除。

    @Test
    void execute_invalidDim_throwsRpt40006() {
        DynamicQueryReqDTO req = new DynamicQueryReqDTO();
        req.setDim("UNKNOWN");
        req.setSubjectIds(List.of("E001"));
        req.setMetricCodes(List.of("M1"));
        req.setDataDate(LocalDate.of(2026, 4, 1));

        assertThatThrownBy(() -> service.execute(req))
                .isInstanceOf(RptException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-40006");
    }

    @Test
    void execute_subjectOutOfScope_throwsRpt40005() {
        DynamicQueryReqDTO req = new DynamicQueryReqDTO();
        req.setDim("EMP");
        req.setSubjectIds(List.of("E_OTHER"));
        req.setMetricCodes(List.of("M1"));
        req.setDataDate(LocalDate.of(2026, 4, 1));

        // 配置 SELF 范围，scope.empId=E001，但请求查 E_OTHER → 应抛 RPT-40005
        // EMP 维度数据范围按维度分流到 REPORT_DYN_EMP
        DataScopeContext scope = new DataScopeContext(
                DataScopeType.SELF, "E001", "ORG001", Set.of(),
                BizType.REPORT_DYN_EMP, BizAction.LIST);
        when(bizScopeApi.buildScopeContext(eq("E001"), eq(BizType.REPORT_DYN_EMP), eq(BizAction.LIST)))
                .thenReturn(scope);

        assertThatThrownBy(() -> service.execute(req))
                .isInstanceOf(RptException.class)
                .hasFieldOrPropertyWithValue("code", "RPT-40005");
    }

    @Test
    void execute_happyPath_returnsColumnsAndRows() {
        DynamicQueryReqDTO req = new DynamicQueryReqDTO();
        req.setDim("EMP");
        req.setSubjectIds(new ArrayList<>(List.of("E001", "E002")));
        req.setMetricCodes(new ArrayList<>(List.of("M_DEPOSIT_BAL")));
        req.setDataDate(LocalDate.of(2026, 4, 1));

        // 1) ALL scope：全部放行
        DataScopeContext scope = new DataScopeContext(
                DataScopeType.ALL, "E001", "ORG001", Set.of(),
                BizType.REPORT, BizAction.LIST);
        when(bizScopeApi.buildScopeContext(any(), any(), any())).thenReturn(scope);

        // 2) MetricApi.getEmpMetricValues 返回单指标值
        when(metricApi.getEmpMetricValues(eq("E001"), any(LocalDate.class), anyList()))
                .thenReturn(Map.of("M_DEPOSIT_BAL", new BigDecimal("1000.00")));
        when(metricApi.getEmpMetricValues(eq("E002"), any(LocalDate.class), anyList()))
                .thenReturn(Map.of("M_DEPOSIT_BAL", new BigDecimal("2000.00")));

        // 3) getMetricDef 提供列定义
        MetricDefDTO def = new MetricDefDTO();
        def.setMetricCode("M_DEPOSIT_BAL");
        def.setMetricName("存款余额");
        when(metricApi.getMetricDef("M_DEPOSIT_BAL")).thenReturn(Optional.of(def));

        // 4) OrgApi.getUserMainOrg 用于解析 EMP subjectName（可通过 orgCode 退化到 empId）
        // lenient 兜底：测试不一定用到
        lenient().when(orgApi.getUserMainOrg(any())).thenReturn(null);

        DynamicQueryRespDTO resp = service.execute(req);

        assertThat(resp.getDim()).isEqualTo("EMP");
        assertThat(resp.getRowCount()).isEqualTo(2);
        assertThat(resp.getColumns()).hasSize(1);
        assertThat(resp.getColumns().get(0).getMetricCode()).isEqualTo("M_DEPOSIT_BAL");
        assertThat(resp.getRows()).hasSize(2);
        assertThat(resp.getRows().get(0))
                .containsEntry("subjectId", "E001")
                .containsEntry("M_DEPOSIT_BAL", new BigDecimal("1000.00"));
    }

    @Test
    void exportExcel_happyPath_returnsXlsxBytes() {
        DynamicQueryReqDTO req = new DynamicQueryReqDTO();
        req.setDim("EMP");
        req.setSubjectIds(new ArrayList<>(List.of("E001", "E002")));
        req.setMetricCodes(new ArrayList<>(List.of("M_DEPOSIT_BAL")));
        req.setDataDate(LocalDate.of(2026, 4, 1));

        DataScopeContext scope = new DataScopeContext(
                DataScopeType.ALL, "E001", "ORG001", Set.of(),
                BizType.REPORT, BizAction.LIST);
        when(bizScopeApi.buildScopeContext(any(), any(), any())).thenReturn(scope);
        when(metricApi.getEmpMetricValues(eq("E001"), any(LocalDate.class), anyList()))
                .thenReturn(Map.of("M_DEPOSIT_BAL", new BigDecimal("1000.00")));
        when(metricApi.getEmpMetricValues(eq("E002"), any(LocalDate.class), anyList()))
                .thenReturn(Map.of("M_DEPOSIT_BAL", new BigDecimal("2000.00")));
        MetricDefDTO def = new MetricDefDTO();
        def.setMetricCode("M_DEPOSIT_BAL");
        def.setMetricName("存款余额");
        when(metricApi.getMetricDef("M_DEPOSIT_BAL")).thenReturn(Optional.of(def));
        lenient().when(orgApi.getUserMainOrg(any())).thenReturn(null);

        byte[] bytes = service.exportExcel(req);

        // xlsx 本质是 zip，magic number 为 PK\x03\x04
        assertThat(bytes).isNotEmpty();
        assertThat(bytes[0]).isEqualTo((byte) 'P');
        assertThat(bytes[1]).isEqualTo((byte) 'K');
    }

    @Test
    void execute_custDim_happyPath_usesCustomerQueryApi() {
        DynamicQueryReqDTO req = new DynamicQueryReqDTO();
        req.setDim("CUST");
        req.setSubjectIds(List.of("C001"));
        req.setMetricCodes(List.of("M_CUST_AUM"));
        req.setDataDate(LocalDate.of(2026, 4, 1));

        DataScopeContext scope = new DataScopeContext(
                DataScopeType.ALL, "E001", "ORG001", Set.of(),
                BizType.REPORT, BizAction.LIST);
        when(bizScopeApi.buildScopeContext(any(), any(), any())).thenReturn(scope);

        when(metricApi.getCustMetricValues(eq("C001"), any(LocalDate.class), anyList()))
                .thenReturn(Map.of("M_CUST_AUM", new BigDecimal("5000.00")));

        CustomerDTO cust = new CustomerDTO();
        cust.setCustName("测试客户");
        when(customerQueryApi.getCustomer("C001")).thenReturn(Optional.of(cust));

        MetricDefDTO def = new MetricDefDTO();
        def.setMetricCode("M_CUST_AUM");
        def.setMetricName("客户 AUM");
        when(metricApi.getMetricDef("M_CUST_AUM")).thenReturn(Optional.of(def));

        DynamicQueryRespDTO resp = service.execute(req);

        assertThat(resp.getDim()).isEqualTo("CUST");
        assertThat(resp.getRows()).hasSize(1);
        assertThat(resp.getRows().get(0))
                .containsEntry("subjectId", "C001")
                .containsEntry("subjectName", "测试客户")
                .containsEntry("M_CUST_AUM", new BigDecimal("5000.00"));
    }

    @Test
    void execute_noSubjects_emp_enumeratesAllInScope() {
        // 不选对象：EMP 维度按数据范围枚举当天宽表全部员工（工号→USER_ID），再查值
        DynamicQueryReqDTO req = new DynamicQueryReqDTO();
        req.setDim("EMP");
        req.setSubjectIds(null);   // 不选对象
        req.setMetricCodes(List.of("M1"));
        req.setDataDate(LocalDate.of(2026, 4, 1));

        DataScopeContext scope = new DataScopeContext(
                DataScopeType.ALL, "E001", "ORG001", Set.of(), BizType.REPORT_DYN_EMP, BizAction.LIST);
        when(bizScopeApi.buildScopeContext(any(), any(), any())).thenReturn(scope);

        // 枚举：宽表当天有数据的工号
        when(metricApi.listEmpIdsWithData(any(LocalDate.class))).thenReturn(List.of("1001", "1002"));
        UserDTO u1 = new UserDTO(); u1.setEmpId("U1"); u1.setUsername("1001"); u1.setMainOrgCode("ORG001");
        UserDTO u2 = new UserDTO(); u2.setEmpId("U2"); u2.setUsername("1002"); u2.setMainOrgCode("ORG001");
        when(userApi.getUsersByUsernames(anyList())).thenReturn(List.of(u1, u2));
        lenient().when(userApi.getUserByEmpIds(anyList())).thenReturn(List.of(u1, u2));

        when(metricApi.getEmpMetricValues(eq("1001"), any(LocalDate.class), anyList()))
                .thenReturn(Map.of("M1", new BigDecimal("10")));
        when(metricApi.getEmpMetricValues(eq("1002"), any(LocalDate.class), anyList()))
                .thenReturn(Map.of("M1", new BigDecimal("20")));
        MetricDefDTO def = new MetricDefDTO(); def.setMetricCode("M1"); def.setMetricName("指标1");
        when(metricApi.getMetricDef("M1")).thenReturn(Optional.of(def));
        lenient().when(orgApi.getUserMainOrg(any())).thenReturn(null);

        DynamicQueryRespDTO resp = service.execute(req);

        assertThat(resp.getTotal()).isEqualTo(2);
        assertThat(resp.getRows()).hasSize(2);
        assertThat(resp.getRows().get(0)).containsEntry("subjectId", "1001");
    }

    @Test
    void execute_noSubjects_selfScope_showsSelfRowEvenWithoutData() {
        // SELF 范围默认查本人：即使本人当天宽表无指标数据，也要显示一行（指标值为空），而非整行消失
        DynamicQueryReqDTO req = new DynamicQueryReqDTO();
        req.setDim("EMP");
        req.setSubjectIds(null);   // 不选对象（默认本人）
        req.setMetricCodes(List.of("M1"));
        req.setDataDate(LocalDate.of(2026, 4, 1));

        DataScopeContext scope = new DataScopeContext(
                DataScopeType.SELF, "U1", "ORG001", Set.of(), BizType.REPORT_DYN_EMP, BizAction.LIST);
        when(bizScopeApi.buildScopeContext(any(), any(), any())).thenReturn(scope);
        UserDTO me = new UserDTO(); me.setEmpId("U1"); me.setUsername("1001"); me.setMainOrgCode("ORG001");
        when(userApi.getUserByEmpIds(anyList())).thenReturn(List.of(me));
        // 本人当天无数据：取值返回空
        when(metricApi.getEmpMetricValues(eq("1001"), any(LocalDate.class), anyList())).thenReturn(Map.of());
        MetricDefDTO def = new MetricDefDTO(); def.setMetricCode("M1"); def.setMetricName("指标1");
        when(metricApi.getMetricDef("M1")).thenReturn(Optional.of(def));
        lenient().when(orgApi.getUserMainOrg(any())).thenReturn(null);

        DynamicQueryRespDTO resp = service.execute(req);

        assertThat(resp.getTotal()).isEqualTo(1);
        assertThat(resp.getRows()).hasSize(1);
        assertThat(resp.getRows().get(0)).containsEntry("subjectId", "1001");
        // 指标无数据 → 该列不入 row（前端显示 "-"）
        assertThat(resp.getRows().get(0)).doesNotContainKey("M1");
    }

    @Test
    void execute_pagination_returnsCurrentPageAndTotal() {
        // 选了 5 个对象，pageNo=2 pageSize=2 → 当前页 2 条、total=5
        DynamicQueryReqDTO req = new DynamicQueryReqDTO();
        req.setDim("EMP");
        req.setSubjectIds(new ArrayList<>(List.of("E1", "E2", "E3", "E4", "E5")));
        req.setMetricCodes(List.of("M1"));
        req.setDataDate(LocalDate.of(2026, 4, 1));
        req.setPageNo(2);
        req.setPageSize(2);

        DataScopeContext scope = new DataScopeContext(
                DataScopeType.ALL, "E001", "ORG001", Set.of(), BizType.REPORT_DYN_EMP, BizAction.LIST);
        when(bizScopeApi.buildScopeContext(any(), any(), any())).thenReturn(scope);
        lenient().when(userApi.getUserByEmpIds(anyList())).thenReturn(List.of());
        MetricDefDTO def = new MetricDefDTO(); def.setMetricCode("M1"); def.setMetricName("指标1");
        when(metricApi.getMetricDef("M1")).thenReturn(Optional.of(def));
        lenient().when(metricApi.getEmpMetricValues(any(), any(LocalDate.class), anyList()))
                .thenReturn(Map.of("M1", new BigDecimal("1")));
        lenient().when(orgApi.getUserMainOrg(any())).thenReturn(null);

        DynamicQueryRespDTO resp = service.execute(req);

        assertThat(resp.getTotal()).isEqualTo(5);
        assertThat(resp.getPageNo()).isEqualTo(2);
        assertThat(resp.getPageSize()).isEqualTo(2);
        assertThat(resp.getRows()).hasSize(2);
        assertThat(resp.getRows().get(0)).containsEntry("subjectId", "E3");
        assertThat(resp.getRows().get(1)).containsEntry("subjectId", "E4");
    }

    @Test
    void execute_orgDim_happyPath_usesOrgApi() {
        DynamicQueryReqDTO req = new DynamicQueryReqDTO();
        req.setDim("ORG");
        req.setSubjectIds(List.of("ORG001"));
        req.setMetricCodes(List.of("M_ORG_CNT"));
        req.setDataDate(LocalDate.of(2026, 4, 1));

        DataScopeContext scope = new DataScopeContext(
                DataScopeType.ALL, "E001", "ORG001", Set.of(),
                BizType.REPORT, BizAction.LIST);
        when(bizScopeApi.buildScopeContext(any(), any(), any())).thenReturn(scope);

        when(metricApi.getOrgMetricValues(eq("ORG001"), any(LocalDate.class), anyList()))
                .thenReturn(Map.of("M_ORG_CNT", new BigDecimal("10")));

        OrgDTO org = new OrgDTO();
        org.setOrgCode("ORG001");
        org.setOrgName("总行");
        when(orgApi.getOrg("ORG001")).thenReturn(org);

        MetricDefDTO def = new MetricDefDTO();
        def.setMetricCode("M_ORG_CNT");
        def.setMetricName("机构客户数");
        when(metricApi.getMetricDef("M_ORG_CNT")).thenReturn(Optional.of(def));

        DynamicQueryRespDTO resp = service.execute(req);

        assertThat(resp.getRows()).hasSize(1);
        assertThat(resp.getRows().get(0))
                .containsEntry("subjectName", "总行");
    }
}
