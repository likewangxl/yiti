package com.bank.branch.platform.performance.facade;

import com.bank.branch.platform.common.web.lock.LockManager;
import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.service.MetricDefService;
import com.bank.branch.platform.performance.service.cmd.CreateMetricDefCmd;
import com.bank.branch.platform.performance.support.PerformanceServiceTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MetricLifecycleFacade 单元测试（去 Redis 后改 LockManager mock）.
 */
class MetricLifecycleFacadeTest extends PerformanceServiceTestBase {

    @Mock
    private LockManager lockManager;

    @Mock
    private MetricDefService metricDefService;

    @InjectMocks
    private MetricLifecycleFacade metricLifecycleFacade;

    @Test
    @DisplayName("获取槽位锁失败时抛 40913")
    void createMetric_whenLockAcquireFailed_throws40913() {
        when(lockManager.tryLock(anyString(), anyString(), anyLong())).thenReturn(false);

        assertThatThrownBy(() -> metricLifecycleFacade.createMetric(cmd()))
                .isInstanceOfSatisfying(PerfException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(PerfErrorCode.VALIDATION_FAILED));
        verify(metricDefService, never()).create(any());
        verify(lockManager, never()).unlock(anyString(), anyString());
    }

    @Test
    @DisplayName("创建成功后总会释放锁")
    void createMetric_whenSuccess_releasesLock() {
        when(lockManager.tryLock(anyString(), anyString(), anyLong())).thenReturn(true);
        PerfMetricDef created = new PerfMetricDef();
        created.setMetricCode("M001");
        when(metricDefService.create(any())).thenReturn(created);

        PerfMetricDef result = metricLifecycleFacade.createMetric(cmd());

        assertThat(result.getMetricCode()).isEqualTo("M001");
        verify(lockManager).unlock(eq("perf:slot-alloc:EMP"), anyString());
    }

    @Test
    @DisplayName("Service 抛异常时依然释放锁")
    void createMetric_whenServiceThrows_stillReleasesLock() {
        when(lockManager.tryLock(anyString(), anyString(), anyLong())).thenReturn(true);
        when(metricDefService.create(any())).thenThrow(new PerfException(PerfErrorCode.METRIC_CODE_DUP, "M001"));

        assertThatThrownBy(() -> metricLifecycleFacade.createMetric(cmd()))
                .isInstanceOf(PerfException.class);
        verify(lockManager).unlock(eq("perf:slot-alloc:EMP"), anyString());
    }

    private static CreateMetricDefCmd cmd() {
        return CreateMetricDefCmd.builder()
                .metricCode("M001")
                .metricName("指标一")
                .baseDim("EMP")
                .metricLevel(1)
                .operator("admin")
                .build();
    }
}
