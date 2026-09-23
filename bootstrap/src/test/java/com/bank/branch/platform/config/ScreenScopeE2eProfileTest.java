package com.bank.branch.platform.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 大屏范围真实联调专用 profile 的静态隔离契约。
 *
 * <p>本测试只解析 YAML，不创建 Spring 上下文，因此不会连接数据库、启动调度器或发起外联。</p>
 */
class ScreenScopeE2eProfileTest {

    private static final String PROFILE_RESOURCE = "application-screen-scope-e2e.yml";

    @Test
    void profile_bindsLoopbackYitiTestAndRequiresEnvironmentCredentials() throws IOException {
        PropertySource<?> properties = loadProfile();

        assertThat(properties.getProperty("server.address")).isEqualTo("127.0.0.1");
        assertThat(properties.getProperty("server.port")).isEqualTo("${SERVER_PORT:0}");

        assertYitiTestUrl(properties.getProperty("spring.datasource.url"));
        assertThat(properties.getProperty("spring.datasource.username"))
                .isEqualTo("${YITI_SCREEN_SCOPE_DB_USERNAME}");
        assertThat(properties.getProperty("spring.datasource.password"))
                .isEqualTo("${YITI_SCREEN_SCOPE_DB_PASSWORD}");

        assertYitiTestUrl(properties.getProperty("rpt.datasource.read-only.url"));
        assertThat(properties.getProperty("rpt.datasource.read-only.username"))
                .isEqualTo("${YITI_SCREEN_SCOPE_DB_USERNAME}");
        assertThat(properties.getProperty("rpt.datasource.read-only.password"))
                .isEqualTo("${YITI_SCREEN_SCOPE_DB_PASSWORD}");
    }

    @Test
    void profile_disablesStartupWritesSchedulersAndOutboundIntegrations() throws IOException {
        PropertySource<?> properties = loadProfile();

        assertThat(properties.getProperty("spring.sql.init.mode")).isEqualTo("never");
        assertThat(properties.getProperty("spring.session.jdbc.initialize-schema")).isEqualTo("never");
        assertThat(properties.getProperty("spring.session.jdbc.cleanup-cron")).isEqualTo("-");

        assertThat(properties.getProperty("spring.quartz.enabled")).isEqualTo(false);
        assertThat(properties.getProperty("spring.quartz.auto-startup")).isEqualTo(false);
        assertThat(propertyNames(properties)).contains("spring.autoconfigure.exclude[0]");
        assertThat(Arrays.stream(propertyNames(properties))
                .map(properties::getProperty)
                .map(String::valueOf))
                .contains("org.springframework.boot.autoconfigure.quartz.QuartzAutoConfiguration");

        assertThat(properties.getProperty("flowable.database-schema-update")).isEqualTo(false);
        assertThat(properties.getProperty("flowable.check-process-definitions")).isEqualTo(false);
        assertThat(properties.getProperty("flowable.async-executor-activate")).isEqualTo(false);
        assertThat(properties.getProperty("flowable.async-history-executor-activate")).isEqualTo(false);
        assertThat(properties.getProperty("flowable.process.async.executor.async-job-acquisition-enabled"))
                .isEqualTo(false);
        assertThat(properties.getProperty("flowable.process.async.executor.timer-job-acquisition-enabled"))
                .isEqualTo(false);
        assertThat(properties.getProperty("flowable.process.async-history.executor.async-job-acquisition-enabled"))
                .isEqualTo(false);
        assertThat(properties.getProperty("flowable.process.async-history.executor.timer-job-acquisition-enabled"))
                .isEqualTo(false);

        assertThat(properties.getProperty("platform.soap.netty.enabled")).isEqualTo(false);
        assertThat(properties.getProperty("platform.sidecar.registration.enabled")).isEqualTo(false);
        assertThat(properties.getProperty("obs.enabled")).isEqualTo(false);
        assertThat(properties.getProperty("platform.lock.cleanup.enabled")).isEqualTo(false);
        assertThat(properties.getProperty("perf.eval.import.compensation.enabled")).isEqualTo(false);
        assertThat(properties.getProperty("perf.scheduler.health-check.enabled")).isEqualTo(false);
        assertThat(properties.getProperty("perf.scheduler.startup-sync.enabled")).isEqualTo(false);
    }

    @Test
    void profile_enablesLocationStorageByDefaultWhileKeepingGeocodingOffAndAllowsStorageOverride()
            throws IOException {
        PropertySource<?> properties = loadProfile();

        assertThat(properties.getProperty("auth.org-location.storage-enabled"))
                .isEqualTo("${YITI_SCREEN_SCOPE_ORG_LOCATION_STORAGE_ENABLED:true}");
        assertThat(properties.getProperty("auth.org-location.geocoding-enabled"))
                .isEqualTo(false);

        assertThat(resolve(properties.getProperty("auth.org-location.storage-enabled"), Map.of()))
                .isEqualTo("true");
        assertThat(resolve(properties.getProperty("auth.org-location.storage-enabled"),
                Map.of("YITI_SCREEN_SCOPE_ORG_LOCATION_STORAGE_ENABLED", "false")))
                .isEqualTo("false");
        assertThat(resolve(properties.getProperty("auth.org-location.geocoding-enabled"), Map.of()))
                .isEqualTo("false");
    }

    private PropertySource<?> loadProfile() throws IOException {
        Resource resource = new ClassPathResource(PROFILE_RESOURCE);
        assertThat(resource.exists()).isTrue();
        List<PropertySource<?>> propertySources = new YamlPropertySourceLoader().load(PROFILE_RESOURCE, resource);
        assertThat(propertySources).hasSize(1);
        return propertySources.get(0);
    }

    private String[] propertyNames(PropertySource<?> properties) {
        assertThat(properties).isInstanceOf(EnumerablePropertySource.class);
        return ((EnumerablePropertySource<?>) properties).getPropertyNames();
    }

    private String resolve(Object value, Map<String, Object> environment) {
        assertThat(value).isNotNull();
        MutablePropertySources sources = new MutablePropertySources();
        sources.addFirst(new MapPropertySource("test-environment", environment));
        return new PropertySourcesPropertyResolver(sources)
                .resolveRequiredPlaceholders(String.valueOf(value));
    }

    private void assertYitiTestUrl(Object value) {
        assertThat(value).isInstanceOf(String.class);
        assertThat((String) value).contains("127.0.0.1:3306/yiti_test");
    }
}
