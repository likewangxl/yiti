package com.bank.branch.platform.it.config;

import io.minio.MinioClient;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisConnection;

/**
 * 集成测试 mock 配置，通过 Mockito 提供业务模块依赖的外部服务 Bean。
 *
 * 测试环境中 Redis、MinIO、Flowable 等外部服务均不可用，
 * 此处提供 mock 实现避免启动失败。
 *
 * Redis mock 连接工厂返回空结果，模拟 "缓存始终未命中"，
 * 让业务逻辑降级到数据库查询，确保所有功能通过 DB 验证而非缓存。
 */
@TestConfiguration
public class TestMockConfig {

    /**
     * Mock RedisConnectionFactory — auth/gov 的 CacheConfig 需要注入此 Bean。
     * 预配置 getConnection() 返回 mock RedisConnection，
     * 其所有操作均返回空/null/0，模拟 "Redis 不可用" 场景。
     */
    @Bean
    @Primary
    public RedisConnectionFactory redisConnectionFactory() {
        RedisConnectionFactory factory = Mockito.mock(RedisConnectionFactory.class);
        RedisConnection connection = Mockito.mock(RedisConnection.class);
        Mockito.when(factory.getConnection()).thenReturn(connection);
        // 模拟连接未进入事务/管道模式
        Mockito.when(connection.isPipelined()).thenReturn(false);
        // 模拟 isQueueing 等基础方法不会抛异常
        Mockito.when(connection.isQueueing()).thenReturn(false);
        return factory;
    }

    /**
     * Mock MinioClient — governance 模块的 MinioConfig 需要此 Bean。
     * 注意: MinioConfig 自己会创建 MinioClient，使用 @Primary 覆盖。
     */
    @Bean
    @Primary
    public MinioClient minioClient() {
        return Mockito.mock(MinioClient.class);
    }

    /**
     * Mock Flowable 服务 — workflow 模块需要注入 Flowable 服务。
     * 测试环境禁用了 Flowable 自动配置且无 Flowable 内部表，
     * 所以所有操作返回空结果。
     */
    @Bean
    @Primary
    public RepositoryService repositoryService() {
        return Mockito.mock(RepositoryService.class);
    }

    @Bean
    @Primary
    public RuntimeService runtimeService() {
        return Mockito.mock(RuntimeService.class);
    }

    @Bean
    @Primary
    public TaskService taskService() {
        return Mockito.mock(TaskService.class);
    }

    @Bean
    @Primary
    public HistoryService historyService() {
        return Mockito.mock(HistoryService.class);
    }
}
