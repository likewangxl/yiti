package com.bank.branch.platform.common.db;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.ResultHandler;

import java.sql.Statement;
import java.util.Properties;

/**
 * 慢 SQL 拦截器
 * 执行时间超过阈值时输出 WARN 日志
 */
@Slf4j
@Getter
@Setter
@Intercepts({
    @Signature(type = StatementHandler.class, method = "query",
        args = {Statement.class, ResultHandler.class}),
    @Signature(type = StatementHandler.class, method = "update",
        args = {Statement.class})
})
public class SlowSqlInterceptor implements Interceptor {

    private long thresholdMs = 5000;

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        long start = System.currentTimeMillis();
        try {
            return invocation.proceed();
        } finally {
            long elapsed = System.currentTimeMillis() - start;
            if (elapsed >= thresholdMs) {
                StatementHandler handler = (StatementHandler) invocation.getTarget();
                String sql = handler.getBoundSql().getSql();
                log.warn("慢SQL检测: 耗时={}ms, 阈值={}ms, SQL={}", elapsed, thresholdMs, sql);
            }
        }
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
        String threshold = properties.getProperty("thresholdMs");
        if (threshold != null) {
            this.thresholdMs = Long.parseLong(threshold);
        }
    }
}
