package com.bank.branch.platform.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 开发环境 Druid 监控与 SQL 日志过滤器的静态配置契约。
 *
 * <p>本测试直接解析主 classpath 中的 application-dev.yml，不创建 Spring 上下文，
 * 因此不会连接数据库、启动调度器或发起外联。</p>
 */
class DevDruidLoggingProfileTest {

    private static final String PROFILE_RESOURCE = "application-dev.yml";

    @Test
    void devProfileShouldKeepDruidMonitoringAndFiveSecondSlowSqlLogging() throws IOException {
        PropertySource<?> properties = loadProfile();

        assertThat(properties.getProperty("spring.datasource.druid.stat-view-servlet.enabled"))
                .isEqualTo(true);
        assertThat(properties.getProperty("spring.datasource.druid.filter.stat.enabled"))
                .isEqualTo(true);
        assertThat(properties.getProperty("spring.datasource.druid.filter.stat.log-slow-sql"))
                .isEqualTo(true);
        assertThat(properties.getProperty("spring.datasource.druid.filter.stat.slow-sql-millis"))
                .isEqualTo(5000);
    }

    @Test
    void devProfileShouldDisableDruidSlf4jLogFilterAndRemoveItsLogger() throws IOException {
        PropertySource<?> properties = loadProfile();

        assertThat(properties.getProperty("spring.datasource.druid.filter.slf4j.enabled"))
                .isEqualTo(false);
        assertThat(properties.getProperty("logging.level.druid.sql.Statement"))
                .isNull();
    }

    private PropertySource<?> loadProfile() throws IOException {
        Resource resource = new ClassPathResource(PROFILE_RESOURCE);
        assertThat(resource.exists()).isTrue();
        List<PropertySource<?>> propertySources =
                new YamlPropertySourceLoader().load(PROFILE_RESOURCE, resource);
        assertThat(propertySources).hasSize(1);
        return propertySources.get(0);
    }
}
