package com.bank.branch.platform.redengine.service;

import com.bank.branch.platform.governance.api.JobApi;
import com.bank.branch.platform.governance.api.dto.RegisterJobCmd;
import com.bank.branch.platform.redengine.job.ReTaskScheduleJob;
import jakarta.annotation.PreDestroy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/** 任务域仅通过固定 JobApi 配置 Quartz 的契约测试。 */
@ExtendWith(MockitoExtension.class)
class ReTaskSchedulerTest {

    @Mock
    private JobApi jobApi;
    @Mock
    private ReTaskManagementService managementService;

    @Test
    void register_usesFixedJobKeyCronAndJobClass() {
        ReTaskScheduler scheduler = new ReTaskScheduler(jobApi, managementService);

        scheduler.register();

        ArgumentCaptor<RegisterJobCmd> captor = ArgumentCaptor.forClass(RegisterJobCmd.class);
        verify(jobApi).registerJob(captor.capture());
        RegisterJobCmd command = captor.getValue();
        assertThat(command.getJobKey()).isEqualTo(ReTaskScheduler.JOB_KEY);
        assertThat(command.getQuartzJobClass()).isEqualTo(ReTaskScheduleJob.class.getName());
        assertThat(command.getCronExpr()).isEqualTo(ReTaskScheduler.CRON);
        assertThat(command.isAllowManualTrigger()).isFalse();
    }

    @Test
    void run_delegatesToCurrentWindowReconciliation() {
        ReTaskScheduler scheduler = new ReTaskScheduler(jobApi, managementService);

        scheduler.run();

        verify(managementService).reconcileCurrentWindows();
    }

    @Test
    void applicationReady_registersFixedJobOnlyOncePerApplication() {
        ReTaskScheduler scheduler = new ReTaskScheduler(jobApi, managementService);

        scheduler.onApplicationReady();
        scheduler.onApplicationReady();

        verify(jobApi, times(1)).registerJob(org.mockito.ArgumentMatchers.any(RegisterJobCmd.class));
    }

    @Test
    void shutdown_unregistersOnlyOwnFixedJobAfterSuccessfulRegistration() {
        ReTaskScheduler scheduler = new ReTaskScheduler(jobApi, managementService);

        scheduler.register();
        clearInvocations(jobApi);
        scheduler.onShutdown();

        verify(jobApi).unregisterJob(ReTaskScheduler.JOB_KEY);
        verifyNoMoreInteractions(jobApi);
    }

    @Test
    void schedulerBeanIsDisabledWithQuartzProperty() {
        org.springframework.boot.autoconfigure.condition.ConditionalOnProperty condition =
                ReTaskScheduler.class.getAnnotation(
                        org.springframework.boot.autoconfigure.condition.ConditionalOnProperty.class);

        assertThat(condition).isNotNull();
        assertThat(condition.prefix()).isEqualTo("spring.quartz");
        assertThat(condition.name()).containsExactly("enabled");
        assertThat(condition.havingValue()).isEqualTo("true");
        assertThat(condition.matchIfMissing()).isTrue();
    }

    @Test
    void lifecycleHooksAreBoundToApplicationReadyAndPreDestroy() throws Exception {
        EventListener ready = ReTaskScheduler.class.getMethod("onApplicationReady")
                .getAnnotation(EventListener.class);
        PreDestroy shutdown = ReTaskScheduler.class.getMethod("onShutdown")
                .getAnnotation(PreDestroy.class);

        assertThat(ready).isNotNull();
        assertThat(ready.value()).containsExactly(ApplicationReadyEvent.class);
        assertThat(shutdown).isNotNull();
    }
}
