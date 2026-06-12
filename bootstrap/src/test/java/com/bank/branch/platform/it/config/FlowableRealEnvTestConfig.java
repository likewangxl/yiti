package com.bank.branch.platform.it.config;

import com.bank.branch.platform.governance.storage.ObsStorageClient;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/**
 * 真实 MySQL/Redis 联调配置。
 * <p>
 * 保留真实数据库、Redis Session、Flowable 与 MVC 鉴权链路，
 * 仅对本次范围外的对象存储(OBS)提供最小 mock，避免阻塞联调。
 * </p>
 */
@TestConfiguration
@Profile("flowable-real-env")
public class FlowableRealEnvTestConfig {

    @Bean
    @Primary
    public ObsStorageClient obsStorageClient() {
        return Mockito.mock(ObsStorageClient.class);
    }
}
