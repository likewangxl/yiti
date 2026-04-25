package com.bank.branch.platform.performance.job.quartz;

import com.bank.branch.platform.performance.job.SysControlCleanupJob;
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
class SysControlCleanupQuartzJobTest {

    @Mock private SysControlCleanupJob sysControlCleanupJob;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS) private JobExecutionContext context;

    @InjectMocks private SysControlCleanupQuartzJob quartzJob;

    @Test
    void execute_delegatesToBusinessJob_ignoresReturnValue() {
        // 不打桩 run() 的返回值（默认返回 0），断言 verify 委托即可，不验证返回值的消费
        assertDoesNotThrow(() -> quartzJob.execute(context));
        verify(sysControlCleanupJob).run();
    }

    @Test
    void execute_businessException_throwsJobExecutionException() {
        doThrow(new RuntimeException("业务异常")).when(sysControlCleanupJob).run();

        assertThatThrownBy(() -> quartzJob.execute(context))
            .isInstanceOf(JobExecutionException.class)
            .hasCauseInstanceOf(RuntimeException.class)
            .satisfies(ex -> assertThat(((JobExecutionException) ex).refireImmediately()).isFalse());
    }
}
