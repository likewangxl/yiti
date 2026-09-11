package com.bank.branch.platform.performance.config;

import com.bank.branch.platform.governance.api.JobApi;
import com.bank.branch.platform.governance.api.dto.RegisterJobCmd;
import com.bank.branch.platform.performance.service.BranchDashboardBatchService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class BranchDashboardBatchJobRegistrarTest {

    @Test
    void register_usesGovernanceJobApiAndFixedJobKey() {
        JobApi jobApi = mock(JobApi.class);
        BranchDashboardBatchProperties properties = new BranchDashboardBatchProperties();
        properties.setGroupCode("ORG_GRP_PRIMARY_OPERATING_UNITS");
        properties.setCron("0 */5 * * * ?");

        new BranchDashboardBatchJobRegistrar(jobApi, properties).register();

        ArgumentCaptor<RegisterJobCmd> captor = ArgumentCaptor.forClass(RegisterJobCmd.class);
        verify(jobApi).registerJob(captor.capture());
        RegisterJobCmd cmd = captor.getValue();
        assertThat(cmd.getJobKey()).isEqualTo(BranchDashboardBatchService.TASK_TYPE);
        assertThat(cmd.getCronExpr()).isEqualTo("0 */5 * * * ?");
        assertThat(cmd.getQuartzJobClass())
                .isEqualTo("com.bank.branch.platform.performance.job.quartz.BranchDashboardBatchQuartzJob");
        assertThat(cmd.getJobData()).containsEntry("groupCode", "ORG_GRP_PRIMARY_OPERATING_UNITS");
        assertThat(cmd.isAllowManualTrigger()).isTrue();
    }
}
