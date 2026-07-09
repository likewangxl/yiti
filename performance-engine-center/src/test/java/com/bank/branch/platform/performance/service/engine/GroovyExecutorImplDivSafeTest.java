package com.bank.branch.platform.performance.service.engine;

import com.bank.branch.platform.performance.config.PerfEngineProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GroovyExecutorImpl 除法安全测试.
 *
 * <p>除数为 0 / 无限小数 → 不抛异常，结果取 0；`div(a,b)` 安全闭包 + 兜底 ArithmeticException。
 */
class GroovyExecutorImplDivSafeTest {

    private GroovyExecutorImpl executor;

    @BeforeEach
    void setup() {
        // groovyEnabled 默认 true、sqlTimeoutSeconds 默认 30
        executor = new GroovyExecutorImpl(new PerfEngineProperties());
    }

    @Test
    void div_byZero_returnsZero() {
        BigDecimal r = executor.execute("div(a, b)",
                Map.of("a", new BigDecimal("100"), "b", BigDecimal.ZERO), Duration.ofSeconds(5));
        assertThat(r).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void div_nonTerminating_noException() {
        BigDecimal r = executor.execute("div(a, b)",
                Map.of("a", new BigDecimal("1"), "b", new BigDecimal("3")), Duration.ofSeconds(5));
        assertThat(r).isEqualByComparingTo(new BigDecimal("0.3333333333"));
    }

    @Test
    void rawSlashByZero_caughtAsZero() {
        BigDecimal r = executor.execute("a / b",
                Map.of("a", new BigDecimal("100"), "b", BigDecimal.ZERO), Duration.ofSeconds(5));
        assertThat(r).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
