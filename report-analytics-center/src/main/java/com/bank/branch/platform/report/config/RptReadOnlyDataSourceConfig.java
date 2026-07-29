package com.bank.branch.platform.report.config;

import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.pool.DruidPooledConnection;
import com.alibaba.druid.spring.boot3.autoconfigure.DruidDataSourceWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.sql.SQLException;

/**
 * SQL 探查独立只读数据源 + 主 DataSource 显式声明（Task M4.2.2，Green，对应 02 §5 + BR-1）.
 *
 * <p><strong>背景（关键）</strong>：自定义 {@code @Bean public DataSource rptReadOnlyDataSource()}
 * 会触发 Spring Boot {@code DataSourceAutoConfiguration} 的 {@code @ConditionalOnMissingBean(DataSource.class)}
 * 短路，导致默认 {@code dataSource} 不再创建，进而让所有 AutoConfiguration 选到 readOnly 的 bean，
 * 触发 setReadOnly(true) 炸出 {@code Cannot execute statement in a READ ONLY transaction}.
 *
 * <p><strong>解决</strong>：本 Configuration 同时显式声明 {@code @Primary} 主 DataSource，
 * 复用 spring-boot 的 {@link DataSourceProperties}（绑定 {@code spring.datasource.*}），
 * 让 MyBatis / 业务 Service 全部走主 DataSource，仅 SQL 探查 service 显式 {@code @Qualifier} 切到 readOnly.
 *
 * <p><strong>readOnly 双层防御</strong>：
 * <ol>
 *   <li>{@code defaultReadOnly = true}（Druid 池级 setReadOnly）</li>
 *   <li>覆盖 {@code getConnection()} 强制 {@code conn.setReadOnly(true)}（Service 层进一步兜底）</li>
 * </ol>
 *
 * <p>测试环境（{@code application-test.yml} 未配置 {@code rpt.datasource.read-only.url}）时
 * 默认回填到主库 onepl_test_bootstrap，仅用于守护 Bean 存在 + readOnly 开关；真实生产前必须替换为
 * {@code sql_probe_readonly} 等独立账号。
 */
@Configuration
@Slf4j
public class RptReadOnlyDataSourceConfig {

    /**
     * 主 DataSource Properties（绑定 {@code spring.datasource.*}），
     * 显式声明以避免被自定义 readOnly DataSource 短路掉.
     */
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties primaryDataSourceProperties() {
        return new DataSourceProperties();
    }

    /**
     * 主 DataSource（@Primary）—— MyBatis / Service 默认走它.
     */
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.druid")
    public DataSource primaryDataSource(DataSourceProperties properties) {
        DataSource ds = properties.initializeDataSourceBuilder()
                .type(DruidDataSourceWrapper.class)
                .build();
        log.info("[RptPrimaryDataSource] initialized url={}", properties.getUrl());
        return ds;
    }

    /**
     * 独立只读 Druid DataSource（SQL 探查专用，物理隔离）.
     *
     * <p>配置回退顺序：
     * <ol>
     *   <li>{@code rpt.datasource.read-only.*}（生产推荐）</li>
     *   <li>{@code spring.datasource.*}（测试 / 单库环境兜底）</li>
     * </ol>
     */
    @Bean(name = "rptReadOnlyDataSource", destroyMethod = "close")
    @Qualifier("rptReadOnlyDataSource")
    public DataSource rptReadOnlyDataSource(
            @Value("${rpt.datasource.read-only.url:${spring.datasource.url}}") String url,
            @Value("${rpt.datasource.read-only.username:${spring.datasource.username}}") String username,
            @Value("${rpt.datasource.read-only.password:${spring.datasource.password}}") String password,
            @Value("${rpt.datasource.read-only.driver-class-name:${spring.datasource.driver-class-name:com.mysql.cj.jdbc.Driver}}") String driverClassName,
            @Value("${rpt.datasource.read-only.max-active:10}") int maxActive,
            @Value("${rpt.datasource.read-only.min-idle:2}") int minIdle,
            @Value("${rpt.datasource.read-only.query-timeout:30}") int queryTimeoutSec) {

        DruidDataSource ds = new DruidDataSource() {
            @Override
            public DruidPooledConnection getConnection() throws SQLException {
                DruidPooledConnection conn = super.getConnection();
                try {
                    conn.setReadOnly(true);
                } catch (SQLException e) {
                    log.warn("[RptReadOnlyDataSource] connection.setReadOnly(true) 失败，由 Service 层 setQueryTimeout 兜底", e);
                }
                return conn;
            }
        };
        ds.setUrl(url);
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setDriverClassName(driverClassName);
        ds.setDefaultReadOnly(true);
        ds.setMaxActive(maxActive);
        ds.setMinIdle(minIdle);
        ds.setInitialSize(minIdle);
        ds.setQueryTimeout(queryTimeoutSec);
        // maxWait：池耗尽时取连接的最大等待(ms)，避免 Druid 默认 -1（无限等待）导致
        // 异步导出线程 getConnection() 永久阻塞、任务永远停在 RUNNING。
        ds.setMaxWait(15000);
        ds.setTestOnBorrow(false);
        ds.setTestWhileIdle(true);
        ds.setName("rptReadOnlyDataSource");

        log.info("[RptReadOnlyDataSource] initialized url={} maxActive={} defaultReadOnly=true",
                url, maxActive);
        return ds;
    }
}
