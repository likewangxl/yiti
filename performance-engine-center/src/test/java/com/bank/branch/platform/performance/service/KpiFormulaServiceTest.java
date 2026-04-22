package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.performance.config.PerfEngineProperties;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.service.engine.GroovyExecutor;
import com.bank.branch.platform.performance.service.engine.GroovyExecutorImpl;
import com.bank.branch.platform.performance.support.PerformanceServiceTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * KpiFormulaService 单元测试（Task P4.1 Red）.
 *
 * <p>职责：把 KPI item 的公式文本（含 {@code ${metric_code}} 占位符）替换为纯 Groovy
 * 表达式后，委托 {@link GroovyExecutor} 计算得到最终数值。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>正常算术：{@code ${M_EMP_A} * 0.3 + ${M_EMP_B} * 0.7}</li>
 *   <li>缺失变量：抛 {@link PerfErrorCode#VALIDATION_FAILED}</li>
 *   <li>空公式：抛 {@link PerfErrorCode#METRIC_CALC_LOGIC_INVALID}</li>
 *   <li>嵌套算术（含括号）</li>
 *   <li>Groovy 沙盒保护：公式中嵌入 {@code System.exit} 被拒绝</li>
 * </ul>
 *
 * <p>为了最接近真实 Groovy 行为，直接组装真实的 {@link GroovyExecutorImpl}（无需 Mock）.
 */
class KpiFormulaServiceTest extends PerformanceServiceTestBase {

    private KpiFormulaService service;

    @BeforeEach
    void setUp() {
        PerfEngineProperties props = new PerfEngineProperties();
        // 测试用小超时，避免单测长时间挂起
        props.setSqlTimeoutSeconds(5);
        GroovyExecutor executor = new GroovyExecutorImpl(props);
        service = new KpiFormulaService(executor, props);
    }

    @Test
    @DisplayName("eval：基础算术 ${M_EMP_A}*0.3 + ${M_EMP_B}*0.7 计算正确")
    void eval_basicArithmetic() {
        String formula = "${M_EMP_A} * 0.3 + ${M_EMP_B} * 0.7";
        Map<String, BigDecimal> values = new HashMap<>();
        values.put("M_EMP_A", new BigDecimal("100"));
        values.put("M_EMP_B", new BigDecimal("200"));

        BigDecimal result = service.eval(formula, values);

        assertThat(result).isEqualByComparingTo("170");
    }

    @Test
    @DisplayName("eval：缺失变量抛 VALIDATION_FAILED 并提示缺失的指标编码")
    void eval_missingVariable_throwsValidationFailed() {
        String formula = "${M_A} + ${M_B}";
        Map<String, BigDecimal> values = Map.of("M_A", new BigDecimal("1"));

        assertThatThrownBy(() -> service.eval(formula, values))
                .isInstanceOf(PerfException.class)
                .extracting("errorCode")
                .isEqualTo(PerfErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("eval：空公式抛 METRIC_CALC_LOGIC_INVALID")
    void eval_emptyFormula_throws() {
        assertThatThrownBy(() -> service.eval("   ", Map.of()))
                .isInstanceOf(PerfException.class)
                .extracting("errorCode")
                .isEqualTo(PerfErrorCode.METRIC_CALC_LOGIC_INVALID);
        assertThatThrownBy(() -> service.eval(null, Map.of()))
                .isInstanceOf(PerfException.class)
                .extracting("errorCode")
                .isEqualTo(PerfErrorCode.METRIC_CALC_LOGIC_INVALID);
    }

    @Test
    @DisplayName("eval：嵌套算术（括号 + 四则）计算正确")
    void eval_nestedArithmetic() {
        String formula = "(${A} + ${B}) * ${C} - 10";
        Map<String, BigDecimal> values = Map.of(
                "A", new BigDecimal("2"),
                "B", new BigDecimal("3"),
                "C", new BigDecimal("4"));

        BigDecimal result = service.eval(formula, values);
        // (2 + 3) * 4 - 10 = 10
        assertThat(result).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("eval：Groovy 沙盒拦截公式中嵌入的 System.exit(0)")
    void eval_rejectsSystemCall() {
        // 攻击者构造：绕过变量替换后直接在表达式中使用 System.exit
        String formula = "System.exit(0) ?: ${A}";
        Map<String, BigDecimal> values = Map.of("A", new BigDecimal("1"));

        assertThatThrownBy(() -> service.eval(formula, values))
                .isInstanceOf(PerfException.class);
    }

    @Test
    @DisplayName("eval：单变量（退化为 ${M} 直接求值）")
    void eval_singleVariable() {
        BigDecimal result = service.eval("${ONE}", Map.of("ONE", new BigDecimal("7.5")));
        assertThat(result).isEqualByComparingTo("7.5");
    }

    @Test
    @DisplayName("eval：无变量公式（纯常量）也能计算")
    void eval_constantOnly() {
        BigDecimal result = service.eval("1 + 2 * 3", Map.of());
        assertThat(result).isEqualByComparingTo("7");
    }

    @Test
    @DisplayName("eval：公式变量值为 null 抛 VALIDATION_FAILED（不能以 null 参与计算）")
    void eval_nullValue_throwsValidation() {
        Map<String, BigDecimal> values = new HashMap<>();
        values.put("M_A", null);

        assertThatThrownBy(() -> service.eval("${M_A} + 1", values))
                .isInstanceOf(PerfException.class)
                .extracting("errorCode")
                .isEqualTo(PerfErrorCode.VALIDATION_FAILED);
    }
}
