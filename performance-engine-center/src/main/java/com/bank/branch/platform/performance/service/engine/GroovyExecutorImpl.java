package com.bank.branch.platform.performance.service.engine;

import com.bank.branch.platform.performance.config.PerfEngineProperties;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.SecureASTCustomizer;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * Groovy 执行器默认实现：SecureASTCustomizer 沙盒 + ExecutorService 超时 + 黑名单兜底.
 *
 * <p>双层防护：
 * <ol>
 *   <li><strong>SecureASTCustomizer</strong>：AST 级拦截 receiver / import / package 白名单之外的访问。</li>
 *   <li><strong>兜底正则黑名单</strong>：在编译前先做文本扫描，对 {@code System./Runtime./Thread/new File/new URL/new Socket} 等
 *       高危引用即刻拒绝，双保险，避免 SecureAST 某些边界场景漏放（例如动态反射）。</li>
 * </ol>
 *
 * <p><strong>为何不禁用 package allowed = false：</strong>SecureAST 的 package 判定会误杀
 * 简单赋值脚本（因为 Groovy 脚本默认挂到 {@code script} package），设为 true。限制改用
 * import / receiver / indirect receiver 黑名单精确打击。
 *
 * <p>线程模型：执行器内部持有单个 cached 线程池，每次 execute 新建 Future，超时
 * 后 {@code cancel(true)} 发出中断；若表达式内的代码不响应中断（如 while(true)
 * 无 IO 调用），JVM 无法强制停止——这是 Groovy/JVM 限制，实践中影响不大，
 * 因为中断请求会触发绝大多数阻塞 API 返回。
 */
@Slf4j
@Service
public class GroovyExecutorImpl implements GroovyExecutor {

    /** 高危关键字黑名单（兜底）：若表达式文本出现则立即拒绝. */
    private static final Pattern DANGEROUS_PATTERN = Pattern.compile(
            "(?s).*\\b("
                    + "System\\s*\\."
                    + "|Runtime\\s*\\."
                    + "|Thread\\s*\\."
                    + "|new\\s+Thread"
                    + "|new\\s+File"
                    + "|new\\s+FileInputStream"
                    + "|new\\s+FileOutputStream"
                    + "|new\\s+URL"
                    + "|new\\s+URI"
                    + "|new\\s+Socket"
                    + "|new\\s+ProcessBuilder"
                    + "|\\.exec\\s*\\("
                    + "|\\.forName\\s*\\("
                    + ")\\b.*");

    /** 沙盒禁用的 receiver 类型（AST 层拦截）. */
    private static final List<String> BLACKLISTED_RECEIVERS = Arrays.asList(
            "java.lang.System",
            "java.lang.Thread",
            "java.lang.Runtime",
            "java.lang.Class",
            "java.lang.ClassLoader",
            "java.lang.ProcessBuilder",
            "java.io.File",
            "java.io.FileInputStream",
            "java.io.FileOutputStream",
            "java.net.URL",
            "java.net.URI",
            "java.net.Socket",
            "groovy.lang.GroovyShell",
            "groovy.lang.GroovyClassLoader"
    );

    /** 沙盒禁用的 import 包/类（AST 层拦截）. */
    private static final List<String> BLACKLISTED_IMPORTS = Arrays.asList(
            "java.io", "java.net", "java.nio",
            "java.lang.reflect", "java.lang.ProcessBuilder",
            "groovy.lang.GroovyShell", "groovy.lang.GroovyClassLoader"
    );

    private final PerfEngineProperties props;
    private final CompilerConfiguration compilerConfig;
    private ExecutorService executorService;

    public GroovyExecutorImpl(PerfEngineProperties props) {
        this.props = props;
        this.compilerConfig = buildSandboxConfig();
    }

