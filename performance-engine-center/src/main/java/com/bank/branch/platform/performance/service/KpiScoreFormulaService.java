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

    /** min / max 闭包前缀（保留入参原值与精度，按 Groovy 数值比较语义取小/取大）. */
    private static final String HELPER_PREFIX =
            "def min = { a, b -> (a <= b) ? a : b }\n"
            + "def max = { a, b -> (a >= b) ? a : b }\n";

    private final GroovyExecutor groovyExecutor;
    private final PerfEngineProperties properties;

    /**
     * 按对象代入计分公式求得得分.
     *
     * @param formula 计分公式文本（非空白；变量 actual/target/base/weight，支持 min/max）
     * @param actual  实际值（null 视为 0）
     * @param target  目标值（null 视为 0）
     * @param base    基础值（null 视为 0）
     * @param weight  权重（null 视为 0）
     * @return 得分
     * @throws com.bank.branch.platform.performance.exception.PerfException 公式非法 / 求值异常（如除零）
     */
    public BigDecimal evalScore(String formula,
                                BigDecimal actual,
                                BigDecimal target,
                                BigDecimal base,
                                BigDecimal weight) {
        String script = HELPER_PREFIX + formula;
        Map<String, Object> binding = new HashMap<>();
        binding.put("actual", actual == null ? BigDecimal.ZERO : actual);
        binding.put("target", target == null ? BigDecimal.ZERO : target);
        binding.put("base", base == null ? BigDecimal.ZERO : base);
        binding.put("weight", weight == null ? BigDecimal.ZERO : weight);

        Duration timeout = Duration.ofSeconds(
                Math.max(1, properties == null ? 30 : properties.getSqlTimeoutSeconds()));
        try {
            BigDecimal score = groovyExecutor.execute(script, binding, timeout);
            log.debug("[KpiScoreFormula] formula='{}' actual={} target={} base={} weight={} score={}",
                    formula, actual, target, base, weight, score);
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
