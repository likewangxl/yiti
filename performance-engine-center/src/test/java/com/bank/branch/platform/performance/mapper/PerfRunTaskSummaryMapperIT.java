package com.bank.branch.platform.performance.mapper;

import com.bank.branch.platform.performance.controller.dto.MetricSummaryDTO;
import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.entity.PerfRunTask;
import com.bank.branch.platform.performance.support.PerformanceMapperTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** 分组汇总 Mapper IT：验证 COUNT/MIN/GROUP BY 与关键字过滤（前缀 TEST_RT_）. */
class PerfRunTaskSummaryMapperIT extends PerformanceMapperTestBase {

    @Autowired
    private PerfRunTaskMapper mapper;

    @Autowired
    private PerfMetricDefMapper metricDefMapper;

    /**
     * 构造一条执行任务行.
     *
     * <p>{@code created} 同时显式写入 start_time 与 created_time：MyBatis-Plus 默认 insert 策略为
     * NOT_NULL（非空字段才拼进 INSERT 列），显式设置的非空值会覆盖 DB 的
     * {@code CURRENT_TIMESTAMP} 默认值，从而让 MIN(created_time)/ORDER BY 可被测试用例确定性验证。
     */
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
        t.setCreatedTime(created);
        return t;
    }

    @Test
    void selectMetricSummary_groupsByMetricAndCounts() {
        // MySQL datetime 列无小数秒精度，显式 withNano(0) 避免截断导致的比对误差
        LocalDateTime earliest = LocalDateTime.now().minusDays(2).withNano(0);
        LocalDateTime middle = LocalDateTime.now().minusDays(1).withNano(0);
        LocalDateTime latest = LocalDateTime.now().withNano(0);
        mapper.insert(row("TEST_RT_S1", "TEST_RT_MA", earliest));
        mapper.insert(row("TEST_RT_S2", "TEST_RT_MA", middle));
        mapper.insert(row("TEST_RT_S3", "TEST_RT_MB", latest));

        List<MetricSummaryDTO> rows = mapper.selectMetricSummary("METRIC_RUN", "TEST_RT_M", null, 0, 20);

        // ORDER BY firstCreatedTime DESC：MB(latest) 早于 MA(MIN=earliest) 之前排出
        assertThat(rows).extracting(MetricSummaryDTO::getMetricCode)
                .containsExactly("TEST_RT_MB", "TEST_RT_MA");

        MetricSummaryDTO ma = rows.stream().filter(r -> "TEST_RT_MA".equals(r.getMetricCode())).findFirst().orElseThrow();
        assertThat(ma.getRunCount()).isEqualTo(2L);
        // firstCreatedTime 应为 MIN(created_time)=earliest，而非后插入的 middle
        assertThat(ma.getFirstCreatedTime()).isEqualTo(earliest);

        MetricSummaryDTO mb = rows.stream().filter(r -> "TEST_RT_MB".equals(r.getMetricCode())).findFirst().orElseThrow();
        assertThat(mb.getFirstCreatedTime()).isEqualTo(latest);

        // 基类 @Transactional 回滚 + TEST_RT_M 前缀隔离，本用例内总数精确为 2（两个指标分组）
        long total = mapper.countMetricSummary("METRIC_RUN", "TEST_RT_M", null);
        assertThat(total).isEqualTo(2L);
    }

    @Test
    void selectMetricSummary_softDeletedMetricName_notLeaked() {
        // 构造一条已软删除的指标定义（deleted=1），metric_code 对应 run_task 的 task_key
        PerfMetricDef deletedDef = new PerfMetricDef();
        deletedDef.setId(UUID.randomUUID().toString().replace("-", ""));
        deletedDef.setMetricCode("TEST_RT_MD");
        deletedDef.setMetricName("TEST_RT_已删指标");
        deletedDef.setBaseDim("EMP"); // 实测库 base_dim 为 NOT NULL 无默认值，须显式填充
        deletedDef.setMetricLevel(1);
        deletedDef.setCalcFreq("DAY");
        deletedDef.setCalcMode("AUTO");
        deletedDef.setCalcLogicType("SQL");
        deletedDef.setStatus("ACTIVE");
        deletedDef.setDeleted(1);
        metricDefMapper.insert(deletedDef);

        // 该指标仍有一条历史执行行（软删指标不影响已产生的执行记录）
        mapper.insert(row("TEST_RT_S5", "TEST_RT_MD", LocalDateTime.now()));

        List<MetricSummaryDTO> rows = mapper.selectMetricSummary("METRIC_RUN", "TEST_RT_MD", null, 0, 20);
        MetricSummaryDTO hit = rows.stream()
                .filter(r -> "TEST_RT_MD".equals(r.getMetricCode()))
                .findFirst().orElseThrow();
        // 行仍在（次数照常计数），但已软删指标的名字不应泄漏
        assertThat(hit.getRunCount()).isEqualTo(1L);
        assertThat(hit.getMetricName()).isNull();

        // 已删除指标名关键字命中不到该行（LEFT JOIN ON 里 d.deleted=0 已把 metric_name 过滤为 null）
        List<MetricSummaryDTO> byDeletedName = mapper.selectMetricSummary(
                "METRIC_RUN", "TEST_RT_已删", null, 0, 20);
        assertThat(byDeletedName).isEmpty();
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
