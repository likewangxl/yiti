package com.bank.branch.platform.performance.service.engine;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;

/**
 * Groovy 表达式执行器（V1.1 Task P2.3）.
 *
 * <p>用于支持二/三级指标的 Groovy 表达式计算（{@code calc_logic_type = EXPR / GROOVY}）.
 *
 * <p><strong>沙盒策略</strong>（通过 {@link org.codehaus.groovy.control.customizers.SecureASTCustomizer}
 * 实现，兜底代码黑名单）：
 * <ul>
 *   <li>禁止 receiver：{@code java.lang.System / Thread / Runtime / Class}</li>
 *   <li>禁止 import：{@code java.io.* / java.net.* / java.nio.*}</li>
 *   <li>禁止 package 访问</li>
 * </ul>
 *
 * <p><strong>超时</strong>：使用 {@code ExecutorService.submit + Future.get(timeout)}，
 * 超时后调用 {@code future.cancel(true)}，抛 {@code TRIAL_RUN_TIMEOUT(PERF-42205)}.
 *
 * <p><strong>错误码</strong>：
 * <ul>
 *   <li>语法错误 / 沙盒拦截 → {@code METRIC_CALC_LOGIC_INVALID(PERF-42201)}</li>
 *   <li>超时 → {@code TRIAL_RUN_TIMEOUT(PERF-42205)}</li>
 * </ul>
 */
public interface GroovyExecutor {

    /**
     * 执行 Groovy 表达式.
     *
     * @param expr    表达式文本
     * @param vars    变量绑定（key → value），支持 {@link BigDecimal} / {@link Number} / {@link String} 等
     * @param timeout 执行超时
     * @return 表达式求值结果（自动转 {@link BigDecimal}）
     * @throws com.bank.branch.platform.performance.exception.PerfException 语法错误 / 沙盒违规 / 超时
     */
    BigDecimal execute(String expr, Map<String, Object> vars, Duration timeout);
}
