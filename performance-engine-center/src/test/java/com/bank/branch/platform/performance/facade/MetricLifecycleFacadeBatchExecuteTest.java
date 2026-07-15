package com.bank.branch.platform.performance.facade;

import com.bank.branch.platform.common.web.lock.LockManager;
import com.bank.branch.platform.performance.controller.dto.BatchExecuteRespDTO;
import com.bank.branch.platform.performance.controller.dto.RunTaskInfoDTO;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.mapper.PerfRunTaskMapper;
import com.bank.branch.platform.performance.service.CascadeRefresher;
import com.bank.branch.platform.performance.service.MetricCalcService;
import com.bank.branch.platform.performance.service.MetricDefService;
import com.bank.branch.platform.performance.service.MetricRefService;
import com.bank.branch.platform.performance.service.MetricSlotService;
import com.bank.branch.platform.performance.service.MetricTrialService;
import com.bank.branch.platform.performance.service.SysControlService;
import com.bank.branch.platform.performance.support.PerformanceServiceTestBase;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

/**
 * {@link MetricLifecycleFacade#batchExecute} 单元测试.
 *
 * <p>batchExecute 只依赖自身 {@code executeMetric}，用 {@code @Spy} 打桩其行为、隔离下游。
 * {@code MetricLifecycleFacade} 用 Lombok {@code @RequiredArgsConstructor} 声明了 9 个 final
 * 构造依赖（无无参构造），要让 {@code @Spy @InjectMocks} 能通过全参构造器建出 facade 实例，
 * 必须把这 9 个依赖全部声明为 {@code @Mock} 字段（否则 Mockito 找不到匹配构造器会抛异常）。
 */
class MetricLifecycleFacadeBatchExecuteTest extends PerformanceServiceTestBase {

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

    // batchExecute 只依赖自身 executeMetric，用 spy 打桩其行为，隔离下游
    @Spy
    @InjectMocks
    private MetricLifecycleFacade facade;

    @Test
    void batchExecute_partialFailure_aggregates() {
        LocalDate d = LocalDate.of(2026, 7, 1);
        doReturn(RunTaskInfoDTO.builder().taskId("T1").status("SUCCESS").build())
                .when(facade).executeMetric(eq("M_OK"), eq(d), eq(false), any());
        doThrow(new PerfException(PerfErrorCode.VALIDATION_FAILED, "boom"))
                .when(facade).executeMetric(eq("M_BAD"), eq(d), eq(false), any());

        BatchExecuteRespDTO resp = facade.batchExecute(List.of("M_OK", "M_BAD"), d);

        assertThat(resp.getTotal()).isEqualTo(2);
        assertThat(resp.getSuccess()).isEqualTo(1);
        assertThat(resp.getFailed()).isEqualTo(1);
        assertThat(resp.getResults()).extracting(BatchExecuteRespDTO.Item::getMetricCode)
                .containsExactly("M_OK", "M_BAD");
        assertThat(resp.getResults().get(1).getErrorMsg()).contains("boom");
    }
}
