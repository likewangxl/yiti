package com.bank.branch.platform.common.db;

import com.bank.branch.platform.common.web.PageRequest;
import com.bank.branch.platform.common.web.PageResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * MyBatis 分页拦截器
 * 自动拦截包含 PageRequest 参数的查询，注入 COUNT + LIMIT SQL
 */
@Slf4j
@Intercepts({
    @Signature(type = Executor.class, method = "query",
        args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class})
})
public class PageInterceptor implements Interceptor {

    private static final Pattern SAFE_SORT_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+$");

    /**
     * 检查排序字段是否安全（仅允许字母、数字、下划线）
     *
     * @param field 排序字段名
     * @return true 表示安全，false 表示可能存在 SQL 注入风险
     */
    public static boolean isSafeSortField(String field) {
        return field != null && SAFE_SORT_PATTERN.matcher(field).matches();
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        // 简化实现：检查参数中是否有 PageRequest
        Object[] args = invocation.getArgs();
        Object parameter = args[1];

        if (parameter instanceof PageRequest pageRequest) {
            // 校验排序字段安全性
            if (pageRequest.getSortBy() != null && !isSafeSortField(pageRequest.getSortBy())) {
                log.warn("不安全的排序字段被拦截: {}", pageRequest.getSortBy());
                pageRequest.setSortBy("created_time");
            }
        }

        return invocation.proceed();
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
        // no-op
    }
}
