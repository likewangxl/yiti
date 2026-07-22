package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.support.PerformanceServiceTestBase;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MetricAsyncRunner} 单元测试。
 *
 * <p>后台执行体：单测里 {@code @Async} 不生效，直接同步调用验证委派与异常吞吐语义。
 */
class MetricAsyncRunnerTest extends PerformanceServiceTestBase {

    @Mock
    private MetricCalcService metricCalcService;
    @Mock
    private CascadeRefresher cascadeRefresher;

    @InjectMocks
    private MetricAsyncRunner runner;

    private static final LocalDate D = LocalDate.of(2026, 7, 22);

    /** 非级联：透传预建 taskId 给 calcMetric，绝不再建第二行 run_task。 */
    @Test
    void runAsync_nonCascade_delegatesToCalcWithPresetTaskId() {
        runner.runAsync("M1", D, "v1", false, null, "T_PRE");

        verify(metricCalcService).calcMetricWithStats("M1", D, "v1", "MANUAL", null, "T_PRE");
        verify(cascadeRefresher, never()).refreshCascade(anyString(), any(), anyString(), any(), anyString());
    }

    /** 级联：根指标复用预建 taskId，下游各自建子任务。 */
    @Test
    void runAsync_cascade_delegatesToCascadeWithPresetRootTaskId() {
        runner.runAsync("M1", D, "v1", true, D, "T_ROOT");

        verify(cascadeRefresher).refreshCascade("M1", D, "v1", D, "T_ROOT");
        verify(metricCalcService, never()).calcMetricWithStats(anyString(), any(), anyString(), anyString(), any(), anyString());
    }

    /**
     * 计算失败：异常必须被吞掉——后台线程没有调用方可以接住它，抛出去只会打到线程池的
     * 默认异常处理器刷栈。任务终态由 calcMetric 内部落 FAILED，这里只记日志。
     */
    @Test
    void runAsync_calcThrows_swallowsSoExecutorThreadSurvives() {
        when(metricCalcService.calcMetricWithStats("M1", D, "v1", "MANUAL", null, "T_ERR"))
                .thenThrow(new PerfException(PerfErrorCode.CALC_JOB_FAILED, "boom"));

        assertThatCode(() -> runner.runAsync("M1", D, "v1", false, null, "T_ERR"))
                .doesNotThrowAnyException();
    }
}
