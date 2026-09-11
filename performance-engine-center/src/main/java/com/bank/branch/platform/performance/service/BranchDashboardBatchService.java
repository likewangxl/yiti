package com.bank.branch.platform.performance.service;

import com.bank.branch.platform.auth.api.OrgGroupApi;
import com.bank.branch.platform.customer.api.MarketingOrgSnapshotQueryApi;
import com.bank.branch.platform.customer.api.dto.MarketingOrgSnapshotDTO;
import com.bank.branch.platform.performance.api.TargetApi;
import com.bank.branch.platform.performance.api.dto.BranchDashboardBatchDTO;
import com.bank.branch.platform.performance.api.dto.BranchDashboardBatchAttemptDTO;
import com.bank.branch.platform.performance.api.dto.BranchDashboardBatchRowDTO;
import com.bank.branch.platform.performance.api.dto.BranchDashboardMetricContractDTO;
import com.bank.branch.platform.performance.api.dto.BranchDashboardQualityDTO;
import com.bank.branch.platform.performance.api.dto.BranchDashboardSourceAsOfDTO;
import com.bank.branch.platform.performance.api.dto.BranchDashboardHistoryCoverageDTO;
import com.bank.branch.platform.performance.api.dto.TargetPlanDTO;
import com.bank.branch.platform.performance.api.dto.TargetValueDTO;
import com.bank.branch.platform.performance.config.BranchDashboardBatchProperties;
import com.bank.branch.platform.performance.entity.PerfMetricDef;
import com.bank.branch.platform.performance.entity.PerfRunTask;
import com.bank.branch.platform.performance.entity.SysControl;
import com.bank.branch.platform.performance.mapper.OrgIndexResultMapper;
import com.bank.branch.platform.performance.mapper.OrgMetricValueRow;
import com.bank.branch.platform.performance.mapper.PerfRunTaskMapper;
import com.bank.branch.platform.performance.service.dto.RunTaskQuery;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 分行经营大屏批次编排服务。
 *
 * <p>服务先完整读取并校验所有来源，再一次性写入五个专用指标槽位；批次输出序列化到
 * {@code PERF_RUN_TASK.result_preview_json}，查询方只读取该不可变快照。财务源来自本域
 * ORG 宽表，客户源和机构组分别通过公开 QueryApi 读取。</p>
 */
@Slf4j
@Service
public class BranchDashboardBatchService {

    /** PERF_RUN_TASK 专用任务类型。 */
    public static final String TASK_TYPE = "BRANCH_DASHBOARD_BATCH";

    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_COMPLETE = "COMPLETE";
    private static final String STATUS_FAILED = "FAILED";
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final int MAX_CANDIDATE_DAYS = 400;
    private static final int PERCENT_SCALE = 4;

    private final OrgGroupApi orgGroupApi;
    private final MarketingOrgSnapshotQueryApi marketingApi;
    private final TargetApi targetApi;
    private final MetricDefService metricDefService;
    private final SysControlService sysControlService;
    private final OrgIndexResultMapper orgIndexResultMapper;
    private final PerfRunTaskMapper runTaskMapper;
    private final BranchDashboardBatchProperties properties;
    private final ObjectMapper objectMapper;
    private final BranchDashboardBatchTaskStore taskStore;
    private final BranchDashboardBatchReadTransaction readTransaction;

    /** Spring 构造入口；taskStore 为批次状态与槽位写入提供独立事务边界。 */
    @Autowired
    public BranchDashboardBatchService(OrgGroupApi orgGroupApi,
                                       MarketingOrgSnapshotQueryApi marketingApi,
                                       TargetApi targetApi,
                                       MetricDefService metricDefService,
                                       SysControlService sysControlService,
                                       OrgIndexResultMapper orgIndexResultMapper,
                                       PerfRunTaskMapper runTaskMapper,
                                       BranchDashboardBatchProperties properties,
                                       ObjectMapper objectMapper,
                                       BranchDashboardBatchTaskStore taskStore,
                                       BranchDashboardBatchReadTransaction readTransaction) {
        this.orgGroupApi = orgGroupApi;
        this.marketingApi = marketingApi;
        this.targetApi = targetApi;
        this.metricDefService = metricDefService;
        this.sysControlService = sysControlService;
        this.orgIndexResultMapper = orgIndexResultMapper;
        this.runTaskMapper = runTaskMapper;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.taskStore = taskStore;
        this.readTransaction = readTransaction;
    }

    /** 保留带 taskStore 的旧测试/组合装配签名；Spring 主路径使用上方代理边界构造器。 */
    public BranchDashboardBatchService(OrgGroupApi orgGroupApi,
                                       MarketingOrgSnapshotQueryApi marketingApi,
                                       TargetApi targetApi,
                                       MetricDefService metricDefService,
                                       SysControlService sysControlService,
                                       OrgIndexResultMapper orgIndexResultMapper,
                                       PerfRunTaskMapper runTaskMapper,
                                       BranchDashboardBatchProperties properties,
                                       ObjectMapper objectMapper,
                                       BranchDashboardBatchTaskStore taskStore) {
        this(orgGroupApi, marketingApi, targetApi, metricDefService, sysControlService,
                orgIndexResultMapper, runTaskMapper, properties, objectMapper, taskStore,
                new BranchDashboardBatchReadTransaction());
    }

