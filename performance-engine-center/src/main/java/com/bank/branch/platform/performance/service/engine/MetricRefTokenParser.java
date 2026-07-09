package com.bank.branch.platform.performance.service.engine;

import com.bank.branch.platform.performance.enums.MetricValueTimeEnum;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 指标引用 token 解析器.
 *
 * <p>把 exprText 里的 {@code M_xxx} 字面量解析成 {@link RefToken}：按<strong>最后一个 {@code __}</strong>
 * 拆分，尾段命中 {@link MetricValueTimeEnum} 保留后缀 → (baseCode, timePoint)；否则整体为 baseCode（今日）。
 * <p>去重保序：同一 token 只出现一次。
 */
public final class MetricRefTokenParser {

    private MetricRefTokenParser() {
    }

    /** 指标编码字面量：M_ 开头，后接字母数字下划线. */
    private static final Pattern METRIC_CODE_PATTERN = Pattern.compile("\\bM_[A-Za-z0-9_]+\\b");

    /** 单个引用 token 的解析结果. */
    public record RefToken(String token, String baseCode, MetricValueTimeEnum timePoint) {
    }

    /** 解析 exprText 中所有引用 token，去重保序. */
    public static List<RefToken> parse(String exprText) {
        if (exprText == null || exprText.isBlank()) {
            return List.of();
        }
        LinkedHashMap<String, RefToken> out = new LinkedHashMap<>();
        Matcher m = METRIC_CODE_PATTERN.matcher(exprText);
        while (m.find()) {
            String token = m.group();
            out.putIfAbsent(token, split(token));
        }
        return new ArrayList<>(out.values());
    }

    /** 单 token 拆分：尾段命中保留后缀→历史档；否则整体 baseCode（今日）. */
    static RefToken split(String token) {
        int idx = token.lastIndexOf("__");
        if (idx > 0) {
            String suffix = token.substring(idx + 2);
            MetricValueTimeEnum tp = MetricValueTimeEnum.bySuffix(suffix);
            if (tp != null) {
                return new RefToken(token, token.substring(0, idx), tp);
            }
        }
        return new RefToken(token, token, MetricValueTimeEnum.TODAY);
    }
}
