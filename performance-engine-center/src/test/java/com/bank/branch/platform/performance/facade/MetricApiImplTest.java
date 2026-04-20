package com.bank.branch.platform.performance.facade;

import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.service.MetricDefService;
import com.bank.branch.platform.performance.support.PerformanceServiceTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * MetricApiImpl 单元测试.
 */
class MetricApiImplTest extends PerformanceServiceTestBase {

    @Mock
    private MetricDefService metricDefService;

    @InjectMocks
    private MetricApiImpl metricApi;

    @Test
    @DisplayName("工作台指标卡片在 V1.0 抛 UOE")
    void getUserMetricCards_throwsUoe() {
        assertThatThrownBy(() -> metricApi.getUserMetricCards("E001"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("员工指标值在 V1.0 抛 UOE")
    void getEmpMetricValues_throwsUoe() {
        assertThatThrownBy(() -> metricApi.getEmpMetricValues("E001", null, List.of("M1")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("机构指标值在 V1.0 抛 UOE")
    void getOrgMetricValues_throwsUoe() {
        assertThatThrownBy(() -> metricApi.getOrgMetricValues("ORG001", null, List.of("M1")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("客户指标值在 V1.0 抛 UOE")
    void getCustMetricValues_throwsUoe() {
        assertThatThrownBy(() -> metricApi.getCustMetricValues("C001", null, List.of("M1")))
                .isInstanceOf(UnsupportedOperationException.class);
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
        PerfMetricDef def = new PerfMetricDef();
        def.setMetricCode(code);
        def.setMetricName(name);
        def.setBaseDim("EMP");
        def.setMetricLevel(1);
        def.setCalcFreq("DAY");
        def.setCalcMode("AUTO");
        def.setCalcLogicType("SQL");
        def.setStatus("ACTIVE");
        return def;
    }
}
