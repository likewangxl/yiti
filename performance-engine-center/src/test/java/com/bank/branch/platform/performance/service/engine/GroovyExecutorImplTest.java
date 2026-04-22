package com.bank.branch.platform.performance.service.engine;

import com.bank.branch.platform.performance.config.PerfEngineProperties;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * GroovyExecutorImpl 单元测试（纯 Java/Groovy，无需 Spring 容器）.
 *
 * <p>覆盖：
 * <ul>
 *   <li>基本算术表达式（+ - * /）</li>
 *   <li>变量绑定</li>
 *   <li>沙盒拦截：System.exit / Runtime / Thread / java.io / java.net</li>
 *   <li>超时拦截：死循环表达式触发 TRIAL_RUN_TIMEOUT</li>
 * </ul>
 */
class GroovyExecutorImplTest {

    private final PerfEngineProperties props = buildProps();

    private final GroovyExecutorImpl executor = new GroovyExecutorImpl(props);

    private static PerfEngineProperties buildProps() {
        PerfEngineProperties p = new PerfEngineProperties();
        p.setGroovyEnabled(true);
        p.setSqlTimeoutSeconds(30);
        p.setCascadeMaxDepth(5);
        return p;
    }

    @Test
    @DisplayName("execute 支持基本算术 a + b * 2")
    void execute_simpleArithmetic() {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("a", new BigDecimal("1"));
        vars.put("b", new BigDecimal("3"));
        BigDecimal r = executor.execute("a + b * 2", vars, Duration.ofSeconds(5));
        assertThat(r).isEqualByComparingTo("7");
    }

    @Test
    @DisplayName("execute 支持 BigDecimal 精度算术")
    void execute_bigDecimalPrecision() {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("a", new BigDecimal("0.1"));
        vars.put("b", new BigDecimal("0.2"));
        BigDecimal r = executor.execute("a + b", vars, Duration.ofSeconds(5));
        assertThat(r).isEqualByComparingTo("0.3");
    }

    @Test
    @DisplayName("execute 拒绝 System.exit 等访问")
    void execute_rejectsSystemExit() {
        assertThatThrownBy(() -> executor.execute("System.exit(0)", Map.of(), Duration.ofSeconds(5)))
                .isInstanceOf(PerfException.class)
                .extracting("errorCode")
                .isEqualTo(PerfErrorCode.METRIC_CALC_LOGIC_INVALID);
    }

    @Test
    @DisplayName("execute 拒绝 Runtime.getRuntime()")
    void execute_rejectsRuntime() {
        assertThatThrownBy(() -> executor.execute("Runtime.getRuntime().exec('notepad')", Map.of(), Duration.ofSeconds(5)))
                .isInstanceOf(PerfException.class)
                .extracting("errorCode")
                .isEqualTo(PerfErrorCode.METRIC_CALC_LOGIC_INVALID);
    }

    @Test
    @DisplayName("execute 拒绝 Thread.start")
    void execute_rejectsThread() {
        assertThatThrownBy(() -> executor.execute("new Thread({}).start()", Map.of(), Duration.ofSeconds(5)))
                .isInstanceOf(PerfException.class)
                .extracting("errorCode")
                .isEqualTo(PerfErrorCode.METRIC_CALC_LOGIC_INVALID);
    }

    @Test
    @DisplayName("execute 拒绝 java.io.File 访问")
    void execute_rejectsFileAccess() {
        assertThatThrownBy(() -> executor.execute("new File('/etc/passwd').text", Map.of(), Duration.ofSeconds(5)))
                .isInstanceOf(PerfException.class)
                .extracting("errorCode")
                .isEqualTo(PerfErrorCode.METRIC_CALC_LOGIC_INVALID);
    }

    @Test
    @DisplayName("execute 拒绝 java.net.URL 访问")
    void execute_rejectsUrlAccess() {
        assertThatThrownBy(() -> executor.execute("new URL('http://evil').text", Map.of(), Duration.ofSeconds(5)))
                .isInstanceOf(PerfException.class)
                .extracting("errorCode")
                .isEqualTo(PerfErrorCode.METRIC_CALC_LOGIC_INVALID);
    }

    @Test
    @DisplayName("execute 死循环在 timeout 之后抛 TRIAL_RUN_TIMEOUT")
    void execute_deadLoop_throwsTimeout() {
        assertThatThrownBy(() -> executor.execute(
                "def s = 0L; while (true) { s = s + 1L }; s",
                Map.of(),
                Duration.ofSeconds(1)))
                .isInstanceOf(PerfException.class)
                .extracting("errorCode")
                .isEqualTo(PerfErrorCode.TRIAL_RUN_TIMEOUT);
    }

    @Test
    @DisplayName("execute 运行后能被多次调用（线程池复用 / 不污染状态）")
    void execute_canBeCalledRepeatedly() {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("a", new BigDecimal("10"));
        assertThatCode(() -> {
            for (int i = 0; i < 5; i++) {
                BigDecimal r = executor.execute("a * 2", vars, Duration.ofSeconds(5));
                assertThat(r).isEqualByComparingTo("20");
            }
        }).doesNotThrowAnyException();
    }
}
