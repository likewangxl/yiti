package com.bank.branch.platform.report.service.screen;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.governance.api.AuditApi;
import com.bank.branch.platform.governance.api.dto.AuditLogCmd;
import com.bank.branch.platform.report.dto.req.ScreenDataReqDTO;
import com.bank.branch.platform.report.dto.req.ScreenDatasourceSaveReqDTO;
import com.bank.branch.platform.report.dto.req.ScreenTryRunReqDTO;
import com.bank.branch.platform.report.dto.resp.ScreenDataRespDTO;
import com.bank.branch.platform.report.dto.resp.ScreenDatasourceRespDTO;
import com.bank.branch.platform.report.dto.resp.ScreenKpiSchemeRespDTO;
import com.bank.branch.platform.report.entity.PerfKpiScheme;
import com.bank.branch.platform.report.entity.RptScreenDatasource;
import com.bank.branch.platform.report.enums.RptErrorCode;
import com.bank.branch.platform.report.exception.RptException;
import com.bank.branch.platform.report.mapper.ScreenKpiSchemeMapper;
import com.bank.branch.platform.report.mapper.RptScreenBlockMapper;
import com.bank.branch.platform.report.mapper.RptScreenDatasourceMapper;
import com.bank.branch.platform.report.support.ScreenConfigSchema;
import com.bank.branch.platform.report.support.ScreenMetricSlotDao;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 大屏数据源管理服务实现.
 *
 * <p>审计：CUSTOM_SQL 的保存与所有试跑属高危操作，手工双写 governance 审计
 * （模块惯例，参考 SqlProbeServiceImpl.safelyAudit，非 @AuditLog 切面）。
 */
@Slf4j
@Service
public class ScreenDatasourceServiceImpl implements ScreenDatasourceService {

    /** 宽表 → [subjectCol, subjectParam, baseDim] */
    private static final Map<String, String[]> WIDE_TABLES = Map.of(
            "EMP_INDEX_RESULT", new String[]{"emp_id", "empId", "EMP"},
            "ORG_INDEX_RESULT", new String[]{"org_code", "orgCode", "ORG"},
            "CUST_INDEX_RESULT", new String[]{"cust_no", "custNo", "CUST"});

    private static final Set<String> KPI_CYCLE_TYPES = Set.of("MONTHLY", "QUARTERLY");

    /** KPI_DETAIL 主体类型（EMP=个人屏 empId / ORG=支行屏 orgCode） */
    private static final Set<String> KPI_DETAIL_SUBJECT_TYPES = Set.of("EMP", "ORG");

    /** KPI_DETAIL TREND 透视值列（默认 score） */
    private static final Set<String> KPI_DETAIL_VALUE_COLS = Set.of("score", "completeRate");

    /** fieldMeta 角色枚举（spec 2026-07-17 §3.2，全 source_kind 通用） */
    private static final Set<String> FIELD_META_ROLES = Set.of("DIM", "METRIC");

    /** scopeMode 枚举（spec 2026-07-17 §4，全 source_kind 通用，缺省 SUBJECT 由 ScreenConfigSchema 补） */
    private static final Set<String> SCOPE_MODES = Set.of("SUBJECT", "GLOBAL");

    /** WIDE_TABLE 聚合 groupBy 枚举（spec 2026-07-17 §3.3） */
    private static final Set<String> WIDE_AGG_GROUP_BYS = Set.of("NONE", "SUBJECT", "DATE");

    /** WIDE_TABLE 聚合函数枚举 */
    private static final Set<String> WIDE_AGG_FUNCS = Set.of("SUM", "AVG", "MAX", "MIN", "COUNT");

    /** WIDE_TABLE filters 操作符枚举 */
    private static final Set<String> WIDE_FILTER_OPS = Set.of("EQ", "NE", "IN", "GT", "GE", "LT", "LE");

