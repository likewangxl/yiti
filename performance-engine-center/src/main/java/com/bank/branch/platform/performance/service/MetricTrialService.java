package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.performance.config.PerfEngineProperties;
import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.service.dto.MetricTrialResult;
import com.bank.branch.platform.performance.service.engine.GroovyExecutor;
import com.bank.branch.platform.performance.service.engine.SqlExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 指标试运行服务（V1.1 Task P3.1）.
 *
 * <p>职责：在<strong>不写宽表、不创建 run_task</strong> 的前提下执行指标的 SQL / Groovy
 * 计算，返回样本结果。用于 {@code POST /api/perf/metrics/{metricCode}/trial-run} 端点。
 *
 * <p>与 {@link MetricCalcService} 的差异：
 * <table border="1">
 *   <tr><th>维度</th><th>MetricCalcService</th><th>MetricTrialService</th></tr>
 *   <tr><td>落库</td><td>写宽表 + 创建 run_task</td><td>全程无侧效</td></tr>
 *   <tr><td>返回</td><td>taskId</td><td>MetricTrialResult（样本）</td></tr>
 *   <tr><td>失败处理</td><td>FAILED 状态机</td><td>异常直接透传</td></tr>
 * </table>
 *
 * <p><strong>sampleSize 语义</strong>：
 * <ul>
 *   <li>null → 默认 20</li>
 *   <li>0 → 默认 20（保护）</li>
 *   <li>&gt;100 → 收敛到 100（与 03 §A.5 "最大 100" 对齐）</li>
 * </ul>
 */
@Slf4j
@Service
public class MetricTrialService {

    /** sampleSize 默认值（03 §A.5）. */
    private static final int DEFAULT_SAMPLE_SIZE = 20;

    /** sampleSize 上限（03 §A.5）. */
    private static final int MAX_SAMPLE_SIZE = 100;

    private final MetricDefService metricDefService;
    private final SqlExecutor sqlExecutor;
    private final GroovyExecutor groovyExecutor;
    private final PerfEngineProperties perfEngineProperties;

    @Autowired
    public MetricTrialService(MetricDefService metricDefService,
                              SqlExecutor sqlExecutor,
                              GroovyExecutor groovyExecutor,
                              PerfEngineProperties perfEngineProperties) {
        this.metricDefService = metricDefService;
        this.sqlExecutor = sqlExecutor;
        this.groovyExecutor = groovyExecutor;
        this.perfEngineProperties = perfEngineProperties;
    }

    /**
     * 试运行指标.
     *
     * @param metricCode 指标编码（必填）
     * @param dataDate   数据日期（参数可为 null，SQL 中按 :dataDate 参数透传）
     * @param sampleSize 样本条数（null 或 0 取默认 20，上限 100）
     * @param params     额外 SQL 参数（与 {@code dataDate} 合并后传入 SqlExecutor）
     * @return {@link MetricTrialResult}
     * @throws PerfException 指标不存在 / 软删除 / SQL 校验失败 / Groovy 沙盒拦截 / 超时
     */
    public MetricTrialResult trial(String metricCode, LocalDate dataDate,
                                   Integer sampleSize, Map<String, Object> params) {
        // 1. 加载指标定义，不存在或软删除均抛 METRIC_NOT_FOUND
        PerfMetricDef def = metricDefService.getByCodeOrNull(metricCode);
        if (def == null || (def.getDeleted() != null && def.getDeleted() == 1)) {
            throw new PerfException(PerfErrorCode.METRIC_NOT_FOUND, metricCode);
        }
        // V1.6 修复 Bug3：停用态禁止试运行（前端也已 disable 按钮，此处后端兜底）
        if ("DISABLED".equals(def.getStatus()) || "INACTIVE".equals(def.getStatus())) {
            throw new PerfException(PerfErrorCode.VALIDATION_FAILED,
                    "指标已停用，禁止试运行：" + metricCode + "（请先启用后再试运行）");
        }

        // 2. 解析 sampleSize（null/0 → 默认；> 100 → 收敛 100）
        int effectiveSample = resolveSampleSize(sampleSize);

        // 3. 解析超时（从 PerfEngineProperties）
        int timeoutSeconds = perfEngineProperties == null
                ? 30 : Math.max(1, perfEngineProperties.getSqlTimeoutSeconds());
        Duration timeout = Duration.ofSeconds(timeoutSeconds);

        // 4. 按 calcLogicType 分派
        String logicType = def.getCalcLogicType();
        long t0 = System.currentTimeMillis();
        MetricTrialResult result = new MetricTrialResult();

        if ("SQL".equalsIgnoreCase(logicType)) {
            runSql(def, dataDate, params, timeout, effectiveSample, result);
        } else if ("EXPR".equalsIgnoreCase(logicType) || "GROOVY".equalsIgnoreCase(logicType)) {
            runExpr(def, params, timeout, result);
        } else {
            throw new PerfException(PerfErrorCode.METRIC_CALC_LOGIC_INVALID,
                    "试运行不支持 calcLogicType=" + logicType);
        }

        result.setExecutionMillis(System.currentTimeMillis() - t0);
        return result;
    }

    /**
     * SQL 指标试运行：调 SqlExecutor（含 SqlValidator）→ 截取样本.
     */
    private void runSql(PerfMetricDef def, LocalDate dataDate,
                        Map<String, Object> params, Duration timeout,
                        int effectiveSample, MetricTrialResult result) {
        if (def.getSqlText() == null || def.getSqlText().isBlank()) {
            throw new PerfException(PerfErrorCode.METRIC_CALC_LOGIC_INVALID,
                    "SQL 类型指标 sqlText 为空: " + def.getMetricCode());
        }
        Map<String, Object> mergedParams = new HashMap<>();
        if (params != null) {
            mergedParams.putAll(params);
        }
        mergedParams.putIfAbsent("dataDate", dataDate);

        Map<String, BigDecimal> all = sqlExecutor.execute(def.getSqlText(), mergedParams, timeout);
        int total = all == null ? 0 : all.size();
        List<Map<String, Object>> samples = new ArrayList<>();
        if (all != null) {
            int count = 0;
            for (Map.Entry<String, BigDecimal> entry : all.entrySet()) {
                if (count++ >= effectiveSample) {
                    break;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("baseKey", entry.getKey());
                row.put("metricValue", entry.getValue());
                samples.add(row);
            }
        }
        result.setTotalRows(total);
        result.setSamples(samples);
        result.setSampleSize(samples.size());
        result.setExprResult(null);
    }

    /**
     * EXPR 指标试运行：调 GroovyExecutor → 返回单值.
     */
    private void runExpr(PerfMetricDef def, Map<String, Object> params,
                         Duration timeout, MetricTrialResult result) {
        if (def.getExprText() == null || def.getExprText().isBlank()) {
            throw new PerfException(PerfErrorCode.METRIC_CALC_LOGIC_INVALID,
                    "EXPR 类型指标 exprText 为空: " + def.getMetricCode());
        }
        Map<String, Object> vars = params == null ? Map.of() : params;
        BigDecimal value = groovyExecutor.execute(def.getExprText(), vars, timeout);
        result.setExprResult(value);
        result.setTotalRows(1);
        result.setSamples(List.of());
        result.setSampleSize(0);
    }

    /** sampleSize 解析：null/0 回默认 20，>100 收敛 100. */
    private int resolveSampleSize(Integer sampleSize) {
        if (sampleSize == null || sampleSize <= 0) {
            return DEFAULT_SAMPLE_SIZE;
        }
        return Math.min(sampleSize, MAX_SAMPLE_SIZE);
    }
}
