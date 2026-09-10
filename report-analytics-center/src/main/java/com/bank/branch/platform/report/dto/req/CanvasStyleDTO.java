package com.bank.branch.platform.report.dto.req;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import java.util.Map;

/** 画布全局样式(对应 CANVAS_STYLE_JSON 的强类型入参). */
@Data
public class CanvasStyleDTO {
    /** schema 版本,自 v1 起 */
    private Integer schemaVersion = 1;
    private Integer designWidth = 1920;
    private Integer designHeight = 1080;
    private String background;
    /** 背景类型:solid/gradient/image(与前端 normalizeCanvasStyle 对齐). */
    private String backgroundType;
    /** 线性渐变背景配置;backgroundType=gradient 时使用. */
    private CanvasGradientDTO bgGradient;
    /** 背景图片 URL;backgroundType=image 时使用. */
    private String bgImage;
    /** 适配策略:keep/keepProportion/widthFirst/heightFirst */
    private String adaptor = "keepProportion";
    private Map<String, Object> themeOverride;
    /**
     * 可选的代码化大屏声明。缺省表示历史坐标画布，必须继续按旧契约兼容。
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private CodeScreenPresentationDTO presentation;

    /**
     * 可选的运行时数据口径说明；保存在既有 canvasStyle JSON 中，不新增表字段。
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String dataNotice;

    /**
     * 可选的代码化 KPI 展示标签覆盖；键和值均由服务端白名单校验。
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Map<String, String> metricLabels;

    /**
     * 可选的数据源可用性说明；仅描述绑定槽位的展示状态，不参与数据请求或权限判断。
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Map<String, SourceAvailabilityDTO> sourceAvailability;
}