    private final RptScreenDatasourceMapper dsMapper;
    private final RptScreenBlockMapper blockMapper;
    private final ScreenQueryEngine engine;
    private final ScreenMetricSlotDao slotDao;
    private final ScreenKpiSchemeMapper kpiSchemeMapper;
    private final CurrentUserApi currentUserApi;
    private final AuditApi auditApi;
    private final ScreenDataScopeGuard scopeGuard;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ScreenDatasourceServiceImpl(RptScreenDatasourceMapper dsMapper,
                                       RptScreenBlockMapper blockMapper,
                                       ScreenQueryEngine engine,
                                       ScreenMetricSlotDao slotDao,
                                       ScreenKpiSchemeMapper kpiSchemeMapper,
                                       CurrentUserApi currentUserApi,
                                       AuditApi auditApi,
                                       ScreenDataScopeGuard scopeGuard) {
        this.dsMapper = dsMapper;
        this.blockMapper = blockMapper;
        this.engine = engine;
        this.slotDao = slotDao;
        this.kpiSchemeMapper = kpiSchemeMapper;
        this.currentUserApi = currentUserApi;
        this.auditApi = auditApi;
        this.scopeGuard = scopeGuard;
    }

    @Override
    public List<ScreenDatasourceRespDTO> list(String dsType, String keyword) {
        LambdaQueryWrapper<RptScreenDatasource> qw = new LambdaQueryWrapper<>();
        if (dsType != null && !dsType.isBlank()) {
            qw.eq(RptScreenDatasource::getDsType, dsType);
        }
        if (keyword != null && !keyword.isBlank()) {
            qw.like(RptScreenDatasource::getDsName, keyword);
        }
        qw.orderByDesc(RptScreenDatasource::getId);
        return dsMapper.selectList(qw).stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(ScreenDatasourceSaveReqDTO req) {
        RptScreenDatasource e = new RptScreenDatasource();
        applyValidated(e, req);
        e.setDsCode("SCRDS_" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 8).toUpperCase(Locale.ROOT));
        e.setCreatedBy(currentUserApi.getCurrentEmpId());
        dsMapper.insert(e);
        if ("CUSTOM_SQL".equals(e.getSourceKind())) {
            safelyAudit("WRITE", "saveDs id=" + e.getId() + ", name=" + e.getDsName(), req.getReason());
        }
        return e.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, ScreenDatasourceSaveReqDTO req) {
        RptScreenDatasource e = requireDs(id);
        applyValidated(e, req);
        dsMapper.updateById(e);
        if ("CUSTOM_SQL".equals(e.getSourceKind())) {
            safelyAudit("WRITE", "updateDs id=" + id, req.getReason());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireDs(id);
        if (blockMapper.countByDsId(id) > 0) {
            throw new RptException(RptErrorCode.SCREEN_DS_IN_USE);
        }
        dsMapper.deleteById(id);
    }

    @Override
    public ScreenDataRespDTO tryRun(ScreenTryRunReqDTO req) {
        // 试跑也走完整校验（含 CUSTOM_SQL 白名单），随后 LIMIT 10 执行
        RptScreenDatasource probe = new RptScreenDatasource();
        ScreenDatasourceSaveReqDTO fake = new ScreenDatasourceSaveReqDTO();
        fake.setDsName("__tryrun__");
        fake.setDsType(req.getDsType());
        fake.setSourceKind(req.getSourceKind());
        fake.setConfigJson(req.getConfigJson());
        applyValidated(probe, fake);

        ScreenDataReqDTO dataReq = new ScreenDataReqDTO();
        dataReq.setPeriod(req.getPeriod());
        dataReq.setDateFrom(req.getDateFrom());
        dataReq.setDateTo(req.getDateTo());
        dataReq.setContextParams(req.getContextParams());
        // try/finally 兜底：engine.tryRun 执行阶段抛异常也必须留痕，不能只审计成功路径
        boolean success = false;
        try {
            ScreenDataRespDTO resp = engine.tryRun(probe.getSourceKind(), probe.getConfigJson(), dataReq);
            success = true;
            return resp;
        } finally {
            String detail = "tryRun kind=" + req.getSourceKind() + ", result=" + (success ? "SUCCESS" : "FAILED");
            safelyAudit("EXECUTE_SQL", detail, req.getReason());
        }
    }

    @Override
    public List<ScreenKpiSchemeRespDTO> listKpiSchemes() {
        // KPI_DETAIL 数据源配置下拉：仅 ACTIVE 方案，按编码稳定排序
        return kpiSchemeMapper.selectList(new LambdaQueryWrapper<PerfKpiScheme>()
                        .eq(PerfKpiScheme::getStatus, "ACTIVE")
                        .orderByAsc(PerfKpiScheme::getSchemeCode))
                .stream()
                .map(s -> new ScreenKpiSchemeRespDTO(s.getSchemeCode(), s.getSchemeName()))
                .collect(Collectors.toList());
    }

    @Override
    public ScreenDataRespDTO queryData(ScreenDataReqDTO req) {
        RptScreenDatasource ds = dsMapper.selectById(req.getDsId());
        if (ds == null || "DISABLED".equals(ds.getStatus())) {
            throw new RptException(RptErrorCode.SCREEN_DS_NOT_FOUND);
        }
        // DATA_SCOPE 行级权限（spec 2026-07-17 §4）：执行前校验主体参数越权，拒绝抛 RPT-43013；
        // try-run（独立高危资源）不走本方法，维持现状不套行级过滤
        scopeGuard.check(ds, req);
        return engine.query(ds, req);
    }

    // ===== 内部 =====

    private RptScreenDatasource requireDs(Long id) {
        RptScreenDatasource e = dsMapper.selectById(id);
        if (e == null) {
            throw new RptException(RptErrorCode.SCREEN_DS_NOT_FOUND);
        }
        return e;
    }

    /** 按 sourceKind 归一化 + 校验，结果写入实体（save/update/tryRun 共用） */
    private void applyValidated(RptScreenDatasource e, ScreenDatasourceSaveReqDTO req) {
        String kind = req.getSourceKind();
        JsonNode cfg = readJson(req.getConfigJson());
        // fieldMeta / scopeMode 对全 source_kind 通用（spec 2026-07-17 §3.2/§4），统一在分派前校验
        validateFieldMeta(cfg);
        validateScopeMode(cfg);
        switch (kind == null ? "" : kind) {
            case "WIDE_TABLE" -> {
                String table = cfg.path("table").asText();
                String[] meta = WIDE_TABLES.get(table);
                if (meta == null) {
                    throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
                }
                List<String> codes = new ArrayList<>();
                cfg.path("metrics").forEach(m -> codes.add(m.path("metricCode").asText()));
                if (codes.isEmpty()) {
                    throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
                }
                Map<String, ScreenMetricSlotDao.MetricSlot> found = slotDao.selectByCodes(codes).stream()
                        .collect(Collectors.toMap(ScreenMetricSlotDao.MetricSlot::metricCode, Function.identity()));
                // 全部存在 + 维度匹配，重写 metrics 快照
                ObjectNode newCfg = objectMapper.createObjectNode();
                newCfg.put("table", table);
                newCfg.put("subjectCol", meta[0]);
                newCfg.put("subjectParam", meta[1]);
                ArrayNode arr = newCfg.putArray("metrics");
                Set<String> slotCols = new LinkedHashSet<>();
                for (String code : codes) {
                    ScreenMetricSlotDao.MetricSlot slot = found.get(code);
                    if (slot == null || slot.valSlot() == null || !meta[2].equals(slot.baseDim())) {
                        throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
                    }
                    ObjectNode m = arr.addObject();
                    m.put("metricCode", slot.metricCode());
                    m.put("metricName", slot.metricName());
                    m.put("slot", slot.valSlot());
                    slotCols.add("val_" + slot.valSlot());
                }
                // schemaVersion/fieldMeta/scopeMode/aggregation 必须随重写后的 config 落库（否则执行期丢失）
                if (cfg.hasNonNull("schemaVersion")) {
                    newCfg.set("schemaVersion", cfg.get("schemaVersion"));
                }
                if (cfg.hasNonNull("fieldMeta")) {
                    newCfg.set("fieldMeta", cfg.get("fieldMeta"));
                }
                if (cfg.hasNonNull("scopeMode")) {
                    // readJson 已补默认 SUBJECT，此处恒透传，确保 Guard 执行期判定不丢失
                    newCfg.set("scopeMode", cfg.get("scopeMode"));
                }
                String dsType = "TIMESERIES";
                JsonNode agg = cfg.path("aggregation");
                if (agg.isObject()) {
                    // 有 aggregation → 校验并按 groupBy 强制 ds_type（DATE→TIMESERIES，NONE/SUBJECT→SINGLE）
                    dsType = validateWideAggregation(agg, meta[0], slotCols);
                    newCfg.set("aggregation", agg);
                }
                e.setConfigJson(newCfg.toString());
                e.setDsType(dsType);
            }
            case "KPI_RESULT" -> {
                String cycleType = cfg.path("cycleType").asText("MONTHLY");
                if (!KPI_CYCLE_TYPES.contains(cycleType)) {
                    throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
                }
                e.setConfigJson(req.getConfigJson());
                e.setDsType("TIMESERIES");
            }
            case "KPI_DETAIL" -> {
                // KPI 细项引导式（spec §3.1）：方案必须存在且 ACTIVE、主体/模式枚举合法、
                // TREND 必须携带细项快照（code+name 均非空）、ds_type 与 mode 强制对应
                String schemeCode = cfg.path("schemeCode").asText();
                if (schemeCode.isBlank()) {
                    throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
                }
                Long activeCount = kpiSchemeMapper.selectCount(new LambdaQueryWrapper<PerfKpiScheme>()
                        .eq(PerfKpiScheme::getSchemeCode, schemeCode)
                        .eq(PerfKpiScheme::getStatus, "ACTIVE"));
                if (activeCount == null || activeCount == 0) {
                    throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
                }
                if (!KPI_DETAIL_SUBJECT_TYPES.contains(cfg.path("subjectType").asText())) {
                    throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
                }
                String mode = cfg.path("mode").asText();
                if (!"SNAPSHOT".equals(mode) && !"TREND".equals(mode)) {
                    throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
                }
                if ("TREND".equals(mode)) {
                    JsonNode metrics = cfg.path("metrics");
                    if (!metrics.isArray() || metrics.isEmpty()) {
                        throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
                    }
                    for (JsonNode m : metrics) {
                        // 名称快照由前端传入，保存时非空校验（细项列名依赖它）
                        if (m.path("metricCode").asText().isBlank() || m.path("metricName").asText().isBlank()) {
                            throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
                        }
                    }
                }
                if (cfg.hasNonNull("valueCol") && !KPI_DETAIL_VALUE_COLS.contains(cfg.path("valueCol").asText())) {
                    throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
                }
                // SNAPSHOT 强制 SINGLE、TREND 强制 TIMESERIES；显式传入不一致的 ds_type 属违规
                String expectedDsType = "SNAPSHOT".equals(mode) ? "SINGLE" : "TIMESERIES";
                if (req.getDsType() != null && !req.getDsType().isBlank()
                        && !expectedDsType.equals(req.getDsType())) {
                    throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
                }
                e.setConfigJson(req.getConfigJson());
                e.setDsType(expectedDsType);
            }
            case "CUSTOM_SQL" -> {
                String sql = cfg.path("sql").asText();
                engine.validateCustomSql(sql);
                String dsType = req.getDsType() == null ? "SINGLE" : req.getDsType();
                String dateCol = cfg.path("dateCol").asText(null);
                if ("TIMESERIES".equals(dsType) && (dateCol == null || dateCol.isBlank())) {
                    throw new RptException(RptErrorCode.SCREEN_DS_TIMESERIES_NEED_DATECOL);
                }
                e.setConfigJson(req.getConfigJson());
                e.setDsType(dsType);
            }
            default -> throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
        }
        e.setDsName(req.getDsName());
        e.setSourceKind(kind);
        e.setTimeParamJson(req.getTimeParamJson());
        e.setStatus(req.getStatus() == null || req.getStatus().isBlank() ? "ACTIVE" : req.getStatus());
        e.setRemark(req.getRemark());
    }

    private JsonNode readJson(String json) {
        try {
            // schemaVersion 读时兼容集中在 ScreenConfigSchema 唯一入口（旧数据视为版本 1）
            return ScreenConfigSchema.withDefaults(objectMapper.readTree(json == null ? "{}" : json));
        } catch (Exception ex) {
            throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID, ex);
        }
    }

