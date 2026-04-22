package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.performance.config.PerfEngineProperties;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.service.engine.GroovyExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * KPI 公式解析服务（Task P4.1）.
 *
 * <p>职责：把 KPI item 的公式文本（形如 {@code ${M_EMP_A} * 0.3 + ${M_EMP_B} * 0.7}）
 * 中的 {@code ${metric_code}} 占位符替换为合法的 Groovy 变量名，再通过 {@link GroovyExecutor}
 * 沙盒化计算得到最终分值。
 *
 * <p>流程：
 * <ol>
 *   <li>扫描公式中所有 {@code ${xxx}} 占位符，收集变量名集合。</li>
 *   <li>校验 {@code metricValues} 包含全部变量且值非 null，缺失抛
 *       {@link PerfErrorCode#VALIDATION_FAILED}。</li>
 *   <li>将 {@code ${xxx}} 全部替换为 {@code xxx}（去掉包围符），得到纯 Groovy 表达式。</li>
 *   <li>委托 {@link GroovyExecutor#execute} 执行，超时时间复用 {@link PerfEngineProperties#getSqlTimeoutSeconds}。</li>
 * </ol>
 *
 * <p>错误码分工：
 * <ul>
 *   <li>公式本身为空（null / blank）或非法 → {@link PerfErrorCode#METRIC_CALC_LOGIC_INVALID}</li>
 *   <li>变量值缺失 / null → {@link PerfErrorCode#VALIDATION_FAILED}</li>
 *   <li>Groovy 沙盒拦截 / 语法错误 → 由 {@link GroovyExecutor} 抛出
 *       {@link PerfErrorCode#METRIC_CALC_LOGIC_INVALID}</li>
 * </ul>
 */
@Slf4j
@Service
public class KpiFormulaService {

    /** 公式变量占位符：{@code ${XXX}} 形式，变量名仅允许 [A-Za-z0-9_]. */
    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)\\}");

    private final GroovyExecutor groovyExecutor;
    private final PerfEngineProperties properties;

    public KpiFormulaService(GroovyExecutor groovyExecutor, PerfEngineProperties properties) {
        this.groovyExecutor = groovyExecutor;
        this.properties = properties;
    }

    /**
     * 计算 KPI 公式.
     *
     * @param formula      含 {@code ${metric_code}} 占位符的公式文本
     * @param metricValues 变量实际值（metricCode → value），禁止空 value
     * @return 计算结果（{@link BigDecimal}）
     * @throws PerfException 公式为空 / 变量缺失 / 变量值为 null / Groovy 沙盒违规
     */
    public BigDecimal eval(String formula, Map<String, BigDecimal> metricValues) {
        if (formula == null || formula.isBlank()) {
            throw new PerfException(PerfErrorCode.METRIC_CALC_LOGIC_INVALID, "KPI 公式不能为空");
        }
        Map<String, BigDecimal> safeValues = metricValues == null ? Map.of() : metricValues;

        // 1) 扫描占位符
        Set<String> vars = extractVariables(formula);

        // 2) 变量校验：存在 + 非 null
        for (String var : vars) {
            if (!safeValues.containsKey(var)) {
                throw new PerfException(PerfErrorCode.VALIDATION_FAILED,
                        "KPI 公式缺少变量值: " + var);
            }
            if (safeValues.get(var) == null) {
                throw new PerfException(PerfErrorCode.VALIDATION_FAILED,
                        "KPI 公式变量值为 null: " + var);
            }
        }

        // 3) 替换占位符：${xxx} → xxx（Groovy 合法变量名）
        String expr = VAR_PATTERN.matcher(formula).replaceAll("$1");

        // 4) 组装 Binding：仅下发用到的变量，避免无关污染
        Map<String, Object> binding = new HashMap<>();
        for (String var : vars) {
            binding.put(var, safeValues.get(var));
        }

        Duration timeout = Duration.ofSeconds(
                Math.max(1, properties == null ? 30 : properties.getSqlTimeoutSeconds()));
        BigDecimal result = groovyExecutor.execute(expr, binding, timeout);
        log.debug("[KpiFormula] formula='{}' vars={} result={}", formula, vars, result);
        return result;
    }

    /**
     * 扫描公式中所有 {@code ${xxx}} 占位符，返回按出现顺序去重后的变量名集合.
     *
     * @param formula 公式文本
     * @return 变量名集合（保持首次出现顺序）
     */
    private Set<String> extractVariables(String formula) {
        Set<String> vars = new LinkedHashSet<>();
        Matcher m = VAR_PATTERN.matcher(formula);
        while (m.find()) {
            vars.add(m.group(1));
        }
        return vars;
    }
}
