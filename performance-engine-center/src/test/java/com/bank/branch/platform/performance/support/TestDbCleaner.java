package com.bank.branch.platform.performance.support;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 测试数据库清理工具.
 * <p>并发测试类 ({@link PerformanceConcurrentTestBase}) 中必须手工调用清理, 因为没有事务自动回滚.
 * <p>使用前缀批量删除, 确保不会误删业务数据.
 */
public final class TestDbCleaner {

    private TestDbCleaner() {
    }

    /**
     * 按前缀删除指定表记录.
     *
     * @param jdbc        JdbcTemplate
     * @param table       表名
     * @param codeColumn  编码列名 (如 metric_code / scheme_code)
     * @param prefix      前缀 (如 TEST_SC_ / CONCUR_METRIC_), 将自动追加 %
     */
    public static int cleanByPrefix(JdbcTemplate jdbc, String table, String codeColumn, String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            throw new IllegalArgumentException("prefix must not be empty (prevent accidental delete all)");
        }
        return jdbc.update("DELETE FROM " + table + " WHERE " + codeColumn + " LIKE ?", prefix + "%");
    }

    /**
     * 按 ID 前缀删除.
     */
    public static int cleanById(JdbcTemplate jdbc, String table, String prefix) {
        return cleanByPrefix(jdbc, table, "id", prefix);
    }
}
