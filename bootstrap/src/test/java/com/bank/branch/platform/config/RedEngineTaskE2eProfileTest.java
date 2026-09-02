package com.bank.branch.platform.config;

import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.common.web.lock.LockAutoConfig;
import com.bank.branch.platform.common.web.lock.LockManager;
import com.bank.branch.platform.governance.config.QuartzConfig;
import com.bank.branch.platform.governance.enums.GovErrorCode;
import com.bank.branch.platform.governance.storage.ObsStorageClient;
import com.bank.branch.platform.redengine.service.ReTaskScheduler;
import com.bank.branch.platform.soap.config.SidecarRegistrationChecker;
import com.bank.branch.platform.soap.config.SoapNettyServer;
import org.flowable.spring.boot.condition.ConditionalOnProcessEngine;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.AbstractDataSource;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 红色引擎任务域隔离 profile 的运行时契约。
 *
 * <p>除检查 YAML 展开值外，本测试还把实际项目组件放进内存 ApplicationContext，验证
 * Flowable 基础引擎按组合根兼容要求启用，但异步执行、Quartz、边车、SOAP 和锁清理均关闭；
 * 不会启动完整应用、连接数据库或发起网络请求。</p>
 */
class RedEngineTaskE2eProfileTest {

    private static final String PROFILE_RESOURCE = "application-redengine-task-e2e.yml";

    @Test
    void profile_declaresYitTestAndAllIsolationFlags() throws IOException {
        PropertySource<?> properties = loadProfile();

        assertThat(properties.getProperty("server.address")).isEqualTo("127.0.0.1");
        assertThat(properties.getProperty("server.port")).isEqualTo("${REDENGINE_TASK_SERVER_PORT:0}");
        assertThat(properties.getProperty("spring.datasource.url"))
                .as("任务域 profile 必须固定在隔离 yit_test，禁止隐式连接其他 schema")
                .isInstanceOf(String.class)
                .asString()
                .contains("127.0.0.1:3306/yit_test");
        assertThat(properties.getProperty("spring.datasource.username"))
                .isEqualTo("${REDENGINE_TASK_DB_USERNAME}");
        assertThat(properties.getProperty("spring.datasource.password"))
                .isEqualTo("${REDENGINE_TASK_DB_PASSWORD}");
        assertThat(properties.getProperty("spring.sql.init.mode")).isEqualTo("never");
        assertThat(properties.getProperty("spring.session.jdbc.initialize-schema")).isEqualTo("never");
        assertThat(properties.getProperty("spring.session.jdbc.cleanup-cron")).isEqualTo("-");
        assertThat(properties.getProperty("server.servlet.session.cookie.name"))
                .isEqualTo("${REDENGINE_TASK_SESSION_COOKIE:REDENGINE_TASK_E2E_SESSION}");

        assertThat(properties.getProperty("spring.quartz.enabled")).isEqualTo(false);
        assertThat(properties.getProperty("spring.quartz.auto-startup")).isEqualTo(false);
        assertThat(properties.getProperty("spring.task.scheduling.enabled")).isEqualTo(false);
        assertThat(propertyValues(properties)).contains(
                "org.springframework.boot.autoconfigure.quartz.QuartzAutoConfiguration",
                "org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration");

        assertThat(properties.getProperty("flowable.process.enabled")).isEqualTo(true);
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

        assertThat(properties.getProperty("obs.enabled")).isEqualTo(false);
        assertThat(properties.getProperty("platform.sidecar.registration.enabled")).isEqualTo(false);
        assertThat(properties.getProperty("platform.soap.netty.enabled")).isEqualTo(false);
        assertThat(properties.getProperty("platform.lock.cleanup.enabled")).isEqualTo(false);
        assertThat(properties.getProperty("platform.notification.outbound.enabled")).isEqualTo(false);
    }

    @Test
    void processEngine_conditionUsesActualFlowablePropertyAndIsEnabled() {
        taskProfile()
                .withUserConfiguration(ProcessEngineProbeConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasBean("processEngineProbe");
                });
    }

    @Test
    void profile_doesNotExcludeFlowableProcessAutoConfiguration() throws IOException {
        PropertySource<?> properties = loadProfile();

        assertThat(propertyValues(properties))
                .noneMatch(value -> value.contains("ProcessEngineAutoConfiguration"));
    }

    @Test
    void actualProjectComponents_honorTaskProfileIsolationGates() {
        taskProfile()
                // LockManager 本身仍允许装配，但测试数据源拒绝所有连接，防止误触数据库。
                .withBean(JdbcTemplate.class, () -> new JdbcTemplate(new NoConnectionDataSource()))
                .withUserConfiguration(
                        QuartzConfig.class,
                        ReTaskScheduler.class,
                        SidecarRegistrationChecker.class,
                        SoapNettyServer.class,
                        LockAutoConfig.class,
                        ObsStorageClient.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getEnvironment().getProperty(
                            "platform.notification.outbound.enabled", Boolean.class)).isFalse();
                    assertThat(context).doesNotHaveBean(QuartzConfig.class);
                    assertThat(context).doesNotHaveBean(ReTaskScheduler.class);
                    assertThat(context).doesNotHaveBean(SidecarRegistrationChecker.class);
                    assertThat(context).doesNotHaveBean(SoapNettyServer.class);
                    assertThat(context).hasSingleBean(LockManager.class);
                    assertThat(context).doesNotHaveBean(LockAutoConfig.LockCleanupJob.class);
                    assertThat(context).hasSingleBean(ObsStorageClient.class);

                    // OBS Bean 仍保留以满足 FileService 类型依赖，但禁用后必须 fail-close，不能触网。
                    ObsStorageClient obs = context.getBean(ObsStorageClient.class);
                    assertThatThrownBy(() -> obs.getBytes("redengine-task-e2e/probe"))
                            .isInstanceOf(BizException.class)
                            .extracting(exception -> ((BizException) exception).getCode())
                            .isEqualTo(GovErrorCode.OBS_DISABLED.getCode());
                });
    }

    private ApplicationContextRunner taskProfile() {
        return new ApplicationContextRunner()
                .withInitializer(context -> addTaskProfile(context.getEnvironment().getPropertySources()));
    }

    private PropertySource<?> loadProfile() throws IOException {
        Resource resource = new ClassPathResource(PROFILE_RESOURCE);
        assertThat(resource.exists()).isTrue();
        List<PropertySource<?>> propertySources = new YamlPropertySourceLoader().load(PROFILE_RESOURCE, resource);
        assertThat(propertySources).hasSize(1);
        return propertySources.get(0);
    }

    private void addTaskProfile(MutablePropertySources propertySources) {
        try {
            propertySources.addFirst(loadProfile());
        } catch (IOException exception) {
            throw new IllegalStateException("无法加载 redengine-task-e2e profile", exception);
        }
    }

    private String[] propertyValues(PropertySource<?> properties) {
        assertThat(properties).isInstanceOf(EnumerablePropertySource.class);
        return Arrays.stream(((EnumerablePropertySource<?>) properties).getPropertyNames())
                .map(properties::getProperty)
                .map(String::valueOf)
                .toArray(String[]::new);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProcessEngine
    static class ProcessEngineProbeConfiguration {

        @Bean
        String processEngineProbe() {
            return "must-not-be-created";
        }
    }

    private static final class NoConnectionDataSource extends AbstractDataSource {

        @Override
        public Connection getConnection() throws SQLException {
            throw new SQLException("任务域 profile 测试禁止数据库连接");
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            throw new SQLException("任务域 profile 测试禁止数据库连接");
        }
    }
}