    /**
     * 单元测试兼容构造入口；生产 Spring 使用带 taskStore 的构造器。
     * taskStore 在单测中直接委托同一 Mapper，不改变测试的可观察写入。
     */
    public BranchDashboardBatchService(OrgGroupApi orgGroupApi,
                                       MarketingOrgSnapshotQueryApi marketingApi,
                                       TargetApi targetApi,
                                       MetricDefService metricDefService,
                                       SysControlService sysControlService,
                                       OrgIndexResultMapper orgIndexResultMapper,
                                       PerfRunTaskMapper runTaskMapper,
                                       BranchDashboardBatchProperties properties,
                                       ObjectMapper objectMapper) {
        this(orgGroupApi, marketingApi, targetApi, metricDefService, sysControlService,
                orgIndexResultMapper, runTaskMapper, properties, objectMapper,
                new BranchDashboardBatchTaskStore(runTaskMapper, orgIndexResultMapper, objectMapper),
                new BranchDashboardBatchReadTransaction());
    }

    /**
     * 手工或定时运行一批分行大屏数据。
     *
     * @param requestedDate 指定业务日；为空时由服务选择最近完整日
     * @param triggerType   MANUAL 或 SCHEDULED/AUTO
     * @param operatorEmpId 发起人；自动触发可为空
     * @return 成功或失败的批次摘要，失败也已留下 FAILED run_task
     */
    public BranchDashboardBatchDTO runBatch(LocalDate requestedDate, String triggerType,
                                            String operatorEmpId) {
        String batchId = newBatchId();
        LocalDateTime startedAt = LocalDateTime.now(SHANGHAI);
        Map<String, Object> initialParams = new LinkedHashMap<>();
        initialParams.put("dataClassification", safe(properties.getDataClassification()));
        initialParams.put("groupCode", safe(properties.getGroupCode()));
        initialParams.put("requestedDate", requestedDate == null ? null : requestedDate.toString());
        initialParams.put("triggerType", safe(triggerType));
        String taskParams = jsonOrFallback(initialParams);

        PerfRunTask task = new PerfRunTask();
        task.setId(batchId);
        task.setTaskType(TASK_TYPE);
        task.setTriggerType(StringUtils.hasText(triggerType) ? triggerType : "AUTO");
        task.setTaskKey(properties.getGroupCode());
        task.setDataDate(requestedDate);
        task.setParamsJson(taskParams);
        task.setStatus(STATUS_RUNNING);
        task.setStartedBy(StringUtils.hasText(operatorEmpId) ? operatorEmpId : "SYSTEM_BRANCH_DASHBOARD");
        task.setStartTime(startedAt);
        taskStore.start(task);

        Calculation calculation = null;
        BatchInputContext inputContext = new BatchInputContext();
        try {
            calculation = readTransaction.inSnapshot(() -> calculate(requestedDate, inputContext));
            BranchDashboardBatchDTO snapshot = calculation.snapshot();
            snapshot.setBatchId(batchId);
            snapshot.setCalculatedAt(LocalDateTime.now(SHANGHAI));

            // 只读快照结束后再次确认控制版本和组成员没有变化，避免把过期输入写成 SUCCESS。
            ensureStableInputs(calculation);

            String resultJson = jsonRequired(snapshot);
            String paramsJson = jsonRequired(calculation.params());
            // taskStore 在一个事务内写五个专用槽位、不可变 JSON 和 SUCCESS；任一步失败整体回滚。
            taskStore.commitSuccess(batchId, snapshot, calculation.outputDefinitions(),
                    calculation.outputCodes(), calculation.version(), resultJson, paramsJson);
            return snapshot;
        } catch (Exception ex) {
            String message = errorMessage(ex);
            log.warn("[BranchDashboardBatchService] 批次失败 batchId={}, groupCode={}, reason={}",
                    batchId, properties.getGroupCode(), message);
            Map<String, Object> failureParams = new LinkedHashMap<>(initialParams);
            failureParams.put("dataDate", inputContext.dataDate);
            failureParams.put("version", inputContext.version);
            failureParams.put("memberOrgCodes", inputContext.memberOrgCodes);
            failureParams.put("error", message);
            // FAILED 记录必须在独立事务中落地，不能被计算/槽位事务回滚吞掉。
            taskStore.markFailed(batchId, message, jsonOrFallback(failureParams),
                    calculation == null ? inputContext.dataDate : calculation.snapshot().getDataDate(),
                    calculation == null ? inputContext.version : calculation.version());
            LocalDate failedDate = calculation == null ? inputContext.dataDate
                    : calculation.snapshot().getDataDate();
            return failedSnapshot(batchId, failedDate != null ? failedDate : requestedDate,
                    calculation == null ? inputContext.version : calculation.version(),
                    startedAt, message);
        }
    }

    /** 使用配置的组编码运行，供 Quartz 包装类和手工入口复用。 */
    public BranchDashboardBatchDTO runBatch(String groupCode, LocalDate requestedDate,
                                            String triggerType, String operatorEmpId) {
        if (!StringUtils.hasText(groupCode) || groupCode.equals(properties.getGroupCode())) {
            return runBatch(requestedDate, triggerType, operatorEmpId);
        }
        throw new IllegalArgumentException("批次服务只允许配置的 groupCode: " + properties.getGroupCode());
    }

    /** 读取指定批次的原始不可变快照；查询授权过滤在 BranchDashboardBatchQueryApiImpl 执行。 */
    public Optional<BranchDashboardBatchDTO> readSnapshot(PerfRunTask task) {
        if (task == null || !TASK_TYPE.equals(task.getTaskType())
                || !STATUS_SUCCESS.equals(task.getStatus())
                || !StringUtils.hasText(task.getResultPreviewJson())) {
            return Optional.empty();
        }
        try {
            BranchDashboardBatchDTO dto = objectMapper.readValue(
                    task.getResultPreviewJson(), BranchDashboardBatchDTO.class);
            if (!StringUtils.hasText(dto.getBatchId())) {
                dto.setBatchId(task.getId());
            }
            return Optional.of(dto);
        } catch (Exception ex) {
            log.warn("[BranchDashboardBatchService] 批次快照解析失败 batchId={}, reason={}",
                    task.getId(), errorMessage(ex));
            return Optional.empty();
        }
    }

