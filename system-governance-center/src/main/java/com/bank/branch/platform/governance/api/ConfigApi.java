package com.bank.branch.platform.governance.api;

import java.util.Optional;

/**
 * 系统配置对外API
 * <p>
 * 提供系统级键值配置的读取能力。
 * 高频调用，内部启用 Redis 缓存（TTL 10分钟）。
 * </p>
 */
public interface ConfigApi {

    /**
     * 获取配置值
     *
     * @param configKey 配置键
     * @return 配置值，不存在时返回 Optional.empty()
     */
    Optional<String> getConfigValue(String configKey);

    /**
     * 获取配置值（带默认值）
     * 配置项不存在时返回指定默认值
     *
     * @param configKey    配置键
     * @param defaultValue 默认值
     * @return 配置值或默认值
     */
    String getConfigValue(String configKey, String defaultValue);

    /**
     * 获取配置值并转为指定类型
     * 支持 Integer、Long、Boolean、String
     *
     * @param configKey 配置键
     * @param type      目标类型
     * @param <T>       目标类型参数
     * @return 转换后的配置值
     * @throws com.bank.branch.platform.common.web.exception.BizException 配置项不存在时抛 GOV-40002
     * @throws com.bank.branch.platform.common.web.exception.BizException 类型转换失败时抛 GOV-42205
     */
    <T> T getConfigValue(String configKey, Class<T> type);
}
