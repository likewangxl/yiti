package com.bank.branch.platform.performance.facade;

import com.bank.branch.platform.performance.support.PerformanceServiceTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MetricQueryApiImpl 单元测试.
 */
class MetricQueryApiImplTest extends PerformanceServiceTestBase {

    private final MetricQueryApiImpl metricQueryApi = new MetricQueryApiImpl();

    @Test
    @DisplayName("员工快照批量查询在 V1.0 抛 UOE")
    void batchQueryEmpSnapshots_throwsUoe() {
        assertThatThrownBy(() -> metricQueryApi.batchQueryEmpSnapshots(List.of("E001"), LocalDate.now(), LocalDate.now(), List.of("M1")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("机构快照批量查询在 V1.0 抛 UOE")
    void batchQueryOrgSnapshots_throwsUoe() {
        assertThatThrownBy(() -> metricQueryApi.batchQueryOrgSnapshots(List.of("O001"), LocalDate.now(), LocalDate.now(), List.of("M1")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("客户快照批量查询在 V1.0 抛 UOE")
    void batchQueryCustSnapshots_throwsUoe() {
        assertThatThrownBy(() -> metricQueryApi.batchQueryCustSnapshots(List.of("C001"), LocalDate.now(), LocalDate.now(), List.of("M1")))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
