package com.bank.branch.platform.performance.service.engine;

import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 指标 SQL 校验器（V1.1 Task P2.2）.
 *
 * <p>策略：
 * <ul>
 *   <li><strong>白名单前缀</strong>：必须以 {@code SELECT} 或 {@code WITH}（CTE）开头。</li>
 *   <li><strong>黑名单关键字</strong>：全文正则匹配任意出现即拦截——
 *       {@code INSERT/UPDATE/DELETE/DROP/TRUNCATE/ALTER/CREATE/RENAME/GRANT/REVOKE/CALL}。
 *       同时拦截 {@code ; DELETE} 等多语句注入；注释开头的 DML/DDL 通过首关键字检测覆盖。</li>
 *   <li><strong>空 SQL</strong>：直接拦截（不下放到 SqlExecutor 避免 NPE/行为不明确）。</li>
 * </ul>
 *
 * <p>失败一律抛 {@link PerfErrorCode#METRIC_CALC_LOGIC_INVALID}（PERF-42201）.
 */
@Component
public class SqlValidator {

    /**
     * 黑名单关键字正则：在 SQL 任意位置出现即拦截（单词边界 \b 防止 "UPDATED_TIME" 误伤）.
     *
     * <p>为了同时覆盖：
     * <ul>
     *   <li>单独出现：{@code INSERT INTO ...}</li>
     *   <li>多语句：{@code SELECT ...; DELETE FROM ...}</li>
     *   <li>注释后 DML：{@code /* ... \*\/ INSERT ...}</li>
     * </ul>
     */
    private static final Pattern BLACKLIST = Pattern.compile(
            "(?is).*\\b(INSERT|UPDATE|DELETE|DROP|TRUNCATE|ALTER|CREATE|RENAME|GRANT|REVOKE|CALL)\\b.*");

    /** 允许的首关键字：SELECT 或 WITH（CTE）. */
    private static final Pattern ALLOWED_PREFIX = Pattern.compile(
            "(?is)^\\s*(/\\*.*?\\*/\\s*)?(SELECT|WITH)\\b.*");

    /**
     * 校验 SQL，失败抛 {@link PerfException}.
     *
     * @param sql 待校验的 SQL 文本
     * @throws PerfException 空 SQL / 含黑名单关键字 / 首关键字不在白名单
     */
    public void validate(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new PerfException(PerfErrorCode.METRIC_CALC_LOGIC_INVALID, "SQL 文本不能为空");
        }
        if (!ALLOWED_PREFIX.matcher(sql).matches()) {
            throw new PerfException(PerfErrorCode.METRIC_CALC_LOGIC_INVALID,
                    "SQL 必须以 SELECT 或 WITH 开头");
        }
        if (BLACKLIST.matcher(sql).matches()) {
            throw new PerfException(PerfErrorCode.METRIC_CALC_LOGIC_INVALID,
                    "SQL 包含禁止的关键字（DML/DDL/存储过程调用）");
        }
    }
}
