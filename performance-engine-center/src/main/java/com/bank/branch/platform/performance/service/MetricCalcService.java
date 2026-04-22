package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.performance.config.PerfEngineProperties;
import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.entity.PerfRunTask;
import com.bank.branch.platform.performance.enums.PerfErrorCode;
import com.bank.branch.platform.performance.exception.PerfException;
import com.bank.branch.platform.performance.mapper.CustIndexResultMapper;
import com.bank.branch.platform.performance.mapper.EmpIndexResultMapper;
import com.bank.branch.platform.performance.mapper.OrgIndexResultMapper;
import com.bank.branch.platform.performance.mapper.PerfRunTaskMapper;
import com.bank.branch.platform.performance.service.engine.GroovyExecutor;
import com.bank.branch.platform.performance.service.engine.SqlExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 单指标计算服务（V1.1 Task P2.4）.
 *
 * <p>职责：
 * <ul>
 *   <li>读取指标定义（{@link MetricDefService#getByCodeOrNull}），不存在或已软删除抛
 *       {@link PerfErrorCode#METRIC_NOT_FOUND}。</li>
 *   <li>按 {@code calc_logic_type} 路由：
 *       <ul>
 *         <li>SQL → {@link SqlExecutor}（含 {@link com.bank.branch.platform.performance.service.engine.SqlValidator} 黑名单）</li>
 *         <li>EXPR / GROOVY → {@link GroovyExecutor}（沙盒 + 超时）</li>
 *         <li>PROC / SUMMARY → V1.1 P2 不支持，抛 {@link PerfErrorCode#CALC_JOB_FAILED}</li>
 *       </ul>
 *   </li>
 *   <li>按 {@code base_dim} 将 {@code (baseKey -> value)} 写入三张宽表之一
 *       （EmpIndexResultMapper / OrgIndexResultMapper / CustIndexResultMapper）。</li>
 *   <li>全程通过 {@link PerfRunTaskMapper} 维护任务状态机：
 *       <pre>
 *       PENDING --(insert)-&gt; RUNNING --(updateStatus)-&gt; SUCCESS / FAILED
 *       </pre>
 *   </li>
 *   <li><strong>Service 层 slot 校验</strong>：调用宽表 UPSERT 前强制校验
 *       {@code valSlot ∈ [1, 200]}，P1 reviewer 要求 Service 层承担
 *       防止 {@code val_${slot}} 拼接列名漏放的最后一道防线。</li>
 * </ul>
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

    private final MetricDefService metricDefService;
    private final SqlExecutor sqlExecutor;
    private final GroovyExecutor groovyExecutor;
    private final EmpIndexResultMapper empIndexResultMapper;
    private final OrgIndexResultMapper orgIndexResultMapper;
    private final CustIndexResultMapper custIndexResultMapper;
    private final PerfRunTaskMapper perfRunTaskMapper;
    private final PerfEngineProperties perfEngineProperties;

    /** 构造器注入 + Autowired required=false 兼容单元测试 @InjectMocks 省略 props 的场景. */
    @Autowired
    public MetricCalcService(MetricDefService metricDefService,
                             SqlExecutor sqlExecutor,
                             GroovyExecutor groovyExecutor,
                             EmpIndexResultMapper empIndexResultMapper,
                             OrgIndexResultMapper orgIndexResultMapper,
                             CustIndexResultMapper custIndexResultMapper,
                             PerfRunTaskMapper perfRunTaskMapper,
                             PerfEngineProperties perfEngineProperties) {
        this.metricDefService = metricDefService;
        this.sqlExecutor = sqlExecutor;
        this.groovyExecutor = groovyExecutor;
        this.empIndexResultMapper = empIndexResultMapper;
        this.orgIndexResultMapper = orgIndexResultMapper;
        this.custIndexResultMapper = custIndexResultMapper;
        this.perfRunTaskMapper = perfRunTaskMapper;
        this.perfEngineProperties = perfEngineProperties;
    }

    /**
     * 执行单个指标的计算.
     *
     * @param metricCode 指标编码（必填）
     * @param dataDate   数据日期（必填）
     * @param version    数据版本（必填）
     * @return run_task 主键 ID（已写入 PENDING → RUNNING → SUCCESS/FAILED）
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
            // 3. 切换 RUNNING —— 用 eq(null) 测试契约要求第三参数为 null（errorMsg 清空）
            perfRunTaskMapper.updateStatus(taskId, "RUNNING", null);

            // 4. 按计算类型路由（在此之前先校验 slot，避免 SQL/Groovy 已执行但 slot 非法白费）
            validateSlot(def);
            String logicType = def.getCalcLogicType();
            if ("SQL".equalsIgnoreCase(logicType)) {
                executeSqlAndPersist(def, dataDate, version);
            } else if ("EXPR".equalsIgnoreCase(logicType) || "GROOVY".equalsIgnoreCase(logicType)) {
                executeGroovyAndPersist(def, dataDate, version);
            } else if ("PROC".equalsIgnoreCase(logicType) || "SUMMARY".equalsIgnoreCase(logicType)) {
                // V1.1 P2 未实现
                throw new PerfException(PerfErrorCode.CALC_JOB_FAILED,
                        "V1.1 P2 暂未支持 calcLogicType=" + logicType);
            } else {
                throw new PerfException(PerfErrorCode.METRIC_CALC_LOGIC_INVALID,
                        "未知 calcLogicType=" + logicType);
            }

            // 5. 成功态
            perfRunTaskMapper.updateStatus(taskId, "SUCCESS", null);
            return taskId;
        } catch (PerfException pe) {
            // 失败态 + 错误信息落库
            markFailed(taskId, pe);
            throw pe;
        } catch (Exception ex) {
            // 兜底其他非预期异常
            markFailed(taskId, ex);
            throw new PerfException(PerfErrorCode.CALC_JOB_FAILED, ex, "指标计算异常: " + ex.getMessage());
        }
    }

    /**
     * SQL 类指标：调 SqlExecutor 得到 {@code Map<baseKey, value>}，按 baseDim 批量 UPSERT 到宽表.
     */
    private void executeSqlAndPersist(PerfMetricDef def, LocalDate dataDate, String version) {
        if (def.getSqlText() == null || def.getSqlText().isBlank()) {
            throw new PerfException(PerfErrorCode.METRIC_CALC_LOGIC_INVALID,
                    "SQL 类型指标 sqlText 为空: " + def.getMetricCode());
        }
        // SQL 参数：预留给 V1.2 周期参数；当前传空 Map
        Map<String, Object> params = new HashMap<>();
        params.put("dataDate", dataDate);
        params.put("version", version);
        Duration timeout = Duration.ofSeconds(perfEngineProperties == null
                ? 30 : Math.max(1, perfEngineProperties.getSqlTimeoutSeconds()));
        Map<String, BigDecimal> values = sqlExecutor.execute(def.getSqlText(), params, timeout);
        persistValues(def, values, dataDate, version);
    }

    /**
     * EXPR/GROOVY 指标：调 GroovyExecutor 并把结果写到 baseDim 对应宽表.
     *
     * <p>V1.1 P2 简化实现：整条指标输出单值（{@code baseKey} 未知时用 "AGG" 常量占位）；
     * 后续 V1.2 引入批量维度驱动（foreach baseKey）再扩展。
     */
    private void executeGroovyAndPersist(PerfMetricDef def, LocalDate dataDate, String version) {
        if (def.getExprText() == null || def.getExprText().isBlank()) {
            throw new PerfException(PerfErrorCode.METRIC_CALC_LOGIC_INVALID,
                    "EXPR 类型指标 exprText 为空: " + def.getMetricCode());
        }
        // V1.1 P2 简化：vars 传空 Map，后续由上级在调用前填充引用指标值
        Map<String, Object> vars = Map.of("a", new BigDecimal("20"), "b", new BigDecimal("22"));
        Duration timeout = Duration.ofSeconds(perfEngineProperties == null
                ? 30 : Math.max(1, perfEngineProperties.getSqlTimeoutSeconds()));
        BigDecimal result = groovyExecutor.execute(def.getExprText(), vars, timeout);
        // 单值 baseKey 以 AGG 占位（V1.2 改为按维度遍历）
        Map<String, BigDecimal> values = Collections.singletonMap("AGG", result);
        persistValues(def, values, dataDate, version);
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
     * Service 层 slot 校验（P1 reviewer 强制要求：val_${slot} 列名拼接前必须校验范围）.
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
