package com.bank.branch.platform.performance.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 指标手动执行异步线程池（2026-07-22）。
 *
 * <p>用于 {@code MetricAsyncRunner.runAsync} 把「指标计算 + 级联刷新」挪到后台执行，
 * 接口线程只做校验 + 预建 PENDING 任务行后立即返回 taskId，前端轮询任务历史看进度。</p>
 *
 * <p>{@code @EnableAsync} 已由 {@code PerformanceAutoConfiguration} 在模块内开启，本类仅注册线程池 bean。</p>
 */
@Configuration
public class MetricExecuteAsyncConfig {

    /**
     * 指标执行专用线程池。
     *
     * <p>核心 2 / 最大 4：指标计算是 CPU + DB 双重的重活，并发过高会拖垮库；队列 200 覆盖
     * 单次批量上限（50）的数倍余量。<b>刻意用默认的 AbortPolicy 而非 CallerRunsPolicy</b>——
     * 队列满时若回落到调用线程执行，等于又把 HTTP 请求阻塞住，正是本次要消除的问题；
     * 拒绝后由 Facade 捕获并把预建任务行标 FAILED，前端立即看到明确失败而不是超时。</p>
     *
     * @return 指标执行线程池
     */
    @Bean("metricExecuteExecutor")
    public TaskExecutor metricExecuteExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(2);
        exec.setMaxPoolSize(4);
        exec.setQueueCapacity(200);
        exec.setThreadNamePrefix("metric-exec-");
        exec.initialize();
        return exec;
    }
}