    /**
     * 校验顶层可选 fieldMeta（spec 2026-07-17 §3.2）：col 非空且不重复、role 枚举合法，违规 43009.
     */
    private void validateFieldMeta(JsonNode cfg) {
        JsonNode fieldMeta = cfg.path("fieldMeta");
        if (fieldMeta.isMissingNode() || fieldMeta.isNull()) {
            return;
        }
        if (!fieldMeta.isArray()) {
            throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
        }
        Set<String> seenCols = new HashSet<>();
        for (JsonNode n : fieldMeta) {
            String col = n.path("col").asText();
            if (col.isBlank() || !seenCols.add(col)) {
                throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
            }
            if (!FIELD_META_ROLES.contains(n.path("role").asText())) {
                throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
            }
        }
    }

    /**
     * 校验顶层可选 scopeMode（spec 2026-07-17 §4）：仅 SUBJECT|GLOBAL，缺省已由 readJson 补 SUBJECT，违规 43009.
     */
    private void validateScopeMode(JsonNode cfg) {
        if (!SCOPE_MODES.contains(cfg.path("scopeMode").asText())) {
            throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
        }
    }

    /**
     * 校验 WIDE_TABLE 可选 aggregation（spec 2026-07-17 §3.3）：groupBy/agg/filters 的 op 均白名单枚举，
     * filters 的 col 仅允许主体列/data_date/已配置槽位列（val_N），违规 43009.
     *
     * @return 按 groupBy 强制的 ds_type（DATE→TIMESERIES，NONE/SUBJECT→SINGLE）
     */
    private String validateWideAggregation(JsonNode agg, String subjectCol, Set<String> slotCols) {
        String groupBy = agg.path("groupBy").asText();
        if (!WIDE_AGG_GROUP_BYS.contains(groupBy)) {
            throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
        }
        if (!WIDE_AGG_FUNCS.contains(agg.path("agg").asText())) {
            throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
        }
        JsonNode filters = agg.path("filters");
        if (!filters.isMissingNode() && !filters.isNull()) {
            if (!filters.isArray()) {
                throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
            }
            for (JsonNode f : filters) {
                String col = f.path("col").asText();
                if (!col.equals(subjectCol) && !"data_date".equals(col) && !slotCols.contains(col)) {
                    throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
                }
                if (!WIDE_FILTER_OPS.contains(f.path("op").asText())) {
                    throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
                }
                if (f.path("value").isMissingNode() || f.path("value").isNull()) {
                    throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
                }
            }
        }
        return "DATE".equals(groupBy) ? "TIMESERIES" : "SINGLE";
    }