    /** 当前组的最新成功批次；按 start_time/id 的 Mapper 排序返回第一条。 */
    public Optional<PerfRunTask> findLatestSuccess(String groupCode) {
        if (!StringUtils.hasText(groupCode)) {
            return Optional.empty();
        }
        List<PerfRunTask> tasks = runTaskMapper.selectByCondition(
                new RunTaskQuery(TASK_TYPE, null, groupCode, STATUS_SUCCESS,
                        null, null, null, null), null, 0, 1);
        return tasks == null || tasks.isEmpty() ? Optional.empty() : Optional.ofNullable(tasks.get(0));
    }

    /** 返回该组最近一次尝试的脱敏元数据，不把 PERF_RUN_TASK 暴露给跨模块调用方。 */
    public Optional<BranchDashboardBatchAttemptDTO> findLatestAttempt(String groupCode) {
        if (!StringUtils.hasText(groupCode)) {
            return Optional.empty();
        }
        List<PerfRunTask> tasks = runTaskMapper.selectByCondition(
                new RunTaskQuery(TASK_TYPE, null, groupCode, null,
                        null, null, null, null), null, 0, 1);
        if (tasks == null || tasks.isEmpty() || tasks.get(0) == null) {
            return Optional.empty();
        }
        PerfRunTask task = tasks.get(0);
        String status = STATUS_SUCCESS.equals(task.getStatus()) ? STATUS_COMPLETE : task.getStatus();
        return Optional.of(BranchDashboardBatchAttemptDTO.builder()
                .attemptId(task.getId())
                .status(status)
                .dataDate(task.getDataDate())
                .startedAt(task.getStartTime())
                .message(attemptMessage(task))
                .build());
    }

    /** 按批次 ID 查询 task；调用方继续通过 readSnapshot 校验任务类型和状态。 */
    public Optional<PerfRunTask> findTask(String batchId) {
        return Optional.ofNullable(runTaskMapper.selectById(batchId));
    }

