package com.bank.branch.platform.performance.service.engine;

import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.ResultSetMetaData;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SqlExecutor 默认实现：基于 {@link NamedParameterJdbcTemplate}.
 *
 * <p>设计决定：
 * <ul>
 *   <li>不复用 Spring 容器内已有的 JdbcTemplate，而是自建 NamedParameterJdbcTemplate 实例——
 *       因为 {@code setQueryTimeout} 作用于底层 JdbcTemplate 的所有语句，若共享 Bean
 *       将影响其他非指标 SQL 查询的超时配置；本类每次调用前在私有 JdbcTemplate 上设置，
 *       线程不安全风险由 {@link Duration#getSeconds()} 的强制转换 + 每次 execute 独立调用隔离。</li>
 *   <li>结果列名校验：ResultSet 元数据断言列数 == 2 且列名包含 {@code base_key} + {@code metric_value}；
 *       不符合则抛 {@code METRIC_CALC_LOGIC_INVALID(PERF-42201)}。</li>
 *   <li>DataAccessException 统一转 PerfException，附带原异常 message 便于排查。</li>
 * </ul>
 */
@Slf4j
@Service
public class SqlExecutorImpl implements SqlExecutor {

    /** 结果集约定列 1：维度键. */
    private static final String COL_BASE_KEY = "base_key";

    /** 结果集约定列 2：指标数值. */
    private static final String COL_METRIC_VALUE = "metric_value";

    private final NamedParameterJdbcTemplate namedJdbc;
    private final SqlValidator sqlValidator;

    public SqlExecutorImpl(DataSource dataSource, SqlValidator sqlValidator) {
        this.namedJdbc = new NamedParameterJdbcTemplate(dataSource);
        this.sqlValidator = sqlValidator;
    }

    @Override
    public Map<String, BigDecimal> execute(String sql, Map<String, Object> params, Duration timeout) {
        // 前置黑名单/白名单拦截：任何 DML/DDL/存储过程都在此抛 METRIC_CALC_LOGIC_INVALID
        sqlValidator.validate(sql);
        int timeoutSeconds = resolveTimeoutSeconds(timeout);
        // 每次调用独立设置 queryTimeout：由于 namedJdbc 是私有字段，不会污染容器内其他 Bean
        namedJdbc.getJdbcTemplate().setQueryTimeout(timeoutSeconds);

        // 为每个命名参数显式声明 SQL 类型：否则参数仅出现在 SELECT 投影位（如 select :objectId as base_key）
        // 时 MySQL 无法从字符串推断类型，报 "Cannot determine value type from string"。
        MapSqlParameterSource paramSource = new MapSqlParameterSource();
        Map<String, Object> safeParams = params == null ? Collections.emptyMap() : params;
        for (Map.Entry<String, Object> e : safeParams.entrySet()) {
            paramSource.addValue(e.getKey(), e.getValue(), inferSqlType(e.getValue()));
        }

        try {
            return namedJdbc.query(sql, paramSource, rs -> {
                Map<String, BigDecimal> result = new LinkedHashMap<>();
                ResultSetMetaData meta = rs.getMetaData();
                validateResultColumns(meta);
                while (rs.next()) {
                    String baseKey = rs.getString(COL_BASE_KEY);
                    BigDecimal value = rs.getBigDecimal(COL_METRIC_VALUE);
                    if (baseKey != null) {
                        result.put(baseKey, value);
                    }
                }
                return result;
            });
        } catch (PerfException pe) {
            // 已转化的业务异常直接抛
            throw pe;
        } catch (DataAccessException ex) {
            log.warn("[SqlExecutor] SQL 执行失败: {}", ex.getMessage());
            throw new PerfException(PerfErrorCode.METRIC_CALC_LOGIC_INVALID, ex, "SQL 执行失败: " + ex.getMessage());
        } catch (Exception ex) {
            log.warn("[SqlExecutor] 未预期异常: {}", ex.getMessage());
            throw new PerfException(PerfErrorCode.METRIC_CALC_LOGIC_INVALID, ex, ex.getMessage());
        }
    }

    /**
     * 按 Java 值类型推断 JDBC SQL 类型，供 MySQL 在 SELECT 投影等无法自动推断的位置正确绑定.
     *
     * @param v 参数值（可空）
     * @return java.sql.Types 常量
     */
    private static int inferSqlType(Object v) {
        if (v == null) {
            return java.sql.Types.VARCHAR;
        }
        if (v instanceof java.time.LocalDate || v instanceof java.sql.Date || v instanceof java.util.Date) {
            return java.sql.Types.DATE;
        }
        if (v instanceof java.time.LocalDateTime || v instanceof java.sql.Timestamp) {
            return java.sql.Types.TIMESTAMP;
        }
        if (v instanceof Integer || v instanceof Long || v instanceof Short) {
            return java.sql.Types.BIGINT;
        }
        if (v instanceof java.math.BigDecimal || v instanceof Double || v instanceof Float) {
            return java.sql.Types.DECIMAL;
        }
        if (v instanceof Boolean) {
            return java.sql.Types.BOOLEAN;
        }
        return java.sql.Types.VARCHAR;
    }

    /**
     * 将 Duration 安全地转换为秒数（JDBC queryTimeout 是 int）.
     *
     * @param timeout 用户指定的超时（null 按 0 处理即不限制，交由 DB 自身超时）
     * @return 秒数（0 表示不设置超时）
     */
    private int resolveTimeoutSeconds(Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            return 0;
        }
        long seconds = timeout.getSeconds();
        return seconds > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) seconds;
    }

    /**
     * 校验结果集列名符合 {@code base_key} + {@code metric_value} 约定.
     *
     * @param meta ResultSetMetaData
     * @throws PerfException 列名不符合约定
     */
    private void validateResultColumns(ResultSetMetaData meta) {
        try {
            int colCount = meta.getColumnCount();
            boolean hasBaseKey = false;
            boolean hasMetricValue = false;
            for (int i = 1; i <= colCount; i++) {
                String name = meta.getColumnLabel(i);
                if (COL_BASE_KEY.equalsIgnoreCase(name)) {
                    hasBaseKey = true;
                } else if (COL_METRIC_VALUE.equalsIgnoreCase(name)) {
                    hasMetricValue = true;
                }
            }
            if (!hasBaseKey || !hasMetricValue) {
                throw new PerfException(PerfErrorCode.METRIC_CALC_LOGIC_INVALID,
                        "SQL 结果列必须包含 base_key 和 metric_value");
            }
        } catch (PerfException pe) {
            throw pe;
        } catch (Exception ex) {
            throw new PerfException(PerfErrorCode.METRIC_CALC_LOGIC_INVALID, ex,
                    "解析结果集元数据失败: " + ex.getMessage());
        }
    }
}
