package com.bank.branch.platform.portal.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * portal-content-center 异步线程池配置
 * portalAggregateExecutor: A.1 工作台聚合的 5 路并行查询专用线程池
 */
@Configuration
public class PortalAsyncConfig {

    @Bean(name = "portalAggregateExecutor")
    public Executor portalAggregateExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(32);
        executor.setThreadNamePrefix("portal-agg-");
        executor.setKeepAliveSeconds(60);
        // queue 满时降级到调用线程，避免请求被静默丢弃
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