    private Calculation calculate(LocalDate requestedDate, BatchInputContext inputContext) {
        ensureEnabled();
        String groupCode = requireText(properties.getGroupCode(), "groupCode");
        List<String> members = normalizeMembers(orgGroupApi.listActiveMemberCodes(groupCode));
        if (members.isEmpty()) {
            throw new IllegalStateException("机构组没有有效成员: " + groupCode);
        }
        inputContext.memberOrgCodes = members;

        SysControl control = sysControlService.getCurrentVersion("ORG");
        String version = requireText(control == null ? null : control.getCurrentVersion(), "ORG current version");
        inputContext.version = version;
        LocalDate today = LocalDate.now(SHANGHAI);
        LocalDate dateTo = requestedDate == null ? today : requestedDate;
        if (dateTo.isAfter(today)) {
            throw new IllegalArgumentException("dataDate 不能晚于当前日期");
        }

        LinkedHashSet<String> requiredCodes = new LinkedHashSet<>();
        requiredCodes.addAll(normalizeCodes(properties.getUpstreamMetricCodes()));
        requiredCodes.add(requireText(properties.getActualMetricCode(), "actualMetricCode"));
        requiredCodes.add(requireText(properties.getCustomerMetricCode(), "customerMetricCode"));
        requiredCodes.add(requireText(properties.getAttentionMetricCode(), "attentionMetricCode"));
        requiredCodes.add(requireText(properties.getTargetMetricCode(), "targetMetricCode"));
        requiredCodes.add(requireText(properties.getOrgRateMetricCode(), "orgRateMetricCode"));
        requiredCodes.add(requireText(properties.getGroupContributionMetricCode(), "groupContributionMetricCode"));
        DefinitionValidation validation = validateDefinitions(requiredCodes);
        Map<String, PerfMetricDef> definitions = validation.definitions();

        List<LocalDate> candidates = orgIndexResultMapper.selectDistinctDataDatesBefore(
                version, dateTo, MAX_CANDIDATE_DAYS);
        if (candidates == null) {
            candidates = List.of();
        }
        List<String> upstreamCodes = normalizeCodes(properties.getUpstreamMetricCodes());
        if (upstreamCodes.isEmpty()) {
            upstreamCodes = List.of(requireText(properties.getActualMetricCode(), "actualMetricCode"));
        }
        LocalDate latestDate = null;
        Map<String, Map<String, BigDecimal>> latestFinancial = null;
        // quality.expected/received 采用最终快照的11项指标口径；历史金融输入的
        // members×upstreamCodes 覆盖单独写入 historyCoverage，不能让页面把6项源指标
        // 误当成最终11列的完整性。
        QualityAccumulator quality = new QualityAccumulator(
                members.size() * requiredCodes.size(), members.size(), members);
        quality.missing.addAll(validation.metadataGaps());
        for (LocalDate candidate : candidates.stream().filter(Objects::nonNull).distinct()
                .sorted(Comparator.reverseOrder()).toList()) {
            QualityAccumulator candidateQuality = new QualityAccumulator(
                    members.size() * upstreamCodes.size(), members.size(), members);
            Map<String, Map<String, BigDecimal>> values = readFinancial(candidate, version, members,
                    upstreamCodes, definitions, candidateQuality, true);
            if (isComplete(values, members, upstreamCodes)) {
                latestDate = candidate;
                latestFinancial = values;
                quality.missing.addAll(candidateQuality.missing);
                // 选中的金融日完整后，客户、关注、目标及两种率也必须已经完成计算；
                // 这些列在下面逐机构校验并写入，因此成功快照应为最终指标全量覆盖。
                quality.received = members.size() * requiredCodes.size();
                quality.receivedSubjects = members.size();
                quality.selectedComplete = true;
                break;
            } else {
                String reason = candidateQuality.missing.isEmpty()
                        ? "INCOMPLETE" : String.join(",", candidateQuality.missing);
                quality.newerIncomplete.add(candidate + ":" + reason);
            }
        }
        if (latestDate == null || latestFinancial == null) {
            throw new IllegalStateException("没有找到组成员和必需金融指标均完整的业务日");
        }
        inputContext.dataDate = latestDate;

        HistoryResult history = loadHistoryRows(
                latestDate, version, members, upstreamCodes, definitions, candidates);

        TargetPlanDTO plan = targetApi.getTargetPlan(
                requireText(properties.getTargetPlanCode(), "targetPlanCode"))
                .orElseThrow(() -> new IllegalStateException("目标方案不存在"));
        validatePlan(plan, latestDate);
        String cycleKey = cycleKey(plan.getTargetCycle(), latestDate);
        Map<String, BigDecimal> targets = loadTargets(plan, members, cycleKey, latestDate);

        List<MarketingOrgSnapshotDTO> marketingRows = marketingApi.batchQueryOrgSnapshots(members, latestDate);
        Map<String, MarketingOrgSnapshotDTO> marketingByOrg = normalizeMarketing(members, marketingRows);

        BigDecimal targetTotal = targets.values().stream()
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (targetTotal.signum() <= 0) {
            throw new IllegalStateException("组目标总额必须大于 0");
        }

        List<BranchDashboardBatchRowDTO> rows = new ArrayList<>(members.size());
        LocalDate marketingAsOf = null;
        boolean mixedPeriod = false;
        for (String orgCode : members) {
            MarketingOrgSnapshotDTO marketing = marketingByOrg.get(orgCode);
            Map<String, BigDecimal> values = new LinkedHashMap<>(latestFinancial.get(orgCode));
            BigDecimal actual = values.get(properties.getActualMetricCode());
            BigDecimal target = targets.get(orgCode);
            if (actual == null || target == null || target.signum() <= 0) {
                throw new IllegalStateException("机构实际值或目标值无效: " + orgCode);
            }
            BigDecimal customer = nonNegative(marketing.getValidCustomerCount(),
                    properties.getCustomerMetricCode(), orgCode);
            BigDecimal attention = nonNegative(marketing.getPendingFollowUpTaskCount(),
                    properties.getAttentionMetricCode(), orgCode);
            values.put(properties.getCustomerMetricCode(), customer);
            values.put(properties.getAttentionMetricCode(), attention);
            values.put(properties.getTargetMetricCode(), target);
            values.put(properties.getOrgRateMetricCode(), percentage(actual, target,
                    definitions.get(properties.getOrgRateMetricCode())));
            values.put(properties.getGroupContributionMetricCode(), percentage(actual, targetTotal,
                    definitions.get(properties.getGroupContributionMetricCode())));
            rows.add(BranchDashboardBatchRowDTO.builder()
                    .orgCode(orgCode)
                    .dataDate(latestDate)
                    .metricValues(values)
                    .build());

            LocalDate sourceDate = marketing.getSourceAsOfDate();
            if (sourceDate != null) {
                marketingAsOf = marketingAsOf == null || sourceDate.isAfter(marketingAsOf)
                        ? sourceDate : marketingAsOf;
                mixedPeriod |= !latestDate.equals(sourceDate);
            }
        }

        // targetAsOf 表示实际采用的目标生效日；采集时刻单独记录，避免把后续新目标
        // 误显示成与金融业务日同一时点。
        LocalDate targetAsOf = plan.getEffectiveDate() != null
                ? plan.getEffectiveDate() : latestDate;
        LocalDate targetEffectiveDate = plan.getEffectiveDate();
        LocalDate revenueAsOf = latestDate;
        LocalDateTime collectedAt = LocalDateTime.now(SHANGHAI);
        BranchDashboardSourceAsOfDTO sourceAsOf = BranchDashboardSourceAsOfDTO.builder()
                .financial(latestDate)
                .marketing(marketingAsOf)
                .target(targetAsOf)
                .revenue(revenueAsOf)
                .targetEffectiveDate(targetEffectiveDate)
                .financialCollectedAt(collectedAt)
                .marketingCollectedAt(collectedAt)
                .targetCollectedAt(collectedAt)
                .revenueCollectedAt(collectedAt)
                .build();
        quality.setMixedPeriod(mixedPeriod);
        quality.receivedSubjects = members.size();

        Map<String, BranchDashboardMetricContractDTO> contracts = validation.contracts();
        String definitionDigest = definitionDigest(contracts);
        Map<String, String> sourceModes = new LinkedHashMap<>();
        sourceModes.put("financial", "ORG_INDEX_RESULT");
        sourceModes.put("marketing", marketingByOrg.values().stream()
                .map(MarketingOrgSnapshotDTO::getSourceMode)
                .filter(StringUtils::hasText).findFirst().orElse("CURRENT_STATE"));
        sourceModes.put("target", "TARGET_API");
        sourceModes.put("revenue", isProduction() ? "ORG_INDEX_RESULT" : "TEST_MANUAL");

        BranchDashboardBatchDTO snapshot = BranchDashboardBatchDTO.builder()
                .groupCode(groupCode)
                .dataDate(latestDate)
                .version(version)
                // 对外快照状态表达“计算完成”；PERF_RUN_TASK 仍以 SUCCESS 作为持久化终态。
                .status(STATUS_COMPLETE)
                .dataClassification(classification())
                .memberOrgCodes(members)
                .sourceAsOf(sourceAsOf)
                .quality(quality.toDto())
                .metricContracts(contracts)
                .definitionDigest(definitionDigest)
                .sourceModes(sourceModes)
                .rows(rows)
                .historyRows(history.rows())
                .historyCoverage(history.coverage())
                .build();

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("dataClassification", properties.getDataClassification());
        params.put("groupCode", groupCode);
        params.put("memberOrgCodes", members);
        params.put("dataDate", latestDate);
        params.put("version", version);
        params.put("upstreamMetricCodes", upstreamCodes);
        params.put("sourceAsOf", sourceAsOf);
        params.put("quality", quality.toDto());
        params.put("metricContracts", contracts);
        params.put("definitionDigest", definitionDigest);
        params.put("sourceModes", sourceModes);
        params.put("historyCoverage", history.coverage());
        params.put("targetPlanCode", plan.getPlanCode());
        params.put("targetCycleKey", cycleKey);
        List<String> outputCodes = List.of(properties.getCustomerMetricCode(),
                properties.getAttentionMetricCode(), properties.getTargetMetricCode(),
                properties.getOrgRateMetricCode(), properties.getGroupContributionMetricCode());
        return new Calculation(snapshot, definitions, outputCodes, version, params);
    }

