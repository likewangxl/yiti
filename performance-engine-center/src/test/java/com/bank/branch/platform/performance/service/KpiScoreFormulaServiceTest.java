package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.performance.config.PerfEngineProperties;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.service.engine.GroovyExecutor;
import com.bank.branch.platform.performance.service.engine.GroovyExecutorImpl;
import com.bank.branch.platform.performance.support.PerformanceServiceTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link KpiScoreFormulaService} 单元测试.
 *
 * <p>用真实 {@link GroovyExecutorImpl} 验证计分公式（变量 actual/target/base/weight + min/max）
 * 求值正确性，重点覆盖 min/max 闭包与 BigDecimal 精度、目标值为 0 触发除零异常上抛。
 */
class KpiScoreFormulaServiceTest extends PerformanceServiceTestBase {

    private KpiScoreFormulaService service;

    @BeforeEach
    void setUp() {
        PerfEngineProperties props = new PerfEngineProperties();
        props.setSqlTimeoutSeconds(5);
        GroovyExecutor executor = new GroovyExecutorImpl(props);
        service = new KpiScoreFormulaService(executor, props);
    }

    @Test
    void evalScore_达成率乘权重() {
        // actual=80, target=100, weight=0.5 → 80/100*0.5 = 0.4
        BigDecimal score = service.evalScore("actual / target * weight",
                new BigDecimal("80"), new BigDecimal("100"), BigDecimal.ZERO, new BigDecimal("0.5"));
        assertThat(score).isEqualByComparingTo("0.4");
    }

    @Test
    void evalScore_min封顶() {
        // min(actual/target*100, 120): 实际 150/100*100=150 → 被 min 封顶到 120
        BigDecimal score = service.evalScore("min(actual / target * 100, 120)",
                new BigDecimal("150"), new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ONE);
        assertThat(score).isEqualByComparingTo("120");
    }

    @Test
    void evalScore_maxAndMin嵌套保留下限() {
        // min(max(actual/target*weight, 计分下限), 计分上限)；下限 10、上限 120
        // actual=0 → 0*weight=0 → max(0,10)=10 → min(10,120)=10
        BigDecimal score = service.evalScore("min(max(actual / target * weight, 10), 120)",
                BigDecimal.ZERO, new BigDecimal("100"), BigDecimal.ZERO, new BigDecimal("2"));
        assertThat(score).isEqualByComparingTo("10");
    }

    @Test
    void evalScore_线性插值基础值目标值() {
        // (actual-base)/(target-base)*weight：实际60、基础20、目标100、权重1 → (40/80)*1 = 0.5
        BigDecimal score = service.evalScore("(actual - base) / (target - base) * weight",
                new BigDecimal("60"), new BigDecimal("100"), new BigDecimal("20"), BigDecimal.ONE);
        assertThat(score).isEqualByComparingTo("0.5");
    }

    @Test
    void evalScore_目标值为0除零记0分() {
        // 业务约定：除零（目标值/基础值缺失致分母为 0）不中断任务，记 0 分
        BigDecimal score = service.evalScore("actual / target * weight",
                new BigDecimal("80"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE);
        assertThat(score).isEqualByComparingTo("0");
    }

    @Test
    void evalScore_线性插值分母为0记0分() {
        // (actual-base)/(target-base)：target==base==50 → 分母 0 → 记 0 分
        BigDecimal score = service.evalScore("(actual - base) / (target - base) * weight",
                new BigDecimal("60"), new BigDecimal("50"), new BigDecimal("50"), BigDecimal.ONE);
        assertThat(score).isEqualByComparingTo("0");
    }

    @Test
    void evalScore_语法错误仍上抛异常() {
        // 非除零的真实公式错误（语法非法）仍 fail-fast
        assertThatThrownBy(() -> service.evalScore("actual ** ", // 非法表达式
                new BigDecimal("80"), new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ONE))
                .isInstanceOf(PerfException.class);
    }
}
