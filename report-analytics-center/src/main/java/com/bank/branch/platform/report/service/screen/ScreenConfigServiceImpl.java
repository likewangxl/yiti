package com.bank.branch.platform.report.service.screen;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.auth.api.ResourceApi;
import com.bank.branch.platform.auth.api.dto.OrgProfileDTO;
import com.bank.branch.platform.common.trace.MdcUtils;
import com.bank.branch.platform.governance.api.AuditApi;
import com.bank.branch.platform.governance.api.dto.AuditLogCmd;
import com.bank.branch.platform.report.dto.req.MapPointDTO;
import com.bank.branch.platform.report.dto.req.ScreenBlockDTO;
import com.bank.branch.platform.report.dto.req.ScreenCreateReqDTO;
import com.bank.branch.platform.report.dto.req.ScreenMetadataUpdateReqDTO;
import com.bank.branch.platform.report.dto.req.ScreenSaveReqDTO;
import com.bank.branch.platform.report.dto.req.presentation.InstitutionRulesDTO;
import com.bank.branch.platform.report.dto.resp.ScreenDetailRespDTO;
import com.bank.branch.platform.report.dto.resp.ScreenEntryRespDTO;
import com.bank.branch.platform.report.dto.resp.ScreenViewRespDTO;
import com.bank.branch.platform.report.dto.resp.MapRegionMetricDTO;
import com.bank.branch.platform.report.dto.resp.PanoramaInstitutionDTO;
import com.bank.branch.platform.performance.api.MetricApi;
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
import com.bank.branch.platform.report.service.screen.presentation.PublishedDatasourceDefinition;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bank.branch.platform.common.web.exception.BizException;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.time.LocalDate;
import java.math.BigDecimal;
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
    private static final String RUNTIME_CATALOG_SCREEN_CODE = "catalog";
    private static final List<RuntimeCatalogRegistration> CODE_SCREEN_CATALOG = List.of(
            new RuntimeCatalogRegistration("SCR_PROVINCE", "分行经营总览", "branch-overview-v1", "COMMON", "TEST"),
            new RuntimeCatalogRegistration("SCR_RETAIL_OVERVIEW", "零售经营总览", "retail-overview-v1", "RETAIL", "LIVE"),
            new RuntimeCatalogRegistration("SCR_CORP_OVERVIEW", "对公经营总览", "corporate-overview-v1", "CORP", "LIVE"));
    /** 逐屏目录校验中可安全排除的授权失败；带 cause 的同码异常表示基础设施失败，必须继续抛出。 */
    private static final Set<String> CATALOG_AUTH_REJECTION_CODES = Set.of(
            RptErrorCode.SCREEN_ACCESS_DENIED.getCode(),
            RptErrorCode.SCREEN_SCOPE_INVALID.getCode());
    private static final Set<String> REGIONS = Set.of("LEFT", "MAIN", "RIGHT");
    /** 地图首屏只查询领导驾驶舱需要的结果、驱动和趋势指标。 */
    private static final List<String> MAP_METRIC_CODES = List.of(
            "KPI_ACHIEVE_RATE_ORG", "DEP_ACHIEVE_RATE_ORG", "LOAN_ACHIEVE_RATE_ORG",
            "NEW_CUST_ACHIEVE_ORG", "M_0265", "M_0348", "NEW_VALID_CUST_ORG_MONTH",
            "DEP_BAL_YOY_RATE", "LOAN_BAL_YOY_RATE", "DEP_BAL_MOM_RATE", "LOAN_BAL_MOM_RATE");
    /** 区块 component_type 白名单；必须与 ScreenCanvasServiceImpl 的画布 innerType 白名单同步。 */
    private static final Set<String> COMPONENT_TYPES = Set.of(
            "METRIC_CARD", "LINE_TREND", "PIE_SHARE", "RANK_LIST", "FLOW_STATUS",
            "BAR_COMPARE", "AREA_STACK", "GAUGE", "TABLE_LIST",
            "KPI_DETAIL_TABLE", "KPI_RADAR", "LIQUID_PROGRESS", "PROGRESS_LIST",
            "COMBO_CHART", "FUNNEL_CHART", "SCATTER_BUBBLE", "HEATMAP_MATRIX",
            "SUNBURST_CHART", "SPARKLINE_CARD");
    /** 需要时序型数据源的组件（needTimeseries 联动，违规 43005） */
    private static final Set<String> TIMESERIES_ONLY_COMPONENTS =
            Set.of("LINE_TREND", "AREA_STACK", "SPARKLINE_CARD");
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

    /** 指标查询只经 performance 的跨模块 API；缺少适配器时返回空指标，不生成兜底值。 */
    @Autowired(required = false)
    private MetricApi metricApi;

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

    /** 查询代码化大屏固定目录，并逐屏复用在线授权门禁。 */
    @Override
    public List<ScreenEntryRespDTO> listAuthorizedCodeScreens() {
        LambdaQueryWrapper<RptScreen> query = new LambdaQueryWrapper<RptScreen>()
                .eq(RptScreen::getStatus, "ACTIVE")
                .in(RptScreen::getScreenCode, CODE_SCREEN_CATALOG.stream()
                        .map(RuntimeCatalogRegistration::screenCode).collect(Collectors.toList()))
                .orderByAsc(RptScreen::getId);
        List<RptScreen> candidates = screenMapper.selectList(query);
        Map<String, List<RptScreen>> byCode = candidates.stream()
                .filter(screen -> screen != null && "ACTIVE".equals(screen.getStatus()))
                .collect(Collectors.groupingBy(screen -> normalizeScreenCode(screen.getScreenCode())));
        List<ScreenEntryRespDTO> result = new ArrayList<>();
        for (RuntimeCatalogRegistration registration : CODE_SCREEN_CATALOG) {
            List<RptScreen> screens = byCode.getOrDefault(registration.screenCode(), List.of());
            // 重复 ACTIVE 编码与详情运行时一样按配置错误 fail-close，不能挑一行泄漏不确定元数据。
            if (screens.size() != 1) {
                continue;
            }
            RptScreen screen = screens.get(0);
            if (!isValidCodeScreenScope(screen, registration)
                    || !isAuthorizedCatalogScreen(screen)) {
                continue;
            }
            String dataMode = catalogDataMode(screen, registration);
            if (dataMode == null) {
                continue;
            }
            ScreenEntryRespDTO entry = toScreenEntry(screen, registration);
            entry.setDataMode(dataMode);
            result.add(entry);
        }
        return result;
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
    public List<MapRegionMetricDTO> listMapRegionMetrics(Long screenId) {
        RptScreen screen = requireScreen(screenId);
        authorizeRuntime(screen);
        if (!"PROVINCE".equals(screen.getViewLevel()) || hasV2Map(screen)) {
            return List.of();
        }
        List<MapPointDTO> points = pointMapper.selectList(new LambdaQueryWrapper<RptScreenMapPoint>()
                        .eq(RptScreenMapPoint::getStatus, "ACTIVE"))
                .stream().map(this::toPointDto).toList();
        return buildMapRegionMetrics(points);
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
        boolean draft = "draft".equalsIgnoreCase(state);
        if (draft) {
            if (resourceApi == null
                    || !resourceApi.hasResourcePermission(currentUserApi.getCurrentEmpId(), "R_RPT_SCR_CV_GET")) {
                // 普通整屏读取权限不能读取未发布草稿，避免设计态内容成为绕过管理端门禁的旁路。
                RptException denied = new RptException(RptErrorCode.SCREEN_ACCESS_DENIED);
                persistRuntimeDeniedAudit(s, "/api/screen/view/" + screenCode, denied);
                throw denied;
            }
            // 草稿态只在上面的画布管理读取权限通过后合成；绑定快照来自当前屏的 block 行，
            // 不能借用发布包或其它屏的可变行，避免预览成为跨屏/越权取数旁路。
            d.setRenderPackageJson(composeDraftPreview(s));
            d.setState("draft");
        } else {
            d.setRenderPackageJson(s.getCanvasPublishedJson());
            d.setState("published");
        }
        boolean codePresentation = validateRenderPackage(s, d.getRenderPackageJson(), !draft);
        InstitutionRulesDTO institutionRules = codePresentation
                ? institutionRulesFromPackage(d.getRenderPackageJson()) : null;
        boolean strictInstitutionRules = codePresentation
                && hasDisplaySchemaVersion(d.getRenderPackageJson());
        d.setInstitutionRules(institutionRules);
        // CODE 画布的组件身份来自 bindSnapshots，不使用旧坐标点位及其逐机构指标 N+1 路径。
        // 历史画布继续保留原有 PROVINCE 实时点位语义。
        if (!codePresentation && "PROVINCE".equals(s.getViewLevel()) && !hasV2Map(s)) {
            d.setMapPoints(pointMapper.selectList(new LambdaQueryWrapper<RptScreenMapPoint>()
                            .eq(RptScreenMapPoint::getStatus, "ACTIVE"))
                    .stream().map(this::toPointDto).collect(Collectors.toList()));
            d.setMapRegionMetrics(buildMapRegionMetrics(d.getMapPoints()));
        } else {
            d.setMapPoints(List.of());
            d.setMapRegionMetrics(List.of());
        }
        if (codePresentation && "NAMED_GROUP".equals(normalizeScopeMode(s.getOrgScopeMode()))) {
            d.setPanoramaInstitutions(buildPanoramaInstitutions(
                    s, authorizedOrgCodes, institutionRules, strictInstitutionRules));
        } else {
            d.setPanoramaInstitutions(List.of());
        }
        d.setRuntimeSchemaVersion(runtimeSchemaVersion(s, d.getRenderPackageJson()));
        if (!codePresentation && screenMapService != null && screenMapService.hasV2Map(d.getRenderPackageJson())
                && scopeAuthorizationService != null) {
            var profiles = scopeAuthorizationService.activeProfiles(s, authorizedOrgCodes);
            d.setMapPackage(screenMapService.render(
                    screenMapService.findV2MapConfig(d.getRenderPackageJson()), authorizedOrgCodes, profiles));
        }
        return d;
    }

    /**
     * CODE 包在运行时再次按不可变快照校验；旧包保持历史解析路径。
     */
    private boolean validateRenderPackage(RptScreen screen, String packageJson, boolean publishedPackage) {
        try {
            JsonNode root = objectMapper.readTree(packageJson == null || packageJson.isBlank()
                    ? "{}" : packageJson);
            String template = CodeScreenPresentationValidator.presentationTemplate(root.path("canvasStyle"));
            boolean code = template != null;
            if (code) {
                validateCodeTemplateScreenLine(screen, template);
                // CODE runtime must pass the existing immutable identity validator as well as
                // the slot/units validator. The latter alone would allow a snapshot map whose
                // keys do not match ChartWidget blockIds.
                JsonNode strictRoot;
                try {
                    strictRoot = PublishedScreenPackageValidator.read(packageJson);
                    PublishedScreenPackageValidator.requireTrustedBindings(strictRoot);
                } catch (Exception ex) {
                    throw new RptException(RptErrorCode.SCREEN_PUBLISHED_SNAPSHOT_UNTRUSTED, ex);
                }
                CodeScreenPresentationValidator.validatePublishedPackage(strictRoot);
                validateCodeDatasourceBindings(screen, strictRoot, template, publishedPackage);
            }
            return code;
        } catch (RptException e) {
            throw e;
        } catch (Exception e) {
            throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID, e);
        }
    }

    private InstitutionRulesDTO institutionRulesFromPackage(String packageJson) {
        try {
            JsonNode root = objectMapper.readTree(packageJson == null || packageJson.isBlank()
                    ? "{}" : packageJson);
            return CodeScreenPresentationValidator.institutionRules(root.path("canvasStyle"));
        } catch (RptException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RptException(RptErrorCode.SCREEN_PUBLISHED_SNAPSHOT_UNTRUSTED, ex);
        }
    }

    private boolean hasDisplaySchemaVersion(String packageJson) {
        try {
            JsonNode root = objectMapper.readTree(packageJson == null || packageJson.isBlank()
                    ? "{}" : packageJson);
            JsonNode presentation = root.path("canvasStyle").path("presentation");
            return presentation.isObject() && presentation.hasNonNull("displaySchemaVersion");
        } catch (Exception ex) {
            throw new RptException(RptErrorCode.SCREEN_PUBLISHED_SNAPSHOT_UNTRUSTED, ex);
        }
    }

    /** CODE 运行/草稿预览按 bindSnapshot 的真实 dsId 校验字段存在性和 DIM/METRIC 角色。 */
    private void validateCodeDatasourceBindings(RptScreen screen, JsonNode root, String template) {
        validateCodeDatasourceBindings(screen, root, template, true);
    }

    /**
     * 校验 CODE 绑定的当前数据源字段；草稿预览使用当前数据源，发布包仍强制不可变来源快照。
     */
    private void validateCodeDatasourceBindings(RptScreen screen, JsonNode root, String template,
                                                boolean publishedPackage) {
        JsonNode components = root.path("components");
        JsonNode snapshots = root.path("bindSnapshots");
        if (!components.isArray() || !snapshots.isObject()) {
            throw new RptException(RptErrorCode.SCREEN_PUBLISHED_SNAPSHOT_UNTRUSTED);
        }
        for (JsonNode component : components) {
            if (!"ChartWidget".equals(component.path("component").asText())) {
                throw new RptException(RptErrorCode.SCREEN_PUBLISHED_SNAPSHOT_UNTRUSTED);
            }
            JsonNode blockId = component.path("blockId");
            String bindingKey = component.path("propValue").path("bindingKey").asText();
            JsonNode snapshot = snapshots.path(blockId.asText());
            JsonNode bind = snapshot.path("bind");
            if (!bind.isObject() || !bind.path("dsId").isIntegralNumber()) {
                throw new RptException(RptErrorCode.SCREEN_PUBLISHED_SNAPSHOT_UNTRUSTED);
            }
            RptScreenDatasource datasource = dsMapper.selectById(bind.path("dsId").longValue());
            if (datasource == null) {
                throw new RptException(RptErrorCode.SCREEN_DS_NOT_FOUND);
            }
            RptScreenDatasource effective = PublishedDatasourceDefinition.effective(
                    snapshot, datasource, objectMapper,
                    publishedPackage && PublishedDatasourceDefinition.requiredFor(root));
            try {
                CodeScreenPresentationValidator.validateBindAgainstDatasource(
                        bind, bindingKey, effective, template, root.path("canvasStyle"));
                validateCodeTemplateDatasourceLine(template, datasource);
                validateCodeTemplateDatasourceLine(template, effective);
                if ("NAMED_GROUP".equalsIgnoreCase(screen.getOrgScopeMode())
                        && ("branches".equals(bindingKey) || "citySummary".equals(bindingKey)
                        || "retailRanking".equals(bindingKey) || "corpRanking".equals(bindingKey))) {
                    try {
                        // The immutable package must satisfy the same server-side identity rule as
                        // save/publish/metadata transitions. A stale package must never reach the
                        // frontend with an org_name pretending to be an orgCode.
                        validateCodeNamedGroupBinding(bindingKey, bind.toString(), effective);
                    } catch (RptException ex) {
                        if (RptErrorCode.SCREEN_DS_CONFIG_INVALID.getCode().equals(ex.getCode())) {
                            throw new RptException(RptErrorCode.SCREEN_PUBLISHED_SNAPSHOT_UNTRUSTED, ex);
                        }
                        throw ex;
                    }
                }
            } catch (RptException ex) {
                if (RptErrorCode.SCREEN_LAYOUT_INVALID.getCode().equals(ex.getCode())) {
                    throw new RptException(RptErrorCode.SCREEN_PUBLISHED_SNAPSHOT_UNTRUSTED, ex);
                }
                throw ex;
            }
        }
    }

    /**
     * 将当前授权机构集合投影为前端目录。遍历只使用 authorizeRuntime 返回的 code，
     * activeProfiles 的额外键不会进入响应；授权 code 缺画像或画像非 ACTIVE 时 fail-close。
     */
    private List<PanoramaInstitutionDTO> buildPanoramaInstitutions(RptScreen screen,
                                                                     Set<String> authorizedOrgCodes,
                                                                     InstitutionRulesDTO institutionRules,
                                                                     boolean strictInstitutionRules) {
        if (scopeAuthorizationService == null || authorizedOrgCodes == null
                || authorizedOrgCodes.isEmpty()) {
            throw new RptException(RptErrorCode.SCREEN_SCOPE_INVALID);
        }
        if (strictInstitutionRules && institutionRules == null) {
            throw new RptException(RptErrorCode.SCREEN_SCOPE_INVALID);
        }
        Map<String, OrgProfileDTO> profiles = scopeAuthorizationService.activeProfiles(
                screen, authorizedOrgCodes);
        if (profiles == null) {
            throw new RptException(RptErrorCode.SCREEN_SCOPE_INVALID);
        }
        List<String> eligibleCodes = new ArrayList<>();
        Map<String, OrgProfileDTO> eligibleProfiles = new LinkedHashMap<>();
        boolean needsLegacyPointFallback = false;
        for (String authorizedCode : authorizedOrgCodes) {
            if (authorizedCode == null || authorizedCode.isBlank()) {
                throw new RptException(RptErrorCode.SCREEN_SCOPE_INVALID);
            }
            OrgProfileDTO profile = profiles.get(authorizedCode);
            if (profile == null || !"ACTIVE".equalsIgnoreCase(profile.getStatus())
                    || profile.getOrgCode() == null
                    || !authorizedCode.equals(profile.getOrgCode().trim())) {
                throw new RptException(RptErrorCode.SCREEN_SCOPE_INVALID);
            }
            if ("DEPARTMENT".equalsIgnoreCase(profile.getOrgNature())) {
                continue;
            }
            if (institutionRules != null && !matchesInstitutionRules(profile, institutionRules)) {
                continue;
            }
            eligibleCodes.add(authorizedCode);
            eligibleProfiles.put(authorizedCode, profile);
            if (strictInstitutionRules && !profileLocationUsable(profile)) {
                needsLegacyPointFallback = true;
            }
        }
        Map<String, RptScreenMapPoint> legacyPoints = needsLegacyPointFallback
                ? loadAuthorizedLegacyMapPoints(authorizedOrgCodes) : Map.of();
        List<PanoramaInstitutionDTO> result = new ArrayList<>();
        for (String authorizedCode : eligibleCodes) {
            OrgProfileDTO profile = eligibleProfiles.get(authorizedCode);
            PanoramaInstitutionDTO dto = new PanoramaInstitutionDTO();
            dto.setOrgCode(authorizedCode);
            dto.setOrgName(profile.getOrgName());
            dto.setCityCode(profile.getCityCode());
            dto.setCityName(profile.getCityName());
            dto.setOwnerOperatingOrgCode(profile.getOwnerOperatingOrgCode());
            dto.setOperatingLevel(profile.getOperatingLevel());
            dto.setOrgNature(profile.getOrgNature());
            dto.setCoordSys(profile.getCoordSys());
            boolean located = profileLocationUsable(profile);
            dto.setLocated(located);
            if (!located) {
                RptScreenMapPoint legacyPoint = legacyPoints.get(authorizedCode);
                if (strictInstitutionRules && validLegacyMapPoint(legacyPoint)) {
                    dto.setLng(legacyPoint.getLng());
                    dto.setLat(legacyPoint.getLat());
                    dto.setCoordSys("GCJ02");
                    dto.setLocated(true);
                    dto.setLocationSource("LEGACY_MAP_POINT");
                } else {
                    dto.setLng(null);
                    dto.setLat(null);
                    dto.setLocationSource(null);
                }
            } else {
                dto.setLng(profile.getLng());
                dto.setLat(profile.getLat());
                dto.setLocationSource(profile.getLocationSource());
            }
            result.add(dto);
        }
        return result;
    }

    private boolean matchesInstitutionRules(OrgProfileDTO profile, InstitutionRulesDTO rules) {
        Set<String> levels = normalizeRuleSet(rules.getAllowedOperatingLevels());
        Set<String> natures = normalizeRuleSet(rules.getAllowedOrgNatures());
        if (levels.isEmpty() || natures.isEmpty()) {
            throw new RptException(RptErrorCode.SCREEN_SCOPE_INVALID);
        }
        return levels.contains(normalizeRuleValue(profile.getOperatingLevel()))
                && natures.contains(normalizeRuleValue(profile.getOrgNature()));
    }

    private Set<String> normalizeRuleSet(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(this::normalizeRuleValue)
                .collect(Collectors.toCollection(HashSet::new));
    }

    private String normalizeRuleValue(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private boolean profileLocationUsable(OrgProfileDTO profile) {
        boolean demo = profile.getRemark() != null
                && profile.getRemark().toUpperCase(Locale.ROOT).contains("SCREEN_MAP_DEMO");
        return validPanoramaCoordinate(profile)
                && (!demo || trustedRuntimeLocationSource(profile.getLocationSource()));
    }

    /** 只查询当前屏已授权机构的 ACTIVE 旧点位，不能把全表点位带入 CODE 目录。 */
    private Map<String, RptScreenMapPoint> loadAuthorizedLegacyMapPoints(Set<String> authorizedOrgCodes) {
        if (authorizedOrgCodes == null || authorizedOrgCodes.isEmpty()) {
            return Map.of();
        }
        List<RptScreenMapPoint> rows = pointMapper.selectList(new LambdaQueryWrapper<RptScreenMapPoint>()
                .in(RptScreenMapPoint::getOrgCode, authorizedOrgCodes)
                .eq(RptScreenMapPoint::getStatus, "ACTIVE")
                .orderByAsc(RptScreenMapPoint::getOrgCode)
                .orderByAsc(RptScreenMapPoint::getId));
        Map<String, RptScreenMapPoint> result = new LinkedHashMap<>();
        if (rows == null) {
            return result;
        }
        for (RptScreenMapPoint row : rows) {
            if (row == null || row.getOrgCode() == null || !authorizedOrgCodes.contains(row.getOrgCode().trim())
                    || !validLegacyMapPoint(row)) {
                continue;
            }
            result.putIfAbsent(row.getOrgCode().trim(), row);
        }
        return result;
    }

    private boolean validLegacyMapPoint(RptScreenMapPoint point) {
        return point != null && "ACTIVE".equalsIgnoreCase(point.getStatus())
                && point.getLng() != null && point.getLat() != null
                && point.getLng().compareTo(BigDecimal.valueOf(-180)) >= 0
                && point.getLng().compareTo(BigDecimal.valueOf(180)) <= 0
                && point.getLat().compareTo(BigDecimal.valueOf(-90)) >= 0
                && point.getLat().compareTo(BigDecimal.valueOf(90)) <= 0;
    }

    /** 已认证的位置台账来源可以覆盖旧 demo 标记；画像来源或未知来源仍保持 fail-close。 */
    private boolean trustedRuntimeLocationSource(String source) {
        if (source == null) {
            return false;
        }
        return "MANUAL".equalsIgnoreCase(source.trim())
                || "GEOCODE_VERIFIED".equalsIgnoreCase(source.trim());
    }

    private boolean validPanoramaCoordinate(OrgProfileDTO profile) {
        return profile.getLng() != null && profile.getLat() != null
                && profile.getLng().compareTo(BigDecimal.valueOf(-180)) >= 0
                && profile.getLng().compareTo(BigDecimal.valueOf(180)) <= 0
                && profile.getLat().compareTo(BigDecimal.valueOf(-90)) >= 0
                && profile.getLat().compareTo(BigDecimal.valueOf(90)) <= 0
                && "GCJ02".equalsIgnoreCase(profile.getCoordSys());
    }

    /**
     * 从真实机构指标宽表构建地图指标包。单机构失败仅留下空 metricValues，既不阻断整屏，
     * 也不能用其它机构或 mock 值替代；前端据此明确显示“--”。
     */
    private List<MapRegionMetricDTO> buildMapRegionMetrics(List<MapPointDTO> points) {
        if (metricApi == null || points == null || points.isEmpty()) {
            return List.of();
        }
        LocalDate dataDate;
        try {
            dataDate = metricApi.getLatestDataDate("ORG");
        } catch (RuntimeException e) {
            log.warn("[ScreenMapMetric] 读取最新机构数据日期失败 cause={}", e.getMessage());
            dataDate = null;
        }
        List<MapRegionMetricDTO> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (MapPointDTO point : points) {
            String orgCode = point == null ? null : point.getOrgCode();
            if (orgCode == null || orgCode.isBlank() || !seen.add(orgCode)) {
                continue;
            }
            Map<String, BigDecimal> values;
            try {
                Map<String, BigDecimal> queried = metricApi.getOrgMetricValues(orgCode, dataDate, MAP_METRIC_CODES);
                values = queried == null ? Map.of() : queried;
            } catch (RuntimeException e) {
                log.warn("[ScreenMapMetric] 机构指标读取失败 orgCode={} cause={}", orgCode, e.getMessage());
                values = Map.of();
            }
            result.add(MapRegionMetricDTO.builder()
                    .orgCode(orgCode)
                    .orgName(point.getOrgName())
                    .dataDate(dataDate)
                    .metricValues(values)
                    .build());
        }
        return result;
    }

    private String composeDraftPreview(RptScreen s) {
        try {
            com.fasterxml.jackson.databind.node.ObjectNode pkg = objectMapper.createObjectNode();
            pkg.set("canvasStyle", objectMapper.readTree(
                    s.getCanvasStyleJson() == null ? "{}" : s.getCanvasStyleJson()));
            com.fasterxml.jackson.databind.JsonNode draft = objectMapper.readTree(
                    s.getCanvasDraftJson() == null ? "{}" : s.getCanvasDraftJson());
            JsonNode components = draft.path("components").isMissingNode()
                    ? objectMapper.createArrayNode() : draft.path("components");
            pkg.set("components", components);
            Set<Long> chartBlockIds = new HashSet<>();
            collectDraftChartBlockIds(components, chartBlockIds);
            Map<Long, RptScreenBlock> blocksById = blockMapper.selectList(
                            new LambdaQueryWrapper<RptScreenBlock>().eq(RptScreenBlock::getScreenId, s.getId()))
                    .stream()
                    // 防御不可信 mapper/旁路实现返回其它屏行，预览身份必须绑定当前 screenId。
                    .filter(block -> s.getId().equals(block.getScreenId()))
                    .collect(Collectors.toMap(RptScreenBlock::getId, Function.identity(), (left, right) -> left));
            if (!blocksById.keySet().containsAll(chartBlockIds)) {
                throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID);
            }
            com.fasterxml.jackson.databind.node.ObjectNode snapshots = pkg.putObject("bindSnapshots");
            for (Long blockId : chartBlockIds) {
                RptScreenBlock block = blocksById.get(blockId);
                com.fasterxml.jackson.databind.node.ObjectNode snapshot = snapshots.putObject(String.valueOf(blockId));
                snapshot.set("bind", objectMapper.readTree(block.getBindJson() == null ? "{}" : block.getBindJson()));
                snapshot.put("componentType", block.getComponentType());
                snapshot.set("styleCfg", objectMapper.readTree(block.getStyleJson() == null ? "{}" : block.getStyleJson()));
                snapshot.set("drill", objectMapper.readTree(block.getDrillJson() == null ? "{}" : block.getDrillJson()));
            }
            pkg.put("schemaVersion", hasV2MapInComponents(pkg.path("components")) ? 2 : 1);
            String packageJson = pkg.toString();
            validateRenderPackage(s, packageJson, false);
            return packageJson;
        } catch (RptException e) {
            throw e;
        } catch (Exception e) {
            throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID, e);
        }
    }

    /** 草稿预览递归收集 ChartWidget 身份；Group.children 不能漏收，否则预览会出现假空图表。 */
    private void collectDraftChartBlockIds(JsonNode components, Set<Long> out) {
        if (components == null || !components.isArray()) {
            return;
        }
        for (JsonNode component : components) {
            if ("ChartWidget".equals(component.path("component").asText())) {
                JsonNode blockId = component.path("blockId");
                if (!blockId.isIntegralNumber() || !blockId.canConvertToLong()
                        || blockId.longValue() <= 0 || !out.add(blockId.longValue())) {
                    throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID);
                }
            }
            collectDraftChartBlockIds(component.path("children"), out);
        }
    }

    // ===== 内部 =====

    /** 创建/更新共同的元数据校验；区块不属于此入口。 */
    private void validateMetadata(ScreenMetadata metadata, RptScreen existing) {
        if (metadata.screenName() == null || metadata.screenName().isBlank()
                || !VIEW_LEVELS.contains(metadata.viewLevel())) {
            throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID);
        }
        if (metadata.screenCode() != null
                && RUNTIME_CATALOG_SCREEN_CODE.equals(metadata.screenCode().trim())) {
            // /api/screen/view/catalog 是保留的字面目录路由，不能被屏编码占用。
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
        String draftTemplate = codeTemplate(screen.getCanvasStyleJson());
        String publishedTemplate = publishedCodeTemplate(screen.getCanvasPublishedJson());
        validateCodeTemplateScreenLine(screen, draftTemplate);
        validateCodeTemplateScreenLine(screen, publishedTemplate);
        List<BlockBinding> bindings = new ArrayList<>();
        List<ScreenBlockDTO> draftBlocks = listBlocks(screen.getId());
        for (ScreenBlockDTO block : draftBlocks) {
            Long datasourceId = readDsId(block.getBindJson());
            if (datasourceId == null) {
                throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID);
            }
            bindings.add(new BlockBinding(datasourceId, block.getComponentType(), block.getDrillJson(), draftTemplate));
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
            validateCodeTemplateDatasourceLine(binding.template(), datasource);
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

        // A legacy screen can already contain a CODE canvas. When its scope changes to
        // NAMED_GROUP, componentType alone is insufficient: the branches slot must still be
        // backed by the server-side SUBJECT/org_code binding contract. Parse both mutable draft
        // components and immutable published components before the metadata CAS, so a scope
        // change cannot create a path that save/publish would have rejected.
        if ("NAMED_GROUP".equals(normalizeScopeMode(screen.getOrgScopeMode()))
                && (draftTemplate != null || publishedTemplate != null)) {
            validateCodeNamedGroupMetadataBindings(screen, draftBlocks, datasourceById,
                    draftTemplate, publishedTemplate);
        }
    }

    private void validateCodeNamedGroupMetadataBindings(RptScreen screen,
                                                        List<ScreenBlockDTO> draftBlocks,
                                                        Map<Long, RptScreenDatasource> datasourceById,
                                                        String draftTemplate,
                                                        String publishedTemplate) {
        Map<Long, ScreenBlockDTO> blocksById = draftBlocks.stream()
                .filter(block -> block.getId() != null)
                .collect(Collectors.toMap(ScreenBlockDTO::getId, Function.identity(), (left, right) -> left));
        if (draftTemplate != null) {
            Map<Long, String> draftBindingKeys = new HashMap<>();
            collectCodeBindingKeys(readJsonNode(screen.getCanvasDraftJson()).path("components"), draftBindingKeys);
            for (Map.Entry<Long, String> entry : draftBindingKeys.entrySet()) {
                if (!isNamedGroupCodeBinding(entry.getValue())) {
                    continue;
                }
                ScreenBlockDTO block = blocksById.get(entry.getKey());
                if (block == null) {
                    throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID);
                }
                Long dsId = readDsId(block.getBindJson());
                RptScreenDatasource datasource = datasourceById.get(dsId);
                if (datasource == null) {
                    throw new RptException(RptErrorCode.SCREEN_DS_NOT_FOUND);
                }
                validateCodeNamedGroupBinding(entry.getValue(), block.getBindJson(), datasource);
            }
        }

        if (publishedTemplate == null || screen.getCanvasPublishedJson() == null
                || screen.getCanvasPublishedJson().isBlank()) {
            return;
        }
        try {
            JsonNode published = PublishedScreenPackageValidator.read(screen.getCanvasPublishedJson());
            Map<Long, JsonNode> snapshots = PublishedScreenPackageValidator.requireTrustedBindings(published);
            Map<Long, String> publishedBindingKeys = new HashMap<>();
            collectCodeBindingKeys(published.path("components"), publishedBindingKeys);
            for (Map.Entry<Long, String> entry : publishedBindingKeys.entrySet()) {
                if (!isNamedGroupCodeBinding(entry.getValue())) {
                    continue;
                }
                JsonNode snapshot = snapshots.get(entry.getKey());
                if (snapshot == null || !snapshot.path("bind").isObject()) {
                    throw new RptException(RptErrorCode.SCREEN_PUBLISHED_SNAPSHOT_UNTRUSTED);
                }
                Long dsId = readDsId(snapshot.path("bind").toString());
                RptScreenDatasource datasource = datasourceById.get(dsId);
                if (datasource == null) {
                    throw new RptException(RptErrorCode.SCREEN_DS_NOT_FOUND);
                }
                validateCodeNamedGroupBinding(entry.getValue(), snapshot.path("bind").toString(), datasource);
            }
        } catch (RptException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RptException(RptErrorCode.SCREEN_PUBLISHED_SNAPSHOT_UNTRUSTED, ex);
        }
    }

    private void collectCodeBindingKeys(JsonNode components, Map<Long, String> keysByBlockId) {
        if (components == null || components.isMissingNode() || components.isNull()) {
            return;
        }
        if (!components.isArray()) {
            throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID);
        }
        for (JsonNode component : components) {
            if ("ChartWidget".equals(component.path("component").asText())) {
                JsonNode blockId = component.path("blockId");
                if (!blockId.isIntegralNumber() || !blockId.canConvertToLong() || blockId.longValue() <= 0) {
                    throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID);
                }
                String bindingKey = component.path("propValue").path("bindingKey").asText();
                if (!bindingKey.isBlank() && keysByBlockId.putIfAbsent(blockId.longValue(), bindingKey) != null) {
                    throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID);
                }
            }
            collectCodeBindingKeys(component.path("children"), keysByBlockId);
        }
    }

    private JsonNode readJsonNode(String json) {
        try {
            return objectMapper.readTree(json == null || json.isBlank() ? "{}" : json);
        } catch (Exception ex) {
            throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID, ex);
        }
    }

    private void validateCodeNamedGroupBinding(String bindingKey, String bindJson,
                                               RptScreenDatasource datasource) {
        if (!isNamedGroupCodeBinding(bindingKey)) {
            return;
        }
        if (M98StatPolicy.SOURCE_KIND.equals(datasource.getSourceKind())
                && !M98StatPolicy.allowsCodeBinding(bindingKey)) {
            throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
        }
        try {
            JsonNode config = objectMapper.readTree(datasource.getConfigJson() == null
                    ? "{}" : datasource.getConfigJson());
            JsonNode bind = objectMapper.readTree(bindJson == null ? "{}" : bindJson);
            boolean namedSubject = "WIDE_TABLE".equals(datasource.getSourceKind())
                    && "ORG_INDEX_RESULT".equals(config.path("table").asText())
                    && "org_code".equals(config.path("subjectCol").asText())
                    && config.path("aggregation").isObject()
                    && "SUBJECT".equals(config.path("aggregation").path("groupBy").asText());
            boolean requiresNamedSubject = "branches".equals(bindingKey)
                    || "retailRanking".equals(bindingKey)
                    || "corpRanking".equals(bindingKey)
                    || ("citySummary".equals(bindingKey) && bind.path("fields").has("orgCode"));
            if (requiresNamedSubject && (!namedSubject
                    || !"org_code".equals(bind.path("fields").path("orgCode").asText()))) {
                throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID);
            }
        } catch (RptException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RptException(RptErrorCode.SCREEN_DS_CONFIG_INVALID, ex);
        }
    }

    private boolean isNamedGroupCodeBinding(String bindingKey) {
        return "branches".equals(bindingKey) || "ranking".equals(bindingKey)
                || "citySummary".equals(bindingKey)
                || "retailRanking".equals(bindingKey) || "corpRanking".equals(bindingKey);
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
            String template = CodeScreenPresentationValidator.presentationTemplate(root.path("canvasStyle"));
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
                        drill.isMissingNode() || drill.isNull() ? "{}" : objectMapper.writeValueAsString(drill),
                        template));
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
        candidate.setCanvasStyleJson(source.getCanvasStyleJson());
        candidate.setCanvasDraftJson(source.getCanvasDraftJson());
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

    private record BlockBinding(Long datasourceId, String componentType, String drillJson, String template) {
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

    /** 代码化目录只接受固定注册屏的有效状态、范围配置和授权结果；发布包由目录模式校验。 */
    private boolean isValidCodeScreenScope(RptScreen screen, RuntimeCatalogRegistration registration) {
        return screen != null
                && "ACTIVE".equals(screen.getStatus())
                && screen.getScreenCode() != null
                && !screen.getScreenCode().isBlank()
                && !RUNTIME_CATALOG_SCREEN_CODE.equals(screen.getScreenCode().trim())
                && screen.getViewLevel() != null
                && VIEW_LEVELS.contains(screen.getViewLevel().trim().toUpperCase(Locale.ROOT))
                && BIZ_LINES.contains(normalizeBizLine(screen.getBizLine()))
                && registration.bizLine().equals(normalizeBizLine(screen.getBizLine()))
                && SCOPE_MODES.contains(normalizeScopeMode(screen.getOrgScopeMode()));
    }

    /** 每个候选屏都必须经过与详情/渲染相同的屏级白名单、机构组和数据范围授权。 */
    private boolean isAuthorizedCatalogScreen(RptScreen screen) {
        try {
            Set<String> authorizedOrgCodes = authorizeRuntime(screen);
            // NAMED_GROUP 的授权结果必须有实际机构范围；LEGACY_CONTEXT 保持现有空集合兼容语义。
            return !"NAMED_GROUP".equals(normalizeScopeMode(screen.getOrgScopeMode()))
                    || (authorizedOrgCodes != null && !authorizedOrgCodes.isEmpty());
        } catch (BizException ex) {
            if (isCatalogAuthorizationRejection(ex)) {
                return false;
            }
            throw ex;
        }
    }

    /** 仅吞掉无底层 cause 的逐屏授权拒绝；带 cause 的同码异常是基础设施故障。 */
    private boolean isCatalogAuthorizationRejection(BizException ex) {
        return CATALOG_AUTH_REJECTION_CODES.contains(ex.getCode()) && ex.getCause() == null;
    }

    private ScreenEntryRespDTO toScreenEntry(RptScreen screen, RuntimeCatalogRegistration registration) {
        ScreenEntryRespDTO entry = new ScreenEntryRespDTO();
        entry.setScreenCode(screen.getScreenCode());
        entry.setScreenName(registration.screenName());
        entry.setViewLevel(screen.getViewLevel());
        entry.setBizLine(registration.bizLine());
        entry.setTemplate(registration.template());
        entry.setDataMode(registration.dataMode());
        return entry;
    }

    private String normalizeScreenCode(String value) {
        return value == null ? "" : value.trim();
    }

    private record RuntimeCatalogRegistration(String screenCode, String screenName,
                                              String template, String bizLine, String dataMode) {
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

    /** 从屏样式读取 CODE 模板；历史坐标画布没有 presentation，返回 null。 */
    private String codeTemplate(String canvasStyleJson) {
        try {
            JsonNode style = objectMapper.readTree(canvasStyleJson == null || canvasStyleJson.isBlank()
                    ? "{}" : canvasStyleJson);
            return CodeScreenPresentationValidator.presentationTemplate(style);
        } catch (RptException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID, ex);
        }
    }

    /** 发布/归档引用必须使用不可变包内的模板，不能以当前可变 canvasStyle 代替。 */
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

    /**
     * 目录只开放当前 ACTIVE 屏的可信 schema2 CODE 发布包；历史坐标、空包、错模板、坏绑定均
     * 按未接入处理，不能再回退到任何前端演示入口。缺省分类沿用注册项的接口数据模式，显式
     * TEST 分类才标为测试库数据；dataNotice 只用于展示说明，不能参与分类判断。
     */
    private String catalogDataMode(RptScreen screen, RuntimeCatalogRegistration registration) {
        try {
            if (screen == null
                    || !(Integer.valueOf(1).equals(screen.getPublishStatus())
                    || Integer.valueOf(2).equals(screen.getPublishStatus()))
                    || screen.getCanvasPublishedJson() == null
                    || screen.getCanvasPublishedJson().isBlank()) {
                return null;
            }
            JsonNode root = PublishedScreenPackageValidator.read(screen.getCanvasPublishedJson());
            JsonNode schemaVersion = root.path("schemaVersion");
            if (!schemaVersion.isIntegralNumber() || !schemaVersion.canConvertToInt()
                    || schemaVersion.intValue() != 2) {
                return null;
            }
            String publishedTemplate = CodeScreenPresentationValidator.presentationTemplate(
                    root.path("canvasStyle"));
            if (!registration.template().equals(publishedTemplate)) {
                return null;
            }
            PublishedScreenPackageValidator.requireTrustedBindings(root);
            CodeScreenPresentationValidator.validatePublishedPackage(root);
            validateCodeDatasourceBindings(screen, root, publishedTemplate);
            String classification = CodeScreenPresentationValidator.dataClassification(
                    root.path("canvasStyle"));
            return "TEST".equals(classification) ? "TEST" : registration.dataMode();
        } catch (Exception ex) {
            // 目录是入口授权边界；发布包解析、schema、模板、绑定或来源校验失败时按未接入处理。
            return null;
        }
    }

    /** 业务专属 CODE 模板必须挂在对应条线屏上，不能以其他条线伪装经营口径。 */
    private void validateCodeTemplateScreenLine(RptScreen screen, String template) {
        String requiredLine = requiredTemplateBizLine(template);
        if (requiredLine != null && !requiredLine.equals(normalizeBizLine(screen.getBizLine()))) {
            throw new RptException(RptErrorCode.SCREEN_BIZ_LINE_MISMATCH);
        }
    }

    /** 业务专属 CODE 模板的每个绑定都必须来自对应条线数据源，COMMON 源不能贴专属标签。 */
    private void validateCodeTemplateDatasourceLine(String template, RptScreenDatasource datasource) {
        String requiredLine = requiredTemplateBizLine(template);
        if (requiredLine != null
                && (datasource == null || !requiredLine.equals(normalizeBizLine(datasource.getBizLine())))) {
            throw new RptException(RptErrorCode.SCREEN_BIZ_LINE_MISMATCH);
        }
    }

    private String requiredTemplateBizLine(String template) {
        if (CodeScreenPresentationValidator.RETAIL_OVERVIEW_TEMPLATE.equals(template)) {
            return "RETAIL";
        }
        if (CodeScreenPresentationValidator.CORPORATE_OVERVIEW_TEMPLATE.equals(template)) {
            return "CORP";
        }
        return null;
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
        return ScreenNamedGroupDatasourcePolicy.isSafe(ds);
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
