package com.bank.branch.platform.report.service.screen;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.ResourceApi;
import com.bank.branch.platform.common.trace.MdcUtils;
import com.bank.branch.platform.governance.api.AuditApi;
import com.bank.branch.platform.governance.api.dto.AuditLogCmd;
import com.bank.branch.platform.report.dto.req.MapPointDTO;
import com.bank.branch.platform.report.dto.req.ScreenBlockDTO;
import com.bank.branch.platform.report.dto.req.ScreenCreateReqDTO;
import com.bank.branch.platform.report.dto.req.ScreenMetadataUpdateReqDTO;
import com.bank.branch.platform.report.dto.req.ScreenSaveReqDTO;
import com.bank.branch.platform.report.dto.resp.ScreenDetailRespDTO;
import com.bank.branch.platform.report.dto.resp.ScreenViewRespDTO;
import com.bank.branch.platform.report.entity.RptScreen;
import com.bank.branch.platform.report.entity.RptScreenAccessRole;
import com.bank.branch.platform.report.entity.RptScreenBlock;
import com.bank.branch.platform.report.entity.RptScreenDatasource;
import com.bank.branch.platform.report.entity.RptScreenMapPoint;
import com.bank.branch.platform.report.enums.RptErrorCode;
import com.bank.branch.platform.report.exception.RptException;
import com.bank.branch.platform.report.mapper.RptScreenBlockMapper;
import com.bank.branch.platform.report.mapper.RptScreenCanvasMapper;
import com.bank.branch.platform.report.mapper.RptScreenDatasourceMapper;
import com.bank.branch.platform.report.mapper.RptScreenMapPointMapper;
import com.bank.branch.platform.report.mapper.RptScreenAccessRoleMapper;
import com.bank.branch.platform.report.mapper.RptScreenMapper;
import com.bank.branch.platform.report.support.PublishedScreenPackageValidator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 大屏布局/区块/地图点位配置服务实现.
 */
@Slf4j
@Service
public class ScreenConfigServiceImpl implements ScreenConfigService {

    private static final Set<String> VIEW_LEVELS = Set.of("PROVINCE", "BRANCH", "PERSON");
    private static final Set<String> BIZ_LINES = Set.of("CORP", "RETAIL", "COMMON");
    private static final Set<String> SCOPE_MODES = Set.of("LEGACY_CONTEXT", "NAMED_GROUP");
    /** 运行时查询以 ACTIVE 精确过滤，持久化状态必须先在写入边界规范为该枚举。 */
    private static final Set<String> SCREEN_STATUSES = Set.of("ACTIVE", "DISABLED");
    private static final Set<String> REGIONS = Set.of("LEFT", "MAIN", "RIGHT");
    /** 区块 component_type 白名单（基础 5 + spec 2026-07-17 §5.1 扩充 8；画布 innerType 白名单
     * 见 ScreenCanvasServiceImpl 同步扩充） */
    private static final Set<String> COMPONENT_TYPES = Set.of(
            "METRIC_CARD", "LINE_TREND", "PIE_SHARE", "RANK_LIST", "FLOW_STATUS",
            "BAR_COMPARE", "AREA_STACK", "GAUGE", "TABLE_LIST",
            "KPI_DETAIL_TABLE", "KPI_RADAR", "LIQUID_PROGRESS", "PROGRESS_LIST");
    /** 需要时序型数据源的组件（needTimeseries 联动，违规 43005） */
    private static final Set<String> TIMESERIES_ONLY_COMPONENTS = Set.of("LINE_TREND", "AREA_STACK");
    /** 仅可绑 source_kind=KPI_DETAIL 数据源的 KPI 专属组件（needKinds 联动，spec §5.1，违规 43005） */
    private static final Set<String> KPI_DETAIL_ONLY_COMPONENTS =
            Set.of("KPI_DETAIL_TABLE", "KPI_RADAR", "PROGRESS_LIST");

    private final RptScreenMapper screenMapper;
    private final RptScreenBlockMapper blockMapper;
    private final RptScreenDatasourceMapper dsMapper;
    private final RptScreenMapPointMapper pointMapper;
    private final CurrentUserApi currentUserApi;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 可选字段注入用于兼容既有 Mockito 构造器；生产 Spring 上下文会提供这些 Bean。 */
    @Autowired(required = false)
    private RptScreenAccessRoleMapper accessRoleMapper;

    @Autowired(required = false)
    private ScreenScopeAuthorizationService scopeAuthorizationService;

    @Autowired(required = false)
    private ScreenMapService screenMapService;

    /** 草稿预览必须具备画布管理读取资源；生产上下文缺少权限适配器时 fail-close。 */
    @Autowired(required = false)
    private ResourceApi resourceApi;

    @Autowired(required = false)
    private AuditApi auditApi;

    /** 高危角色白名单与画布共用 canvas_version 的 CAS mapper。 */
    @Autowired(required = false)
    private RptScreenCanvasMapper canvasMapper;

    public ScreenConfigServiceImpl(RptScreenMapper screenMapper,
                                   RptScreenBlockMapper blockMapper,
                                   RptScreenDatasourceMapper dsMapper,
                                   RptScreenMapPointMapper pointMapper,
                                   CurrentUserApi currentUserApi) {
        this.screenMapper = screenMapper;
        this.blockMapper = blockMapper;
        this.dsMapper = dsMapper;
        this.pointMapper = pointMapper;
        this.currentUserApi = currentUserApi;
    }

    @Override
    public List<ScreenDetailRespDTO> listScreens() {
        LambdaQueryWrapper<RptScreen> qw = new LambdaQueryWrapper<RptScreen>().orderByDesc(RptScreen::getId);
        return screenMapper.selectList(qw).stream().map(s -> toDetail(s, null)).collect(Collectors.toList());
    }

