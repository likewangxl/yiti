package com.bank.branch.platform.governance.service;

import com.bank.branch.platform.common.web.PageResult;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.governance.api.dto.ConfigDTO;
import com.bank.branch.platform.governance.entity.SysConfigKv;
import com.bank.branch.platform.governance.enums.GovErrorCode;
import com.bank.branch.platform.governance.mapper.ConfigMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 系统配置服务
 * <p>
 * 负责 sys_config_kv 表的读写操作，集成 Redis 缓存实现 cache-aside 模式。
 * 缓存 key 格式：gov:config:{configKey}，TTL 为 10 分钟。
 * 缓存中存储的是原始 configValue 字符串，而非实体对象。
 * 所有写操作完成后自动清除对应配置键的缓存，保证数据一致性。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ConfigMapper configMapper;
    private final ObjectMapper objectMapper;

    /** 缓存 key 前缀 */
    private static final String CACHE_PREFIX = "gov:config:";

    /** 缓存过期时间 */
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    /**
     * 获取配置值（cache-aside 模式）。
     * <p>
     * 优先从 Redis 缓存读取，缓存未命中时查询数据库并回填缓存。
     * 配置项不存在时返回 null。
     * </p>
     *
     * @param key 配置键
     * @return 配置值字符串，不存在时返回 null
     */
    public String getConfigValue(String key) {
        String cacheKey = CACHE_PREFIX + key;
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            log.debug("[ConfigService.getConfigValue] 缓存命中 configKey={}", key);
            return cached.toString();
        }
        // 缓存未命中，从数据库加载
        log.debug("[ConfigService.getConfigValue] 缓存未命中，查询数据库 configKey={}", key);
        SysConfigKv config = configMapper.selectByConfigKey(key);
        if (config == null) {
            return null;
        }
        // 缓存中存储原始 configValue 字符串
        redisTemplate.opsForValue().set(cacheKey, config.getConfigValue(), CACHE_TTL);
        return config.getConfigValue();
    }

    /**
     * 获取配置值（带默认值）。
     * 配置项不存在时返回指定默认值。
     *
     * @param key          配置键
     * @param defaultValue 默认值
     * @return 配置值或默认值
     */
    public String getConfigValue(String key, String defaultValue) {
        String value = getConfigValue(key);
        return value != null ? value : defaultValue;
    }

    /**
     * 获取配置值并转为指定类型。
     * <p>
     * 根据 sys_config_kv 表中的 value_type 字段进行类型转换：
     * - STRING：直接返回字符串（强转为 T）
     * - NUMBER：解析为 Long 或 Double
     * - BOOL：解析为 Boolean
     * - JSON：使用 Jackson ObjectMapper 反序列化
     * </p>
     *
     * @param key        配置键
     * @param targetType 目标类型
     * @param <T>        目标类型参数
     * @return 转换后的配置值
     * @throws BizException GOV-40002 配置项不存在
     * @throws BizException GOV-42205 类型转换失败
     */
    @SuppressWarnings("unchecked")
    public <T> T getConfigValue(String key, Class<T> targetType) {
        // 先尝试从缓存获取原始值
        String rawValue = getConfigValue(key);
        if (rawValue == null) {
            // 缓存未命中且数据库也无记录，需要查数据库以获取 valueType
            SysConfigKv config = configMapper.selectByConfigKey(key);
            if (config == null) {
                throw new BizException(GovErrorCode.CONFIG_NOT_FOUND.getCode(),
                        GovErrorCode.CONFIG_NOT_FOUND.getMessage());
            }
            rawValue = config.getConfigValue();
        }
        // 获取 valueType 用于类型转换判断
        SysConfigKv config = configMapper.selectByConfigKey(key);
        if (config == null) {
            throw new BizException(GovErrorCode.CONFIG_NOT_FOUND.getCode(),
                    GovErrorCode.CONFIG_NOT_FOUND.getMessage());
        }
        try {
            String valueType = config.getValueType();
            return convertValue(rawValue, valueType, targetType);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("[ConfigService.getConfigValue] 类型转换失败 configKey={}, targetType={}", key, targetType, e);
            throw new BizException(GovErrorCode.CONFIG_VALUE_TYPE_INVALID.getCode(),
                    GovErrorCode.CONFIG_VALUE_TYPE_INVALID.getMessage(), e);
        }
    }

    /**
     * 分页查询配置列表。
     *
     * @param status   状态过滤，为 null 时不过滤
     * @param pageNo   当前页码（从 1 开始）
     * @param pageSize 每页大小
     * @return 分页结果
     */
    public PageResult<ConfigDTO> listConfigs(String status, int pageNo, int pageSize) {
        log.debug("[ConfigService.listConfigs] status={}, pageNo={}, pageSize={}", status, pageNo, pageSize);
        int offset = (pageNo - 1) * pageSize;
        long total = configMapper.countAll(status);
        List<SysConfigKv> records = configMapper.selectAll(status, offset, pageSize);
        List<ConfigDTO> dtoList = records.stream()
                .map(this::toConfigDTO)
                .collect(Collectors.toList());
        return PageResult.of(pageNo, pageSize, total, dtoList);
    }

    /**
     * 更新配置项的值。
     * <p>
     * 根据 configKey 查找配置项，不存在时抛出 GOV-40002。
     * 更新值后清除 Redis 缓存。
     * </p>
     *
     * @param key   配置键
     * @param value 新的配置值
     * @throws BizException GOV-40002 配置项不存在
     */
    @Transactional
    public void updateConfig(String key, String value) {
        log.info("[ConfigService.updateConfig] configKey={}", key);
        SysConfigKv existing = configMapper.selectByConfigKey(key);
        if (existing == null) {
            throw new BizException(GovErrorCode.CONFIG_NOT_FOUND.getCode(),
                    GovErrorCode.CONFIG_NOT_FOUND.getMessage());
        }
        existing.setConfigValue(value);
        existing.setUpdatedTime(LocalDateTime.now());
        configMapper.updateById(existing);
        // 更新后清除缓存，保证下次读取到最新数据
        redisTemplate.delete(CACHE_PREFIX + key);
        log.info("[ConfigService.updateConfig] 配置更新成功 configKey={}", key);
    }

    // ── 私有方法 ──────────────────────────────────────────────────

    /**
     * 根据 valueType 将原始字符串转为目标类型。
     *
     * @param rawValue   原始配置值字符串
     * @param valueType  值类型（STRING/NUMBER/BOOL/JSON）
     * @param targetType 目标 Java 类型
     * @param <T>        目标类型参数
     * @return 转换后的值
     * @throws Exception 转换失败时抛出异常
     */
    @SuppressWarnings("unchecked")
    private <T> T convertValue(String rawValue, String valueType, Class<T> targetType) throws Exception {
        switch (valueType) {
            case "STRING":
                return (T) rawValue;
            case "NUMBER":
                if (targetType == Long.class || targetType == long.class) {
                    return (T) Long.valueOf(Long.parseLong(rawValue));
                } else if (targetType == Integer.class || targetType == int.class) {
                    return (T) Integer.valueOf(Integer.parseInt(rawValue));
                } else if (targetType == Double.class || targetType == double.class) {
                    return (T) Double.valueOf(Double.parseDouble(rawValue));
                }
                // 默认解析为 Long
                return (T) Long.valueOf(Long.parseLong(rawValue));
            case "BOOL":
                return (T) Boolean.valueOf(Boolean.parseBoolean(rawValue));
            case "JSON":
                return objectMapper.readValue(rawValue, targetType);
            default:
                throw new BizException(GovErrorCode.CONFIG_VALUE_TYPE_INVALID.getCode(),
                        GovErrorCode.CONFIG_VALUE_TYPE_INVALID.getMessage());
        }
    }

    /**
     * 将 SysConfigKv 实体转为 ConfigDTO。
     *
     * @param entity 配置实体
     * @return ConfigDTO
     */
    private ConfigDTO toConfigDTO(SysConfigKv entity) {
        ConfigDTO dto = new ConfigDTO();
        dto.setId(entity.getId());
        dto.setConfigKey(entity.getConfigKey());
        dto.setConfigValue(entity.getConfigValue());
        dto.setValueType(entity.getValueType());
        dto.setStatus(entity.getStatus());
        dto.setRemark(entity.getRemark());
        if (entity.getCreatedTime() != null) {
            dto.setCreatedTime(entity.getCreatedTime().toString());
        }
        if (entity.getUpdatedTime() != null) {
            dto.setUpdatedTime(entity.getUpdatedTime().toString());
        }
        return dto;
    }
}
