package com.bank.branch.platform.performance.mapper;

import com.bank.branch.platform.performance.controller.dto.MetricSummaryDTO;
import com.bank.branch.platform.performance.entity.PerfRunTask;
import com.bank.branch.platform.performance.support.PerformanceMapperTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 分组汇总 Mapper IT：验证 COUNT/MIN/GROUP BY 与关键字过滤（前缀 TEST_RT_）. */
class PerfRunTaskSummaryMapperIT extends PerformanceMapperTestBase {

    @Autowired
    private PerfRunTaskMapper mapper;

    private PerfRunTask row(String id, String metricCode, LocalDateTime created) {
        PerfRunTask t = new PerfRunTask();
        t.setId(id);
        t.setTaskType("METRIC_RUN");
        t.setTriggerType("MANUAL");
        t.setTaskKey(metricCode);
        t.setDataDate(LocalDate.of(2026, 7, 1));
        t.setStatus("SUCCESS");
        t.setStartedBy("TEST_RT_EMP");
        t.setStartTime(created);
        return t; // created_time 由 DB 默认值填充
    }

    @Test
    void selectMetricSummary_groupsByMetricAndCounts() {
        mapper.insert(row("TEST_RT_S1", "TEST_RT_MA", LocalDateTime.now().minusDays(2)));
        mapper.insert(row("TEST_RT_S2", "TEST_RT_MA", LocalDateTime.now().minusDays(1)));
        mapper.insert(row("TEST_RT_S3", "TEST_RT_MB", LocalDateTime.now()));

        List<MetricSummaryDTO> rows = mapper.selectMetricSummary("METRIC_RUN", "TEST_RT_M", null, 0, 20);

        assertThat(rows).extracting(MetricSummaryDTO::getMetricCode)
                .contains("TEST_RT_MA", "TEST_RT_MB");
        MetricSummaryDTO ma = rows.stream().filter(r -> "TEST_RT_MA".equals(r.getMetricCode())).findFirst().orElseThrow();
        assertThat(ma.getRunCount()).isEqualTo(2L);
        assertThat(ma.getFirstCreatedTime()).isNotNull();

        long total = mapper.countMetricSummary("METRIC_RUN", "TEST_RT_M", null);
        assertThat(total).isGreaterThanOrEqualTo(2L);
    }

    @Test
    void selectMetricSummary_dataScopeFilterNarrowsByStartedBy() {
        mapper.insert(row("TEST_RT_S4", "TEST_RT_MC", LocalDateTime.now()));
        List<MetricSummaryDTO> mine = mapper.selectMetricSummary(
                "METRIC_RUN", "TEST_RT_MC", "AND started_by = 'TEST_RT_EMP'", 0, 20);
        assertThat(mine).extracting(MetricSummaryDTO::getMetricCode).contains("TEST_RT_MC");

        List<MetricSummaryDTO> others = mapper.selectMetricSummary(
                "METRIC_RUN", "TEST_RT_MC", "AND started_by = 'NOBODY_X'", 0, 20);
        assertThat(others).extracting(MetricSummaryDTO::getMetricCode).doesNotContain("TEST_RT_MC");
    }
}
