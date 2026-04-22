package com.bank.branch.platform.performance.facade;

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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * MetricApiImpl 单元测试.
 *
 * <p>V1.1 P2.6 将原先 3 个 getXxxMetricValues 从 UOE 替换为真实实现，
 * getUserMetricCards 仍保留 UOE（V1.2 目标/实绩联动时再实现）。
 */
class MetricApiImplTest extends PerformanceServiceTestBase {

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
    private MetricApiImpl metricApi;

    @Test
    @DisplayName("工作台指标卡片在 V1.0 抛 UOE（V1.2 才实现）")
    void getUserMetricCards_throwsUoe() {
        assertThatThrownBy(() -> metricApi.getUserMetricCards("E001"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("getEmpMetricValues：按 metricCodes 映射到 slot 并查 EMP 宽表，返回 (metricCode -> value)")
    void getEmpMetricValues_returnsMetricCodeToValueMap() {
        PerfMetricDef m1 = metric("M001", "指标一", "EMP", 1);
        PerfMetricDef m2 = metric("M002", "指标二", "EMP", 2);
        when(metricDefService.getByCodes(List.of("M001", "M002"))).thenReturn(List.of(m1, m2));
        // slot=1 的 M001 = 100；slot=2 的 M002 = 200
        when(empIndexResultMapper.selectSlotValue(eq("E001"), any(LocalDate.class), eq("v1"), eq(1)))
                .thenReturn(new BigDecimal("100"));
        when(empIndexResultMapper.selectSlotValue(eq("E001"), any(LocalDate.class), eq("v1"), eq(2)))
                .thenReturn(new BigDecimal("200"));
        when(sysControlService.getCurrentVersion("EMP")).thenReturn(sysControl("v1"));

        Map<String, BigDecimal> result = metricApi.getEmpMetricValues(
                "E001", LocalDate.of(2026, 4, 22), List.of("M001", "M002"));

        assertThat(result)
                .containsEntry("M001", new BigDecimal("100"))
                .containsEntry("M002", new BigDecimal("200"));
    }

    @Test
    @DisplayName("getEmpMetricValues：dataDate=null 时回退到 sys_control.latest_data_date")
    void getEmpMetricValues_nullDate_fallbacksToSysControl() {
        PerfMetricDef m1 = metric("M001", "指标一", "EMP", 1);
        when(metricDefService.getByCodes(List.of("M001"))).thenReturn(List.of(m1));
        when(sysControlService.getCurrentVersion("EMP")).thenReturn(sysControl("v1", LocalDate.of(2026, 1, 15)));
        when(empIndexResultMapper.selectSlotValue(eq("E001"), eq(LocalDate.of(2026, 1, 15)), eq("v1"), eq(1)))
                .thenReturn(new BigDecimal("50"));

        Map<String, BigDecimal> result = metricApi.getEmpMetricValues("E001", null, List.of("M001"));
        assertThat(result).containsEntry("M001", new BigDecimal("50"));
    }

    @Test
    @DisplayName("getEmpMetricValues：未分配 slot 的指标跳过（不抛异常），返回 Map 不包含该 code")
    void getEmpMetricValues_skipsUnallocatedSlot() {
        PerfMetricDef m1 = metric("M001", "指标一", "EMP", 1);
        PerfMetricDef noSlot = metric("M099", "指标未分配", "EMP", 1);
        noSlot.setValSlot(null);
        when(metricDefService.getByCodes(List.of("M001", "M099"))).thenReturn(List.of(m1, noSlot));
        when(sysControlService.getCurrentVersion("EMP")).thenReturn(sysControl("v1"));
        when(empIndexResultMapper.selectSlotValue(eq("E001"), any(LocalDate.class), eq("v1"), eq(1)))
                .thenReturn(new BigDecimal("10"));

        Map<String, BigDecimal> result = metricApi.getEmpMetricValues(
                "E001", LocalDate.of(2026, 4, 22), List.of("M001", "M099"));

        assertThat(result).containsEntry("M001", new BigDecimal("10"));
        assertThat(result).doesNotContainKey("M099");
    }

    @Test
    @DisplayName("getEmpMetricValues：metricCodes 超 100 抛 BATCH_QUERY_EXCEEDS_LIMIT")
    void getEmpMetricValues_overLimit_throws() {
        List<String> over = new java.util.ArrayList<>();
        for (int i = 0; i < 101; i++) {
            over.add("M" + i);
        }
        assertThatThrownBy(() -> metricApi.getEmpMetricValues("E001", LocalDate.now(), over))
                .isInstanceOf(PerfException.class)
                .extracting("errorCode")
                .isEqualTo(PerfErrorCode.BATCH_QUERY_EXCEEDS_LIMIT);
    }

    @Test
    @DisplayName("getEmpMetricValues：非 EMP 维度的指标被过滤掉")
    void getEmpMetricValues_filtersNonEmp() {
        PerfMetricDef emp = metric("M001", "EMP 指标", "EMP", 1);
        PerfMetricDef org = metric("M002", "ORG 指标", "ORG", 1);
        org.setValSlot(2);
        when(metricDefService.getByCodes(List.of("M001", "M002"))).thenReturn(List.of(emp, org));
        when(sysControlService.getCurrentVersion("EMP")).thenReturn(sysControl("v1"));
        when(empIndexResultMapper.selectSlotValue(eq("E001"), any(LocalDate.class), eq("v1"), eq(1)))
                .thenReturn(new BigDecimal("3"));

        Map<String, BigDecimal> result = metricApi.getEmpMetricValues(
                "E001", LocalDate.of(2026, 4, 22), List.of("M001", "M002"));

        assertThat(result).containsEntry("M001", new BigDecimal("3"));
        assertThat(result).doesNotContainKey("M002");
    }

    @Test
    @DisplayName("getOrgMetricValues：路由 OrgIndexResultMapper")
    void getOrgMetricValues_routesToOrgMapper() {
        PerfMetricDef m1 = metric("MO01", "机构指标", "ORG", 1);
        when(metricDefService.getByCodes(List.of("MO01"))).thenReturn(List.of(m1));
        when(sysControlService.getCurrentVersion("ORG")).thenReturn(sysControl("v2"));
        when(orgIndexResultMapper.selectSlotValue(eq("OR01"), any(LocalDate.class), eq("v2"), eq(1)))
                .thenReturn(new BigDecimal("777"));

        Map<String, BigDecimal> result = metricApi.getOrgMetricValues(
                "OR01", LocalDate.of(2026, 4, 22), List.of("MO01"));

        assertThat(result).containsEntry("MO01", new BigDecimal("777"));
    }

    @Test
    @DisplayName("getCustMetricValues：路由 CustIndexResultMapper")
    void getCustMetricValues_routesToCustMapper() {
        PerfMetricDef m1 = metric("MC01", "客户指标", "CUST", 3);
        // helper 默认 slot=level=3，显式测试 slot=3 的路由
        when(metricDefService.getByCodes(List.of("MC01"))).thenReturn(List.of(m1));
        when(sysControlService.getCurrentVersion("CUST")).thenReturn(sysControl("v3"));
        when(custIndexResultMapper.selectSlotValue(eq("C001"), any(LocalDate.class), eq("v3"), eq(3)))
                .thenReturn(new BigDecimal("123.45"));

        Map<String, BigDecimal> result = metricApi.getCustMetricValues(
                "C001", LocalDate.of(2026, 4, 22), List.of("MC01"));

        assertThat(result).containsEntry("MC01", new BigDecimal("123.45"));
    }

    @Test
    @DisplayName("getEmpMetricValues：返回 null 值的 slot 跳过")
    void getEmpMetricValues_skipsNullValue() {
        PerfMetricDef m1 = metric("M001", "指标一", "EMP", 1);
        when(metricDefService.getByCodes(List.of("M001"))).thenReturn(List.of(m1));
        when(sysControlService.getCurrentVersion("EMP")).thenReturn(sysControl("v1"));
        when(empIndexResultMapper.selectSlotValue(eq("E001"), any(LocalDate.class), eq("v1"), eq(1)))
                .thenReturn(null);

        Map<String, BigDecimal> result = metricApi.getEmpMetricValues(
                "E001", LocalDate.of(2026, 4, 22), List.of("M001"));

        assertThat(result).doesNotContainKey("M001");
    }

    @Test
    @DisplayName("getEmpMetricValues：空 metricCodes 返回空 Map")
    void getEmpMetricValues_emptyCodes_returnsEmptyMap() {
        Map<String, BigDecimal> result = metricApi.getEmpMetricValues("E001", LocalDate.now(), List.of());
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getMetricDef 返回 Optional DTO")
    void getMetricDef_returnsOptional() {
        when(metricDefService.getByCodeOrNull("M001")).thenReturn(metric("M001", "指标一"));

        assertThat(metricApi.getMetricDef("M001"))
                .isPresent()
                .get()
                .extracting("metricCode", "metricName")
                .containsExactly("M001", "指标一");
    }

    @Test
    @DisplayName("getMetricDefs 返回 DTO 列表")
    void getMetricDefs_returnsDtos() {
        when(metricDefService.getByCodes(List.of("M001", "M002")))
                .thenReturn(List.of(metric("M001", "指标一"), metric("M002", "指标二")));

        assertThat(metricApi.getMetricDefs(List.of("M001", "M002")))
                .extracting("metricCode")
                .containsExactly("M001", "M002");
    }

    @Test
    @DisplayName("listMetrics 返回已启用指标列表")
    void listMetrics_returnsDtos() {
        when(metricDefService.listActiveMetrics("EMP", 1))
                .thenReturn(List.of(metric("M001", "指标一")));

        assertThat(metricApi.listMetrics("EMP", 1))
                .extracting("metricCode")
                .containsExactly("M001");
    }

    private static PerfMetricDef metric(String code, String name) {
        return metric(code, name, "EMP", 1);
    }

    private static PerfMetricDef metric(String code, String name, String baseDim, int level) {
        PerfMetricDef def = new PerfMetricDef();
        def.setMetricCode(code);
        def.setMetricName(name);
        def.setBaseDim(baseDim);
        def.setMetricLevel(level);
        def.setCalcFreq("DAY");
        def.setCalcMode("AUTO");
        def.setCalcLogicType("SQL");
        def.setStatus("ACTIVE");
        // 默认给一个合法 slot；未分配的测试用例会显式覆盖为 null
        def.setValSlot(level);
        return def;
    }

    private static SysControl sysControl(String version) {
        return sysControl(version, LocalDate.of(2026, 4, 22));
    }

    private static SysControl sysControl(String version, LocalDate latestDate) {
        SysControl sc = new SysControl();
        sc.setScopeDim("EMP");
        sc.setCurrentVersion(version);
        sc.setLatestDataDate(latestDate);
        sc.setIsValid(1);
        return sc;
    }
}
