package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.auth.api.CurrentUserApi;
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
import com.bank.branch.platform.performance.service.engine.DateMacroResolver;
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
 *       {@code valSlot ∈ [1, 400]}，防止 {@code val_${slot}} 拼接列名漏放。</li>
 * </ul>
 *
 * <p><strong>V1.7 多主体改造</strong>：EXPR/GROOVY 类型改为逐主体执行，
 * 引入 {@link SubjectStats} 追踪每主体成功/失败，按结果写 SUCCESS / PARTIAL_FAILED / FAILED。
 * SubjectStats 新增 jobKey / triggerType 字段（spec § 4.6）。
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

    /** 默认触发类型（手动触发场景 / 兼容旧调用方）. */
    private static final String TRIGGER_TYPE_MANUAL = "MANUAL";

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
    private final CurrentUserApi currentUserApi;

    /**
     * 构造器注入（V1.7 新增 SubjectFetcher + ApplicationEventPublisher 参数；
     * 新增 CurrentUserApi 用于 run_task.started_by 兜底）.
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
                             ApplicationEventPublisher eventPublisher,
                             CurrentUserApi currentUserApi) {
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
        this.currentUserApi = currentUserApi;
    }

    /**
     * 执行单个指标的计算（V1.7 多主体版本，兼容旧调用方，默认 triggerType=MANUAL）.
     *
     * @param metricCode 指标编码（必填）
     * @param dataDate   数据日期（必填）
     * @param version    数据版本（必填）
     * @return run_task 主键 ID
     * @throws PerfException 指标不存在 / 指标已软删除 / SQL 语法非法 / 沙盒拦截 / 超时 / PROC/SUMMARY 未支持 / slot 未分配
     */
    public String calcMetric(String metricCode, LocalDate dataDate, String version) {
        // 保持旧调用方兼容：默认 triggerType = MANUAL
        return calcMetric(metricCode, dataDate, version, TRIGGER_TYPE_MANUAL);
    }

    /**
     * 执行单个指标的计算（V1.7 多主体版本，支持指定 triggerType）.
     *
     * @param metricCode  指标编码（必填）
     * @param dataDate    数据日期（必填）
     * @param version     数据版本（必填）
     * @param triggerType 触发类型（SCHEDULED/MANUAL/RECALC，spec § 4.5）
     * @return run_task 主键 ID
     * @throws PerfException 指标不存在 / 指标已软删除 / SQL 语法非法 / 沙盒拦截 / 超时 / PROC/SUMMARY 未支持 / slot 未分配
     */
    public String calcMetric(String metricCode, LocalDate dataDate, String version, String triggerType) {
        // 业绩分配日期 allocDate 不传 → 委托 5 参重载传 null，由 SQL 绑定阶段兜底为 dataDate
        return calcMetric(metricCode, dataDate, version, triggerType, null);
    }

    /**
     * 执行单个指标的计算（新增业绩分配日期 :allocDate 入参）.
     *
     * <p>allocDate 非派生变量，作为入参显式传入：定时任务链路默认 null（绑定阶段兜底为 dataDate，
     * 即 T-1）；手动执行 / 试运行可由页面指定具体业绩分配日期。
     *
     * @param metricCode  指标编码（必填）
     * @param dataDate    数据日期（必填）
     * @param version     数据版本（必填）
     * @param triggerType 触发类型（SCHEDULED/MANUAL/RECALC，spec § 4.5）
     * @param allocDate   业绩分配日期（可为 null，绑定阶段兜底为 dataDate）
     * @return run_task 主键 ID
     * @throws PerfException 指标不存在 / 指标已软删除 / SQL 语法非法 / 沙盒拦截 / 超时 / PROC/SUMMARY 未支持 / slot 未分配
     */
    public String calcMetric(String metricCode, LocalDate dataDate, String version,
                             String triggerType, LocalDate allocDate) {
        return calcMetricWithStats(metricCode, dataDate, version, triggerType, allocDate).runTaskId();
    }

    /**
     * V1.13+ 新增：在 calcMetric 行为基础上额外返回主体统计（total/success/failed），
     * 供批量入口（{@link MetricBatchCalcService}）拿到真实写入行数填 PERF_METRIC_CALC_LOG.row_count。
     *
     * <p>语义与 {@link #calcMetric(String, LocalDate, String, String)} 完全等价，
     * 仅返回类型从 String runTaskId 扩展为 {@link MetricCalcResult}。
     */
    public MetricCalcResult calcMetricWithStats(String metricCode, LocalDate dataDate, String version, String triggerType) {
        // 业绩分配日期不传 → 委托 5 参重载传 null（绑定阶段兜底为 dataDate）
        return calcMetricWithStats(metricCode, dataDate, version, triggerType, null);
    }

    /**
     * V1.13+ 在 {@link #calcMetricWithStats(String, LocalDate, String, String)} 基础上新增业绩分配
     * 日期 :allocDate 入参（非派生，作为参数显式传入；null 时绑定阶段兜底为 dataDate）.
     *
     * @param allocDate 业绩分配日期（可为 null，绑定阶段兜底为 dataDate）
     */
    public MetricCalcResult calcMetricWithStats(String metricCode, LocalDate dataDate, String version,
                                                String triggerType, LocalDate allocDate) {
        // 1. 定义加载与基本校验——指标不存在直接抛，不插 run_task（计划要求）
        PerfMetricDef def = metricDefService.getByCodeOrNull(metricCode);
        if (def == null || (def.getDeleted() != null && def.getDeleted() == 1)) {
            throw new PerfException(PerfErrorCode.METRIC_NOT_FOUND, metricCode);
        }
        // V1.9：维度无关型指标（baseDim=null）无 slot、无宽表归属，不允许进入计算路径
        if (def.getBaseDim() == null || def.getBaseDim().isBlank()) {
            throw new PerfException(PerfErrorCode.METRIC_CALC_LOGIC_INVALID,
                    "维度无关型指标不支持自动/手动计算: " + metricCode + "（base_dim 为空）");
        }

        // 2. jobKey 推导规则：PERF_METRIC_{metricCode}（与 P1 resolveGroup 规则一致）
        String jobKey = "PERF_METRIC_" + metricCode;

        // 3. 生成任务 ID 并插入 PENDING 记录（先插入再切状态，便于 FAILED 场景可见）
        String taskId = UUID.randomUUID().toString().replace("-", "");
        insertPendingTask(taskId, metricCode, dataDate, version);

        try {
            // 4. 切换 RUNNING
            perfRunTaskMapper.updateStatus(taskId, "RUNNING", null);

            // 5. slot 校验（在路由前执行，避免 SQL/Groovy 已执行但 slot 非法白费）
            validateSlot(def);

            // 6. 按计算类型路由
            String logicType = def.getCalcLogicType();
            SubjectStats stats;
            if ("SQL".equalsIgnoreCase(logicType)) {
                stats = executeSqlAndPersist(def, dataDate, version, jobKey, triggerType, allocDate);
            } else if ("EXPR".equalsIgnoreCase(logicType) || "GROOVY".equalsIgnoreCase(logicType)) {
                stats = executeGroovyAndPersist(def, dataDate, version, jobKey, triggerType);
            } else if ("PROC".equalsIgnoreCase(logicType) || "SUMMARY".equalsIgnoreCase(logicType)) {
                throw new PerfException(PerfErrorCode.CALC_JOB_FAILED,
                        "PROC/SUMMARY 暂不支持自动调度: " + logicType);
            } else {
                throw new PerfException(PerfErrorCode.METRIC_CALC_LOGIC_INVALID,
                        "未知 calcLogicType=" + logicType);
            }

            // 7. 按主体统计决定终态
            String finalStatus = stats.failed() == 0 ? "SUCCESS"
                    : stats.success() == 0 ? "FAILED" : "PARTIAL_FAILED";
            perfRunTaskMapper.updateStatusWithParams(taskId, finalStatus, null, stats.toJson());

            // 8. 发布计算完成事件（终态写入后），triggerType 来自入参（spec § 4.5）
            eventPublisher.publishEvent(new MetricCalcCompletedEvent(
                    metricCode, def.getBaseDim(), dataDate, version,
                    finalStatus, stats.total(), stats.success(), stats.failed(),
                    taskId, triggerType, LocalDateTime.now()));

            return new MetricCalcResult(taskId, stats.total(), stats.success(), stats.failed());
        } catch (PerfException pe) {
            // 失败态 + 错误信息落库
            markFailed(taskId, pe);
            // 发布 FAILED 事件（安全包装，避免 listener 抛异常掩盖原异常）
            safePublishFailedEvent(metricCode, def, dataDate, version, taskId, triggerType);
            throw pe;
        } catch (Exception ex) {
            // 兜底其他非预期异常
            markFailed(taskId, ex);
            safePublishFailedEvent(metricCode, def, dataDate, version, taskId, triggerType);
            throw new PerfException(PerfErrorCode.CALC_JOB_FAILED, ex, "指标计算异常: " + ex.getMessage());
        }
    }

    /**
     * SQL 类指标：调 SqlExecutor 得到 Map&lt;baseKey, value&gt;，按 baseDim 批量 UPSERT 到宽表.
     *
     * @param jobKey      任务键（透传到 SubjectStats）
     * @param triggerType 触发类型（透传到 SubjectStats）
     * @param allocDate   业绩分配日期（:allocDate；null 时兜底为 dataDate）
     * @return SubjectStats（SQL 类型按结果行数计，全部视为成功）
     */
    private SubjectStats executeSqlAndPersist(PerfMetricDef def, LocalDate dataDate, String version,
                                               String jobKey, String triggerType, LocalDate allocDate) {
        if (def.getSqlText() == null || def.getSqlText().isBlank()) {
            throw new PerfException(PerfErrorCode.METRIC_CALC_LOGIC_INVALID,
                    "SQL 类型指标 sqlText 为空: " + def.getMetricCode());
        }
        Map<String, Object> params = new HashMap<>();
        params.put("dataDate", dataDate);
        params.put("version", version);
        params.putAll(DateMacroResolver.resolve(dataDate));
        // 业绩分配日期 :allocDate —— 非派生入参；定时链路传 null 兜底为 dataDate（已是 T-1），
        // 手动执行 / 试运行由页面指定。放在 DateMacroResolver.resolve 之后，避免被派生宏覆盖。
        params.put("allocDate", allocDate != null ? allocDate : dataDate);
        // 对象id占位符 :objectId —— 真实调度执行无单主体上下文，绑定 null；
        // 生产 SQL 应写成 (:objectId IS NULL OR emp_id = :objectId)，使试运行(传值)与真实执行(null=全量)都成立
        params.putIfAbsent("objectId", null);
        Duration timeout = Duration.ofSeconds(perfEngineProperties == null
                ? 30 : Math.max(1, perfEngineProperties.getSqlTimeoutSeconds()));
        Map<String, BigDecimal> values = sqlExecutor.execute(def.getSqlText(), params, timeout);
        persistValues(def, values, dataDate, version);
        return SubjectStats.allSuccess(values.size(), jobKey, triggerType);
    }

    /**
     * EXPR/GROOVY 类指标：通过 SubjectFetcher 取主体集合，逐主体调 GroovyExecutor（V1.7 多主体）.
     *
     * <p>单个主体失败时捕获异常继续处理下一个主体（per-subject 容错），
     * 汇总成功/失败数写入 SubjectStats，failedSamples 最多保留前 10 个主体 ID。
     *
     * <p>V1.7 N+1 优化：metricCode → val_slot 映射在 for 循环外预查 1 次，
     * 避免 N 个主体各重查一次（共 N+1 → 1+N 次 SQL 优化为 1+N 次，slotMap 查询从 N 次降为 1 次）。
     *
     * @param jobKey      任务键（透传到 SubjectStats）
     * @param triggerType 触发类型（透传到 SubjectStats）
     * @return SubjectStats（包含 total/success/failed/failedSamples/jobKey/triggerType）
     */
    private SubjectStats executeGroovyAndPersist(PerfMetricDef def, LocalDate dataDate, String version,
                                                  String jobKey, String triggerType) {
        if (def.getExprText() == null || def.getExprText().isBlank()) {
            throw new PerfException(PerfErrorCode.METRIC_CALC_LOGIC_INVALID,
                    "EXPR 类型指标 exprText 为空: " + def.getMetricCode());
        }
        // V1.13+：直接从对应宽表按 (data_date, version) 取主体集合，废弃 subject_sql。
        // 业务方不再需要为每个 EXPR 指标维护一段主体查询 SQL；主体即"该日宽表中已存在该维度的所有行键"。
        String baseDim = def.getBaseDim() == null ? "" : def.getBaseDim().toUpperCase();
        List<String> subjects = switch (baseDim) {
            case "EMP"  -> empIndexResultMapper.selectDistinctEmpIds(dataDate, version);
            case "ORG"  -> orgIndexResultMapper.selectDistinctOrgCodes(dataDate, version);
            case "CUST" -> custIndexResultMapper.selectDistinctCustIds(dataDate, version);
            default -> throw new PerfException(PerfErrorCode.METRIC_CALC_LOGIC_INVALID,
                    "未知 baseDim=" + def.getBaseDim());
        };
        if (subjects.isEmpty()) {
            log.warn("[MetricCalc] metric={} baseDim={} 该日宽表无任何主体，跳过",
                    def.getMetricCode(), baseDim);
            return SubjectStats.empty(jobKey, triggerType);
        }

        // V1.13+：refCodes 来自两路并集——def.ref_metric_codes（业务/前端登记的）+ 正则从 expr_text
        // 扫描出的 M_xxx 字面量。前端"表达式构建器"暂未把公式里的指标自动写回 ref_metric_codes，
        // 后端兜底解析保证 Groovy vars 完整覆盖公式里出现的所有 metric_code。
        List<String> refCodes = mergeRefCodes(
                parseRefMetricCodes(def.getRefMetricCodes()),
                extractMetricCodesFromExpr(def.getExprText()));
        Duration timeout = Duration.ofSeconds(perfEngineProperties == null
                ? 30 : Math.max(1, perfEngineProperties.getSqlTimeoutSeconds()));

        // V1.7 N+1 优化：预查 metricCode → val_slot 映射 1 次，避免 N 主体 × N 次重查
        Map<String, Integer> slotMap = refCodes.isEmpty() ? Map.of()
                : resolveSlotMap(def.getBaseDim(), refCodes);

        int success = 0;
        int failed = 0;
        List<String> failedSamples = new ArrayList<>();
        Map<String, BigDecimal> outputs = new HashMap<>();

        for (String subject : subjects) {
            try {
                // 使用预查的 slotMap 加载引用指标值（避免每主体重查 slotMap）
                Map<String, Object> vars = loadRefValuesBySlotMap(subject, slotMap, refCodes,
                        def.getBaseDim(), dataDate, version);
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
        return new SubjectStats(subjects.size(), success, failed, failedSamples, jobKey, triggerType);
    }

    /**
     * V1.7：根据 baseDim 一次性查 metricCode → val_slot 映射（N+1 优化的预查步骤）.
     *
     * @param baseDim  基础维度（EMP/ORG/CUST）
     * @param refCodes 引用指标编码列表（非空）
     * @return metricCode -&gt; val_slot 映射
     */
    private Map<String, Integer> resolveSlotMap(String baseDim, List<String> refCodes) {
        return switch (baseDim == null ? "" : baseDim.toUpperCase()) {
            case "EMP"  -> empIndexResultMapper.selectValSlotsByCodes(refCodes);
            case "ORG"  -> orgIndexResultMapper.selectValSlotsByCodes(refCodes);
            case "CUST" -> custIndexResultMapper.selectValSlotsByCodes(refCodes);
            default -> throw new PerfException(PerfErrorCode.METRIC_CALC_LOGIC_INVALID,
                    "未知 baseDim=" + baseDim);
        };
    }

    /**
     * V1.7：基于预查的 slotMap，per-subject 取每个 slot 的值（N+1 优化的 per-subject 步骤）.
     *
     * @param subject  主体 ID（员工工号/机构编码/客户 ID）
     * @param slotMap  metricCode -&gt; val_slot 预查结果
     * @param baseDim  基础维度（EMP/ORG/CUST）
     * @param dataDate 数据日期
     * @param version  数据版本
     * @return 指标编码 -&gt; 指标值 映射（作为 Groovy vars）
     */
    private Map<String, Object> loadRefValuesBySlotMap(String subject, Map<String, Integer> slotMap,
                                                        List<String> refCodes,
                                                        String baseDim, LocalDate dataDate, String version) {
        if (refCodes == null || refCodes.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> result = new HashMap<>();
        // V1.13+：遍历原始 refCodes 而非 slotMap.keys，保证所有引用指标都进 vars。
        // - slotMap 缺失（def.val_slot=NULL，根本没分配槽位）→ 直接 BigDecimal.ZERO
        // - slotMap 命中但宽表查无该 (subject, slot) 行/列值→ 也兜底 BigDecimal.ZERO
        // 目的：避免 Groovy "No such property" 或 null + number NPE，让 EXPR 公式总能跑通。
        for (String refCode : refCodes) {
            Integer slot = slotMap.get(refCode);
            BigDecimal value = null;
            if (slot != null) {
                value = switch (baseDim == null ? "" : baseDim.toUpperCase()) {
                    case "EMP"  -> empIndexResultMapper.selectValBySlot(subject, slot, dataDate, version);
                    case "ORG"  -> orgIndexResultMapper.selectValBySlot(subject, slot, dataDate, version);
                    case "CUST" -> custIndexResultMapper.selectValBySlot(subject, slot, dataDate, version);
                    default -> throw new PerfException(PerfErrorCode.METRIC_CALC_LOGIC_INVALID,
                            "未知 baseDim=" + baseDim);
                };
            }
            result.put(refCode, value != null ? value : BigDecimal.ZERO);
        }
        return result;
    }

    /**
     * 供试运行复用：按 (baseDim, dataDate, subjectId, version) 从对应维度宽表加载 exprText 中引用的
     * 所有指标值，作为 Groovy 变量。缺失槽位/查无值/维度无关均兜底 ZERO，避免 Groovy "No such property"。
     *
     * @param baseDim   基础维度 EMP/ORG/CUST
     * @param exprText  Groovy 表达式（含 M_xxx 引用）
     * @param dataDate  数据日期
     * @param subjectId 对象值（员工工号/机构编码/客户ID）
     * @param version   数据版本
     * @return 指标编码 -&gt; 值 的变量映射
     */
    /**
     * 供试运行复用：解析某主体某日期"最近导入"的数据版本（宽表按 updated_time 优先）。
     *
     * <p>宽表行按 (subject, data_date, version) 隔离，而导入数据散落在多个时间戳版本，
     * SYS_CONTROL 当前版本未必是该主体该日有数据的版本。试运行据此按数据反查真实值，避免恒为 0。
     *
     * @return 命中的数据版本；主体为空、维度无关或查无数据时返回 null（调用方再降级到 SYS_CONTROL/V1）
     */
    public String resolveDataVersionForSubject(String baseDim, String subjectId, LocalDate dataDate) {
        if (subjectId == null || subjectId.isBlank()) {
            return null;
        }
        return switch (baseDim == null ? "" : baseDim.toUpperCase()) {
            case "EMP"  -> empIndexResultMapper.selectLatestVersionForSubject(subjectId, dataDate);
            case "ORG"  -> orgIndexResultMapper.selectLatestVersionForSubject(subjectId, dataDate);
            case "CUST" -> custIndexResultMapper.selectLatestVersionForSubject(subjectId, dataDate);
            default -> null;
        };
    }

    /**
     * 按客户编号 + 数据日期，从 CUST_INDEX_RESULT 取指定指标编号(MC_xxx)的值。
     *
     * <p>供"新建调整申请-余额概览"反显用：版本按 (custId, dataDate) 反查最近导入版本，查不到降级 V1。
     * 返回 metricCode → 值；查无数据的指标编号不出现在结果中（前端显示 '-'）。
     *
     * @param custId   客户编号
     * @param dataDate 数据日期（一般取昨日）
     * @param codes    指标编号列表（如 MC_001..MC_004）
     * @return 命中值的 metricCode → 数值映射
     */
    public Map<String, java.math.BigDecimal> loadCustIndexValues(String custId, LocalDate dataDate, List<String> codes) {
        if (custId == null || custId.isBlank() || codes == null || codes.isEmpty()) {
            return java.util.Map.of();
        }
        String version = resolveDataVersionForSubject("CUST", custId, dataDate);
        if (version == null) {
            version = "V1";
        }
        return custIndexResultMapper.selectSlotValuesByCodes(custId, codes, dataDate, version);
    }

    public Map<String, Object> loadGroovyVarsForSubject(String baseDim, String exprText,
                                                        LocalDate dataDate, String subjectId, String version) {
        List<String> refCodes = extractMetricCodesFromExpr(exprText);
        Map<String, Object> vars = new HashMap<>();
        if (refCodes.isEmpty()) {
            return vars;
        }
        String dim = baseDim == null ? "" : baseDim.toUpperCase();
        if (!dim.equals("EMP") && !dim.equals("ORG") && !dim.equals("CUST")) {
            // 维度无关/未知维度：无法定位宽表，引用指标兜底 ZERO（保证 Groovy 可跑）
            for (String code : refCodes) {
                vars.put(code, BigDecimal.ZERO);
            }
            return vars;
        }
        Map<String, Integer> slotMap = resolveSlotMap(dim, refCodes);
        vars.putAll(loadRefValuesBySlotMap(subjectId, slotMap, refCodes, dim, dataDate, version));
        return vars;
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

    /** V1.13+：正则匹配 Groovy/EXPR 公式里出现的 metric_code 字面量（形如 M_0269、M_00X、M_AUM_TOTAL）. */
    private static final java.util.regex.Pattern METRIC_CODE_PATTERN =
            java.util.regex.Pattern.compile("\\bM_[A-Za-z0-9_]+\\b");

    /**
     * V1.13+：从 expr_text 正则提取所有 M_xxx 形式的 metric_code 字面量，去重保序.
     *
     * <p>用于 ref_metric_codes 字段为空时的兜底——前端"表达式构建器"暂未把公式里的指标
     * 自动写回 def.ref_metric_codes，由后端在 calc 时主动解析。
     */
    private List<String> extractMetricCodesFromExpr(String exprText) {
        if (exprText == null || exprText.isBlank()) {
            return List.of();
        }
        java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<>();
        java.util.regex.Matcher m = METRIC_CODE_PATTERN.matcher(exprText);
        while (m.find()) {
            set.add(m.group());
        }
        return new ArrayList<>(set);
    }

    /** V1.13+：把 def.ref_metric_codes 与正则提取结果合并，保留顺序去重. */
    private List<String> mergeRefCodes(List<String> declared, List<String> extracted) {
        if ((declared == null || declared.isEmpty()) && (extracted == null || extracted.isEmpty())) {
            return List.of();
        }
        java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<>();
        if (declared != null) set.addAll(declared);
        if (extracted != null) set.addAll(extracted);
        return new ArrayList<>(set);
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
        String dim = baseDim == null ? "" : baseDim.toUpperCase();
        // 落库前先清空该数据日期+版本下本指标(slot)的旧值，再写入本轮结果，
        // 避免上一轮存在、本轮已不在结果集中的主体残留脏数据（被移除的主体值不再保留）。
        switch (dim) {
            case "EMP":
                empIndexResultMapper.clearSlot(dataDate, version, slot);
                break;
            case "ORG":
                orgIndexResultMapper.clearSlot(dataDate, version, slot);
                break;
            case "CUST":
                custIndexResultMapper.clearSlot(dataDate, version, slot);
                break;
            default:
                throw new PerfException(PerfErrorCode.METRIC_CALC_LOGIC_INVALID,
                        "未知 baseDim=" + baseDim);
        }
        for (Map.Entry<String, BigDecimal> entry : values.entrySet()) {
            String key = entry.getKey();
            BigDecimal value = entry.getValue();
            switch (dim) {
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
     * @throws PerfException slot 为 null 或不在 [1, 400]（V1.12: 200 → 400）
     */
    private void validateSlot(PerfMetricDef def) {
        Integer slot = def.getValSlot();
        if (slot == null) {
            throw new PerfException(PerfErrorCode.METRIC_CALC_LOGIC_INVALID,
                    "指标 " + def.getMetricCode() + " 未分配 val_slot");
        }
        if (slot < 1 || slot > 400) {
            throw new PerfException(PerfErrorCode.METRIC_CALC_LOGIC_INVALID,
                    "val_slot 超出 [1,400]: " + slot);
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
        // started_by NOT NULL：HTTP 请求触发拿当前用户；Quartz 调度无 ThreadLocal 上下文 → 兜底 SYSTEM
        String startedBy;
        try {
            startedBy = currentUserApi.getCurrentEmpId();
        } catch (Exception e) {
            startedBy = "SYSTEM";
        }
        task.setStartedBy(startedBy);
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

    /**
     * V1.7：失败事件发布的安全包装，避免 listener 抛异常掩盖原始业务异常.
     *
     * <p>spec § 7.5 要求 def==null 兜底（虽然 calcMetric 在 def==null 时提前抛出，
     * 但语义保守处理，防御未来代码路径变化）。
     *
     * @param metricCode  指标编码
     * @param def         指标定义（可能为 null，用 null 兜底 baseDim）
     * @param dataDate    数据日期
     * @param version     数据版本
     * @param taskId      run_task 主键
     * @param triggerType 触发类型
     */
    private void safePublishFailedEvent(String metricCode, PerfMetricDef def,
                                         LocalDate dataDate, String version,
                                         String taskId, String triggerType) {
        try {
            // spec § 7.5：def==null 兜底，baseDim 为 null（当前代码路径 def 必非 null，保守处理）
            String baseDim = (def == null) ? null : def.getBaseDim();
            eventPublisher.publishEvent(new MetricCalcCompletedEvent(
                    metricCode, baseDim, dataDate, version,
                    "FAILED", 0, 0, 0, taskId, triggerType, LocalDateTime.now()));
        } catch (Exception swallow) {
            log.error("[MetricCalc] FAILED 事件发布失败（已忽略）: taskId={}", taskId, swallow);
        }
    }
}
