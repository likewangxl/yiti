package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.service.engine.SqlValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * EXPR/GROOVY 类型指标的主体集合取数器（V1.7）.
 *
 * <p>用法：把指标定义的 subject_sql（例如 "SELECT emp_id FROM ext_user_org WHERE ..."）
 * 执行后返回去重的 base_key 列表，供 MetricCalcService.executeGroovyAndPersist 遍历执行 EXPR.
 *
 * <p>SQL 安全：复用 SqlValidator 黑名单（与 SqlExecutor 同源），禁止 DROP/UPDATE/DELETE 等关键词.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubjectFetcher {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final SqlValidator sqlValidator;

    /**
     * 执行 subject_sql 取主体集合.
     *
     * @param subjectSql 必填，单列字符串结果（SELECT base_key FROM ...）
     * @param params     SQL 命名参数（如 dataDate / version）
     * @return 去重后的 base_key 列表
     * @throws PerfException PERF-40022 subject_sql 为空
     * @throws PerfException PERF-50004 SQL 执行 DataAccessException
     */
    public List<String> fetch(String subjectSql, Map<String, Object> params) {
        if (!StringUtils.hasText(subjectSql)) {
            throw new PerfException(PerfErrorCode.METRIC_SUBJECT_SQL_REQUIRED);
        }
        // 复用 SqlValidator 黑名单校验，禁止 DML/DDL 注入
        sqlValidator.validate(subjectSql);
        try {
            List<String> keys = jdbcTemplate.queryForList(subjectSql, params, String.class);
            // 去重后返回，保证调用方遍历时不重复计算同一主体
            return keys.stream().distinct().toList();
        } catch (DataAccessException e) {
            log.warn("[SubjectFetcher] subject_sql 执行失败: {}", e.getMessage());
            throw new PerfException(PerfErrorCode.METRIC_SUBJECT_SQL_FAILED, e, e.getMessage());
        }
    }
}
