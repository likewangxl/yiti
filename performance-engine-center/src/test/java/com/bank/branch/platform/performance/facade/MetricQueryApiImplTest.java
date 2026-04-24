package com.bank.branch.platform.performance.facade;

import com.bank.branch.platform.performance.api.dto.CustMetricSnapshotDTO;
import com.bank.branch.platform.performance.api.dto.EmpMetricSnapshotDTO;
import com.bank.branch.platform.performance.api.dto.OrgMetricSnapshotDTO;
import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.entity.SysControl;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.mapper.CustIndexResultMapper;
import com.bank.branch.platform.performance.mapper.CustMetricValueRow;
import com.bank.branch.platform.performance.mapper.EmpIndexResultMapper;
import com.bank.branch.platform.performance.mapper.EmpMetricValueRow;
import com.bank.branch.platform.performance.mapper.OrgIndexResultMapper;
import com.bank.branch.platform.performance.mapper.OrgMetricValueRow;
import com.bank.branch.platform.performance.service.MetricDefService;
import com.bank.branch.platform.performance.service.SysControlService;
import com.bank.branch.platform.performance.support.PerformanceServiceTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * MetricQueryApiImpl 单元测试（V1.3 R2.2 batchQueryEmpSnapshots 行为断言）.
 *
 * <p>V1.3 R2.2 将 batchQueryEmpSnapshots 从 V1.2 UOE 替换为真实实现：
 * EmpIndexResult 宽表按 slot 批量查并返回 DTO 列表。Org/Cust 两处留到 R2.3+R2.4 替换。
 *
 * <p>约束：入参 empIds size ≤ 500, metricCodes size ≤ 50。
 * 超限抛 {@link PerfErrorCode#BATCH_QUERY_EXCEEDS_LIMIT}.
 *
 * <p>dataDate 使用 dateFrom（V1.3 初版只支持单日点查询；跨日期留 V1.4 迭代）。
 */
class MetricQueryApiImplTest extends PerformanceServiceTestBase {

    @Mock
    private MetricDefService metricDefService;

    @Mock
    private SysControlService sysControlService;

    @Mock
    private EmpIndexResultMapper empIndexResultMapper;

    @Mock
    private OrgIndexResultMapper orgIndexResultMapper;

    @Mock
    private CustIndexResultMapper custIndexResultMapper;

    @InjectMocks
    private MetricQueryApiImpl api;

    // ================= R2.2 batchQueryEmpSnapshots =================

    @Test
    @DisplayName("batchQueryEmpSnapshots: 按 slot 查 EMP 宽表并组装 DTO (empId + metricValues)")
    void batchQueryEmpSnapshots_returnsDtoListWithMetricValues() {
        List<String> empIds = List.of("E001", "E002");
        List<String> metricCodes = List.of("M_EMP_A");
        LocalDate dataDate = LocalDate.of(2026, 4, 1);

        when(metricDefService.getByCodes(metricCodes)).thenReturn(List.of(
                metric("M_EMP_A", "EMP", 5)));
        when(sysControlService.getCurrentVersion("EMP")).thenReturn(sysControl("v1"));
        when(empIndexResultMapper.selectSlotValuesByEmps(eq(empIds), eq(dataDate), eq("v1"), eq(5)))
                .thenReturn(List.of(
                        empRow("E001", new BigDecimal("100")),
                        empRow("E002", new BigDecimal("200"))));

        List<EmpMetricSnapshotDTO> result =
                api.batchQueryEmpSnapshots(empIds, dataDate, dataDate, metricCodes);

        assertThat(result).hasSize(2);
        EmpMetricSnapshotDTO e1 = result.stream().filter(d -> "E001".equals(d.getEmpId())).findFirst().orElseThrow();
        assertThat(e1.getDataDate()).isEqualTo(dataDate);
        assertThat(e1.getVersion()).isEqualTo("v1");
        assertThat(e1.getMetricValues()).containsEntry("M_EMP_A", new BigDecimal("100"));
        EmpMetricSnapshotDTO e2 = result.stream().filter(d -> "E002".equals(d.getEmpId())).findFirst().orElseThrow();
        assertThat(e2.getMetricValues()).containsEntry("M_EMP_A", new BigDecimal("200"));
    }

    @Test
    @DisplayName("batchQueryEmpSnapshots: empIds > 500 抛 BATCH_QUERY_EXCEEDS_LIMIT(PERF-42206)")
    void batchQueryEmpSnapshots_empIdsOverLimit_throws42206() {
        List<String> tooMany = IntStream.range(0, 501).mapToObj(i -> "E" + i).toList();
        LocalDate date = LocalDate.of(2026, 4, 1);
        assertThatThrownBy(() ->
                api.batchQueryEmpSnapshots(tooMany, date, date, List.of("M_EMP_A")))
                .isInstanceOf(PerfException.class)
                .extracting("errorCode")
                .isEqualTo(PerfErrorCode.BATCH_QUERY_EXCEEDS_LIMIT);
    }

    @Test
    @DisplayName("batchQueryEmpSnapshots: metricCodes > 50 抛 BATCH_QUERY_EXCEEDS_LIMIT(PERF-42206)")
    void batchQueryEmpSnapshots_metricCodesOverLimit_throws42206() {
        List<String> codes = IntStream.range(0, 51).mapToObj(i -> "M" + i).toList();
        LocalDate date = LocalDate.of(2026, 4, 1);
        assertThatThrownBy(() ->
                api.batchQueryEmpSnapshots(List.of("E001"), date, date, codes))
                .isInstanceOf(PerfException.class)
                .extracting("errorCode")
                .isEqualTo(PerfErrorCode.BATCH_QUERY_EXCEEDS_LIMIT);
    }

    @Test
    @DisplayName("batchQueryEmpSnapshots: 非 EMP 维度 metric 被跳过（无命中返回空列表）")
    void batchQueryEmpSnapshots_nonEmpMetricSkipped() {
        List<String> empIds = List.of("E001");
        List<String> codes = List.of("M_ORG_X");
        LocalDate date = LocalDate.of(2026, 4, 1);
        when(metricDefService.getByCodes(codes)).thenReturn(List.of(
                metric("M_ORG_X", "ORG", 2)));
        // 无 EMP 维度指标时, Facade 提前短路返回空列表, 不需要查 sys_control

        List<EmpMetricSnapshotDTO> result = api.batchQueryEmpSnapshots(empIds, date, date, codes);

        assertThat(result).isEmpty();
    }

    // ================= R2.3 batchQueryOrgSnapshots =================

    @Test
    @DisplayName("batchQueryOrgSnapshots: 按 slot 查 ORG 宽表并组装 DTO (orgCode + metricValues)")
    void batchQueryOrgSnapshots_returnsDtoListWithMetricValues() {
        List<String> orgCodes = List.of("ORG_001", "ORG_002");
        List<String> metricCodes = List.of("M_ORG_DEP_TOTAL");
        LocalDate dataDate = LocalDate.of(2026, 4, 1);

        when(metricDefService.getByCodes(metricCodes)).thenReturn(List.of(
                metric("M_ORG_DEP_TOTAL", "ORG", 10)));
        when(sysControlService.getCurrentVersion("ORG")).thenReturn(sysControl("v1"));
        when(orgIndexResultMapper.selectSlotValuesByOrgs(eq(orgCodes), eq(dataDate), eq("v1"), eq(10)))
                .thenReturn(List.of(
                        orgRow("ORG_001", new BigDecimal("1000")),
                        orgRow("ORG_002", new BigDecimal("2000"))));

        List<OrgMetricSnapshotDTO> result =
                api.batchQueryOrgSnapshots(orgCodes, dataDate, dataDate, metricCodes);

        assertThat(result).hasSize(2);
        OrgMetricSnapshotDTO o1 = result.stream().filter(d -> "ORG_001".equals(d.getOrgCode())).findFirst().orElseThrow();
        assertThat(o1.getDataDate()).isEqualTo(dataDate);
        assertThat(o1.getVersion()).isEqualTo("v1");
        assertThat(o1.getMetricValues()).containsEntry("M_ORG_DEP_TOTAL", new BigDecimal("1000"));
        OrgMetricSnapshotDTO o2 = result.stream().filter(d -> "ORG_002".equals(d.getOrgCode())).findFirst().orElseThrow();
        assertThat(o2.getMetricValues()).containsEntry("M_ORG_DEP_TOTAL", new BigDecimal("2000"));
    }

    @Test
    @DisplayName("batchQueryOrgSnapshots: orgCodes > 500 抛 BATCH_QUERY_EXCEEDS_LIMIT(PERF-42206)")
    void batchQueryOrgSnapshots_orgCodesOverLimit_throws42206() {
        List<String> tooMany = IntStream.range(0, 501).mapToObj(i -> "O_" + i).toList();
        LocalDate date = LocalDate.of(2026, 4, 1);
        assertThatThrownBy(() ->
                api.batchQueryOrgSnapshots(tooMany, date, date, List.of("M1")))
                .isInstanceOf(PerfException.class)
                .extracting("errorCode")
                .isEqualTo(PerfErrorCode.BATCH_QUERY_EXCEEDS_LIMIT);
    }

    @Test
    @DisplayName("batchQueryOrgSnapshots: metricCodes > 50 抛 BATCH_QUERY_EXCEEDS_LIMIT(PERF-42206)")
    void batchQueryOrgSnapshots_metricCodesOverLimit_throws42206() {
        List<String> codes = IntStream.range(0, 51).mapToObj(i -> "M" + i).toList();
        LocalDate date = LocalDate.of(2026, 4, 1);
        assertThatThrownBy(() ->
                api.batchQueryOrgSnapshots(List.of("O001"), date, date, codes))
                .isInstanceOf(PerfException.class)
                .extracting("errorCode")
                .isEqualTo(PerfErrorCode.BATCH_QUERY_EXCEEDS_LIMIT);
    }

    @Test
    @DisplayName("batchQueryOrgSnapshots: 非 ORG 维度 metric 被过滤, 返回空列表")
    void batchQueryOrgSnapshots_nonOrgMetricSkipped() {
        List<String> orgCodes = List.of("ORG_001");
        List<String> codes = List.of("M_EMP_X");
        LocalDate date = LocalDate.of(2026, 4, 1);
        when(metricDefService.getByCodes(codes)).thenReturn(List.of(
                metric("M_EMP_X", "EMP", 1)));

        List<OrgMetricSnapshotDTO> result = api.batchQueryOrgSnapshots(orgCodes, date, date, codes);

        assertThat(result).isEmpty();
    }

    // ================= R2.4 batchQueryCustSnapshots =================

    @Test
    @DisplayName("batchQueryCustSnapshots: 按 slot 查 CUST 宽表并组装 DTO (custId + metricValues)")
    void batchQueryCustSnapshots_returnsDtoListWithMetricValues() {
        List<String> custIds = List.of("C001", "C002");
        List<String> metricCodes = List.of("M_CUST_AUM");
        LocalDate dataDate = LocalDate.of(2026, 4, 1);

        when(metricDefService.getByCodes(metricCodes)).thenReturn(List.of(
                metric("M_CUST_AUM", "CUST", 7)));
        when(sysControlService.getCurrentVersion("CUST")).thenReturn(sysControl("v1"));
        when(custIndexResultMapper.selectSlotValuesByCusts(eq(custIds), eq(dataDate), eq("v1"), eq(7)))
                .thenReturn(List.of(
                        custRow("C001", new BigDecimal("500000")),
                        custRow("C002", new BigDecimal("888000"))));

        List<CustMetricSnapshotDTO> result =
                api.batchQueryCustSnapshots(custIds, dataDate, dataDate, metricCodes);

        assertThat(result).hasSize(2);
        CustMetricSnapshotDTO c1 = result.stream().filter(d -> "C001".equals(d.getCustId())).findFirst().orElseThrow();
        assertThat(c1.getDataDate()).isEqualTo(dataDate);
        assertThat(c1.getVersion()).isEqualTo("v1");
        assertThat(c1.getMetricValues()).containsEntry("M_CUST_AUM", new BigDecimal("500000"));
        CustMetricSnapshotDTO c2 = result.stream().filter(d -> "C002".equals(d.getCustId())).findFirst().orElseThrow();
        assertThat(c2.getMetricValues()).containsEntry("M_CUST_AUM", new BigDecimal("888000"));
    }

    @Test
    @DisplayName("batchQueryCustSnapshots: custIds > 500 抛 BATCH_QUERY_EXCEEDS_LIMIT(PERF-42206)")
    void batchQueryCustSnapshots_custIdsOverLimit_throws42206() {
        List<String> tooMany = IntStream.range(0, 501).mapToObj(i -> "C_" + i).toList();
        LocalDate date = LocalDate.of(2026, 4, 1);
        assertThatThrownBy(() ->
                api.batchQueryCustSnapshots(tooMany, date, date, List.of("M1")))
                .isInstanceOf(PerfException.class)
                .extracting("errorCode")
                .isEqualTo(PerfErrorCode.BATCH_QUERY_EXCEEDS_LIMIT);
    }

    @Test
    @DisplayName("batchQueryCustSnapshots: metricCodes > 50 抛 BATCH_QUERY_EXCEEDS_LIMIT(PERF-42206)")
    void batchQueryCustSnapshots_metricCodesOverLimit_throws42206() {
        List<String> codes = IntStream.range(0, 51).mapToObj(i -> "M" + i).toList();
        LocalDate date = LocalDate.of(2026, 4, 1);
        assertThatThrownBy(() ->
                api.batchQueryCustSnapshots(List.of("C001"), date, date, codes))
                .isInstanceOf(PerfException.class)
                .extracting("errorCode")
                .isEqualTo(PerfErrorCode.BATCH_QUERY_EXCEEDS_LIMIT);
    }

    // ================= 辅助构造 =================

    private static PerfMetricDef metric(String code, String baseDim, int slot) {
        PerfMetricDef def = new PerfMetricDef();
        def.setMetricCode(code);
        def.setBaseDim(baseDim);
        def.setValSlot(slot);
        def.setStatus("ACTIVE");
        return def;
    }

    private static SysControl sysControl(String version) {
        SysControl sc = new SysControl();
        sc.setCurrentVersion(version);
        sc.setLatestDataDate(LocalDate.of(2026, 4, 1));
        sc.setIsValid(1);
        return sc;
    }

    private static EmpMetricValueRow empRow(String empId, BigDecimal value) {
        EmpMetricValueRow r = new EmpMetricValueRow();
        r.setEmpId(empId);
        r.setMetricValue(value);
        return r;
    }

    private static OrgMetricValueRow orgRow(String orgCode, BigDecimal value) {
        OrgMetricValueRow r = new OrgMetricValueRow();
        r.setOrgCode(orgCode);
        r.setMetricValue(value);
        return r;
    }

    private static CustMetricValueRow custRow(String custId, BigDecimal value) {
        CustMetricValueRow r = new CustMetricValueRow();
        r.setCustId(custId);
        r.setMetricValue(value);
        return r;
    }
}
