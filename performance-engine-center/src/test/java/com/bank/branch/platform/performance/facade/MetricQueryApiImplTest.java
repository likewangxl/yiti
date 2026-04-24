package com.bank.branch.platform.performance.facade;

import com.bank.branch.platform.performance.api.dto.EmpMetricSnapshotDTO;
import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.entity.SysControl;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.mapper.CustIndexResultMapper;
import com.bank.branch.platform.performance.mapper.EmpIndexResultMapper;
import com.bank.branch.platform.performance.mapper.EmpMetricValueRow;
import com.bank.branch.platform.performance.mapper.OrgIndexResultMapper;
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
        when(sysControlService.getCurrentVersion("EMP")).thenReturn(sysControl("v1"));

        List<EmpMetricSnapshotDTO> result = api.batchQueryEmpSnapshots(empIds, date, date, codes);

        assertThat(result).isEmpty();
    }

    // ================= R2.3/R2.4 仍处于 UOE 占位，本阶段 Red 只覆盖 Emp =================

    @Test
    @DisplayName("机构快照批量查询：R2.3 实现前仍保持 UOE 占位")
    void batchQueryOrgSnapshots_throwsUoe() {
        assertThatThrownBy(() ->
                api.batchQueryOrgSnapshots(List.of("O001"), LocalDate.now(), LocalDate.now(), List.of("M1")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("客户快照批量查询：R2.4 实现前仍保持 UOE 占位")
    void batchQueryCustSnapshots_throwsUoe() {
        assertThatThrownBy(() ->
                api.batchQueryCustSnapshots(List.of("C001"), LocalDate.now(), LocalDate.now(), List.of("M1")))
                .isInstanceOf(UnsupportedOperationException.class);
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
}
