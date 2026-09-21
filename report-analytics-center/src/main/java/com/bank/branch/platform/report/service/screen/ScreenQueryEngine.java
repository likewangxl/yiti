package com.bank.branch.platform.report.service.screen;

import com.bank.branch.platform.auth.api.OrgApi;
import com.bank.branch.platform.auth.api.dto.OrgDTO;
import com.bank.branch.platform.common.web.exception.BizException;
import com.bank.branch.platform.performance.api.BranchDashboardBatchQueryApi;
import com.bank.branch.platform.performance.api.dto.BranchDashboardBatchAttemptDTO;
import com.bank.branch.platform.performance.api.dto.BranchDashboardBatchDTO;
import com.bank.branch.platform.performance.api.dto.BranchDashboardBatchRowDTO;
import com.bank.branch.platform.performance.api.dto.BranchDashboardHistoryCoverageDTO;
import com.bank.branch.platform.performance.api.dto.BranchDashboardMetricContractDTO;
import com.bank.branch.platform.performance.api.dto.BranchDashboardQualityDTO;
import com.bank.branch.platform.performance.api.dto.BranchDashboardSourceAsOfDTO;
import com.bank.branch.platform.report.dto.req.ScreenDataReqDTO;
import com.bank.branch.platform.report.dto.resp.ScreenDataRespDTO;
import com.bank.branch.platform.report.entity.RptScreenDatasource;
import com.bank.branch.platform.report.enums.RptErrorCode;
import com.bank.branch.platform.report.exception.RptException;
import com.bank.branch.platform.report.support.ScreenConfigSchema;
import com.bank.branch.platform.report.support.ScreenPeriodResolver;
import com.bank.branch.platform.report.support.ScreenPeriodResolver.ResolvedPeriod;
import com.bank.branch.platform.report.support.ScreenSqlTemplate;
import com.bank.branch.platform.report.support.SqlSafeValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;

/**
 * 大屏查询执行引擎（核心）.
 *
 * <p>四类数据源统一出口：宽表引导式（EMP/ORG/CUST_INDEX_RESULT，按 SYS_CONTROL 当前版本 + 周期过滤）、
 * KPI 结果引导式（KPI_RESULT 行式表，每周期取 as_of_date 最新一条）、KPI 细项引导式
 * （PERF_KPI_SCORE T+1 快照，SNAPSHOT 细项明细 / TREND 按细项透视）、自定义 SQL
 * （#{param} 占位 → PreparedStatement 绑定，JSqlParser 白名单校验，外层强制 LIMIT）。
 * 全部跑在 rptReadOnlyDataSource 只读数据源上，query timeout 5s。
 *
 * <p>SqlSafeValidator 在此内部 new（不注册 Bean），避免与 SQL 探查的同类型 Bean 注入冲突。
 */
@Slf4j
@Service
public class ScreenQueryEngine {

    /** 查询超时（秒），大屏接口要求快速失败 */
    private static final int QUERY_TIMEOUT_SEC = 5;

    /** 宽表引导式允许的表 → [主体列, 上下文参数名, SYS_CONTROL.scope_dim] */
    private static final Map<String, String[]> WIDE_TABLES = Map.of(
            "EMP_INDEX_RESULT", new String[]{"emp_id", "empId", "EMP"},
            "ORG_INDEX_RESULT", new String[]{"org_code", "orgCode", "ORG"},
            "CUST_INDEX_RESULT", new String[]{"cust_no", "custNo", "CUST"});

    // KPI_RESULT 允许的周期类型：库中真实口径以 YEARLY 为主（MONTHLY/QUARTERLY 亦保留），
    // 缺 YEARLY 会导致个人屏 KPI 卡/趋势即使 empId 正确也永远空 rows（FIX-2）
    private static final Set<String> KPI_CYCLE_TYPES = Set.of("MONTHLY", "QUARTERLY", "YEARLY");

    /** 金额展示预设到单位的映射；仅用于 columnsMeta，不对 rows 原始值做换算。 */
    private static final Map<String, String> FIELD_META_AMOUNT_SCALE_UNITS = Map.of(
            "YUAN", "元",
            "TEN_THOUSAND_YUAN", "万元",
            "HUNDRED_MILLION_YUAN", "亿元");

    /** 金额展示预设固定保留 2 位小数。 */
    private static final int FIELD_META_AMOUNT_SCALE_DECIMALS = 2;

    private final DataSource readOnlyDataSource;
    private final SqlSafeValidator validator;
    /** 大屏白名单（大写表名）——SqlSafeValidator 自 3f22660c 起不再做白名单拒绝（SQL 探查产品决策），大屏按 D1 决策在引擎侧自查 */
    private final Set<String> whitelistUpper;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final int maxRows;
    private final OrgApi orgApi;
    private final M98StatQuery m98StatQuery;
    /** FREE_REPORT 的固定批次只读查询器；不使用自由报表业务 mapper。 */
    private final FreeReportScreenQuery freeReportScreenQuery;

    /** 由 performance 提供的不可变批次查询契约；未配置 batchPolicy 时完全不参与旧路径。 */
    @Autowired(required = false)
    private BranchDashboardBatchQueryApi batchQueryApi;

