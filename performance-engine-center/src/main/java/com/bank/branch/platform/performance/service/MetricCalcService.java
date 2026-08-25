package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.performance.config.PerfEngineProperties;
import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.entity.PerfRunTask;
import com.bank.branch.platform.performance.enums.MetricValueTimeEnum;
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
import com.bank.branch.platform.performance.service.engine.MetricRefTokenParser;
import com.bank.branch.platform.performance.service.engine.ObjectFilterSqlResolver;
import com.bank.branch.platform.performance.service.engine.SqlExecutor;
import com.bank.branch.platform.performance.service.engine.StatShowSqlRouter;
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
    private final StatShowSqlRouter statShowSqlRouter;
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
                             StatShowSqlRouter statShowSqlRouter,
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
        this.statShowSqlRouter = statShowSqlRouter;
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
     * 无副作用预检指标执行请求。
     *
     * <p>只读取指标定义并校验日期/统计展示表路由，不创建或更新 run_task；Facade、级联
     * 和历史回算入口应在创建任务前调用。正式执行阶段仍由 {@link #executeSqlAndPersist}
     * 再次路由，防止绕过入口直接调用计算服务时执行未改写 SQL。
     */
    public void preflightMetric(String metricCode, LocalDate dataDate) {
        statShowSqlRouter.validateRecalcDate(dataDate);
        PerfMetricDef def = loadCalcableDef(metricCode);
        if ("SQL".equalsIgnoreCase(def.getCalcLogicType())) {
            statShowSqlRouter.route(def.getSqlText(), dataDate);
        }
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
        // 无预建任务行（同步链路 / 定时调度）：本方法内部自建
        return calcMetricWithStats(metricCode, dataDate, version, triggerType, allocDate, null);
    }

    /**
     * 在 {@link #calcMetricWithStats(String, LocalDate, String, String, LocalDate)} 基础上支持
     * <b>复用外部预建的 run_task 行</b>（异步提交链路）.
     *
     * <p>异步执行时任务行必须在**请求线程**内先建好：一来 taskId 要立刻返回给前端轮询，
     * 二来 {@code started_by} 取自 {@code CurrentUserApi} 的 ThreadLocal，异步线程里拿不到会
     * 兜底成 SYSTEM，操作人就丢了。故由 {@link #createPendingTask} 先建行、本方法复用其 ID。
     *
     * @param presetTaskId 预建的 run_task 主键；为 null 时本方法自行创建（原行为）
     * @return 计算结果（runTaskId 即 presetTaskId，若传入的话）
     */
    public MetricCalcResult calcMetricWithStats(String metricCode, LocalDate dataDate, String version,
                                                String triggerType, LocalDate allocDate, String presetTaskId) {
        // 1. 定义加载与基本校验——指标不存在直接抛，不插 run_task（计划要求）
        PerfMetricDef def;
        try {
            def = loadCalcableDef(metricCode);
        } catch (RuntimeException e) {
            // 预建行已落库（异步链路）：校验不过必须就地标 FAILED，否则永远停在 PENDING，
            // 监控页看着像"在跑"，实际没有任何线程会再碰它。
            if (presetTaskId != null) {
                markFailed(presetTaskId, e);
            }
            throw e;
        }

        // 2. jobKey 推导规则：PERF_METRIC_{metricCode}（与 P1 resolveGroup 规则一致）
        String jobKey = "PERF_METRIC_" + metricCode;

        // 3. 任务 ID：复用预建行（异步链路）或就地插入 PENDING 记录（先插入再切状态，便于 FAILED 场景可见）
        String taskId = presetTaskId != null ? presetTaskId
                : createPendingTask(metricCode, dataDate, version, triggerType);

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
        // 正式批量执行没有单主体上下文；受控 EMP_ID 条件由 resolver 在执行前移除。
        String objectId = null;
        params.put("objectId", objectId);
        Duration timeout = Duration.ofSeconds(perfEngineProperties == null
                ? 30 : Math.max(1, perfEngineProperties.getSqlTimeoutSeconds()));
        // 路由只产生新的 SQL 字符串，不修改指标定义中保存的原始 SQL。
        String routedSql = statShowSqlRouter.route(def.getSqlText(), dataDate);
        String resolvedSql = ObjectFilterSqlResolver.resolve(routedSql, objectId);
        Map<String, BigDecimal> values = sqlExecutor.execute(resolvedSql, params, timeout);
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

        // V1.13+ 取值时间：token 承载取值时间，按 baseCode 预查 slot、按 token 绑定变量
        List<MetricRefTokenParser.RefToken> refTokens =
                new ArrayList<>(MetricRefTokenParser.parse(def.getExprText()));
        // 声明式 ref_metric_codes 里的裸编码按今日补齐（去重）
        for (String declared : parseRefMetricCodes(def.getRefMetricCodes())) {
            boolean exists = refTokens.stream().anyMatch(rt -> rt.token().equals(declared));
            if (!exists) {
                refTokens.add(new MetricRefTokenParser.RefToken(declared, declared, MetricValueTimeEnum.TODAY));
            }
        }
        List<String> baseCodes = refTokens.stream()
                .map(MetricRefTokenParser.RefToken::baseCode).distinct().toList();
        Duration timeout = Duration.ofSeconds(perfEngineProperties == null
                ? 30 : Math.max(1, perfEngineProperties.getSqlTimeoutSeconds()));

        // V1.7 N+1 优化：预查 baseCode → val_slot 映射 1 次，避免 N 主体 × N 次重查
        Map<String, Integer> slotMap = baseCodes.isEmpty() ? Map.of()
                : resolveSlotMap(def.getBaseDim(), baseCodes);

        int success = 0;
        int failed = 0;
        List<String> failedSamples = new ArrayList<>();
        Map<String, BigDecimal> outputs = new HashMap<>();

        for (String subject : subjects) {
            try {
                // 按 RefToken 逐个解析取值时间目标日期加载引用指标值（避免每主体重查 slotMap）
                Map<String, Object> vars = loadRefValuesByTokens(subject, refTokens, slotMap,
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
     * V1.13+：按 RefToken 逐个解析目标日期，从宽表取值，以完整 token 为变量名绑定.
     *
     * <p>目标日期 = timePoint.resolve(dataDate)（复用 DateMacroResolver）；缺 slot / 查无值 → ZERO。
     *
     * @param subject   主体 ID（员工工号/机构编码/客户 ID）
     * @param refTokens 引用 token 列表（含 baseCode 与取值时间）
     * @param slotMap   baseCode -&gt; val_slot 预查结果
     * @param baseDim   基础维度（EMP/ORG/CUST）
     * @param dataDate  数据日期
     * @param version   数据版本（一律用本轮 version，不做历史版本反查）
     * @return 完整 token -&gt; 指标值 映射（作为 Groovy vars）
     */
    private Map<String, Object> loadRefValuesByTokens(String subject,
                                                      List<MetricRefTokenParser.RefToken> refTokens,
                                                      Map<String, Integer> slotMap,
                                                      String baseDim, LocalDate dataDate, String version) {
        if (refTokens == null || refTokens.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> result = new HashMap<>();
        for (MetricRefTokenParser.RefToken rt : refTokens) {
            Integer slot = slotMap.get(rt.baseCode());
            BigDecimal value = null;
            if (slot != null) {
                LocalDate targetDate = rt.timePoint().resolve(dataDate);
                value = switch (baseDim == null ? "" : baseDim.toUpperCase()) {
                    case "EMP"  -> empIndexResultMapper.selectValBySlot(subject, slot, targetDate, version);
                    case "ORG"  -> orgIndexResultMapper.selectValBySlot(subject, slot, targetDate, version);
                    case "CUST" -> custIndexResultMapper.selectValBySlot(subject, slot, targetDate, version);
                    default -> throw new PerfException(PerfErrorCode.METRIC_CALC_LOGIC_INVALID,
                            "未知 baseDim=" + baseDim);
                };
            }
            result.put(rt.token(), value != null ? value : BigDecimal.ZERO);
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
        // V1.13+ 取值时间：与真实计算共用 token 口径（baseCode 预查 slot，token 绑变量、按取值时间取历史日期）
        List<MetricRefTokenParser.RefToken> refTokens = MetricRefTokenParser.parse(exprText);
        if (refTokens.isEmpty()) {
            return new HashMap<>();
        }
        String dim = baseDim == null ? "" : baseDim.toUpperCase();
        if (!dim.equals("EMP") && !dim.equals("ORG") && !dim.equals("CUST")) {
            // 维度无关/未知维度：无法定位宽表，引用指标兜底 ZERO（保证 Groovy 可跑）
            Map<String, Object> vars = new HashMap<>();
            for (MetricRefTokenParser.RefToken rt : refTokens) {
                vars.put(rt.token(), BigDecimal.ZERO);
            }
            return vars;
        }
        List<String> baseCodes = refTokens.stream()
                .map(MetricRefTokenParser.RefToken::baseCode).distinct().toList();
        Map<String, Integer> slotMap = resolveSlotMap(dim, baseCodes);
        return loadRefValuesByTokens(subjectId, refTokens, slotMap, dim, dataDate, version);
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

    /**
     * 加载可计算的指标定义并做基本校验（不存在 / 已软删 / 维度无关型一律拒绝）.
     *
     * @param metricCode 指标编码
     * @return 指标定义
     * @throws PerfException METRIC_NOT_FOUND / METRIC_CALC_LOGIC_INVALID
     */
    private PerfMetricDef loadCalcableDef(String metricCode) {
        PerfMetricDef def = metricDefService.getByCodeOrNull(metricCode);
        if (def == null || (def.getDeleted() != null && def.getDeleted() == 1)) {
            throw new PerfException(PerfErrorCode.METRIC_NOT_FOUND, metricCode);
        }
        // V1.9：维度无关型指标（baseDim=null）无 slot、无宽表归属，不允许进入计算路径
        if (def.getBaseDim() == null || def.getBaseDim().isBlank()) {
            throw new PerfException(PerfErrorCode.METRIC_CALC_LOGIC_INVALID,
                    "维度无关型指标不支持自动/手动计算: " + metricCode + "（base_dim 为空）");
        }
        return def;
    }

    /**
     * 预建 PENDING 的 run_task 行并返回其 ID，供异步提交链路在<b>请求线程</b>内调用.
     *
     * <p>必须在请求线程调用的两个理由：taskId 要立刻返回给前端轮询；{@code started_by} 取自
     * {@code CurrentUserApi} 的 ThreadLocal，异步线程里拿不到会兜底成 SYSTEM，操作人就丢了。
     *
     * @param metricCode  指标编码
     * @param dataDate    数据日期
     * @param version     数据版本
     * @param triggerType 触发类型（SCHEDULED/MANUAL/RECALC）
     * @return 新建 run_task 主键 ID
     */
    public String createPendingTask(String metricCode, LocalDate dataDate, String version, String triggerType) {
        String taskId = UUID.randomUUID().toString().replace("-", "");
        insertPendingTask(taskId, metricCode, dataDate, version, triggerType);
        return taskId;
    }

    private void insertPendingTask(String taskId, String metricCode,
                                   LocalDate dataDate, String version, String triggerType) {
        PerfRunTask task = new PerfRunTask();
        task.setId(taskId);
        task.setTaskType("METRIC_RUN");
        task.setTriggerType(triggerType);
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
