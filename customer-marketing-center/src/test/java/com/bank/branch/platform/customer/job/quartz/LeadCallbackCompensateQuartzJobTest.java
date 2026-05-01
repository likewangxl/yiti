package com.bank.branch.platform.customer.job.quartz;

import com.bank.branch.platform.customer.service.LeadCallbackCompensationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobKey;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * V1.8 — {@link LeadCallbackCompensateQuartzJob} 单元测试。
 * 验证 Quartz 入口顶层 try-catch 兜底语义保留（与原 scheduledScan() 一致）。
 */
class LeadCallbackCompensateQuartzJobTest {

    private LeadCallbackCompensationService compensationService;
    private LeadCallbackCompensateQuartzJob job;
    private JobExecutionContext context;

    @BeforeEach
    void setUp() {
        compensationService = mock(LeadCallbackCompensationService.class);
        job = new LeadCallbackCompensateQuartzJob(compensationService);

        context = mock(JobExecutionContext.class);
        JobDetail jobDetail = mock(JobDetail.class);
        when(context.getJobDetail()).thenReturn(jobDetail);
        when(jobDetail.getKey()).thenReturn(JobKey.jobKey("LEAD_CALLBACK_COMPENSATE", "DEFAULT"));
    }

    @Test
    @DisplayName("正常路径：execute 调用 service.scanAndCompensate 一次")
    void execute_callsScanAndCompensate_normalPath() throws Exception {
        job.execute(context);

        verify(compensationService, times(1)).scanAndCompensate();
    }

    @Test
    @DisplayName("异常兜底：service 抛出 RuntimeException 时 execute 不冒泡")
    void execute_swallowsServiceException_doesNotThrow() {
        doThrow(new RuntimeException("simulated lead compensation failure"))
                .when(compensationService).scanAndCompensate();

        // 顶层 try-catch 必须吞掉异常，避免 Quartz scheduler 把 trigger 标记为 ERROR
        assertThatCode(() -> job.execute(context)).doesNotThrowAnyException();

        verify(compensationService, times(1)).scanAndCompensate();
    }
}
