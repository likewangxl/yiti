package com.bank.branch.platform.performance.facade;

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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MetricLifecycleFacade 单元测试.
 */
class MetricLifecycleFacadeTest extends PerformanceServiceTestBase {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private MetricDefService metricDefService;

    @InjectMocks
    private MetricLifecycleFacade metricLifecycleFacade;

    @Test
    @DisplayName("获取槽位锁失败时抛 40913")
    void createMetric_whenLockAcquireFailed_throws40913() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), any(), any(Duration.class))).thenReturn(Boolean.FALSE);

        assertThatThrownBy(() -> metricLifecycleFacade.createMetric(cmd()))
                .isInstanceOfSatisfying(PerfException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(PerfErrorCode.VALIDATION_FAILED));
        verify(metricDefService, never()).create(any());
    }

    @Test
    @DisplayName("创建成功后总会释放锁")
    void createMetric_whenSuccess_releasesLock() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), any(), any(Duration.class))).thenReturn(Boolean.TRUE);
        PerfMetricDef created = new PerfMetricDef();
        created.setMetricCode("M001");
        when(metricDefService.create(any())).thenReturn(created);

        PerfMetricDef result = metricLifecycleFacade.createMetric(cmd());

        assertThat(result.getMetricCode()).isEqualTo("M001");
        verify(redisTemplate).execute(any(RedisScript.class), eq(List.of("perf:slot-alloc:EMP")), any());
    }

    @Test
    @DisplayName("Service 抛异常时依然释放锁")
    void createMetric_whenServiceThrows_stillReleasesLock() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), any(), any(Duration.class))).thenReturn(Boolean.TRUE);
        when(metricDefService.create(any())).thenThrow(new PerfException(PerfErrorCode.METRIC_CODE_DUP, "M001"));

        assertThatThrownBy(() -> metricLifecycleFacade.createMetric(cmd()))
                .isInstanceOf(PerfException.class);
        verify(redisTemplate).execute(any(RedisScript.class), eq(List.of("perf:slot-alloc:EMP")), any());
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
