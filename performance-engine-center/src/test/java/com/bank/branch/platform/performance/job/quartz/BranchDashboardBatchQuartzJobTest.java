package com.bank.branch.platform.performance.job.quartz;

import com.bank.branch.platform.performance.api.dto.BranchDashboardBatchDTO;
import com.bank.branch.platform.performance.service.BranchDashboardBatchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BranchDashboardBatchQuartzJobTest {

    private BranchDashboardBatchService batchService;
    private BranchDashboardBatchQuartzJob job;
    private JobExecutionContext context;

    @BeforeEach
    void setUp() {
        batchService = mock(BranchDashboardBatchService.class);
        job = new BranchDashboardBatchQuartzJob();
        ReflectionTestUtils.setField(job, "batchService", batchService);
        context = mock(JobExecutionContext.class);
        when(context.getMergedJobDataMap()).thenReturn(new JobDataMap());
    }

    @Test
    @DisplayName("未提供 dataDate 时委托 null，由 Service 自动选择最近完整金融日")
    void execute_withoutDateUsesServiceAutoSelection() throws Exception {
        when(batchService.runBatch(isNull(LocalDate.class), eq("AUTO"), isNull(String.class)))
                .thenReturn(BranchDashboardBatchDTO.builder().status("COMPLETE").build());

        job.execute(context);

        verify(batchService).runBatch(isNull(LocalDate.class), eq("AUTO"), isNull(String.class));
    }

    @Test
    @DisplayName("Service 返回 FAILED 时 Quartz 必须失败，避免治理日志误记 SUCCESS")
    void execute_failedBatchRaisesJobExecutionException() {
        when(batchService.runBatch(isNull(LocalDate.class), eq("AUTO"), isNull(String.class)))
                .thenReturn(BranchDashboardBatchDTO.builder().status("FAILED").build());

        assertThatThrownBy(() -> job.execute(context))
                .hasMessageContaining("FAILED")
                .isInstanceOf(org.quartz.JobExecutionException.class);
    }

    @Test
    @DisplayName("Quartz 包装类禁止并发且不作为 Spring Component 自动注册")
    void jobHasGovernanceLifecycleAnnotations() {
        assertThat(job.getClass().isAnnotationPresent(DisallowConcurrentExecution.class)).isTrue();
        assertThat(job.getClass().isAnnotationPresent(Component.class)).isFalse();
    }
}
