package com.bank.branch.platform.performance.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link EvalImportAsyncConfig} 单元测试：评价导入专用线程池参数。
 */
class EvalImportAsyncConfigTest {

    @Test
    @DisplayName("evalImportExecutor：core=1 / max=2 / CallerRunsPolicy（低频不丢任务）")
    void evalImportExecutor_poolSizing() {
        ThreadPoolTaskExecutor exec =
                (ThreadPoolTaskExecutor) new EvalImportAsyncConfig().evalImportExecutor();

        assertThat(exec.getCorePoolSize()).isEqualTo(1);
        assertThat(exec.getMaxPoolSize()).isEqualTo(2);
        // CallerRunsPolicy：队列满时由调用线程直接执行，导入低频不丢任务
        assertThat(exec.getThreadPoolExecutor().getRejectedExecutionHandler())
                .isInstanceOf(java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy.class);
    }
}
