package com.bank.branch.platform.common.db.config;

import com.bank.branch.platform.common.db.AuditFieldFiller;
import com.bank.branch.platform.common.db.DruidConfig;
import com.bank.branch.platform.common.db.PageInterceptor;
import com.bank.branch.platform.common.db.SlowSqlInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * 数据库公共组件自动配置
 * 注册分页拦截器、审计字段填充器、慢SQL拦截器和 Druid 配置
 */
@AutoConfiguration
@Import(DruidConfig.class)
public class DbAutoConfiguration {

    /**
     * 注册分页拦截器
     *
     * @return PageInterceptor 实例
     */
    @Bean
    public PageInterceptor pageInterceptor() {
        return new PageInterceptor();
    }

    /**
     * 注册审计字段自动填充拦截器
     *
     * @return AuditFieldFiller 实例
     */
    @Bean
    public AuditFieldFiller auditFieldFiller() {
        return new AuditFieldFiller();
    }

    /**
     * 注册慢SQL拦截器
     *
     * @param thresholdMs 慢SQL阈值（毫秒），默认 5000
     * @return SlowSqlInterceptor 实例
     */
    @Bean
    public SlowSqlInterceptor slowSqlInterceptor(
            @Value("${platform.slow-sql-threshold-ms:5000}") long thresholdMs) {
        SlowSqlInterceptor interceptor = new SlowSqlInterceptor();
        interceptor.setThresholdMs(thresholdMs);
        return interceptor;
    }
}
