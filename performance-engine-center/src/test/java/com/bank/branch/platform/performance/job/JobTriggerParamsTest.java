package com.bank.branch.platform.performance.job;

import org.junit.jupiter.api.Test;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link JobTriggerParams} 单元测试：手动触发参数解析 + 自动触发回退.
 */
class JobTriggerParamsTest {

    private JobExecutionContext ctxWith(JobDataMap data) {
        JobExecutionContext ctx = mock(JobExecutionContext.class);
        when(ctx.getMergedJobDataMap()).thenReturn(data);
        return ctx;
    }

    @Test
    void dataDate_validInMap_parsed() {
        JobDataMap data = new JobDataMap();
        data.put("dataDate", "2026-06-03");
        assertThat(JobTriggerParams.dataDate(ctxWith(data))).isEqualTo(LocalDate.of(2026, 6, 3));
    }

    @Test
    void dataDate_absent_fallsBackToYesterday() {
        assertThat(JobTriggerParams.dataDate(ctxWith(new JobDataMap())))
                .isEqualTo(LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(1));
    }

    @Test
    void dataDate_invalid_fallsBackToYesterday() {
        JobDataMap data = new JobDataMap();
        data.put("dataDate", "not-a-date");
        assertThat(JobTriggerParams.dataDate(ctxWith(data)))
                .isEqualTo(LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(1));
    }

    @Test
    void allocDate_validInMap_parsed() {
        JobDataMap data = new JobDataMap();
        data.put("allocDate", "2026-06-02");
        assertThat(JobTriggerParams.allocDate(ctxWith(data))).isEqualTo(LocalDate.of(2026, 6, 2));
    }

    @Test
    void allocDate_absent_returnsNull() {
        // 与 dataDate 不同：缺失时返回 null（不回退昨日），由计算引擎兜底为 dataDate
        assertThat(JobTriggerParams.allocDate(ctxWith(new JobDataMap()))).isNull();
    }

    @Test
    void allocDate_invalid_returnsNull() {
        JobDataMap data = new JobDataMap();
        data.put("allocDate", "not-a-date");
        assertThat(JobTriggerParams.allocDate(ctxWith(data))).isNull();
    }

    @Test
    void triggerType_manualAndDefault() {
        JobDataMap manual = new JobDataMap();
        manual.put("triggerType", "MANUAL");
        assertThat(JobTriggerParams.triggerType(ctxWith(manual))).isEqualTo("MANUAL");
        assertThat(JobTriggerParams.triggerType(ctxWith(new JobDataMap()))).isEqualTo("AUTO");
    }

    @Test
    void operatorEmpId_presentAndAbsent() {
        JobDataMap data = new JobDataMap();
        data.put("operatorEmpId", "emp001");
        assertThat(JobTriggerParams.operatorEmpId(ctxWith(data))).isEqualTo("emp001");
        assertThat(JobTriggerParams.operatorEmpId(ctxWith(new JobDataMap()))).isNull();
    }
}
