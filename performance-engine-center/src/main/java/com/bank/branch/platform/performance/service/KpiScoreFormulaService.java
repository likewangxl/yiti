package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.performance.config.PerfEngineProperties;
import com.bank.branch.platform.performance.service.engine.GroovyExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * KPI 计分公式求值服务（KPI 分值计算专用）.
 *
 * <p>与 {@link KpiFormulaService}（{@code ${metric_code}} 占位符范式，KPI 总分加权求和）不同，
 * 本服务面向"指标项配置的计分公式"（前端 KpiRules.vue 编辑、{@code PERF_KPI_ITEM.formula} 持久化），
 * 公式用裸变量名书写并支持 {@code min} / {@code max} 函数，例：
 * <pre>min(actual / target * 100, 120)</pre>
 *
 * <p>可用变量：
 * <ul>
 *   <li>{@code actual} —— 实际值（指标结果表槽位值）</li>
 *   <li>{@code target} —— 目标值（目标管理表，未匹配默认 0）</li>
 *   <li>{@code base}   —— 基础值（目标管理表，未匹配默认 0）</li>
 *   <li>{@code weight} —— 权重</li>
 * </ul>
 *
 * <p>{@code min} / {@code max} 通过在脚本头部注入两个闭包实现（保留 BigDecimal 精度，
 * 不退化为 double），由 {@link GroovyExecutor} 沙盒执行。求值异常（如目标值为 0 触发除零）
 * 按"计算失败"上抛，由调用方按 fail-fast 语义停止任务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KpiScoreFormulaService {

    /**
     * 公式头部注入的闭包：
     * <ul>
     *   <li>{@code min}/{@code max}：取小/取大（保留 BigDecimal 精度）；</li>
     *   <li>{@code __sdiv}：安全除法，除数为 0 时该次除法记 0（让公式自身的 min/max/下限继续生效，
     *       而不是整条公式归零）。公式里的 {@code a / b} 会被 {@link #DIV_PATTERN} 改写为 {@code __sdiv(a, b)}。</li>
     * </ul>
     */
    private static final String HELPER_PREFIX =
            "def min = { a, b -> (a <= b) ? a : b }\n"
            + "def max = { a, b -> (a >= b) ? a : b }\n"
            + "def __sdiv = { a, b -> (b == 0) ? 0 : a / b }\n";

    /**
     * 匹配 {@code 项 / 项} 的除法（项 = 标识符[含中文] / 数字 / 单层括号组），改写成 {@code __sdiv(项, 项)}.
     * <p>避免目标值/基础值缺失导致分母为 0 时整条公式抛除零异常；除零时该次除法取 0。
     */
    private static final Pattern DIV_PATTERN = Pattern.compile(
            "(\\([^()]*\\)|[A-Za-z_\\u4e00-\\u9fa5][A-Za-z0-9_\\u4e00-\\u9fa5]*|\\d+(?:\\.\\d+)?)"
            + "\\s*/\\s*"
            + "(\\([^()]*\\)|[A-Za-z_\\u4e00-\\u9fa5][A-Za-z0-9_\\u4e00-\\u9fa5]*|\\d+(?:\\.\\d+)?)");

    private final GroovyExecutor groovyExecutor;
    private final PerfEngineProperties properties;

    /**
     * 按对象代入计分公式求得得分.
     *
     * <p>可用变量（中英双绑，前端公式提示用中文）：
     * <ul>
     *   <li>{@code actual} —— 实际值</li>
     *   <li>{@code target} —— 目标值</li>
     *   <li>{@code base} —— 基础值</li>
     *   <li>{@code weight} / {@code 权重} —— 权重（PERF_KPI_ITEM.weight）</li>
     *   <li>{@code 计分上限} —— 计分上限（PERF_KPI_ITEM.max_score）</li>
     *   <li>{@code 计分下限} —— 计分下限（PERF_KPI_ITEM.min_score）</li>
     * </ul>
     * 支持 {@code min} / {@code max} 函数。
     *
     * @param formula  计分公式文本（非空白）
     * @param actual   实际值（null 视为 0）
     * @param target   目标值（null 视为 0）
     * @param base     基础值（null 视为 0）
     * @param weight   权重（null 视为 0）
     * @param minScore 计分下限（null 视为 0）
     * @param maxScore 计分上限（null 视为 0）
     * @return 得分
     * @throws com.bank.branch.platform.performance.exception.PerfException 公式非法 / 求值异常（如除零）
     */
    public BigDecimal evalScore(String formula,
                                BigDecimal actual,
                                BigDecimal target,
                                BigDecimal base,
                                BigDecimal weight,
                                BigDecimal minScore,
                                BigDecimal maxScore) {
        // 把 a / b 改写为安全除法 __sdiv(a, b)：除数为 0 时该次除法取 0，公式自身的 min/max/下限照常生效
        String safeFormula = formula == null ? null : DIV_PATTERN.matcher(formula).replaceAll("__sdiv($1, $2)");
        String script = HELPER_PREFIX + safeFormula;
        BigDecimal w = weight == null ? BigDecimal.ZERO : weight;
        BigDecimal lo = minScore == null ? BigDecimal.ZERO : minScore;
        BigDecimal hi = maxScore == null ? BigDecimal.ZERO : maxScore;
        Map<String, Object> binding = new HashMap<>();
        binding.put("actual", actual == null ? BigDecimal.ZERO : actual);
        binding.put("target", target == null ? BigDecimal.ZERO : target);
        binding.put("base", base == null ? BigDecimal.ZERO : base);
        // 权重：英文 weight + 中文 权重 双绑
        binding.put("weight", w);
        binding.put("权重", w);
        // 计分上下限：对应 PERF_KPI_ITEM.max_score / min_score
        // 英文 minScore / maxScore + 中文 计分下限 / 计分上限 双绑（模板默认公式用英文裸变量名）
        binding.put("minScore", lo);
        binding.put("maxScore", hi);
        binding.put("计分上限", hi);
        binding.put("计分下限", lo);

        Duration timeout = Duration.ofSeconds(
                Math.max(1, properties == null ? 30 : properties.getSqlTimeoutSeconds()));
        try {
            BigDecimal score = groovyExecutor.execute(script, binding, timeout);
            log.debug("[KpiScoreFormula] formula='{}' actual={} target={} base={} weight={} min={} max={} score={}",
                    formula, actual, target, base, w, lo, hi, score);
            return score;
        } catch (RuntimeException e) {
            // 业务约定：除零（目标值/基础值缺失致分母为 0）不中断任务，记 0 分；
            // 其它求值错误（语法非法 / 沙盒违规等）仍上抛，由调用方 fail-fast。
            if (isDivisionByZero(e)) {
                log.warn("[KpiScoreFormula] 公式除零，记 0 分：formula='{}' target={} base={}",
                        formula, target, base);
                return BigDecimal.ZERO;
            }
            throw e;
        }
    }

    /**
     * 判断异常根因是否为除零（{@link ArithmeticException} 或其 "by zero" 文案）.
     *
     * @param t 异常
     * @return 是否除零
     */
    private boolean isDivisionByZero(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof ArithmeticException) {
                return true;
            }
            String msg = c.getMessage();
            if (msg != null && msg.toLowerCase().contains("by zero")) {
                return true;
            }
            if (c.getCause() == c) {
                break;
            }
        }
        return false;
    }
}