    public ScreenQueryEngine(
            @Qualifier("rptReadOnlyDataSource") DataSource readOnlyDataSource,
            OrgApi orgApi,
            @Value("#{'${rpt.screen.whitelist-tables:EMP_INDEX_RESULT,ORG_INDEX_RESULT,CUST_INDEX_RESULT,"
                    + "KPI_RESULT,PERF_KPI_SCORE,PERF_KPI_SCHEME,PERF_METRIC_DEF,PERF_TARGET_VALUE,"
                    + "PERF_TARGET_PLAN,SYS_CONTROL,EXT_ORG_INFO,"
                    + "ADDRBOOK_EMPLOYEE,TOUCH_TASK,TOUCH_LOG,ACT_RU_TASK,ACT_HI_PROCINST,BIZ_PROCESS_MAP}'"
                    + ".split(',')}") List<String> whitelistTables,
            @Value("#{'${rpt.screen.forbidden-keywords:DROP,DELETE,UPDATE,INSERT,TRUNCATE,ALTER,CREATE,RENAME,"
                    + "REPLACE,GRANT,REVOKE,LOCK,UNLOCK,CALL,EXEC,EXECUTE,LOAD,SHUTDOWN,USE,DESCRIBE,EXPLAIN,SHOW,"
                    + "COMMIT,ROLLBACK,SAVEPOINT,DECLARE,HANDLER,SIGNAL,RESIGNAL}'.split(',')}")
            List<String> forbiddenKeywords,
            @Value("${rpt.screen.max-rows:1000}") int maxRows) {
        this.readOnlyDataSource = readOnlyDataSource;
        this.orgApi = orgApi;
        this.maxRows = maxRows;
        this.validator = new SqlSafeValidator(whitelistTables, forbiddenKeywords, maxRows, 8000, 3);
        this.whitelistUpper = whitelistTables.stream()
                .map(t -> t.trim().toUpperCase(java.util.Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
        this.m98StatQuery = new M98StatQuery(readOnlyDataSource, orgApi);
        this.freeReportScreenQuery = new FreeReportScreenQuery(readOnlyDataSource);
    }

    /** 包级测试注入点；生产环境由 Spring 注入 performance 公共只读契约。 */
    void setBatchQueryApi(BranchDashboardBatchQueryApi batchQueryApi) {
        this.batchQueryApi = batchQueryApi;
    }

    /** 校验自定义 SQL 模板（保存/试跑/每次执行都调用），失败抛 RPT-43002 */
    public void validateCustomSql(String sqlTemplate) {
        com.bank.branch.platform.report.support.SqlSafeResult result;
        try {
            // SqlSafeValidator/ScreenSqlTemplate 抛的一切校验错（含 RPT-42xxx 语法/关键字/深度校验）
            // 统一收敛为大屏语义 43002（保留原因链）；SqlSafeValidator 自 3f22660c 起只提取表名不做白名单拒绝
            result = validator.validateAndNormalize(ScreenSqlTemplate.toValidatable(sqlTemplate));
        } catch (BizException e) {
            throw new RptException(RptErrorCode.SCREEN_DS_SQL_INVALID, e);
        }
        // 白名单拒绝在引擎侧自查（SqlSafeResult#getReferencedTables() 已统一大写，见源码 §referencedTables 字段）
        for (String table : result.getReferencedTables()) {
            if (!whitelistUpper.contains(table)) {
                log.warn("[ScreenQueryEngine] 自定义 SQL 命中白名单外表 {}", table);
                throw new RptException(RptErrorCode.SCREEN_DS_SQL_INVALID);
            }
        }
    }

    /** 保存 FREE_REPORT 数据源时的只读批次和列定义校验。 */
    public void validateFreeReportSource(JsonNode config) {
        freeReportScreenQuery.validateConfiguredSource(config);
    }

    /** 正式取数（LIMIT = maxRows） */
    public ScreenDataRespDTO query(RptScreenDatasource ds, ScreenDataReqDTO req) {
        return queryAt(ds, req, LocalDate.now(), maxRows);
    }

    /** 配置态试跑（LIMIT = 10），与正式取数一样透出 columnsMeta */
    public ScreenDataRespDTO tryRun(String sourceKind, String configJson, ScreenDataReqDTO req) {
        return tryRunAt(sourceKind, configJson, req, LocalDate.now(), 10);
    }

    /**
     * 正式取数的可测试入口，允许固定“当前日期”验证未来日期和时效边界。
     * 运行时质量预检只应用于命名机构组的 ORG 宽表 LATEST 聚合。
    */
    ScreenDataRespDTO queryAt(RptScreenDatasource ds, ScreenDataReqDTO req, LocalDate today, int limit) {
        return runAt(ds.getSourceKind(), ds.getConfigJson(), req, today, limit);
    }

    /** 配置态试跑的可测试入口，与正式取数共用同一完整批次预检和 SQL 构造路径。 */
    ScreenDataRespDTO tryRunAt(String sourceKind, String configJson, ScreenDataReqDTO req,
                               LocalDate today, int limit) {
        return runAt(sourceKind, configJson, req, today, limit);
    }

    private ScreenDataRespDTO runAt(String sourceKind, String configJson, ScreenDataReqDTO req,
                                    LocalDate today, int limit) {
        JsonNode cfg = readConfig(configJson);
        if (FreeReportScreenPolicy.SOURCE_KIND.equals(sourceKind)) {
            cfg = FreeReportScreenPolicy.parseConfig(configJson);
            List<String> authorized = namedGroupAuthorizedOrgCodes(req);
            List<String> output = namedGroupOutputOrgCodes(req);
            return freeReportScreenQuery.query(cfg, authorized, output,
                    req == null ? null : req.getServerRequestedOrgCode(), limit);
        }
        if (usesImmutableBatch(sourceKind, cfg, req)) {
            return runImmutableBatch(cfg, req, today, limit);
        }
        if (isNamedKpiDetail(cfg, sourceKind, req)) {
            // 命名组 KPI 细项只返回服务端已核验机构集合的交集；不使用客户端 orgCode，
            // 也不走逐机构 latest，确保同一响应只有一个共同数据日期。
            List<String> authorized = namedGroupAuthorizedOrgCodes(req);
            BuiltQuery q = build(sourceKind, configJson, req, limit, today, null);
            ScreenDataRespDTO resp = execute(q);
            attachKpiDetailQuality(resp, authorized);
            fillColumnsMeta(resp, configJson);
            return resp;
        }
        if (isNamedM98Stat(cfg, sourceKind, req)) {
            List<String> authorized = namedGroupAuthorizedOrgCodes(req);
            List<String> output = namedGroupOutputOrgCodes(req);
            return m98StatQuery.query(cfg.path("profile").asText(), authorized, output, today, limit);
        }
        if (needsWideBatchResolution(sourceKind, cfg, req, today)) {
            // 先在建立连接前校验授权集合，空集合直接 fail-close，避免任何全量查询旁路。
            authorizedOrgCodes(req);
            // 预检和主查询必须共享同一只读事务快照，避免预检后源行变化导致 quality 与 rows 不一致。
            try (Connection conn = readOnlyDataSource.getConnection()) {
                boolean autoCommit = conn.getAutoCommit();
                conn.setReadOnly(true);
                conn.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
                if (autoCommit) {
                    conn.setAutoCommit(false);
                }
                try {
                    BatchResolution resolution = resolveWideBatchIfRequired(sourceKind, cfg, req, today, conn);
                    BuiltQuery q = build(sourceKind, configJson, req, limit, today, resolution);
                    ScreenDataRespDTO resp = execute(q, conn);
                    if (autoCommit) {
                        conn.commit();
                    }
                    attachQuality(resp, resolution);
                    fillColumnsMeta(resp, configJson);
                    return resp;
                } catch (SQLException e) {
                    if (autoCommit) {
                        try {
                            conn.rollback();
                        } catch (SQLException rollbackFailure) {
                            e.addSuppressed(rollbackFailure);
                        }
                    }
                    throw e;
                }
            } catch (SQLException e) {
                log.warn("[ScreenQueryEngine] 完整批次取数失败 cause={}", e.getMessage());
                throw new RptException(RptErrorCode.SCREEN_DATA_QUERY_FAILED, e);
            }
        }
        BuiltQuery q = build(sourceKind, configJson, req, limit, today, null);
        ScreenDataRespDTO resp = execute(q);
        fillColumnsMeta(resp, configJson);
        return resp;
    }

    /**
     * 分行机构组数据源的不可变批次路径。batchPolicy 采用“对象存在即启用”，因此不会把
     * performance 快照误当成旧宽表 SQL 的旁路；旧数据源没有该配置时仍走原路径。
     */
    private boolean usesImmutableBatch(String sourceKind, JsonNode cfg, ScreenDataReqDTO req) {
        return "WIDE_TABLE".equals(sourceKind)
                && "ORG_INDEX_RESULT".equals(cfg.path("table").asText())
                && isNamedGroup(cfg, req)
                && cfg.path("batchPolicy").isObject();
    }

    private ScreenDataRespDTO runImmutableBatch(JsonNode cfg, ScreenDataReqDTO req,
                                                LocalDate today, int limit) {
        // 即使批次数据由 performance 提供，report 配置的槽位仍必须接受同一 1..400 校验；
        // 槽位只用于契约/列元数据，不进入任何 SQL 字符串。
        configuredSlotCols(cfg.path("metrics"));
        List<String> authorized = authorizedOrgCodes(req);
        String groupCode = trimToNull(req.getServerGroupCode());
        if (groupCode == null) {
            throw new RptException(RptErrorCode.SCREEN_SCOPE_INVALID);
        }
        if (batchQueryApi == null) {
            throw new RptException(RptErrorCode.SCREEN_DATA_QUERY_FAILED,
                    new IllegalStateException("BranchDashboardBatchQueryApi unavailable"));
        }
        BatchPolicy policy = batchPolicy(cfg);
        Optional<BranchDashboardBatchDTO> found;
        try {
            String batchId = trimToNull(req.getBatchId());
            found = batchId == null
                    ? batchQueryApi.latest(groupCode, authorized)
                    : batchQueryApi.byId(batchId, authorized);
        } catch (RuntimeException ex) {
            log.warn("[ScreenQueryEngine] 不可变批次查询失败 groupCode={} batchId={} cause={}",
                    groupCode, req.getBatchId(), ex.toString());
            throw new RptException(RptErrorCode.SCREEN_DATA_QUERY_FAILED, ex);
        }
        if (found == null || found.isEmpty()) {
            return noImmutableBatch(cfg, authorized.size(), policy.maxAgeDays(),
                    "当前机构组或授权范围内没有可见不可变批次");
        }

        BranchDashboardBatchDTO snapshot = found.get();
        if (!groupCode.equals(trimToNull(snapshot.getGroupCode()))) {
            return noImmutableBatch(cfg, authorized.size(), policy.maxAgeDays(),
                    "不可变批次机构组与当前屏不一致");
        }
        BranchDashboardQualityDTO sourceQuality = snapshot.getQuality();
        int expectedSubjects = sourceQuality == null || sourceQuality.getExpectedSubjects() <= 0
                ? snapshot.getMemberOrgCodes() == null ? 0 : snapshot.getMemberOrgCodes().size()
                : sourceQuality.getExpectedSubjects();
        int receivedSubjects = sourceQuality == null ? 0 : sourceQuality.getReceivedSubjects();
        boolean selectedComplete = sourceQuality != null && sourceQuality.isSelectedComplete();
        // performance 的公共快照 DTO 对外使用 COMPLETE；SUCCESS 仅保留为旧快照兼容值。
        String snapshotStatus = trimToNull(snapshot.getStatus());
        boolean validStatus = "SUCCESS".equalsIgnoreCase(snapshotStatus)
                || "COMPLETE".equalsIgnoreCase(snapshotStatus);
        boolean futureDate = snapshot.getDataDate() == null || snapshot.getDataDate().isAfter(today);
        boolean fullCoverage = selectedComplete && expectedSubjects > 0
                && receivedSubjects == expectedSubjects;
        // requiredComplete=false 只影响契约兼容，不允许 PARTIAL 批次进入汇总，避免返回半组合计。
        if (!validStatus || futureDate || !fullCoverage) {
            return noImmutableBatch(cfg, expectedSubjects, policy.maxAgeDays(),
                    immutableBatchFailureMessage(snapshot, sourceQuality, futureDate));
        }

        int ageDays = Math.max(0, (int) ChronoUnit.DAYS.between(snapshot.getDataDate(), today));
        boolean stale = policy.maxAgeDays() != null && ageDays > policy.maxAgeDays();
        ScreenDataRespDTO response = mapImmutableBatch(cfg, req, snapshot, today, limit, authorized);
        if ("SUBJECT".equals(cfg.path("aggregation").path("groupBy").asText())) {
            response = appendOrgNames(response);
        }
        response.setQuality(toImmutableQuality(snapshot, sourceQuality, expectedSubjects, receivedSubjects,
                policy.maxAgeDays(), ageDays,
                stale ? "STALE" : "COMPLETE",
                stale ? "最近完整不可变批次已超过允许时效" : "授权机构范围内最近完整不可变批次"));
        fillImmutableColumnsMeta(response, cfg, snapshot);
        return response;
    }

    private String immutableBatchFailureMessage(BranchDashboardBatchDTO snapshot,
                                                BranchDashboardQualityDTO quality,
                                                boolean futureDate) {
        if (futureDate) {
            return "不可变批次日期晚于当前业务日期";
        }
        if (quality != null && quality.getMissing() != null && !quality.getMissing().isEmpty()) {
            return "不可变批次机构或指标覆盖不完整";
        }
        return "不可变批次状态不可用于完整汇总";
    }

    private ScreenDataRespDTO noImmutableBatch(JsonNode cfg, int expectedSubjects,
                                               Integer maxAgeDays, String message) {
        ScreenDataRespDTO response = new ScreenDataRespDTO(batchColumns(cfg), List.of());
        ScreenDataRespDTO.Quality quality = new ScreenDataRespDTO.Quality(
                null, null, null, "NO_COMPLETE_BATCH", expectedSubjects, 0,
                maxAgeDays, null, message);
        quality.setExpected(0);
        quality.setReceived(0);
        quality.setSelectedComplete(false);
        quality.setMixedPeriod(false);
        quality.setMissing(List.of(message));
        quality.setMissingSubjects(List.of());
        quality.setNewerIncomplete(List.of());
        response.setQuality(quality);
        fillColumnsMeta(response, cfg.toString());
        return response;
    }

    private BatchPolicy batchPolicy(JsonNode cfg) {
        JsonNode policy = cfg.path("batchPolicy");
        Integer maxAgeDays = policy.has("maxAgeDays") ? policy.path("maxAgeDays").asInt() : null;
        boolean requiredComplete = !policy.has("requiredComplete")
                || policy.path("requiredComplete").asBoolean();
        return new BatchPolicy(maxAgeDays, requiredComplete);
    }

    private List<String> batchColumns(JsonNode cfg) {
        JsonNode aggregation = cfg.path("aggregation");
        String groupBy = aggregation.path("groupBy").asText();
        List<String> columns = new ArrayList<>();
        if (aggregation.isMissingNode() || aggregation.isNull()) {
            columns.add("data_date");
        } else if ("SUBJECT".equals(groupBy)) {
            columns.add("org_code");
        } else if ("DATE".equals(groupBy)) {
            columns.add("data_date");
        }
        cfg.path("metrics").forEach(metric -> columns.add(metricAlias(metric)));
        return columns;
    }

    private String metricAlias(JsonNode metric) {
        String alias = metric.path("metricName").asText();
        alias = alias.replaceAll("[`'\"\\\\]", "");
        if (alias.isBlank()) {
            alias = metric.path("metricCode").asText();
        }
        return alias;
    }

    private ScreenDataRespDTO mapImmutableBatch(JsonNode cfg, ScreenDataReqDTO req,
                                                BranchDashboardBatchDTO snapshot, LocalDate today,
                                                int limit, List<String> authorized) {
        String groupBy = cfg.path("aggregation").path("groupBy").asText();
        String agg = cfg.path("aggregation").path("agg").asText();
        boolean rawRows = cfg.path("aggregation").isMissingNode()
                || cfg.path("aggregation").isNull();
        ResolvedPeriod period = ScreenPeriodResolver.resolve(req.getPeriod(), req.getDateFrom(), req.getDateTo(), today);
        String requested = trimToNull(req.getServerRequestedOrgCode());
        if (requested != null && !authorized.contains(requested)) {
            throw new RptException(RptErrorCode.SCREEN_SCOPE_INVALID);
        }
        Set<String> visibleMembers = new LinkedHashSet<>(authorized);
        if (requested != null) {
            visibleMembers.retainAll(List.of(requested));
        }
        List<BranchDashboardBatchRowDTO> sourceRows = snapshot.getRows() == null
                ? List.of() : snapshot.getRows();
        // LATEST 必须使用选中不可变批次的 rows；historyRows 只服务于历史周期，避免历史金融列
        // 覆盖当前批次已计算的客户/目标/率等输出指标。
        if (!period.latestOnly() && (rawRows || "DATE".equals(groupBy))) {
            sourceRows = snapshot.getHistoryRows() == null || snapshot.getHistoryRows().isEmpty()
                    ? sourceRows : snapshot.getHistoryRows();
        }
        List<BranchDashboardBatchRowDTO> visibleRows = sourceRows.stream()
                .filter(row -> row != null && visibleMembers.contains(row.getOrgCode()))
                .filter(row -> row.getDataDate() != null && !row.getDataDate().isAfter(today))
                .sorted(Comparator.comparing(BranchDashboardBatchRowDTO::getDataDate)
                        .thenComparing(BranchDashboardBatchRowDTO::getOrgCode,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        if (!period.latestOnly()) {
            visibleRows = visibleRows.stream()
                    .filter(row -> !row.getDataDate().isBefore(period.from())
                            && !row.getDataDate().isAfter(period.to()))
                    .toList();
        } else {
            LocalDate selectedDate = snapshot.getDataDate();
            visibleRows = visibleRows.stream()
                    .filter(row -> selectedDate.equals(row.getDataDate()))
                    .toList();
        }

        List<List<Object>> rows = new ArrayList<>();
        if (rawRows) {
            for (BranchDashboardBatchRowDTO row : visibleRows.stream().limit(limit).toList()) {
                List<Object> output = new ArrayList<>();
                output.add(row.getDataDate().toString());
                output.addAll(metricValues(cfg, List.of(row), snapshot, agg, false, 1));
                rows.add(output);
            }
        } else if ("SUBJECT".equals(groupBy)) {
            for (BranchDashboardBatchRowDTO row : visibleRows.stream()
                    .filter(r -> snapshot.getDataDate().equals(r.getDataDate()))
                    .limit(limit)
                    .toList()) {
                List<Object> output = new ArrayList<>();
                output.add(row.getOrgCode());
                output.addAll(metricValues(cfg, List.of(row), snapshot, agg, false, 1));
                rows.add(output);
            }
        } else if ("DATE".equals(groupBy)) {
            Map<LocalDate, List<BranchDashboardBatchRowDTO>> byDate = new LinkedHashMap<>();
            for (BranchDashboardBatchRowDTO row : visibleRows) {
                byDate.computeIfAbsent(row.getDataDate(), ignored -> new ArrayList<>()).add(row);
            }
            for (Map.Entry<LocalDate, List<BranchDashboardBatchRowDTO>> entry : byDate.entrySet()) {
                List<Object> output = new ArrayList<>();
                output.add(entry.getKey().toString());
                output.addAll(metricValues(cfg, entry.getValue(), snapshot, agg, true, visibleMembers.size()));
                rows.add(output);
                if (rows.size() >= limit) {
                    break;
                }
            }
        } else {
            if (!visibleRows.isEmpty()) {
                rows.add(metricValues(cfg, visibleRows, snapshot, agg, true, visibleMembers.size()));
            }
        }
        return new ScreenDataRespDTO(batchColumns(cfg), rows);
    }

    private List<Object> metricValues(JsonNode cfg, List<BranchDashboardBatchRowDTO> rows,
                                      BranchDashboardBatchDTO snapshot, String agg, boolean aggregate,
                                      int expectedSubjects) {
        List<Object> values = new ArrayList<>();
        for (JsonNode metric : cfg.path("metrics")) {
            String code = metric.path("metricCode").asText();
            BranchDashboardMetricContractDTO contract = snapshot.getMetricContracts() == null
                    ? null : snapshot.getMetricContracts().get(code);
            if (!aggregate) {
                values.add(valueOf(rows.get(0), code));
            } else {
                values.add(aggregateMetric(code, rows, contract, agg, expectedSubjects));
            }
        }
        return values;
    }

    private Object valueOf(BranchDashboardBatchRowDTO row, String metricCode) {
        if (row.getMetricValues() == null) {
            return null;
        }
        return row.getMetricValues().get(metricCode);
    }

    private BigDecimal aggregateMetric(String metricCode, List<BranchDashboardBatchRowDTO> rows,
                                       BranchDashboardMetricContractDTO contract, String agg,
                                       int expectedSubjects) {
        List<BigDecimal> values = rows.stream().map(row -> valueOf(row, metricCode))
                .filter(BigDecimal.class::isInstance).map(BigDecimal.class::cast).toList();
        if (values.isEmpty() || values.size() != expectedSubjects) {
            return null;
        }
        if (isPercent(contract) && "SUM".equals(agg)) {
            RatioContract ratio = ratioContract(contract);
            if (ratio == null) {
                // 百分率不是可加指标；等 performance 合同提供明确分子/分母前保持空值 fail-close。
                return null;
            }
            BigDecimal numerator = sumMetric(rows, ratio.numeratorCode(), expectedSubjects);
            BigDecimal denominator = sumMetric(rows, ratio.denominatorCode(), expectedSubjects);
            return denominator == null || denominator.signum() == 0 || numerator == null
                    ? null : numerator.divide(denominator, 8, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }
        return switch (agg) {
            case "SUM" -> values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            case "AVG" -> values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(values.size()), 8, RoundingMode.HALF_UP);
            case "MAX" -> values.stream().max(Comparator.naturalOrder()).orElse(null);
            case "MIN" -> values.stream().min(Comparator.naturalOrder()).orElse(null);
            case "COUNT" -> BigDecimal.valueOf(values.size());
            default -> null;
        };
    }

    private BigDecimal sumMetric(List<BranchDashboardBatchRowDTO> rows, String metricCode,
                                 int expectedSubjects) {
        List<BigDecimal> values = rows.stream().map(row -> valueOf(row, metricCode))
                .filter(BigDecimal.class::isInstance).map(BigDecimal.class::cast).toList();
        return values.size() != expectedSubjects
                ? null : values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private boolean isPercent(BranchDashboardMetricContractDTO contract) {
        if (contract == null || contract.getUnit() == null) {
            return false;
        }
        String unit = contract.getUnit().trim().toUpperCase(java.util.Locale.ROOT);
        return "%".equals(unit) || "PERCENT".equals(unit) || "百分比".equals(unit)
                || "PCT".equals(unit);
    }

    /** 百分率汇总直接使用 performance 合同的 typed 分子/分母，不猜业务指标别名。 */
    private RatioContract ratioContract(BranchDashboardMetricContractDTO contract) {
        if (contract == null) {
            return null;
        }
        String numerator = trimToNull(contract.getNumeratorMetricCode());
        String denominator = trimToNull(contract.getDenominatorMetricCode());
        return numerator == null || denominator == null
                ? null : new RatioContract(numerator, denominator);
    }

    private ScreenDataRespDTO.Quality toImmutableQuality(BranchDashboardBatchDTO snapshot,
                                                         BranchDashboardQualityDTO sourceQuality,
                                                         int expectedSubjects,
                                                         int receivedSubjects,
                                                         Integer maxAgeDays, int ageDays,
                                                         String status, String message) {
        String effectiveMessage = messageWithLatestAttempt(snapshot, message);
        ScreenDataRespDTO.Quality quality = new ScreenDataRespDTO.Quality(
                snapshot.getBatchId(),
                snapshot.getDataDate() == null ? null : snapshot.getDataDate().toString(),
                snapshot.getVersion(), status, expectedSubjects, receivedSubjects,
                maxAgeDays, ageDays, effectiveMessage);
        quality.setExpected(sourceQuality == null ? null : sourceQuality.getExpected());
        quality.setReceived(sourceQuality == null ? null : sourceQuality.getReceived());
        quality.setDataClassification(snapshot.getDataClassification());
        String subjectValueMode = snapshot.getSourceModes() == null
                ? null : trimToNull(snapshot.getSourceModes().get("subjectValueMode"));
        if ("TEST".equalsIgnoreCase(trimToNull(snapshot.getDataClassification()))
                && "EXCLUSIVE".equalsIgnoreCase(subjectValueMode)) {
            quality.setSubjectValueMode("EXCLUSIVE");
        }
        quality.setSelectedComplete(sourceQuality != null && sourceQuality.isSelectedComplete());
        quality.setMixedPeriod(sourceQuality != null && sourceQuality.isMixedPeriod());
        quality.setMissing(sourceQuality == null || sourceQuality.getMissing() == null
                ? List.of() : List.copyOf(sourceQuality.getMissing()));
        quality.setMissingSubjects(sourceQuality == null || sourceQuality.getMissingSubjects() == null
                ? List.of() : List.copyOf(sourceQuality.getMissingSubjects()));
        quality.setNewerIncomplete(sourceQuality == null || sourceQuality.getNewerIncomplete() == null
                ? List.of() : List.copyOf(sourceQuality.getNewerIncomplete()));
        quality.setHistoryCoverage(historyCoverage(snapshot.getHistoryCoverage()));
        quality.setCalculatedAt(toShanghaiIso(snapshot.getCalculatedAt()));
        quality.setSourceAsOf(sourceAsOf(snapshot.getSourceAsOf()));
        return quality;
    }

    private List<ScreenDataRespDTO.HistoryCoverage> historyCoverage(
            List<BranchDashboardHistoryCoverageDTO> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        return source.stream().filter(java.util.Objects::nonNull)
                .map(item -> new ScreenDataRespDTO.HistoryCoverage(
                        asIso(item.getDataDate()),
                        item.getExpected(),
                        item.getReceived(),
                        item.getExpectedSubjects(),
                        item.getReceivedSubjects(),
                        item.isComplete(),
                        item.getMissingSubjects() == null ? List.of() : List.copyOf(item.getMissingSubjects()),
                        item.getMissing() == null ? List.of() : List.copyOf(item.getMissing())))
                .toList();
    }

    /**
     * latest 查询可能回退到上一份 SUCCESS 快照，而最近一次计算尝试已经 FAILED/RUNNING。
     * 保持质量状态枚举表示当前返回快照，同时把回退原因放进 message，避免静态 AVAILABLE
     * 覆盖运行时事实或让页面误以为返回的是最新成功计算。
     */
    private String messageWithLatestAttempt(BranchDashboardBatchDTO snapshot, String message) {
        BranchDashboardBatchAttemptDTO attempt = snapshot.getLatestAttempt();
        if (attempt == null || trimToNull(attempt.getStatus()) == null) {
            return message;
        }
        String attemptStatus = attempt.getStatus().trim();
        if ("SUCCESS".equalsIgnoreCase(attemptStatus) || "COMPLETE".equalsIgnoreCase(attemptStatus)) {
            return message;
        }
        StringBuilder detail = new StringBuilder("最近一次批次尝试状态=").append(attemptStatus);
        if (trimToNull(attempt.getAttemptId()) != null) {
            detail.append("；最近尝试批次编号=").append(attempt.getAttemptId().trim());
        }
        if (trimToNull(attempt.getMessage()) != null) {
            detail.append("：").append(attempt.getMessage().trim());
        }
        return message == null || message.isBlank()
                ? detail.toString() : message + "；" + detail;
    }

    private Map<String, String> sourceAsOf(BranchDashboardSourceAsOfDTO source) {
        if (source == null) {
            return null;
        }
        Map<String, String> result = new LinkedHashMap<>();
        result.put("financial", asIso(source.getFinancial()));
        result.put("marketing", asIso(source.getMarketing()));
        result.put("target", asIso(source.getTarget()));
        result.put("revenue", asIso(source.getRevenue()));
        result.put("targetEffectiveDate", asIso(source.getTargetEffectiveDate()));
        result.put("financialCollectedAt", toShanghaiIso(source.getFinancialCollectedAt()));
        result.put("marketingCollectedAt", toShanghaiIso(source.getMarketingCollectedAt()));
        result.put("targetCollectedAt", toShanghaiIso(source.getTargetCollectedAt()));
        result.put("revenueCollectedAt", toShanghaiIso(source.getRevenueCollectedAt()));
        result.values().removeIf(java.util.Objects::isNull);
        return result;
    }

    private String asIso(LocalDate date) {
        return date == null ? null : date.toString();
    }

    private String toShanghaiIso(LocalDateTime dateTime) {
        return dateTime == null ? null : OffsetDateTime.of(dateTime, ZoneOffset.ofHours(8)).toString();
    }

    private void fillImmutableColumnsMeta(ScreenDataRespDTO response, JsonNode cfg,
                                          BranchDashboardBatchDTO snapshot) {
        Map<String, BranchDashboardMetricContractDTO> contracts = snapshot.getMetricContracts() == null
                ? Map.of() : snapshot.getMetricContracts();
        Map<String, JsonNode> metrics = new LinkedHashMap<>();
        cfg.path("metrics").forEach(metric -> metrics.put(metricAlias(metric), metric));
        List<ScreenDataRespDTO.ColumnMeta> meta = new ArrayList<>();
        for (String col : response.getColumns()) {
            if ("org_code".equals(col) || "org_name".equals(col) || "data_date".equals(col)) {
                meta.add(new ScreenDataRespDTO.ColumnMeta(col, null, "DIM", null, null));
                continue;
            }
            JsonNode metric = metrics.get(col);
            String code = metric == null ? null : metric.path("metricCode").asText(null);
            BranchDashboardMetricContractDTO contract = code == null ? null : contracts.get(code);
            meta.add(new ScreenDataRespDTO.ColumnMeta(
                    col,
                    contract == null ? null : contract.getMetricName(),
                    "METRIC",
                    contract == null ? null : canonicalUnit(contract.getUnit()),
                    contract == null ? null : contract.getDecimalPlaces()));
        }
        response.setColumnsMeta(meta);
    }

    private String canonicalUnit(String unit) {
        if (unit == null || unit.isBlank()) {
            return null;
        }
        return switch (unit.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "元", "YUAN", "RMB" -> "YUAN";
            case "户", "COUNT", "个", "人" -> "COUNT";
            case "%", "PERCENT", "PCT", "百分比" -> "PERCENT";
            default -> unit.trim();
        };
    }

    private record BatchPolicy(Integer maxAgeDays, boolean requiredComplete) {
    }

    private record RatioContract(String numeratorCode, String denominatorCode) {
    }

    /**
     * 按列名把 config_json.fieldMeta 匹配到响应 columnsMeta（spec 2026-07-17 §3.2，全 source_kind 通用）.
     *
     * <p>实现选择（测试固化）：仅包含配置过 fieldMeta 的列，顺序跟随 columns；
     * 数据源未配置 fieldMeta（或无任何列命中）时 columnsMeta 保持 null，旧调用方零影响。
     * 包级可见，供单测直接验证（execute 触库，纯逻辑在此收口）。
     */
    void fillColumnsMeta(ScreenDataRespDTO resp, String configJson) {
        JsonNode fieldMeta = readConfig(configJson).path("fieldMeta");
        if (!fieldMeta.isArray() || fieldMeta.isEmpty() || resp.getColumns() == null) {
            return;
        }
        Map<String, ScreenDataRespDTO.ColumnMeta> byCol = new HashMap<>();
        for (JsonNode n : fieldMeta) {
            String col = n.path("col").asText();
            String role = n.hasNonNull("role") ? n.path("role").asText() : null;
            String unit = n.hasNonNull("unit") ? n.path("unit").asText() : null;
            Integer decimals = n.hasNonNull("decimals") ? n.path("decimals").asInt() : null;
            String amountScale = n.hasNonNull("amountScale") ? n.path("amountScale").asText() : null;
            if (n.hasNonNull("amountScale")) {
                if (!FIELD_META_AMOUNT_SCALE_UNITS.containsKey(amountScale)
                        || !"METRIC".equals(role)
                        || n.has("unit")
                        || n.has("decimals")) {
                    throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
                }
                unit = FIELD_META_AMOUNT_SCALE_UNITS.get(amountScale);
                decimals = FIELD_META_AMOUNT_SCALE_DECIMALS;
            }
            byCol.put(col, new ScreenDataRespDTO.ColumnMeta(
                    col,
                    n.hasNonNull("alias") ? n.path("alias").asText() : null,
                    role,
                    unit,
                    decimals,
                    amountScale));
        }
        List<ScreenDataRespDTO.ColumnMeta> metas = new ArrayList<>();
        for (String column : resp.getColumns()) {
            ScreenDataRespDTO.ColumnMeta m = byCol.get(column);
            if (m != null) {
                metas.add(m);
            }
        }
        if (!metas.isEmpty()) {
            resp.setColumnsMeta(metas);
        }
    }

    /** 构造结果：最终 SQL（已含 LIMIT）+ 有序绑定参数 */
    record BuiltQuery(String sql, List<Object> params, boolean enrichOrgNames) {
        BuiltQuery(String sql, List<Object> params) {
            this(sql, params, false);
        }
    }

    /** 包级可见，供单测注入固定 today */
    BuiltQuery build(String sourceKind, String configJson, ScreenDataReqDTO req, int limit, LocalDate today) {
        return build(sourceKind, configJson, req, limit, today, null);
    }

    private BuiltQuery build(String sourceKind, String configJson, ScreenDataReqDTO req, int limit,
                             LocalDate today, BatchResolution resolution) {
        JsonNode cfg = readConfig(configJson);
        if (FreeReportScreenPolicy.SOURCE_KIND.equals(sourceKind)) {
            cfg = FreeReportScreenPolicy.parseConfig(configJson);
        }
        if (isNamedGroup(cfg, req)
                && !"WIDE_TABLE".equals(sourceKind)
                && !isNamedKpiDetailConfig(sourceKind, cfg)
                && !isNamedM98StatConfig(sourceKind, cfg)
                && !FreeReportScreenPolicy.SOURCE_KIND.equals(sourceKind)) {
            // 命名组只能绑定服务端可证明机构约束的内置查询；CUSTOM_SQL/KPI_RESULT 及
            // KPI_DETAIL 的 EMP/TREND 组合没有本次 ORG 快照的固定安全落点。
            throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
        }
        return switch (sourceKind == null ? "" : sourceKind) {
            case "WIDE_TABLE" -> buildWideTableQuery(cfg, req, limit, today, resolution);
            case "KPI_RESULT" -> buildKpiQuery(cfg, req, limit, today);
            case "KPI_DETAIL" -> buildKpiDetailQuery(cfg, req, limit, today);
            case "M98_STAT" -> buildM98StatQuery(cfg, req, limit, today);
            case "FREE_REPORT" -> buildFreeReportQuery(cfg, req, limit);
            case "CUSTOM_SQL" -> buildCustomQuery(cfg, req, limit, today);
            default -> throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
        };
    }

    private JsonNode readConfig(String configJson) {
        try {
            // schemaVersion 读时兼容集中在 ScreenConfigSchema 唯一入口（旧数据视为版本 1）
            JsonNode config = ScreenConfigSchema.withDefaults(
                    objectMapper.readTree(configJson == null ? "{}" : configJson));
            validateQualityPolicy(config);
            validateBatchPolicy(config);
            return config;
        } catch (Exception e) {
            throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID, e);
        }
    }

    /** 宽表聚合 groupBy 白名单（spec 2026-07-17 §3.3） */
    private static final Set<String> WIDE_AGG_GROUP_BYS = Set.of("NONE", "SUBJECT", "DATE");

    /** 宽表聚合函数白名单 */
    private static final Set<String> WIDE_AGG_FUNCS = Set.of("SUM", "AVG", "MAX", "MIN", "COUNT");

    /** 宽表 filters 操作符白名单 → SQL 运算符（IN 单独处理为多 ? 绑定） */
    private static final Map<String, String> WIDE_FILTER_OPS = Map.of(
            "EQ", "=", "NE", "<>", "GT", ">", "GE", ">=", "LT", "<", "LE", "<=");

    private BuiltQuery buildWideTableQuery(JsonNode cfg, ScreenDataReqDTO req, int limit, LocalDate today,
                                           BatchResolution resolution) {
        String table = cfg.path("table").asText();
        String[] meta = WIDE_TABLES.get(table);
        if (meta == null) {
            throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
        }
        if (isNamedGroup(cfg, req) && !"ORG_INDEX_RESULT".equals(table)) {
            // org_code 由内置 WIDE_TABLES 元数据推导，而不是信任 configJson 中可伪造的 subjectCol。
            throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
        }
        JsonNode metrics = cfg.path("metrics");
        if (!metrics.isArray() || metrics.isEmpty()) {
            throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
        }
        if (cfg.path("aggregation").isObject()) {
            // 有 aggregation → 聚合形态（跨主体，不再要求主体上下文参数）；无则明细行为完全不变
            return buildWideTableAggQuery(cfg, meta, table, req, limit, today, resolution);
        }
        StringBuilder cols = new StringBuilder("data_date");
        for (JsonNode m : metrics) {
            int slot = m.path("slot").asInt();
            if (slot < 1 || slot > 400) {
                throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
            }
            // 别名用指标名（剔除可破坏反引号包裹的字符），前端直接以列名展示
            String alias = m.path("metricName").asText().replaceAll("[`'\"\\\\]", "");
            cols.append(", val_").append(slot).append(" AS `").append(alias).append("`");
        }

        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT ").append(cols)
                .append(" FROM ").append(table)
                .append(" WHERE 1=1");
        if (isNamedGroup(cfg, req)) {
            appendOrgScopePredicate(sql, params, meta[0], req);
        } else {
            String subjectVal = ctxParam(req, meta[1]);
            sql.append(" AND ").append(meta[0]).append(" = ?");
            params.add(subjectVal);
        }
        sql.append(" AND version = COALESCE((SELECT current_version FROM SYS_CONTROL WHERE scope_dim = '")
                .append(meta[2]).append("' AND is_valid = 1 ORDER BY latest_data_date DESC LIMIT 1), 'V1')");
        ResolvedPeriod p = ScreenPeriodResolver.resolve(req.getPeriod(), req.getDateFrom(), req.getDateTo(), today);
        if (p.latestOnly()) {
            sql.append(" ORDER BY data_date DESC LIMIT 1");
        } else {
            sql.append(" AND data_date BETWEEN ? AND ?");
            params.add(p.from());
            params.add(p.to());
            if (p.eomOnly()) {
                sql.append(" AND data_date = LAST_DAY(data_date)");
            }
            sql.append(" ORDER BY data_date LIMIT ").append(limit);
        }
        return new BuiltQuery(sql.toString(), params);
    }

    /**
     * WIDE_TABLE 聚合形态（spec 2026-07-17 §3.3）：SELECT [维度列,] AGG(val_N) AS `指标名`...
     *
     * <p>groupBy=NONE 跨主体聚合单行 / SUBJECT 按主体列分组（排名对比，按第一个聚合列倒序）/
     * DATE 按 data_date 分组时序。三态均不再强制主体上下文参数；仍按 SYS_CONTROL 最新版本过滤；
     * LATEST=当前版本内最新 data_date 单日（跨主体不能再用 ORDER BY ... LIMIT 1）。
     * filters 的 col 仅允许主体列/data_date/已配置槽位列，op 白名单枚举，值全部 ? 绑定（IN 逗号拆分多 ?），
     * 禁止任何字符串拼接用户值。
     */
    private BuiltQuery buildWideTableAggQuery(JsonNode cfg, String[] meta, String table,
                                              ScreenDataReqDTO req, int limit, LocalDate today,
                                              BatchResolution resolution) {
        JsonNode agg = cfg.path("aggregation");
        String groupBy = agg.path("groupBy").asText();
        if (!WIDE_AGG_GROUP_BYS.contains(groupBy)) {
            throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
        }
        String aggFunc = agg.path("agg").asText();
        if (!WIDE_AGG_FUNCS.contains(aggFunc)) {
            throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
        }

        // 聚合列清单 + 已配置槽位列集合（filters 列白名单用）+ 第一个聚合别名（SUBJECT 排名排序用）
        Set<String> slotCols = new java.util.LinkedHashSet<>();
        StringBuilder aggCols = new StringBuilder();
        String firstAlias = null;
        for (JsonNode m : cfg.path("metrics")) {
            int slot = m.path("slot").asInt();
            if (slot < 1 || slot > 400) {
                throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
            }
            slotCols.add("val_" + slot);
            String alias = m.path("metricName").asText().replaceAll("[`'\"\\\\]", "");
            if (firstAlias == null) {
                firstAlias = alias;
            }
            if (!aggCols.isEmpty()) {
                aggCols.append(", ");
            }
            aggCols.append(aggFunc).append("(val_").append(slot).append(") AS `").append(alias).append("`");
        }

        String versionCond = wideVersionCondition(meta[2], null);
        String selectPrefix = switch (groupBy) {
            case "SUBJECT" -> meta[0] + ", ";
            case "DATE" -> "data_date, ";
            default -> "";
        };
        StringBuilder sql = new StringBuilder("SELECT ").append(selectPrefix).append(aggCols)
                .append(" FROM ").append(table).append(" WHERE ");

        List<Object> params = new ArrayList<>();
        if (resolution != null && resolution.version() != null) {
            sql.append("version = ?");
            params.add(resolution.version());
        } else {
            sql.append(versionCond);
        }
        if (isNamedGroup(cfg, req)) {
            appendOrgScopePredicate(sql, params, meta[0], req);
        }
        ResolvedPeriod p = ScreenPeriodResolver.resolve(req.getPeriod(), req.getDateFrom(), req.getDateTo(), today);
        boolean strictNamedGroupLatest = isStrictNamedGroupLatest(cfg, req, table, p);
        if (strictNamedGroupLatest) {
            if (resolution != null) {
                // 预检得到的日期即为本次批次身份的一部分；NULL 保持无完整批次时主查询零行。
                sql.append(" AND data_date = ?");
                params.add(resolution.dataDate());
            } else {
                appendLatestCompleteDatePredicate(sql, params, table, meta, req, today, slotCols);
            }
            appendRequiredSlotPredicates(sql, slotCols);
        } else if (p.latestOnly()) {
            sql.append(" AND data_date = (SELECT MAX(data_date) FROM ").append(table)
                    .append(" WHERE ").append(versionCond).append(")");
        } else {
            sql.append(" AND data_date BETWEEN ? AND ?");
            params.add(p.from());
            params.add(p.to());
            if (p.eomOnly()) {
                sql.append(" AND data_date = LAST_DAY(data_date)");
            }
        }
        appendWideFilters(sql, params, agg.path("filters"), meta[0], slotCols);

        switch (groupBy) {
            case "SUBJECT" -> sql.append(" GROUP BY ").append(meta[0])
                    .append(" ORDER BY `").append(firstAlias).append("` DESC LIMIT ").append(limit);
            case "DATE" -> sql.append(" GROUP BY data_date ORDER BY data_date LIMIT ").append(limit);
            default -> {
                // NONE 的聚合在 SQL 上天然会产生一行空合计；严格完整批次没有日期时必须零行。
                if (strictNamedGroupLatest) {
                    sql.append(" HAVING COUNT(*) > 0");
                }
                sql.append(" LIMIT ").append(limit);
            }
        }
        return new BuiltQuery(sql.toString(), params,
                "ORG_INDEX_RESULT".equals(table) && "SUBJECT".equals(groupBy));
    }

    private boolean isStrictNamedGroupLatest(JsonNode cfg, ScreenDataReqDTO req, String table,
                                             ResolvedPeriod period) {
        return "ORG_INDEX_RESULT".equals(table) && period.latestOnly() && isNamedGroup(cfg, req);
    }

    private String wideVersionCondition(String scopeDim, String alias) {
        String prefix = alias == null || alias.isBlank() ? "" : alias + ".";
        return prefix + "version = COALESCE((SELECT current_version FROM SYS_CONTROL WHERE scope_dim = '"
                + scopeDim + "' AND is_valid = 1 ORDER BY latest_data_date DESC LIMIT 1), 'V1')";
    }

    /**
     * 生成命名机构组最近完整日期：先排除未来日期，再按日期聚合并要求每个授权机构都有
     * 至少一行且全部配置槽位非空。机构编码和日期均为 JDBC 绑定值。
     */
    private void appendLatestCompleteDatePredicate(StringBuilder sql, List<Object> params, String table,
                                                   String[] meta, ScreenDataReqDTO req, LocalDate today,
                                                   Set<String> slotCols) {
        List<String> codes = authorizedOrgCodes(req);
        sql.append(" AND data_date = (SELECT MAX(complete_batch.data_date) FROM (SELECT candidate.data_date FROM ")
                .append(table).append(" candidate WHERE ")
                .append(wideVersionCondition(meta[2], "candidate"))
                .append(" AND candidate.data_date <= ?")
                .append(" AND candidate.org_code IN (")
                .append(String.join(", ", java.util.Collections.nCopies(codes.size(), "?")))
                .append(") GROUP BY candidate.data_date HAVING COUNT(DISTINCT CASE WHEN ");
        appendSlotNotNullCondition(sql, slotCols, "candidate");
        sql.append(" THEN candidate.org_code END) = ? AND COUNT(*) = ?");
        sql.append(") complete_batch)");
        params.add(today);
        params.addAll(codes);
        params.add(codes.size());
        params.add(codes.size());
    }

    private void appendRequiredSlotPredicates(StringBuilder sql, Set<String> slotCols) {
        for (String slotCol : slotCols) {
            sql.append(" AND ").append(slotCol).append(" IS NOT NULL");
        }
    }

    private void appendSlotNotNullCondition(StringBuilder sql, Set<String> slotCols, String alias) {
        boolean first = true;
        for (String slotCol : slotCols) {
            if (!first) {
                sql.append(" AND ");
            }
            sql.append(alias).append('.').append(slotCol).append(" IS NOT NULL");
            first = false;
        }
    }

    /** 只对 qualityPolicy 允许的两个字段做严格校验，避免未知键改变运行时语义。 */
    private void validateQualityPolicy(JsonNode cfg) {
        JsonNode policy = cfg.path("qualityPolicy");
        if (policy.isMissingNode()) {
            return;
        }
        if (!policy.isObject()) {
            throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
        }
        Set<String> allowed = Set.of("maxAgeDays", "requiredComplete");
        var fields = policy.fieldNames();
        while (fields.hasNext()) {
            if (!allowed.contains(fields.next())) {
                throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
            }
        }
        if (policy.has("maxAgeDays")) {
            JsonNode maxAgeDays = policy.path("maxAgeDays");
            if (!maxAgeDays.isIntegralNumber() || !maxAgeDays.canConvertToInt() || maxAgeDays.asInt() < 0) {
                throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
            }
        }
        if (policy.has("requiredComplete") && !policy.path("requiredComplete").isBoolean()) {
            throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
        }
    }

    /** 不可变批次适配器的严格配置：对象存在即启用，groupCode 只能来自服务端屏元数据。 */
    private void validateBatchPolicy(JsonNode cfg) {
        JsonNode policy = cfg.path("batchPolicy");
        if (policy.isMissingNode()) {
            return;
        }
        if (!policy.isObject()) {
            throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
        }
        Set<String> allowed = Set.of("maxAgeDays", "requiredComplete");
        var fields = policy.fieldNames();
        while (fields.hasNext()) {
            if (!allowed.contains(fields.next())) {
                throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
            }
        }
        if (policy.has("maxAgeDays")) {
            JsonNode maxAgeDays = policy.path("maxAgeDays");
            if (!maxAgeDays.isIntegralNumber() || !maxAgeDays.canConvertToInt() || maxAgeDays.asInt() < 0) {
                throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
            }
        }
        if (policy.has("requiredComplete") && !policy.path("requiredComplete").isBoolean()) {
            throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
        }
    }

    private QualityPolicy qualityPolicy(JsonNode cfg) {
        JsonNode policy = cfg.path("qualityPolicy");
        if (policy.isMissingNode()) {
            return new QualityPolicy(null, true);
        }
        Integer maxAgeDays = policy.has("maxAgeDays") ? policy.path("maxAgeDays").asInt() : null;
        boolean requiredComplete = !policy.has("requiredComplete")
                || policy.path("requiredComplete").asBoolean();
        return new QualityPolicy(maxAgeDays, requiredComplete);
    }

    /**
     * 命名机构组 ORG 宽表的运行时完整批次预检。其他 sourceKind、周期和范围保持原有单查询路径。
     */
    private boolean needsWideBatchResolution(String sourceKind, JsonNode cfg,
                                              ScreenDataReqDTO req, LocalDate today) {
        if (!"WIDE_TABLE".equals(sourceKind)
                || !"ORG_INDEX_RESULT".equals(cfg.path("table").asText())
                || !cfg.path("aggregation").isObject()
                || !isNamedGroup(cfg, req)) {
            return false;
        }
        return ScreenPeriodResolver.resolve(req.getPeriod(), req.getDateFrom(), req.getDateTo(), today)
                .latestOnly();
    }

    private BatchResolution resolveWideBatchIfRequired(String sourceKind, JsonNode cfg,
                                                       ScreenDataReqDTO req, LocalDate today,
                                                       Connection conn) throws SQLException {
        if (!needsWideBatchResolution(sourceKind, cfg, req, today)) {
            return null;
        }
        String[] meta = WIDE_TABLES.get("ORG_INDEX_RESULT");
        Set<String> slotCols = configuredSlotCols(cfg.path("metrics"));
        List<String> codes = authorizedOrgCodes(req);
        QualityPolicy policy = qualityPolicy(cfg);

        StringBuilder sql = new StringBuilder(
                "SELECT candidate.version AS batch_version, candidate.data_date, "
                        + "COUNT(DISTINCT CASE WHEN ");
        appendSlotNotNullCondition(sql, slotCols, "candidate");
        sql.append(" THEN candidate.org_code END) AS received_subjects FROM ORG_INDEX_RESULT candidate")
                .append(" WHERE ").append(wideVersionCondition(meta[2], "candidate"))
                .append(" AND candidate.data_date <= ? AND candidate.org_code IN (")
                .append(String.join(", ", java.util.Collections.nCopies(codes.size(), "?")))
                .append(") GROUP BY candidate.version, candidate.data_date HAVING COUNT(DISTINCT CASE WHEN ");
        appendSlotNotNullCondition(sql, slotCols, "candidate");
        sql.append(" THEN candidate.org_code END) = ? AND COUNT(*) = ? "
                + "ORDER BY candidate.data_date DESC LIMIT 1");

        try (PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            stmt.setQueryTimeout(QUERY_TIMEOUT_SEC);
            int index = 1;
            stmt.setObject(index++, java.sql.Date.valueOf(today));
            for (String code : codes) {
                stmt.setObject(index++, code);
            }
            stmt.setObject(index++, codes.size());
            stmt.setObject(index, codes.size());
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return BatchResolution.noComplete(codes.size(), policy.maxAgeDays());
                }
                String version = trimToNull(rs.getString(1));
                LocalDate dataDate = asLocalDate(rs.getObject(2));
                int received = rs.getInt(3);
                if (rs.wasNull()) {
                    received = 0;
                }
                int ageDays = dataDate == null
                        ? 0
                        : Math.max(0, (int) ChronoUnit.DAYS.between(dataDate, today));
                boolean stale = policy.maxAgeDays() != null && ageDays > policy.maxAgeDays();
                return new BatchResolution(
                        version,
                        dataDate,
                        codes.size(),
                        received,
                        policy.maxAgeDays(),
                        dataDate == null ? null : ageDays,
                        stale ? "STALE" : "COMPLETE",
                        stale ? "最近完整批次已超过允许时效" : "授权机构范围内最近完整批次");
            }
        } catch (SQLException e) {
            log.warn("[ScreenQueryEngine] 完整批次预检失败 sql={} cause={}", sql, e.getMessage());
            throw new RptException(RptErrorCode.SCREEN_DATA_QUERY_FAILED, e);
        }
    }

    private Set<String> configuredSlotCols(JsonNode metrics) {
        if (!metrics.isArray() || metrics.isEmpty()) {
            throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
        }
        Set<String> slotCols = new LinkedHashSet<>();
        for (JsonNode metric : metrics) {
            int slot = metric.path("slot").asInt();
            if (slot < 1 || slot > 400) {
                throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
            }
            slotCols.add("val_" + slot);
        }
        return slotCols;
    }

    private List<String> authorizedOrgCodes(ScreenDataReqDTO req) {
        if (req == null) {
            throw new RptException(RptErrorCode.SCREEN_SCOPE_INVALID);
        }
        List<String> source = req.getServerAuthorizedOrgCodes();
        if (source == null || source.isEmpty()) {
            source = req.getServerOrgCodes();
        }
        if (source == null || source.isEmpty()) {
            throw new RptException(RptErrorCode.SCREEN_SCOPE_INVALID);
        }
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        for (String code : source) {
            if (code == null || code.isBlank()) {
                throw new RptException(RptErrorCode.SCREEN_SCOPE_INVALID);
            }
            codes.add(code.trim());
        }
        if (codes.isEmpty()) {
            throw new RptException(RptErrorCode.SCREEN_SCOPE_INVALID);
        }
        return new ArrayList<>(codes);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private LocalDate asLocalDate(Object value) {
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate();
        }
        if (value instanceof LocalDate date) {
            return date;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime().toLocalDate();
        }
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(String.valueOf(value));
        } catch (RuntimeException ex) {
            throw new RptException(RptErrorCode.SCREEN_DATA_QUERY_FAILED, ex);
        }
    }

    private void attachQuality(ScreenDataRespDTO response, BatchResolution resolution) {
        if (resolution != null) {
            response.setQuality(resolution.toQuality());
        }
    }

    private record QualityPolicy(Integer maxAgeDays, boolean requiredComplete) {
    }

    private record BatchResolution(String version, LocalDate dataDate, int expectedSubjects,
                                   int receivedSubjects, Integer maxAgeDays, Integer ageDays,
                                   String status, String message) {

        private static BatchResolution noComplete(int expectedSubjects, Integer maxAgeDays) {
            return new BatchResolution(null, null, expectedSubjects, 0, maxAgeDays, null,
                    "NO_COMPLETE_BATCH", "授权机构范围内没有包含全部必需指标的完整批次");
        }

        private ScreenDataRespDTO.Quality toQuality() {
            String batchId = version == null || dataDate == null
                    ? null
                    : "ORG:" + version + ":" + dataDate;
            return new ScreenDataRespDTO.Quality(
                    batchId,
                    dataDate == null ? null : dataDate.toString(),
                    version,
                    status,
                    expectedSubjects,
                    receivedSubjects,
                    maxAgeDays,
                    ageDays,
                    message);
        }
    }

    /** filters 逐条追加（col/op 白名单校验，违规 43009；值全部 ? 绑定，IN 逗号拆分多 ?） */
    private void appendWideFilters(StringBuilder sql, List<Object> params, JsonNode filters,
                                   String subjectCol, Set<String> slotCols) {
        if (filters.isMissingNode() || filters.isNull()) {
            return;
        }
        if (!filters.isArray()) {
            throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
        }
        for (JsonNode f : filters) {
            String col = f.path("col").asText();
            // 列白名单：主体列 / data_date / 该数据源已配置槽位列，越界即配置非法
            if (!col.equals(subjectCol) && !"data_date".equals(col) && !slotCols.contains(col)) {
                throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
            }
            JsonNode value = f.path("value");
            if (value.isMissingNode() || value.isNull()) {
                throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
            }
            String op = f.path("op").asText();
            if ("IN".equals(op)) {
                List<String> parts = new ArrayList<>();
                for (String part : value.asText().split(",")) {
                    if (!part.trim().isEmpty()) {
                        parts.add(part.trim());
                    }
                }
                if (parts.isEmpty()) {
                    throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
                }
                sql.append(" AND ").append(col).append(" IN (")
                        .append(String.join(", ", java.util.Collections.nCopies(parts.size(), "?")))
                        .append(")");
                params.addAll(parts);
            } else {
                String sqlOp = WIDE_FILTER_OPS.get(op);
                if (sqlOp == null) {
                    throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
                }
                sql.append(" AND ").append(col).append(" ").append(sqlOp).append(" ?");
                // 统一按字符串绑定（MySQL 对数值列自动类型转换），杜绝任何拼接
                params.add(value.asText());
            }
        }
    }

    private BuiltQuery buildKpiQuery(JsonNode cfg, ScreenDataReqDTO req, int limit, LocalDate today) {
        String cycleType = cfg.path("cycleType").asText("MONTHLY");
        if (!KPI_CYCLE_TYPES.contains(cycleType)) {
            throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
        }
        String empId = ctxParam(req, "empId");
        ResolvedPeriod p = ScreenPeriodResolver.resolve(req.getPeriod(), req.getDateFrom(), req.getDateTo(), today);

        // 每个周期日取 as_of_date 最新一次计算结果
        StringBuilder sql = new StringBuilder(
                "SELECT cycle_date AS data_date, kpi_total_score AS `KPI总分` FROM ("
                        + "SELECT cycle_date, kpi_total_score, "
                        + "ROW_NUMBER() OVER (PARTITION BY cycle_date ORDER BY as_of_date DESC) rn "
                        + "FROM KPI_RESULT WHERE emp_id = ? AND cycle_type = ?) t WHERE rn = 1");
        List<Object> params = new ArrayList<>(List.of(empId, cycleType));
        if (p.latestOnly()) {
            sql.append(" ORDER BY cycle_date DESC LIMIT 1");
        } else {
            sql.append(" AND cycle_date BETWEEN ? AND ?");
            params.add(p.from());
            params.add(p.to());
            sql.append(" ORDER BY cycle_date LIMIT ").append(limit);
        }
        return new BuiltQuery(sql.toString(), params);
    }

    /** KPI_DETAIL 主体类型 → 上下文参数名（EMP→empId、ORG→orgCode），subject_id 按该参数取值 */
    private static final Map<String, String> KPI_DETAIL_SUBJECT_PARAMS = Map.of("EMP", "empId", "ORG", "orgCode");

    /** 完成率算式：target=0 经 NULLIF 置 NULL（前端显示"—"），快照列与 TREND completeRate 透视共用 */
    private static final String KPI_COMPLETE_RATE_EXPR =
            "ROUND(s.actual_value / NULLIF(s.target_value, 0) * 100, 2)";

    /**
     * KPI 细项引导式（PERF_KPI_SCORE T+1 每日快照，spec §3.1）：
     * SNAPSHOT=最新快照日全部细项一行一项（目标/实际/权重/得分/完成率/缺口现算）；
     * TREND=按周期透视，data_date × 每个配置细项一列（MAX(CASE WHEN metric_code=?)，列值 score 或完成率）。
     */
    private BuiltQuery buildKpiDetailQuery(JsonNode cfg, ScreenDataReqDTO req, int limit, LocalDate today) {
        String schemeCode = cfg.path("schemeCode").asText();
        if (schemeCode.isBlank()) {
            throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
        }
        if ("NAMED_GROUP".equalsIgnoreCase(cfg.path("scopeMode").asText())
                || (req != null && req.isNamedGroup())) {
            return buildNamedKpiDetailSnapshot(cfg, req, limit);
        }
        String subjectType = cfg.path("subjectType").asText();
        String ctxName = KPI_DETAIL_SUBJECT_PARAMS.get(subjectType);
        if (ctxName == null) {
            throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
        }
        String mode = cfg.path("mode").asText();
        return switch (mode) {
            case "SNAPSHOT" -> buildKpiDetailSnapshot(schemeCode, subjectType, ctxParam(req, ctxName), limit);
            case "TREND" -> buildKpiDetailTrend(cfg, schemeCode, subjectType, ctxParam(req, ctxName), req, limit, today);
            default -> throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
        };
    }

    /**
     * 命名机构组 KPI_DETAIL 的固定 ORG 快照查询。
     *
     * <p>外层只绑定 serverOrgCodes（其已由 serverAuthorizedOrgCodes 收口），共同日期子查询
     * 使用完整授权集合；因此某机构缺数据时只返回有数据的交集行，并通过 quality 暴露 PARTIAL，
     * 不按机构各取 latest，也不补造缺失机构行。</p>
     */
    private BuiltQuery buildNamedKpiDetailSnapshot(JsonNode cfg, ScreenDataReqDTO req, int limit) {
        if (!isNamedKpiDetailConfig("KPI_DETAIL", cfg)) {
            throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
        }
        String schemeCode = cfg.path("schemeCode").asText();
        List<String> outputCodes = namedGroupOutputOrgCodes(req);
        List<String> authorizedCodes = namedGroupAuthorizedOrgCodes(req);
        List<String> metricCodes = namedKpiMetricCodes(cfg);

        StringBuilder sql = new StringBuilder(
                "SELECT s.subject_id AS org_code, o.ORG_NAME AS org_name, s.data_date AS data_date, "
                        + "s.metric_code AS metric_code, COALESCE(d.metric_name, s.metric_code) AS metric_name, "
                        + "s.actual_value AS actual_value, s.target_value AS target_value, "
                        + "s.weight AS weight, s.score AS score, "
                        + KPI_COMPLETE_RATE_EXPR + " AS completion_rate, "
                        + "s.target_value - s.actual_value AS gap, "
                        + "CONCAT(COALESCE(o.ORG_NAME, s.subject_id), ' ', "
                        + "COALESCE(d.metric_name, s.metric_code), '目标待跟进') AS attention_label, "
                        + "CASE WHEN s.actual_value IS NULL OR s.target_value IS NULL "
                        + "OR s.target_value <= 0 THEN NULL "
                        + "WHEN s.actual_value < s.target_value THEN 1 ELSE 0 END AS attention_count "
                        + "FROM PERF_KPI_SCORE s "
                        + "LEFT JOIN PERF_METRIC_DEF d ON d.metric_code = s.metric_code AND d.deleted = 0 "
                        + "LEFT JOIN EXT_ORG_INFO o ON o.ORG_CODE = s.subject_id "
                        + "WHERE s.scheme_code = ? AND s.subject_type = ? AND s.subject_id IN (");
        List<Object> params = new ArrayList<>();
        params.add(schemeCode);
        params.add("ORG");
        appendPlaceholders(sql, outputCodes.size());
        sql.append(") AND s.data_date = (SELECT MAX(common.data_date) FROM PERF_KPI_SCORE common "
                + "WHERE common.scheme_code = ? AND common.subject_type = ? AND common.subject_id IN (");
        params.addAll(outputCodes);
        params.add(schemeCode);
        params.add("ORG");
        appendPlaceholders(sql, authorizedCodes.size());
        sql.append(")");
        params.addAll(authorizedCodes);
        if (!metricCodes.isEmpty()) {
            sql.append(" AND common.metric_code IN (");
            appendPlaceholders(sql, metricCodes.size());
            sql.append(")");
            params.addAll(metricCodes);
        }
        sql.append(")");
        if (!metricCodes.isEmpty()) {
            sql.append(" AND s.metric_code IN (");
            appendPlaceholders(sql, metricCodes.size());
            sql.append(")");
            params.addAll(metricCodes);
        }
        sql.append(" ORDER BY s.subject_id, s.metric_code LIMIT ").append(limit);
        return new BuiltQuery(sql.toString(), params);
    }

    private void appendPlaceholders(StringBuilder sql, int count) {
        if (count <= 0) {
            throw new RptException(RptErrorCode.SCREEN_SCOPE_INVALID);
        }
        sql.append(String.join(", ", java.util.Collections.nCopies(count, "?")));
    }

    private boolean isNamedKpiDetail(JsonNode cfg, String sourceKind, ScreenDataReqDTO req) {
        return isNamedGroup(cfg, req) && isNamedKpiDetailConfig(sourceKind, cfg);
    }

    private BuiltQuery buildM98StatQuery(JsonNode cfg, ScreenDataReqDTO req, int limit, LocalDate today) {
        if (!isNamedM98StatConfig("M98_STAT", cfg)) {
            throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
        }
        List<String> authorized = namedGroupAuthorizedOrgCodes(req);
        List<String> output = namedGroupOutputOrgCodes(req);
        M98StatQuery.ScreenQueryPlan plan = m98StatQuery.previewPlan(
                cfg.path("profile").asText(), authorized, output, today, limit);
        return new BuiltQuery(plan.sql(), plan.params());
    }

    private BuiltQuery buildFreeReportQuery(JsonNode cfg, ScreenDataReqDTO req, int limit) {
        FreeReportScreenPolicy.validateConfig(cfg);
        List<String> authorized = namedGroupAuthorizedOrgCodes(req);
        List<String> output = namedGroupOutputOrgCodes(req);
        FreeReportScreenQuery.ScreenQueryPlan plan = freeReportScreenQuery.previewPlan(
                cfg, authorized, output, req == null ? null : req.getServerRequestedOrgCode(), limit);
        return new BuiltQuery(plan.sql(), plan.params());
    }

    /** 命名组 KPI_DETAIL 的精确组合；旧 SUBJECT/EMP/TREND 路径仍由既有分支处理。 */
    private boolean isNamedKpiDetailConfig(String sourceKind, JsonNode cfg) {
        return "KPI_DETAIL".equals(sourceKind)
                && cfg.path("schemaVersion").isIntegralNumber()
                && cfg.path("schemaVersion").asInt() == 2
                && "NAMED_GROUP".equalsIgnoreCase(cfg.path("scopeMode").asText())
                && "ORG".equals(cfg.path("subjectType").asText())
                && "SNAPSHOT".equals(cfg.path("mode").asText())
                && !cfg.path("schemeCode").asText().isBlank();
    }

    private boolean isNamedM98Stat(JsonNode cfg, String sourceKind, ScreenDataReqDTO req) {
        return isNamedGroup(cfg, req) && isNamedM98StatConfig(sourceKind, cfg);
    }

    private boolean isNamedM98StatConfig(String sourceKind, JsonNode cfg) {
        return M98StatPolicy.SOURCE_KIND.equals(sourceKind) && M98StatPolicy.isStrictConfig(cfg);
    }

    private List<String> namedKpiMetricCodes(JsonNode cfg) {
        JsonNode metrics = cfg.path("metrics");
        if (metrics.isMissingNode() || metrics.isNull()) {
            return List.of();
        }
        if (!metrics.isArray() || metrics.isEmpty()) {
            throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
        }
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        for (JsonNode metric : metrics) {
            String code = metric.path("metricCode").asText();
            if (code.isBlank()) {
                throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
            }
            codes.add(code.trim());
        }
        return new ArrayList<>(codes);
    }

    /** SNAPSHOT：主体最新 data_date 当日全部细项，列清单固定（一行一细项） */
    private BuiltQuery buildKpiDetailSnapshot(String schemeCode, String subjectType, String subjectId, int limit) {
        String sql = "SELECT s.metric_code, COALESCE(d.metric_name, s.metric_code) AS `细项名称`, "
                + "s.target_value AS `目标值`, s.actual_value AS `实际值`, s.weight AS `权重`, s.score AS `得分`, "
                + KPI_COMPLETE_RATE_EXPR + " AS `完成率`, "
                + "s.target_value - s.actual_value AS `缺口` "
                + "FROM PERF_KPI_SCORE s "
                // 已删除的指标定义不取名（回落 metric_code），条件放 ON 侧保住 LEFT 语义
                + "LEFT JOIN PERF_METRIC_DEF d ON d.metric_code = s.metric_code AND d.deleted = 0 "
                + "WHERE s.scheme_code = ? AND s.subject_type = ? AND s.subject_id = ? "
                + "AND s.data_date = (SELECT MAX(data_date) FROM PERF_KPI_SCORE"
                + " WHERE scheme_code = ? AND subject_type = ? AND subject_id = ?) "
                + "ORDER BY s.metric_code LIMIT " + limit;
        return new BuiltQuery(sql, new ArrayList<>(List.of(
                schemeCode, subjectType, subjectId, schemeCode, subjectType, subjectId)));
    }

    /** TREND：data_date + 每个配置细项一列（metricCode 全部 ? 绑定，列名=metricName） */
    private BuiltQuery buildKpiDetailTrend(JsonNode cfg, String schemeCode, String subjectType, String subjectId,
                                           ScreenDataReqDTO req, int limit, LocalDate today) {
        JsonNode metrics = cfg.path("metrics");
        if (!metrics.isArray() || metrics.isEmpty()) {
            throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
        }
        String valueCol = cfg.path("valueCol").asText("score");
        String expr = switch (valueCol) {
            case "score" -> "s.score";
            case "completeRate" -> KPI_COMPLETE_RATE_EXPR;
            default -> throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
        };
        StringBuilder cols = new StringBuilder("s.data_date");
        List<Object> params = new ArrayList<>();
        for (JsonNode m : metrics) {
            String metricCode = m.path("metricCode").asText();
            if (metricCode.isBlank()) {
                throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
            }
            // 列名用细项名快照（保存时已强制非空），剔除可破坏反引号包裹的字符，兜底回落 metricCode
            String alias = m.path("metricName").asText().replaceAll("[`'\"\\\\]", "");
            if (alias.isBlank()) {
                alias = metricCode;
            }
            cols.append(", MAX(CASE WHEN s.metric_code = ? THEN ").append(expr)
                    .append(" END) AS `").append(alias).append("`");
            params.add(metricCode);
        }
        ResolvedPeriod p = ScreenPeriodResolver.resolve(req.getPeriod(), req.getDateFrom(), req.getDateTo(), today);
        StringBuilder sql = new StringBuilder("SELECT ").append(cols)
                .append(" FROM PERF_KPI_SCORE s")
                .append(" WHERE s.scheme_code = ? AND s.subject_type = ? AND s.subject_id = ?");
        params.add(schemeCode);
        params.add(subjectType);
        params.add(subjectId);
        if (p.latestOnly()) {
            sql.append(" GROUP BY s.data_date ORDER BY s.data_date DESC LIMIT 1");
        } else {
            sql.append(" AND s.data_date BETWEEN ? AND ?");
            params.add(p.from());
            params.add(p.to());
            sql.append(" GROUP BY s.data_date ORDER BY s.data_date LIMIT ").append(limit);
        }
        return new BuiltQuery(sql.toString(), params);
    }

    private BuiltQuery buildCustomQuery(JsonNode cfg, ScreenDataReqDTO req, int limit, LocalDate today) {
        String template = cfg.path("sql").asText();
        ResolvedPeriod p = ScreenPeriodResolver.resolve(req.getPeriod(), req.getDateFrom(), req.getDateTo(), today);

        Map<String, Object> vals = new HashMap<>();
        vals.put("orgCode", req.getContextParams() == null ? null : req.getContextParams().get("orgCode"));
        vals.put("empId", req.getContextParams() == null ? null : req.getContextParams().get("empId"));
        vals.put("dateFrom", p.from());
        vals.put("dateTo", p.to());

        List<Object> params = new ArrayList<>();
        validateCustomSql(template);
        ScreenSqlTemplate.Parsed parsed = ScreenSqlTemplate.parse(template);
        for (String name : parsed.paramNames()) {
            Object v = vals.get(name);
            if (v == null) {
                // SQL 用到了 orgCode/empId 占位但上下文没传 → 入参校验失败（43010），非 SQL 执行失败（43008）
                throw new RptException(RptErrorCode.SCREEN_CTX_PARAM_MISSING);
            }
            params.add(v);
        }
        String jdbcSql = parsed.jdbcSql();
        String sql = "SELECT * FROM (" + jdbcSql + ") rpt_scr_q LIMIT " + limit;
        return new BuiltQuery(sql, params);
    }

    private boolean isNamedGroup(JsonNode cfg, ScreenDataReqDTO req) {
        return "NAMED_GROUP".equalsIgnoreCase(cfg.path("scopeMode").asText())
                || (req != null && req.isNamedGroup());
    }

    /** 追加服务端机构范围谓词，机构编码永不拼接到 SQL。 */
    private void appendOrgScopePredicate(StringBuilder sql, List<Object> params,
                                         String subjectCol, ScreenDataReqDTO req) {
        if (!"org_code".equals(subjectCol)) {
            throw new RptException(RptErrorCode.SCREEN_SCOPE_INVALID);
        }
        List<String> codes = outputOrgCodes(req);
        sql.append(" AND ").append(subjectCol).append(" IN (")
                .append(String.join(", ", java.util.Collections.nCopies(codes.size(), "?")))
                .append(")");
        params.addAll(codes);
    }

    /** 主查询可按服务端已核验的单机构条件收窄；完整性预检始终调用 authorizedOrgCodes。 */
    private List<String> outputOrgCodes(ScreenDataReqDTO req) {
        List<String> codes = req == null ? null : req.getServerOrgCodes();
        if (codes == null || codes.isEmpty()) {
            return authorizedOrgCodes(req);
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String code : codes) {
            if (code == null || code.isBlank()) {
                throw new RptException(RptErrorCode.SCREEN_SCOPE_INVALID);
            }
            normalized.add(code.trim());
        }
        if (normalized.isEmpty()) {
            throw new RptException(RptErrorCode.SCREEN_SCOPE_INVALID);
        }
        List<String> authorized = authorizedOrgCodes(req);
        if (!authorized.containsAll(normalized)) {
            throw new RptException(RptErrorCode.SCREEN_SCOPE_INVALID);
        }
        return new ArrayList<>(normalized);
    }

    /** 命名组 KPI 日期选择使用完整服务端授权集合，缺失或空集合一律拒绝。 */
    List<String> namedGroupAuthorizedOrgCodes(ScreenDataReqDTO req) {
        if (req == null) {
            throw new RptException(RptErrorCode.SCREEN_SCOPE_INVALID);
        }
        return requiredOrgCodes(req.getServerAuthorizedOrgCodes());
    }

    /** 命名组 KPI 外层结果只接受 serverOrgCodes，并验证其为完整授权集合的子集。 */
    List<String> namedGroupOutputOrgCodes(ScreenDataReqDTO req) {
        List<String> authorized = namedGroupAuthorizedOrgCodes(req);
        List<String> output = requiredOrgCodes(req == null ? null : req.getServerOrgCodes());
        if (!authorized.containsAll(output)) {
            throw new RptException(RptErrorCode.SCREEN_SCOPE_INVALID);
        }
        return output;
    }

    private List<String> requiredOrgCodes(Collection<String> source) {
        if (source == null || source.isEmpty()) {
            throw new RptException(RptErrorCode.SCREEN_SCOPE_INVALID);
        }
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        for (String code : source) {
            if (code == null || code.isBlank()) {
                throw new RptException(RptErrorCode.SCREEN_SCOPE_INVALID);
            }
            codes.add(code.trim());
        }
        if (codes.isEmpty()) {
            throw new RptException(RptErrorCode.SCREEN_SCOPE_INVALID);
        }
        return new ArrayList<>(codes);
    }

    /** 从命名组 KPI 固定结果列推导质量摘要，不为缺失机构创建任何虚假行。 */
    private void attachKpiDetailQuality(ScreenDataRespDTO response, List<String> authorizedCodes) {
        List<String> columns = response.getColumns() == null ? List.of() : response.getColumns();
        int orgIndex = columns.indexOf("org_code");
        int dateIndex = columns.indexOf("data_date");
        LinkedHashSet<String> receivedCodes = new LinkedHashSet<>();
        String dataDate = null;
        if (response.getRows() != null) {
            for (List<Object> row : response.getRows()) {
                if (row == null) {
                    continue;
                }
                if (orgIndex >= 0 && orgIndex < row.size()) {
                    String code = normalizeOrgCode(row.get(orgIndex));
                    if (code != null) {
                        receivedCodes.add(code);
                    }
                }
                if (dataDate == null && dateIndex >= 0 && dateIndex < row.size() && row.get(dateIndex) != null) {
                    String value = String.valueOf(row.get(dateIndex)).trim();
                    if (!value.isEmpty()) {
                        dataDate = value;
                    }
                }
            }
        }
        List<String> missing = authorizedCodes.stream()
                .filter(code -> !receivedCodes.contains(code)).toList();
        int expected = authorizedCodes.size();
        int received = receivedCodes.size();
        String status = expected == received ? "COMPLETE" : "PARTIAL";
        ScreenDataRespDTO.Quality quality = new ScreenDataRespDTO.Quality(
                null, dataDate, null, status, expected, received, null, null,
                "COMPLETE".equals(status) ? null : "仅返回授权机构与 KPI 数据的交集");
        quality.setSelectedComplete("COMPLETE".equals(status));
        quality.setMixedPeriod(false);
        quality.setMissing(List.of());
        quality.setMissingSubjects(missing);
        quality.setNewerIncomplete(List.of());
        response.setQuality(quality);
    }

    private String ctxParam(ScreenDataReqDTO req, String name) {
        String v = req.getContextParams() == null ? null : req.getContextParams().get(name);
        if (v == null || v.isBlank()) {
            // 必填上下文参数（empId/orgCode/custNo）缺失属入参校验失败（43010），前端据此渲染引导态而非当作 SQL 执行失败（43008）
            throw new RptException(RptErrorCode.SCREEN_CTX_PARAM_MISSING);
        }
        return v;
    }

    private ScreenDataRespDTO execute(BuiltQuery q) {
        try (Connection conn = readOnlyDataSource.getConnection()) {
            return execute(q, conn);
        } catch (SQLException e) {
            log.warn("[ScreenQueryEngine] 取数失败 sql={} cause={}", q.sql(), e.getMessage());
            throw new RptException(RptErrorCode.SCREEN_DATA_QUERY_FAILED, e);
        }
    }

    private ScreenDataRespDTO execute(BuiltQuery q, Connection conn) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(q.sql())) {
            stmt.setQueryTimeout(QUERY_TIMEOUT_SEC);
            for (int i = 0; i < q.params().size(); i++) {
                Object v = q.params().get(i);
                if (v instanceof LocalDate d) {
                    stmt.setObject(i + 1, java.sql.Date.valueOf(d));
                } else {
                    stmt.setObject(i + 1, v);
                }
            }
            try (ResultSet rs = stmt.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData();
                List<String> columns = new ArrayList<>();
                for (int i = 1; i <= md.getColumnCount(); i++) {
                    columns.add(md.getColumnLabel(i));
                }
                List<List<Object>> rows = new ArrayList<>();
                while (rs.next()) {
                    List<Object> row = new ArrayList<>(columns.size());
                    for (int i = 1; i <= columns.size(); i++) {
                        row.add(normalize(rs.getObject(i)));
                    }
                    rows.add(row);
                }
                ScreenDataRespDTO response = new ScreenDataRespDTO(columns, rows);
                return q.enrichOrgNames() ? appendOrgNames(response) : response;
            }
        }
    }

    /**
     * 为机构主体聚合结果补充展示名称。
     *
     * <p>机构编码先按结果行顺序去空去重，再通过公开 OrgApi 一次批量查询；未命中的编码保持名称为
     * {@code null}，不把编码当作名称兜底。该补充发生在统一执行出口，正式取数和试跑/探查共用。</p>
     */
    private ScreenDataRespDTO appendOrgNames(ScreenDataRespDTO response) {
        try {
            List<String> columns = response.getColumns();
            if (columns == null) {
                return response;
            }
            int orgCodeIndex = columns.indexOf("org_code");
            if (orgCodeIndex < 0) {
                return response;
            }

            LinkedHashSet<String> distinctCodes = new LinkedHashSet<>();
            if (response.getRows() != null) {
                for (List<Object> row : response.getRows()) {
                    if (row != null && orgCodeIndex < row.size()) {
                        String code = normalizeOrgCode(row.get(orgCodeIndex));
                        if (code != null) {
                            distinctCodes.add(code);
                        }
                    }
                }
            }

            Map<String, String> orgNames = new LinkedHashMap<>();
            if (!distinctCodes.isEmpty()) {
                List<String> codes = new ArrayList<>(distinctCodes);
                List<OrgDTO> orgs = orgApi.getOrgsByCodes(codes);
                if (orgs != null) {
                    for (OrgDTO org : orgs) {
                        if (org == null) {
                            continue;
                        }
                        String code = normalizeOrgCode(org.getOrgCode());
                        if (code != null) {
                            orgNames.putIfAbsent(code, org.getOrgName());
                        }
                    }
                }
            }

            int orgNameIndex = orgCodeIndex + 1;
            List<String> enrichedColumns = new ArrayList<>(columns);
            enrichedColumns.add(orgNameIndex, "org_name");
            List<List<Object>> enrichedRows = new ArrayList<>();
            if (response.getRows() != null) {
                for (List<Object> sourceRow : response.getRows()) {
                    List<Object> row = sourceRow == null
                            ? new ArrayList<>()
                            : new ArrayList<>(sourceRow);
                    String code = row.size() > orgCodeIndex
                            ? normalizeOrgCode(row.get(orgCodeIndex))
                            : null;
                    row.add(Math.min(orgNameIndex, row.size()), code == null ? null : orgNames.get(code));
                    enrichedRows.add(row);
                }
            }
            response.setColumns(enrichedColumns);
            response.setRows(enrichedRows);
            return response;
        } catch (Exception e) {
            log.warn("[ScreenQueryEngine] 机构名称补充失败 cause={}", e.getMessage());
            throw new RptException(RptErrorCode.SCREEN_DATA_QUERY_FAILED, e);
        }
    }

    private String normalizeOrgCode(Object value) {
        if (value == null) {
            return null;
        }
        String code = String.valueOf(value).trim();
        return code.isEmpty() ? null : code;
    }

    /** JDBC 值 → JSON 友好值（日期/时间戳转字符串，数值保持原样） */
    private Object normalize(Object v) {
        if (v instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime().toString();
        }
        if (v instanceof java.sql.Date d) {
            return d.toLocalDate().toString();
        }
        if (v instanceof LocalDateTime ldt) {
            return ldt.toString();
        }
        return v;
    }
}
