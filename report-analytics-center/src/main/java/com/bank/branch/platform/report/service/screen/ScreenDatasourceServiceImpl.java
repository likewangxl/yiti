package com.bank.branch.platform.report.service.screen;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.governance.api.AuditApi;
import com.bank.branch.platform.governance.api.dto.AuditLogCmd;
import com.bank.branch.platform.report.dto.req.ScreenDataReqDTO;
import com.bank.branch.platform.report.dto.req.ScreenDatasourceSaveReqDTO;
import com.bank.branch.platform.report.dto.req.ScreenTryRunReqDTO;
import com.bank.branch.platform.report.dto.resp.ScreenDataRespDTO;
import com.bank.branch.platform.report.dto.resp.ScreenDatasourceRespDTO;
import com.bank.branch.platform.report.entity.RptScreenDatasource;
import com.bank.branch.platform.report.enums.RptErrorCode;
import com.bank.branch.platform.report.exception.RptException;
import com.bank.branch.platform.report.mapper.RptScreenBlockMapper;
import com.bank.branch.platform.report.mapper.RptScreenDatasourceMapper;
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

    private final RptScreenDatasourceMapper dsMapper;
    private final RptScreenBlockMapper blockMapper;
    private final ScreenQueryEngine engine;
    private final ScreenMetricSlotDao slotDao;
    private final CurrentUserApi currentUserApi;
    private final AuditApi auditApi;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ScreenDatasourceServiceImpl(RptScreenDatasourceMapper dsMapper,
                                       RptScreenBlockMapper blockMapper,
                                       ScreenQueryEngine engine,
                                       ScreenMetricSlotDao slotDao,
                                       CurrentUserApi currentUserApi,
                                       AuditApi auditApi) {
        this.dsMapper = dsMapper;
        this.blockMapper = blockMapper;
        this.engine = engine;
        this.slotDao = slotDao;
        this.currentUserApi = currentUserApi;
        this.auditApi = auditApi;
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
        ScreenDataRespDTO resp = engine.tryRun(probe.getSourceKind(), probe.getConfigJson(), dataReq);
        safelyAudit("EXECUTE_SQL", "tryRun kind=" + req.getSourceKind(), req.getReason());
        return resp;
    }

    @Override
    public ScreenDataRespDTO queryData(ScreenDataReqDTO req) {
        RptScreenDatasource ds = dsMapper.selectById(req.getDsId());
        if (ds == null || "DISABLED".equals(ds.getStatus())) {
            throw new RptException(RptErrorCode.SCREEN_DS_NOT_FOUND);
        }
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
                for (String code : codes) {
                    ScreenMetricSlotDao.MetricSlot slot = found.get(code);
                    if (slot == null || slot.valSlot() == null || !meta[2].equals(slot.baseDim())) {
                        throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
                    }
                    ObjectNode m = arr.addObject();
                    m.put("metricCode", slot.metricCode());
                    m.put("metricName", slot.metricName());
                    m.put("slot", slot.valSlot());
                }
                e.setConfigJson(newCfg.toString());
                e.setDsType("TIMESERIES");
            }
            case "KPI_RESULT" -> {
                String cycleType = cfg.path("cycleType").asText("MONTHLY");
                if (!KPI_CYCLE_TYPES.contains(cycleType)) {
                    throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
                }
                e.setConfigJson(req.getConfigJson());
                e.setDsType("TIMESERIES");
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
            return objectMapper.readTree(json == null ? "{}" : json);
        } catch (Exception ex) {
            throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID, ex);
        }
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
