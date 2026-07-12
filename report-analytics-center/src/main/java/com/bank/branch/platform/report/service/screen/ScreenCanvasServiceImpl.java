package com.bank.branch.platform.report.service.screen;

import com.bank.branch.platform.auth.api.CurrentUserApi;
import com.bank.branch.platform.report.dto.req.CanvasComponentDTO;
import com.bank.branch.platform.report.dto.req.ScreenBlockDTO;
import com.bank.branch.platform.report.dto.req.ScreenCanvasSaveReqDTO;
import com.bank.branch.platform.report.dto.resp.ScreenCanvasEditorRespDTO;
import com.bank.branch.platform.report.dto.resp.ScreenCanvasSaveRespDTO;
import com.bank.branch.platform.report.entity.RptScreen;
import com.bank.branch.platform.report.entity.RptScreenBlock;
import com.bank.branch.platform.report.enums.RptErrorCode;
import com.bank.branch.platform.report.exception.RptException;
import com.bank.branch.platform.report.mapper.RptScreenBlockMapper;
import com.bank.branch.platform.report.mapper.RptScreenCanvasMapper;
import com.bank.branch.platform.report.mapper.RptScreenDatasourceMapper;
import com.bank.branch.platform.report.mapper.RptScreenMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 大屏画布双态服务实现(加载/保存草稿).
 *
 * <p>坐标模型:设计态恒 1920×1080 基准像素(幂等)。取数配置不进组件树,只在 RPT_SCREEN_BLOCK 行,
 * 组件树 ChartWidget 节点靠 blockId 弱关联(照搬 DataEase componentData/core_chart_view 分层边界)。
 */
@Slf4j
@Service
public class ScreenCanvasServiceImpl implements ScreenCanvasService {

    /** 组件类型白名单(用户输入必须验证红线) */
    private static final Set<String> COMPONENT_TYPES = Set.of(
            "ChartWidget", "TextLabel", "ImageBox", "RectShape", "BorderDecor", "ClockWidget");
    /** ChartWidget 的 innerType 白名单(复用现有 5 图表) */
    private static final Set<String> INNER_TYPES = Set.of(
            "METRIC_CARD", "LINE_TREND", "PIE_SHARE", "RANK_LIST", "FLOW_STATUS");
    /** 画布 JSON 上限 2MB(字符数近似) */
    private static final int MAX_JSON_LEN = 2 * 1024 * 1024;
    private static final int DESIGN_W = 1920;
    private static final int DESIGN_H = 1080;

    private final RptScreenMapper screenMapper;
    private final RptScreenBlockMapper blockMapper;
    private final RptScreenDatasourceMapper dsMapper;
    private final RptScreenCanvasMapper canvasMapper;
    private final CurrentUserApi currentUserApi;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ScreenCanvasServiceImpl(RptScreenMapper screenMapper,
                                   RptScreenBlockMapper blockMapper,
                                   RptScreenDatasourceMapper dsMapper,
                                   RptScreenCanvasMapper canvasMapper,
                                   CurrentUserApi currentUserApi) {
        this.screenMapper = screenMapper;
        this.blockMapper = blockMapper;
        this.dsMapper = dsMapper;
        this.canvasMapper = canvasMapper;
        this.currentUserApi = currentUserApi;
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

    // ===== 内部 =====

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
