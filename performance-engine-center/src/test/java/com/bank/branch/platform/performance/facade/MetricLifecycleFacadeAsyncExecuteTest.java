package com.bank.branch.platform.performance.facade;

import com.bank.branch.platform.common.web.lock.LockManager;
import com.bank.branch.platform.performance.controller.dto.BatchExecuteRespDTO;
import com.bank.branch.platform.performance.controller.dto.RunTaskInfoDTO;
import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.entity.SysControl;
import com.bank.branch.platform.performance.mapper.PerfRunTaskMapper;
import com.bank.branch.platform.performance.service.CascadeRefresher;
import com.bank.branch.platform.performance.service.MetricAsyncRunner;
import com.bank.branch.platform.performance.service.MetricCalcService;
import com.bank.branch.platform.performance.service.MetricDefService;
import com.bank.branch.platform.performance.service.MetricRefService;
import com.bank.branch.platform.performance.service.MetricSlotService;
import com.bank.branch.platform.performance.service.MetricTrialService;
import com.bank.branch.platform.performance.service.SysControlService;
import com.bank.branch.platform.performance.support.PerformanceServiceTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.core.task.TaskRejectedException;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MetricLifecycleFacade#executeMetric} 异步提交语义单元测试。
 *
 * <p>背景：指标执行原为**全同步**——HTTP 请求一直阻塞到 SQL 跑完、级联下游也刷完才返回，
 * 重指标/大批量会撞网关读超时，前端表现为「网络异常或后端未启动」。改为提交即返回：
 * 请求线程内同步预建 PENDING 的 {@code PERF_RUN_TASK} 行（保证 started_by 取到真实操作人，
 * 异步线程无会话 ThreadLocal 会兜底成 SYSTEM），把 taskId 立刻返回给前端，真正的计算交给
 * {@link MetricAsyncRunner} 后台执行，前端轮询任务历史看进度。
 */
class MetricLifecycleFacadeAsyncExecuteTest extends PerformanceServiceTestBase {

    @Mock
    private LockManager lockManager;
    @Mock
    private MetricDefService metricDefService;
    @Mock
    private MetricTrialService metricTrialService;
    @Mock
    private MetricCalcService metricCalcService;
    @Mock
    private CascadeRefresher cascadeRefresher;
    @Mock
    private SysControlService sysControlService;
    @Mock
    private PerfRunTaskMapper perfRunTaskMapper;
    @Mock
    private MetricSlotService metricSlotService;
    @Mock
    private MetricRefService metricRefService;
    @Mock
    private MetricAsyncRunner metricAsyncRunner;

    @InjectMocks
    private MetricLifecycleFacade facade;

    private static final LocalDate D = LocalDate.of(2026, 7, 22);

    @BeforeEach
    void stubMetricAndVersion() {
        PerfMetricDef def = new PerfMetricDef();
        def.setMetricCode("M1");
        def.setBaseDim("EMP");
        lenient().when(metricDefService.getByCode("M1")).thenReturn(def);
        SysControl ctl = new SysControl();
        ctl.setCurrentVersion("v1");
        lenient().when(sysControlService.getCurrentVersion("EMP")).thenReturn(ctl);
    }

    /**
     * 异步提交（cascade=false）：请求线程只预建任务行 + 交给 runner，立刻返回 taskId/PENDING，
     * 绝不在请求线程里跑 calcMetric。
     */
    @Test
    void executeMetric_async_returnsTaskIdImmediatelyWithoutCalculating() {
        when(metricCalcService.createPendingTask("M1", D, "v1", "MANUAL")).thenReturn("T_PRE");

        RunTaskInfoDTO dto = facade.executeMetric("M1", D, Boolean.FALSE, null, Boolean.TRUE);

        assertThat(dto.getTaskId()).isEqualTo("T_PRE");
        assertThat(dto.getStatus()).isEqualTo("PENDING");
        assertThat(dto.getVersion()).isEqualTo("v1");
        // 请求线程内绝不能真算——这正是超时的根因
        verify(metricCalcService, never()).calcMetric(anyString(), any(), anyString(), anyString(), any());
        verify(cascadeRefresher, never()).refreshCascade(anyString(), any(), anyString(), any(), any());
        // 计算交给后台 runner，并复用预建的 taskId（不得再建第二行）
        verify(metricAsyncRunner).runAsync("M1", D, "v1", false, null, "T_PRE");
    }

