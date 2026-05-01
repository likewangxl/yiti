package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.performance.config.PerfEngineProperties;
import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.entity.PerfRunTask;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.event.MetricCalcCompletedEvent;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.mapper.CustIndexResultMapper;
import com.bank.branch.platform.performance.mapper.EmpIndexResultMapper;
import com.bank.branch.platform.performance.mapper.OrgIndexResultMapper;
import com.bank.branch.platform.performance.mapper.PerfRunTaskMapper;
import com.bank.branch.platform.performance.service.dto.SubjectStats;
import com.bank.branch.platform.performance.service.engine.GroovyExecutor;
import com.bank.branch.platform.performance.service.engine.SqlExecutor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 单指标计算服务（V1.7 多主体改造）.
 *
 * <p>职责：
 * <ul>
 *   <li>读取指标定义（{@link MetricDefService#getByCodeOrNull}），不存在或已软删除抛
 *       {@link PerfErrorCode#METRIC_NOT_FOUND}。</li>
 *   <li>按 {@code calc_logic_type} 路由：
 *       <ul>
 *         <li>SQL → {@link SqlExecutor}（含 {@link com.bank.branch.platform.performance.service.engine.SqlValidator} 黑名单）</li>
 *         <li>EXPR / GROOVY → {@link SubjectFetcher} 取主体集合，逐主体调 {@link GroovyExecutor}（沙盒 + 超时）</li>
 *         <li>PROC / SUMMARY → 暂不支持，抛 {@link PerfErrorCode#CALC_JOB_FAILED}</li>
 *       </ul>
 *   </li>
 *   <li>按 {@code base_dim} 将 {@code (baseKey -> value)} 写入三张宽表之一
 *       （EmpIndexResultMapper / OrgIndexResultMapper / CustIndexResultMapper）。</li>
 *   <li>全程通过 {@link PerfRunTaskMapper} 维护任务状态机：
 *       <pre>
 *       PENDING --(insert)-&gt; RUNNING --(updateStatusWithParams)-&gt; SUCCESS / PARTIAL_FAILED / FAILED
 *       </pre>
 *   </li>
 *   <li>终态写入后通过 {@link ApplicationEventPublisher} 发布 {@link MetricCalcCompletedEvent}。</li>
 *   <li><strong>Service 层 slot 校验</strong>：调用宽表 UPSERT 前强制校验
 *       {@code valSlot ∈ [1, 200]}，防止 {@code val_${slot}} 拼接列名漏放。</li>
 * </ul>
 *
 * <p><strong>V1.7 多主体改造</strong>：EXPR/GROOVY 类型改为逐主体执行，
 * 引入 {@link SubjectStats} 追踪每主体成功/失败，按结果写 SUCCESS / PARTIAL_FAILED / FAILED。
 *
 * <p><strong>设计决定 — 事务边界</strong>：本 Service <em>不开 {@link org.springframework.transaction.annotation.Transactional}</em>
 * 最外层事务。原因：若整个方法包一个事务，异常抛出时 FAILED 状态会被回滚，造成
 * "任务跑失败却看不到失败记录"的诊断黑洞。采用"异常时手工写 FAILED 状态 +
 * 调用方单独请求感知"的方式——由于 {@code perfRunTaskMapper.updateStatus} 使用独立
 * 的 JDBC 连接（事务未开启时），状态写入能落库。
 */
@Slf4j
@Service
public class MetricCalcService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final MetricDefService metricDefService;
    private final SqlExecutor sqlExecutor;
    private final GroovyExecutor groovyExecutor;
    private final EmpIndexResultMapper empIndexResultMapper;
    private final OrgIndexResultMapper orgIndexResultMapper;
    private final CustIndexResultMapper custIndexResultMapper;
    private final PerfRunTaskMapper perfRunTaskMapper;
    private final PerfEngineProperties perfEngineProperties;
    private final SubjectFetcher subjectFetcher;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 构造器注入（V1.7 新增 SubjectFetcher + ApplicationEventPublisher 参数）.
     */
    @Autowired
    public MetricCalcService(MetricDefService metricDefService,
                             SqlExecutor sqlExecutor,
                             GroovyExecutor groovyExecutor,
                             EmpIndexResultMapper empIndexResultMapper,
                             OrgIndexResultMapper orgIndexResultMapper,
                             CustIndexResultMapper custIndexResultMapper,
                             PerfRunTaskMapper perfRunTaskMapper,
                             PerfEngineProperties perfEngineProperties,
                             SubjectFetcher subjectFetcher,
                             ApplicationEventPublisher eventPublisher) {
        this.metricDefService = metricDefService;
        this.sqlExecutor = sqlExecutor;
        this.groovyExecutor = groovyExecutor;
        this.empIndexResultMapper = empIndexResultMapper;
        this.orgIndexResultMapper = orgIndexResultMapper;
        this.custIndexResultMapper = custIndexResultMapper;
        this.perfRunTaskMapper = perfRunTaskMapper;
        this.perfEngineProperties = perfEngineProperties;
        this.subjectFetcher = subjectFetcher;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 执行单个指标的计算（V1.7 多主体版本）.
     *
     * @param metricCode 指标编码（必填）
     * @param dataDate   数据日期（必填）
     * @param version    数据版本（必填）
     * @return run_task 主键 ID
     * @throws PerfException 指标不存在 / 指标已软删除 / SQL 语法非法 / 沙盒拦截 / 超时 / PROC/SUMMARY 未支持 / slot 未分配
     */
    public String calcMetric(String metricCode, LocalDate dataDate, String version) {
        // 1. 定义加载与基本校验——指标不存在直接抛，不插 run_task（计划要求）
        PerfMetricDef def = metricDefService.getByCodeOrNull(metricCode);
        if (def == null || (def.getDeleted() != null && def.getDeleted() == 1)) {
            throw new PerfException(PerfErrorCode.METRIC_NOT_FOUND, metricCode);
        }

        // 2. 生成任务 ID 并插入 PENDING 记录（先插入再切状态，便于 FAILED 场景可见）
        String taskId = UUID.randomUUID().toString().replace("-", "");
        insertPendingTask(taskId, metricCode, dataDate, version);

        try {
            // 3. 切换 RUNNING
            perfRunTaskMapper.updateStatus(taskId, "RUNNING", null);

            // 4. slot 校验（在路由前执行，避免 SQL/Groovy 已执行但 slot 非法白费）
            validateSlot(def);

            // 5. 按计算类型路由
            String logicType = def.getCalcLogicType();
            SubjectStats stats;
            if ("SQL".equalsIgnoreCase(logicType)) {
                stats = executeSqlAndPersist(def, dataDate, version);
            } else if ("EXPR".equalsIgnoreCase(logicType) || "GROOVY".equalsIgnoreCase(logicType)) {
                stats = executeGroovyAndPersist(def, dataDate, version);
            } else if ("PROC".equalsIgnoreCase(logicType) || "SUMMARY".equalsIgnoreCase(logicType)) {
                throw new PerfException(PerfErrorCode.CALC_JOB_FAILED,
                        "PROC/SUMMARY 暂不支持自动调度: " + logicType);
            } else {
                throw new PerfException(PerfErrorCode.METRIC_CALC_LOGIC_INVALID,
                        "未知 calcLogicType=" + logicType);
            }

            // 6. 按主体统计决定终态
            String finalStatus = stats.failed() == 0 ? "SUCCESS"
                    : stats.success() == 0 ? "FAILED" : "PARTIAL_FAILED";
            perfRunTaskMapper.updateStatusWithParams(taskId, finalStatus, null, stats.toJson());

            // 7. 发布计算完成事件（终态写入后）
            eventPublisher.publishEvent(new MetricCalcCompletedEvent(
                    metricCode, def.getBaseDim(), dataDate, version,
                    finalStatus, stats.total(), stats.success(), stats.failed(),
                    taskId, "SCHEDULED", LocalDateTime.now()));

            return taskId;
        } catch (PerfException pe) {
            // 失败态 + 错误信息落库
            markFailed(taskId, pe);
            // 发布 FAILED 事件（PerfException 路径）
            eventPublisher.publishEvent(new MetricCalcCompletedEvent(
                    metricCode, def.getBaseDim(), dataDate, version,
                    "FAILED", 0, 0, 0, taskId, "SCHEDULED", LocalDateTime.now()));
            throw pe;
        } catch (Exception ex) {
            // 兜底其他非预期异常
            markFailed(taskId, ex);
            eventPublisher.publishEvent(new MetricCalcCompletedEvent(
                    metricCode, def.getBaseDim(), dataDate, version,
                    "FAILED", 0, 0, 0, taskId, "SCHEDULED", LocalDateTime.now()));
            throw new PerfException(PerfErrorCode.CALC_JOB_FAILED, ex, "指标计算异常: " + ex.getMessage());
        }
    }

    /**
     * SQL 类指标：调 SqlExecutor 得到 Map&lt;baseKey, value&gt;，按 baseDim 批量 UPSERT 到宽表.
     *
     * @return SubjectStats（SQL 类型按结果行数计，全部视为成功）
     */
    private SubjectStats executeSqlAndPersist(PerfMetricDef def, LocalDate dataDate, String version) {
        if (def.getSqlText() == null || def.getSqlText().isBlank()) {
            throw new PerfException(PerfErrorCode.METRIC_CALC_LOGIC_INVALID,
                    "SQL 类型指标 sqlText 为空: " + def.getMetricCode());
        }
        Map<String, Object> params = new HashMap<>();
        params.put("dataDate", dataDate);
        params.put("version", version);
        Duration timeout = Duration.ofSeconds(perfEngineProperties == null
                ? 30 : Math.max(1, perfEngineProperties.getSqlTimeoutSeconds()));
        Map<String, BigDecimal> values = sqlExecutor.execute(def.getSqlText(), params, timeout);
        persistValues(def, values, dataDate, version);
        return SubjectStats.allSuccess(values.size());
    }

    /**
     * EXPR/GROOVY 类指标：通过 SubjectFetcher 取主体集合，逐主体调 GroovyExecutor（V1.7 多主体）.
     *
     * <p>单个主体失败时捕获异常继续处理下一个主体（per-subject 容错），
     * 汇总成功/失败数写入 SubjectStats，failedSamples 最多保留前 10 个主体 ID。
     *
     * @return SubjectStats（包含 total/success/failed/failedSamples）
     */
    private SubjectStats executeGroovyAndPersist(PerfMetricDef def, LocalDate dataDate, String version) {
        if (def.getExprText() == null || def.getExprText().isBlank()) {
            throw new PerfException(PerfErrorCode.METRIC_CALC_LOGIC_INVALID,
                    "EXPR 类型指标 exprText 为空: " + def.getMetricCode());
        }
        // 取主体集合
        Map<String, Object> sqlParams = Map.of("dataDate", dataDate, "version", version);
        List<String> subjects = subjectFetcher.fetch(def.getSubjectSql(), sqlParams);
        if (subjects.isEmpty()) {
            log.warn("[MetricCalc] metric={} 主体集合为空，跳过", def.getMetricCode());
            return SubjectStats.empty();
        }

        List<String> refCodes = parseRefMetricCodes(def.getRefMetricCodes());
        Duration timeout = Duration.ofSeconds(perfEngineProperties == null
                ? 30 : Math.max(1, perfEngineProperties.getSqlTimeoutSeconds()));

        int success = 0;
        int failed = 0;
        List<String> failedSamples = new ArrayList<>();
        Map<String, BigDecimal> outputs = new HashMap<>();

        for (String subject : subjects) {
            try {
                // 加载该主体的引用指标值作为 Groovy 变量
                Map<String, Object> vars = loadRefValues(subject, refCodes, def.getBaseDim(), dataDate, version);
                BigDecimal value = groovyExecutor.execute(def.getExprText(), vars, timeout);
                outputs.put(subject, value);
                success++;
            } catch (Exception perSubjectEx) {
                failed++;
                // 最多保留 10 个失败样本，避免 params_json 过大
                if (failedSamples.size() < 10) {
                    failedSamples.add(subject);
                }
                log.warn("[MetricCalc] metric={} subject={} 失败: {}",
                        def.getMetricCode(), subject, perSubjectEx.getMessage());
            }
        }

        // 将成功主体的计算结果批量写入宽表
        persistValues(def, outputs, dataDate, version);
        return new SubjectStats(subjects.size(), success, failed, failedSamples);
    }

    /**
     * 解析引用指标编码 JSON 数组.
     *
     * @param json refMetricCodes 字段值（可空）
     * @return 指标编码列表，解析失败返回空列表
     */
    private List<String> parseRefMetricCodes(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("[MetricCalc] refMetricCodes 解析失败: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 按 baseDim 从宽表加载引用指标值，作为 Groovy 变量绑定.
     *
     * @param subject  主体 ID（员工工号/机构编码/客户 ID）
     * @param refCodes 引用指标编码列表
     * @param baseDim  基础维度（EMP/ORG/CUST）
     * @param dataDate 数据日期
     * @param version  数据版本
     * @return 指标编码 -&gt; 指标值 映射（作为 Groovy vars）
     */
    private Map<String, Object> loadRefValues(String subject, List<String> refCodes,
                                               String baseDim, LocalDate dataDate, String version) {
        if (refCodes.isEmpty()) {
            return Map.of();
        }
        Map<String, BigDecimal> values = switch (baseDim == null ? "" : baseDim.toUpperCase()) {
            case "EMP"  -> empIndexResultMapper.selectSlotValuesByCodes(subject, refCodes, dataDate, version);
            case "ORG"  -> orgIndexResultMapper.selectSlotValuesByCodes(subject, refCodes, dataDate, version);
            case "CUST" -> custIndexResultMapper.selectSlotValuesByCodes(subject, refCodes, dataDate, version);
            default -> throw new PerfException(PerfErrorCode.METRIC_CALC_LOGIC_INVALID,
                    "未知 baseDim=" + baseDim);
        };
        return new HashMap<>(values);
    }

    /**
     * 按 baseDim 分流到三张宽表.
     */
    private void persistValues(PerfMetricDef def, Map<String, BigDecimal> values,
                               LocalDate dataDate, String version) {
        String baseDim = def.getBaseDim();
        Integer slot = def.getValSlot();
        if (values == null || values.isEmpty()) {
            log.info("[MetricCalc] metric={} 无结果，跳过 UPSERT", def.getMetricCode());
            return;
        }
        for (Map.Entry<String, BigDecimal> entry : values.entrySet()) {
            String key = entry.getKey();
            BigDecimal value = entry.getValue();
            switch (baseDim == null ? "" : baseDim.toUpperCase()) {
                case "EMP":
                    empIndexResultMapper.insertSlotValue(key, dataDate, version, slot, value);
                    break;
                case "ORG":
                    orgIndexResultMapper.insertSlotValue(key, dataDate, version, slot, value);
                    break;
                case "CUST":
                    custIndexResultMapper.insertSlotValue(key, dataDate, version, slot, value);
                    break;
                default:
                    throw new PerfException(PerfErrorCode.METRIC_CALC_LOGIC_INVALID,
                            "未知 baseDim=" + baseDim);
            }
        }
    }

    /**
     * Service 层 slot 校验（val_${slot} 列名拼接前必须校验范围）.
     *
     * @throws PerfException slot 为 null 或不在 [1, 200]
     */
    private void validateSlot(PerfMetricDef def) {
        Integer slot = def.getValSlot();
        if (slot == null) {
            throw new PerfException(PerfErrorCode.METRIC_CALC_LOGIC_INVALID,
                    "指标 " + def.getMetricCode() + " 未分配 val_slot");
        }
        if (slot < 1 || slot > 200) {
            throw new PerfException(PerfErrorCode.METRIC_CALC_LOGIC_INVALID,
                    "val_slot 超出 [1,200]: " + slot);
        }
    }

    private void insertPendingTask(String taskId, String metricCode,
                                   LocalDate dataDate, String version) {
        PerfRunTask task = new PerfRunTask();
        task.setId(taskId);
        task.setTaskType("METRIC_RUN");
        task.setTaskKey(metricCode);
        task.setDataDate(dataDate);
        task.setDataVersion(version);
        task.setStatus("PENDING");
        task.setStartTime(LocalDateTime.now());
        perfRunTaskMapper.insert(task);
    }

    private void markFailed(String taskId, Throwable ex) {
        try {
            String msg = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            // 错误信息超长截断，避免 longtext 过大
            if (msg.length() > 2000) {
                msg = msg.substring(0, 2000);
            }
            perfRunTaskMapper.updateStatus(taskId, "FAILED", msg);
        } catch (Exception suppressed) {
            log.error("[MetricCalc] 任务 {} 标记 FAILED 失败: {}", taskId, suppressed.getMessage());
        }
    }
}