    @Override
    public ScreenDetailRespDTO getScreen(Long id) {
        RptScreen s = requireScreen(id);
        return toDetail(s, listBlocks(s.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createScreen(ScreenCreateReqDTO req) {
        ScreenMetadata metadata = metadataOf(req);
        validateMetadata(metadata, null);
        RptScreen s = new RptScreen();
        s.setScreenCode(metadata.screenCode() == null || metadata.screenCode().isBlank()
                    ? "SCR_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT)
                    : metadata.screenCode().trim());
        s.setCreatedBy(currentUserApi.getCurrentEmpId());
        applyScreenFields(s, metadata, true);
        s.setUpdatedBy(currentUserApi.getCurrentEmpId());
        // 创建接口只处理屏元数据；角色需经 PERMISSION_CHANGE，画布和 block 需经 canvas/save。
        validateScopeConfiguration(s, List.of(), false);
        ensureScreenCodeUnique(s);
        try {
            // 预检只能降低常见冲突；active_screen_code 唯一键才是并发创建的最终裁决。
            screenMapper.insert(s);
        } catch (DuplicateKeyException ex) {
            throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID, ex);
        }
        return s.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long updateScreenMetadata(Long id, ScreenMetadataUpdateReqDTO req) {
        if (req.getExpectedVersion() == null) {
            throw new RptException(RptErrorCode.SCREEN_CANVAS_CONFLICT);
        }
        if (req.getReason() == null || req.getReason().isBlank()) {
            throw new RptException(RptErrorCode.SCREEN_AUDIT_REASON_REQUIRED);
        }
        RptScreen before = requireScreen(id);
        ScreenMetadata metadata = metadataOf(req);
        validateMetadata(metadata, before);
        // 不直接改 managed entity：下面只把候选元数据传给 allowlist CAS SQL，避免未来字段增加时
        // 被 updateById 隐式覆盖草稿、发布包或发布状态。
        RptScreen candidate = metadataCandidate(before);
        if (metadata.screenCode() != null && !metadata.screenCode().isBlank()) {
            candidate.setScreenCode(metadata.screenCode().trim());
        }
        applyScreenFields(candidate, metadata, false);
        candidate.setUpdatedBy(currentUserApi.getCurrentEmpId());
        validateScopeConfiguration(candidate, listAccessRoleCodes(candidate.getId()), false);
        // 范围/条线变更不得静默使既有草稿 block 越权；仅只读校验，不重建、更新或删除 block。
        // 发布快照同样必须复核：在线 status=1/2 只读取其 bindSnapshots，不能只检查当前草稿行。
        validateExistingBlockBindings(candidate);
        ensureScreenCodeUnique(candidate);
        try {
            if (canvasMapper == null || canvasMapper.updateMetadataCas(candidate, req.getExpectedVersion(),
                    currentUserApi.getCurrentEmpId()) == 0) {
                throw new RptException(RptErrorCode.SCREEN_CANVAS_CONFLICT);
            }
        } catch (DuplicateKeyException ex) {
            // 改编码同样可能与并发新建/更新相撞，不能把底层约束异常暴露为 500。
            throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID, ex);
        }
        persistMetadataAudit(before, candidate, req.getExpectedVersion(), req.getReason());
        return candidate.getId();
    }

    /**
     * Java 兼容入口：保留旧调用方的编译兼容，但此 DTO 已移除 blocks，绝不会再走区块保存。
     * HTTP 管理端请使用 createScreen / updateScreenMetadata 两个明确端点。
     */
    @Override
    @Deprecated(since = "2026-08-11", forRemoval = false)
    @Transactional(rollbackFor = Exception.class)
    public Long saveScreen(ScreenSaveReqDTO req) {
        if (req == null) {
            throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID);
        }
        if (req.getId() == null) {
            ScreenCreateReqDTO create = new ScreenCreateReqDTO();
            copyMetadata(req, create);
            return createScreen(create);
        }
        ScreenMetadataUpdateReqDTO update = new ScreenMetadataUpdateReqDTO();
        copyMetadata(req, update);
        // 此 Java 兼容入口没有 HTTP 请求体可提供版本/原因；显式读取当前版本并留下固定兼容原因，
        // 仍通过同一 allowlist CAS + 审计链路，且绝不会接收 block/角色字段。
        RptScreen current = requireScreen(req.getId());
        update.setExpectedVersion(current.getCanvasVersion() == null ? 0 : current.getCanvasVersion());
        update.setReason("LEGACY_JAVA_METADATA_COMPAT");
        return updateScreenMetadata(req.getId(), update);
    }

    /** screenCode 唯一性（deleted=0 内，排除自身）。 */
    private void ensureScreenCodeUnique(RptScreen s) {
        LambdaQueryWrapper<RptScreen> dup = new LambdaQueryWrapper<RptScreen>()
                .eq(RptScreen::getScreenCode, s.getScreenCode())
                .ne(RptScreen::getId, s.getId());
        if (screenMapper.selectCount(dup) > 0) {
            throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteScreen(Long id) {
        requireScreen(id);
        screenMapper.deleteById(id);
        blockMapper.delete(new LambdaQueryWrapper<RptScreenBlock>().eq(RptScreenBlock::getScreenId, id));
    }

    @Override
    public List<MapPointDTO> listMapPoints() {
        return pointMapper.selectList(new LambdaQueryWrapper<RptScreenMapPoint>()
                        .orderByAsc(RptScreenMapPoint::getOrgCode))
                .stream().map(this::toPointDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveMapPoints(List<MapPointDTO> points) {
        // 覆盖式保存语义：null 视为非法入参直接拒绝（防止误清全表点位且无恢复手段）；
        // 空列表 [] 才是"清空全部点位"的合法表达，允许继续执行下面的删除。
        if (points == null) {
            throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID);
        }
        pointMapper.delete(new LambdaQueryWrapper<>());
        for (MapPointDTO p : points) {
            RptScreenMapPoint e = new RptScreenMapPoint();
            e.setOrgCode(p.getOrgCode());
            e.setOrgName(p.getOrgName());
            e.setLng(p.getLng());
            e.setLat(p.getLat());
            e.setTargetScreenCode(p.getTargetScreenCode() == null || p.getTargetScreenCode().isBlank()
                    ? "SCR_BRANCH" : p.getTargetScreenCode());
            e.setStatus(p.getStatus() == null || p.getStatus().isBlank() ? "ACTIVE" : p.getStatus());
            pointMapper.insert(e);
        }
    }

    @Override
    public ScreenViewRespDTO getViewByCode(String screenCode) {
        List<RptScreen> hits = screenMapper.selectList(new LambdaQueryWrapper<RptScreen>()
                .eq(RptScreen::getScreenCode, screenCode)
                .eq(RptScreen::getStatus, "ACTIVE"));
        if (hits.size() != 1) {
            throw new RptException(RptErrorCode.SCREEN_NOT_FOUND);
        }
        RptScreen s = hits.get(0);
        Set<String> authorizedOrgCodes;
        try {
            authorizedOrgCodes = authorizeRuntime(s);
        } catch (RptException denied) {
            persistRuntimeDeniedAudit(s, "/api/screen/view/" + screenCode, denied);
            throw denied;
        }
        ScreenViewRespDTO view = new ScreenViewRespDTO();
        view.setScreen(toDetail(s, null));
        view.setOrgScopeMode(normalizeScopeMode(s.getOrgScopeMode()));
        view.setRuntimeSchemaVersion(runtimeSchemaVersion(s, s.getCanvasPublishedJson()));
        view.setBlocks(listBlocks(s.getId()));
        if ("PROVINCE".equals(s.getViewLevel()) && !hasV2Map(s)) {
            view.setMapPoints(pointMapper.selectList(new LambdaQueryWrapper<RptScreenMapPoint>()
                            .eq(RptScreenMapPoint::getStatus, "ACTIVE"))
                    .stream().map(this::toPointDto).collect(Collectors.toList()));
        } else {
            view.setMapPoints(List.of());
        }
        if (hasV2Map(s) && scopeAuthorizationService != null && screenMapService != null) {
            var profiles = scopeAuthorizationService.activeProfiles(s, authorizedOrgCodes);
            view.setMapPackage(screenMapService.render(
                    screenMapService.findV2MapConfig(s.getCanvasPublishedJson()), authorizedOrgCodes, profiles));
        }
        return view;
    }

    @Override
    public com.bank.branch.platform.report.dto.resp.ScreenRenderRespDTO
            getRenderByCode(String screenCode, String state) {
        List<RptScreen> hits = screenMapper.selectList(new LambdaQueryWrapper<RptScreen>()
                .eq(RptScreen::getScreenCode, screenCode)
                .eq(RptScreen::getStatus, "ACTIVE"));
        if (hits.size() != 1) {
            throw new RptException(RptErrorCode.SCREEN_NOT_FOUND);
        }
        RptScreen s = hits.get(0);
        Set<String> authorizedOrgCodes;
        try {
            authorizedOrgCodes = authorizeRuntime(s);
        } catch (RptException denied) {
            persistRuntimeDeniedAudit(s, "/api/screen/view/" + screenCode, denied);
            throw denied;
        }
        var d = new com.bank.branch.platform.report.dto.resp.ScreenRenderRespDTO();
        d.setScreenId(s.getId());
        d.setScreenCode(s.getScreenCode());
        d.setScreenName(s.getScreenName());
        d.setViewLevel(s.getViewLevel());
        d.setOrgScopeMode(normalizeScopeMode(s.getOrgScopeMode()));
        // mapPoints 沿用旧 getViewByCode 的实时查询语义:仅 PROVINCE 屏现查现填,不烘焙进渲染包,
        // published/draft 两态一致(点位数据独立于画布双态)。
        if ("PROVINCE".equals(s.getViewLevel()) && !hasV2Map(s)) {
            d.setMapPoints(pointMapper.selectList(new LambdaQueryWrapper<RptScreenMapPoint>()
                            .eq(RptScreenMapPoint::getStatus, "ACTIVE"))
                    .stream().map(this::toPointDto).collect(Collectors.toList()));
        } else {
            d.setMapPoints(List.of());
        }
        boolean draft = "draft".equalsIgnoreCase(state);
        if (draft) {
            if (resourceApi == null
                    || !resourceApi.hasResourcePermission(currentUserApi.getCurrentEmpId(), "R_RPT_SCR_CV_GET")) {
                // 普通整屏读取权限不能读取未发布草稿，避免设计态内容成为绕过管理端门禁的旁路。
                RptException denied = new RptException(RptErrorCode.SCREEN_ACCESS_DENIED);
                persistRuntimeDeniedAudit(s, "/api/screen/view/" + screenCode, denied);
                throw denied;
            }
            // 草稿态:临时合成一个只含 components 的渲染包(bindSnapshots 由前端设计器内已持有 block,
            // 或运行时按 blockId 走 /api/screen/data 实时取数);此处直投 draft 组件树 + style。
            d.setRenderPackageJson(composeDraftPreview(s));
            d.setState("draft");
        } else {
            d.setRenderPackageJson(s.getCanvasPublishedJson());
            d.setState("published");
        }
        d.setRuntimeSchemaVersion(runtimeSchemaVersion(s, d.getRenderPackageJson()));
        if (screenMapService != null && screenMapService.hasV2Map(d.getRenderPackageJson())
                && scopeAuthorizationService != null) {
            var profiles = scopeAuthorizationService.activeProfiles(s, authorizedOrgCodes);
            d.setMapPackage(screenMapService.render(
                    screenMapService.findV2MapConfig(d.getRenderPackageJson()), authorizedOrgCodes, profiles));
        }
        return d;
    }

    private String composeDraftPreview(RptScreen s) {
        try {
            com.fasterxml.jackson.databind.node.ObjectNode pkg = objectMapper.createObjectNode();
            pkg.set("canvasStyle", objectMapper.readTree(
                    s.getCanvasStyleJson() == null ? "{}" : s.getCanvasStyleJson()));
            com.fasterxml.jackson.databind.JsonNode draft = objectMapper.readTree(
                    s.getCanvasDraftJson() == null ? "{}" : s.getCanvasDraftJson());
            pkg.set("components", draft.path("components").isMissingNode()
                    ? objectMapper.createArrayNode() : draft.path("components"));
            pkg.putObject("bindSnapshots"); // 草稿预览留空,ChartWidget 走实时取数
            pkg.put("schemaVersion", hasV2MapInComponents(pkg.path("components")) ? 2 : 1);
            return pkg.toString();
        } catch (Exception e) {
            throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID, e);
        }
    }

    // ===== 内部 =====

    /** 创建/更新共同的元数据校验；区块不属于此入口。 */
    private void validateMetadata(ScreenMetadata metadata, RptScreen existing) {
        if (metadata.screenName() == null || metadata.screenName().isBlank()
                || !VIEW_LEVELS.contains(metadata.viewLevel())) {
            throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID);
        }
        boolean create = existing == null;
        if (create && (metadata.bizLine() == null || metadata.bizLine().isBlank())) {
            throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID);
        }
        if (create && (metadata.orgScopeMode() == null || metadata.orgScopeMode().isBlank())) {
            throw new RptException(RptErrorCode.SCREEN_SCOPE_INVALID);
        }
        String businessLine = metadata.bizLine() == null || metadata.bizLine().isBlank()
                ? normalizeBizLine(existing == null ? null : existing.getBizLine())
                : normalizeBizLine(metadata.bizLine());
        if (!BIZ_LINES.contains(businessLine)) {
            throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID);
        }
        String scopeMode = metadata.orgScopeMode() == null || metadata.orgScopeMode().isBlank()
                ? normalizeScopeMode(existing == null ? null : existing.getOrgScopeMode())
                : normalizeScopeMode(metadata.orgScopeMode());
        if (!SCOPE_MODES.contains(scopeMode)) {
            throw new RptException(RptErrorCode.SCREEN_SCOPE_INVALID);
        }
        // 在元数据校验阶段提前拒绝未知状态，避免后续 CAS/insert 才暴露脏值。
        resolveScreenStatus(metadata.status(), existing == null ? null : existing.getStatus());
    }

