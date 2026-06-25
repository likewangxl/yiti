package com.bank.branch.platform.report.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 报表模块异步线程池配置.
 *
 * <p>SQL 探查「异步下载」用独立线程池跑后台导出，避免占用 Web 请求线程；
 * 队列有界 + CallerRunsPolicy 兜底，防止任务无限堆积。</p>
 */
@Configuration
public class RptAsyncConfig {

    /** SQL 探查异步导出专用线程池. */
    @Bean(name = "sqlProbeExportExecutor")
    public Executor sqlProbeExportExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("sql-export-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
