package com.bank.branch.platform.report.service.screen;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.governance.api.AuditApi;
import com.bank.branch.platform.governance.api.dto.AuditLogCmd;
import com.bank.branch.platform.report.dto.req.CanvasComponentDTO;
import com.bank.branch.platform.report.dto.req.ScreenBlockDTO;
import com.bank.branch.platform.report.dto.req.ScreenCanvasPublishReqDTO;
import com.bank.branch.platform.report.dto.req.ScreenCanvasRollbackReqDTO;
import com.bank.branch.platform.report.dto.req.ScreenCanvasSaveReqDTO;
import com.bank.branch.platform.report.dto.resp.ScreenCanvasEditorRespDTO;
import com.bank.branch.platform.report.dto.resp.ScreenCanvasSaveRespDTO;
import com.bank.branch.platform.report.dto.resp.ScreenPublishLogRespDTO;
import com.bank.branch.platform.report.entity.RptScreen;
import com.bank.branch.platform.report.entity.RptScreenBlock;
import com.bank.branch.platform.report.entity.RptScreenPublishLog;
import com.bank.branch.platform.report.enums.RptErrorCode;
import com.bank.branch.platform.report.exception.RptException;
import com.bank.branch.platform.report.mapper.RptScreenBlockMapper;
import com.bank.branch.platform.report.mapper.RptScreenCanvasMapper;
import com.bank.branch.platform.report.mapper.RptScreenDatasourceMapper;
import com.bank.branch.platform.report.mapper.RptScreenMapper;
import com.bank.branch.platform.report.mapper.RptScreenPublishLogMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 大屏画布双态服务实现(加载/保存草稿 + 发布/回滚/放弃草稿).
 *
 * <p>坐标模型:设计态恒 1920×1080 基准像素(幂等)。取数配置不进组件树,只在 RPT_SCREEN_BLOCK 行,
 * 组件树 ChartWidget 节点靠 blockId 弱关联(照搬 DataEase componentData/core_chart_view 分层边界)。
 *
 * <p>发布:结构化解析 DRAFT_JSON 收集 ChartWidget 的 blockId 集合,与本屏 block 行集合做交叉一致性
 * 校验(禁字符串 contains),合成渲染包(嵌入 bindSnapshots)写 PUBLISHED_JSON,并按屏滚动保留最近
 * {@link #PUBLISH_LOG_KEEP} 份归档(避坑:DataEase 双态互相覆盖后历史彻底丢失)。
 */
@Slf4j
@Service
public class ScreenCanvasServiceImpl implements ScreenCanvasService {

    /** 组件类型白名单(用户输入必须验证红线)。MapCenter(省级屏地图,Task10 渲染层已支持
     * component==='MapCenter' 独立渲染分支)不是 ChartWidget,不占 block 行、无 innerType,
     * 但仍需在此白名单内才能保存(Task11 三屏重配发现的缺口,补齐)。 */
    private static final Set<String> COMPONENT_TYPES = Set.of(
            "ChartWidget", "TextLabel", "ImageBox", "RectShape", "BorderDecor", "ClockWidget", "MapCenter");
    /** ChartWidget 的 innerType 白名单(复用现有 5 图表) */
    private static final Set<String> INNER_TYPES = Set.of(
            "METRIC_CARD", "LINE_TREND", "PIE_SHARE", "RANK_LIST", "FLOW_STATUS");
    /** 画布 JSON 上限 2MB(字符数近似) */
    private static final int MAX_JSON_LEN = 2 * 1024 * 1024;
    private static final int DESIGN_W = 1920;
    private static final int DESIGN_H = 1080;

    /** 每屏发布归档滚动保留份数(避坑:DataEase 双态互相覆盖后历史彻底丢失) */
    private static final int PUBLISH_LOG_KEEP = 10;

    private final RptScreenMapper screenMapper;
    private final RptScreenBlockMapper blockMapper;
    private final RptScreenDatasourceMapper dsMapper;
    private final RptScreenCanvasMapper canvasMapper;
    private final CurrentUserApi currentUserApi;
    private final RptScreenPublishLogMapper publishLogMapper;
    private final AuditApi auditApi;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ScreenCanvasServiceImpl(RptScreenMapper screenMapper,
                                   RptScreenBlockMapper blockMapper,
                                   RptScreenDatasourceMapper dsMapper,
                                   RptScreenCanvasMapper canvasMapper,
                                   CurrentUserApi currentUserApi,
                                   RptScreenPublishLogMapper publishLogMapper,
                                   AuditApi auditApi) {
        this.screenMapper = screenMapper;
        this.blockMapper = blockMapper;
        this.dsMapper = dsMapper;
        this.canvasMapper = canvasMapper;
        this.currentUserApi = currentUserApi;
        this.publishLogMapper = publishLogMapper;
        this.auditApi = auditApi;
    }

    @Override
    public ScreenCanvasEditorRespDTO loadCanvas(Long id) {
        RptScreen s = requireScreen(id);
        ScreenCanvasEditorRespDTO d = new ScreenCanvasEditorRespDTO();
        d.setScreenId(s.getId());
        d.setScreenCode(s.getScreenCode());
        d.setScreenName(s.getScreenName());
        d.setViewLevel(s.getViewLevel());
        d.setCanvasStyleJson(s.getCanvasStyleJson());
        d.setCanvasDraftJson(s.getCanvasDraftJson());
        d.setCanvasVersion(s.getCanvasVersion() == null ? 0 : s.getCanvasVersion());
        d.setPublishStatus(s.getPublishStatus() == null ? 0 : s.getPublishStatus());
        d.setBlocks(listBlocks(s.getId()));
        return d;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScreenCanvasSaveRespDTO saveCanvas(ScreenCanvasSaveReqDTO req) {
        RptScreen s = requireScreen(req.getScreenId());
        List<CanvasComponentDTO> comps = req.getComponents() == null ? List.of() : req.getComponents();

        // 1) 结构化校验:组件类型白名单 + innerType + 坐标/尺寸范围
        for (CanvasComponentDTO c : comps) {
            if (c.getComponent() == null || !COMPONENT_TYPES.contains(c.getComponent())) {
                throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID);
            }
            if ("ChartWidget".equals(c.getComponent())
                    && (c.getInnerType() == null || !INNER_TYPES.contains(c.getInnerType()))) {
                throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID);
            }
            validateStyle(c.getStyle());
        }

        // 2) blocks 增删改(upsert,保留 id,禁先删后插以免 blockId 引用失效)。
        //    仅 ChartWidget 参与;素材组件不占 block 行。
        List<CanvasComponentDTO> chartNodes = comps.stream()
                .filter(c -> "ChartWidget".equals(c.getComponent()))
                .collect(Collectors.toList());
        Set<Long> keepBlockIds = new HashSet<>();
        for (CanvasComponentDTO c : chartNodes) {
            if (c.getBlockId() != null) {
                // 归属校验:禁越权引用他屏 block
                RptScreenBlock existing = blockMapper.selectById(c.getBlockId());
                if (existing == null || !existing.getScreenId().equals(s.getId())) {
                    throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID);
                }
                existing.setComponentType(c.getInnerType());
                existing.setBindJson(nullToEmptyObj(c.getBindJson()));
                existing.setStyleJson(c.getStyleJson());
                existing.setDrillJson(c.getDrillJson());
                blockMapper.updateById(existing);
                keepBlockIds.add(existing.getId());
            } else {
                RptScreenBlock e = new RptScreenBlock();
                e.setScreenId(s.getId());
                // 区块表 region/rowNo/colNo/widthPct/heightPct 是旧行/块布局遗留 NOT NULL 列,
                // 画布态不再用它们定位,置默认值只为满足非空约束(旧布局字段保留只读备份)。
                e.setRegion("MAIN");
                e.setRowNo(1);
                e.setColNo(1);
                e.setWidthPct(100);
                e.setHeightPct(100);
                e.setComponentType(c.getInnerType());
                e.setBindJson(nullToEmptyObj(c.getBindJson()));
                e.setStyleJson(c.getStyleJson());
                e.setDrillJson(c.getDrillJson());
                blockMapper.insert(e);
                c.setBlockId(e.getId()); // 回吐 resolved id
                keepBlockIds.add(e.getId());
            }
        }
        // 删除本屏不再被引用的孤儿 block 行
        List<RptScreenBlock> owned = blockMapper.selectList(
                new LambdaQueryWrapper<RptScreenBlock>().eq(RptScreenBlock::getScreenId, s.getId()));
        for (RptScreenBlock b : owned) {
            if (!keepBlockIds.contains(b.getId())) {
                blockMapper.deleteById(b.getId());
            }
        }

        // 3) 序列化组件树(含 resolved blockId)+ 样式;上限校验
        String styleJson = writeJson(req.getCanvasStyle());
        String draftJson = buildDraftJson(comps);
        if (draftJson.length() > MAX_JSON_LEN || styleJson.length() > MAX_JSON_LEN) {
            throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID);
        }

        // 4) 乐观锁自增(WHERE canvas_version=expected);命中 0 行=冲突
        int rows = canvasMapper.bumpVersion(s.getId(), req.getExpectedVersion(),
                styleJson, draftJson, currentUserApi.getCurrentEmpId());
        if (rows == 0) {
            throw new RptException(RptErrorCode.SCREEN_CANVAS_CONFLICT);
        }

        ScreenCanvasSaveRespDTO resp = new ScreenCanvasSaveRespDTO();
        resp.setCanvasVersion(req.getExpectedVersion() + 1);
        resp.setCanvasDraftJson(draftJson);
        return resp;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publishCanvas(ScreenCanvasPublishReqDTO req) {
        RptScreen s = requireScreen(req.getScreenId());
        int curVersion = s.getCanvasVersion() == null ? 0 : s.getCanvasVersion();
        if (!Integer.valueOf(curVersion).equals(req.getExpectedVersion())) {
            throw new RptException(RptErrorCode.SCREEN_CANVAS_CONFLICT);
        }
        // 1) 结构化解析 DRAFT 收集 ChartWidget 的 blockId 集合(不做字符串 contains)
        JsonNode draft;
        try {
            draft = objectMapper.readTree(s.getCanvasDraftJson() == null ? "{}" : s.getCanvasDraftJson());
        } catch (Exception e) {
            throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID, e);
        }
        Set<Long> draftBlockIds = new HashSet<>();
        JsonNode comps = draft.path("components");
        if (comps.isArray()) {
            for (JsonNode n : comps) {
                if ("ChartWidget".equals(n.path("component").asText())) {
                    JsonNode bid = n.path("blockId");
                    if (bid.isNumber()) {
                        draftBlockIds.add(bid.asLong());
                    } else {
                        // ChartWidget 必须有 blockId
                        throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID);
                    }
                }
            }
        }
        // 2) 与本屏 block 行集合交叉一致性校验
        List<RptScreenBlock> rows = blockMapper.selectList(
                new LambdaQueryWrapper<RptScreenBlock>().eq(RptScreenBlock::getScreenId, s.getId()));
        Set<Long> rowIds = rows.stream().map(RptScreenBlock::getId).collect(Collectors.toSet());
        if (!rowIds.containsAll(draftBlockIds)) {
            // 草稿引用了不属于本屏的 block → 拒绝发布(发布渲染包与 block 行漂移防线)
            throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID);
        }
        // 3) 合成渲染包 = canvasStyle + components + bindSnapshots(从 block 行快照)
        ObjectNode pkg = objectMapper.createObjectNode();
        pkg.put("schemaVersion", 1);
        try {
            pkg.set("canvasStyle", objectMapper.readTree(
                    s.getCanvasStyleJson() == null ? "{}" : s.getCanvasStyleJson()));
            pkg.set("components", draft.path("components").isMissingNode()
                    ? objectMapper.createArrayNode() : draft.path("components"));
        } catch (Exception e) {
            throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID, e);
        }
        ObjectNode snaps = pkg.putObject("bindSnapshots");
        for (RptScreenBlock b : rows) {
            if (!draftBlockIds.contains(b.getId())) continue; // 只快照被引用的
            ObjectNode snap = snaps.putObject(String.valueOf(b.getId()));
            try {
                JsonNode bind = objectMapper.readTree(b.getBindJson() == null ? "{}" : b.getBindJson());
                snap.set("bind", bind);
                snap.put("componentType", b.getComponentType());
                snap.set("styleCfg", objectMapper.readTree(
                        b.getStyleJson() == null ? "{}" : b.getStyleJson()));
                snap.set("drill", objectMapper.readTree(
                        b.getDrillJson() == null ? "{}" : b.getDrillJson()));
            } catch (Exception e) {
                throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID, e);
            }
        }
        String publishedJson = pkg.toString();
        // 4) 状态机:发布后 publish_status=1;写 published + 审计
        int rowsUpd = canvasMapper.applyPublished(s.getId(), publishedJson, 1,
                currentUserApi.getCurrentEmpId());
        if (rowsUpd == 0) {
            throw new RptException(RptErrorCode.SCREEN_NOT_FOUND);
        }
        // 5) 归档 + 滚动保留最近 10 份
        RptScreenPublishLog logEntry = new RptScreenPublishLog();
        logEntry.setScreenId(s.getId());
        logEntry.setSnapshotJson(publishedJson);
        logEntry.setPublishedBy(currentUserApi.getCurrentEmpId());
        logEntry.setPublishedAt(LocalDateTime.now());
        publishLogMapper.insert(logEntry);
        trimPublishLogs(s.getId());
        // 6) 高危发布手工审计
        safelyAudit("SCREEN_PUBLISH", "publish screenId=" + s.getId(), "CONFIG");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rollbackCanvas(ScreenCanvasRollbackReqDTO req) {
        RptScreen s = requireScreen(req.getScreenId());
        RptScreenPublishLog logEntry = publishLogMapper.selectById(req.getPublishLogId());
        if (logEntry == null || !logEntry.getScreenId().equals(s.getId())) {
            throw new RptException(RptErrorCode.SCREEN_NOT_FOUND);
        }
        int rows = canvasMapper.applyPublished(s.getId(), logEntry.getSnapshotJson(), 1,
                currentUserApi.getCurrentEmpId());
        if (rows == 0) {
            throw new RptException(RptErrorCode.SCREEN_NOT_FOUND);
        }
        safelyAudit("SCREEN_ROLLBACK",
                "rollback screenId=" + s.getId() + " logId=" + req.getPublishLogId(), "CONFIG");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void discardDraft(Long screenId) {
        RptScreen s = requireScreen(screenId);
        // 放弃草稿:发布态组件树覆盖 DRAFT(结构同源,直接取 published.components)
        String draftJson;
        try {
            JsonNode pub = objectMapper.readTree(
                    s.getCanvasPublishedJson() == null ? "{}" : s.getCanvasPublishedJson());
            ObjectNode d = objectMapper.createObjectNode();
            d.put("schemaVersion", 1);
            d.set("components", pub.path("components").isMissingNode()
                    ? objectMapper.createArrayNode() : pub.path("components"));
            draftJson = d.toString();
        } catch (Exception e) {
            throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID, e);
        }
        RptScreen upd = new RptScreen();
        upd.setId(s.getId());
        upd.setCanvasDraftJson(draftJson);
        upd.setPublishStatus(1); // 放弃未发布修改 → 回到已发布态
        screenMapper.updateById(upd);
    }

    @Override
    public List<ScreenPublishLogRespDTO> listPublishLogs(Long screenId) {
        return publishLogMapper.selectList(new LambdaQueryWrapper<RptScreenPublishLog>()
                        .eq(RptScreenPublishLog::getScreenId, screenId)
                        .orderByDesc(RptScreenPublishLog::getId))
                .stream().map(e -> {
                    ScreenPublishLogRespDTO d = new ScreenPublishLogRespDTO();
                    d.setId(e.getId());
                    d.setScreenId(e.getScreenId());
                    d.setPublishedBy(e.getPublishedBy());
                    d.setPublishedAt(e.getPublishedAt());
                    return d;
                }).collect(Collectors.toList());
    }

    // ===== 内部 =====

    /** 按屏滚动保留最近 PUBLISH_LOG_KEEP 份,超出删最旧 */
    private void trimPublishLogs(Long screenId) {
        List<RptScreenPublishLog> all = publishLogMapper.selectList(
                new LambdaQueryWrapper<RptScreenPublishLog>()
                        .eq(RptScreenPublishLog::getScreenId, screenId)
                        .orderByDesc(RptScreenPublishLog::getId));
        for (int i = PUBLISH_LOG_KEEP; i < all.size(); i++) {
            publishLogMapper.deleteById(all.get(i).getId());
        }
    }

    /** 高危操作手工审计(失败仅告警不阻断,模块惯例,参考 ScreenDatasourceServiceImpl.safelyAudit) */
    private void safelyAudit(String action, String detail, String reason) {
        try {
            AuditLogCmd cmd = AuditLogCmd.builder()
                    .empId(currentUserApi.getCurrentEmpId())
                    .bizType("REPORT")
                    .bizAction(action)
                    .resourceUrl("/api/screen/admin/canvas")
                    .requestMethod("POST")
                    .requestParams(detail != null && detail.length() > 1000
                            ? detail.substring(0, 1000) : detail)
                    .responseStatus(200)
                    .reason(reason)
                    .build();
            auditApi.log(cmd);
        } catch (RuntimeException ex) {
            log.warn("[ScreenCanvasService] AuditApi.log 失败 cause={}", ex.getMessage());
        }
    }

    /** 坐标/尺寸数值范围:top/left ∈ [0, 设计基准],width/height ∈ [1, 设计基准] */
    private void validateStyle(java.util.Map<String, Object> style) {
        if (style == null) {
            throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID);
        }
        int top = intOf(style.get("top"));
        int left = intOf(style.get("left"));
        int width = intOf(style.get("width"));
        int height = intOf(style.get("height"));
        if (top < 0 || left < 0 || width < 1 || height < 1
                || left + width > DESIGN_W || top + height > DESIGN_H) {
            throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID);
        }
    }

    private int intOf(Object o) {
        if (o instanceof Number n) return n.intValue();
        throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID);
    }

    private String nullToEmptyObj(String json) {
        return json == null || json.isBlank() ? "{}" : json;
    }

    private String buildDraftJson(List<CanvasComponentDTO> comps) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("schemaVersion", 1);
        ArrayNode arr = root.putArray("components");
        for (CanvasComponentDTO c : comps) {
            arr.add(objectMapper.valueToTree(c));
        }
        return root.toString();
    }

    private String writeJson(Object o) {
        try {
            return o == null ? "{}" : objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID, e);
        }
    }

    private RptScreen requireScreen(Long id) {
        RptScreen s = screenMapper.selectById(id);
        if (s == null) {
            throw new RptException(RptErrorCode.SCREEN_NOT_FOUND);
        }
        return s;
    }

    private List<ScreenBlockDTO> listBlocks(Long screenId) {
        return blockMapper.selectList(new LambdaQueryWrapper<RptScreenBlock>()
                        .eq(RptScreenBlock::getScreenId, screenId))
                .stream().map(e -> {
                    ScreenBlockDTO d = new ScreenBlockDTO();
                    d.setId(e.getId());
                    d.setRegion(e.getRegion());
                    d.setRowNo(e.getRowNo());
                    d.setColNo(e.getColNo());
                    d.setWidthPct(e.getWidthPct());
                    d.setHeightPct(e.getHeightPct());
                    d.setComponentType(e.getComponentType());
                    d.setBindJson(e.getBindJson());
                    d.setStyleJson(e.getStyleJson());
                    d.setDrillJson(e.getDrillJson());
                    return d;
                }).collect(Collectors.toList());
    }
}
