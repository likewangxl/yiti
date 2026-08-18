package com.bank.branch.platform.performance.job.quartz;

import com.bank.branch.platform.performance.service.MetricCalcReadyCoordinator;
import org.junit.jupiter.api.Test;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** 数据就绪协调器 Quartz 包装约束测试。 */
class MetricCalcReadyCoordinatorQuartzJobTest {

    @Test
    void wrapper_isNonSpringComponent_disallowsConcurrency_andDelegates() throws Exception {
        assertThat(MetricCalcReadyCoordinatorQuartzJob.class).isAssignableTo(Job.class);
        assertThat(MetricCalcReadyCoordinatorQuartzJob.class
                .isAnnotationPresent(DisallowConcurrentExecution.class)).isTrue();
        assertThat(MetricCalcReadyCoordinatorQuartzJob.class
                .isAnnotationPresent(Component.class)).isFalse();

        MetricCalcReadyCoordinator coordinator = mock(MetricCalcReadyCoordinator.class);
        MetricCalcReadyCoordinatorQuartzJob wrapper = new MetricCalcReadyCoordinatorQuartzJob();
        Field field = MetricCalcReadyCoordinatorQuartzJob.class
                .getDeclaredField("coordinator");
        field.setAccessible(true);
        field.set(wrapper, coordinator);

        wrapper.execute(mock(JobExecutionContext.class));

        verify(coordinator).run();
    }
}
