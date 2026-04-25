package com.bank.branch.platform.report.support;

import com.bank.branch.platform.report.exception.RptException;
import com.bank.branch.platform.report.enums.RptErrorCode;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.BinaryExpression;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.NotExpression;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.ExistsExpression;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.ParenthesedFromItem;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.statement.select.SetOperationList;
import net.sf.jsqlparser.util.TablesNamesFinder;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SQL 探查 AST 安全校验器（Task M4.1.1，Green）.
 *
 * <p>对应 02 §5 + BR-1 决策点 + 08 §1.1 白名单。校验链 8 步：
 * <ol>
 *   <li>SQL 长度上限 → RPT-42008</li>
 *   <li>禁用关键字正则兜底 → RPT-42003（先于解析，提供清晰错误码；防御 AST 漏检场景）</li>
 *   <li>JSqlParser 4.9 解析（拿 AST） → RPT-42001</li>
 *   <li>仅 Select 语句 → RPT-42007（SHOW / EXPLAIN / CALL 等非 Select 拒绝）</li>
 *   <li>禁 UNION / EXCEPT / INTERSECT（SetOperationList）→ RPT-42001</li>
 *   <li>子查询深度 ≤ {@code maxSubqueryDepth}（默认 3）→ RPT-42001</li>
 *   <li>白名单表（TablesNamesFinder）→ RPT-42002</li>
 *   <li>LIMIT 标准化（无则追加 maxRows / 超则截断 / 内则保留）</li>
 * </ol>
 *
 * <p>JSqlParser 4.9 适配说明（与 plan 伪代码差异）：
 * <ul>
 *   <li>不再有 {@code SelectBody} 和 {@code SubSelect}：{@link Select} 现在直接实现 {@code Expression}，
 *       子查询出现在表达式位置时就是 {@code Select} 实例（如 {@link ParenthesedSelect}）</li>
 *   <li>不再有 {@code SubJoin}：括号内 FROM item 用 {@link ParenthesedFromItem}</li>
 *   <li>不再有 {@code SelectExpressionItem} / {@code ItemsList}：
 *       {@link SelectItem#getExpression()} 直接给出表达式，
 *       {@link InExpression#getRightExpression()} 直接给出表达式（含 Select 子查询）</li>
 * </ul>
 *
 * <p>本类设计为 Spring 容器外可独立 new（构造器注入参数），方便单元测试；
 * 实际生产 bean 由 {@code RptSqlSafeConfig} 用配置项装配（M4.2 阶段）。
 */
@Slf4j
public class SqlSafeValidator {

    /** 白名单表名（小写）. */
    private final List<String> whitelistTables;

    /** 禁用关键字（大写）. */
    private final List<String> forbiddenKeywords;

    /** 最大返回行数（LIMIT clamp 上限），默认 1000. */
    private final int maxRows;

    /** SQL 长度上限. */
    private final int maxSqlLength;

    /** 子查询嵌套深度上限. */
    private final int maxSubqueryDepth;

    /** 关键字正则（pre-compiled，大小写不敏感）. */
    private final Pattern keywordPattern;

    /** LIMIT 末尾匹配（兼容 LIMIT n / LIMIT n,m）. */
    private final Pattern limitPattern;

    public SqlSafeValidator(List<String> whitelistTables,
                            List<String> forbiddenKeywords,
                            int maxRows,
                            int maxSqlLength,
                            int maxSubqueryDepth) {
        this.whitelistTables = whitelistTables.stream()
                .map(s -> s.toLowerCase(Locale.ROOT))
                .toList();
        this.forbiddenKeywords = forbiddenKeywords;
        this.maxRows = maxRows;
        this.maxSqlLength = maxSqlLength;
        this.maxSubqueryDepth = maxSubqueryDepth;
        this.keywordPattern = Pattern.compile(
                "(?i)\\b(" + String.join("|", forbiddenKeywords) + ")\\b");
        // 末尾 LIMIT n 或 LIMIT n,m，忽略尾随分号 / 空白
        this.limitPattern = Pattern.compile("(?i)\\bLIMIT\\s+(\\d+)(\\s*,\\s*\\d+)?\\s*;?\\s*$");
    }

    /**
     * 主校验入口：通过返回 {@link SqlSafeResult}，拒绝抛 {@link RptException}.
     */
    public SqlSafeResult validateAndNormalize(String sql) {
        // 1) 长度上限
        if (sql == null || sql.length() > maxSqlLength) {
            throw new RptException(RptErrorCode.SQL_LENGTH_EXCEEDED);
        }

        // 2) 禁用关键字正则兜底（先于解析，防御 AST 解析漏检 / DROP / DELETE / ...）
        if (keywordPattern.matcher(sql).find()) {
            throw new RptException(RptErrorCode.SQL_FORBIDDEN_KEYWORD);
        }

        // 3) JSqlParser 解析
        Statement stmt;
        try {
            stmt = CCJSqlParserUtil.parse(sql);
        } catch (JSQLParserException ex) {
            log.debug("[SqlSafeValidator] parse failed: {}", ex.getMessage());
            throw new RptException(RptErrorCode.SQL_PARSE_FAILED);
        }

        // 4) 仅 Select 语句
        if (!(stmt instanceof Select)) {
            throw new RptException(RptErrorCode.SQL_ONLY_SELECT_ALLOWED);
        }
        Select select = (Select) stmt;

        // 5) UNION / EXCEPT / INTERSECT 禁用（SetOperationList 也是 Select 子类）
        if (select instanceof SetOperationList) {
            throw new RptException(RptErrorCode.SQL_PARSE_FAILED);
        }

        // 6) 子查询嵌套深度
        int depth = computeDepth(select, 1);
        if (depth > maxSubqueryDepth) {
            log.debug("[SqlSafeValidator] subquery depth {} > max {}", depth, maxSubqueryDepth);
            throw new RptException(RptErrorCode.SQL_PARSE_FAILED);
        }

        // 7) 白名单表校验
        TablesNamesFinder finder = new TablesNamesFinder();
        List<String> tables = finder.getTableList(stmt);
        for (String t : tables) {
            if (!whitelistTables.contains(t.toLowerCase(Locale.ROOT))) {
                log.debug("[SqlSafeValidator] table {} not in whitelist", t);
                throw new RptException(RptErrorCode.SQL_TABLE_NOT_WHITELISTED);
            }
        }

        // 8) LIMIT 标准化
        String normalized = normalizeLimit(sql);
        return SqlSafeResult.allowed(normalized, tables);
    }

    /**
     * 递归计算子查询嵌套深度（plan L2443-L2483 完整实现，jsqlparser 4.9 适配）.
     *
     * <p>覆盖路径（使用 instanceof 显式递归）：
     * <ol>
     *   <li>FROM item：Table（叶子） / ParenthesedSelect（子查询） / ParenthesedFromItem（括号 from） 三种</li>
     *   <li>JOIN：每个 join 的 right item + ON 表达式</li>
     *   <li>WHERE：表达式中的 Select（IN / EXISTS / 比较 / AND / OR / NOT）</li>
     *   <li>HAVING：同 WHERE</li>
     *   <li>SELECT 项：列中可能 inline Select（如 SELECT (SELECT MAX(x) FROM t2) FROM t1）</li>
     * </ol>
     */
    private int computeDepth(Select select, int currentDepth) {
        // ParenthesedSelect 包装时，剥到内部 Select 继续递归（不增加深度，因为 wrap 不算嵌套层）
        if (select instanceof ParenthesedSelect) {
            Select inner = ((ParenthesedSelect) select).getSelect();
            if (inner != null) {
                return computeDepth(inner, currentDepth);
            }
            return currentDepth;
        }
        if (!(select instanceof PlainSelect)) {
            return currentDepth;
        }
        PlainSelect ps = (PlainSelect) select;
        int max = currentDepth;

        // 1) FROM item
        max = Math.max(max, depthOfFromItem(ps.getFromItem(), currentDepth));

        // 2) JOINs
        if (ps.getJoins() != null) {
            for (Join j : ps.getJoins()) {
                max = Math.max(max, depthOfFromItem(j.getRightItem(), currentDepth));
                if (j.getOnExpressions() != null) {
                    for (Expression onExpr : j.getOnExpressions()) {
                        max = Math.max(max, depthOfExpression(onExpr, currentDepth));
                    }
                }
            }
        }

        // 3) WHERE
        if (ps.getWhere() != null) {
            max = Math.max(max, depthOfExpression(ps.getWhere(), currentDepth));
        }

        // 4) HAVING
        if (ps.getHaving() != null) {
            max = Math.max(max, depthOfExpression(ps.getHaving(), currentDepth));
        }

        // 5) SELECT 项（4.9 的 SelectItem 直接拿 Expression）
        if (ps.getSelectItems() != null) {
            for (SelectItem<?> item : ps.getSelectItems()) {
                Expression expr = item.getExpression();
                if (expr != null) {
                    max = Math.max(max, depthOfExpression(expr, currentDepth));
                }
            }
        }

        return max;
    }

    /** FromItem 递归：Table 叶子 / ParenthesedSelect 进入子查询 / ParenthesedFromItem 递归. */
    private int depthOfFromItem(FromItem item, int currentDepth) {
        if (item == null) {
            return currentDepth;
        }
        // 4.9：(SELECT ...) FROM item 是 ParenthesedSelect，进入子查询深度 +1
        if (item instanceof ParenthesedSelect) {
            ParenthesedSelect ps = (ParenthesedSelect) item;
            Select inner = ps.getSelect();
            if (inner != null) {
                return computeDepth(inner, currentDepth + 1);
            }
            return currentDepth;
        }
        // 4.9：(table | join) FROM item 包装；递归内部 from item，并处理嵌套 join list
        if (item instanceof ParenthesedFromItem) {
            ParenthesedFromItem pf = (ParenthesedFromItem) item;
            int max = depthOfFromItem(pf.getFromItem(), currentDepth);
            if (pf.getJoins() != null) {
                for (Join j : pf.getJoins()) {
                    max = Math.max(max, depthOfFromItem(j.getRightItem(), currentDepth));
                }
            }
            return max;
        }
        // Table / 其他叶子（V1 简化）
        return currentDepth;
    }

    /** 表达式递归：覆盖 Select / IN / EXISTS / NOT / AND / OR / 比较运算. */
    private int depthOfExpression(Expression expr, int currentDepth) {
        if (expr == null) {
            return currentDepth;
        }
        // 4.9：表达式位置出现的 Select 子查询（含 ParenthesedSelect）—— 进入子查询深度 +1
        if (expr instanceof Select) {
            return computeDepth((Select) expr, currentDepth + 1);
        }
        // IN
        if (expr instanceof InExpression) {
            InExpression in = (InExpression) expr;
            int max = currentDepth;
            if (in.getLeftExpression() != null) {
                max = Math.max(max, depthOfExpression(in.getLeftExpression(), currentDepth));
            }
            if (in.getRightExpression() != null) {
                max = Math.max(max, depthOfExpression(in.getRightExpression(), currentDepth));
            }
            return max;
        }
        // EXISTS
        if (expr instanceof ExistsExpression) {
            return depthOfExpression(((ExistsExpression) expr).getRightExpression(), currentDepth);
        }
        // NOT
        if (expr instanceof NotExpression) {
            return depthOfExpression(((NotExpression) expr).getExpression(), currentDepth);
        }
        // AND / OR
        if (expr instanceof AndExpression) {
            AndExpression and = (AndExpression) expr;
            return Math.max(
                    depthOfExpression(and.getLeftExpression(), currentDepth),
                    depthOfExpression(and.getRightExpression(), currentDepth));
        }
        if (expr instanceof OrExpression) {
            OrExpression or = (OrExpression) expr;
            return Math.max(
                    depthOfExpression(or.getLeftExpression(), currentDepth),
                    depthOfExpression(or.getRightExpression(), currentDepth));
        }
        // 比较运算（=, >, <, !=, LIKE 等都继承自 BinaryExpression）
        if (expr instanceof BinaryExpression) {
            BinaryExpression bin = (BinaryExpression) expr;
            return Math.max(
                    depthOfExpression(bin.getLeftExpression(), currentDepth),
                    depthOfExpression(bin.getRightExpression(), currentDepth));
        }
        // 其他叶子（Column / LongValue / StringValue / Function 等）无子查询
        return currentDepth;
    }

    /**
     * LIMIT 标准化.
     *
     * <ul>
     *   <li>已有 LIMIT n 且 n &le; maxRows → 保留</li>
     *   <li>已有 LIMIT n 且 n &gt; maxRows → 替换为 LIMIT maxRows</li>
     *   <li>无 LIMIT → 追加 LIMIT maxRows</li>
     * </ul>
     *
     * <p>简化规则：不深入 SubSelect 内部 LIMIT，仅处理顶层尾部 LIMIT。
     */
    private String normalizeLimit(String sql) {
        String trimmed = sql.trim();
        if (trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }
        Matcher m = limitPattern.matcher(trimmed);
        if (m.find()) {
            int n = Integer.parseInt(m.group(1));
            if (n > maxRows) {
                // 替换末尾整个 LIMIT 子句为 LIMIT maxRows
                return m.replaceFirst("LIMIT " + maxRows);
            }
            return trimmed;
        }
        // 无 LIMIT 自动追加
        return trimmed + " LIMIT " + maxRows;
    }
}
