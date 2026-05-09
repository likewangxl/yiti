package com.bank.branch.platform.auth.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 缓存配置
 * 配置 RedisTemplate 的 Key/Value 序列化策略：
 * - Key 使用 StringRedisSerializer（可读性好，便于直接 redis-cli 查看）
 * - Value 使用 GenericJackson2JsonRedisSerializer（支持复杂对象的序列化与反序列化）
 */
@Configuration
@Profile("!test")
public class CacheConfig {

    /**
     * 通用 RedisTemplate，Key 为 String，Value 为 JSON 序列化的 Object
     * TTL、Key前缀等策略由 PermissionCacheService 管理，此处只声明序列化方式
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // 必须开启 default typing：GenericJackson2JsonRedisSerializer 接收
        // 自定义 ObjectMapper 时不会自动开启，序列化结果丢失 @class 字段，
        // 反序列化只能拿到 LinkedHashMap/ArrayList，导致 ClassCastException
        // (典型场景：DictService 缓存 List<SysDict> 命中后转 DTO 时报错)
        objectMapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY);
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(objectMapper);

        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        // Key 序列化为 UTF-8 字符串
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        // Value 序列化为 JSON
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);
        template.afterPropertiesSet();
        return template;
    }
}
