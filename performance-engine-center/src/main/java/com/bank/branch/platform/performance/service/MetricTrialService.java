package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.performance.config.PerfEngineProperties;
import com.bank.branch.platform.performance.service.engine.DateMacroResolver;
import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.entity.SysControl;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.service.dto.MetricTrialResult;
import com.bank.branch.platform.performance.service.engine.GroovyExecutor;
import com.bank.branch.platform.performance.service.engine.SqlExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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
    /** EXPR 试运行时按 baseDim 选宽表加载 M_xxx 引用指标值，复用调度态绑定逻辑. */
    private final MetricCalcService metricCalcService;
    /** EXPR 试运行解析当前生效数据版本（宽表行按 version 隔离）. */
    private final SysControlService sysControlService;

    @Autowired
    public MetricTrialService(MetricDefService metricDefService,
                              SqlExecutor sqlExecutor,
                              GroovyExecutor groovyExecutor,
                              PerfEngineProperties perfEngineProperties,
                              MetricCalcService metricCalcService,
                              SysControlService sysControlService) {
        this.metricDefService = metricDefService;
        this.sqlExecutor = sqlExecutor;
        this.groovyExecutor = groovyExecutor;
        this.perfEngineProperties = perfEngineProperties;
        this.metricCalcService = metricCalcService;
        this.sysControlService = sysControlService;
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

        // 2-4. 共用分派逻辑
        return runByDef(def, dataDate, sampleSize, params);
    }

    /**
     * 直接试运行 SQL / Groovy 文本（无需先保存指标）—— 新增指标页面"试运行"按钮直接取表达式执行.
     *
     * @param calcLogicType SQL / EXPR(GROOVY)
     * @param sqlText       SQL 文本（SQL 场景）
     * @param exprText      Groovy 表达式文本（EXPR 场景）
     * @param dataDate      数据日期（:dataDate 等占位符值）
     * @param sampleSize    样本条数
     * @param params        附加参数（含对象值 :objectId）
     * @return {@link MetricTrialResult}
     */
    public MetricTrialResult trialAdhoc(String calcLogicType, String baseDim, String sqlText, String exprText,
                                        LocalDate dataDate, Integer sampleSize, Map<String, Object> params) {
        PerfMetricDef def = new PerfMetricDef();
        def.setMetricCode("(未保存)");
        def.setCalcLogicType(calcLogicType);
        // EXPR 试运行需 baseDim 选宽表加载引用指标值；SQL 场景该字段不参与
        def.setBaseDim(baseDim);
        def.setSqlText(sqlText);
        def.setExprText(exprText);
        return runByDef(def, dataDate, sampleSize, params);
    }

    /** 共用：按 def（已保存或临时构造）按 calcLogicType 分派执行试运行. */
    private MetricTrialResult runByDef(PerfMetricDef def, LocalDate dataDate,
                                       Integer sampleSize, Map<String, Object> params) {
        int effectiveSample = resolveSampleSize(sampleSize);
        int timeoutSeconds = perfEngineProperties == null
                ? 30 : Math.max(1, perfEngineProperties.getSqlTimeoutSeconds());
        Duration timeout = Duration.ofSeconds(timeoutSeconds);
        String logicType = def.getCalcLogicType();
        long t0 = System.currentTimeMillis();
        MetricTrialResult result = new MetricTrialResult();

        if ("SQL".equalsIgnoreCase(logicType)) {
            runSql(def, dataDate, params, timeout, effectiveSample, result);
        } else if ("EXPR".equalsIgnoreCase(logicType) || "GROOVY".equalsIgnoreCase(logicType)) {
            runExpr(def, dataDate, params, timeout, result);
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
        if (dataDate != null) {
            mergedParams.putAll(DateMacroResolver.resolve(dataDate));
        }
        // 对象id占位符 :objectId —— 由试运行的"对象值"输入框经 params 传入；未输入时绑 null，避免 SQL 含 :objectId 时绑定缺失报错
        mergedParams.putIfAbsent("objectId", null);

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
     * EXPR 指标试运行：按 (baseDim, dataDate, objectId, version) 从对应维度宽表加载表达式引用的
     * 所有 {@code M_xxx} 指标值作为 Groovy 变量，再调 GroovyExecutor → 返回单值.
     *
     * <p>与调度态 {@link MetricCalcService#executeGroovyAndPersist} 的绑定口径一致，
     * 解决"试运行 Groovy 报 No such property: M_xxxx"——引用指标未绑定的问题。
     * objectId（对象值）由前端"对象值"输入框经 {@code params} 传入，缺失时引用指标兜底 ZERO。
     */
    private void runExpr(PerfMetricDef def, LocalDate dataDate, Map<String, Object> params,
                         Duration timeout, MetricTrialResult result) {
        if (def.getExprText() == null || def.getExprText().isBlank()) {
            throw new PerfException(PerfErrorCode.METRIC_CALC_LOGIC_INVALID,
                    "EXPR 类型指标 exprText 为空: " + def.getMetricCode());
        }
        // 对象值（员工工号 / 机构编码 / 客户ID）——定位宽表中该主体那一行
        String objectId = params == null || params.get("objectId") == null
                ? null : String.valueOf(params.get("objectId"));
        // 数据版本：优先按 (对象, 日期) 反查"最近导入"的版本（宽表数据散落多版本，SYS_CONTROL 当前版本未必有数据），
        // 查不到再降级到 SYS_CONTROL 当前版本 / V1，避免取不到数据恒为 0。
        String version = metricCalcService.resolveDataVersionForSubject(def.getBaseDim(), objectId, dataDate);
        if (version == null) {
            version = resolveCurrentVersion(def.getBaseDim());
        }
        // 加载宽表引用指标值（所有引用 code 必绑定，缺失值 ZERO，杜绝 No such property）
        Map<String, Object> refValues = metricCalcService.loadGroovyVarsForSubject(
                def.getBaseDim(), def.getExprText(), dataDate, objectId, version);
        Map<String, Object> vars = new HashMap<>(refValues);
        // params 中的其余自定义变量（如 objectId 本身）补充进去，不覆盖宽表指标值
        if (params != null) {
            for (Map.Entry<String, Object> e : params.entrySet()) {
                vars.putIfAbsent(e.getKey(), e.getValue());
            }
        }
        BigDecimal value = groovyExecutor.execute(def.getExprText(), vars, timeout);
        result.setExprResult(value);
        // 回传引用指标取值 + 命中版本，供前端"列出 Groovy 计算用到的用户指标数据"
        result.setExprVars(refValues);
        result.setDataVersion(version);
        result.setTotalRows(1);
        result.setSamples(List.of());
        result.setSampleSize(0);
    }

    /** 解析维度当前生效数据版本；无 SYS_CONTROL 记录（如试运行未初始化维度）降级 V1. */
    private String resolveCurrentVersion(String baseDim) {
        if (!StringUtils.hasText(baseDim)) {
            return "V1";
        }
        try {
            SysControl sc = sysControlService.getCurrentVersion(baseDim);
            return sc != null && StringUtils.hasText(sc.getCurrentVersion()) ? sc.getCurrentVersion() : "V1";
        } catch (Exception e) {
            return "V1";
        }
    }

    /** sampleSize 解析：null/0 回默认 20，>100 收敛 100. */
    private int resolveSampleSize(Integer sampleSize) {
        if (sampleSize == null || sampleSize <= 0) {
            return DEFAULT_SAMPLE_SIZE;
        }
        return Math.min(sampleSize, MAX_SAMPLE_SIZE);
    }
}
