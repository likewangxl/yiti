package com.bank.branch.platform.it.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.minio.MinioClient;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.mockito.Mockito;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * 集成测试 mock 配置，通过 Mockito 提供业务模块依赖的外部服务 Bean。
 *
 * 测试环境中 Redis、MinIO、Flowable 等外部服务均不可用，
 * 此处提供 mock 实现避免启动失败。
 *
 * Redis mock 连接工厂返回空结果，模拟 "缓存始终未命中"，
 * 让业务逻辑降级到数据库查询，确保所有功能通过 DB 验证而非缓存。
 *
 * 注意: MyBatis @MapperScan 必须显式指定包路径，
 * 因为 MyBatisAutoConfiguration.AutoConfiguredMapperScannerRegistrar 在 JAR 依赖场景下
 * 无法自动发现 mapper 接口（JAR 内类扫描受限）。
 */
@TestConfiguration
@MapperScan(basePackages = {
        "com.bank.branch.platform.auth.mapper",
        "com.bank.branch.platform.governance.mapper",
        "com.bank.branch.platform.workflow.mapper"
})
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
     * 配置好的 RedisTemplate，使用注册了 JavaTimeModule 的 ObjectMapper。
     * 解决 DictService 等业务服务缓存 SysDict 等含 LocalDateTime 字段实体时的序列化问题。
     */
    @Bean
    @Primary
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        // LocalDateTime 序列化为 ISO-8601 字符串，而非时间戳数组
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(objectMapper);

        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(jsonSerializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(jsonSerializer);
        template.afterPropertiesSet();
        return template;
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
