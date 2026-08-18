package com.bank.branch.platform.governance.facade;

import com.bank.branch.platform.governance.api.dto.JobTriggerRespDTO;
import com.bank.branch.platform.governance.service.JobService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** JobApi 新增触发/运行状态契约的 Facade 委托测试。 */
@ExtendWith(MockitoExtension.class)
class JobFacadeTest {

    @Mock
    JobService jobService;

    @InjectMocks
    JobFacade jobFacade;

    @Test
    void triggerJobByKey_delegatesToService() {
        JobTriggerRespDTO expected = JobTriggerRespDTO.builder()
                .jobKey("LEVEL1_METRIC_CALC")
                .triggerType("MANUAL")
                .build();
        when(jobService.triggerJobByKey("LEVEL1_METRIC_CALC", "MANUAL", "补算",
                "2026-08-17", "2026-08-16", "EMP001"))
                .thenReturn(expected);

        JobTriggerRespDTO actual = jobFacade.triggerJobByKey("LEVEL1_METRIC_CALC", "MANUAL", "补算",
                "2026-08-17", "2026-08-16", "EMP001");

        assertThat(actual).isSameAs(expected);
        verify(jobService).triggerJobByKey("LEVEL1_METRIC_CALC", "MANUAL", "补算",
                "2026-08-17", "2026-08-16", "EMP001");
    }

    @Test
    void isJobRunning_delegatesToService() {
        when(jobService.isJobRunning("LEVEL1_METRIC_CALC")).thenReturn(true);

        assertThat(jobFacade.isJobRunning("LEVEL1_METRIC_CALC")).isTrue();
        verify(jobService).isJobRunning("LEVEL1_METRIC_CALC");
    }
}