    private void ensureStableInputs(Calculation calculation) {
        SysControl currentControl = sysControlService.getCurrentVersion("ORG");
        String currentVersion = currentControl == null ? null : currentControl.getCurrentVersion();
        if (!Objects.equals(calculation.version(), currentVersion)) {
            throw new IllegalStateException("ORG current version 在批次写入前发生变化");
        }
        List<String> currentMembers = normalizeMembers(
                orgGroupApi.listActiveMemberCodes(calculation.snapshot().getGroupCode()));
        if (!currentMembers.equals(normalizeMembers(calculation.snapshot().getMemberOrgCodes()))) {
            throw new IllegalStateException("机构组成员在批次写入前发生变化");
        }
    }

    private DefinitionValidation validateDefinitions(Collection<String> requiredCodes) {
        List<PerfMetricDef> defs = metricDefService.getByCodes(new ArrayList<>(requiredCodes));
        Map<String, PerfMetricDef> byCode = defs == null ? new LinkedHashMap<>() : defs.stream()
                .filter(Objects::nonNull)
                .filter(d -> StringUtils.hasText(d.getMetricCode()))
                .collect(Collectors.toMap(PerfMetricDef::getMetricCode, d -> d,
                        (left, right) -> left, LinkedHashMap::new));
        List<String> metadataGaps = new ArrayList<>();
        Map<Integer, String> usedSlots = new HashMap<>();
        for (String code : requiredCodes) {
            PerfMetricDef def = byCode.get(code);
            if (def == null) {
                throw new IllegalStateException("指标定义不存在: " + code);
            }
            if (isProduction() && (isDemoSourceCode(def.getSqlText())
                    || isDemoSourceCode(def.getExprText()))) {
                throw new IllegalStateException("PROD 指标来源包含测试/演示逻辑: " + code);
            }
            if (!"ORG".equalsIgnoreCase(def.getBaseDim())) {
                throw new IllegalStateException("指标维度必须为 ORG: " + code);
            }
            if (def.getValSlot() == null || def.getValSlot() < 1 || def.getValSlot() > 400) {
                throw new IllegalStateException("指标槽位必须在 1..400: " + code);
            }
            String duplicate = usedSlots.putIfAbsent(def.getValSlot(), code);
            if (duplicate != null && !duplicate.equals(code)) {
                throw new IllegalStateException("指标槽位重复: " + duplicate + " / " + code);
            }
            if (!"ACTIVE".equalsIgnoreCase(def.getStatus())
                    && !"PUBLISHED".equalsIgnoreCase(def.getStatus())) {
                throw new IllegalStateException("指标定义必须为 ACTIVE/PUBLISHED: " + code);
            }
            if (!StringUtils.hasText(def.getMetricDesc()) && !StringUtils.hasText(def.getDescription())) {
                if (isProduction()) {
                    throw new IllegalStateException("生产指标缺少 description: " + code);
                }
                metadataGaps.add("DESCRIPTION:" + code);
            }
            if (def.getDecimalPlaces() == null) {
                if (isProduction()) {
                    throw new IllegalStateException("生产指标缺少 decimalPlaces: " + code);
                }
                metadataGaps.add("DECIMAL_PLACES:" + code);
            }
            if (!StringUtils.hasText(def.getUnit())) {
                if (isProduction()) {
                    throw new IllegalStateException("生产指标缺少 unit: " + code);
                }
                String expected = expectedUnit(code);
                if (!StringUtils.hasText(expected)) {
                    throw new IllegalStateException("TEST 指标缺少 expectedUnits: " + code);
                }
                metadataGaps.add("UNIT:" + code + " expected=" + expected);
            } else {
                String expected = expectedUnit(code);
                if (StringUtils.hasText(expected) && !expected.equals(def.getUnit())) {
                    throw new IllegalStateException("指标单位与 expectedUnits 不一致: " + code);
                }
            }
        }
        Map<String, BranchDashboardMetricContractDTO> contracts = new LinkedHashMap<>();
        for (String code : requiredCodes) {
            PerfMetricDef d = byCode.get(code);
            contracts.put(code, BranchDashboardMetricContractDTO.builder()
                    .metricCode(code)
                    .metricName(d.getMetricName())
                    .unit(StringUtils.hasText(d.getUnit()) ? d.getUnit() : expectedUnit(code))
                    .decimalPlaces(d.getDecimalPlaces())
                    .baseDim(d.getBaseDim())
                    .valSlot(d.getValSlot())
                    .metricCategory(d.getMetricCategory())
                    .description(d.getDescription())
                    .metricDesc(d.getMetricDesc())
                    .status(d.getStatus())
                    .numeratorMetricCode(isRate(code) ? properties.getActualMetricCode() : null)
                    .denominatorMetricCode(isRate(code) ? properties.getTargetMetricCode() : null)
                    .build());
        }
        return new DefinitionValidation(byCode, metadataGaps, contracts);
    }

