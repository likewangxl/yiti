package com.bank.branch.platform.performance.service.engine;

import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.job.StatShowArchiveDates;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 统计展示表指标 SQL 的历史表路由器。
 *
 * <p>该类只处理 SQL 文本与日期策略，不访问数据库，也不承担 SELECT/DML 安全校验。
 * 表名替换基于轻量词法扫描，仅处理 {@code FROM}/{@code JOIN} 后的真实关系表位置，
 * 因而不会把注释、字符串字面量、别名或相似名称中的文本误改。
 */
@Component
public final class StatShowSqlRouter {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final Set<String> MAIN_TABLES = Set.of(
            "XAN_M98_CUST_STAT_SHOW3",
            "XAN_M98_EMP_STAT_SHOW3");

    private static final Set<String> CLAUSE_TERMINATORS = Set.of(
            "WHERE", "GROUP", "ORDER", "HAVING", "LIMIT", "QUALIFY",
            "UNION", "EXCEPT", "INTERSECT", "WINDOW", "FETCH");

    private final Clock clock;

    /** 使用上海时区系统时钟。 */
    @Autowired
    public StatShowSqlRouter() {
        this(Clock.system(SHANGHAI));
    }

    /**
     * 使用指定时钟，便于纯单测固定业务日期。
     *
     * @param clock 时钟；实际日期始终按上海时区解释
     */
    public StatShowSqlRouter(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock").withZone(SHANGHAI);
    }

    /**
     * 路由指标 SQL。
     *
     * @param sql      指标 SQL（SELECT/WITH）
     * @param dataDate 选择的数据日期；引用目标主表时必须早于上海时区当天
     * @return 路由后的 SQL；超过 20 天的自然月末返回原主表 SQL
     */
    public String route(String sql, LocalDate dataDate) {
        if (sql == null || sql.isEmpty()) {
            return sql;
        }

        List<Token> tokens = tokenize(sql);
        Set<String> cteNames = collectCteNames(tokens);
        List<Replacement> targetTables = findTargetTables(tokens, cteNames);
        if (targetTables.isEmpty()) {
            return sql;
        }

        // 非目标 SQL 只需原样返回；全局重算入口另行调用 validateRecalcDate。
        validateRecalcDate(dataDate);

        LocalDate today = today();
        long ageDays = ChronoUnit.DAYS.between(dataDate, today);
        if (ageDays > 20) {
            if (!isMonthEnd(dataDate)) {
                throw new PerfException(PerfErrorCode.METRIC_RECALC_DATE_TOO_OLD);
            }
            return sql;
        }

        // 归档任务在 dataDate + 1 运行，因此旬后缀按该归档运行日计算。
        String suffix = StatShowArchiveDates.histSuffixForDataDate(dataDate);
        StringBuilder routed = new StringBuilder(sql.length() + targetTables.size() * suffix.length());
        int cursor = 0;
        for (Replacement replacement : targetTables) {
            routed.append(sql, cursor, replacement.start());
            routed.append(replaceToken(replacement.tableToken(), suffix));
            cursor = replacement.end();
        }
        routed.append(sql, cursor, sql.length());
        return routed.toString();
    }

    /** 语义更明确的别名，供调用方按“改写 SQL”命名使用。 */
    public String rewrite(String sql, LocalDate dataDate) {
        return route(sql, dataDate);
    }

    /**
     * 校验重算日期：必须早于上海时区当天。
     *
     * <p>该校验独立于 SQL 是否引用统计展示表，供 Facade/History 等在创建任务前做
     * 无副作用的全局日期预检；{@link #route(String, LocalDate)} 仍会再次调用，作为
     * 执行层兜底。
     */
    public void validateRecalcDate(LocalDate dataDate) {
        if (dataDate == null || !dataDate.isBefore(today())) {
            throw new PerfException(PerfErrorCode.METRIC_RECALC_DATE_INVALID);
        }
    }

