package com.bank.branch.platform.performance.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 缓存启用配置.
 * <p>启用 Spring Cache 注解 (@Cacheable / @CacheEvict), Redis 后端由 bootstrap 的 RedisAutoConfiguration 提供.
 * <p>v1.2: 本模块使用 Redis 做两件事:
 * <ul>
 *   <li>配置表读缓存 (perf:metric_def:*, perf:kpi_scheme:*, perf:sys_control:*, perf:alloc:*)</li>
 *   <li>分布式锁 (perf:sys_control:switch:*, perf:slot-alloc:*)</li>
 * </ul>
 * <p>提供 {@code RedisTemplate<String, Object>} Bean (JSON value, String key),
 * 在 bootstrap 已定义时使用 @ConditionalOnMissingBean 退让.
 */
@Configuration
@EnableCaching
public class PerformanceRedisConfig {

    /**
     * 提供 RedisTemplate<String, Object>, 被 SysControlFacade 等使用.
     * Key: StringRedisSerializer, Value: GenericJackson2JsonRedisSerializer.
     */
    @Bean
    @ConditionalOnMissingBean(name = "redisTemplate")
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(objectMapper);

        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);
        template.afterPropertiesSet();
        return template;
    }
}
