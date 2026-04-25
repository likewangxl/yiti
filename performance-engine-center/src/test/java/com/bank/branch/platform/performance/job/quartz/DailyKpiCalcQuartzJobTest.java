package com.bank.branch.platform.performance.job.quartz;

import com.bank.branch.platform.performance.job.DailyKpiCalcJob;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DailyKpiCalcQuartzJobTest {

    @Mock private DailyKpiCalcJob dailyKpiCalcJob;
    /**
     * 使用 RETURNS_DEEP_STUBS 让 context.getJobDetail().getKey() 链式调用返回非 null
     * mock，否则 execute 异常分支里的 log.error 会先 NPE 再触发 JobExecutionException.
     */
    @Mock(answer = Answers.RETURNS_DEEP_STUBS) private JobExecutionContext context;

    @InjectMocks private DailyKpiCalcQuartzJob quartzJob;

    @Test
    void execute_delegatesToBusinessJob() {
        assertDoesNotThrow(() -> quartzJob.execute(context));
        verify(dailyKpiCalcJob).run();
    }

    @Test
    void execute_businessException_throwsJobExecutionException_refireFalse() {
        doThrow(new RuntimeException("业务异常")).when(dailyKpiCalcJob).run();

        assertThatThrownBy(() -> quartzJob.execute(context))
            .isInstanceOf(JobExecutionException.class)
            .hasCauseInstanceOf(RuntimeException.class)
            .satisfies(ex -> assertThat(((JobExecutionException) ex).refireImmediately()).isFalse());
    }
}
