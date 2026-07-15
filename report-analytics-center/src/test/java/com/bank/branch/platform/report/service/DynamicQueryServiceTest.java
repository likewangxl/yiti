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
import com.bank.branch.platform.performance.api.MetricQueryApi;
import com.bank.branch.platform.performance.api.dto.EmpMetricSnapshotDTO;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
    private MetricQueryApi metricQueryApi;

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

        // 2) MetricQueryApi.batchQueryEmpSnapshots 批量返回两员工指标值（N+1 修复后 EMP 维度不再逐员工查）
        when(metricQueryApi.batchQueryEmpSnapshots(eq(List.of("E001", "E002")),
                any(LocalDate.class), any(LocalDate.class), eq(List.of("M_DEPOSIT_BAL"))))
                .thenReturn(List.of(
                        EmpMetricSnapshotDTO.builder().empId("E001")
                                .metricValues(Map.of("M_DEPOSIT_BAL", new BigDecimal("1000.00"))).build(),
                        EmpMetricSnapshotDTO.builder().empId("E002")
                                .metricValues(Map.of("M_DEPOSIT_BAL", new BigDecimal("2000.00"))).build()));

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

        // 防回归关键断言：EMP 维度改走批量 API，不再逐员工调用 getEmpMetricValues
        verify(metricQueryApi, times(1)).batchQueryEmpSnapshots(anyList(), any(), any(), anyList());
        verify(metricApi, never()).getEmpMetricValues(any(), any(), any());
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
        when(metricQueryApi.batchQueryEmpSnapshots(eq(List.of("E001", "E002")),
                any(LocalDate.class), any(LocalDate.class), eq(List.of("M_DEPOSIT_BAL"))))
                .thenReturn(List.of(
                        EmpMetricSnapshotDTO.builder().empId("E001")
                                .metricValues(Map.of("M_DEPOSIT_BAL", new BigDecimal("1000.00"))).build(),
                        EmpMetricSnapshotDTO.builder().empId("E002")
                                .metricValues(Map.of("M_DEPOSIT_BAL", new BigDecimal("2000.00"))).build()));
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

        when(metricQueryApi.batchQueryEmpSnapshots(eq(List.of("1001", "1002")),
                any(LocalDate.class), any(LocalDate.class), eq(List.of("M1"))))
                .thenReturn(List.of(
                        EmpMetricSnapshotDTO.builder().empId("1001")
                                .metricValues(Map.of("M1", new BigDecimal("10"))).build(),
                        EmpMetricSnapshotDTO.builder().empId("1002")
                                .metricValues(Map.of("M1", new BigDecimal("20"))).build()));
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
        // 本人当天无数据：批量取值返回空列表（EmpMetricSnapshotDTO 契约：无数据的 empId 不出现在结果中）
        when(metricQueryApi.batchQueryEmpSnapshots(eq(List.of("1001")),
                any(LocalDate.class), any(LocalDate.class), eq(List.of("M1")))).thenReturn(List.of());
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
        // 分页只取当前页对象取值：批量调用应仅针对当前页 lookupIds（E3/E4），而非全部 5 个
        lenient().when(metricQueryApi.batchQueryEmpSnapshots(eq(List.of("E3", "E4")),
                        any(LocalDate.class), any(LocalDate.class), eq(List.of("M1"))))
                .thenReturn(List.of(
                        EmpMetricSnapshotDTO.builder().empId("E3")
                                .metricValues(Map.of("M1", new BigDecimal("1"))).build(),
                        EmpMetricSnapshotDTO.builder().empId("E4")
                                .metricValues(Map.of("M1", new BigDecimal("1"))).build()));
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

    // ------------------------------------------------------------------
    // EMP 维度 N+1 修复专项测试（复用 performance.MetricQueryApi.batchQueryEmpSnapshots）
    // ------------------------------------------------------------------

    @Test
    void execute_empDim_multipleEmployees_usesBatchQueryNotPerEmployeeLoop() {
        // 3 个员工、显式选择对象：验证 EMP 维度改走一次批量调用取全部指标值，而不是逐员工循环
        DynamicQueryReqDTO req = new DynamicQueryReqDTO();
        req.setDim("EMP");
        req.setSubjectIds(new ArrayList<>(List.of("E001", "E002", "E003")));
        req.setMetricCodes(new ArrayList<>(List.of("M_DEPOSIT_BAL")));
        req.setDataDate(LocalDate.of(2026, 4, 1));

        DataScopeContext scope = new DataScopeContext(
                DataScopeType.ALL, "E001", "ORG001", Set.of(), BizType.REPORT_DYN_EMP, BizAction.LIST);
        when(bizScopeApi.buildScopeContext(any(), any(), any())).thenReturn(scope);

        UserDTO u1 = new UserDTO(); u1.setEmpId("E001"); u1.setUsername("1001");
        UserDTO u2 = new UserDTO(); u2.setEmpId("E002"); u2.setUsername("1002");
        UserDTO u3 = new UserDTO(); u3.setEmpId("E003"); u3.setUsername("1003");
        when(userApi.getUserByEmpIds(anyList())).thenReturn(List.of(u1, u2, u3));

        when(metricQueryApi.batchQueryEmpSnapshots(eq(List.of("1001", "1002", "1003")),
                eq(req.getDataDate()), eq(req.getDataDate()), eq(List.of("M_DEPOSIT_BAL"))))
                .thenReturn(List.of(
                        EmpMetricSnapshotDTO.builder().empId("1001")
                                .metricValues(Map.of("M_DEPOSIT_BAL", new BigDecimal("100"))).build(),
                        EmpMetricSnapshotDTO.builder().empId("1002")
                                .metricValues(Map.of("M_DEPOSIT_BAL", new BigDecimal("200"))).build(),
                        EmpMetricSnapshotDTO.builder().empId("1003")
                                .metricValues(Map.of("M_DEPOSIT_BAL", new BigDecimal("300"))).build()));

        MetricDefDTO def = new MetricDefDTO();
        def.setMetricCode("M_DEPOSIT_BAL");
        def.setMetricName("存款余额");
        when(metricApi.getMetricDef("M_DEPOSIT_BAL")).thenReturn(Optional.of(def));

        DynamicQueryRespDTO resp = service.execute(req);

        assertThat(resp.getRows()).hasSize(3);
        assertThat(resp.getRows().get(0))
                .containsEntry("subjectId", "1001")
                .containsEntry("M_DEPOSIT_BAL", new BigDecimal("100"));
        assertThat(resp.getRows().get(1))
                .containsEntry("subjectId", "1002")
                .containsEntry("M_DEPOSIT_BAL", new BigDecimal("200"));
        assertThat(resp.getRows().get(2))
                .containsEntry("subjectId", "1003")
                .containsEntry("M_DEPOSIT_BAL", new BigDecimal("300"));

        // 防回归关键断言：EMP 维度不再逐员工调用 getEmpMetricValues（N+1 已修复），批量接口恰好调用 1 次
        verify(metricQueryApi, times(1)).batchQueryEmpSnapshots(anyList(), any(), any(), anyList());
        verify(metricApi, never()).getEmpMetricValues(any(), any(), any());
    }

    @Test
    void execute_empDim_metricCodesExceed50_splitsIntoMultipleBatchCalls() {
        // metricCodes 超过批量上限 50 → 应按 50 一片分多次调用 batchQueryEmpSnapshots
        DynamicQueryReqDTO req = new DynamicQueryReqDTO();
        req.setDim("EMP");
        req.setSubjectIds(new ArrayList<>(List.of("E001")));
        List<String> metricCodes = IntStream.rangeClosed(1, 51)
                .mapToObj(i -> "M" + i)
                .collect(Collectors.toList());
        req.setMetricCodes(metricCodes);
        req.setDataDate(LocalDate.of(2026, 4, 1));

        DataScopeContext scope = new DataScopeContext(
                DataScopeType.ALL, "E001", "ORG001", Set.of(), BizType.REPORT_DYN_EMP, BizAction.LIST);
        when(bizScopeApi.buildScopeContext(any(), any(), any())).thenReturn(scope);
        lenient().when(userApi.getUserByEmpIds(anyList())).thenReturn(List.of());
        lenient().when(metricApi.getMetricDef(anyString())).thenReturn(Optional.empty());
        when(metricQueryApi.batchQueryEmpSnapshots(anyList(), any(), any(), anyList()))
                .thenReturn(List.of());

        service.execute(req);

        // 51 个指标码按 50 上限分片 → 触发 2 次批量调用（1 个 subject 分片 × 2 个 metric 分片）
        verify(metricQueryApi, times(2)).batchQueryEmpSnapshots(anyList(), any(), any(), anyList());
    }

    @Test
    void execute_empDim_empIdsExceed500_splitsIntoMultipleBatchCalls() {
        // lookupIds 超过批量上限 500 → 应按 500 一片分多次调用 batchQueryEmpSnapshots
        DynamicQueryReqDTO req = new DynamicQueryReqDTO();
        req.setDim("EMP");
        List<String> subjectIds = IntStream.rangeClosed(1, 501)
                .mapToObj(i -> "E" + i)
                .collect(Collectors.toList());
        req.setSubjectIds(new ArrayList<>(subjectIds));
        req.setMetricCodes(new ArrayList<>(List.of("M1")));
        req.setDataDate(LocalDate.of(2026, 4, 1));

        DataScopeContext scope = new DataScopeContext(
                DataScopeType.ALL, "E001", "ORG001", Set.of(), BizType.REPORT_DYN_EMP, BizAction.LIST);
        when(bizScopeApi.buildScopeContext(any(), any(), any())).thenReturn(scope);
        lenient().when(userApi.getUserByEmpIds(anyList())).thenReturn(List.of());
        lenient().when(metricApi.getMetricDef(anyString())).thenReturn(Optional.empty());
        when(metricQueryApi.batchQueryEmpSnapshots(anyList(), any(), any(), anyList()))
                .thenReturn(List.of());

        service.execute(req);

        // 501 个员工按 500 上限分片 → 触发 2 次批量调用（2 个 subject 分片 × 1 个 metric 分片）
        verify(metricQueryApi, times(2)).batchQueryEmpSnapshots(anyList(), any(), any(), anyList());
    }

    @Test
    void execute_empDim_batchQueryThrows_fallsBackToPerEmployeeFetch() {
        // 批量取值整体异常 → 应 log.warn 后回退到原逐员工 fetchValuesByDim，保证单点故障不拖垮整批
        DynamicQueryReqDTO req = new DynamicQueryReqDTO();
        req.setDim("EMP");
        req.setSubjectIds(new ArrayList<>(List.of("E001")));
        req.setMetricCodes(new ArrayList<>(List.of("M1")));
        req.setDataDate(LocalDate.of(2026, 4, 1));

        DataScopeContext scope = new DataScopeContext(
                DataScopeType.ALL, "E001", "ORG001", Set.of(), BizType.REPORT_DYN_EMP, BizAction.LIST);
        when(bizScopeApi.buildScopeContext(any(), any(), any())).thenReturn(scope);
        lenient().when(userApi.getUserByEmpIds(anyList())).thenReturn(List.of());
        when(metricQueryApi.batchQueryEmpSnapshots(anyList(), any(), any(), anyList()))
                .thenThrow(new RuntimeException("批量查询失败"));
        when(metricApi.getEmpMetricValues(eq("E001"), any(LocalDate.class), anyList()))
                .thenReturn(Map.of("M1", new BigDecimal("999")));
        MetricDefDTO def = new MetricDefDTO(); def.setMetricCode("M1"); def.setMetricName("指标1");
        when(metricApi.getMetricDef("M1")).thenReturn(Optional.of(def));

        DynamicQueryRespDTO resp = service.execute(req);

        assertThat(resp.getRows()).hasSize(1);
        assertThat(resp.getRows().get(0)).containsEntry("M1", new BigDecimal("999"));
        verify(metricApi, times(1)).getEmpMetricValues(eq("E001"), any(LocalDate.class), anyList());
    }
}
