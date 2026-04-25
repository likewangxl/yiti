package com.bank.branch.platform.report.support;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * SQL 安全校验结果（Task M4.1.1）.
 *
 * <p>由 {@link SqlSafeValidator#validateAndNormalize(String)} 返回，
 * 仅在校验通过时构造（拒绝场景直接抛 {@link com.bank.branch.platform.common.web.exception.BizException}）。
 *
 * <ul>
 *   <li>{@code allowed} —— 当前实现总是 {@code true}（拒绝走异常路径），保留字段方便上层将来扩展为软拒绝</li>
 *   <li>{@code normalizedSql} —— LIMIT 标准化后的 SQL，可直接交给 readOnly DataSource 执行</li>
 *   <li>{@code referencedTables} —— JSqlParser 解析出的全部表名（已通过白名单校验）</li>
 * </ul>
 */
@Data
@AllArgsConstructor
public class SqlSafeResult {

    private boolean allowed;

    private String normalizedSql;

    private List<String> referencedTables;

    public static SqlSafeResult allowed(String sql, List<String> tables) {
        return new SqlSafeResult(true, sql, tables);
    }
}
