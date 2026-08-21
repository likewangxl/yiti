package com.bank.branch.platform.performance.service.engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析指标 SQL 中受控的员工对象条件。
 *
 * <p>这里只识别两种固定模板：{@code AND EMP_ID = :objectId} 和旧版的
 * {@code AND (:objectId IS NULL OR EMP_ID = :objectId)}。字段只能是 EMP_ID（可带一个表别名），
 * 参数只能是 {@code objectId}；对象值始终由命名参数绑定，绝不拼进 SQL 文本。
 * <p>匹配前会跳过字符串、标识符引用和 SQL 注释，避免误改用户 SQL 中的普通文本。
 */
public final class ObjectFilterSqlResolver {

    private static final String EMP_COLUMN =
            "(?<column>(?:[A-Za-z_][A-Za-z0-9_$]*\\s*\\.\\s*)?EMP_ID)";
    private static final String OBJECT_PARAM = ":objectId(?![A-Za-z0-9_$])";

    private static final Pattern LEGACY_PREDICATE = Pattern.compile(
            "(?i)\\bAND\\s*\\(\\s*" + OBJECT_PARAM
                    + "\\s+IS\\s+NULL\\s+OR\\s+" + EMP_COLUMN
                    + "\\s*=\\s*" + OBJECT_PARAM + "\\s*\\)");

    private static final Pattern DIRECT_PREDICATE = Pattern.compile(
            "(?i)\\bAND\\s+" + EMP_COLUMN + "\\s*=\\s*" + OBJECT_PARAM);

    private ObjectFilterSqlResolver() {
    }

    /**
     * 按对象值解析受控员工条件。
     *
     * @param sql SQL 原文
     * @param objectId 对象值；null、空串和纯空白均表示不限定员工
     * @return 解析后的 SQL；有值时直接等值模板保持原样，旧模板化简为等值模板
     */
    public static String resolve(String sql, String objectId) {
        if (sql == null || sql.isEmpty()) {
            return sql;
        }
        boolean removePredicate = normalizeObjectId(objectId) == null;
        boolean[] protectedCharacters = findProtectedCharacters(sql);
        List<Replacement> replacements = new ArrayList<>();

        Matcher legacyMatcher = LEGACY_PREDICATE.matcher(sql);
        while (legacyMatcher.find()) {
            if (isUsableMatch(sql, protectedCharacters, legacyMatcher.start(), legacyMatcher.end())) {
                String replacement = removePredicate
                        ? ""
                        : "AND " + legacyMatcher.group("column") + " = :objectId";
                replacements.add(new Replacement(legacyMatcher.start(), legacyMatcher.end(), replacement));
            }
        }

        Matcher directMatcher = DIRECT_PREDICATE.matcher(sql);
        while (directMatcher.find()) {
            if (isUsableMatch(sql, protectedCharacters, directMatcher.start(), directMatcher.end())) {
                replacements.add(new Replacement(
                        directMatcher.start(), directMatcher.end(), removePredicate ? "" : null));
            }
        }

        if (replacements.isEmpty()) {
            return sql;
        }

        replacements.sort(Comparator.comparingInt(Replacement::start));
        StringBuilder resolved = new StringBuilder(sql.length());
        int copiedUntil = 0;
        for (Replacement replacement : replacements) {
            // Legacy predicate contains the direct equality text; never apply an overlapping match twice.
            if (replacement.start() < copiedUntil) {
                continue;
            }
            resolved.append(sql, copiedUntil, replacement.start());
            if (replacement.replacement() == null) {
                resolved.append(sql, replacement.start(), replacement.end());
            } else {
                resolved.append(replacement.replacement());
            }
            copiedUntil = replacement.end();
        }
        resolved.append(sql, copiedUntil, sql.length());
        return resolved.toString();
    }

    /** 归一化命名参数中的员工号；空白对象值按未提供处理。 */
    public static String normalizeObjectId(Object objectId) {
        if (objectId == null) {
            return null;
        }
        String normalized = String.valueOf(objectId).trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static boolean isUsableMatch(String sql, boolean[] protectedCharacters, int start, int end) {
        for (int i = start; i < end; i++) {
            if (protectedCharacters[i]) {
                return false;
            }
        }
        // Do not rewrite a parameter embedded in another token or a non-canonical cast/expression.
        if (end < sql.length()) {
            char next = sql.charAt(end);
            if (!Character.isWhitespace(next) && next != ')' && next != ',' && next != ';') {
                return false;
            }
        }
        return true;
    }

    /** 标记 SQL 字符串、引用标识符和注释，正则只可作用于剩余代码片段。 */
    private static boolean[] findProtectedCharacters(String sql) {
        boolean[] protectedCharacters = new boolean[sql.length()];
        int i = 0;
        while (i < sql.length()) {
            char current = sql.charAt(i);
            if (current == '\'' || current == '"' || current == '`') {
                i = markQuoted(sql, i, current, protectedCharacters);
            } else if (current == '-' && i + 1 < sql.length() && sql.charAt(i + 1) == '-') {
                i = markLineComment(sql, i, protectedCharacters, 2);
            } else if (current == '#') {
                i = markLineComment(sql, i, protectedCharacters, 1);
            } else if (current == '/' && i + 1 < sql.length() && sql.charAt(i + 1) == '*') {
                i = markBlockComment(sql, i, protectedCharacters);
            } else {
                i++;
            }
        }
        return protectedCharacters;
    }

    private static int markQuoted(String sql, int start, char quote, boolean[] protectedCharacters) {
        protectedCharacters[start] = true;
        int i = start + 1;
        while (i < sql.length()) {
            protectedCharacters[i] = true;
            if (sql.charAt(i) == '\\' && i + 1 < sql.length()) {
                protectedCharacters[i + 1] = true;
                i += 2;
                continue;
            } else if (sql.charAt(i) == quote) {
                if (i + 1 < sql.length() && sql.charAt(i + 1) == quote) {
                    protectedCharacters[i + 1] = true;
                    i += 2;
                    continue;
                } else {
                    return i + 1;
                }
            }
            i++;
        }
        return sql.length();
    }

    private static int markLineComment(String sql, int start, boolean[] protectedCharacters, int markerLength) {
        int i = start;
        while (i < sql.length()) {
            protectedCharacters[i] = true;
            if (sql.charAt(i) == '\n' || sql.charAt(i) == '\r') {
                return i + 1;
            }
            i++;
        }
        return Math.max(i, start + markerLength);
    }

    private static int markBlockComment(String sql, int start, boolean[] protectedCharacters) {
        int i = start;
        while (i < sql.length()) {
            protectedCharacters[i] = true;
            if (sql.charAt(i) == '*' && i + 1 < sql.length() && sql.charAt(i + 1) == '/') {
                protectedCharacters[++i] = true;
                return i + 1;
            }
            i++;
        }
        return sql.length();
    }

    private record Replacement(int start, int end, String replacement) {
    }
}
