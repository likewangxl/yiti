package com.bank.branch.platform.redengine.service;

import com.bank.branch.platform.governance.api.JobApi;
import com.bank.branch.platform.governance.api.dto.RegisterJobCmd;
import com.bank.branch.platform.redengine.job.ReTaskScheduleJob;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

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
}