    private Map<String, Map<String, BigDecimal>> readFinancial(LocalDate date, String version,
                                                                List<String> members, List<String> codes,
                                                                Map<String, PerfMetricDef> definitions,
                                                                QualityAccumulator quality,
                                                                boolean collectMissing) {
        Map<String, Map<String, BigDecimal>> result = new LinkedHashMap<>();
        for (String code : codes) {
            PerfMetricDef def = definitions.get(code);
            List<OrgMetricValueRow> rows = orgIndexResultMapper.selectSlotValuesByOrgs(
                    members, date, version, def.getValSlot());
            if (rows == null) {
                rows = List.of();
            }
            for (OrgMetricValueRow row : rows) {
                if (row == null || !members.contains(row.getOrgCode()) || row.getMetricValue() == null) {
                    continue;
                }
                result.computeIfAbsent(row.getOrgCode(), ignored -> new LinkedHashMap<>())
                        .put(code, row.getMetricValue());
            }
        }
        if (collectMissing) {
            for (String org : members) {
                for (String code : codes) {
                    if (result.getOrDefault(org, Map.of()).get(code) == null) {
                        quality.missing.add(org + ":" + code);
                    }
                }
            }
        }
        return result;
    }

    private HistoryResult loadHistoryRows(LocalDate latestDate, String version,
                                          List<String> members, List<String> codes,
                                          Map<String, PerfMetricDef> definitions,
                                          List<LocalDate> candidates) {
        int days = Math.max(1, properties.getHistoryDays());
        LocalDate from = latestDate.minusDays(days - 1L);
        List<BranchDashboardBatchRowDTO> rows = new ArrayList<>();
        List<BranchDashboardHistoryCoverageDTO> coverage = new ArrayList<>();
        for (LocalDate date : candidates.stream().filter(Objects::nonNull).distinct()
                .filter(d -> !d.isAfter(latestDate) && !d.isBefore(from))
                .sorted().toList()) {
            Map<String, Map<String, BigDecimal>> values = readFinancial(
                    date, version, members, codes, definitions, new QualityAccumulator(0, 0, members), false);
            List<String> missing = new ArrayList<>();
            List<String> missingSubjects = new ArrayList<>();
            int received = 0;
            int receivedSubjects = 0;
            for (String org : members) {
                Map<String, BigDecimal> orgValues = values.get(org);
                boolean subjectComplete = true;
                for (String code : codes) {
                    if (orgValues == null || orgValues.get(code) == null) {
                        subjectComplete = false;
                        missing.add(org + ":" + code);
                    } else {
                        received++;
                    }
                }
                if (subjectComplete) {
                    receivedSubjects++;
                } else {
                    missingSubjects.add(org);
                }
                if (orgValues != null && !orgValues.isEmpty()) {
                    rows.add(BranchDashboardBatchRowDTO.builder()
                            .orgCode(org)
                            .dataDate(date)
                            .metricValues(new LinkedHashMap<>(orgValues))
                            .build());
                }
            }
            coverage.add(BranchDashboardHistoryCoverageDTO.builder()
                    .dataDate(date)
                    .expected(members.size() * codes.size())
                    .received(received)
                    .expectedSubjects(members.size())
                    .receivedSubjects(receivedSubjects)
                    .complete(received == members.size() * codes.size())
                    .missingSubjects(missingSubjects)
                    .missing(missing)
                    .build());
        }
        return new HistoryResult(rows, coverage);
    }

    private Map<String, BigDecimal> loadTargets(TargetPlanDTO plan, List<String> members,
                                                 String cycleKey, LocalDate dataDate) {
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        String actualCode = properties.getActualMetricCode();
        for (String org : members) {
            List<TargetValueDTO> values = targetApi.listTargetValues(plan.getId(), "ORG", org, cycleKey);
            List<TargetValueDTO> valid = values == null ? List.of() : values.stream()
                    .filter(Objects::nonNull)
                    .filter(value -> actualCode.equals(value.getMetricCode()))
                    .filter(value -> covers(value.getStartDate(), value.getEndDate(), dataDate))
                    .toList();
            if (valid.size() != 1 || valid.get(0).getTargetValue() == null
                    || valid.get(0).getTargetValue().signum() <= 0) {
                throw new IllegalStateException("机构目标必须恰有一条且大于 0: " + org);
            }
            result.put(org, valid.get(0).getTargetValue());
        }
        return result;
    }