    @PostConstruct
    void initExecutor() {
        // 专用线程池：单独命名 / 守护线程，避免阻塞 JVM 退出
        final AtomicLong counter = new AtomicLong();
        ThreadFactory tf = r -> {
            Thread t = new Thread(r, "perf-groovy-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
        this.executorService = Executors.newCachedThreadPool(tf);
    }

    @PreDestroy
    void shutdownExecutor() {
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }

    @Override
    public BigDecimal execute(String expr, Map<String, Object> vars, Duration timeout) {
        if (!props.isGroovyEnabled()) {
            throw new PerfException(PerfErrorCode.METRIC_CALC_LOGIC_INVALID, "Groovy 执行已禁用");
        }
        if (expr == null || expr.isBlank()) {
            throw new PerfException(PerfErrorCode.METRIC_CALC_LOGIC_INVALID, "Groovy 表达式不能为空");
        }

        // 兜底黑名单：在编译前就拦截高危文本
        if (DANGEROUS_PATTERN.matcher(expr).matches()) {
            throw new PerfException(PerfErrorCode.METRIC_CALC_LOGIC_INVALID,
                    "表达式包含禁用关键字（System/Runtime/Thread/File/URL 等）");
        }

        // 兜底 ExecutorService 若 Spring 容器未经 PostConstruct（如单元测试直接 new），在此懒初始化
        ensureExecutor();

        Callable<Object> task = () -> {
            Binding binding = new Binding(vars == null ? new java.util.HashMap<>() : new java.util.HashMap<>(vars));
            // 注入安全除法闭包 div(a,b)：除数为 0 → 0，固定 scale 规避无限小数异常
            binding.setVariable("div", new SafeDivClosure(this));
            GroovyShell shell = new GroovyShell(binding, compilerConfig);
            return shell.evaluate(expr);
        };

        long timeoutSeconds = resolveTimeoutSeconds(timeout);
        Future<Object> future = executorService.submit(task);
        try {
            Object result = future.get(timeoutSeconds, TimeUnit.SECONDS);
            return toBigDecimal(result);
        } catch (TimeoutException te) {
            future.cancel(true);
            throw new PerfException(PerfErrorCode.TRIAL_RUN_TIMEOUT,
                    "Groovy 表达式执行超过 " + timeoutSeconds + " 秒");
        } catch (PerfException pe) {
            throw pe;
        } catch (Exception ex) {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            if (cause instanceof PerfException pe) {
                throw pe;
            }
            // 除数为 0 / 无限小数（手写裸 / 触发）：兜底返回 0，不使该主体失败（spec §9）
            if (cause instanceof ArithmeticException) {
                log.warn("[GroovyExecutor] 算术异常兜底返回 0: {}", cause.getMessage());
                return BigDecimal.ZERO;
            }
            log.warn("[GroovyExecutor] 表达式执行失败: {}", cause.getMessage());
            throw new PerfException(PerfErrorCode.METRIC_CALC_LOGIC_INVALID, cause,
                    "Groovy 执行失败: " + cause.getMessage());
        }
    }

    /**
     * 构造 SecureAST 沙盒配置.
     *
     * @return 受限制的 CompilerConfiguration
     */
    private CompilerConfiguration buildSandboxConfig() {
        SecureASTCustomizer sec = new SecureASTCustomizer();
        // indirectImportCheckEnabled = true 阻止通过 new java.io.File(...) 形式绕过 import 白名单
        sec.setIndirectImportCheckEnabled(true);
        sec.setReceiversBlackList(BLACKLISTED_RECEIVERS);
        sec.setImportsBlacklist(BLACKLISTED_IMPORTS);
        sec.setStarImportsBlacklist(BLACKLISTED_IMPORTS);
        sec.setStaticImportsBlacklist(BLACKLISTED_IMPORTS);
        sec.setStaticStarImportsBlacklist(BLACKLISTED_IMPORTS);
        // package 允许 true（不阻断脚本默认 package），用 receiver 黑名单精确打击
        sec.setPackageAllowed(true);

        CompilerConfiguration cfg = new CompilerConfiguration();
        cfg.addCompilationCustomizers(sec);
        return cfg;
    }

    /** 兜底懒初始化线程池（单元测试 new 对象时用）. */
    private synchronized void ensureExecutor() {
        if (executorService == null || executorService.isShutdown()) {
            initExecutor();
        }
    }

    /** 将任意结果转 BigDecimal；null 视为 BigDecimal.ZERO 避免 NPE. */
    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        if (value instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException nfe) {
            throw new PerfException(PerfErrorCode.METRIC_CALC_LOGIC_INVALID, nfe,
                    "Groovy 结果无法转为数值: " + value);
        }
    }

    /** 安全解析 timeout（null/<=0 回落 PerfEngineProperties.sqlTimeoutSeconds）. */
    private long resolveTimeoutSeconds(Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            return Math.max(1, props.getSqlTimeoutSeconds());
        }
        long s = timeout.getSeconds();
        return s <= 0 ? 1 : s;
    }
}
