package com.bank.branch.platform.report.service.screen;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.common.trace.MdcUtils;
import com.bank.branch.platform.governance.api.AuditApi;
import com.bank.branch.platform.governance.api.dto.AuditLogCmd;
import com.bank.branch.platform.report.dto.req.CanvasComponentDTO;
import com.bank.branch.platform.report.dto.req.ScreenBlockDTO;
import com.bank.branch.platform.report.dto.req.ScreenCanvasPublishReqDTO;
import com.bank.branch.platform.report.dto.req.ScreenCanvasDiscardReqDTO;
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
import com.bank.branch.platform.report.support.PublishedScreenPackageValidator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
     * 但仍需在此白名单内才能保存(Task11 三屏重配发现的缺口,补齐)。
     * Group(2026-07-17 画布多选成组容器):children 携带子组件(相对坐标),自身不占 block 行;
     * 校验/区块 upsert/发布 blockId 收集均须递归展开 children(见 flatten/collectChartBlockIds)。 */
    private static final Set<String> COMPONENT_TYPES = Set.of(
            "ChartWidget", "TextLabel", "ImageBox", "RectShape", "BorderDecor", "ClockWidget", "MapCenter",
            "Group",
            // 2026-07-17 素材装饰扩充:科技感标题条/装饰线/跑马灯(前端 widgets 注册表同步新增)
            "TitleBar", "DecorLine", "Marquee",
            // 2026-07-17 §5.3 全屏周期过滤器(运行时联动 TIMESERIES 区块;每屏最多 1 个,见 requireAtMostOnePeriodFilter)
            "PeriodFilter");

    /** 全屏周期过滤器组件名(每屏最多 1 个的保存/发布双侧校验共用) */
    private static final String PERIOD_FILTER = "PeriodFilter";
    /** ChartWidget 的 innerType 白名单（基础图表、KPI 图表与常用扩展图表；
     * 区块保存侧 component_type 白名单见 ScreenConfigServiceImpl 同步扩充）。 */
    private static final Set<String> INNER_TYPES = Set.of(
            "METRIC_CARD", "LINE_TREND", "PIE_SHARE", "RANK_LIST", "FLOW_STATUS",
            "BAR_COMPARE", "AREA_STACK", "GAUGE", "TABLE_LIST",
            "KPI_DETAIL_TABLE", "KPI_RADAR", "LIQUID_PROGRESS", "PROGRESS_LIST",
            "COMBO_CHART", "FUNNEL_CHART", "SCATTER_BUBBLE", "HEATMAP_MATRIX",
            "SUNBURST_CHART", "SPARKLINE_CARD");
    /** 时序图和启用钻取的图表只能绑定时序数据源。 */
    private static final Set<String> TIMESERIES_ONLY_INNER_TYPES =
            Set.of("LINE_TREND", "AREA_STACK", "SPARKLINE_CARD");
    /** KPI 专属图表只能绑定 KPI_DETAIL 数据源。 */
    private static final Set<String> KPI_DETAIL_ONLY_INNER_TYPES =
            Set.of("KPI_DETAIL_TABLE", "KPI_RADAR", "PROGRESS_LIST");
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

    @Autowired(required = false)
    private ScreenScopeAuthorizationService scopeAuthorizationService;

    @Autowired(required = false)
    private ScreenMapService screenMapService;

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

        // 1) 结构化校验:组件类型白名单 + innerType + 坐标/尺寸范围。
        //    Group 节点的 children 一并摊平校验(children 为相对组左上角坐标,恒落在
        //    [0,组尺寸] ⊆ [0,设计基准] 内,validateStyle 范围校验语义不变)。
        List<CanvasComponentDTO> flat = flatten(comps);
        for (CanvasComponentDTO c : flat) {
            if (c.getComponent() == null || !COMPONENT_TYPES.contains(c.getComponent())) {
                throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID);
            }
            if ("ChartWidget".equals(c.getComponent())
                    && (c.getInnerType() == null || !INNER_TYPES.contains(c.getInnerType()))) {
                throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID);
            }
            validateStyle(c.getStyle());
        }
        // 全屏周期过滤器每屏最多 1 个(spec 2026-07-17 §5.3):多个过滤器会互相覆盖 screen 级
        // globalPeriod,联动语义不可仲裁 → 按布局非法拒绝(计数用摊平列表,防 Group 嵌套绕过)
        requireAtMostOnePeriodFilter(flat.stream()
                .filter(c -> PERIOD_FILTER.equals(c.getComponent())).count());

        // 2) blocks 增删改(upsert,保留 id,禁先删后插以免 blockId 引用失效)。
        //    仅 ChartWidget 参与;素材组件不占 block 行。必须含 Group children 里的图表节点,
        //    否则组内图表的 block 行会被下方孤儿清理误删(blockId 引用失效)。
        List<CanvasComponentDTO> chartNodes = flat.stream()
                .filter(c -> "ChartWidget".equals(c.getComponent()))
                .collect(Collectors.toList());
        // 先完成 schema/条线/命名组主体校验，再触碰 block 行；错误配置不能留下半更新草稿。
        String unboundDraftJson = buildDraftJson(s, comps);
        validateCanvasSchema(unboundDraftJson);
        if (screenMapService != null) {
            screenMapService.validateDraftStructure(unboundDraftJson);
        }
        validateDraftDatasourceLines(s, chartNodes);

        List<RptScreenBlock> existingBlocksToUpdate = new ArrayList<>();
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
                // 已有块的真实 UPDATE 延后到画布 CAS 成功后，版本冲突不会先改草稿块。
                existingBlocksToUpdate.add(existing);
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
            }
        }

        // 3) 序列化组件树(含 resolved blockId)+ 样式;上限校验
        String styleJson = writeJson(req.getCanvasStyle());
        String draftJson = buildDraftJson(s, comps);
        validateCanvasSchema(draftJson);
        if (screenMapService != null) {
            screenMapService.validateDraftStructure(draftJson);
        }
        if (draftJson.length() > MAX_JSON_LEN || styleJson.length() > MAX_JSON_LEN) {
            throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID);
        }

        // 4) 乐观锁自增(WHERE canvas_version=expected);命中 0 行=冲突
        int rows = canvasMapper.bumpVersion(s.getId(), req.getExpectedVersion(),
                styleJson, draftJson, currentUserApi.getCurrentEmpId());
        if (rows == 0) {
            throw new RptException(RptErrorCode.SCREEN_CANVAS_CONFLICT);
        }

        // CAS 已成功后才写既有块并删除组件树中缺席的同屏草稿块。运行时 schema2 只读取
        // published bindSnapshots，已发布版本不会再查询这些草稿行；本事务提交前编辑态也不会
        // 观察到“新草稿 JSON 已写入但孤儿 block 未清理”的中间状态。
        for (RptScreenBlock existing : existingBlocksToUpdate) {
            blockMapper.updateById(existing);
        }
        Set<Long> retainedBlockIds = chartNodes.stream().map(CanvasComponentDTO::getBlockId)
                .collect(Collectors.toCollection(HashSet::new));
        Set<Long> staleBlockIds = blockMapper.selectList(new LambdaQueryWrapper<RptScreenBlock>()
                        .eq(RptScreenBlock::getScreenId, s.getId()))
                .stream().filter(block -> s.getId().equals(block.getScreenId()))
                .map(RptScreenBlock::getId).filter(blockId -> !retainedBlockIds.contains(blockId))
                .collect(Collectors.toCollection(HashSet::new));
        if (!staleBlockIds.isEmpty()) {
            // Mapper SQL 同时钳制 screen_id + id IN (...)，即使传入集合被污染也删不到他屏行。
            blockMapper.deleteByScreenIdAndIds(s.getId(), staleBlockIds);
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
        if (scopeAuthorizationService != null) {
            scopeAuthorizationService.validatePublishRoles(s);
        } else if ("NAMED_GROUP".equalsIgnoreCase(s.getOrgScopeMode())) {
            throw new RptException(RptErrorCode.SCREEN_SCOPE_INVALID);
        }
        boolean v2Map = screenMapService != null && screenMapService.hasV2Map(s.getCanvasDraftJson());
        if (v2Map && !"NAMED_GROUP".equalsIgnoreCase(s.getOrgScopeMode())) {
            // schema2 地图的真实点位来自命名机构组画像，不能挂在旧上下文屏上发布。
            throw new RptException(RptErrorCode.SCREEN_SCOPE_INVALID);
        }
        if (v2Map) {
            if (scopeAuthorizationService == null) {
                throw new RptException(RptErrorCode.SCREEN_SCOPE_INVALID);
            }
            Set<String> members = scopeAuthorizationService.configuredMemberCodes(s);
            var profiles = scopeAuthorizationService.activeProfiles(s, members);
            screenMapService.validateForPublish(s.getCanvasDraftJson(), members, profiles);
        }
        // 发布侧防线:草稿多个 PeriodFilter 同样拒绝(保存已拦,但历史草稿/旁路写入不可信,双侧校验)
        requireAtMostOnePeriodFilter(countComponentNodes(draft.path("components"), PERIOD_FILTER));
        Set<Long> draftBlockIds = new HashSet<>();
        // 递归收集:Group 节点的 children 里的 ChartWidget 同样引用 block 行,漏收会导致
        // 发布渲染包 bindSnapshots 缺失(组内图表线上无数据)
        collectChartBlockIds(draft.path("components"), draftBlockIds, RptErrorCode.SCREEN_LAYOUT_INVALID);
        // 2) 与本屏 block 行集合交叉一致性校验
        List<RptScreenBlock> rows = blockMapper.selectList(
                new LambdaQueryWrapper<RptScreenBlock>().eq(RptScreenBlock::getScreenId, s.getId()));
        Set<Long> rowIds = rows.stream().map(RptScreenBlock::getId).collect(Collectors.toSet());
        if (!rowIds.containsAll(draftBlockIds)) {
            // 草稿引用了不属于本屏的 block → 拒绝发布(发布渲染包与 block 行漂移防线)
            throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID);
        }
        // 发布必须重做屏/数据源业务条线矩阵校验，不能只依赖保存时的草稿检查。
        validatePublishedDatasourceLines(s, rows, draftBlockIds);
        // 3) 合成渲染包 = canvasStyle + components + bindSnapshots(从 block 行快照)
        ObjectNode pkg = objectMapper.createObjectNode();
        pkg.put("schemaVersion", requiresRuntimeSchemaV2(s, draft.path("components")) ? 2 : 1);
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
        validatePublishedSnapshot(s, publishedJson);
        requireAuditReason(req.getReason());
        CanvasBlockDiff publishDiff = canvasBlockDiff(s.getCanvasPublishedJson(), publishedJson);
        int rowsUpd = canvasMapper.applyPublishedCas(s.getId(), req.getExpectedVersion(), publishedJson, 1,
                currentUserApi.getCurrentEmpId());
        if (rowsUpd == 0) {
            throw new RptException(RptErrorCode.SCREEN_CANVAS_CONFLICT);
        }
        // 5) 归档 + 滚动保留最近 10 份
        RptScreenPublishLog logEntry = new RptScreenPublishLog();
        logEntry.setScreenId(s.getId());
        logEntry.setSnapshotJson(publishedJson);
        logEntry.setPublishedBy(currentUserApi.getCurrentEmpId());
        logEntry.setPublishedAt(LocalDateTime.now());
        publishLogMapper.insert(logEntry);
        trimPublishLogs(s.getId());
        // 6) 高危发布手工审计；审计失败抛异常将回滚发布、归档及快照写入。
        persistCanvasAudit("SCREEN_PUBLISH", s, "/api/screen/admin/canvas/publish", "POST",
                s.getCanvasPublishedJson(), publishedJson, publishDiff.added(), publishDiff.removed(),
                req.getExpectedVersion(), req.getReason());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rollbackCanvas(ScreenCanvasRollbackReqDTO req) {
        RptScreen s = requireScreen(req.getScreenId());
        int curVersion = s.getCanvasVersion() == null ? 0 : s.getCanvasVersion();
        if (!Integer.valueOf(curVersion).equals(req.getExpectedVersion())) {
            throw new RptException(RptErrorCode.SCREEN_CANVAS_CONFLICT);
        }
        RptScreenPublishLog logEntry = publishLogMapper.selectById(req.getPublishLogId());
        if (logEntry == null || !logEntry.getScreenId().equals(s.getId())) {
            throw new RptException(RptErrorCode.SCREEN_NOT_FOUND);
        }
        // 回滚不是盲写历史 JSON：当前条线/机构组/数据源状态已变化时必须重新 fail-close 校验。
        validatePublishedSnapshot(s, logEntry.getSnapshotJson());
        requireAuditReason(req.getReason());
        CanvasBlockDiff rollbackDiff = canvasBlockDiff(s.getCanvasPublishedJson(), logEntry.getSnapshotJson());
        int rows = canvasMapper.applyPublishedCas(s.getId(), req.getExpectedVersion(), logEntry.getSnapshotJson(), 1,
                currentUserApi.getCurrentEmpId());
        if (rows == 0) {
            throw new RptException(RptErrorCode.SCREEN_CANVAS_CONFLICT);
        }
        persistCanvasAudit("SCREEN_ROLLBACK", s, "/api/screen/admin/canvas/rollback", "POST",
                s.getCanvasPublishedJson(), logEntry.getSnapshotJson(), rollbackDiff.added(), rollbackDiff.removed(),
                req.getExpectedVersion(), req.getReason());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void discardDraft(ScreenCanvasDiscardReqDTO req) {
        RptScreen s = requireScreen(req.getScreenId());
        int curVersion = s.getCanvasVersion() == null ? 0 : s.getCanvasVersion();
        if (!Integer.valueOf(curVersion).equals(req.getExpectedVersion())) {
            throw new RptException(RptErrorCode.SCREEN_CANVAS_CONFLICT);
        }
        requireAuditReason(req.getReason());
        // 先用当前范围、条线及角色规则复核不可变发布快照；草稿行可能已被删除，不能作为依据。
        validatePublishedSnapshot(s, s.getCanvasPublishedJson());
        JsonNode published;
        String draftJson;
        List<PublishedBlockSnapshot> publishedBlocks;
        try {
            published = objectMapper.readTree(s.getCanvasPublishedJson());
            ObjectNode d = objectMapper.createObjectNode();
            d.put("schemaVersion", hasV2Map(published.path("components")) ? 2 : 1);
            d.set("components", published.path("components").isMissingNode()
                    ? objectMapper.createArrayNode() : published.path("components"));
            draftJson = d.toString();
            publishedBlocks = publishedBlocks(published);
        } catch (RptException e) {
            throw e;
        } catch (Exception e) {
            throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID, e);
        }
        // CAS 必须先成功，后续 block 恢复才会发生；冲突路径不触碰 block，事务失败也会回滚 CAS。
        if (canvasMapper.discardDraftCas(s.getId(), req.getExpectedVersion(), draftJson,
                currentUserApi.getCurrentEmpId()) == 0) {
            throw new RptException(RptErrorCode.SCREEN_CANVAS_CONFLICT);
        }
        restorePublishedBlocks(s.getId(), publishedBlocks);
        // 发布组件树是 discard 后草稿 block 的唯一真相。恢复后再删除新增/孤儿行，避免已删除
        // 的草稿图表在下一次编辑时借残留 block 重新出现；零 ChartWidget 时 retained 为空，会清空本屏行。
        Set<Long> retainedBlockIds = publishedBlocks.stream().map(PublishedBlockSnapshot::blockId)
                .collect(Collectors.toSet());
        Set<Long> orphanBlockIds = blockMapper.selectList(new LambdaQueryWrapper<RptScreenBlock>()
                        .eq(RptScreenBlock::getScreenId, s.getId()))
                .stream().filter(block -> s.getId().equals(block.getScreenId()))
                .map(RptScreenBlock::getId).filter(blockId -> !retainedBlockIds.contains(blockId))
                .collect(Collectors.toSet());
        if (!orphanBlockIds.isEmpty()) {
            blockMapper.deleteByScreenIdAndIds(s.getId(), orphanBlockIds);
        }
        CanvasBlockDiff discardDiff = canvasBlockDiff(s.getCanvasDraftJson(), draftJson);
        persistCanvasAudit("SCREEN_DISCARD_DRAFT", s, "/api/screen/admin/canvas/discard", "POST",
                s.getCanvasDraftJson(), draftJson, discardDiff.added(), discardDiff.removed(),
                req.getExpectedVersion(), req.getReason());
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

    /**
     * 由发布渲染包恢复可编辑 block 行。保存草稿允许删除缺席 block，但 published bindSnapshots
     * 是不可变真相；discard 后必须用同一 ID 恢复，不能生成新 ID 令组件树失联。
     */
    private List<PublishedBlockSnapshot> publishedBlocks(JsonNode published) {
        Map<Long, JsonNode> snapshots;
        try {
            snapshots = PublishedScreenPackageValidator.requireTrustedBindings(published);
        } catch (RuntimeException ex) {
            throw new RptException(RptErrorCode.SCREEN_PUBLISHED_SNAPSHOT_UNTRUSTED, ex);
        }
        List<PublishedBlockSnapshot> result = new ArrayList<>();
        for (Map.Entry<Long, JsonNode> entry : snapshots.entrySet()) {
            Long blockId = entry.getKey();
            JsonNode snapshot = entry.getValue();
            String componentType = snapshot.path("componentType").asText();
            JsonNode bind = snapshot.path("bind");
            if (componentType.isBlank() || !bind.isObject() || !bind.path("dsId").isIntegralNumber()) {
                throw new RptException(RptErrorCode.SCREEN_PUBLISHED_SNAPSHOT_UNTRUSTED);
            }
            result.add(new PublishedBlockSnapshot(blockId, componentType, jsonOf(bind),
                    jsonOfOrEmpty(snapshot.path("styleCfg")), jsonOfOrEmpty(snapshot.path("drill"))));
        }
        return result;
    }

    /** CAS 后才执行恢复；所有查询和写入均带当前 screenId，绝不接触其他屏的 block。 */
    private void restorePublishedBlocks(Long screenId, List<PublishedBlockSnapshot> snapshots) {
        if (snapshots.isEmpty()) {
            return;
        }
        Map<Long, RptScreenBlock> current = blockMapper.selectList(
                        new LambdaQueryWrapper<RptScreenBlock>().eq(RptScreenBlock::getScreenId, screenId))
                .stream().collect(Collectors.toMap(RptScreenBlock::getId, block -> block));
        for (PublishedBlockSnapshot snapshot : snapshots) {
            RptScreenBlock block = current.get(snapshot.blockId());
            if (block == null) {
                block = new RptScreenBlock();
                // IdType.AUTO 在非空 ID 时由 MyBatis-Plus 直接使用显式值；这是恢复发布包引用的唯一
                // 稳定 blockId 的必要条件，不能重新生成自增主键。
                block.setId(snapshot.blockId());
                block.setScreenId(screenId);
                block.setRegion("MAIN");
                block.setRowNo(1);
                block.setColNo(1);
                block.setWidthPct(100);
                block.setHeightPct(100);
                applySnapshot(block, snapshot);
                blockMapper.insert(block);
            } else {
                // 查询条件已钳制 screen_id；即使快照 ID 与他屏 ID 相同也不会取得他屏记录。
                applySnapshot(block, snapshot);
                blockMapper.updateById(block);
            }
        }
    }

    private void applySnapshot(RptScreenBlock block, PublishedBlockSnapshot snapshot) {
        block.setComponentType(snapshot.componentType());
        block.setBindJson(snapshot.bindJson());
        block.setStyleJson(snapshot.styleJson());
        block.setDrillJson(snapshot.drillJson());
    }

    private String jsonOf(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID, e);
        }
    }

    private String jsonOfOrEmpty(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? "{}" : jsonOf(node);
    }

    private record PublishedBlockSnapshot(Long blockId, String componentType, String bindJson,
                                          String styleJson, String drillJson) {
    }

    // ===== 内部 =====

    /**
     * 摊平组件树:顶层节点 + Group 的 children 全部纳入(设计器保证组不嵌套,此处仍防御性递归)。
     * 返回的列表持有原 DTO 引用——区块 upsert 对 children 图表节点 setBlockId 回吐后,
     * buildDraftJson 序列化顶层 comps 时嵌套 children 同步携带 resolved id。
     */
    private List<CanvasComponentDTO> flatten(List<CanvasComponentDTO> comps) {
        List<CanvasComponentDTO> out = new java.util.ArrayList<>();
        collectFlat(comps, out);
        return out;
    }

    private void collectFlat(List<CanvasComponentDTO> comps, List<CanvasComponentDTO> out) {
        if (comps == null) {
            return;
        }
        for (CanvasComponentDTO c : comps) {
            out.add(c);
            collectFlat(c.getChildren(), out);
        }
    }

    /** 每屏最多 1 个 PeriodFilter,超限按布局非法(RPT-43006)拒绝(保存/发布双侧共用) */
    private void requireAtMostOnePeriodFilter(long count) {
        if (count > 1) {
            throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID);
        }
    }

    /** 递归统计组件树里指定 component 的节点数(含 Group children,与 flatten 摊平语义一致) */
    private long countComponentNodes(JsonNode comps, String component) {
        if (comps == null || !comps.isArray()) {
            return 0;
        }
        long count = 0;
        for (JsonNode n : comps) {
            if (component.equals(n.path("component").asText())) {
                count++;
            }
            count += countComponentNodes(n.path("children"), component);
        }
        return count;
    }

    /** 递归收集 ChartWidget 的 blockId(含 Group children);ChartWidget 缺 blockId 视为非法草稿 */
    private void collectChartBlockIds(JsonNode comps, Set<Long> out, RptErrorCode errorCode) {
        if (comps == null || !comps.isArray()) {
            return;
        }
        for (JsonNode n : comps) {
            if ("ChartWidget".equals(n.path("component").asText())) {
                JsonNode bid = n.path("blockId");
                if (bid.isIntegralNumber() && bid.canConvertToLong() && bid.longValue() > 0
                        && out.add(bid.longValue())) {
                    // blockId 唯一：禁止 Set 折叠两个不同 ChartWidget 的同一身份。
                } else {
                    throw new RptException(errorCode);
                }
            }
            collectChartBlockIds(n.path("children"), out, errorCode);
        }
    }

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

    /** 发布、回滚、放弃草稿均必须真实写入治理审计表，保留结构化前后快照及差异。 */
    private void persistCanvasAudit(String action, RptScreen screen, String resourceUrl, String requestMethod,
                                    String beforeSnapshot, String afterSnapshot, List<?> addedItems,
                                    List<?> removedItems, Integer expectedVersion, String reason) {
        if (auditApi == null) {
            throw new RptException(RptErrorCode.CROSS_MODULE_CALL_FAILED);
        }
        try {
            String traceId = traceId();
            String operator = currentUserApi.getCurrentEmpId();
            Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("event", action);
            payload.put("screenId", screen.getId());
            payload.put("screenCode", screen.getScreenCode());
            payload.put("expectedVersion", expectedVersion);
            payload.put("reason", reason);
            payload.put("operator", operator);
            payload.put("traceId", traceId);
            AuditLogCmd cmd = AuditLogCmd.builder()
                    .traceId(traceId)
                    .empId(operator)
                    .bizType("REPORT")
                    .bizAction(canvasAuditAction(action))
                    .resourceUrl(resourceUrl)
                    .requestMethod(requestMethod)
                    .requestParams(objectMapper.writeValueAsString(payload))
                    .responseStatus(200)
                    .reason(reason)
                    .targetType("RPT_SCREEN")
                    .targetId(String.valueOf(screen.getId()))
                    .beforeSnapshot(beforeSnapshot)
                    .afterSnapshot(afterSnapshot)
                    .addedItems(objectMapper.writeValueAsString(addedItems))
                    .removedItems(objectMapper.writeValueAsString(removedItems))
                    .build();
            auditApi.log(cmd);
        } catch (Exception ex) {
            throw new RptException(RptErrorCode.CROSS_MODULE_CALL_FAILED, ex);
        }
    }

    /** 发布相关审计动作必须使用治理域可检索的真实动作，而非内部事件名。 */
    private String canvasAuditAction(String event) {
        return switch (event) {
            case "SCREEN_PUBLISH" -> "PUBLISH";
            case "SCREEN_ROLLBACK" -> "ROLLBACK";
            case "SCREEN_DISCARD_DRAFT" -> "CONFIG";
            default -> "CONFIG";
        };
    }

    /** 服务层二次校验，防止内部调用绕过 DTO 的 @NotBlank。 */
    private void requireAuditReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new RptException(RptErrorCode.SCREEN_AUDIT_REASON_REQUIRED);
        }
    }

    /**
     * 审计 diff 以画布组件树中的 blockId 为准，发布包在进入此处前已通过不可变快照双向校验。
     * 旧包仅用于 before 审计时不参与授权；其畸形身份不阻止受控重新发布，但不能制造虚假的 ID。
     */
    private CanvasBlockDiff canvasBlockDiff(String beforeJson, String afterJson) {
        Set<Long> before = auditComponentBlockIds(beforeJson);
        Set<Long> after = auditComponentBlockIds(afterJson);
        List<Long> added = after.stream().filter(id -> !before.contains(id)).sorted().toList();
        List<Long> removed = before.stream().filter(id -> !after.contains(id)).sorted().toList();
        return new CanvasBlockDiff(added, removed);
    }

    private Set<Long> auditComponentBlockIds(String canvasJson) {
        Set<Long> ids = new HashSet<>();
        try {
            collectAuditComponentBlockIds(objectMapper.readTree(canvasJson == null ? "{}" : canvasJson)
                    .path("components"), ids);
        } catch (Exception ignored) {
            // 畸形旧包不能作为运行/回滚身份依据；审计只保留可从结构中确定的正整数 ID。
        }
        return ids;
    }

    private void collectAuditComponentBlockIds(JsonNode components, Set<Long> ids) {
        if (components == null || !components.isArray()) {
            return;
        }
        for (JsonNode component : components) {
            if ("ChartWidget".equals(component.path("component").asText())) {
                JsonNode blockId = component.path("blockId");
                if (blockId.isIntegralNumber() && blockId.canConvertToLong() && blockId.longValue() > 0) {
                    ids.add(blockId.longValue());
                }
            }
            collectAuditComponentBlockIds(component.path("children"), ids);
        }
    }

    private record CanvasBlockDiff(List<Long> added, List<Long> removed) {
    }

    private String traceId() {
        String traceId = MdcUtils.getTraceId();
        return traceId == null || traceId.isBlank()
                ? UUID.randomUUID().toString().replace("-", "") : traceId;
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

    private String buildDraftJson(RptScreen screen, List<CanvasComponentDTO> comps) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("schemaVersion", requiresRuntimeSchemaV2(screen, comps) ? 2 : 1);
        ArrayNode arr = root.putArray("components");
        for (CanvasComponentDTO c : comps) {
            arr.add(objectMapper.valueToTree(c));
        }
        return root.toString();
    }

    private boolean hasV2Map(List<CanvasComponentDTO> comps) {
        if (comps == null) {
            return false;
        }
        for (CanvasComponentDTO c : comps) {
            if ("MapCenter".equals(c.getComponent()) && c.getPropValue() != null) {
                Object version = c.getPropValue().get("schemaVersion");
                if (version instanceof Number n && n.intValue() == 2) {
                    return true;
                }
            }
            if (hasV2Map(c.getChildren())) {
                return true;
            }
        }
        return false;
    }

    private boolean requiresRuntimeSchemaV2(RptScreen screen, List<CanvasComponentDTO> components) {
        return "NAMED_GROUP".equalsIgnoreCase(screen.getOrgScopeMode()) || hasV2Map(components);
    }

    private boolean hasV2Map(JsonNode components) {
        if (components == null || !components.isArray()) {
            return false;
        }
        for (JsonNode node : components) {
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

    private String writeJson(Object o) {
        try {
            return o == null ? "{}" : objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID, e);
        }
    }

    private void validatePublishedDatasourceLines(RptScreen screen,
                                                  List<RptScreenBlock> rows,
                                                  Set<Long> draftBlockIds) {
        // 未声明条线的历史屏按旧契约兼容；新屏保存后会有显式值，命名组屏无论如何都必须校验。
        boolean explicitLine = screen.getBizLine() != null && !screen.getBizLine().isBlank();
        boolean namedGroup = "NAMED_GROUP".equalsIgnoreCase(screen.getOrgScopeMode());
        if (!explicitLine && !namedGroup) {
            return;
        }
        String screenLine = normalizeBizLine(screen.getBizLine());
        for (RptScreenBlock block : rows) {
            if (!draftBlockIds.contains(block.getId())) {
                continue;
            }
            Long dsId = readDsId(block.getBindJson());
            if (dsId == null) {
                throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID);
            }
            var dataSource = dsMapper.selectById(dsId);
            if (dataSource == null || !isBizLineCompatible(screenLine, dataSource.getBizLine())) {
                throw new RptException(dataSource == null
                        ? RptErrorCode.SCREEN_DS_NOT_FOUND : RptErrorCode.SCREEN_BIZ_LINE_MISMATCH);
            }
            if (namedGroup && !isNamedGroupSafeDatasource(dataSource)) {
                throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
            }
            validateComponentDatasourceCompatibility(block.getComponentType(), block.getDrillJson(), dataSource);
        }
    }

    /** 保存画布也必须执行同一条线矩阵与命名组主体校验，不能等到发布才发现错误。 */
    private void validateDraftDatasourceLines(RptScreen screen, List<CanvasComponentDTO> chartNodes) {
        String screenLine = normalizeBizLine(screen.getBizLine());
        boolean namedGroup = "NAMED_GROUP".equalsIgnoreCase(screen.getOrgScopeMode());
        for (CanvasComponentDTO chart : chartNodes) {
            Long dsId = readDsId(chart.getBindJson());
            // 设计器可先放空图表；一旦声明绑定，保存时立即验证，避免非法绑定进入草稿。
            if (dsId == null) {
                continue;
            }
            var datasource = dsMapper.selectById(dsId);
            if (datasource == null) {
                throw new RptException(RptErrorCode.SCREEN_DS_NOT_FOUND);
            }
            if (!isBizLineCompatible(screenLine, datasource.getBizLine())) {
                throw new RptException(RptErrorCode.SCREEN_BIZ_LINE_MISMATCH);
            }
            if (namedGroup && !isNamedGroupSafeDatasource(datasource)) {
                throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
            }
            validateComponentDatasourceCompatibility(chart.getInnerType(), chart.getDrillJson(), datasource);
        }
    }

    /**
     * 画布成为唯一 block 写入口后，原配置保存的组件-数据源类型约束必须在这里继续 fail-close。
     */
    private void validateComponentDatasourceCompatibility(String componentType, String drillJson,
                                                          com.bank.branch.platform.report.entity.RptScreenDatasource datasource) {
        boolean needsTimeseries = TIMESERIES_ONLY_INNER_TYPES.contains(componentType) || drillEnabled(drillJson);
        if (needsTimeseries && !"TIMESERIES".equals(datasource.getDsType())) {
            throw new RptException(RptErrorCode.SCREEN_BLOCK_BIND_MISMATCH);
        }
        if (KPI_DETAIL_ONLY_INNER_TYPES.contains(componentType)
                && !"KPI_DETAIL".equals(datasource.getSourceKind())) {
            throw new RptException(RptErrorCode.SCREEN_BLOCK_BIND_MISMATCH);
        }
    }

    /**
     * 对已生成的发布包做运行同源校验。这里完全从包内 bindSnapshots 取 dsId，绝不读取当前
     * draft block 行，因此草稿删除/改绑不会破坏在线的历史发布态或回滚态。
     */
    private void validatePublishedSnapshot(RptScreen screen, String snapshotJson) {
        JsonNode root;
        try {
            root = objectMapper.readTree(snapshotJson == null ? "{}" : snapshotJson);
        } catch (Exception e) {
            throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID, e);
        }
        final Map<Long, JsonNode> trustedBindings;
        try {
            // 已发布包使用局部严格 parser/校验：JSON 浮点/字符串身份、重复字段、重复 Chart blockId
            // 或 bindSnapshots 非一一映射均不允许走回滚/放弃草稿或发布后的运行链路。
            root = PublishedScreenPackageValidator.read(snapshotJson);
            trustedBindings = PublishedScreenPackageValidator.requireTrustedBindings(root);
        } catch (Exception ex) {
            throw new RptException(RptErrorCode.SCREEN_PUBLISHED_SNAPSHOT_UNTRUSTED, ex);
        }
        validateCanvasSchema(root);
        if (scopeAuthorizationService != null) {
            scopeAuthorizationService.validatePublishRoles(screen);
        } else if ("NAMED_GROUP".equalsIgnoreCase(screen.getOrgScopeMode())) {
            throw new RptException(RptErrorCode.SCREEN_SCOPE_INVALID);
        }
        if (screenMapService != null) {
            screenMapService.validateDraftStructure(snapshotJson);
            if (screenMapService.hasV2Map(snapshotJson)) {
                if (!"NAMED_GROUP".equalsIgnoreCase(screen.getOrgScopeMode()) || scopeAuthorizationService == null) {
                    throw new RptException(RptErrorCode.SCREEN_SCOPE_INVALID);
                }
                Set<String> members = scopeAuthorizationService.configuredMemberCodes(screen);
                screenMapService.validateForPublish(snapshotJson, members,
                        scopeAuthorizationService.activeProfiles(screen, members));
            }
        }
        boolean explicitLine = screen.getBizLine() != null && !screen.getBizLine().isBlank();
        boolean namedGroup = "NAMED_GROUP".equalsIgnoreCase(screen.getOrgScopeMode());
        // 只有真正历史的无条线旧屏保留旧快照兼容；所有新屏和 NAMED_GROUP 均完整复核。
        if (!explicitLine && !namedGroup) {
            return;
        }
        String screenLine = normalizeBizLine(screen.getBizLine());
        for (JsonNode snapshot : trustedBindings.values()) {
            JsonNode dsNode = snapshot.path("bind").path("dsId");
            var datasource = dsMapper.selectById(dsNode.longValue());
            if (datasource == null) {
                throw new RptException(RptErrorCode.SCREEN_DS_NOT_FOUND);
            }
            if (!isBizLineCompatible(screenLine, datasource.getBizLine())) {
                throw new RptException(RptErrorCode.SCREEN_BIZ_LINE_MISMATCH);
            }
            if (namedGroup && !isNamedGroupSafeDatasource(datasource)) {
                throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
            }
        }
    }

    private void validateCanvasSchema(String canvasJson) {
        try {
            validateCanvasSchema(objectMapper.readTree(canvasJson == null ? "{}" : canvasJson));
        } catch (RptException e) {
            throw e;
        } catch (Exception e) {
            throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID, e);
        }
    }

    private void validateCanvasSchema(JsonNode root) {
        JsonNode version = root.path("schemaVersion");
        if (!version.isMissingNode() && !version.isNull()
                && (!version.isIntegralNumber() || !version.canConvertToInt()
                || (version.intValue() != 1 && version.intValue() != 2))) {
            throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID);
        }
        validateMapSchemas(root.path("components"));
    }

    private void validateMapSchemas(JsonNode components) {
        if (components == null || !components.isArray()) {
            return;
        }
        for (JsonNode node : components) {
            if ("MapCenter".equals(node.path("component").asText())) {
                JsonNode version = node.path("propValue").path("schemaVersion");
                if (!version.isMissingNode() && !version.isNull()
                        && (!version.isIntegralNumber() || !version.canConvertToInt()
                        || (version.intValue() != 1 && version.intValue() != 2))) {
                    throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID);
                }
            }
            validateMapSchemas(node.path("children"));
        }
    }

    private boolean isNamedGroupSafeDatasource(com.bank.branch.platform.report.entity.RptScreenDatasource ds) {
        if (!"WIDE_TABLE".equals(ds.getSourceKind())) {
            return false;
        }
        try {
            JsonNode cfg = objectMapper.readTree(ds.getConfigJson() == null ? "{}" : ds.getConfigJson());
            return "ORG_INDEX_RESULT".equals(cfg.path("table").asText())
                    && "org_code".equals(cfg.path("subjectCol").asText());
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isBizLineCompatible(String screenLine, String dataSourceLine) {
        String dsLine = normalizeBizLine(dataSourceLine);
        return switch (screenLine) {
            case "CORP" -> "CORP".equals(dsLine) || "COMMON".equals(dsLine);
            case "RETAIL" -> "RETAIL".equals(dsLine) || "COMMON".equals(dsLine);
            case "COMMON" -> "COMMON".equals(dsLine);
            default -> false;
        };
    }

    private boolean requiresRuntimeSchemaV2(RptScreen screen, JsonNode components) {
        return "NAMED_GROUP".equalsIgnoreCase(screen.getOrgScopeMode()) || hasV2Map(components);
    }

    private Long readDsId(String bindJson) {
        try {
            JsonNode node = objectMapper.readTree(bindJson == null ? "{}" : bindJson).path("dsId");
            return node.isNumber() ? node.asLong() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private boolean drillEnabled(String drillJson) {
        try {
            return objectMapper.readTree(drillJson == null ? "{}" : drillJson)
                    .path("drillEnabled").asBoolean(false);
        } catch (Exception e) {
            return false;
        }
    }

    private String normalizeBizLine(String value) {
        return value == null || value.isBlank() ? "COMMON" : value.trim().toUpperCase(Locale.ROOT);
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