    /** 异步提交（cascade=true）：同样立刻返回，级联在后台跑。 */
    @Test
    void executeMetric_asyncCascade_submitsCascadeToRunner() {
        when(metricCalcService.createPendingTask("M1", D, "v1", "MANUAL")).thenReturn("T_PRE2");

        RunTaskInfoDTO dto = facade.executeMetric("M1", D, Boolean.TRUE, D, Boolean.TRUE);

        assertThat(dto.getTaskId()).isEqualTo("T_PRE2");
        assertThat(dto.getStatus()).isEqualTo("PENDING");
        verify(metricAsyncRunner).runAsync("M1", D, "v1", true, D, "T_PRE2");
    }

    /**
     * 线程池拒绝（队列满）：预建的任务行必须立即标 FAILED，否则会永远停在 PENDING，
     * 监控页看着像「在跑」实际没人跑。
     */
    @Test
    void executeMetric_asyncRejected_marksTaskFailed() {
        when(metricCalcService.createPendingTask("M1", D, "v1", "MANUAL")).thenReturn("T_REJ");
        doThrow(new TaskRejectedException("queue full"))
                .when(metricAsyncRunner).runAsync(anyString(), any(), anyString(), anyBooleanArg(), any(), anyString());

        RunTaskInfoDTO dto = facade.executeMetric("M1", D, Boolean.FALSE, null, Boolean.TRUE);

        assertThat(dto.getStatus()).isEqualTo("FAILED");
        verify(perfRunTaskMapper).updateStatus(eq("T_REJ"), eq("FAILED"), anyString());
    }

    /** async=false 时保留原同步语义（历史调用方/需要立即拿终态的场景）。 */
    @Test
    void executeMetric_sync_stillCalculatesInline() {
        when(metricCalcService.calcMetric("M1", D, "v1", "MANUAL", null)).thenReturn("T_SYNC");

        RunTaskInfoDTO dto = facade.executeMetric("M1", D, Boolean.FALSE, null, Boolean.FALSE);

        assertThat(dto.getTaskId()).isEqualTo("T_SYNC");
        verify(metricAsyncRunner, never()).runAsync(anyString(), any(), anyString(), anyBooleanArg(), any(), anyString());
    }

    /** 批量异步：逐个提交，返回全部 PENDING + taskId，不在请求线程里逐个跑完。 */
    @Test
    void batchExecute_async_submitsAllAndReturnsPending() {
        PerfMetricDef d2 = new PerfMetricDef();
        d2.setMetricCode("M2");
        d2.setBaseDim("EMP");
        when(metricDefService.getByCode("M2")).thenReturn(d2);
        when(metricCalcService.createPendingTask(eq("M1"), eq(D), eq("v1"), eq("MANUAL"))).thenReturn("T_A");
        when(metricCalcService.createPendingTask(eq("M2"), eq(D), eq("v1"), eq("MANUAL"))).thenReturn("T_B");

        BatchExecuteRespDTO resp = facade.batchExecute(List.of("M1", "M2"), D, Boolean.TRUE);

        assertThat(resp.getTotal()).isEqualTo(2);
        assertThat(resp.getSuccess()).isEqualTo(2);
        assertThat(resp.getFailed()).isZero();
        assertThat(resp.getResults()).extracting(BatchExecuteRespDTO.Item::getStatus)
                .containsExactly("PENDING", "PENDING");
        assertThat(resp.getResults()).extracting(BatchExecuteRespDTO.Item::getRunTaskId)
                .containsExactly("T_A", "T_B");
        verify(metricCalcService, never()).calcMetric(anyString(), any(), anyString(), anyString(), any());
    }

    /** Mockito 的 boolean 匹配器，抽出来避免与 assertj 的静态导入混淆。 */
    private static boolean anyBooleanArg() {
        return org.mockito.ArgumentMatchers.anyBoolean();
    }
}
