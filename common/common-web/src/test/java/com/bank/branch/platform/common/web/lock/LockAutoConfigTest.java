package com.bank.branch.platform.common.web.lock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.AbstractDataSource;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * {@link LockAutoConfig} 的装配测试：锁能力始终可用，后台清理可独立关闭。
 */
class LockAutoConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(LockAutoConfig.class))
            // 仅验证 Bean 装配；若误触发数据库操作立即失败，绝不建立真实连接。
            .withBean(JdbcTemplate.class, () -> new JdbcTemplate(new NoConnectionDataSource()));

    private static final class NoConnectionDataSource extends AbstractDataSource {
        @Override
        public Connection getConnection() throws SQLException {
            throw new SQLException("本测试不允许数据库连接");
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            throw new SQLException("本测试不允许数据库连接");
        }
    }

    @Test
    @DisplayName("未配置开关时保持既有行为：LockManager 与过期锁清理任务均装配")
    void defaultConfiguration_assemblesLockManagerAndCleanupJob() {
        contextRunner.run(context -> {
            org.assertj.core.api.Assertions.assertThat(context).hasSingleBean(LockManager.class);
            org.assertj.core.api.Assertions.assertThat(context)
                    .hasSingleBean(LockAutoConfig.LockCleanupJob.class);
        });
    }

    @Test
    @DisplayName("关闭清理开关时保留业务锁，但不装配会写 PT_LOCK 的定时清理任务")
    void cleanupDisabled_keepsLockManagerWithoutCleanupJob() {
        contextRunner.withPropertyValues("platform.lock.cleanup.enabled=false")
                .run(context -> {
                    org.assertj.core.api.Assertions.assertThat(context).hasSingleBean(LockManager.class);
                    org.assertj.core.api.Assertions.assertThat(context)
                            .doesNotHaveBean(LockAutoConfig.LockCleanupJob.class);
                });
    }
}
