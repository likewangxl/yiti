package com.bank.branch.platform.governance.facade;

import com.bank.branch.platform.governance.api.ConfigApi;
import com.bank.branch.platform.governance.service.ConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 系统配置 Facade 实现
 * <p>
 * 实现 ConfigApi 接口，委托给 ConfigService 处理。
 * 所有方法均为同步调用，缓存由 ConfigService 内部管理。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigFacade implements ConfigApi {

    private final ConfigService configService;

    /**
     * 获取配置值
     *
     * @param configKey 配置键
     * @return 配置值，不存在时返回 Optional.empty()
     */
    @Override
    public Optional<String> getConfigValue(String configKey) {
        return Optional.ofNullable(configService.getConfigValue(configKey));
    }

    /**
     * 获取配置值（带默认值）
     *
     * @param configKey    配置键
     * @param defaultValue 默认值
     * @return 配置值或默认值
     */
    @Override
    public String getConfigValue(String configKey, String defaultValue) {
        return configService.getConfigValue(configKey, defaultValue);
    }

    /**
     * 获取配置值并转为指定类型
     *
     * @param configKey 配置键
     * @param type      目标类型
     * @param <T>       目标类型参数
     * @return 转换后的配置值
     */
    @Override
    public <T> T getConfigValue(String configKey, Class<T> type) {
        return configService.getConfigValue(configKey, type);
    }
}