    private Map<String, MarketingOrgSnapshotDTO> normalizeMarketing(List<String> members,
                                                                      List<MarketingOrgSnapshotDTO> rows) {
        Map<String, MarketingOrgSnapshotDTO> byOrg = new LinkedHashMap<>();
        if (rows == null) {
            throw new IllegalStateException("客户营销快照缺机构: " + members);
        }
        String sourceMode = null;
        for (MarketingOrgSnapshotDTO row : rows) {
            if (row == null || !StringUtils.hasText(row.getOrgCode())
                    || !members.contains(row.getOrgCode().trim())) {
                throw new IllegalStateException("客户营销快照包含未知或空机构");
            }
            String orgCode = row.getOrgCode().trim();
            if (byOrg.containsKey(orgCode)) {
                throw new IllegalStateException("客户营销快照机构重复: " + orgCode);
            }
            if (row.getValidCustomerCount() == null || row.getValidCustomerCount() < 0
                    || row.getPendingFollowUpTaskCount() == null
                    || row.getPendingFollowUpTaskCount() < 0) {
                throw new IllegalStateException("客户营销指标必须存在且非负: " + orgCode);
            }
            if (row.getSourceAsOfDate() == null) {
                throw new IllegalStateException("客户营销快照缺 sourceAsOfDate: " + orgCode);
            }
            if (!StringUtils.hasText(row.getSourceMode())) {
                throw new IllegalStateException("客户营销快照缺 sourceMode: " + orgCode);
            }
            String rowSourceMode = row.getSourceMode().trim();
            if (sourceMode == null) {
                sourceMode = rowSourceMode;
            } else if (!sourceMode.equals(rowSourceMode)) {
                throw new IllegalStateException("客户营销快照 sourceMode 不一致");
            }
            byOrg.put(orgCode, row);
        }
        if (byOrg.size() != members.size()) {
            List<String> missing = members.stream().filter(org -> !byOrg.containsKey(org)).toList();
            throw new IllegalStateException("客户营销快照缺机构: " + missing);
        }
        return byOrg;
    }

    private void validatePlan(TargetPlanDTO plan, LocalDate date) {
        if (plan == null || !"ACTIVE".equalsIgnoreCase(plan.getStatus())
                || !"ORG".equalsIgnoreCase(plan.getTargetDim())) {
            throw new IllegalStateException("目标方案必须为 ACTIVE/ORG");
        }
        LocalDate start = plan.getStartDate() != null ? plan.getStartDate() : plan.getEffectiveDate();
        LocalDate end = plan.getEndDate();
        if (start != null && date.isBefore(start)) {
            throw new IllegalStateException("目标方案尚未覆盖业务日");
        }
        if (end != null && date.isAfter(end)) {
            throw new IllegalStateException("目标方案已过期");
        }
    }

    private String cycleKey(String cycle, LocalDate date) {
        String normalized = cycle == null ? "QUARTER" : cycle.trim().toUpperCase();
        if (normalized.contains("YEAR")) {
            return String.valueOf(date.getYear());
        }
        if (normalized.contains("MONTH")) {
            return String.format("%d%02d", date.getYear(), date.getMonthValue());
        }
        int quarter = (date.getMonthValue() - 1) / 3 + 1;
        return date.getYear() + "Q" + quarter;
    }

    private boolean isComplete(Map<String, Map<String, BigDecimal>> values,
                               List<String> members, List<String> codes) {
        return members.stream().allMatch(org -> codes.stream().allMatch(code ->
                values.getOrDefault(org, Map.of()).get(code) != null));
    }

    private BigDecimal percentage(BigDecimal numerator, BigDecimal denominator, PerfMetricDef def) {
        int scale = def == null || def.getDecimalPlaces() == null
                ? PERCENT_SCALE : Math.max(0, def.getDecimalPlaces());
        return numerator.multiply(BigDecimal.valueOf(100))
                .divide(denominator, scale, RoundingMode.HALF_UP);
    }

    private BigDecimal nonNegative(Long value, String metricCode, String orgCode) {
        if (value == null || value < 0) {
            throw new IllegalStateException("客户营销指标必须存在且非负: " + orgCode + ":" + metricCode);
        }
        return BigDecimal.valueOf(value);
    }

    private boolean covers(LocalDate start, LocalDate end, LocalDate date) {
        return (start == null || !date.isBefore(start)) && (end == null || !date.isAfter(end));
    }

