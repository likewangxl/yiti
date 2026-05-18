package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.governance.api.JobApi;
import com.bank.branch.platform.governance.api.dto.RegisterJobCmd;
import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.mapper.PerfMetricDefMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * MetricSchedulerService 单元测试（V1.7 P6 Task 17）.
 */
class MetricSchedulerServiceTest {

    private JobApi jobApi;
    private PerfMetricDefMapper perfMetricDefMapper;
    private MetricCronResolver cronResolver;
    private MetricSchedulerService scheduler;

    @BeforeEach
    void setup() {
        jobApi = mock(JobApi.class);
        perfMetricDefMapper = mock(PerfMetricDefMapper.class);
        cronResolver = new MetricCronResolver();
        scheduler = new MetricSchedulerService(jobApi, perfMetricDefMapper, cronResolver);
    }

    @Test
    void isSchedulable_active_auto_sql_true() {
        assertThat(scheduler.isSchedulable(newDef("M_A", "ACTIVE", "AUTO", "SQL", 0))).isTrue();
    }

    @Test
    void isSchedulable_disabled_false() {
        assertThat(scheduler.isSchedulable(newDef("M_A", "DISABLED", "AUTO", "SQL", 0))).isFalse();
    }

    @Test
    void isSchedulable_manual_false() {
        assertThat(scheduler.isSchedulable(newDef("M_A", "ACTIVE", "MANUAL", "SQL", 0))).isFalse();
    }

    @Test
    void isSchedulable_deleted_false() {
        assertThat(scheduler.isSchedulable(newDef("M_A", "ACTIVE", "AUTO", "SQL", 1))).isFalse();
    }

    @Test
    void isSchedulable_PROC_false() {
        assertThat(scheduler.isSchedulable(newDef("M_A", "ACTIVE", "AUTO", "PROC", 0))).isFalse();
    }

    @Test
    void isSchedulable_SUMMARY_false() {
        assertThat(scheduler.isSchedulable(newDef("M_A", "ACTIVE", "AUTO", "SUMMARY", 0))).isFalse();
    }

    @Test
    void isSchedulable_EXPR_subjectSql_blank_false() {
        PerfMetricDef def = newDef("M_X", "ACTIVE", "AUTO", "EXPR", 0);
        def.setSubjectSql(null);
        assertThat(scheduler.isSchedulable(def)).isFalse();
    }

    @Test
    void isSchedulable_GROOVY_subjectSql_blank_false() {
        PerfMetricDef def = newDef("M_Y", "ACTIVE", "AUTO", "GROOVY", 0);
        def.setSubjectSql("  ");
        assertThat(scheduler.isSchedulable(def)).isFalse();
    }

    @Test
    void isSchedulable_EXPR_with_subjectSql_true() {
        PerfMetricDef def = newDef("M_Z", "ACTIVE", "AUTO", "EXPR", 0);
        def.setSubjectSql("SELECT emp_id FROM t");
        assertThat(scheduler.isSchedulable(def)).isTrue();
    }

    @Test
    void register_calls_jobApi_with_correct_jobKey_and_cron() {
        PerfMetricDef def = newDef("M_DEPOSIT", "ACTIVE", "AUTO", "SQL", 0);
        def.setCalcFreq("DAY");
        scheduler.register(def);
        ArgumentCaptor<RegisterJobCmd> cap = ArgumentCaptor.forClass(RegisterJobCmd.class);
        verify(jobApi).registerJob(cap.capture());
        assertThat(cap.getValue().getJobKey()).isEqualTo("PERF_METRIC_M_DEPOSIT");
        assertThat(cap.getValue().getCronExpr()).isEqualTo("0 0 2 * * ?");
        assertThat(cap.getValue().getJobData()).containsEntry("metricCode", "M_DEPOSIT");
    }

    @Test
    void register_skips_when_EXPR_subjectSql_blank() {
        PerfMetricDef def = newDef("M_X", "ACTIVE", "AUTO", "EXPR", 0);
        def.setCalcFreq("DAY");
        def.setSubjectSql(null);
        scheduler.register(def);
        verify(jobApi, never()).registerJob(any());
    }

    @Test
    void unregister_delegates_to_jobApi() {
        scheduler.unregister("M_A");
        verify(jobApi).unregisterJob("PERF_METRIC_M_A");
    }

    @Test
    void syncOnStartup_counts_success_and_failed() {
        PerfMetricDef ok = newDef("M_OK", "ACTIVE", "AUTO", "SQL", 0);
        ok.setCalcFreq("DAY");
        PerfMetricDef bad = newDef("M_BAD", "ACTIVE", "AUTO", "SQL", 0);
        bad.setCalcFreq("HOURLY");   // 触发 cronResolver 抛异常
        when(perfMetricDefMapper.selectSchedulable()).thenReturn(List.of(ok, bad));
        scheduler.syncOnStartup();   // 不抛
        verify(jobApi, times(1)).registerJob(any());   // 仅 ok 被注册
    }

    private PerfMetricDef newDef(String code, String status, String mode, String logic, int deleted) {
        PerfMetricDef def = new PerfMetricDef();
        def.setMetricCode(code);
        def.setStatus(status);
        def.setCalcMode(mode);
        def.setCalcLogicType(logic);
        def.setDeleted(deleted);
        def.setSubjectSql("SELECT emp_id FROM t");
        // V1.9：isSchedulable 需要 baseDim 非空，默认 EMP 让既有调度类用例保持可调度语义
        def.setBaseDim("EMP");
        return def;
    }

    @Test
    void isSchedulable_baseDim_null_false() {
        // V1.9：维度无关型指标（baseDim=null）不进入自动调度
        PerfMetricDef def = newDef("M_META", "ACTIVE", "AUTO", "SQL", 0);
        def.setBaseDim(null);
        assertThat(scheduler.isSchedulable(def)).isFalse();
    }

    @Test
    void isSchedulable_baseDim_blank_false() {
        PerfMetricDef def = newDef("M_META", "ACTIVE", "AUTO", "SQL", 0);
        def.setBaseDim("  ");
        assertThat(scheduler.isSchedulable(def)).isFalse();
    }
}
