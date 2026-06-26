package com.bank.branch.platform.performance.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 评价任务导入异步线程池（2026-06-26）。
 *
 * <p>用于 {@code EvalAssignImportService.processImport} 把「逐行校验 + 入库」挪到后台执行，
 * 接口线程仅解析 + 建 IMPORTING 批次后立即返回 batchId，前端轮询批次状态。</p>
 *
 * <p>{@code @EnableAsync} 已由 {@code PerformanceAutoConfiguration} 在模块内开启，本类仅注册线程池 bean。</p>
 */
@Configuration
public class EvalImportAsyncConfig {

    /**
     * 评价导入专用线程池。
     *
     * <p>核心线程 1、最大 2、小队列；导入是低频管理操作，单文件序列化落库偏重，
     * 不宜高并发。{@code CallerRunsPolicy} 在队列满时由调用线程直接执行，
     * 保证不丢任务（以牺牲吞吐为代价，符合低频导入场景）。</p>
     *
     * @return 评价导入线程池
     */
    @Bean("evalImportExecutor")
    public TaskExecutor evalImportExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(1);
        exec.setMaxPoolSize(2);
        exec.setQueueCapacity(50);
        exec.setThreadNamePrefix("eval-import-");
        exec.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        exec.initialize();
        return exec;
    }
}