    private void ensureEnabled() {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("分行大屏批次服务未启用");
        }
        String classification = classification();
        if (!"TEST".equals(classification) && !"PROD".equals(classification)) {
            throw new IllegalStateException("dataClassification 只能是 TEST 或 PROD");
        }
        if ("PROD".equals(classification)) {
            List<String> sourceCodes = new ArrayList<>();
            sourceCodes.add(properties.getGroupCode());
            sourceCodes.add(properties.getTargetPlanCode());
            sourceCodes.add(properties.getActualMetricCode());
            sourceCodes.addAll(normalizeCodes(properties.getUpstreamMetricCodes()));
            sourceCodes.add(properties.getCustomerMetricCode());
            sourceCodes.add(properties.getAttentionMetricCode());
            sourceCodes.add(properties.getTargetMetricCode());
            sourceCodes.add(properties.getOrgRateMetricCode());
            sourceCodes.add(properties.getGroupContributionMetricCode());
            String demoCode = sourceCodes.stream()
                    .filter(this::isDemoSourceCode)
                    .findFirst()
                    .orElse(null);
            if (demoCode != null) {
                throw new IllegalStateException("PROD 禁止使用测试/演示来源: " + demoCode);
            }
        }
    }

    private boolean isProduction() {
        return "PROD".equals(classification());
    }

    private String classification() {
        return properties.getDataClassification() == null
                ? "" : properties.getDataClassification().trim().toUpperCase(Locale.ROOT);
    }

    private boolean isDemoSourceCode(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return normalized.startsWith("TEST_")
                || normalized.startsWith("TEST-")
                || normalized.contains("RAND")
                || normalized.contains("DEMO")
                || normalized.contains("MOCK")
                || normalized.contains("SAMPLE")
                || normalized.contains("DUMMY")
                || normalized.contains("FAKE")
                || normalized.contains("12500")
                || normalized.contains("2026-08-30")
                || normalized.contains("20260830")
                || normalized.contains("8/30");
    }

    /** 公共查询只返回稳定用户提示；数据库错误详情仅保留在 PERF_RUN_TASK.error_msg。 */
    private String attemptMessage(PerfRunTask task) {
        if (task == null || STATUS_SUCCESS.equals(task.getStatus())) {
            return null;
        }
        if (STATUS_RUNNING.equals(task.getStatus()) || "PENDING".equals(task.getStatus())) {
            return "本次计算正在进行";
        }
        return "本次计算失败，请根据批次编号查看任务日志";
    }

    private boolean isRate(String metricCode) {
        return Objects.equals(metricCode, properties.getOrgRateMetricCode())
                || Objects.equals(metricCode, properties.getGroupContributionMetricCode());
    }

    private String definitionDigest(Map<String, BranchDashboardMetricContractDTO> contracts) {
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(contracts);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (JsonProcessingException | NoSuchAlgorithmException ex) {
            throw new IllegalStateException("指标合同摘要不可生成", ex);
        }
    }

    private List<String> normalizeMembers(Collection<String> members) {
        if (members == null) {
            return List.of();
        }
        return members.stream().filter(StringUtils::hasText).map(String::trim).distinct().sorted().toList();
    }

    private List<String> normalizeCodes(Collection<String> codes) {
        if (codes == null) {
            return new ArrayList<>();
        }
        return codes.stream().filter(StringUtils::hasText).map(String::trim).distinct().toList();
    }

    private BranchDashboardBatchDTO failedSnapshot(String batchId, LocalDate dataDate, String version,
                                                    LocalDateTime calculatedAt, String message) {
        return BranchDashboardBatchDTO.builder()
                .batchId(batchId)
                .groupCode(properties.getGroupCode())
                .dataDate(dataDate)
                .version(version)
                .status(STATUS_FAILED)
                .calculatedAt(calculatedAt)
                .memberOrgCodes(List.of())
                .quality(BranchDashboardQualityDTO.builder().expected(0).received(0)
                        .missing(List.of(message)).mixedPeriod(false).build())
                .rows(List.of())
                .historyRows(List.of())
                .historyCoverage(List.of())
                .build();
    }

    private String newBatchId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String requireText(String value, String label) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(label + " 必须配置");
        }
        return value.trim();
    }

    private String expectedUnit(String metricCode) {
        Map<String, String> expectedUnits = properties.getExpectedUnits();
        return expectedUnits == null ? null : expectedUnits.get(metricCode);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String jsonOrFallback(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "{\"serializationError\":\"" + errorMessage(ex).replace("\"", "'") + "\"}";
        }
    }

    /** 成功快照和参数必须可序列化；失败时抛异常进入 FAILED 事务，不允许伪造 SUCCESS JSON。 */
    private String jsonRequired(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("批次快照不可序列化", ex);
        }
    }

    private String errorMessage(Exception ex) {
        String message = ex.getMessage();
        if (!StringUtils.hasText(message)) {
            return ex.getClass().getSimpleName();
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }

    private String safeError(String message) {
        if (!StringUtils.hasText(message)) {
            return null;
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }

    private record Calculation(BranchDashboardBatchDTO snapshot,
                               Map<String, PerfMetricDef> outputDefinitions,
                               List<String> outputCodes,
                               String version,
                               Map<String, Object> params) {
    }

    private static final class BatchInputContext {
        private LocalDate dataDate;
        private String version;
        private List<String> memberOrgCodes = List.of();
    }

    private record HistoryResult(List<BranchDashboardBatchRowDTO> rows,
                                 List<BranchDashboardHistoryCoverageDTO> coverage) {
    }

    private record DefinitionValidation(Map<String, PerfMetricDef> definitions,
                                        List<String> metadataGaps,
                                        Map<String, BranchDashboardMetricContractDTO> contracts) {
    }

    private static final class QualityAccumulator {
        private final int expected;
        private final int expectedSubjects;
        private final Set<String> subjectCodes;
        private int received;
        private int receivedSubjects;
        private final List<String> missing = new ArrayList<>();
        private final List<String> newerIncomplete = new ArrayList<>();
        private boolean mixedPeriod;
        private boolean selectedComplete;

        private QualityAccumulator(int expected, int expectedSubjects, Collection<String> subjectCodes) {
            this.expected = expected;
            this.expectedSubjects = expectedSubjects;
            this.subjectCodes = subjectCodes == null ? Set.of() : Set.copyOf(subjectCodes);
        }

        private void setMixedPeriod(boolean value) {
            this.mixedPeriod = value;
        }

        private BranchDashboardQualityDTO toDto() {
            return BranchDashboardQualityDTO.builder()
                    .expected(expected)
                    .received(received)
                    .expectedSubjects(expectedSubjects)
                    .receivedSubjects(receivedSubjects)
                    .missingSubjects(missing.stream().filter(value -> value.contains(":"))
                            .map(value -> value.substring(0, value.indexOf(':')))
                            .filter(subjectCodes::contains).distinct().toList())
                    .missing(new ArrayList<>(new LinkedHashSet<>(missing)))
                    .mixedPeriod(mixedPeriod)
                    .selectedComplete(selectedComplete)
                    .newerIncomplete(new ArrayList<>(newerIncomplete))
                    .build();
        }
    }
}
