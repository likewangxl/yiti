package com.bank.branch.platform.performance.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * KPI 联动异步线程池（V1.7）.
 *
 * <p>用于 KpiCascadeListener.onMetricCompleted 异步触发 KPI 方案重算，
 * 避免阻塞 MetricExecuteQuartzJob 的执行线程.
 */
@Configuration
public class KpiCascadeAsyncConfig {

    /**
     * KPI 联动专用线程池.
     *
     * <p>核心线程 2，最大 4，队列容量 200；CallerRunsPolicy 在队列满时由调用线程直接执行，
     * 保证不丢任务（以牺牲调用线程吞吐为代价，符合低频联动场景）.
     */
    @Bean("kpiCascadeExecutor")
    public TaskExecutor kpiCascadeExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(2);
        exec.setMaxPoolSize(4);
        exec.setQueueCapacity(200);
        exec.setThreadNamePrefix("kpi-cascade-");
        exec.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        exec.initialize();
        return exec;
    }
}
