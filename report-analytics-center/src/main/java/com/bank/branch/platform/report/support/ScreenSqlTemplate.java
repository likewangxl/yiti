package com.bank.branch.platform.report.support;

import com.bank.branch.platform.report.enums.RptErrorCode;
import com.bank.branch.platform.report.exception.RptException;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 大屏自定义 SQL 模板占位参数工具.
 *
 * <p>占位语法 {@code #{name}}，仅允许 orgCode / empId / dateFrom / dateTo 四个参数名；
 * {@link #parse} 产出 JDBC 可执行 SQL（? 占位）与按出现顺序的参数名列表（可重复）；
 * {@link #toValidatable} 将占位替换为常量字面量 '1'，供 JSqlParser 白名单校验使用
 * （校验与执行分离：先校验 substitute 后的静态 SQL，再以 PreparedStatement 绑定真实参数，杜绝拼接注入）。
 */
public final class ScreenSqlTemplate {

    private static final Pattern PLACEHOLDER = Pattern.compile("#\\{(\\w+)\\}");

    /** 允许的占位参数名（超出即 RPT-43002 拒绝） */
    public static final Set<String> ALLOWED_PARAMS = Set.of("orgCode", "empId", "dateFrom", "dateTo");

    private ScreenSqlTemplate() {
    }

    /** 解析结果：jdbcSql 为 ? 占位 SQL，paramNames 按出现顺序（同名重复出现则重复收集） */
    public record Parsed(String jdbcSql, List<String> paramNames) {
    }

    /** 模板 → JDBC SQL + 有序参数名 */
    public static Parsed parse(String template) {
        List<String> names = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        Matcher m = PLACEHOLDER.matcher(template);
        while (m.find()) {
            String name = m.group(1);
            if (!ALLOWED_PARAMS.contains(name)) {
                throw new RptException(RptErrorCode.SCREEN_DS_SQL_INVALID);
            }
            names.add(name);
            m.appendReplacement(sb, "?");
        }
        m.appendTail(sb);
        return new Parsed(sb.toString(), names);
    }

    /** 模板 → 可静态校验 SQL（占位替换为 '1'） */
    public static String toValidatable(String template) {
        StringBuilder sb = new StringBuilder();
        Matcher m = PLACEHOLDER.matcher(template);
        while (m.find()) {
            if (!ALLOWED_PARAMS.contains(m.group(1))) {
                throw new RptException(RptErrorCode.SCREEN_DS_SQL_INVALID);
            }
            m.appendReplacement(sb, "'1'");
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
