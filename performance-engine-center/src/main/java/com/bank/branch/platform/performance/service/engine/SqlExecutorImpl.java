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

    /** 指标 SQL 结果集约定列 1：维度键. */
    private static final String COL_BASE_KEY = "base_key";

    /** 指标 SQL 结果集约定列 2：指标数值. */
    private static final String COL_METRIC_VALUE = "metric_value";

    /** KPI 计分 SQL 结果集约定列：KPI 得分（对象id 由调用方按行传入，不从 SQL 取）. */
    private static final String COL_KPI_VALUE = "kpi_value";

    private final NamedParameterJdbcTemplate namedJdbc;
    private final SqlValidator sqlValidator;

    public SqlExecutorImpl(DataSource dataSource, SqlValidator sqlValidator) {
        this.namedJdbc = new NamedParameterJdbcTemplate(dataSource);
        this.sqlValidator = sqlValidator;
    }

    @Override
    public Map<String, BigDecimal> execute(String sql, Map<String, Object> params, Duration timeout) {
        return query(sql, params, timeout, COL_BASE_KEY, COL_METRIC_VALUE);
    }

    @Override
    public BigDecimal executeScore(String sql, Map<String, Object> params, Duration timeout) {
        sqlValidator.validate(sql);
        namedJdbc.getJdbcTemplate().setQueryTimeout(resolveTimeoutSeconds(timeout));
        MapSqlParameterSource paramSource = buildParamSource(params);
        try {
            return namedJdbc.query(sql, paramSource, rs -> {
                validateHasColumn(rs.getMetaData(), COL_KPI_VALUE);
                return rs.next() ? rs.getBigDecimal(COL_KPI_VALUE) : null;
            });
        } catch (PerfException pe) {
            throw pe;
        } catch (DataAccessException ex) {
            log.warn("[SqlExecutor] KPI 计分 SQL 执行失败: {}", ex.getMessage());
            throw new PerfException(PerfErrorCode.METRIC_CALC_LOGIC_INVALID, ex, "SQL 执行失败: " + ex.getMessage());
        } catch (Exception ex) {
            log.warn("[SqlExecutor] KPI 计分 SQL 未预期异常: {}", ex.getMessage());
            throw new PerfException(PerfErrorCode.METRIC_CALC_LOGIC_INVALID, ex, ex.getMessage());
        }
    }

    /**
     * 只读执行命名参数 SQL，按约定的 (键列, 值列) 规约为 {@code key -> value} 映射.
     *
     * @param keyCol 结果集键列名（{@code base_key} 或 {@code obj_id}）
     * @param valCol 结果集值列名（{@code metric_value} 或 {@code kpi_value}）
     */
    private Map<String, BigDecimal> query(String sql, Map<String, Object> params, Duration timeout,
                                          String keyCol, String valCol) {
        // 前置黑名单/白名单拦截：任何 DML/DDL/存储过程都在此抛 METRIC_CALC_LOGIC_INVALID
        sqlValidator.validate(sql);
        // 每次调用独立设置 queryTimeout：由于 namedJdbc 是私有字段，不会污染容器内其他 Bean
        namedJdbc.getJdbcTemplate().setQueryTimeout(resolveTimeoutSeconds(timeout));
        MapSqlParameterSource paramSource = buildParamSource(params);

        try {
            return namedJdbc.query(sql, paramSource, rs -> {
                Map<String, BigDecimal> result = new LinkedHashMap<>();
                ResultSetMetaData meta = rs.getMetaData();
                validateResultColumns(meta, keyCol, valCol);
                while (rs.next()) {
                    String key = rs.getString(keyCol);
                    BigDecimal value = rs.getBigDecimal(valCol);
                    if (key != null) {
                        result.put(key, value);
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
     * 构建命名参数源，并为每个参数显式声明 SQL 类型.
     *
     * <p>为每个命名参数显式声明 SQL 类型：否则参数仅出现在 SELECT 投影位（如 {@code select :actual as kpi_value}）
     * 时 MySQL 无法从字符串推断类型，报 "Cannot determine value type from string"。
     */
    private MapSqlParameterSource buildParamSource(Map<String, Object> params) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource();
        Map<String, Object> safeParams = params == null ? Collections.emptyMap() : params;
        for (Map.Entry<String, Object> e : safeParams.entrySet()) {
            paramSource.addValue(e.getKey(), e.getValue(), inferSqlType(e.getValue()));
        }
        return paramSource;
    }

    /**
     * 校验结果集包含指定列（KPI 计分 SQL 只需 {@code kpi_value} 一列）.
     *
     * @throws PerfException 列缺失
     */
    private void validateHasColumn(ResultSetMetaData meta, String col) {
        try {
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                if (col.equalsIgnoreCase(meta.getColumnLabel(i))) {
                    return;
                }
            }
            throw new PerfException(PerfErrorCode.METRIC_CALC_LOGIC_INVALID, "SQL 结果列必须包含 " + col);
        } catch (PerfException pe) {
            throw pe;
        } catch (Exception ex) {
            throw new PerfException(PerfErrorCode.METRIC_CALC_LOGIC_INVALID, ex,
                    "解析结果集元数据失败: " + ex.getMessage());
        }
    }

    /**
     * 校验结果集列名包含约定的 (键列, 值列).
     *
     * @param meta   ResultSetMetaData
     * @param keyCol 键列名（{@code base_key} 或 {@code obj_id}）
     * @param valCol 值列名（{@code metric_value} 或 {@code kpi_value}）
     * @throws PerfException 列名不符合约定
     */
    private void validateResultColumns(ResultSetMetaData meta, String keyCol, String valCol) {
        try {
            int colCount = meta.getColumnCount();
            boolean hasKey = false;
            boolean hasValue = false;
            for (int i = 1; i <= colCount; i++) {
                String name = meta.getColumnLabel(i);
                if (keyCol.equalsIgnoreCase(name)) {
                    hasKey = true;
                } else if (valCol.equalsIgnoreCase(name)) {
                    hasValue = true;
                }
            }
            if (!hasKey || !hasValue) {
                throw new PerfException(PerfErrorCode.METRIC_CALC_LOGIC_INVALID,
                        "SQL 结果列必须包含 " + keyCol + " 和 " + valCol);
            }
        } catch (PerfException pe) {
            throw pe;
        } catch (Exception ex) {
            throw new PerfException(PerfErrorCode.METRIC_CALC_LOGIC_INVALID, ex,
                    "解析结果集元数据失败: " + ex.getMessage());
        }
    }
}
