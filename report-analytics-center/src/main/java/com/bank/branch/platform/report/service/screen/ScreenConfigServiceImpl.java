package com.bank.branch.platform.report.service.screen;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.report.dto.req.MapPointDTO;
import com.bank.branch.platform.report.dto.req.ScreenBlockDTO;
import com.bank.branch.platform.report.dto.req.ScreenSaveReqDTO;
import com.bank.branch.platform.report.dto.resp.ScreenDetailRespDTO;
import com.bank.branch.platform.report.dto.resp.ScreenViewRespDTO;
import com.bank.branch.platform.report.entity.RptScreen;
import com.bank.branch.platform.report.entity.RptScreenBlock;
import com.bank.branch.platform.report.entity.RptScreenDatasource;
import com.bank.branch.platform.report.entity.RptScreenMapPoint;
import com.bank.branch.platform.report.enums.RptErrorCode;
import com.bank.branch.platform.report.exception.RptException;
import com.bank.branch.platform.report.mapper.RptScreenBlockMapper;
import com.bank.branch.platform.report.mapper.RptScreenDatasourceMapper;
import com.bank.branch.platform.report.mapper.RptScreenMapPointMapper;
import com.bank.branch.platform.report.mapper.RptScreenMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
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
    private static final Set<String> REGIONS = Set.of("LEFT", "MAIN", "RIGHT");
    private static final Set<String> COMPONENT_TYPES =
            Set.of("METRIC_CARD", "LINE_TREND", "PIE_SHARE", "RANK_LIST", "FLOW_STATUS");
    /** 需要时序型数据源的组件 */
    private static final Set<String> TIMESERIES_ONLY_COMPONENTS = Set.of("LINE_TREND");

    private final RptScreenMapper screenMapper;
    private final RptScreenBlockMapper blockMapper;
    private final RptScreenDatasourceMapper dsMapper;
    private final RptScreenMapPointMapper pointMapper;
    private final CurrentUserApi currentUserApi;
    private final ObjectMapper objectMapper = new ObjectMapper();

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
    public Long saveScreen(ScreenSaveReqDTO req) {
        validate(req);
        RptScreen s;
        if (req.getId() == null) {
            s = new RptScreen();
            s.setScreenCode(req.getScreenCode() == null || req.getScreenCode().isBlank()
                    ? "SCR_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT)
                    : req.getScreenCode());
            s.setCreatedBy(currentUserApi.getCurrentEmpId());
            applyScreenFields(s, req);
            screenMapper.insert(s);
        } else {
            s = requireScreen(req.getId());
            if (req.getScreenCode() != null && !req.getScreenCode().isBlank()) {
                s.setScreenCode(req.getScreenCode());
            }
            applyScreenFields(s, req);
            screenMapper.updateById(s);
        }
        // screenCode 唯一性（deleted=0 内，排除自身）
        LambdaQueryWrapper<RptScreen> dup = new LambdaQueryWrapper<RptScreen>()
                .eq(RptScreen::getScreenCode, s.getScreenCode())
                .ne(RptScreen::getId, s.getId());
        if (screenMapper.selectCount(dup) > 0) {
            throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID);
        }
        // 区块先删后插（整体覆盖语义）
        blockMapper.delete(new LambdaQueryWrapper<RptScreenBlock>().eq(RptScreenBlock::getScreenId, s.getId()));
        if (req.getBlocks() != null) {
            for (ScreenBlockDTO b : req.getBlocks()) {
                RptScreenBlock e = new RptScreenBlock();
                e.setScreenId(s.getId());
                e.setRegion(b.getRegion());
                e.setRowNo(b.getRowNo());
                e.setColNo(b.getColNo());
                e.setWidthPct(b.getWidthPct());
                e.setHeightPct(b.getHeightPct());
                e.setComponentType(b.getComponentType());
                e.setBindJson(b.getBindJson());
                e.setStyleJson(b.getStyleJson());
                e.setDrillJson(b.getDrillJson());
                blockMapper.insert(e);
            }
        }
        return s.getId();
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
        if (hits.isEmpty()) {
            throw new RptException(RptErrorCode.SCREEN_NOT_FOUND);
        }
        RptScreen s = hits.get(0);
        ScreenViewRespDTO view = new ScreenViewRespDTO();
        view.setScreen(toDetail(s, null));
        view.setBlocks(listBlocks(s.getId()));
        if ("PROVINCE".equals(s.getViewLevel())) {
            view.setMapPoints(pointMapper.selectList(new LambdaQueryWrapper<RptScreenMapPoint>()
                            .eq(RptScreenMapPoint::getStatus, "ACTIVE"))
                    .stream().map(this::toPointDto).collect(Collectors.toList()));
        } else {
            view.setMapPoints(List.of());
        }
        return view;
    }

    // ===== 内部 =====

    private void validate(ScreenSaveReqDTO req) {
        if (!VIEW_LEVELS.contains(req.getViewLevel())) {
            throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID);
        }
        List<ScreenBlockDTO> blocks = req.getBlocks() == null ? List.of() : req.getBlocks();

        // 第一遍：仅收集可解析出的 dsId 并统一查询数据源；无论后续布局校验在哪个区块失败，
        // 数据源查询都必须先行完成一次（避免规则顺序耦合导致数据源信息缺失）。
        Set<Long> dsIds = new HashSet<>();
        for (ScreenBlockDTO b : blocks) {
            Long dsId = readDsId(b.getBindJson());
            if (dsId != null) {
                dsIds.add(dsId);
            }
        }
        Map<Long, RptScreenDatasource> dsMap = dsIds.isEmpty() ? Map.of()
                : dsMapper.selectBatchIds(dsIds).stream()
                        .collect(Collectors.toMap(RptScreenDatasource::getId, Function.identity()));

        // 第二遍：逐块校验布局规则 + 数据绑定 + 组件与数据源能力匹配
        Map<String, Integer> rowWidth = new HashMap<>();
        for (ScreenBlockDTO b : blocks) {
            if (!REGIONS.contains(b.getRegion())
                    || !COMPONENT_TYPES.contains(b.getComponentType())
                    || b.getWidthPct() == null || b.getWidthPct() < 1 || b.getWidthPct() > 100
                    || b.getHeightPct() == null || b.getHeightPct() < 1 || b.getHeightPct() > 100) {
                throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID);
            }
            // PROVINCE 中心区固定为地图，不允许配置普通区块
            if ("PROVINCE".equals(req.getViewLevel()) && "MAIN".equals(b.getRegion())) {
                throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID);
            }
            String rowKey = b.getRegion() + "#" + b.getRowNo();
            int sum = rowWidth.merge(rowKey, b.getWidthPct(), Integer::sum);
            if (sum > 100) {
                throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID);
            }
            Long dsId = readDsId(b.getBindJson());
            if (dsId == null) {
                throw new RptException(RptErrorCode.SCREEN_LAYOUT_INVALID);
            }
            RptScreenDatasource ds = dsMap.get(dsId);
            if (ds == null) {
                throw new RptException(RptErrorCode.SCREEN_DS_NOT_FOUND);
            }
            boolean needTs = TIMESERIES_ONLY_COMPONENTS.contains(b.getComponentType()) || drillEnabled(b.getDrillJson());
            if (needTs && !"TIMESERIES".equals(ds.getDsType())) {
                throw new RptException(RptErrorCode.SCREEN_BLOCK_BIND_MISMATCH);
            }
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

    private void applyScreenFields(RptScreen s, ScreenSaveReqDTO req) {
        s.setScreenName(req.getScreenName());
        s.setViewLevel(req.getViewLevel());
        s.setThemeJson(req.getThemeJson());
        s.setStatus(req.getStatus() == null || req.getStatus().isBlank() ? "ACTIVE" : req.getStatus());
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
}