    /** 兼容旧调用方的日期校验名称。 */
    public void validateDataDate(LocalDate dataDate) {
        validateRecalcDate(dataDate);
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private static boolean isMonthEnd(LocalDate date) {
        return date.getDayOfMonth() == date.lengthOfMonth();
    }

    private static List<Replacement> findTargetTables(List<Token> tokens, Set<String> cteNames) {
        List<Replacement> replacements = new ArrayList<>();
        Set<Integer> replacedSpans = new HashSet<>();
        for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);
            Relation relation;
            if (token.isWord("FROM") || token.isWord("JOIN") || token.isWord("STRAIGHT_JOIN")) {
                relation = parseRelation(tokens, i + 1);
            } else if (token.isIdentifier() && isCommaRelationStart(tokens, i)) {
                relation = parseRelation(tokens, i);
            } else {
                continue;
            }
            if (relation == null || relation.tableToken() == null) {
                continue;
            }
            Token table = relation.tableToken();
            String normalized = table.value().toUpperCase(Locale.ROOT);
            if (!MAIN_TABLES.contains(normalized)
                    || (!relation.qualified() && cteNames.contains(normalized))
                    || (relation.nextIndex() < tokens.size()
                    && tokens.get(relation.nextIndex()).isSymbol("("))
                    || endsWithHistorySuffix(normalized)) {
                continue;
            }
            if (replacedSpans.add(table.start())) {
                replacements.add(new Replacement(table.start(), table.end(), table));
            }
        }
        return replacements;
    }

    /** 识别 FROM foo, bar 形式的逗号关系表，避免把 SELECT 列表逗号当作表名。 */
    private static boolean isCommaRelationStart(List<Token> tokens, int start) {
        if (start == 0 || !tokens.get(start - 1).isSymbol(",")) {
            return false;
        }
        int depth = tokens.get(start).depth();
        for (int i = start - 2; i >= 0; i--) {
            Token token = tokens.get(i);
            if (token.depth() < depth) {
                break;
            }
            if (token.depth() > depth) {
                continue;
            }
            if (token.isWord("FROM") || token.isWord("JOIN")) {
                return true;
            }
            if (token.isWord("ON") || token.isWord("SELECT")
                    || CLAUSE_TERMINATORS.contains(token.value().toUpperCase(Locale.ROOT))) {
                return false;
            }
        }
        return false;
    }

    private static String replaceToken(Token table, String suffix) {
        String raw = table.raw();
        if (raw.charAt(0) == '`' || raw.charAt(0) == '"') {
            return raw.substring(0, raw.length() - 1) + suffix + raw.substring(raw.length() - 1);
        }
        return raw + suffix;
    }

    private static boolean endsWithHistorySuffix(String normalized) {
        return normalized.endsWith("_H1") || normalized.endsWith("_H2") || normalized.endsWith("_H3");
    }

    private static Relation parseRelation(List<Token> tokens, int start) {
        if (start >= tokens.size() || !tokens.get(start).isIdentifier()) {
            return null;
        }
        int index = start;
        Token last = tokens.get(index++);
        boolean qualified = false;
        while (index + 1 < tokens.size()
                && tokens.get(index).isSymbol(".")
                && tokens.get(index + 1).isIdentifier()) {
            qualified = true;
            last = tokens.get(index + 1);
            index += 2;
        }
        return new Relation(last, index, qualified);
    }

    private static Set<String> collectCteNames(List<Token> tokens) {
        Set<String> names = new HashSet<>();
        for (int i = 0; i + 2 < tokens.size(); i++) {
            Token name = tokens.get(i);
            if (!name.isIdentifier()) {
                continue;
            }
            int asIndex = i + 1;
            // 支持 WITH cte(col1, col2) AS (...) 形式，避免把 CTE 名误认为物理表。
            if (tokens.get(asIndex).isSymbol("(")) {
                int depth = 1;
                asIndex++;
                while (asIndex < tokens.size() && depth > 0) {
                    if (tokens.get(asIndex).isSymbol("(")) {
                        depth++;
                    } else if (tokens.get(asIndex).isSymbol(")")) {
                        depth--;
                    }
                    asIndex++;
                }
            }
            if (asIndex + 1 < tokens.size()
                    && tokens.get(asIndex).isWord("AS")
                    && tokens.get(asIndex + 1).isSymbol("(")) {
                names.add(name.value().toUpperCase(Locale.ROOT));
            }
        }
        return names;
    }

    private static List<Token> tokenize(String sql) {
        List<Token> tokens = new ArrayList<>();
        int length = sql.length();
        int index = 0;
        int depth = 0;
        while (index < length) {
            char c = sql.charAt(index);
            if (Character.isWhitespace(c)) {
                index++;
                continue;
            }
            if (c == '-' && index + 1 < length && sql.charAt(index + 1) == '-') {
                index = skipLineComment(sql, index + 2);
                continue;
            }
            if (c == '#') {
                index = skipLineComment(sql, index + 1);
                continue;
            }
            if (c == '/' && index + 1 < length && sql.charAt(index + 1) == '*') {
                index = skipBlockComment(sql, index + 2);
                continue;
            }
            if (c == '\'') {
                index = skipQuotedLiteral(sql, index, '\'');
                continue;
            }
            if (c == '`' || c == '"') {
                int end = readQuotedIdentifier(sql, index, c);
                String raw = sql.substring(index, end);
                String value = raw.substring(1, raw.length() - 1)
                        .replace(String.valueOf(c) + c, String.valueOf(c));
                tokens.add(new Token(raw, value, index, end, TokenKind.IDENTIFIER, depth));
                index = end;
                continue;
            }
            if (isIdentifierStart(c)) {
                int start = index++;
                while (index < length && isIdentifierPart(sql.charAt(index))) {
                    index++;
                }
                String raw = sql.substring(start, index);
                tokens.add(new Token(raw, raw, start, index, TokenKind.IDENTIFIER, depth));
                continue;
            }
            if (c == ')') {
                depth = Math.max(0, depth - 1);
            }
            tokens.add(new Token(String.valueOf(c), String.valueOf(c), index, index + 1,
                    TokenKind.SYMBOL, depth));
            if (c == '(') {
                depth++;
            }
            index++;
        }
        return tokens;
    }

    private static int skipLineComment(String sql, int index) {
        while (index < sql.length() && sql.charAt(index) != '\n' && sql.charAt(index) != '\r') {
            index++;
        }
        return index;
    }

    private static int skipBlockComment(String sql, int index) {
        while (index + 1 < sql.length()) {
            if (sql.charAt(index) == '*' && sql.charAt(index + 1) == '/') {
                return index + 2;
            }
            index++;
        }
        return sql.length();
    }

    private static int skipQuotedLiteral(String sql, int index, char quote) {
        int cursor = index + 1;
        while (cursor < sql.length()) {
            char c = sql.charAt(cursor);
            if (c == '\\' && cursor + 1 < sql.length()) {
                cursor += 2;
                continue;
            }
            if (c == quote) {
                if (cursor + 1 < sql.length() && sql.charAt(cursor + 1) == quote) {
                    cursor += 2;
                    continue;
                }
                return cursor + 1;
            }
            cursor++;
        }
        return sql.length();
    }

    private static int readQuotedIdentifier(String sql, int index, char quote) {
        int cursor = index + 1;
        while (cursor < sql.length()) {
            char c = sql.charAt(cursor);
            if (c == quote) {
                if (cursor + 1 < sql.length() && sql.charAt(cursor + 1) == quote) {
                    cursor += 2;
                    continue;
                }
                return cursor + 1;
            }
            cursor++;
        }
        return sql.length();
    }

    private static boolean isIdentifierStart(char c) {
        return Character.isLetter(c) || c == '_' || c == '$';
    }

    private static boolean isIdentifierPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }

    private enum TokenKind {
        IDENTIFIER,
        SYMBOL
    }

    private record Token(String raw, String value, int start, int end, TokenKind kind, int depth) {

        private boolean isIdentifier() {
            return kind == TokenKind.IDENTIFIER;
        }

        private boolean isWord(String expected) {
            return isIdentifier()
                    && raw.charAt(0) != '`'
                    && raw.charAt(0) != '"'
                    && value.equalsIgnoreCase(expected);
        }

        private boolean isSymbol(String expected) {
            return kind == TokenKind.SYMBOL && raw.equals(expected);
        }
    }

    private record Relation(Token tableToken, int nextIndex, boolean qualified) {
    }

    private record Replacement(int start, int end, Token tableToken) {
    }
}