    private ScreenDatasourceRespDTO toDto(RptScreenDatasource e) {
        ScreenDatasourceRespDTO d = new ScreenDatasourceRespDTO();
        d.setId(e.getId());
        d.setDsCode(e.getDsCode());
        d.setDsName(e.getDsName());
        d.setDsType(e.getDsType());
        d.setSourceKind(e.getSourceKind());
        d.setConfigJson(e.getConfigJson());
        d.setTimeParamJson(e.getTimeParamJson());
        d.setStatus(e.getStatus());
        d.setRemark(e.getRemark());
        d.setCreatedTime(e.getCreatedTime());
        return d;
    }

    /** 高危操作手工审计（失败仅告警不阻断业务，模块惯例） */
    private void safelyAudit(String action, String detail, String reason) {
        try {
            AuditLogCmd cmd = AuditLogCmd.builder()
                    .empId(currentUserApi.getCurrentEmpId())
                    .bizType("REPORT")
                    .bizAction(action)
                    .resourceUrl("/api/screen/admin/datasources")
                    .requestMethod("POST")
                    .requestParams(detail != null && detail.length() > 1000 ? detail.substring(0, 1000) : detail)
                    .responseStatus(200)
                    .reason(reason)
                    .build();
            auditApi.log(cmd);
        } catch (RuntimeException ex) {
            log.warn("[ScreenDatasourceService] AuditApi.log 失败 cause={}", ex.getMessage());
        }
    }
}
