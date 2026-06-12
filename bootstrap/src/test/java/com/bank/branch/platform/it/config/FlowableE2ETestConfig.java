package com.bank.branch.platform.it.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.bank.branch.platform.governance.storage.ObsStorageClient;
import org.mybatis.spring.annotation.MapperScan;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Flowable E2E 集成测试配置。
 *
 * 保留真实 Flowable / MyBatis / MVC 链路，仅对 Redis 与 MinIO 提供最小 mock，
 * 避免本地外部依赖阻塞基于 H2 的工作流端到端验证。
 */
@TestConfiguration
@Profile("flowable-e2e")
@MapperScan(basePackages = {
        "com.bank.branch.platform.auth.mapper",
        "com.bank.branch.platform.governance.mapper",
        "com.bank.branch.platform.workflow.mapper"
})
public class FlowableE2ETestConfig {

    @Bean
    @Primary
    public RedisConnectionFactory redisConnectionFactory() {
        RedisConnectionFactory factory = Mockito.mock(RedisConnectionFactory.class);
        RedisConnection connection = Mockito.mock(RedisConnection.class);
        Mockito.when(factory.getConnection()).thenReturn(connection);
        Mockito.when(connection.isPipelined()).thenReturn(false);
        Mockito.when(connection.isQueueing()).thenReturn(false);
        return factory;
    }

    @Bean
    @Primary
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
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

    @Bean
    @Primary
    public ObsStorageClient obsStorageClient() {
        return Mockito.mock(ObsStorageClient.class);
    }
}
