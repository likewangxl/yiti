package com.bank.branch.platform.report.service.screen;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.ResourceApi;
import com.bank.branch.platform.common.trace.MdcUtils;
import com.bank.branch.platform.governance.api.AuditApi;
import com.bank.branch.platform.governance.api.dto.AuditLogCmd;
import com.bank.branch.platform.report.dto.req.ScreenDataReqDTO;
import com.bank.branch.platform.report.dto.req.ScreenDatasourceSaveReqDTO;
import com.bank.branch.platform.report.dto.req.ScreenDatasourceProbeReqDTO;
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
import com.bank.branch.platform.report.mapper.RptScreenMapper;
import com.bank.branch.platform.report.mapper.RptScreenPublishLogMapper;
import com.bank.branch.platform.report.entity.RptScreen;
import com.bank.branch.platform.report.entity.RptScreenBlock;
import com.bank.branch.platform.report.entity.RptScreenPublishLog;
import com.bank.branch.platform.report.support.ScreenConfigSchema;
import com.bank.branch.platform.report.support.ScreenMetricSlotDao;
import com.bank.branch.platform.report.support.PublishedScreenPackageValidator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
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

    /** 度量金额显示预设；仅作用于 fieldMeta 的 METRIC，展示时不换算 rows 原始值。 */
    private static final Set<String> FIELD_META_AMOUNT_SCALES = Set.of(
            "YUAN", "TEN_THOUSAND_YUAN", "HUNDRED_MILLION_YUAN");

    /** scopeMode 枚举（spec 2026-07-17 §4，全 source_kind 通用，缺省 SUBJECT 由 ScreenConfigSchema 补） */
    private static final Set<String> SCOPE_MODES = Set.of("SUBJECT", "GLOBAL", "NAMED_GROUP");

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

    @Autowired(required = false)
    private RptScreenMapper screenMapper;

    /** 发布归档仅用于数据源引用与删除保护扫描；v1 运行身份只认当前发布包，草稿 JSON 不参与证明。 */
    @Autowired(required = false)
    private RptScreenPublishLogMapper publishLogMapper;

    @Autowired(required = false)
    private ScreenScopeAuthorizationService scopeAuthorizationService;

    /** 草稿实时取数与草稿渲染共用画布管理读取资源，缺失时 fail-close。 */
    @Autowired(required = false)
    private ResourceApi resourceApi;

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
        List<RptScreenDatasource> datasources = dsMapper.selectList(qw);
        if (datasources == null || datasources.isEmpty()) {
            return List.of();
        }
        // 远程数据库下不能逐数据源重读相同屏、区块和归档；请求级快照保留保守引用语义。
        List<RptScreen> screens = screenMapper == null ? List.of()
                : screenMapper.selectList(new LambdaQueryWrapper<>());
        List<RptScreenBlock> blocks = blockMapper.selectList(new LambdaQueryWrapper<>());
        List<RptScreenPublishLog> archives = List.of();
        boolean archiveReadFailed = false;
        if (publishLogMapper != null) {
            try {
                archives = publishLogMapper.selectList(new LambdaQueryWrapper<>());
            } catch (RuntimeException ex) {
                archiveReadFailed = true;
                log.warn("[ScreenDatasource] 列表归档读取失败，采用保守引用提示", ex);
            }
        }
        ScreenDatasourceReferenceIndex.Snapshot references = ScreenDatasourceReferenceIndex.build(
                screens, blocks, archives, archiveReadFailed);
        return datasources.stream().map(e -> toDto(e, references)).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(ScreenDatasourceSaveReqDTO req) {
        if (!isExplicitBizLine(req.getBizLine())) {
            // 新数据源不能再依赖数据库/服务端 COMMON 默认值；历史记录的兼容只发生在更新路径。
            throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
        }
        RptScreenDatasource e = new RptScreenDatasource();
        applyValidated(e, req);
        requireAuditReason(req.getReason());
        e.setBizLine(normalizeBizLine(req.getBizLine()));
        e.setDsCode("SCRDS_" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 8).toUpperCase(Locale.ROOT));
        e.setCreatedBy(currentUserApi.getCurrentEmpId());
        e.setUpdatedBy(currentUserApi.getCurrentEmpId());
        dsMapper.insert(e);
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("event", "DATASOURCE_CREATE");
        payload.put("datasourceId", e.getId());
        payload.put("bizLine", e.getBizLine());
        payload.put("sourceKind", e.getSourceKind());
        persistAudit("DATASOURCE_CREATE", "RPT_SCREEN_DATASOURCE", String.valueOf(e.getId()),
                "/api/screen/admin/datasources", "POST", null, datasourceSnapshot(e), List.of(), List.of(),
                payload, req.getReason());
        return e.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, ScreenDatasourceSaveReqDTO req) {
        RptScreenDatasource existing = requireDs(id);
        RptScreenDatasource candidate = copyDatasource(existing);
        String oldBizLine = normalizeBizLine(existing.getBizLine());
        String effectiveBizLine = isExplicitBizLine(req.getBizLine())
                ? normalizeBizLine(req.getBizLine()) : oldBizLine;
        applyValidated(candidate, req);
        requireAuditReason(req.getReason());
        candidate.setBizLine(effectiveBizLine);
        candidate.setUpdatedBy(currentUserApi.getCurrentEmpId());

        List<String> publishedReferences = referencedScreenCodes(id, true);
        Map<String, Object> before = datasourceSnapshot(existing, publishedReferences);
        if (hasQuerySemanticChange(existing, candidate)) {
            // 已发布/归档引用也允许原地编辑查询语义；仍须重新通过条线与 NAMED_GROUP
            // 安全矩阵，不能因复制实体而沿用旧数据源的校验结果。
            validateReferencedScreenLines(id, candidate);
        }
        dsMapper.updateById(candidate);
        Map<String, Object> after = datasourceSnapshot(candidate, publishedReferences);
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("event", "DATASOURCE_UPDATE");
        payload.put("datasourceId", id);
        payload.put("before", before);
        payload.put("after", after);
        payload.put("publishedReferences", publishedReferences);
        persistAudit("DATASOURCE_UPDATE", "RPT_SCREEN_DATASOURCE", String.valueOf(id),
                "/api/screen/admin/datasources/" + id, "PUT", before, after, List.of(), List.of(),
                payload, req.getReason());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, String reason) {
        requireAuditReason(reason);
        RptScreenDatasource before = requireDs(id);
        List<String> publishedReferences = referencedScreenCodes(id, true);
        // 已发布/归档引用的删除保护优先于草稿占用：两者同时存在时，前端必须拿到完整
        // 发布引用屏去执行“新建副本→重新绑定→重新发布”，不能被泛化草稿错误吞掉。
        if (!publishedReferences.isEmpty()) {
            throw publishedDatasourceReferenceConflict(publishedReferences);
        }
        if (blockMapper.countByDsId(id) > 0) {
            throw new RptException(RptErrorCode.SCREEN_DS_IN_USE);
        }
        dsMapper.deleteById(id);
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("event", "DATASOURCE_DELETE");
        payload.put("datasourceId", id);
        payload.put("publishedReferences", publishedReferences);
        persistAudit("DATASOURCE_DELETE", "RPT_SCREEN_DATASOURCE", String.valueOf(id),
                "/api/screen/admin/datasources/" + id, "DELETE", datasourceSnapshot(before, publishedReferences), null,
                List.of(), List.of(), payload, reason);
    }

    @Override
    public ScreenDataRespDTO tryRun(ScreenTryRunReqDTO req) {
        requireAuditReason(req.getReason());
        // 试跑也走完整校验（含 CUSTOM_SQL 白名单），随后 LIMIT 10 执行
        RptScreenDatasource probe = new RptScreenDatasource();
        ScreenDatasourceSaveReqDTO fake = new ScreenDatasourceSaveReqDTO();
        fake.setDsName("__tryrun__");
        fake.setDsType(req.getDsType());
        fake.setSourceKind(req.getSourceKind());
        fake.setConfigJson(req.getConfigJson());
        fake.setBizLine("COMMON");
        applyValidated(probe, fake);

        ScreenDataReqDTO dataReq = new ScreenDataReqDTO();
        dataReq.setPeriod(req.getPeriod());
        dataReq.setDateFrom(req.getDateFrom());
        dataReq.setDateTo(req.getDateTo());
        dataReq.setContextParams(req.getContextParams());
        JsonNode probeConfig = readJson(probe.getConfigJson());
        if ("NAMED_GROUP".equalsIgnoreCase(probeConfig.path("scopeMode").asText())) {
            if (scopeAuthorizationService == null || req.getTestOrgGroupCode() == null
                    || req.getTestOrgGroupCode().isBlank()) {
                throw new RptException(RptErrorCode.SCREEN_SCOPE_INVALID);
            }
            Set<String> members = scopeAuthorizationService.testGroupMemberCodes(req.getTestOrgGroupCode());
            ensureNamedGroupDatasourceSafe(probe);
            dataReq.setNamedGroup(true);
            dataReq.setServerOrgCodes(new ArrayList<>(members));
        }
        // try/finally 兜底：engine.tryRun 执行阶段抛异常也必须留痕，不能只审计成功路径
        boolean success = false;
        try {
            ScreenDataRespDTO resp = engine.tryRun(probe.getSourceKind(), probe.getConfigJson(), dataReq);
            success = true;
            return resp;
        } finally {
            Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("event", "DATASOURCE_TRY_RUN");
            payload.put("sourceKind", req.getSourceKind());
            payload.put("namedGroup", dataReq.isNamedGroup());
            payload.put("testOrgGroupCode", req.getTestOrgGroupCode());
            payload.put("result", success ? "SUCCESS" : "FAILED");
            persistAudit("DATASOURCE_TRY_RUN", "RPT_SCREEN_DATASOURCE_TRY_RUN", req.getSourceKind(),
                    "/api/screen/admin/datasources/try-run", "POST", null, datasourceSnapshot(probe),
                    List.of(), List.of(), payload, req.getReason());
        }
    }

    /**
     * 已保存数据源的列探测。该入口只服务管理端设计器，拥有独立资源与审计；运行时接口不再
     * 接受缺屏身份的 schema1 请求。
     */
    @Override
    public ScreenDataRespDTO probeColumns(Long datasourceId, ScreenDatasourceProbeReqDTO req) {
        if (req.getReason() == null || req.getReason().isBlank()) {
            throw new RptException(RptErrorCode.SCREEN_AUDIT_REASON_REQUIRED);
        }
        RptScreenDatasource datasource = requireDs(datasourceId);
        ScreenDataReqDTO dataReq = new ScreenDataReqDTO();
        dataReq.setPeriod(req.getPeriod());
        dataReq.setDateFrom(req.getDateFrom());
        dataReq.setDateTo(req.getDateTo());
        dataReq.setContextParams(req.getContextParams());
        JsonNode config = readJson(datasource.getConfigJson());
        if ("NAMED_GROUP".equalsIgnoreCase(config.path("scopeMode").asText())) {
            if (scopeAuthorizationService == null || req.getTestOrgGroupCode() == null
                    || req.getTestOrgGroupCode().isBlank()) {
                throw new RptException(RptErrorCode.SCREEN_SCOPE_INVALID);
            }
            ensureNamedGroupDatasourceSafe(datasource);
            Set<String> members = scopeAuthorizationService.testGroupMemberCodes(req.getTestOrgGroupCode());
            dataReq.setNamedGroup(true);
            dataReq.setServerOrgCodes(new ArrayList<>(members));
        }
        boolean success = false;
        try {
            ScreenDataRespDTO response = engine.tryRun(datasource.getSourceKind(), datasource.getConfigJson(), dataReq);
            success = true;
            return response;
        } finally {
            Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("event", "DATASOURCE_PROBE_COLUMNS");
            payload.put("datasourceId", datasourceId);
            payload.put("sourceKind", datasource.getSourceKind());
            payload.put("namedGroup", dataReq.isNamedGroup());
            payload.put("testOrgGroupCode", req.getTestOrgGroupCode());
            payload.put("result", success ? "SUCCESS" : "FAILED");
            persistAudit("DATASOURCE_PROBE_COLUMNS", "RPT_SCREEN_DATASOURCE", String.valueOf(datasourceId),
                    "/api/screen/admin/datasources/" + datasourceId + "/probe-columns", "POST", null,
                    datasourceSnapshot(datasource), List.of(), List.of(), payload, req.getReason());
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
        try {
            validatePreviewState(req);
            RptScreen screen = resolveRuntimeScreen(req);
            RptScreenDatasource ds = resolveRuntimeDatasource(req, screen);
            // 状态入库统一大写；运行时仍按规范化值判断，避免历史小写 disabled 被当作 ACTIVE 绕过。
            if (ds == null || !"ACTIVE".equals(normalizedDatasourceStatus(ds.getStatus()))) {
                throw new RptException(RptErrorCode.SCREEN_DS_NOT_FOUND);
            }
            String runtimeTemplate = runtimeCodeTemplate(screen, req);
            validateRetailTemplateScreenLine(screen, runtimeTemplate);
            validateRetailTemplateDatasourceLine(runtimeTemplate, ds);
            if ("NAMED_GROUP".equalsIgnoreCase(screen.getOrgScopeMode())) {
                if (scopeAuthorizationService == null) {
                    // 命名组运行时没有 auth 适配器时必须拒绝，不能退回旧 DATA_SCOPE 全局口径。
                    throw new RptException(RptErrorCode.SCREEN_ACCESS_DENIED);
                }
                if (!isBizLineCompatible(screen.getBizLine(), ds.getBizLine())) {
                    throw new RptException(RptErrorCode.SCREEN_BIZ_LINE_MISMATCH);
                }
                ensureNamedGroupDatasourceSafe(ds);
                // 命名机构组权限在屏级同角色门禁中已完成；将服务端机构集合注入引擎，
                // 不再信任客户端 orgCodes 及旧 DATA_SCOPE 主体参数；单个 orgCode 只允许
                // 在已授权集合内做收窄，不能借此扩大范围。
                req.setNamedGroup(true);
                Set<String> authorizedOrgCodes = scopeAuthorizationService.authorize(screen);
                if (authorizedOrgCodes == null || authorizedOrgCodes.isEmpty()) {
                    throw new RptException(RptErrorCode.SCREEN_ACCESS_DENIED);
                }
                String requestedOrgCode = requestedOrgCode(req);
                if (requestedOrgCode != null) {
                    if (!authorizedOrgCodes.contains(requestedOrgCode)) {
                        throw new RptException(RptErrorCode.SCREEN_ACCESS_DENIED);
                    }
                    req.setServerOrgCodes(List.of(requestedOrgCode));
                } else {
                    req.setServerOrgCodes(new java.util.ArrayList<>(authorizedOrgCodes));
                }
            } else {
                if (!isBizLineCompatible(screen.getBizLine(), ds.getBizLine())) {
                    throw new RptException(RptErrorCode.SCREEN_BIZ_LINE_MISMATCH);
                }
                // schemaVersion=1 保持旧 DATA_SCOPE 逻辑，但屏与 dsId 已先由服务端绑定核验。
                scopeGuard.check(ds, req);
            }
            return engine.query(ds, req);
        } catch (RptException ex) {
            persistRuntimeDenied(req, ex);
            throw ex;
        }
    }

    private String requestedOrgCode(ScreenDataReqDTO req) {
        if (req == null || req.getContextParams() == null) {
            return null;
        }
        String orgCode = req.getContextParams().get("orgCode");
        return orgCode == null || orgCode.isBlank() ? null : orgCode.trim();
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
                if ("NAMED_GROUP".equals(cfg.path("scopeMode").asText())) {
                    validateNamedGroupCustomSql(cfg);
                } else {
                    engine.validateCustomSql(sql);
                }
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
        e.setStatus(resolveDatasourceStatus(req.getStatus(), e.getStatus()));
        e.setRemark(req.getRemark());
        if ("NAMED_GROUP".equalsIgnoreCase(readJson(e.getConfigJson()).path("scopeMode").asText())) {
            ensureNamedGroupDatasourceSafe(e);
        }
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
     * amountScale 只允许 METRIC 使用，且与手工 unit/decimals 互斥；该预设只改变展示元数据，
     * 不改变查询 SQL 或 rows 原始值。
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
            String role = n.path("role").asText();
            if (!FIELD_META_ROLES.contains(role)) {
                throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
            }
            if (n.hasNonNull("amountScale")) {
                String amountScale = n.path("amountScale").asText();
                if (!FIELD_META_AMOUNT_SCALES.contains(amountScale)
                        || !"METRIC".equals(role)
                        || n.has("unit")
                        || n.has("decimals")) {
                    throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
                }
            }
        }
    }

    /**
     * 校验顶层可选 scopeMode（spec 2026-08-11）：SUBJECT/GLOBAL/NAMED_GROUP，缺省已由
     * readJson 补 SUBJECT，违规 43009。
     */
    private void validateScopeMode(JsonNode cfg) {
        if (!SCOPE_MODES.contains(cfg.path("scopeMode").asText())) {
            throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
        }
    }

    /**
     * 防御性校验：引擎仍保留外层范围包装用于历史脏数据的 fail-close；但本期配置面
     * 不允许把 CUSTOM_SQL 绑定到 NAMED_GROUP，因为无法形成可审计的稳定 org_code 映射。
     */
    private void validateNamedGroupCustomSql(JsonNode cfg) {
        throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
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

    private ScreenDatasourceRespDTO toDto(RptScreenDatasource e, ScreenDatasourceReferenceIndex.Snapshot references) {
        ScreenDatasourceRespDTO d = new ScreenDatasourceRespDTO();
        d.setId(e.getId());
        d.setDsCode(e.getDsCode());
        d.setDsName(e.getDsName());
        d.setDsType(e.getDsType());
        d.setSourceKind(e.getSourceKind());
        d.setBizLine(normalizeBizLine(e.getBizLine()));
        d.setConfigJson(e.getConfigJson());
        d.setTimeParamJson(e.getTimeParamJson());
        d.setStatus(e.getStatus());
        d.setRemark(e.getRemark());
        d.setCreatedTime(e.getCreatedTime());
        d.setDraftReferenceScreenCodes(references.draftCodes(e.getId()));
        d.setPublishedReferenceScreenCodes(references.publishedCodes(e.getId()));
        return d;
    }

    private String normalizeBizLine(String value) {
        return value == null || value.isBlank() ? "COMMON" : value.trim().toUpperCase(Locale.ROOT);
    }

    private boolean isExplicitBizLine(String value) {
        return value != null && Set.of("CORP", "RETAIL", "COMMON")
                .contains(value.trim().toUpperCase(Locale.ROOT));
    }

    private RptScreen resolveRuntimeScreen(ScreenDataReqDTO req) {
        if (req.getScreenCode() == null || req.getScreenCode().isBlank()) {
            // v1 也必须有服务端可核验的当前发布屏身份；/api/screen/data 不再是任意 dsId 探测后门。
            throw new RptException(RptErrorCode.SCREEN_BLOCK_NOT_PUBLISHED);
        }
        if (screenMapper == null) {
            throw new RptException(RptErrorCode.SCREEN_NOT_FOUND);
        }
        List<RptScreen> screens = screenMapper.selectList(new LambdaQueryWrapper<RptScreen>()
                .eq(RptScreen::getScreenCode, req.getScreenCode())
                .eq(RptScreen::getStatus, "ACTIVE"));
        // 脏数据重复时绝不能“取第一条”；数据库唯一约束与此处 fail-close 双重收敛竞态。
        if (screens.size() != 1) {
            throw new RptException(RptErrorCode.SCREEN_NOT_FOUND);
        }
        return screens.get(0);
    }

    private RptScreenDatasource resolveRuntimeDatasource(ScreenDataReqDTO req, RptScreen screen) {
        if ("draft".equalsIgnoreCase(req.getPreviewState())) {
            return resolveDraftDatasource(req, screen);
        }
        boolean schemaV2 = requiresSchemaV2(screen);
        if (schemaV2) {
            if (!Integer.valueOf(2).equals(req.getSchemaVersion())) {
                throw new RptException(RptErrorCode.SCREEN_BLOCK_NOT_PUBLISHED);
            }
            if (!isPublishedOrDrafting(screen.getPublishStatus()) || screen.getCanvasPublishedJson() == null
                    || screen.getCanvasPublishedJson().isBlank()) {
                throw new RptException(RptErrorCode.SCREEN_BLOCK_NOT_PUBLISHED);
            }
            if (req.getBlockId() == null) {
                throw new RptException(RptErrorCode.SCREEN_BLOCK_NOT_PUBLISHED);
            }
            if (publishedPackageBindingEvidence(screen.getCanvasPublishedJson(), null).untrusted()) {
                throw new RptException(RptErrorCode.SCREEN_PUBLISHED_SNAPSHOT_UNTRUSTED);
            }
            Long resolvedDsId = readPublishedDsId(screen.getCanvasPublishedJson(), req.getBlockId());
            if (resolvedDsId == null) {
                throw new RptException(RptErrorCode.SCREEN_BLOCK_NOT_PUBLISHED);
            }
            // 客户端 dsId 即使同时传入也不参与解析，防止跨屏数据源拼接。
            return dsMapper.selectById(resolvedDsId);
        }
        // 历史 v1 也不能因显式版本就接受任意 dsId：它必须来自当前屏可验证的发布绑定。
        if (!Integer.valueOf(1).equals(req.getSchemaVersion()) || req.getDsId() == null) {
            throw new RptException(RptErrorCode.SCREEN_BLOCK_NOT_PUBLISHED);
        }
        PublishedPackageEvidence evidence = currentPublishedBindingEvidence(screen, req.getDsId());
        if (evidence.untrusted()) {
            throw new RptException(RptErrorCode.SCREEN_PUBLISHED_SNAPSHOT_UNTRUSTED);
        }
        if (!evidence.bound()) {
            throw new RptException(RptErrorCode.SCREEN_BLOCK_NOT_PUBLISHED);
        }
        return dsMapper.selectById(req.getDsId());
    }

    /**
     * 草稿预览取数只接受显式 screenCode + blockId：身份来自当前屏草稿组件树和本屏 block 行，
     * 客户端 dsId 即使传入也完全不参与解析。该支路不读取发布包，避免改绑/新增草稿被旧快照身份覆盖。
     */
    private RptScreenDatasource resolveDraftDatasource(ScreenDataReqDTO req, RptScreen screen) {
        if (resourceApi == null
                || !resourceApi.hasResourcePermission(currentUserApi.getCurrentEmpId(), "R_RPT_SCR_CV_GET")) {
            throw new RptException(RptErrorCode.SCREEN_ACCESS_DENIED);
        }
        // 草稿是未发布配置，除画布读取资源外仍必须复用屏级角色白名单门禁；
        // LEGACY_CONTEXT 也不能因为继续使用旧 DATA_SCOPE 就跳过屏自身的访问角色。
        if (scopeAuthorizationService == null) {
            throw new RptException(RptErrorCode.SCREEN_ACCESS_DENIED);
        }
        scopeAuthorizationService.authorize(screen);
        if (req.getScreenCode() == null || req.getScreenCode().isBlank() || req.getBlockId() == null
                || req.getBlockId() <= 0) {
            throw new RptException(RptErrorCode.SCREEN_BLOCK_NOT_PUBLISHED);
        }
        // 草稿协议版本必须由当前草稿决定，不能沿用旧发布包；否则发布 v1 的屏
        // 在草稿加入 V2 MapCenter 后会被错误要求继续提交 schemaVersion=1。
        int requiredSchemaVersion = requiresDraftSchemaV2(screen) ? 2 : 1;
        if (!Integer.valueOf(requiredSchemaVersion).equals(req.getSchemaVersion())) {
            throw new RptException(RptErrorCode.SCREEN_BLOCK_NOT_PUBLISHED);
        }
        Set<Long> draftBlockIds = new HashSet<>();
        try {
            JsonNode draft = objectMapper.readTree(screen.getCanvasDraftJson() == null
                    ? "{}" : screen.getCanvasDraftJson());
            collectDraftChartBlockIds(draft.path("components"), draftBlockIds);
        } catch (RptException e) {
            throw e;
        } catch (Exception e) {
            throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID, e);
        }
        if (!draftBlockIds.contains(req.getBlockId())) {
            throw new RptException(RptErrorCode.SCREEN_BLOCK_NOT_PUBLISHED);
        }
        List<RptScreenBlock> rows = blockMapper.selectList(new LambdaQueryWrapper<RptScreenBlock>()
                .eq(RptScreenBlock::getScreenId, screen.getId()));
        RptScreenBlock block = rows.stream()
                .filter(row -> screen.getId().equals(row.getScreenId()))
                .filter(row -> req.getBlockId().equals(row.getId()))
                .findFirst()
                .orElseThrow(() -> new RptException(RptErrorCode.SCREEN_BLOCK_NOT_PUBLISHED));
        Long dsId = readBlockDatasourceId(block.getBindJson());
        if (dsId == null) {
            throw new RptException(RptErrorCode.SCREEN_BLOCK_BIND_MISMATCH);
        }
        return dsMapper.selectById(dsId);
    }

    /** previewState 是安全状态开关：只有精确小写 draft 才进入草稿支路，其余非空值拒绝。 */
    private void validatePreviewState(ScreenDataReqDTO req) {
        if (req != null && req.getPreviewState() != null && !"draft".equals(req.getPreviewState())) {
            throw new RptException(RptErrorCode.SCREEN_BLOCK_NOT_PUBLISHED);
        }
    }

    private Long readBlockDatasourceId(String bindJson) {
        try {
            JsonNode bind = objectMapper.readTree(bindJson == null ? "{}" : bindJson);
            JsonNode dsId = bind.path("dsId");
            return dsId.isIntegralNumber() && dsId.asLong() > 0 ? dsId.asLong() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void collectDraftChartBlockIds(JsonNode components, Set<Long> out) {
        if (components == null || !components.isArray()) {
            return;
        }
        for (JsonNode node : components) {
            if ("ChartWidget".equals(node.path("component").asText())) {
                JsonNode blockId = node.path("blockId");
                if (!blockId.isIntegralNumber() || !blockId.canConvertToLong()
                        || blockId.longValue() <= 0 || !out.add(blockId.longValue())) {
                    throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID);
                }
            }
            collectDraftChartBlockIds(node.path("children"), out);
        }
    }

    /**
     * v1 唯一兼容支路：数据源必须由已发布屏的不可变包证明。
     *
     * <p>v1 运行只以当前 {@code canvasPublishedJson} 证明当前发布绑定，绝不从归档包借用旧
     * dsId 身份。不能再把当前可变 RPT_SCREEN_BLOCK 行当作历史证明：那会令草稿改绑、删块或
     * 跨状态切换重新打开 v1 任意 dsId 查询路径。</p>
     */
    private PublishedPackageEvidence currentPublishedBindingEvidence(RptScreen screen, Long dsId) {
        if (screen == null || dsId == null) {
            return new PublishedPackageEvidence(false, false);
        }
        // 不以 publish_status=1/2 为安全分支：只要记录上存在当前发布包就检验它，避免攻击者
        // 通过状态翻转让同一份无快照 JSON 绕过校验。
        if (isPublishedOrDrafting(screen.getPublishStatus())
                || (screen.getCanvasPublishedJson() != null && !screen.getCanvasPublishedJson().isBlank())) {
            return publishedPackageBindingEvidence(screen.getCanvasPublishedJson(), dsId);
        }
        return new PublishedPackageEvidence(false, false);
    }

    /**
     * 单个当前或归档发布包的严格证明。每个 ChartWidget 的 integral blockId 与快照键必须双向
     * 一一对应，且快照 bind.dsId 必须为 integral JSON 数字；否则此包不可信。
     */
    private PublishedPackageEvidence publishedPackageBindingEvidence(String publishedJson, Long dsId) {
        try {
            JsonNode root = PublishedScreenPackageValidator.read(publishedJson);
            Map<Long, JsonNode> snapshots = PublishedScreenPackageValidator.requireTrustedBindings(root);
            boolean bound = false;
            for (JsonNode snapshot : snapshots.values()) {
                JsonNode candidate = snapshot.path("bind").path("dsId");
                if (dsId != null && dsId.equals(candidate.longValue())) {
                    bound = true;
                }
            }
            return new PublishedPackageEvidence(bound, false);
        } catch (Exception e) {
            return PublishedPackageEvidence.invalidPackage();
        }
    }

    private Map<Long, Integer> strictPublishedChartBlockCounts(JsonNode components) {
        Map<Long, Integer> counts = new java.util.LinkedHashMap<>();
        collectStrictPublishedChartBlockCounts(components, counts);
        if (counts.values().stream().anyMatch(count -> count != 1)) {
            throw new RptException(RptErrorCode.SCREEN_PUBLISHED_SNAPSHOT_UNTRUSTED);
        }
        return counts;
    }

    private void collectStrictPublishedChartBlockCounts(JsonNode components, Map<Long, Integer> counts) {
        if (components == null || !components.isArray()) {
            return;
        }
        for (JsonNode node : components) {
            if ("ChartWidget".equals(node.path("component").asText())) {
                JsonNode blockId = node.path("blockId");
                if (!blockId.isIntegralNumber()) {
                    throw new RptException(RptErrorCode.SCREEN_PUBLISHED_SNAPSHOT_UNTRUSTED);
                }
                counts.merge(blockId.longValue(), 1, Integer::sum);
            }
            collectStrictPublishedChartBlockCounts(node.path("children"), counts);
        }
    }

    private record PublishedPackageEvidence(boolean bound, boolean untrusted) {
        private static PublishedPackageEvidence invalidPackage() {
            return new PublishedPackageEvidence(false, true);
        }
    }

    private Long readBlockId(String raw) {
        try {
            return raw == null || raw.isBlank() ? null : Long.valueOf(raw);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** 收集发布组件树中恰好出现一次的 blockId；重复 ID 属歧义发布包，拒绝其证明。 */
    private Set<Long> publishedBlockIds(JsonNode components) {
        Map<Long, Integer> counts = new java.util.HashMap<>();
        collectPublishedBlockCounts(components, counts);
        return counts.entrySet().stream()
                .filter(entry -> entry.getValue() == 1)
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private int publishedBlockCount(JsonNode components, Long blockId) {
        Map<Long, Integer> counts = new java.util.HashMap<>();
        collectPublishedBlockCounts(components, counts);
        return counts.getOrDefault(blockId, 0);
    }

    private void collectPublishedBlockCounts(JsonNode components, Map<Long, Integer> counts) {
        if (components == null || !components.isArray()) {
            return;
        }
        for (JsonNode node : components) {
            // 只有已发布 ChartWidget 的 blockId 能成为数据源身份凭据；装饰节点即使被伪造
            // 了同名字段，也不代表 RPT_SCREEN_BLOCK 的可执行绑定。
            if ("ChartWidget".equals(node.path("component").asText())
                    && node.path("blockId").isNumber()) {
                counts.merge(node.path("blockId").asLong(), 1, Integer::sum);
            }
            collectPublishedBlockCounts(node.path("children"), counts);
        }
    }

    private Long readDsIdLong(String bindJson) {
        try {
            JsonNode dsId = objectMapper.readTree(bindJson == null ? "{}" : bindJson).path("dsId");
            return dsId.isNumber() ? dsId.asLong() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private Long readPublishedDsId(String publishedJson, Long blockId) {
        try {
            JsonNode root = PublishedScreenPackageValidator.read(publishedJson);
            JsonNode snapshot = PublishedScreenPackageValidator.requireTrustedBindings(root).get(blockId);
            return snapshot == null ? null : snapshot.path("bind").path("dsId").longValue();
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isSchemaV2(String publishedJson) {
        final JsonNode root;
        try {
            root = objectMapper.readTree(publishedJson == null ? "{}" : publishedJson);
        } catch (Exception e) {
            // 损坏的历史发布包不能把运行期降级为“任意 dsId”；调用方随后会以严格绑定
            // 校验拒绝 schema1 请求。显式的未知 schema 则仍必须在下方 fail-close。
            return false;
        }
        validatePublishedSchema(root);
        if (root.path("schemaVersion").asInt(1) == 2) {
            return true;
        }
        return hasV2Map(root.path("components"));
    }

    private boolean hasV2Map(JsonNode nodes) {
        if (nodes == null || !nodes.isArray()) {
            return false;
        }
        for (JsonNode node : nodes) {
            if ("MapCenter".equals(node.path("component").asText())
                    && node.path("propValue").path("schemaVersion").asInt(1) == 2) {
                return true;
            }
            if (hasV2Map(node.path("children"))) {
                return true;
            }
        }
        return false;
    }

    /** 命名机构组屏固定使用 schema2；发布包根节点 schema2 也进入 blockId 解析链路。 */
    private boolean requiresSchemaV2(RptScreen screen) {
        return "NAMED_GROUP".equalsIgnoreCase(screen.getOrgScopeMode())
                || isSchemaV2(screen.getCanvasPublishedJson());
    }

    /** 草稿取数的协议版本只看当前草稿（命名机构组仍固定为 schema2）。 */
    private boolean requiresDraftSchemaV2(RptScreen screen) {
        return "NAMED_GROUP".equalsIgnoreCase(screen.getOrgScopeMode())
                || isSchemaV2(screen.getCanvasDraftJson());
    }

    private boolean isPublishedOrDrafting(Integer publishStatus) {
        return Integer.valueOf(1).equals(publishStatus) || Integer.valueOf(2).equals(publishStatus);
    }

    /** 根/MapCenter 的显式未知 schema 均不允许按 v1 降级。 */
    private void validatePublishedSchema(JsonNode root) {
        JsonNode rootVersion = root.path("schemaVersion");
        if (!rootVersion.isMissingNode() && !rootVersion.isNull()
                && (!rootVersion.canConvertToInt() || (rootVersion.asInt() != 1 && rootVersion.asInt() != 2))) {
            throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID);
        }
        validateMapSchema(root.path("components"));
    }

    private void validateMapSchema(JsonNode components) {
        if (components == null || !components.isArray()) {
            return;
        }
        for (JsonNode node : components) {
            if ("MapCenter".equals(node.path("component").asText())) {
                JsonNode version = node.path("propValue").path("schemaVersion");
                if (!version.isMissingNode() && !version.isNull()
                        && (!version.canConvertToInt() || (version.asInt() != 1 && version.asInt() != 2))) {
                    throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID);
                }
            }
            validateMapSchema(node.path("children"));
        }
    }

    private boolean publishedBlockExists(JsonNode components, Long blockId) {
        if (components == null || !components.isArray()) {
            return false;
        }
        for (JsonNode node : components) {
            if ("ChartWidget".equals(node.path("component").asText())
                    && node.path("blockId").isNumber()
                    && blockId.equals(node.path("blockId").asLong())) {
                return true;
            }
            if (publishedBlockExists(node.path("children"), blockId)) {
                return true;
            }
        }
        return false;
    }

    private boolean isBizLineCompatible(String screenLine, String dsLine) {
        String screen = normalizeBizLine(screenLine);
        String dataSource = normalizeBizLine(dsLine);
        return switch (screen) {
            case "CORP" -> "CORP".equals(dataSource) || "COMMON".equals(dataSource);
            case "RETAIL" -> "RETAIL".equals(dataSource) || "COMMON".equals(dataSource);
            case "COMMON" -> "COMMON".equals(dataSource);
            default -> false;
        };
    }

    /** RETAIL CODE 模板只能挂在 RETAIL 屏上；历史坐标画布不触发该专属约束。 */
    private void validateRetailTemplateScreenLine(RptScreen screen) {
        validateRetailTemplateScreenLine(screen, codeTemplate(screen));
    }

    private void validateRetailTemplateScreenLine(RptScreen screen, String template) {
        if (CodeScreenPresentationValidator.RETAIL_OVERVIEW_TEMPLATE.equals(template)
                && !"RETAIL".equals(normalizeBizLine(screen.getBizLine()))) {
            throw new RptException(RptErrorCode.SCREEN_BIZ_LINE_MISMATCH);
        }
    }

    /** 已被 RETAIL CODE 屏引用的数据源必须保持 RETAIL，COMMON 全行源不能伪装零售口径。 */
    private void validateRetailTemplateDatasourceLine(RptScreen screen, RptScreenDatasource datasource) {
        validateRetailTemplateDatasourceLine(codeTemplate(screen), datasource);
    }

    private void validateRetailTemplateDatasourceLine(String template, RptScreenDatasource datasource) {
        if (CodeScreenPresentationValidator.RETAIL_OVERVIEW_TEMPLATE.equals(template)
                && (datasource == null || !"RETAIL".equals(normalizeBizLine(datasource.getBizLine())))) {
            throw new RptException(RptErrorCode.SCREEN_BIZ_LINE_MISMATCH);
        }
    }

    /** 正式取数只认当前不可变发布包模板；草稿预览才读取当前可变画布样式。 */
    private String runtimeCodeTemplate(RptScreen screen, ScreenDataReqDTO req) {
        if (req != null && "draft".equals(req.getPreviewState())) {
            return codeTemplate(screen);
        }
        return publishedCodeTemplate(screen);
    }

    private String publishedCodeTemplate(RptScreen screen) {
        return publishedCodeTemplate(screen == null ? null : screen.getCanvasPublishedJson());
    }

    private String publishedCodeTemplate(String publishedJson) {
        try {
            JsonNode root = PublishedScreenPackageValidator.read(publishedJson);
            return CodeScreenPresentationValidator.presentationTemplate(root.path("canvasStyle"));
        } catch (RptException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RptException(RptErrorCode.SCREEN_PUBLISHED_SNAPSHOT_UNTRUSTED, ex);
        }
    }

    private String codeTemplate(RptScreen screen) {
        try {
            JsonNode style = objectMapper.readTree(screen == null || screen.getCanvasStyleJson() == null
                    || screen.getCanvasStyleJson().isBlank() ? "{}" : screen.getCanvasStyleJson());
            return CodeScreenPresentationValidator.presentationTemplate(style);
        } catch (RptException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID, ex);
        }
    }

    private void validateReferencedScreenLines(Long dsId, RptScreenDatasource candidate) {
        if (screenMapper == null || blockMapper == null) {
            return;
        }
        List<RptScreenBlock> draftBlocks = blockMapper.selectList(new LambdaQueryWrapper<>());
        Map<Long, Set<Long>> draftByScreen = draftBlocks.stream()
                .filter(block -> String.valueOf(dsId).equals(readDsId(block.getBindJson())))
                .collect(Collectors.groupingBy(RptScreenBlock::getScreenId,
                        Collectors.mapping(RptScreenBlock::getId, Collectors.toSet())));
        for (RptScreen screen : screenMapper.selectList(new LambdaQueryWrapper<>())) {
            boolean referencedByDraft = draftByScreen.containsKey(screen.getId());
            boolean referencedByPublished = publishedScreenReferences(screen, dsId);
            if (!referencedByDraft && !referencedByPublished) {
                continue;
            }
            if (!isBizLineCompatible(screen.getBizLine(), candidate.getBizLine())) {
                throw new RptException(RptErrorCode.SCREEN_BIZ_LINE_MISMATCH);
            }
            if (referencedByDraft) {
                String draftTemplate = codeTemplate(screen);
                validateRetailTemplateScreenLine(screen, draftTemplate);
                validateRetailTemplateDatasourceLine(draftTemplate, candidate);
            }
            if (referencedByPublished) {
                for (String publishedTemplate : publishedCodeTemplates(screen, dsId)) {
                    validateRetailTemplateScreenLine(screen, publishedTemplate);
                    validateRetailTemplateDatasourceLine(publishedTemplate, candidate);
                }
            }
            if ("NAMED_GROUP".equalsIgnoreCase(screen.getOrgScopeMode())) {
                ensureNamedGroupDatasourceSafe(candidate);
            }
        }
    }

    /** 当前发布包与所有归档包都属于引用编辑的安全边界，不能只看可变 canvasStyleJson。 */
    private Set<String> publishedCodeTemplates(RptScreen screen, Long dsId) {
        Set<String> templates = new LinkedHashSet<>();
        String current = publishedCodeTemplateForDatasource(
                screen == null ? null : screen.getCanvasPublishedJson(), dsId);
        if (current != null) {
            templates.add(current);
        }
        if (publishLogMapper == null || screen == null || screen.getId() == null) {
            return templates;
        }
        try {
            List<RptScreenPublishLog> archives = publishLogMapper.selectList(
                    new LambdaQueryWrapper<RptScreenPublishLog>()
                            .eq(RptScreenPublishLog::getScreenId, screen.getId()));
            if (archives != null) {
                for (RptScreenPublishLog archive : archives) {
                    String template = publishedCodeTemplateForDatasource(archive.getSnapshotJson(), dsId);
                    if (template != null) {
                        templates.add(template);
                    }
                }
            }
            return templates;
        } catch (RuntimeException ex) {
            throw new RptException(RptErrorCode.SCREEN_PUBLISHED_SNAPSHOT_UNTRUSTED, ex);
        }
    }

    private String publishedCodeTemplateForDatasource(String publishedJson, Long dsId) {
        try {
            JsonNode root = PublishedScreenPackageValidator.read(publishedJson);
            String template = CodeScreenPresentationValidator.presentationTemplate(root.path("canvasStyle"));
            // Legacy coordinate packages have no CODE declaration and keep the existing
            // conservative reference matrix; they cannot contribute a retail template rule.
            if (template == null) {
                return null;
            }
            Map<Long, JsonNode> snapshots = PublishedScreenPackageValidator.requireTrustedBindings(root);
            boolean referencesDatasource = snapshots.values().stream()
                    .map(snapshot -> snapshot.path("bind").path("dsId"))
                    .anyMatch(value -> value.isIntegralNumber() && dsId != null && dsId.equals(value.longValue()));
            return referencesDatasource ? template : null;
        } catch (RptException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RptException(RptErrorCode.SCREEN_PUBLISHED_SNAPSHOT_UNTRUSTED, ex);
        }
    }

    /** 返回草稿或发布快照中完整的引用屏编码，供数据源详情和冲突提示复用。 */
    private List<String> referencedScreenCodes(Long dsId, boolean published) {
        if (dsId == null || screenMapper == null || blockMapper == null) {
            return List.of();
        }
        List<RptScreen> screens = screenMapper.selectList(new LambdaQueryWrapper<>());
        if (screens == null) {
            return List.of();
        }
        Set<Long> draftScreenIds = Set.of();
        if (!published) {
            List<RptScreenBlock> blocks = blockMapper.selectList(new LambdaQueryWrapper<RptScreenBlock>());
            draftScreenIds = (blocks == null ? List.<RptScreenBlock>of() : blocks).stream()
                    .filter(block -> String.valueOf(dsId).equals(readDsId(block.getBindJson())))
                    .map(RptScreenBlock::getScreenId).collect(Collectors.toSet());
        }
        List<String> result = new ArrayList<>();
        for (RptScreen screen : screens) {
            boolean hit = published
                    ? publishedScreenReferences(screen, dsId)
                    : draftScreenIds.contains(screen.getId());
            if (hit && screen.getScreenCode() != null && !screen.getScreenCode().isBlank()) {
                result.add(screen.getScreenCode());
            }
        }
        return result.stream().distinct().sorted().toList();
    }

    /**
     * 已发布引用扫描覆盖当前发布包与归档包。归档不参与 v1 运行身份，但仍属于删除保护和
     * 编辑风险提示范围；读取/解析失败保守判定为引用，避免误删并确保安全矩阵复核。
     */
    private boolean publishedScreenReferences(RptScreen screen, Long dsId) {
        if (publishedPackageReferencesConservatively(screen, screen.getCanvasPublishedJson(), dsId)) {
            return true;
        }
        if (publishLogMapper == null) {
            return false;
        }
        try {
            List<RptScreenPublishLog> archives = publishLogMapper.selectList(
                    new LambdaQueryWrapper<RptScreenPublishLog>()
                            .eq(RptScreenPublishLog::getScreenId, screen.getId()));
            return archives != null && archives.stream().anyMatch(archive ->
                    publishedPackageReferencesConservatively(screen, archive.getSnapshotJson(), dsId));
        } catch (RuntimeException ex) {
            log.warn("[ScreenDatasource] 读取已发布归档引用失败，保守拒绝 datasourceId={} screenId={} cause={}",
                    dsId, screen.getId(), ex.getMessage());
            return true;
        }
    }

    private boolean publishedPackageReferencesConservatively(RptScreen screen, String publishedJson, Long dsId) {
        // 删除保护扫描与运行时不同：归档无可信快照时无法证明某个 ds“不被引用”，因此对每个候选
        // 数据源均保守视为引用，要求受控迁移或业务核对后重新发布后才能删除。
        PublishedPackageEvidence evidence = publishedPackageBindingEvidence(publishedJson, dsId);
        return evidence.untrusted() || evidence.bound();
    }

    private String readDsId(String bindJson) {
        try {
            JsonNode node = objectMapper.readTree(bindJson == null ? "{}" : bindJson).path("dsId");
            return node.isNumber() ? String.valueOf(node.asLong()) : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** NAMED_GROUP 目前只接受可由服务端 org_code 集合直接支配的 ORG 宽表。 */
    private void ensureNamedGroupDatasourceSafe(RptScreenDatasource datasource) {
        if (!"WIDE_TABLE".equals(datasource.getSourceKind())) {
            throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
        }
        JsonNode config = readJson(datasource.getConfigJson());
        if (!"ORG_INDEX_RESULT".equals(config.path("table").asText())
                || !"org_code".equals(config.path("subjectCol").asText())) {
            throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
        }
    }

    /** 旧调用的兼容包装：统一补足目标、URL、前后快照字段，不允许只把事件打到 logger。 */
    private void persistAudit(String action, Map<String, ?> payload, String reason) {
        Object datasourceId = payload.get("datasourceId");
        String targetId = datasourceId == null ? String.valueOf(payload.get("sourceKind"))
                : String.valueOf(datasourceId);
        String url = "DATASOURCE_TRY_RUN".equals(action)
                ? "/api/screen/admin/datasources/try-run" : "/api/screen/admin/datasources";
        Object before = payload.get("before");
        Object after = payload.get("after");
        persistAudit(action, "RPT_SCREEN_DATASOURCE", targetId, url, "POST", before, after,
                List.of(), List.of(), payload, reason);
    }

    /** 审计 API 会写 governance 审计表；高危写/试跑失败时抛错，调用方事务据此回滚。 */
    private void persistAudit(String action, String targetType, String targetId, String resourceUrl,
                              String requestMethod, Object before, Object after, Object added, Object removed,
                              Map<String, ?> payload, String reason) {
        if (auditApi == null) {
            throw new RptException(RptErrorCode.CROSS_MODULE_CALL_FAILED);
        }
        try {
            AuditLogCmd cmd = AuditLogCmd.builder()
                    .traceId(traceId())
                    .empId(currentUserApi.getCurrentEmpId())
                    .bizType("REPORT")
                    .bizAction(auditActionFor(action))
                    .resourceUrl(resourceUrl)
                    .requestMethod(requestMethod)
                    .requestParams(objectMapper.writeValueAsString(payload))
                    .responseStatus(200)
                    .reason(reason)
                    .targetType(targetType)
                    .targetId(targetId)
                    .beforeSnapshot(jsonOrNull(before))
                    .afterSnapshot(jsonOrNull(after))
                    .addedItems(jsonOrNull(added))
                    .removedItems(jsonOrNull(removed))
                    .build();
            auditApi.log(cmd);
        } catch (Exception ex) {
            // 数据源配置与试跑是高危行为；审计无法落库时回滚本次写入，不能用 logger 伪造审计。
            throw new RptException(RptErrorCode.CROSS_MODULE_CALL_FAILED, ex);
        }
    }

    /** 运行时拒绝也必须留痕；记录请求身份与拒绝码而不回显上下文敏感字段。 */
    private void persistRuntimeDenied(ScreenDataReqDTO req, RptException denied) {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("event", "SCREEN_RUNTIME_DENIED");
        payload.put("screenCode", req.getScreenCode());
        payload.put("blockId", req.getBlockId());
        payload.put("schemaVersion", req.getSchemaVersion());
        payload.put("requestedDatasourceId", req.getDsId());
        payload.put("denyCode", denied.getCode());
        try {
            // 拒绝审计必须由真实 /data POST 入口写入；审计故障不能把原本 fail-close 的 403
            // 覆盖成 500，也不能让调用方误以为身份校验出现系统错误。
            persistAudit("SCREEN_RUNTIME_DENIED", "RPT_SCREEN_RUNTIME", req.getScreenCode(), "/api/screen/data",
                    "POST", null, null, List.of(), List.of(), payload, denied.getCode());
        } catch (RptException auditFailure) {
            log.warn("[ScreenDatasource] 运行拒绝审计写入失败 screenCode={} denyCode={} cause={}",
                    req.getScreenCode(), denied.getCode(), auditFailure.getMessage());
        }
    }

    private Map<String, Object> datasourceSnapshot(RptScreenDatasource datasource) {
        return datasourceSnapshot(datasource, List.of());
    }

    /** 数据源审计快照必须包含所有可改变查询语义的字段以及完整发布引用。 */
    private Map<String, Object> datasourceSnapshot(RptScreenDatasource datasource, List<String> publishedReferences) {
        Map<String, Object> snapshot = new java.util.LinkedHashMap<>();
        snapshot.put("id", datasource.getId());
        snapshot.put("dsCode", datasource.getDsCode());
        snapshot.put("dsName", datasource.getDsName());
        snapshot.put("dsType", datasource.getDsType());
        snapshot.put("sourceKind", datasource.getSourceKind());
        snapshot.put("bizLine", datasource.getBizLine());
        snapshot.put("status", datasource.getStatus());
        snapshot.put("configJson", datasource.getConfigJson());
        snapshot.put("timeParamJson", datasource.getTimeParamJson());
        snapshot.put("remark", datasource.getRemark());
        snapshot.put("publishedReferences", publishedReferences == null ? List.of() : publishedReferences);
        return snapshot;
    }

    /** 高危配置/执行审计使用治理域可检索的真实动作，不泄露内部事件命名。 */
    private String auditActionFor(String event) {
        return switch (event) {
            case "DATASOURCE_DELETE" -> "DELETE";
            case "DATASOURCE_CREATE", "DATASOURCE_UPDATE" -> "CONFIG";
            case "DATASOURCE_TRY_RUN", "DATASOURCE_PROBE_COLUMNS" -> "EXECUTE_SQL";
            default -> "CONFIG";
        };
    }

    /** DTO 校验以外的服务层安全边界，避免内部调用绕过 @Valid。 */
    private void requireAuditReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new RptException(RptErrorCode.SCREEN_AUDIT_REASON_REQUIRED);
        }
    }

    /** 只复制数据源持久化字段；更新候选实体绝不复用/污染已读取的 before 实体。 */
    private RptScreenDatasource copyDatasource(RptScreenDatasource source) {
        RptScreenDatasource copy = new RptScreenDatasource();
        copy.setId(source.getId());
        copy.setDsCode(source.getDsCode());
        copy.setDsName(source.getDsName());
        copy.setDsType(source.getDsType());
        copy.setSourceKind(source.getSourceKind());
        copy.setBizLine(source.getBizLine());
        copy.setConfigJson(source.getConfigJson());
        copy.setTimeParamJson(source.getTimeParamJson());
        copy.setStatus(source.getStatus());
        copy.setRemark(source.getRemark());
        copy.setCreatedBy(source.getCreatedBy());
        copy.setUpdatedBy(source.getUpdatedBy());
        copy.setCreatedTime(source.getCreatedTime());
        copy.setUpdatedTime(source.getUpdatedTime());
        copy.setDeleted(source.getDeleted());
        return copy;
    }

    /** 查询语义变更的字段集合；名称和备注等展示元数据不触发引用屏安全矩阵复核。 */
    private boolean hasQuerySemanticChange(RptScreenDatasource before, RptScreenDatasource after) {
        return !java.util.Objects.equals(before.getDsType(), after.getDsType())
                || !java.util.Objects.equals(before.getSourceKind(), after.getSourceKind())
                || !normalizeBizLine(before.getBizLine()).equals(normalizeBizLine(after.getBizLine()))
                || !java.util.Objects.equals(before.getConfigJson(), after.getConfigJson())
                || !java.util.Objects.equals(before.getTimeParamJson(), after.getTimeParamJson())
                || !normalizedDatasourceStatus(before.getStatus()).equals(normalizedDatasourceStatus(after.getStatus()));
    }

    private String normalizedDatasourceStatus(String status) {
        return resolveDatasourceStatus(status, "ACTIVE");
    }

    /** 新建空值默认 ACTIVE；更新空值保留候选实体携带的旧状态；任何未知字面量均拒绝。 */
    private String resolveDatasourceStatus(String requestedStatus, String fallbackStatus) {
        String raw = requestedStatus == null || requestedStatus.isBlank() ? fallbackStatus : requestedStatus;
        String normalized = raw == null || raw.isBlank() ? "ACTIVE" : raw.trim().toUpperCase(Locale.ROOT);
        if (!"ACTIVE".equals(normalized) && !"DISABLED".equals(normalized)) {
            throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
        }
        return normalized;
    }

    /** 返回所有已发布引用屏，供前端提示先“新建副本→重新绑定→重新发布”的安全迁移路径。 */
    private RptException publishedDatasourceReferenceConflict(List<String> screenCodes) {
        String references = screenCodes == null || screenCodes.isEmpty()
                ? ""
                : "；已发布引用屏：" + String.join(",", screenCodes);
        return new RptException(RptErrorCode.SCREEN_DS_IN_USE,
                RptErrorCode.SCREEN_DS_IN_USE.getMsg() + references);
    }

    private String jsonOrNull(Object value) throws Exception {
        return value == null ? null : objectMapper.writeValueAsString(value);
    }

    private String traceId() {
        String traceId = MdcUtils.getTraceId();
        return traceId == null || traceId.isBlank()
                ? UUID.randomUUID().toString().replace("-", "") : traceId;
    }
}
