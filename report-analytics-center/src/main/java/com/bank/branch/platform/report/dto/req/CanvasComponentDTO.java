package com.bank.branch.platform.report.dto.req;

import lombok.Data;
import java.util.List;
import java.util.Map;

/** 画布单个组件节点(对应 DRAFT.components[i]). */
@Data
public class CanvasComponentDTO {
    /** 客户端生成的稳定 id(w-xxxx) */
    private String id;
    /** 组件类型:ChartWidget/TextLabel/ImageBox/RectShape/BorderDecor/ClockWidget/MapCenter/Group */
    private String component;
    /** 组件自定义名称(图层面板双击改名;可空,空时前端显示组件类型 label) */
    private String name;
    /** 仅 Group(多选成组容器):子组件节点,style 为相对组左上角坐标;其余组件为 null */
    private List<CanvasComponentDTO> children;
    /** 仅 ChartWidget：服务端白名单中的 19 种 innerType；未知类型保存时 Fail Close */
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
