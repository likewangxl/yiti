package com.bank.branch.platform.common.web.sidecar;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 边车客户端自动装配。
 * <p>通过 {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
 * 在 common-web 中默认开启，所有依赖 common-web 的业务模块都可直接 {@code @Autowired SidecarHttpClient}。
 */
@AutoConfiguration
@EnableConfigurationProperties(SidecarProperties.class)
public class SidecarAutoConfig {

    @Bean
    @ConditionalOnMissingBean
    public FlowIdGenerator flowIdGenerator() {
        return new FlowIdGenerator();
    }

    @Bean
    @ConditionalOnMissingBean
    public SidecarHttpClient sidecarHttpClient(SidecarProperties props,
                                               FlowIdGenerator idGen,
                                               ObjectMapper objectMapper) {
        return new SidecarHttpClient(props, idGen, objectMapper);
    }
}
