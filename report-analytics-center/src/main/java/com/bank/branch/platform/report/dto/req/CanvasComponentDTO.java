package com.bank.branch.platform.report.dto.req;

import lombok.Data;
import java.util.Map;

/** 画布单个组件节点(对应 DRAFT.components[i]). */
@Data
public class CanvasComponentDTO {
    /** 客户端生成的稳定 id(w-xxxx) */
    private String id;
    /** 组件类型:ChartWidget/TextLabel/ImageBox/RectShape/BorderDecor/ClockWidget */
    private String component;
    /** 仅 ChartWidget:METRIC_CARD/LINE_TREND/PIE_SHARE/RANK_LIST/FLOW_STATUS */
    private String innerType;
    /** 仅 ChartWidget:关联的区块 id;素材组件为 null */
    private Long blockId;
    /** 位置尺寸(top/left/width/height,1920×1080 设计基准像素) */
    private Map<String, Object> style;
    /** 素材组件私有配置 */
    private Map<String, Object> propValue;
    /** 仅 ChartWidget 保存时携带绑定配置(bind/style/drill 三段 JSON 字符串),后端 upsert 进 block 行 */
    private String bindJson;
    private String styleJson;
    private String drillJson;
    private Boolean isLock = false;
    private Boolean isShow = true;
}
