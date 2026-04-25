package com.bank.branch.platform.report.config;

import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.pool.DruidPooledConnection;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.SQLException;

/**
 * SQL 探查独立只读数据源（Task M4.2.2，Green，对应 02 §5 + BR-1）.
 *
 * <p><strong>用途</strong>：与主 DataSource 物理隔离，使用独立账号
 * （通常仅授予 SELECT 权限 + 仅白名单表）。配置项：{@code rpt.datasource.read-only.*}.
 *
 * <p><strong>双层防御</strong>：
 * <ol>
 *   <li>{@code defaultReadOnly = true}（Druid 池级 setReadOnly）</li>
 *   <li>覆盖 {@code getConnection()} 强制 {@code conn.setReadOnly(true)}（Service 层进一步兜底）</li>
 * </ol>
 *
 * <p>测试环境（{@code application-test.yml} 未配置 {@code rpt.datasource.read-only.url}）时
 * 默认回填到主库 onepl_test_v103，仅用于守护 Bean 存在 + readOnly 开关；真实生产前必须替换为
 * {@code sql_probe_readonly} 等独立账号。
 */
@Configuration
@Slf4j
public class RptReadOnlyDataSourceConfig {

    /**
     * 独立只读 Druid DataSource.
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
        ds.setTestOnBorrow(false);
        ds.setTestWhileIdle(true);
        ds.setName("rptReadOnlyDataSource");

        log.info("[RptReadOnlyDataSource] initialized url={} maxActive={} defaultReadOnly=true",
                url, maxActive);
        return ds;
    }
}