    /**
     * 范围和条线更新同时只读检查草稿 block 与不可变发布快照的绑定。两者任一越权都拒绝；
     * 此入口绝不重建、更新或删除 block。
     */
    private void validateExistingBlockBindings(RptScreen screen) {
        List<BlockBinding> bindings = new ArrayList<>();
        for (ScreenBlockDTO block : listBlocks(screen.getId())) {
            Long datasourceId = readDsId(block.getBindJson());
            if (datasourceId == null) {
                throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID);
            }
            bindings.add(new BlockBinding(datasourceId, block.getComponentType(), block.getDrillJson()));
        }
        bindings.addAll(readPublishedBindings(screen, screen.getCanvasPublishedJson()));
        if (bindings.isEmpty()) {
            return;
        }
        Set<Long> datasourceIds = bindings.stream().map(BlockBinding::datasourceId).collect(Collectors.toSet());
        Map<Long, RptScreenDatasource> datasourceById = dsMapper.selectBatchIds(datasourceIds).stream()
                .collect(Collectors.toMap(RptScreenDatasource::getId, Function.identity()));
        for (BlockBinding binding : bindings) {
            RptScreenDatasource datasource = datasourceById.get(binding.datasourceId());
            if (datasource == null) {
                throw new RptException(RptErrorCode.SCREEN_DS_NOT_FOUND);
            }
            if (!isBizLineCompatible(normalizeBizLine(screen.getBizLine()),
                    normalizeBizLine(datasource.getBizLine()))) {
                throw new RptException(RptErrorCode.SCREEN_BIZ_LINE_MISMATCH);
            }
            if ("NAMED_GROUP".equals(normalizeScopeMode(screen.getOrgScopeMode()))
                    && !isNamedGroupSafeDatasource(datasource)) {
                throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
            }
            boolean needsTimeseries = TIMESERIES_ONLY_COMPONENTS.contains(binding.componentType())
                    || drillEnabled(binding.drillJson());
            if (needsTimeseries && !"TIMESERIES".equals(datasource.getDsType())) {
                throw new RptException(RptErrorCode.SCREEN_BLOCK_BIND_MISMATCH);
            }
            if (KPI_DETAIL_ONLY_COMPONENTS.contains(binding.componentType())
                    && !"KPI_DETAIL".equals(datasource.getSourceKind())) {
                throw new RptException(RptErrorCode.SCREEN_BLOCK_BIND_MISMATCH);
            }
        }
    }

    /**
     * 元数据 CAS 只核当前发布包的不可变绑定。缺少/损坏 bindSnapshots 的旧包必须先经受控
     * 迁移或业务核对后重新发布，绝不从当前可变 RPT_SCREEN_BLOCK 回填历史身份。
     */
    private List<BlockBinding> readPublishedBindings(RptScreen screen, String publishedJson) {
        if (publishedJson == null || publishedJson.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = PublishedScreenPackageValidator.read(publishedJson);
            Map<Long, JsonNode> snapshots = PublishedScreenPackageValidator.requireTrustedBindings(root);
            List<BlockBinding> bindings = new ArrayList<>();
            for (JsonNode snapshot : snapshots.values()) {
                JsonNode dsId = snapshot.path("bind").path("dsId");
                String componentType = snapshot.path("componentType").asText();
                if (!dsId.isIntegralNumber() || componentType.isBlank()) {
                    throw new RptException(RptErrorCode.SCREEN_PUBLISHED_SNAPSHOT_UNTRUSTED);
                }
                JsonNode drill = snapshot.path("drill");
                bindings.add(new BlockBinding(dsId.asLong(), componentType,
                        drill.isMissingNode() || drill.isNull() ? "{}" : objectMapper.writeValueAsString(drill)));
            }
            return bindings;
        } catch (RptException e) {
            throw e;
        } catch (Exception e) {
            throw new RptException(RptErrorCode.SCREEN_PUBLISHED_SNAPSHOT_UNTRUSTED, e);
        }
    }

    private void collectPublishedChartBlockCounts(JsonNode components, Map<Long, Integer> counts) {
        if (components == null || !components.isArray()) {
            return;
        }
        for (JsonNode component : components) {
            if ("ChartWidget".equals(component.path("component").asText())) {
                if (!component.path("blockId").isNumber()) {
                    throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID);
                }
                counts.merge(component.path("blockId").asLong(), 1, Integer::sum);
            }
            collectPublishedChartBlockCounts(component.path("children"), counts);
        }
    }

    private Long readDsId(String bindJson) {
        try {
            JsonNode n = objectMapper.readTree(bindJson == null ? "{}" : bindJson).path("dsId");
            return n.isNumber() ? n.asLong() : null;
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

    private RptScreen requireScreen(Long id) {
        RptScreen s = screenMapper.selectById(id);
        if (s == null) {
            throw new RptException(RptErrorCode.SCREEN_NOT_FOUND);
        }
        return s;
    }

    private void applyScreenFields(RptScreen s, ScreenMetadata metadata, boolean create) {
        s.setScreenName(metadata.screenName().trim());
        s.setViewLevel(metadata.viewLevel());
        if (metadata.bizLine() != null && !metadata.bizLine().isBlank()) {
            s.setBizLine(normalizeBizLine(metadata.bizLine()));
        } else {
            s.setBizLine(normalizeBizLine(s.getBizLine()));
        }
        if (metadata.orgScopeMode() != null && !metadata.orgScopeMode().isBlank()) {
            s.setOrgScopeMode(normalizeScopeMode(metadata.orgScopeMode()));
        } else {
            s.setOrgScopeMode(normalizeScopeMode(s.getOrgScopeMode()));
        }
        // 更新时 null 表示“不改历史绑定”；显式空串才表示清空（随后 NAMED_GROUP 校验会 fail-close）。
        if (metadata.orgGroupCode() != null) {
            s.setOrgGroupCode(metadata.orgGroupCode().isBlank() ? null : metadata.orgGroupCode().trim());
        }
        if (metadata.themeJson() != null || create) {
            s.setThemeJson(metadata.themeJson());
        }
        // 新建缺省 ACTIVE；更新缺省保留旧值并同时修正历史大小写，非法枚举已在 validateMetadata 拒绝。
        s.setStatus(resolveScreenStatus(metadata.status(), s.getStatus()));
    }

    /** 屏状态只允许 ACTIVE/DISABLED；大写规范化防止运行时精确 ACTIVE 查询被大小写绕过。 */
    private String resolveScreenStatus(String requestedStatus, String fallbackStatus) {
        String raw = requestedStatus == null || requestedStatus.isBlank() ? fallbackStatus : requestedStatus;
        String normalized = raw == null || raw.isBlank() ? "ACTIVE" : raw.trim().toUpperCase(Locale.ROOT);
        if (!SCREEN_STATUSES.contains(normalized)) {
            throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID);
        }
        return normalized;
    }

    /** 只复制元数据 allowlist，明确不携带草稿、发布包、发布状态及其他可变画布字段。 */
    private RptScreen metadataCandidate(RptScreen source) {
        RptScreen candidate = new RptScreen();
        candidate.setId(source.getId());
        candidate.setScreenCode(source.getScreenCode());
        candidate.setScreenName(source.getScreenName());
        candidate.setViewLevel(source.getViewLevel());
        candidate.setBizLine(source.getBizLine());
        candidate.setOrgScopeMode(source.getOrgScopeMode());
        candidate.setOrgGroupCode(source.getOrgGroupCode());
        candidate.setThemeJson(source.getThemeJson());
        candidate.setStatus(source.getStatus());
        // 仅供更新前的发布快照矩阵复核使用；updateMetadataCas XML 未引用该字段，绝不写回数据库。
        candidate.setCanvasPublishedJson(source.getCanvasPublishedJson());
        return candidate;
    }

    private ScreenMetadata metadataOf(ScreenCreateReqDTO req) {
        return new ScreenMetadata(req.getScreenCode(), req.getScreenName(), req.getViewLevel(), req.getBizLine(),
                req.getOrgScopeMode(), req.getOrgGroupCode(), req.getThemeJson(), req.getStatus());
    }

    private ScreenMetadata metadataOf(ScreenMetadataUpdateReqDTO req) {
        return new ScreenMetadata(req.getScreenCode(), req.getScreenName(), req.getViewLevel(), req.getBizLine(),
                req.getOrgScopeMode(), req.getOrgGroupCode(), req.getThemeJson(), req.getStatus());
    }

    private void copyMetadata(ScreenSaveReqDTO source, ScreenCreateReqDTO target) {
        target.setScreenCode(source.getScreenCode());
        target.setScreenName(source.getScreenName());
        target.setViewLevel(source.getViewLevel());
        target.setBizLine(source.getBizLine());
        target.setOrgScopeMode(source.getOrgScopeMode());
        target.setOrgGroupCode(source.getOrgGroupCode());
        target.setThemeJson(source.getThemeJson());
        target.setStatus(source.getStatus());
    }

    private void copyMetadata(ScreenSaveReqDTO source, ScreenMetadataUpdateReqDTO target) {
        target.setScreenCode(source.getScreenCode());
        target.setScreenName(source.getScreenName());
        target.setViewLevel(source.getViewLevel());
        target.setBizLine(source.getBizLine());
        target.setOrgScopeMode(source.getOrgScopeMode());
        target.setOrgGroupCode(source.getOrgGroupCode());
        target.setThemeJson(source.getThemeJson());
        target.setStatus(source.getStatus());
    }

    private record ScreenMetadata(String screenCode, String screenName, String viewLevel, String bizLine,
                                  String orgScopeMode, String orgGroupCode, String themeJson, String status) {
    }

    private record BlockBinding(Long datasourceId, String componentType, String drillJson) {
    }

    private List<ScreenBlockDTO> listBlocks(Long screenId) {
        return blockMapper.selectList(new LambdaQueryWrapper<RptScreenBlock>()
                        .eq(RptScreenBlock::getScreenId, screenId)
                        .orderByAsc(RptScreenBlock::getRegion)
                        .orderByAsc(RptScreenBlock::getRowNo)
                        .orderByAsc(RptScreenBlock::getColNo))
                .stream().map(this::toBlockDto).collect(Collectors.toList());
    }

    private ScreenBlockDTO toBlockDto(RptScreenBlock e) {
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
    }

    private ScreenDetailRespDTO toDetail(RptScreen s, List<ScreenBlockDTO> blocks) {
        ScreenDetailRespDTO d = new ScreenDetailRespDTO();
        d.setId(s.getId());
        d.setScreenCode(s.getScreenCode());
        d.setScreenName(s.getScreenName());
        d.setViewLevel(s.getViewLevel());
        d.setBizLine(normalizeBizLine(s.getBizLine()));
        d.setOrgScopeMode(normalizeScopeMode(s.getOrgScopeMode()));
        d.setOrgGroupCode(s.getOrgGroupCode());
        d.setAllowedRoleCodes(listAccessRoleCodes(s.getId()));
        d.setThemeJson(s.getThemeJson());
        d.setStatus(s.getStatus());
        d.setCreatedTime(s.getCreatedTime());
        d.setBlocks(blocks);
        return d;
    }

    private MapPointDTO toPointDto(RptScreenMapPoint e) {
        MapPointDTO d = new MapPointDTO();
        d.setId(e.getId());
        d.setOrgCode(e.getOrgCode());
        d.setOrgName(e.getOrgName());
        d.setLng(e.getLng());
        d.setLat(e.getLat());
        d.setTargetScreenCode(e.getTargetScreenCode());
        d.setStatus(e.getStatus());
        return d;
    }

    @Override
    public List<String> listAccessRoleCodes(Long screenId) {
        if (accessRoleMapper == null) {
            return List.of();
        }
        return accessRoleMapper.selectList(new LambdaQueryWrapper<RptScreenAccessRole>()
                        .eq(RptScreenAccessRole::getScreenId, screenId)
                        .eq(RptScreenAccessRole::getStatus, "ACTIVE"))
                .stream().map(RptScreenAccessRole::getRoleCode)
                .filter(v -> v != null && !v.isBlank()).distinct().toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveAccessRoleCodes(Long screenId, List<String> roleCodes, String reason, Integer expectedVersion) {
        RptScreen screen = requireScreen(screenId);
        if (roleCodes == null || accessRoleMapper == null || expectedVersion == null) {
            throw new RptException(RptErrorCode.SCREEN_ACCESS_ROLE_REQUIRED);
        }
        if (reason == null || reason.isBlank()) {
            throw new RptException(RptErrorCode.SCREEN_AUDIT_REASON_REQUIRED);
        }
        List<String> before = listAccessRoleCodes(screenId);
        List<String> normalized = roleCodes.stream().filter(v -> v != null && !v.isBlank())
                .map(value -> value.trim().toUpperCase(Locale.ROOT)).distinct().sorted().toList();
        validateScopeConfiguration(screen, normalized,
                "NAMED_GROUP".equals(normalizeScopeMode(screen.getOrgScopeMode())));
        if (canvasMapper == null || canvasMapper.bumpConfigVersion(screenId, expectedVersion,
                currentUserApi.getCurrentEmpId()) == 0) {
            throw new RptException(RptErrorCode.SCREEN_CANVAS_CONFLICT);
        }
        accessRoleMapper.delete(new LambdaQueryWrapper<RptScreenAccessRole>()
                .eq(RptScreenAccessRole::getScreenId, screenId));
        String operator = currentUserApi.getCurrentEmpId();
        for (String roleCode : normalized) {
            RptScreenAccessRole e = new RptScreenAccessRole();
            e.setScreenId(screenId);
            e.setRoleCode(roleCode);
            e.setStatus("ACTIVE");
            e.setCreatedBy(operator);
            e.setUpdatedBy(operator);
            accessRoleMapper.insert(e);
        }
        Set<String> beforeSet = new HashSet<>(before);
        Set<String> afterSet = new HashSet<>(normalized);
        Set<String> added = new HashSet<>(afterSet);
        added.removeAll(beforeSet);
        Set<String> removed = new HashSet<>(beforeSet);
        removed.removeAll(afterSet);
        persistRoleAudit(screen, expectedVersion, before, normalized, added, removed, reason);
    }

    private void validateScopeConfiguration(RptScreen screen, java.util.Collection<String> roleCodes,
                                            boolean requireRoles) {
        if (scopeAuthorizationService != null) {
            scopeAuthorizationService.validateForSave(screen, roleCodes, requireRoles);
        } else if ("NAMED_GROUP".equals(normalizeScopeMode(screen.getOrgScopeMode()))) {
            throw new RptException(RptErrorCode.SCREEN_SCOPE_INVALID);
        }
    }

    private Set<String> authorizeRuntime(RptScreen screen) {
        if (scopeAuthorizationService == null) {
            if ("NAMED_GROUP".equals(normalizeScopeMode(screen.getOrgScopeMode()))) {
                throw new RptException(RptErrorCode.SCREEN_ACCESS_DENIED);
            }
            return Set.of();
        }
        return scopeAuthorizationService.authorize(screen);
    }

    private boolean hasV2Map(RptScreen screen) {
        return screenMapService != null && screenMapService.hasV2Map(screen.getCanvasPublishedJson());
    }

    private boolean hasV2MapInComponents(JsonNode components) {
        if (components == null || !components.isArray()) {
            return false;
        }
        for (JsonNode node : components) {
            if ("MapCenter".equals(node.path("component").asText())
                    && node.path("propValue").path("schemaVersion").asInt(1) == 2) {
                return true;
            }
            if (hasV2MapInComponents(node.path("children"))) {
                return true;
            }
        }
        return false;
    }

    private String normalizeBizLine(String value) {
        return value == null || value.isBlank() ? "COMMON" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeScopeMode(String value) {
        return value == null || value.isBlank() ? "LEGACY_CONTEXT" : value.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 计算整屏响应使用的运行时取数契约版本。
     *
     * <p>命名机构组屏即使暂时没有 MapCenter，也必须走 schemaVersion=2 的
     * {@code screenCode + blockId} 解析链路；旧屏则以发布包根节点的版本保持兼容。
     */
    private int runtimeSchemaVersion(RptScreen screen, String renderPackageJson) {
        int packageVersion = 1;
        if (renderPackageJson != null && !renderPackageJson.isBlank()) {
            try {
                JsonNode root = objectMapper.readTree(renderPackageJson);
                JsonNode version = root.path("schemaVersion");
                if (!version.isMissingNode() && !version.isNull()
                        && (!version.canConvertToInt() || (version.asInt() != 1 && version.asInt() != 2))) {
                    throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID);
                }
                packageVersion = version.asInt(1);
                validateMapSchemaVersions(root.path("components"));
            } catch (RptException e) {
                throw e;
            } catch (Exception e) {
                throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID, e);
            }
        }
        if ("NAMED_GROUP".equals(normalizeScopeMode(screen.getOrgScopeMode()))) {
            return 2;
        }
        return packageVersion == 2 ? 2 : 1;
    }

    private void validateMapSchemaVersions(JsonNode components) {
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
            validateMapSchemaVersions(node.path("children"));
        }
    }

    private boolean isBizLineCompatible(String screenLine, String dsLine) {
        return switch (screenLine) {
            case "CORP" -> "CORP".equals(dsLine) || "COMMON".equals(dsLine);
            case "RETAIL" -> "RETAIL".equals(dsLine) || "COMMON".equals(dsLine);
            case "COMMON" -> "COMMON".equals(dsLine);
            default -> false;
        };
    }

    /** 命名机构组只允许服务端可外层约束的 org_code 宽表主体。 */
    private boolean isNamedGroupSafeDatasource(RptScreenDatasource ds) {
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

    /** 元数据 CAS 成功后在同一事务内写入治理审计；审计失败必须让 CAS 回滚。 */
    private void persistMetadataAudit(RptScreen before, RptScreen after, Integer expectedVersion, String reason) {
        if (auditApi == null) {
            throw new RptException(RptErrorCode.CROSS_MODULE_CALL_FAILED);
        }
        try {
            Map<String, Object> beforeSnapshot = metadataSnapshot(before);
            Map<String, Object> afterSnapshot = metadataSnapshot(after);
            Map<String, Object> changed = new java.util.LinkedHashMap<>();
            for (String field : beforeSnapshot.keySet()) {
                if (!java.util.Objects.equals(beforeSnapshot.get(field), afterSnapshot.get(field))) {
                    Map<String, Object> diff = new java.util.LinkedHashMap<>();
                    diff.put("before", beforeSnapshot.get(field));
                    diff.put("after", afterSnapshot.get(field));
                    changed.put(field, diff);
                }
            }
            Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("event", "SCREEN_METADATA_CHANGE");
            payload.put("screenId", after.getId());
            payload.put("expectedVersion", expectedVersion);
            payload.put("changed", changed);
            auditApi.log(AuditLogCmd.builder()
                    .traceId(traceId())
                    .empId(currentUserApi.getCurrentEmpId())
                    .bizType("REPORT")
                    .bizAction("SCREEN_METADATA_CHANGE")
                    .resourceUrl("/api/screen/admin/screens/" + after.getId() + "/metadata")
                    .requestMethod("PUT")
                    .requestParams(objectMapper.writeValueAsString(payload))
                    .responseStatus(200)
                    .reason(reason)
                    .targetType("RPT_SCREEN")
                    .targetId(String.valueOf(after.getId()))
                    .beforeSnapshot(objectMapper.writeValueAsString(beforeSnapshot))
                    .afterSnapshot(objectMapper.writeValueAsString(afterSnapshot))
                    .addedItems("[]")
                    .removedItems("[]")
                    .build());
        } catch (Exception ex) {
            throw new RptException(RptErrorCode.CROSS_MODULE_CALL_FAILED, ex);
        }
    }

    /**
     * 运行时拒绝的审计必须由持有实际 HTTP 语义的入口写入，不能在范围适配器中猜测 URL。
     * 审计不可用时仍保留原 403 语义，不能把拒绝误报为跨模块 500。
     */
    private void persistRuntimeDeniedAudit(RptScreen screen, String resourceUrl, RptException denied) {
        if (auditApi == null) {
            log.warn("[ScreenConfig] 运行拒绝审计不可用 screenId={} code={}", screen.getId(), denied.getCode());
            return;
        }
        try {
            Map<String, Object> snapshot = metadataSnapshot(screen);
            Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("event", "SCREEN_RUNTIME_DENIED");
            payload.put("screenId", screen.getId());
            payload.put("screenCode", screen.getScreenCode());
            payload.put("denyCode", denied.getCode());
            auditApi.log(AuditLogCmd.builder()
                    .traceId(traceId())
                    .empId(currentUserApi.getCurrentEmpId())
                    .bizType("REPORT")
                    .bizAction("SCREEN_RUNTIME_DENIED")
                    .resourceUrl(resourceUrl)
                    .requestMethod("GET")
                    .requestParams(objectMapper.writeValueAsString(payload))
                    .responseStatus(403)
                    .reason(denied.getCode())
                    .targetType("RPT_SCREEN")
                    .targetId(String.valueOf(screen.getId()))
                    .beforeSnapshot(objectMapper.writeValueAsString(snapshot))
                    .afterSnapshot(objectMapper.writeValueAsString(snapshot))
                    .addedItems("[]")
                    .removedItems("[]")
                    .build());
        } catch (Exception ex) {
            log.warn("[ScreenConfig] 运行拒绝审计写入失败 screenId={} cause={}",
                    screen.getId(), ex.getMessage());
        }
    }

    private Map<String, Object> metadataSnapshot(RptScreen screen) {
        Map<String, Object> snapshot = new java.util.LinkedHashMap<>();
        snapshot.put("screenCode", screen.getScreenCode());
        snapshot.put("screenName", screen.getScreenName());
        snapshot.put("viewLevel", screen.getViewLevel());
        snapshot.put("bizLine", normalizeBizLine(screen.getBizLine()));
        snapshot.put("orgScopeMode", normalizeScopeMode(screen.getOrgScopeMode()));
        snapshot.put("orgGroupCode", screen.getOrgGroupCode());
        snapshot.put("themeJson", screen.getThemeJson());
        snapshot.put("status", screen.getStatus());
        return snapshot;
    }

    private String traceId() {
        String traceId = MdcUtils.getTraceId();
        return traceId == null || traceId.isBlank()
                ? UUID.randomUUID().toString().replace("-", "") : traceId;
    }

    /** 角色变更必须落到治理审计表；没有 AuditApi 时拒绝写入，不能把 logger 当审计。 */
    private void persistRoleAudit(RptScreen screen, Integer expectedVersion, List<String> before,
                                  List<String> after, Set<String> added, Set<String> removed, String reason) {
        if (auditApi == null) {
            throw new RptException(RptErrorCode.CROSS_MODULE_CALL_FAILED);
        }
        try {
            Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("event", "SCREEN_ACCESS_ROLE_CHANGE");
            payload.put("screenId", screen.getId());
            payload.put("screenCode", screen.getScreenCode());
            payload.put("expectedVersion", expectedVersion);
            payload.put("beforeRoleCodes", before);
            payload.put("afterRoleCodes", after);
            payload.put("addedRoleCodes", added.stream().sorted().toList());
            payload.put("removedRoleCodes", removed.stream().sorted().toList());
            auditApi.log(AuditLogCmd.builder()
                    .traceId(traceId())
                    .empId(currentUserApi.getCurrentEmpId())
                    .bizType("REPORT")
                    .bizAction("PERMISSION_CHANGE")
                    .resourceUrl("/api/screen/admin/screens/" + screen.getId() + "/access-roles")
                    .requestMethod("PUT")
                    .requestParams(objectMapper.writeValueAsString(payload))
                    .responseStatus(200)
                    .reason(reason)
                    .targetType("RPT_SCREEN_ACCESS_ROLE")
                    .targetId(String.valueOf(screen.getId()))
                    .beforeSnapshot(objectMapper.writeValueAsString(before.stream().sorted().toList()))
                    .afterSnapshot(objectMapper.writeValueAsString(after.stream().sorted().toList()))
                    .addedItems(objectMapper.writeValueAsString(added.stream().sorted().toList()))
                    .removedItems(objectMapper.writeValueAsString(removed.stream().sorted().toList()))
                    .build());
        } catch (Exception ex) {
            throw new RptException(RptErrorCode.CROSS_MODULE_CALL_FAILED, ex);
        }
    }
}
